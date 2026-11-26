package com.apigen;

import com.apigen.service.connector.JdbcConnectionProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class ApiGenApplication {

    public static void main(String[] args) {
        // Avant SpringApplication : le pilote Oracle lit ses maps NLS au chargement de sa classe.
        JdbcConnectionProps.configureOracleNls();
        SpringApplication.run(ApiGenApplication.class, args);
    }
}
