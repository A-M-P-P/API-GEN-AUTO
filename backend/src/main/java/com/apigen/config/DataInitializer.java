package com.apigen.config;

import com.apigen.domain.*;
import com.apigen.repository.ApiDefinitionRepository;
import com.apigen.repository.ConnectionRepository;
import com.apigen.repository.OrganizationRepository;
import com.apigen.repository.EndpointRepository;
import com.apigen.repository.ScheduleRepository;
import com.apigen.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Initialise le compte administrateur et, en développement, un jeu de démonstration.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final Pattern CONNECTION_ID = Pattern.compile("\"connectionId\"\\s*:\\s*(\\d+)");

    private static final String DEMO_POSTS_PIPELINE = """
            [
              { "type": "HTTP", "name": "fetch", "method": "GET", "url": "/posts/1",
                "connectionId": %d, "failOnError": false },
              {
                "type": "MAP",
                "name": "shape",
                "source": "output",
                "mappings": [
                  { "source": "body.title", "target": "titre", "default": "Titre de démonstration" },
                  { "source": "body.body", "target": "contenu", "default": "Exemple affiché hors connexion" }
                ]
              }
            ]
            """;

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final ApiDefinitionRepository apiRepository;
    private final EndpointRepository endpointRepository;
    private final ConnectionRepository connectionRepository;
    private final ScheduleRepository scheduleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public DataInitializer(UserRepository userRepository,
                           OrganizationRepository organizationRepository,
                           ApiDefinitionRepository apiRepository,
                           EndpointRepository endpointRepository,
                           ConnectionRepository connectionRepository,
                           ScheduleRepository scheduleRepository,
                           PasswordEncoder passwordEncoder,
                           AppProperties properties) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.apiRepository = apiRepository;
        this.endpointRepository = endpointRepository;
        this.connectionRepository = connectionRepository;
        this.scheduleRepository = scheduleRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            AppUser admin = new AppUser();
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode("ChangeMe123!"));
            admin.setFullName("Administrateur");
            admin.setRole(Role.ADMIN);
            admin.setEnabled(true);
            userRepository.save(admin);
            log.warn("Compte admin créé : admin / ChangeMe123! — changez ce mot de passe en production.");
        }

        Organization defaultOrganization = ensureDefaultOrganization();

        if (properties.seed().demo() && apiRepository.count() == 0) {
            seedDemo(defaultOrganization);
        }
        // Les installations déjà créées conservent leurs données : on met à jour les endpoints
        // de la démo qui utilisaient l'ancien pipeline sans valeur de repli.
        patchDemoEndpoints();
    }

    /** Organisation de rassemblement des API non affectées à une organisation nominative. */
    private Organization ensureDefaultOrganization() {
        if (organizationRepository.count() > 0) {
            return organizationRepository.findAll().stream()
                    .filter(Organization::isActive)
                    .findFirst()
                    .orElse(null);
        }
        Organization organization = new Organization();
        organization.setCode("default");
        organization.setName("Organisation par défaut");
        organization.setDescription("Regroupe les API qui ne sont rattachées à aucune organisation nominative.");
        organization.setActive(true);
        Organization saved = organizationRepository.save(organization);
        log.info("Organisation par défaut créée.");

        // Lors de la montée de version, les API existantes n'ont pas d'organisation :
        // on les range dans l'organisation par défaut plutôt que de les laisser non classées.
        for (ApiDefinition api : apiRepository.findAll()) {
            if (api.getOrganization() == null) {
                api.setOrganization(saved);
                apiRepository.save(api);
            }
        }
        return saved;
    }

    private void seedDemo(Organization organization) {
        Connection rest = new Connection();
        rest.setName("JSONPlaceholder");
        rest.setType(ConnectionType.REST);
        rest.setDescription("Service public de démonstration pour les appels REST sortants.");
        rest.setBaseConfig("{\"baseUrl\":\"https://jsonplaceholder.typicode.com\",\"authType\":\"NONE\"}");
        rest.setActive(true);
        rest = connectionRepository.save(rest);

        Connection files = new Connection();
        files.setName("Fichiers d'échange");
        files.setType(ConnectionType.FILE);
        files.setDescription("Répertoire local des échanges CSV/JSON/XLSX.");
        files.setBaseConfig("{\"basePath\":\"./data/exchanges\"}");
        files.setActive(true);
        files = connectionRepository.save(files);

        ApiDefinition api = new ApiDefinition();
        api.setCode("demo");
        api.setName("API de démonstration");
        api.setDescription("Exemples d'API configurables : écho, salutation, appel REST sortant et écriture de fichier.");
        api.setVersion("1.0");
        api.setStatus(ApiStatus.ACTIVE);
        api.setInboundAuth(InboundAuth.NONE);
        api.setCreatedBy("system");
        api.setOrganization(organization);
        api = apiRepository.save(api);

        createEndpoint(api, "Écho", "POST", "/echo", Direction.INBOUND,
                "Répond le message reçu en majuscules.",
                """
                [
                  {
                    "type": "MAP",
                    "name": "echo",
                    "source": "request.body",
                    "mappings": [
                      { "source": "message", "target": "echo", "transform": "UPPER", "default": "hello" },
                      { "source": "", "target": "receivedVia", "default": "{{request.method}}" }
                    ]
                  }
                ]
                """);

        createEndpoint(api, "Salutation", "GET", "/hello", Direction.INBOUND,
                "Salue la personne passée en paramètre ?name=...",
                """
                [
                  {
                    "type": "MAP",
                    "name": "greeting",
                    "source": "request.query",
                    "mappings": [
                      { "source": "name", "target": "greeting", "transform": "CONCAT", "params": { "prefix": "Bonjour " }, "default": "monde" }
                    ]
                  }
                ]
                """);

        Endpoint posts = createEndpoint(api, "Dernier article", "GET", "/posts", Direction.OUTBOUND,
                "Récupère un article depuis JSONPlaceholder et le remappe.",
                DEMO_POSTS_PIPELINE.formatted(rest.getId()));

        Endpoint export = createEndpoint(api, "Export clients", "POST", "/export/clients", Direction.OUTBOUND,
                "Écrit les données reçues dans un fichier CSV.",
                """
                [
                  {
                    "type": "FILE_WRITE",
                    "name": "export",
                    "connectionId": %d,
                    "path": "exports/clients.csv",
                    "format": "CSV",
                    "data": "payload"
                  }
                ]
                """.formatted(files.getId()));

        createSchedule(posts, "0 */5 * * * *", false);
        createSchedule(export, "0 0 2 * * *", false);

        log.info("Jeu de démonstration créé : API « demo » (echo, hello, posts, export) dans l'organisation « {} ».",
                organization != null ? organization.getCode() : "default");
    }

    /** Rend le pipeline « Dernier article » compatible hors ligne sur les bases déjà initialisées. */
    private void patchDemoEndpoints() {
        for (ApiDefinition api : apiRepository.findAll()) {
            if (!"demo".equalsIgnoreCase(api.getCode())) {
                continue;
            }
            for (Endpoint endpoint : endpointRepository.findByApiIdWithSchedule(api.getId())) {
                String pipeline = endpoint.getPipeline();
                if (!"Dernier article".equals(endpoint.getName())
                        || pipeline == null
                        || !pipeline.contains("/posts/1")
                        || pipeline.contains("failOnError")) {
                    continue;
                }
                Matcher matcher = CONNECTION_ID.matcher(pipeline);
                if (!matcher.find()) {
                    continue;
                }
                endpoint.setPipeline(DEMO_POSTS_PIPELINE.formatted(matcher.group(1)));
                endpointRepository.save(endpoint);
                log.info("Endpoint de démonstration « Dernier article » mis à jour (mode hors ligne).");
            }
        }
    }

    private Endpoint createEndpoint(ApiDefinition api, String name, String method, String path,
                                    Direction direction, String description, String pipeline) {
        Endpoint endpoint = new Endpoint();
        endpoint.setApi(api);
        endpoint.setName(name);
        endpoint.setMethod(method);
        endpoint.setPath(path);
        endpoint.setDirection(direction);
        endpoint.setDescription(description);
        endpoint.setPipeline(pipeline);
        endpoint.setActive(true);
        return endpointRepository.save(endpoint);
    }

    private void createSchedule(Endpoint endpoint, String cron, boolean enabled) {
        Schedule schedule = new Schedule();
        schedule.setEndpoint(endpoint);
        schedule.setCronExpression(cron);
        schedule.setEnabled(enabled);
        schedule.setNextRunAt(enabled ? java.time.Instant.now().plusSeconds(300) : null);
        schedule.setPayload("{}");
        scheduleRepository.save(schedule);
    }
}
