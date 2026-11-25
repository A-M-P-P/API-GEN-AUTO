package com.apigen.service;

import com.apigen.domain.ConnectionType;
import com.apigen.dto.MetadataDtos;
import com.apigen.service.connector.JdbcConnectionProps;
import com.apigen.service.connector.JdbcPoolManager;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Découverte de la structure d'une base cible : schémas, tables, vues et colonnes.
 *
 * <p>Les requêtes sont écrites par dialecte plutôt que via {@link java.sql.DatabaseMetaData}
 * parce que le pilote Oracle tronque {@code getSchemas()} au seul utilisateur courant.</p>
 */
@Service
public class MetadataService {

    private static final int MAX_SCHEMAS = 100;
    private static final int MAX_TABLES = 300;
    private static final int MAX_COLUMNS = 200;

    private static final Map<ConnectionType, String> SCHEMA_SQL = Map.of(
            ConnectionType.ORACLE,
            "SELECT username FROM all_users WHERE oracle_maintained = 'N' ORDER BY username",
            ConnectionType.POSTGRESQL,
            "SELECT schema_name FROM information_schema.schemata "
                    + "WHERE schema_name NOT IN ('pg_catalog', 'information_schema', 'pg_toast') "
                    + "ORDER BY schema_name",
            ConnectionType.MYSQL,
            "SELECT schema_name FROM information_schema.schemata "
                    + "WHERE schema_name NOT IN ('mysql', 'information_schema', 'performance_schema', 'sys') "
                    + "ORDER BY schema_name",
            ConnectionType.SQLSERVER,
            "SELECT name FROM sys.schemas WHERE name NOT IN ("
                    + "'db_owner', 'db_accessadmin', 'db_securityadmin', 'db_ddladmin', 'db_backupoperator', "
                    + "'db_datareader', 'db_datawriter', 'db_denydatareader', 'db_denydatawriter', "
                    + "'guest', 'INFORMATION_SCHEMA', 'sys') ORDER BY name"
    );

    /** Repli pour Oracle 11g : la colonne ORACLE_MAINTAINED n'existe qu'à partir de 12c. */
    private static final String SCHEMA_SQL_ORACLE_LEGACY =
            "SELECT username FROM all_users WHERE username NOT IN ("
                    + "'SYS','SYSTEM','XDB','MDSYS','CTXSYS','DBSNMP','OUTLN','WMSYS','ORDSYS','ORDDATA',"
                    + "'LBACSYS','DMSYS','TSMSYS','DIP','ORACLE_OCM','APPQOSSYS','OLAPSYS','SI_INFORMTN_SCHEMA',"
                    + "'EXFSYS','FLOWS_FILES','PM','SH','IX','OE','HR','SCOTT','AUDSYS','GSMADMIN_INTERNAL',"
                    + "'GSMUSER','DVF','DVSYS','OJVMSYS','SYSBACKUP','SYSDG','SYSKM','SYSRAC','DBSFWUSER',"
                    + "'ANONYMOUS','XS$NULL','APEX_PUBLIC_USER') ORDER BY username";

    private static final String TABLE_SQL_ORACLE =
            "SELECT table_name, 'TABLE' AS object_type FROM all_tables WHERE owner = ? "
                    + "UNION ALL SELECT view_name, 'VIEW' FROM all_views WHERE owner = ? "
                    + "ORDER BY 1";
    private static final String TABLE_SQL_INFORMATION_SCHEMA =
            "SELECT table_name, table_type FROM information_schema.tables WHERE table_schema = ? ORDER BY table_name";
    private static final String COLUMN_SQL_ORACLE =
            "SELECT column_name, data_type, nullable FROM all_tab_columns "
                    + "WHERE owner = ? AND table_name = ? ORDER BY column_id";
    private static final String COLUMN_SQL_INFORMATION_SCHEMA =
            "SELECT column_name, data_type, is_nullable FROM information_schema.columns "
                    + "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position";

    private final ConnectionService connectionService;
    private final JdbcPoolManager poolManager;

    public MetadataService(ConnectionService connectionService, JdbcPoolManager poolManager) {
        this.connectionService = connectionService;
        this.poolManager = poolManager;
    }

    public MetadataDtos.DiscoveryResponse discover(MetadataDtos.DiscoveryRequest request) throws SQLException {
        ConnectionService.ConnectionSettings settings = resolve(request);
        try (Connection connection = open(request, settings)) {
            List<MetadataDtos.SchemaSummary> schemas = new ArrayList<>();
            List<String> names = listSchemas(connection, settings);
            for (String name : names) {
                schemas.add(new MetadataDtos.SchemaSummary(name, listTables(connection, settings, name)));
            }
            return new MetadataDtos.DiscoveryResponse(
                    product(connection), version(connection), schemas);
        }
    }

    private List<String> listSchemas(Connection connection,
                                     ConnectionService.ConnectionSettings settings) throws SQLException {
        try {
            return querySchemas(connection, schemaSql(settings));
        } catch (SQLException first) {
            if (settings.connection().getType() != ConnectionType.ORACLE) {
                throw first;
            }
            // Oracle 11g : ORACLE_MAINTAINED est inconnu (ORA-00904). On retente sans.
            return querySchemas(connection, SCHEMA_SQL_ORACLE_LEGACY);
        }
    }

