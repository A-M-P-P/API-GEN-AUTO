package com.apigen.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Charge un fichier de configuration externe et lui donne la plus haute priorité
 * après les arguments de ligne de commande.
 *
 * <p>C'est le mécanisme qui permet de changer une adresse, un port ou un secret
 * <strong>sans reconstruire le WAR</strong> : on édite le fichier puis on redémarre
 * le serveur. Le chemin se règle par {@code APIGEN_RUNTIME_CONFIG}.</p>
 *
 * <p>Ordre de priorité résultant :
 * {@code ligne de commande > runtime.properties > variables d'environnement > application.yml}</p>
 */
@Component
public class RuntimeConfigEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RuntimeConfigEnvironmentPostProcessor.class);
    static final String PROPERTY_SOURCE_NAME = "apigen-runtime-config";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String location = environment.getProperty("apigen.runtime-config", "./config/runtime.properties");
        Path path = Path.of(location);
        if (!Files.isRegularFile(path)) {
            log.info("Aucune configuration externe à {} (utilisez APIGEN_RUNTIME_CONFIG pour en désigner une)",
                    path.toAbsolutePath());
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException e) {
            log.warn("Configuration externe illisible : {} ({})", path.toAbsolutePath(), e.getMessage());
            return;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            values.put(name, properties.getProperty(name));
        }
        PropertySource<?> source = new MapPropertySource(PROPERTY_SOURCE_NAME, values);
        if (environment.getPropertySources().contains("commandLineArgs")) {
            environment.getPropertySources().addAfter("commandLineArgs", source);
        } else {
            environment.getPropertySources().addFirst(source);
        }
        log.info("Configuration externe chargée : {} ({} propriétés)", path.toAbsolutePath(), values.size());
    }

    @Override
    public int getOrder() {
        // Après ConfigDataEnvironmentPostProcessor, pour que apigen.runtime-config
        // puisse lui-même être défini dans application.yml.
        return Ordered.LOWEST_PRECEDENCE;
    }
}
