-- Platform migrations: mysql (new installations).
ALTER TABLE api_definitions DROP INDEX ux_api_code;

ALTER TABLE api_definitions ADD CONSTRAINT ux_api_code_org UNIQUE (organization_id, code);
