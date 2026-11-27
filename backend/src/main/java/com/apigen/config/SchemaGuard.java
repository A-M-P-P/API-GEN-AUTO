package com.apigen.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Garde-fou de schéma : vérifie au démarrage que chaque table attendue existe et la crée si
 * elle manque (par exemple {@code app_users}, qui gère les utilisateurs de l'application).
 *
 * <p>Flyway reste le mécanisme nominal. Ce composant couvre les cas où une table a été
 * supprimée à la main, où le schéma a été partiellement créé, ou encore où l'historique
 * Flyway a été perdu. Il s'exécute après Flyway et avant les initialisations de données.</p>
 */
@Component
@Order(1)
public class SchemaGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaGuard.class);

    private final JdbcTemplate jdbc;

    public SchemaGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        String product = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                connection -> connection.getMetaData().getDatabaseProductName());
        if (product == null || !(product.toLowerCase(java.util.Locale.ROOT).contains("oracle") || product.equalsIgnoreCase("H2"))) {
            log.info("Schéma {} géré exclusivement par Flyway ; garde-fou Oracle/H2 non exécuté.", product);
            return;
        }
        int created = 0;
        for (TableSpec spec : TABLES) {
            if (!tableExists(spec.name())) {
                for (String ddl : spec.ddl()) {
                    jdbc.execute(ddl);
                }
                created++;
                log.warn("Table {} absente : créée automatiquement.", spec.name());
            }
        }

        if (tableExists("organizations") && !columnExists("organizations", "context_config")) {
            jdbc.execute("ALTER TABLE organizations ADD context_config CLOB");
            log.warn("Colonne organizations.context_config absente : ajoutée automatiquement.");
        }

        if (tableExists("api_definitions") && !columnExists("api_definitions", "organization_id")) {
            jdbc.execute("ALTER TABLE api_definitions ADD organization_id NUMBER(19)");
            log.warn("Colonne api_definitions.organization_id absente : ajoutée automatiquement.");
            safeExecute("CREATE INDEX idx_api_organization ON api_definitions (organization_id)");
            safeExecute("ALTER TABLE api_definitions ADD CONSTRAINT fk_api_organization "
                    + "FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE SET NULL");
        }

        // Le code d'API devient unique par organisation ; l'ancienne contrainte globale est retirée.
        if (tableExists("api_definitions")) {
            safeExecute("ALTER TABLE api_definitions DROP CONSTRAINT ux_api_code");
            safeExecute("ALTER TABLE api_definitions ADD CONSTRAINT ux_api_code_org UNIQUE (organization_id, code)");
        }

        // Formats de réponse des endpoints.
        if (tableExists("endpoints")) {
            safeExecute("ALTER TABLE endpoints ADD response_format VARCHAR(20) DEFAULT 'JSON' NOT NULL");
            safeExecute("ALTER TABLE endpoints ADD response_file_name VARCHAR(200)");
            safeExecute("ALTER TABLE endpoints ADD response_content_type VARCHAR(100)");
            safeExecute("ALTER TABLE endpoints ADD redirect_location VARCHAR(500)");
        }

        if (created > 0) {
            log.warn("Schéma auto-complété : {} table(s) créée(s).", created);
        } else {
            log.info("Schéma vérifié : toutes les tables sont présentes.");
        }
    }

    private boolean tableExists(String table) {
        try {
            jdbc.query("SELECT 1 FROM " + table + " WHERE 1=0", row -> { });
            return true;
        } catch (DataAccessException e) {
            return false;
        }
    }

    private boolean columnExists(String table, String column) {
        try {
            jdbc.query("SELECT " + column + " FROM " + table + " WHERE 1=0", row -> { });
            return true;
        } catch (DataAccessException e) {
            return false;
        }
    }

    private void safeExecute(String ddl) {
        try {
            jdbc.execute(ddl);
        } catch (DataAccessException e) {
            log.debug("DDL ignoré (déjà présent) : {}", e.getMostSpecificCause().getMessage());
        }
    }

    private record TableSpec(String name, List<String> ddl) {
    }

    /** DDL identique à celui des migrations Flyway : Oracle 12c+ et H2 (mode Oracle). */
    private static final List<TableSpec> TABLES = List.of(
            new TableSpec("organizations", List.of("""
                    CREATE TABLE organizations (
                        id              NUMBER(19) GENERATED BY DEFAULT AS IDENTITY,
                        code            VARCHAR(60) NOT NULL,
                        name            VARCHAR(150) NOT NULL,
                        description     CLOB,
                        context_config  CLOB,
                        active          NUMBER(1) DEFAULT 1 NOT NULL,
                        created_at      TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        updated_at      TIMESTAMP(6),
                        CONSTRAINT pk_organization PRIMARY KEY (id),
                        CONSTRAINT ux_organization_code UNIQUE (code)
                    )""")),
            new TableSpec("api_definitions", List.of("""
                    CREATE TABLE api_definitions (
                        id              NUMBER(19) GENERATED BY DEFAULT AS IDENTITY,
                        code            VARCHAR(80) NOT NULL,
                        name            VARCHAR(150) NOT NULL,
                        description     CLOB,
                        version         VARCHAR(20) DEFAULT '1.0' NOT NULL,
                        status          VARCHAR(20) DEFAULT 'DRAFT' NOT NULL,
                        inbound_auth    VARCHAR(20) DEFAULT 'NONE' NOT NULL,
                        api_key_hash    VARCHAR(128),
                        organization_id NUMBER(19),
                        created_by      VARCHAR(100),
                        created_at      TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        updated_at      TIMESTAMP(6),
                        CONSTRAINT pk_api_def PRIMARY KEY (id),
                        CONSTRAINT ux_api_code_org UNIQUE (organization_id, code),
                        CONSTRAINT ck_api_status CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED')),
                        CONSTRAINT ck_api_auth CHECK (inbound_auth IN ('NONE', 'API_KEY', 'JWT'))
                    )""", """
                    ALTER TABLE api_definitions
                        ADD CONSTRAINT fk_api_organization
                        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE SET NULL""", """
                    CREATE INDEX idx_api_organization ON api_definitions (organization_id)""")),
            new TableSpec("endpoints", List.of("""
                    CREATE TABLE endpoints (
                        id              NUMBER(19) GENERATED BY DEFAULT AS IDENTITY,
                        api_id          NUMBER(19) NOT NULL,
                        name            VARCHAR(120) NOT NULL,
                        method          VARCHAR(10) NOT NULL,
                        path            VARCHAR(300) NOT NULL,
                        direction       VARCHAR(20) DEFAULT 'INBOUND' NOT NULL,
                        description     CLOB,
                        response_format VARCHAR(20) DEFAULT 'JSON' NOT NULL,
                        response_file_name VARCHAR(200),
                        response_content_type VARCHAR(100),
                        redirect_location VARCHAR(500),
                        pipeline        CLOB,
                        active          NUMBER(1) DEFAULT 1 NOT NULL,
                        created_at      TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        updated_at      TIMESTAMP(6),
                        CONSTRAINT pk_endpoint PRIMARY KEY (id),
                        CONSTRAINT fk_endpoint_api FOREIGN KEY (api_id) REFERENCES api_definitions (id) ON DELETE CASCADE,
                        CONSTRAINT ux_endpoint_route UNIQUE (api_id, method, path),
                        CONSTRAINT ck_endpoint_direction CHECK (direction IN ('INBOUND', 'OUTBOUND')),
                        CONSTRAINT ck_endpoint_method CHECK (method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'))
                    )""", """
                    CREATE INDEX idx_endpoints_lookup ON endpoints (api_id, active, method, path)""")),
            new TableSpec("connections", List.of("""
                    CREATE TABLE connections (
                        id              NUMBER(19) GENERATED BY DEFAULT AS IDENTITY,
                        name            VARCHAR(120) NOT NULL,
                        type            VARCHAR(30) NOT NULL,
                        description     CLOB,
                        base_config     CLOB,
                        credentials     CLOB,
                        active          NUMBER(1) DEFAULT 1 NOT NULL,
                        created_at      TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        updated_at      TIMESTAMP(6),
                        CONSTRAINT pk_connection PRIMARY KEY (id),
                        CONSTRAINT ux_connection_name UNIQUE (name),
                        CONSTRAINT ck_connection_type CHECK (type IN ('ORACLE', 'POSTGRESQL', 'MYSQL', 'SQLSERVER', 'REST', 'FILE', 'FTP', 'SFTP'))
                    )""")),
            new TableSpec("schedules", List.of("""
                    CREATE TABLE schedules (
                        id              NUMBER(19) GENERATED BY DEFAULT AS IDENTITY,
                        endpoint_id     NUMBER(19) NOT NULL,
                        cron_expression VARCHAR(100) NOT NULL,
                        enabled         NUMBER(1) DEFAULT 1 NOT NULL,
                        payload         CLOB,
                        next_run_at     TIMESTAMP(6),
                        last_run_at     TIMESTAMP(6),
                        created_at      TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        updated_at      TIMESTAMP(6),
                        CONSTRAINT pk_schedule PRIMARY KEY (id),
                        CONSTRAINT fk_schedule_endpoint FOREIGN KEY (endpoint_id) REFERENCES endpoints (id) ON DELETE CASCADE,
                        CONSTRAINT ux_schedule_endpoint UNIQUE (endpoint_id)
                    )""", """
                    CREATE INDEX idx_schedules_due ON schedules (enabled, next_run_at)""")),
            new TableSpec("exchange_runs", List.of("""
                    CREATE TABLE exchange_runs (
                        id              NUMBER(19) GENERATED BY DEFAULT AS IDENTITY,
                        api_id          NUMBER(19),
                        endpoint_id     NUMBER(19),
                        status          VARCHAR(20) NOT NULL,
                        direction       VARCHAR(20),
                        trigger_type    VARCHAR(20),
                        correlation_id  VARCHAR(80),
                        request_summary CLOB,
                        response_summary CLOB,
                        error_message   CLOB,
                        started_at      TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        finished_at     TIMESTAMP(6),
                        duration_ms     NUMBER(19),
                        CONSTRAINT pk_run PRIMARY KEY (id),
                        CONSTRAINT fk_run_api FOREIGN KEY (api_id) REFERENCES api_definitions (id) ON DELETE SET NULL,
                        CONSTRAINT fk_run_endpoint FOREIGN KEY (endpoint_id) REFERENCES endpoints (id) ON DELETE SET NULL,
                        CONSTRAINT ck_run_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED'))
                    )""", """
                    CREATE INDEX idx_runs_started ON exchange_runs (started_at DESC)""", """
                    CREATE INDEX idx_runs_status ON exchange_runs (status)""", """
                    CREATE INDEX idx_runs_api ON exchange_runs (api_id)""")),
            new TableSpec("app_users", List.of("""
                    CREATE TABLE app_users (
                        id              NUMBER(19) GENERATED BY DEFAULT AS IDENTITY,
                        username        VARCHAR(80) NOT NULL,
                        password_hash   VARCHAR(120) NOT NULL,
                        full_name       VARCHAR(150),
                        role            VARCHAR(20) DEFAULT 'VIEWER' NOT NULL,
                        enabled         NUMBER(1) DEFAULT 1 NOT NULL,
                        created_at      TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        updated_at      TIMESTAMP(6),
                        CONSTRAINT pk_app_user PRIMARY KEY (id),
                        CONSTRAINT ux_user_username UNIQUE (username),
                        CONSTRAINT ck_user_role CHECK (role IN ('ADMIN', 'EDITOR', 'VIEWER'))
                    )""")),
            new TableSpec("audit_events", List.of("""
                    CREATE TABLE audit_events (
                        id              NUMBER(19) GENERATED BY DEFAULT AS IDENTITY,
                        actor           VARCHAR(100),
                        action          VARCHAR(80) NOT NULL,
                        entity_type     VARCHAR(80),
                        entity_id       VARCHAR(80),
                        details         CLOB,
                        created_at      TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        CONSTRAINT pk_audit PRIMARY KEY (id)
                    )""", """
                    CREATE INDEX idx_audit_created ON audit_events (created_at DESC)"""))
    );
}
