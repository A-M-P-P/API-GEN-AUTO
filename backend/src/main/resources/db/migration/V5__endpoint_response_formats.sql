-- API-GEN-AUTO - formats de réponse et de réception des endpoints.
-- Compatible Oracle 12c+ et H2 (mode Oracle).

ALTER TABLE endpoints ADD response_format VARCHAR(20) DEFAULT 'JSON' NOT NULL;
ALTER TABLE endpoints ADD response_file_name VARCHAR(200);
ALTER TABLE endpoints ADD response_content_type VARCHAR(100);
ALTER TABLE endpoints ADD redirect_location VARCHAR(500);
