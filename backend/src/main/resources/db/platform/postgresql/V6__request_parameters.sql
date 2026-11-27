-- Platform migrations: postgresql (new installations).
ALTER TABLE endpoints ADD request_parameters TEXT;
