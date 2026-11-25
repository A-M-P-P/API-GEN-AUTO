package com.apigen.service.connector;

import com.apigen.domain.ConnectionType;
import com.apigen.execution.ExecutionContext;
import com.apigen.execution.TemplateEngine;
import com.apigen.service.ConnectionService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Connecteur JDBC pour Oracle, PostgreSQL, MySQL et SQL Server.
 */
@Component
public class JdbcConnector {

    private static final int DEFAULT_MAX_ROWS = 1000;
    private static final int MAX_ROWS_CAP = 10_000;

    private final ConnectionService connectionService;
    private final TemplateEngine templates;
    private final JdbcPoolManager poolManager;

    public JdbcConnector(ConnectionService connectionService, TemplateEngine templates, JdbcPoolManager poolManager) {
        this.connectionService = connectionService;
        this.templates = templates;
        this.poolManager = poolManager;
    }

    public Object query(Map<String, Object> step, ExecutionContext context) {
        ConnectionService.ConnectionSettings settings = requireDatabaseConnection(step, context);
        String sql = templates.resolveString(string(step.get("sql"), ""), context);
        if (sql.isBlank()) {
            throw new IllegalArgumentException("L'étape DB_QUERY doit définir une requête sql");
        }
        int maxRows = clamp(integer(step.get("maxRows"), DEFAULT_MAX_ROWS), 1, MAX_ROWS_CAP);
        int timeout = clamp(integer(step.get("timeoutSeconds"), 60), 1, 3600);

        try (Connection connection = openConnection(settings);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setMaxRows(maxRows);
            statement.setQueryTimeout(timeout);
            bindParameters(statement, step.get("params"), context);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(readRow(resultSet));
                }
                return rows;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Requête SQL échouée : " + rootMessage(e), e);
        }
    }

    public Map<String, Object> update(Map<String, Object> step, ExecutionContext context) {
        ConnectionService.ConnectionSettings settings = requireDatabaseConnection(step, context);
        String sql = templates.resolveString(string(step.get("sql"), ""), context);
        if (sql.isBlank()) {
            throw new IllegalArgumentException("L'étape DB_EXEC doit définir une requête sql");
        }
        int timeout = clamp(integer(step.get("timeoutSeconds"), 60), 1, 3600);

        try (Connection connection = openConnection(settings);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(timeout);
            bindParameters(statement, step.get("params"), context);
            int count = statement.executeUpdate();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("updateCount", count);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Exécution SQL échouée : " + rootMessage(e), e);
        }
    }

    public void testQuery(String jdbcUrl, String username, String password) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl, JdbcConnectionProps.forUrl(jdbcUrl, username, password))) {
            connection.isValid(5);
        }
    }
    private ConnectionService.ConnectionSettings requireDatabaseConnection(Map<String, Object> step, ExecutionContext context) {
        Object rawId = templates.resolve(step.get("connectionId"), context);
        if (rawId == null || String.valueOf(rawId).isBlank()) {
            throw new IllegalArgumentException("L'étape base de données doit définir connectionId");
        }
        Long id = rawId instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(rawId));
        ConnectionService.ConnectionSettings settings = connectionService.settings(id);
        if (!isDatabase(settings.connection().getType())) {
            throw new IllegalArgumentException("La connexion " + id + " n'est pas une connexion base de données");
        }
        return settings;
    }

    private boolean isDatabase(ConnectionType type) {
        return type == ConnectionType.ORACLE || type == ConnectionType.POSTGRESQL
                || type == ConnectionType.MYSQL || type == ConnectionType.SQLSERVER;
    }

    private Connection openConnection(ConnectionService.ConnectionSettings settings) throws Exception {
        JsonNode config = settings.baseConfig();
        String jdbcUrl = text(config, "jdbcUrl");
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl manquant dans la configuration de la connexion");
        }
        String username = value(settings.credentials(), "username");
        String password = value(settings.credentials(), "password");
        int maxPoolSize = integer(config.has("maxPoolSize") ? config.get("maxPoolSize").asInt() : null, 5);
        JdbcPoolManager.PoolSpec spec = new JdbcPoolManager.PoolSpec(jdbcUrl, username, password, maxPoolSize);
        return poolManager.borrow("conn:" + settings.connection().getId(), spec);
    }

    private void bindParameters(PreparedStatement statement, Object params, ExecutionContext context) throws Exception {
        if (params == null) {
            return;
        }
        Object resolved = templates.resolve(params, context);
        List<?> values = resolved instanceof List<?> list ? list : List.of(resolved);
        int index = 1;
        for (Object value : values) {
            if (value == null) {
                statement.setObject(index++, null);
            } else if (value instanceof Number || value instanceof Boolean || value instanceof String) {
                statement.setObject(index++, value);
            } else {
                statement.setString(index++, String.valueOf(value));
            }
        }
    }

    private Map<String, Object> readRow(ResultSet resultSet) throws Exception {
        ResultSetMetaData meta = resultSet.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String label = meta.getColumnLabel(i);
            row.put(label == null ? meta.getColumnName(i) : label.toLowerCase(Locale.ROOT), normalize(resultSet.getObject(i)));
        }
        return row;
    }

    private Object normalize(Object value) throws Exception {
        if (value == null) {
            return null;
        }
        if (value instanceof Clob clob) {
            return clob.getSubString(1, (int) clob.length());
        }
        if (value instanceof Blob blob) {
            return blob.getBytes(1, (int) blob.length());
        }
        if (value instanceof Array array) {
            return array.getArray();
        }
        if (value instanceof BigDecimal decimal) {
            if (decimal.stripTrailingZeros().scale() <= 0) {
                return decimal.longValue();
            }
            return decimal;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        return node == null || !node.hasNonNull(field) ? null : node.get(field).asText();
    }

    private String value(Map<String, Object> credentials, String key) {
        Object value = credentials == null ? null : credentials.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String string(Object value, String defaultValue) {
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    private int integer(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
