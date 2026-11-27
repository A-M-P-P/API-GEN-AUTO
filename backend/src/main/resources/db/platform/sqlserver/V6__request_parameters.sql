-- Platform migrations: sqlserver (new installations).
ALTER TABLE endpoints ADD request_parameters NVARCHAR(MAX);
