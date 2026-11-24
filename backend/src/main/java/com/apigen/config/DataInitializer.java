package com.apigen.config;

import com.apigen.domain.*;
import com.apigen.repository.ApiDefinitionRepository;
import com.apigen.repository.ConnectionRepository;
import com.apigen.repository.EndpointRepository;
import com.apigen.repository.ScheduleRepository;
import com.apigen.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Initialise le compte administrateur et, en développement, un jeu de démonstration.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final ApiDefinitionRepository apiRepository;
    private final EndpointRepository endpointRepository;
    private final ConnectionRepository connectionRepository;
    private final ScheduleRepository scheduleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public DataInitializer(UserRepository userRepository,
                           ApiDefinitionRepository apiRepository,
                           EndpointRepository endpointRepository,
                           ConnectionRepository connectionRepository,
                           ScheduleRepository scheduleRepository,
                           PasswordEncoder passwordEncoder,
                           AppProperties properties) {
        this.userRepository = userRepository;
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

        if (properties.seed().demo() && apiRepository.count() == 0) {
            seedDemo();
        }
    }

    private void seedDemo() {
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
                """
                [
                  { "type": "HTTP", "name": "fetch", "method": "GET", "url": "/posts/1", "connectionId": %d },
                  {
                    "type": "MAP",
                    "name": "shape",
                    "source": "output",
                    "mappings": [
                      { "source": "body.title", "target": "titre" },
                      { "source": "body.body", "target": "contenu" }
                    ]
                  }
                ]
                """.formatted(rest.getId()));

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

        log.info("Jeu de démonstration créé : API « demo » (echo, hello, posts, export).");
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
