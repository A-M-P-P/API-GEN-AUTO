-- Platform migrations: sqlserver (new installations).
CREATE TABLE consumer_profiles (
 id BIGINT IDENTITY(1,1) PRIMARY KEY,
 organization_id BIGINT NOT NULL,
 code NVARCHAR(60) NOT NULL,
 name NVARCHAR(150) NOT NULL,
 active BIT DEFAULT 0 NOT NULL,
 auth_mode NVARCHAR(20) NOT NULL,
 pipeline NVARCHAR(MAX),
 api_ids NVARCHAR(MAX),
 CONSTRAINT ux_consumer_profile UNIQUE (organization_id, code),
 CONSTRAINT fk_cp_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
);
CREATE TABLE consumer_clients (
 id BIGINT IDENTITY(1,1) PRIMARY KEY,
 profile_id BIGINT NOT NULL,
 client_id NVARCHAR(100) NOT NULL,
 secret_hash NVARCHAR(100) NOT NULL,
 active BIT DEFAULT 1 NOT NULL,
 CONSTRAINT ux_consumer_client UNIQUE (profile_id, client_id),
 CONSTRAINT fk_cc_profile FOREIGN KEY (profile_id) REFERENCES consumer_profiles(id) ON DELETE CASCADE
);
CREATE TABLE consumer_sessions (
 id BIGINT IDENTITY(1,1) PRIMARY KEY,
 profile_id BIGINT NOT NULL,
 subject NVARCHAR(200) NOT NULL,
 client_id BIGINT,
 access_hash NVARCHAR(64) NOT NULL UNIQUE,
 refresh_hash NVARCHAR(64) NOT NULL UNIQUE,
 access_expires DATETIME2(6) NOT NULL,
 refresh_expires DATETIME2(6) NOT NULL,
 revoked BIT DEFAULT 0 NOT NULL,
 api_ids NVARCHAR(MAX),
 CONSTRAINT fk_cs_profile FOREIGN KEY (profile_id) REFERENCES consumer_profiles(id) ON DELETE CASCADE,
 CONSTRAINT fk_cs_client FOREIGN KEY (client_id) REFERENCES consumer_clients(id)
);
CREATE INDEX idx_cs_profile ON consumer_sessions(profile_id);
CREATE INDEX idx_cc_profile ON consumer_clients(profile_id);
