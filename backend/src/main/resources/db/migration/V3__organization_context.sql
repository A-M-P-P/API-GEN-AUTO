-- API-GEN-AUTO - contexte de configuration propre à chaque organisation.
-- Compatible Oracle 12c+ et H2 (mode Oracle).
--
-- JSON : { "variables": {...}, "secrets": {...}, "overrides": { "rest": {...}, "files": {...} } }
-- Les valeurs de "secrets" sont chiffrées par CryptoService avant stockage.

ALTER TABLE organizations ADD context_config CLOB;
