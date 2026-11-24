package com.apigen.service;

import com.apigen.domain.Connection;
import com.apigen.domain.ConnectionType;
import com.apigen.dto.ConnectionDtos;
import com.apigen.repository.ConnectionRepository;
import com.apigen.security.SecurityUtils;
import com.apigen.service.connector.JdbcPoolManager;
import com.apigen.web.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConnectionService {

    private final ConnectionRepository repository;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;
    private final JdbcPoolManager jdbcPoolManager;

    public ConnectionService(ConnectionRepository repository,
                             CryptoService cryptoService,
                             ObjectMapper objectMapper,
                             SecurityUtils securityUtils,
                             AuditService auditService,
                             JdbcPoolManager jdbcPoolManager) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
        this.securityUtils = securityUtils;
        this.auditService = auditService;
        this.jdbcPoolManager = jdbcPoolManager;
    }

    public record ConnectionSettings(Connection connection, JsonNode baseConfig, Map<String, Object> credentials) {
    }

    @Transactional(readOnly = true)
    public List<ConnectionDtos.ConnectionResponse> list() {
        return repository.findAll().stream().sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ConnectionDtos.ConnectionResponse get(Long id) {
        return toResponse(requireEntity(id));
    }

    @Transactional(readOnly = true)
    public Connection requireEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Connexion introuvable : " + id));
    }

    @Transactional(readOnly = true)
    public ConnectionSettings settings(Long id) {
        return settings(requireEntity(id));
    }

    public ConnectionSettings settings(Connection connection) {
        try {
            JsonNode baseConfig = readJson(connection.getBaseConfig(), objectMapper.createObjectNode());
            String decrypted = cryptoService.decrypt(connection.getCredentials());
            Map<String, Object> credentials = decrypted == null || decrypted.isBlank()
                    ? new LinkedHashMap<>()
                    : objectMapper.readValue(decrypted, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            return new ConnectionSettings(connection, baseConfig, credentials);
        } catch (Exception e) {
            throw new IllegalStateException("Configuration de connexion illisible : " + connection.getName(), e);
        }
    }

    /**
     * Construit les paramètres d'une connexion non enregistrée, à partir d'un formulaire.
     * Les identifiants sont fournis en clair par le client et ne sont jamais persistés.
     */
    public ConnectionSettings inlineSettings(ConnectionType type, JsonNode baseConfig, JsonNode credentials) {
        Map<String, Object> creds = new LinkedHashMap<>();
        if (credentials != null && !credentials.isNull() && !credentials.isEmpty()) {
            creds = objectMapper.convertValue(credentials, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        }
        Connection transientConnection = new Connection();
        transientConnection.setType(type);
        return new ConnectionSettings(transientConnection,
                baseConfig == null || baseConfig.isNull() ? objectMapper.createObjectNode() : baseConfig, creds);
    }

    public ConnectionDtos.ConnectionResponse create(ConnectionDtos.ConnectionRequest request) {
        if (repository.existsByNameIgnoreCase(request.name())) {
            throw ApiException.conflict("Une connexion portant ce nom existe déjà");
        }
        Connection connection = new Connection();
        connection.setName(request.name().trim());
        connection.setType(request.type());
        connection.setDescription(request.description());
        connection.setBaseConfig(writeJson(request.baseConfig()));
        connection.setCredentials(encryptCredentials(request.credentials()));
        connection.setActive(request.active() == null || request.active());
        Connection saved = repository.save(connection);
        auditService.record("CONNECTION_CREATE", "Connection", String.valueOf(saved.getId()), "Création de " + saved.getName());
        return toResponse(saved);
    }

    @Transactional
    public ConnectionDtos.ConnectionResponse update(Long id, ConnectionDtos.ConnectionRequest request) {
        Connection connection = requireEntity(id);
        repository.findByNameIgnoreCase(request.name()).filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.conflict("Une connexion portant ce nom existe déjà");
                });
        connection.setName(request.name().trim());
        connection.setType(request.type());
        connection.setDescription(request.description());
        connection.setBaseConfig(writeJson(request.baseConfig()));
        if (request.credentials() != null) {
            connection.setCredentials(encryptCredentials(request.credentials()));
        }
        if (request.active() != null) {
            connection.setActive(request.active());
        }
        connection.touch();
        Connection saved = repository.save(connection);
        // Les paramètres ont pu changer : on ferme le pool existant, il sera recréé au prochain usage.
        jdbcPoolManager.evict("conn:" + saved.getId());
        auditService.record("CONNECTION_UPDATE", "Connection", String.valueOf(saved.getId()), "Mise à jour de " + saved.getName());
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Connection connection = requireEntity(id);
        repository.delete(connection);
        jdbcPoolManager.evict("conn:" + id);
        auditService.record("CONNECTION_DELETE", "Connection", String.valueOf(id), "Suppression de " + connection.getName());
    }

    public ConnectionDtos.ConnectionTestResponse test(Long id) {
        Connection connection = requireEntity(id);
        return doTest(new ConnectionDtos.ConnectionTestRequest(connection.getType(),
                readJson(connection.getBaseConfig(), objectMapper.createObjectNode()),
                objectMapper.valueToTree(settings(connection).credentials())));
    }

    public ConnectionDtos.ConnectionTestResponse test(ConnectionDtos.ConnectionTestRequest request) {
        return doTest(request);
    }

    private ConnectionDtos.ConnectionTestResponse doTest(ConnectionDtos.ConnectionTestRequest request) {
        long start = System.nanoTime();
        String message;
        boolean ok;
        try {
            switch (request.type()) {
                case REST -> {
                    String baseUrl = text(request.baseConfig(), "baseUrl");
                    if (baseUrl == null || baseUrl.isBlank()) {
                        throw new IllegalArgumentException("baseUrl est requis");
                    }
                    RestClient.create().get().uri(baseUrl).exchange((req, res) -> res.getStatusCode().value(), true);
                    message = "Connexion HTTP établie vers " + baseUrl;
                }
                case ORACLE, POSTGRESQL, MYSQL, SQLSERVER -> {
                    String url = text(request.baseConfig(), "jdbcUrl");
                    if (url == null || url.isBlank()) {
                        throw new IllegalArgumentException("jdbcUrl est requis");
                    }
                    String user = text(request.credentials(), "username");
                    String password = text(request.credentials(), "password");
                    // Test en connexion directe, avec un délai court : on veut une réponse
                    // rapide même quand l'hôte ne répond pas (le pool, lui, attend son
                    // connectionTimeout de 10 s et retente, ce qui est trop lent pour un bouton).
                    int previousTimeout = DriverManager.getLoginTimeout();
                    DriverManager.setLoginTimeout(5);
                    try (var connection = DriverManager.getConnection(url, user, password)) {
                        ok = connection.isValid(5);
                        message = ok ? "Connexion base de données établie" : "Base de données inaccessible";
                    } finally {
                        DriverManager.setLoginTimeout(previousTimeout);
                    }
                }
                case FILE -> {
                    String basePath = text(request.baseConfig(), "basePath");
                    Path path = Path.of(basePath == null || basePath.isBlank() ? "./data/exchanges" : basePath);
                    Files.createDirectories(path);
                    message = "Répertoire accessible : " + path.toAbsolutePath();
                }
                default -> message = "Test non implémenté pour ce type de connexion";
            }
            ok = true;
        } catch (Exception e) {
            ok = false;
            message = rootMessage(e);
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return new ConnectionDtos.ConnectionTestResponse(ok, message, latencyMs);
    }

    private ConnectionDtos.ConnectionResponse toResponse(Connection connection) {
        return new ConnectionDtos.ConnectionResponse(
                connection.getId(),
                connection.getName(),
                connection.getType(),
                connection.getDescription(),
                readJson(connection.getBaseConfig(), objectMapper.createObjectNode()),
                connection.getCredentials() != null && !connection.getCredentials().isBlank(),
                connection.isActive(),
                connection.getCreatedAt(),
                connection.getUpdatedAt()
        );
    }

    private JsonNode readJson(String raw, JsonNode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return fallback;
        }
    }

    private String writeJson(JsonNode node) {
        if (node == null || node.isNull() || node.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw ApiException.badRequest("JSON de configuration invalide");
        }
    }

    private String encryptCredentials(JsonNode credentials) {
        if (credentials == null || credentials.isNull() || credentials.isEmpty()) {
            return null;
        }
        try {
            return cryptoService.encrypt(objectMapper.writeValueAsString(credentials));
        } catch (Exception e) {
            throw ApiException.badRequest("JSON des identifiants invalide");
        }
    }

    private String text(JsonNode node, String field) {
        return node == null || !node.hasNonNull(field) ? null : node.get(field).asText();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null ? current.getClass().getSimpleName() : message;
    }
}
