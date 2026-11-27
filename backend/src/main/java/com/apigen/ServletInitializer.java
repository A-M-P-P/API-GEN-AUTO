package com.apigen;

import com.apigen.service.connector.JdbcConnectionProps;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Point d'entrée pour un déploiement WAR dans Tomcat 10.1+ (Jakarta EE).
 * La classe {@code ApiGenApplication.main()} n'est pas appelée par le conteneur :
 * on y applique les mêmes réglages NLS Oracle avant de démarrer Spring.
 */
public class ServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        JdbcConnectionProps.configureOracleNls();
        return application.sources(com.apigensetup.SetupMode.enabled()
                ? com.apigensetup.SetupApplication.class : ApiGenApplication.class);
    }
}
