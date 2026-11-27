-- Platform migrations: mysql (new installations).
CREATE TABLE organizations (
    id              BIGINT AUTO_INCREMENT,
    code            VARCHAR(60) NOT NULL,
    name            VARCHAR(150) NOT NULL,
    description     LONGTEXT,
    active          BOOLEAN DEFAULT 1 NOT NULL,
    created_at      TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6),
    CONSTRAINT pk_organization PRIMARY KEY (id),
    CONSTRAINT ux_organization_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE api_definitions ADD organization_id BIGINT;

ALTER TABLE api_definitions
    ADD CONSTRAINT fk_api_organization
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE SET NULL;

CREATE INDEX idx_api_organization ON api_definitions (organization_id);
