-- Platform migrations: sqlserver (new installations).
ALTER TABLE endpoints ADD response_format NVARCHAR(20) DEFAULT 'JSON' NOT NULL;
ALTER TABLE endpoints ADD response_file_name NVARCHAR(200);
ALTER TABLE endpoints ADD response_content_type NVARCHAR(100);
ALTER TABLE endpoints ADD redirect_location NVARCHAR(500);
