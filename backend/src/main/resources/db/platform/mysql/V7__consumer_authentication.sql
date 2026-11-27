-- Platform migrations: mysql (new installations).
CREATE TABLE consumer_profiles (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 organization_id BIGINT NOT NULL,
 code VARCHAR(60) NOT NULL,
 name VARCHAR(150) NOT NULL,
 active BOOLEAN DEFAULT 0 NOT NULL,
 auth_mode VARCHAR(20) NOT NULL,
 pipeline LONGTEXT,
 api_ids LONGTEXT,
 CONSTRAINT ux_consumer_profile UNIQUE (organization_id, code),
 CONSTRAINT fk_cp_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE consumer_clients (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 profile_id BIGINT NOT NULL,
 client_id VARCHAR(100) NOT NULL,
 secret_hash VARCHAR(100) NOT NULL,
 active BOOLEAN DEFAULT 1 NOT NULL,
 CONSTRAINT ux_consumer_client UNIQUE (profile_id, client_id),
 CONSTRAINT fk_cc_profile FOREIGN KEY (profile_id) REFERENCES consumer_profiles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE consumer_sessions (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 profile_id BIGINT NOT NULL,
 subject VARCHAR(200) NOT NULL,
 client_id BIGINT,
 access_hash VARCHAR(64) NOT NULL UNIQUE,
 refresh_hash VARCHAR(64) NOT NULL UNIQUE,
 access_expires TIMESTAMP(6) NOT NULL,
 refresh_expires TIMESTAMP(6) NOT NULL,
 revoked BOOLEAN DEFAULT 0 NOT NULL,
 api_ids LONGTEXT,
 CONSTRAINT fk_cs_profile FOREIGN KEY (profile_id) REFERENCES consumer_profiles(id) ON DELETE CASCADE,
 CONSTRAINT fk_cs_client FOREIGN KEY (client_id) REFERENCES consumer_clients(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_cs_profile ON consumer_sessions(profile_id);
CREATE INDEX idx_cc_profile ON consumer_clients(profile_id);
