-- Platform migrations: sqlserver (new installations).
CREATE TABLE organizations (
    id              BIGINT IDENTITY(1,1),
    code            NVARCHAR(60) NOT NULL,
    name            NVARCHAR(150) NOT NULL,
    description     NVARCHAR(MAX),
    active          BIT DEFAULT 1 NOT NULL,
    created_at      DATETIME2(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      DATETIME2(6),
    CONSTRAINT pk_organization PRIMARY KEY (id),
    CONSTRAINT ux_organization_code UNIQUE (code)
);

ALTER TABLE api_definitions ADD organization_id BIGINT;

ALTER TABLE api_definitions
    ADD CONSTRAINT fk_api_organization
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE SET NULL;

CREATE INDEX idx_api_organization ON api_definitions (organization_id);
