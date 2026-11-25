-- API-GEN-AUTO - le code d'API devient local à chaque organisation.
-- Compatible Oracle 12c+ et H2 (mode Oracle).
--
-- Le code reste unique dans une organisation. Les API non classées (organization_id NULL)
-- sont contrôlées côté application, la contrainte SQL ne portant que sur les API rattachées.

ALTER TABLE api_definitions DROP CONSTRAINT ux_api_code;

ALTER TABLE api_definitions ADD CONSTRAINT ux_api_code_org UNIQUE (organization_id, code);
