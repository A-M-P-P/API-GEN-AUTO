package com.apigen.service;

import com.apigen.dto.ConfigDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Lecture et écriture de la configuration externe.
 *
 * <p>L'écriture ne modifie jamais l'artefact déployé : elle alimente le fichier
 * {@code runtime.properties} chargé par
 * {@link com.apigen.config.RuntimeConfigEnvironmentPostProcessor} au démarrage suivant.</p>
 */
@Service
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);
    private static final String MASK = "••••••••";

    /** Propriété éditable, avec son libellé et son aide. */
    private record EntrySpec(String key, String label, boolean secret, boolean editable, String hint) {
    }

    private static final List<EntrySpec> CATALOG = List.of(
            new EntrySpec("spring.datasource.url", "URL de la base de données", false, true,
                    "Ex. jdbc:oracle:thin:@//hote:1521/SERVICE"),
            new EntrySpec("spring.datasource.username", "Utilisateur de la base", false, true,
                    "Compte utilisé par la plateforme elle-même."),
            new EntrySpec("spring.datasource.password", "Mot de passe de la base", true, true,
                    "Stocké en clair dans le fichier externe : restreignez ses droits."),
            new EntrySpec("server.port", "Port d'écoute", false, true,
                    "Ignorée sur WebLogic : le port est celui du serveur WebLogic."),
            new EntrySpec("server.servlet.context-path", "Chemin de contexte", false, true,
                    "Ex. /apigen — l'API devient /apigen/api/..."),
            new EntrySpec("apigen.jwt.secret", "Secret JWT", true, true,
                    "Au moins 64 caractères aléatoires."),
            new EntrySpec("apigen.jwt.expiration-hours", "Durée de vie des jetons (heures)", false, true,
                    "Durée avant déconnexion du studio."),
            new EntrySpec("apigen.crypto.key", "Clé de chiffrement", true, true,
                    "Chiffre les identifiants des connexions (AES-256-GCM)."),
            new EntrySpec("apigen.cors.allowed-origins", "Origines CORS autorisées", false, true,
                    "Séparées par des virgules."),
            new EntrySpec("apigen.scheduler.enabled", "Planificateur actif", false, true,
                    "true ou false."),
            new EntrySpec("apigen.scheduler.poll-interval-ms", "Fréquence de scrutation (ms)", false, true,
                    "Intervalle entre deux vérifications des tâches planifiées."),
            new EntrySpec("apigen.audit.retention-days", "Rétention d'audit (jours)", false, true,
                    "Durée de conservation du journal d'audit."),
            new EntrySpec("apigen.files.base-dir", "Répertoire des échanges", false, true,
                    "Racine des lectures et écritures de fichiers."),
            new EntrySpec("apigen.seed.demo", "Jeu de démonstration", false, true,
                    "false en production.")
    );

    private final ConfigurableEnvironment environment;

    public ConfigService(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    public ConfigDtos.ConfigView view() {
        List<ConfigDtos.ConfigEntry> entries = new ArrayList<>();
        for (EntrySpec spec : CATALOG) {
            String raw = environment.getProperty(spec.key());
            boolean masked = spec.secret() && raw != null && !raw.isBlank();
            entries.add(new ConfigDtos.ConfigEntry(
                    spec.key(),
                    spec.label(),
                    masked ? MASK : (raw == null ? "" : raw),
                    masked,
                    spec.secret(),
                    spec.editable(),
                    sourceOf(spec.key()),
                    spec.hint()
            ));
        }
        Path path = runtimePath();
        return new ConfigDtos.ConfigView(
                path.toAbsolutePath().toString(),
                Files.isRegularFile(path),
                String.join(",", environment.getActiveProfiles()),
                environment.getProperty("server.port", "8080"),
                environment.getProperty("server.servlet.context-path", "/"),
                Files.isRegularFile(path),
                entries
        );
    }

    public ConfigDtos.ConfigUpdateResponse update(ConfigDtos.ConfigUpdateRequest request) {
        Set<String> editable = new LinkedHashSet<>();
        for (EntrySpec spec : CATALOG) {
            editable.add(spec.key());
        }
        Map<String, String> values = request.values() == null ? Map.of() : request.values();

        Map<String, String> current = readCurrentFile();
        List<String> changed = new ArrayList<>();
        List<String> ignored = new ArrayList<>();

        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (!editable.contains(key)) {
                ignored.add(key);
                continue;
            }
            // Un champ masqué renvoyé tel quel signifie « ne pas changer ».
            if (MASK.equals(value)) {
                continue;
            }
            String previous = current.get(key);
            if (value.isEmpty()) {
                if (previous != null) {
                    current.remove(key);
                    changed.add(key);
                } else {
                    ignored.add(key);
                }
                continue;
            }
            if (value.equals(previous)) {
                ignored.add(key);
                continue;
            }
            current.put(key, value);
            changed.add(key);
        }

        if (changed.isEmpty()) {
            return new ConfigDtos.ConfigUpdateResponse(List.of(), ignored,
                    runtimePath().toAbsolutePath().toString(), false);
        }
        write(current);
        log.info("Configuration externe mise à jour : {}", changed);
        return new ConfigDtos.ConfigUpdateResponse(changed, ignored,
                runtimePath().toAbsolutePath().toString(), true);
    }

    private Map<String, String> readCurrentFile() {
        Map<String, String> values = new LinkedHashMap<>();
        Path path = runtimePath();
        if (!Files.isRegularFile(path)) {
            return values;
        }
        try {
            Properties properties = new Properties();
            try (var input = Files.newInputStream(path)) {
                properties.load(input);
            }
            for (String name : properties.stringPropertyNames()) {
                values.put(name, properties.getProperty(name));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Configuration externe illisible : " + e.getMessage(), e);
        }
        return values;
    }

    private void write(Map<String, String> values) {
        Path path = runtimePath();
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            List<String> lines = new ArrayList<>();
            lines.add("# Configuration externe API-GEN-AUTO");
            lines.add("# Chargee au demarrage prioritairement sur application.yml.");
            lines.add("# Genere par le studio : ne pas editer pendant que l'application tourne.");
            lines.add("");
            for (Map.Entry<String, String> entry : values.entrySet()) {
                lines.add(entry.getKey() + "=" + entry.getValue());
            }
            Files.write(path, lines);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Ecriture impossible dans " + path.toAbsolutePath() + " : " + e.getMessage(), e);
        }
    }

    private Path runtimePath() {
        return Path.of(environment.getProperty("apigen.runtime-config", "./config/runtime.properties"));
    }

    /** Nom lisible de la source qui fournit réellement la valeur. */
    private String sourceOf(String key) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            String name = source.getName();
            // Spring Boot attache un agrégat "configurationProperties" qui contient toutes
            // les propriétés : il masquerait l'origine réelle si on s'arrêtait à lui.
            if ("configurationProperties".equals(name)) {
                continue;
            }
            if (source.containsProperty(key)) {
                return label(name);
            }
        }
        return "Valeur par défaut";
    }

    private String label(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("runtime-config")) {
            return "Fichier externe";
        }
        if (lower.contains("commandline")) {
            return "Ligne de commande";
        }
        if (lower.contains("systemenvironment")) {
            return "Variable d'environnement";
        }
        if (lower.contains("systemproperties")) {
            return "Propriété système";
        }
        if (lower.contains("application")) {
            return "application.yml";
        }
        return name;
    }
}
