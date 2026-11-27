-- Platform migrations: sqlserver (new installations).
ALTER TABLE organizations ADD context_config NVARCHAR(MAX);
