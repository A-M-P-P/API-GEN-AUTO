package com.apigen.config;

import com.apigen.dto.ConnectionDtos;
import com.apigen.repository.ConnectionRepository;
import com.apigen.service.ConnectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Crée au démarrage les connexions (sources de données des pipelines) déclarées dans un
 * fichier JSON externe. Utile quand le studio n'est pas encore accessible : les connexions
 * n'ont pas besoin d'être saisies à la main après le premier login.
 *
 * Emplacement par défaut : ./config/connections.json
 * Surcharge : APIGEN_SEED_CONNECTIONS_FILE=/chemin/connections.json
 * Le fichier est idempotent : une connexion existante n'est jamais écrasée.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class BootstrapConnectionsRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapConnectionsRunner.class);

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final ConnectionRepository connectionRepository;
    private final ConnectionService connectionService;

    public BootstrapConnectionsRunner(Environment environment,
                                      ObjectMapper objectMapper,
                                      ConnectionRepository connectionRepository,
                                      ConnectionService connectionService) {
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.connectionRepository = connectionRepository;
        this.connectionService = connectionService;
    }

    @Override
    public void run(String... args) {
        String location = environment.getProperty("apigen.seed.connections-file", "./config/connections.json");
        Path path = Path.of(location);
        if (!Files.isRegularFile(path)) {
            log.info("Aucun fichier de connexions initiales à {} (facultatif).", path.toAbsolutePath());
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(path.toFile());
            if (!root.isArray()) {
                log.warn("Le fichier {} doit contenir un tableau JSON de connexions.", path.toAbsolutePath());
                return;
            }
            int created = 0;
            int skipped = 0;
            for (JsonNode node : root) {
                String name = node.path("name").asText("").trim();
                if (name.isEmpty() || connectionRepository.existsByNameIgnoreCase(name)) {
                    skipped++;
                    continue;
                }
                ConnectionDtos.ConnectionRequest request =
                        objectMapper.treeToValue(node, ConnectionDtos.ConnectionRequest.class);
                connectionService.create(request);
                created++;
                log.info("Connexion initiale créée : {} ({})", name, request.type());
            }
            log.info("Connexions initiales : {} créée(s), {} ignorée(s) depuis {}.",
                    created, skipped, path.toAbsolutePath());
        } catch (Exception e) {
            // Comme le jeu de démonstration, une erreur ici ne doit pas empêcher le démarrage.
            log.warn("Connexions initiales non chargées depuis {} : {}",
                    path.toAbsolutePath(), e.getMessage());
        }
    }
}
