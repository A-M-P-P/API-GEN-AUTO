-- Platform migrations: postgresql (new installations).
ALTER TABLE api_definitions DROP CONSTRAINT ux_api_code;

ALTER TABLE api_definitions ADD CONSTRAINT ux_api_code_org UNIQUE (organization_id, code);
