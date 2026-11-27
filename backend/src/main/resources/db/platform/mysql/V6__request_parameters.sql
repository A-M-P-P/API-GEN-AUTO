-- Platform migrations: mysql (new installations).
ALTER TABLE endpoints ADD request_parameters LONGTEXT;
