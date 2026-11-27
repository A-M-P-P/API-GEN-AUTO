-- Platform migrations: mysql (new installations).
CREATE TABLE api_definitions (
    id              BIGINT AUTO_INCREMENT,
    code            VARCHAR(80) NOT NULL,
    name            VARCHAR(150) NOT NULL,
    description     LONGTEXT,
    version         VARCHAR(20) DEFAULT '1.0' NOT NULL,
    status          VARCHAR(20) DEFAULT 'DRAFT' NOT NULL,
    inbound_auth    VARCHAR(20) DEFAULT 'NONE' NOT NULL,
    api_key_hash    VARCHAR(128),
    created_by      VARCHAR(100),
    created_at      TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6),
    CONSTRAINT pk_api_def PRIMARY KEY (id),
    CONSTRAINT ux_api_code UNIQUE (code),
    CONSTRAINT ck_api_status CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED')),
    CONSTRAINT ck_api_auth CHECK (inbound_auth IN ('NONE', 'API_KEY', 'JWT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE endpoints (
    id              BIGINT AUTO_INCREMENT,
    api_id          BIGINT NOT NULL,
    name            VARCHAR(120) NOT NULL,
    method          VARCHAR(10) NOT NULL,
    path            VARCHAR(300) NOT NULL,
    direction       VARCHAR(20) DEFAULT 'INBOUND' NOT NULL,
    description     LONGTEXT,
    pipeline        LONGTEXT,
    active          BOOLEAN DEFAULT 1 NOT NULL,
    created_at      TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6),
    CONSTRAINT pk_endpoint PRIMARY KEY (id),
    CONSTRAINT fk_endpoint_api FOREIGN KEY (api_id) REFERENCES api_definitions (id) ON DELETE CASCADE,
    CONSTRAINT ux_endpoint_route UNIQUE (api_id, method, path),
    CONSTRAINT ck_endpoint_direction CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT ck_endpoint_method CHECK (method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_endpoints_lookup ON endpoints (api_id, active, method, path);

CREATE TABLE connections (
    id              BIGINT AUTO_INCREMENT,
    name            VARCHAR(120) NOT NULL,
    type            VARCHAR(30) NOT NULL,
    description     LONGTEXT,
    base_config     LONGTEXT,
    credentials     LONGTEXT,
    active          BOOLEAN DEFAULT 1 NOT NULL,
    created_at      TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6),
    CONSTRAINT pk_connection PRIMARY KEY (id),
    CONSTRAINT ux_connection_name UNIQUE (name),
    CONSTRAINT ck_connection_type CHECK (type IN ('ORACLE', 'POSTGRESQL', 'MYSQL', 'SQLSERVER', 'REST', 'FILE', 'FTP', 'SFTP'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE schedules (
    id              BIGINT AUTO_INCREMENT,
    endpoint_id     BIGINT NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    enabled         BOOLEAN DEFAULT 1 NOT NULL,
    payload         LONGTEXT,
    next_run_at     TIMESTAMP(6),
    last_run_at     TIMESTAMP(6),
    created_at      TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6),
    CONSTRAINT pk_schedule PRIMARY KEY (id),
    CONSTRAINT fk_schedule_endpoint FOREIGN KEY (endpoint_id) REFERENCES endpoints (id) ON DELETE CASCADE,
    CONSTRAINT ux_schedule_endpoint UNIQUE (endpoint_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_schedules_due ON schedules (enabled, next_run_at);

CREATE TABLE exchange_runs (
    id              BIGINT AUTO_INCREMENT,
    api_id          BIGINT,
    endpoint_id     BIGINT,
    status          VARCHAR(20) NOT NULL,
    direction       VARCHAR(20),
    trigger_type    VARCHAR(20),
    correlation_id  VARCHAR(80),
    request_summary LONGTEXT,
    response_summary LONGTEXT,
    error_message   LONGTEXT,
    started_at      TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    finished_at     TIMESTAMP(6),
    duration_ms     BIGINT,
    CONSTRAINT pk_run PRIMARY KEY (id),
    CONSTRAINT fk_run_api FOREIGN KEY (api_id) REFERENCES api_definitions (id) ON DELETE SET NULL,
    CONSTRAINT fk_run_endpoint FOREIGN KEY (endpoint_id) REFERENCES endpoints (id) ON DELETE SET NULL,
    CONSTRAINT ck_run_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_runs_started ON exchange_runs (started_at DESC);
CREATE INDEX idx_runs_status ON exchange_runs (status);
CREATE INDEX idx_runs_api ON exchange_runs (api_id);

CREATE TABLE app_users (
    id              BIGINT AUTO_INCREMENT,
    username        VARCHAR(80) NOT NULL,
    password_hash   VARCHAR(120) NOT NULL,
    full_name       VARCHAR(150),
    role            VARCHAR(20) DEFAULT 'VIEWER' NOT NULL,
    enabled         BOOLEAN DEFAULT 1 NOT NULL,
    created_at      TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6),
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT ux_user_username UNIQUE (username),
    CONSTRAINT ck_user_role CHECK (role IN ('ADMIN', 'EDITOR', 'VIEWER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_events (
    id              BIGINT AUTO_INCREMENT,
    actor           VARCHAR(100),
    action          VARCHAR(80) NOT NULL,
    entity_type     VARCHAR(80),
    entity_id       VARCHAR(80),
    details         LONGTEXT,
    created_at      TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_audit PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_audit_created ON audit_events (created_at DESC);