    private List<String> querySchemas(Connection connection, String sql) throws SQLException {
        List<String> names = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next() && names.size() < MAX_SCHEMAS) {
                names.add(resultSet.getString(1));
            }
        }
        return names;
    }

    public MetadataDtos.ColumnsResponse columns(MetadataDtos.ColumnsRequest request) throws SQLException {
        ConnectionService.ConnectionSettings settings = resolve(request);
        try (Connection connection = open(request, settings)) {
            String schema = request.schema();
            String table = request.table();
            if (schema == null || schema.isBlank() || table == null || table.isBlank()) {
                throw new IllegalArgumentException("schema et table sont requis");
            }
            boolean oracle = settings.connection().getType() == ConnectionType.ORACLE;
            String sql = oracle ? COLUMN_SQL_ORACLE : COLUMN_SQL_INFORMATION_SCHEMA;
            List<MetadataDtos.ColumnInfo> columns = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, schema);
                statement.setString(2, table);
                statement.setMaxRows(MAX_COLUMNS);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        columns.add(new MetadataDtos.ColumnInfo(
                                resultSet.getString(1),
                                resultSet.getString(2),
                                isNullable(resultSet.getString(3))));
                    }
                }
            }
            if (columns.isEmpty()) {
                throw new IllegalArgumentException(
                        "Aucune colonne trouvée pour " + schema + "." + table);
            }
            return new MetadataDtos.ColumnsResponse(schema, table, columns);
        }
    }

    private List<MetadataDtos.TableSummary> listTables(Connection connection,
                                                       ConnectionService.ConnectionSettings settings,
                                                       String schema) throws SQLException {
        boolean oracle = settings.connection().getType() == ConnectionType.ORACLE;
        String sql = oracle ? TABLE_SQL_ORACLE : TABLE_SQL_INFORMATION_SCHEMA;
        List<MetadataDtos.TableSummary> tables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            if (oracle) {
                statement.setString(2, schema);
            }
            statement.setMaxRows(MAX_TABLES);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tables.add(new MetadataDtos.TableSummary(resultSet.getString(1), resultSet.getString(2)));
                }
            }
        } catch (SQLException e) {
            // Un schéma peut être illisible (droits insuffisants) : on ne bloque pas la découverte.
            return tables;
        }
        return tables;
    }

    private String schemaSql(ConnectionService.ConnectionSettings settings) {
        String sql = SCHEMA_SQL.get(settings.connection().getType());
        if (sql == null) {
            throw new IllegalArgumentException(
                    "La découverte n'est pas disponible pour le type " + settings.connection().getType());
        }
        return sql;
    }

    private ConnectionService.ConnectionSettings resolve(MetadataDtos.DiscoveryRequest request) {
        if (request.connectionId() != null) {
            return connectionService.settings(request.connectionId());
        }
        return connectionService.inlineSettings(request.type(), request.baseConfig(), request.credentials());
    }

    private ConnectionService.ConnectionSettings resolve(MetadataDtos.ColumnsRequest request) {
        if (request.connectionId() != null) {
            return connectionService.settings(request.connectionId());
        }
        return connectionService.inlineSettings(request.type(), request.baseConfig(), request.credentials());
    }

    /**
     * Ouvre une connexion au pool de la connexion enregistrée, ou en direct pour un formulaire
     * non encore enregistré.
     */
    private Connection open(MetadataDtos.DiscoveryRequest request,
                            ConnectionService.ConnectionSettings settings) throws SQLException {
        JdbcPoolManager.PoolSpec spec = JdbcPoolManager.specOf(settings.baseConfig(), settings.credentials());
        if (request.connectionId() != null) {
            return poolManager.borrow("conn:" + request.connectionId(), spec);
        }
        return direct(spec);
    }

    private Connection open(MetadataDtos.ColumnsRequest request,
                            ConnectionService.ConnectionSettings settings) throws SQLException {
        JdbcPoolManager.PoolSpec spec = JdbcPoolManager.specOf(settings.baseConfig(), settings.credentials());
        if (request.connectionId() != null) {
            return poolManager.borrow("conn:" + request.connectionId(), spec);
        }
        return direct(spec);
    }

    private Connection direct(JdbcPoolManager.PoolSpec spec) throws SQLException {
        int previous = DriverManager.getLoginTimeout();
        DriverManager.setLoginTimeout(5);
        try {
            return DriverManager.getConnection(spec.jdbcUrl(), JdbcConnectionProps.toProperties(
                    spec.driverProperties(), spec.username(), spec.password()));
        } finally {
            DriverManager.setLoginTimeout(previous);
        }
    }

    private String product(Connection connection) throws SQLException {
        String value = connection.getMetaData().getDatabaseProductName();
        return value == null ? "?" : value;
    }

    private String version(Connection connection) throws SQLException {
        String value = connection.getMetaData().getDatabaseProductVersion();
        if (value == null) {
            return "";
        }
        return value.length() > 80 ? value.substring(0, 80) : value;
    }

    private boolean isNullable(String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return !"N".equals(normalized) && !"NO".equals(normalized);
    }
}
