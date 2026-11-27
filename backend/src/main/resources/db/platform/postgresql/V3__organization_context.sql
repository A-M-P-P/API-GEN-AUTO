-- Platform migrations: postgresql (new installations).
ALTER TABLE organizations ADD context_config TEXT;
