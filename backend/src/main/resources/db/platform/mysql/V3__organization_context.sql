-- Platform migrations: mysql (new installations).
ALTER TABLE organizations ADD context_config LONGTEXT;
