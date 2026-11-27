-- Platform migrations: sqlserver (new installations).
CREATE TABLE api_definitions (
    id              BIGINT IDENTITY(1,1),
    code            NVARCHAR(80) NOT NULL,
    name            NVARCHAR(150) NOT NULL,
    description     NVARCHAR(MAX),
    version         NVARCHAR(20) DEFAULT '1.0' NOT NULL,
    status          NVARCHAR(20) DEFAULT 'DRAFT' NOT NULL,
    inbound_auth    NVARCHAR(20) DEFAULT 'NONE' NOT NULL,
    api_key_hash    NVARCHAR(128),
    created_by      NVARCHAR(100),
    created_at      DATETIME2(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      DATETIME2(6),
    CONSTRAINT pk_api_def PRIMARY KEY (id),
    CONSTRAINT ux_api_code UNIQUE (code),
    CONSTRAINT ck_api_status CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED')),
    CONSTRAINT ck_api_auth CHECK (inbound_auth IN ('NONE', 'API_KEY', 'JWT'))
);

CREATE TABLE endpoints (
    id              BIGINT IDENTITY(1,1),
    api_id          BIGINT NOT NULL,
    name            NVARCHAR(120) NOT NULL,
    method          NVARCHAR(10) NOT NULL,
    path            NVARCHAR(300) NOT NULL,
    direction       NVARCHAR(20) DEFAULT 'INBOUND' NOT NULL,
    description     NVARCHAR(MAX),
    pipeline        NVARCHAR(MAX),
    active          BIT DEFAULT 1 NOT NULL,
    created_at      DATETIME2(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      DATETIME2(6),
    CONSTRAINT pk_endpoint PRIMARY KEY (id),
    CONSTRAINT fk_endpoint_api FOREIGN KEY (api_id) REFERENCES api_definitions (id) ON DELETE CASCADE,
    CONSTRAINT ux_endpoint_route UNIQUE (api_id, method, path),
    CONSTRAINT ck_endpoint_direction CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT ck_endpoint_method CHECK (method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'))
);

CREATE INDEX idx_endpoints_lookup ON endpoints (api_id, active, method, path);

CREATE TABLE connections (
    id              BIGINT IDENTITY(1,1),
    name            NVARCHAR(120) NOT NULL,
    type            NVARCHAR(30) NOT NULL,
    description     NVARCHAR(MAX),
    base_config     NVARCHAR(MAX),
    credentials     NVARCHAR(MAX),
    active          BIT DEFAULT 1 NOT NULL,
    created_at      DATETIME2(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      DATETIME2(6),
    CONSTRAINT pk_connection PRIMARY KEY (id),
    CONSTRAINT ux_connection_name UNIQUE (name),
    CONSTRAINT ck_connection_type CHECK (type IN ('ORACLE', 'POSTGRESQL', 'MYSQL', 'SQLSERVER', 'REST', 'FILE', 'FTP', 'SFTP'))
);

CREATE TABLE schedules (
    id              BIGINT IDENTITY(1,1),
    endpoint_id     BIGINT NOT NULL,
    cron_expression NVARCHAR(100) NOT NULL,
    enabled         BIT DEFAULT 1 NOT NULL,
    payload         NVARCHAR(MAX),
    next_run_at     DATETIME2(6),
    last_run_at     DATETIME2(6),
    created_at      DATETIME2(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      DATETIME2(6),
    CONSTRAINT pk_schedule PRIMARY KEY (id),
    CONSTRAINT fk_schedule_endpoint FOREIGN KEY (endpoint_id) REFERENCES endpoints (id) ON DELETE CASCADE,
    CONSTRAINT ux_schedule_endpoint UNIQUE (endpoint_id)
);

CREATE INDEX idx_schedules_due ON schedules (enabled, next_run_at);

CREATE TABLE exchange_runs (
    id              BIGINT IDENTITY(1,1),
    api_id          BIGINT,
    endpoint_id     BIGINT,
    status          NVARCHAR(20) NOT NULL,
    direction       NVARCHAR(20),
    trigger_type    NVARCHAR(20),
    correlation_id  NVARCHAR(80),
    request_summary NVARCHAR(MAX),
    response_summary NVARCHAR(MAX),
    error_message   NVARCHAR(MAX),
    started_at      DATETIME2(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    finished_at     DATETIME2(6),
    duration_ms     BIGINT,
    CONSTRAINT pk_run PRIMARY KEY (id),
    CONSTRAINT fk_run_endpoint FOREIGN KEY (endpoint_id) REFERENCES endpoints (id) ON DELETE SET NULL,
    CONSTRAINT ck_run_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX idx_runs_started ON exchange_runs (started_at DESC);
CREATE INDEX idx_runs_status ON exchange_runs (status);
CREATE INDEX idx_runs_api ON exchange_runs (api_id);

CREATE TABLE app_users (
    id              BIGINT IDENTITY(1,1),
    username        NVARCHAR(80) NOT NULL,
    password_hash   NVARCHAR(120) NOT NULL,
    full_name       NVARCHAR(150),
    role            NVARCHAR(20) DEFAULT 'VIEWER' NOT NULL,
    enabled         BIT DEFAULT 1 NOT NULL,
    created_at      DATETIME2(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      DATETIME2(6),
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT ux_user_username UNIQUE (username),
    CONSTRAINT ck_user_role CHECK (role IN ('ADMIN', 'EDITOR', 'VIEWER'))
);

CREATE TABLE audit_events (
    id              BIGINT IDENTITY(1,1),
    actor           NVARCHAR(100),
    action          NVARCHAR(80) NOT NULL,
    entity_type     NVARCHAR(80),
    entity_id       NVARCHAR(80),
    details         NVARCHAR(MAX),
    created_at      DATETIME2(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_audit PRIMARY KEY (id)
);

CREATE INDEX idx_audit_created ON audit_events (created_at DESC);
