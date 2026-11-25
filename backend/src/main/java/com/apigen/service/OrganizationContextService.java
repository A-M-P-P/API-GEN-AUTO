package com.apigen.service;

import com.apigen.domain.Organization;
import com.apigen.execution.OrganizationContext;
import com.apigen.repository.EndpointRepository;
import com.apigen.repository.OrganizationRepository;
import com.apigen.web.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gestion du contexte de configuration propre à chaque organisation.
 *
 * <p>À l'écriture, les valeurs de {@code secrets} sont chiffrées avec la même clé AES-256-GCM
 * que les identifiants de connexion. À la lecture via l'API d'administration, elles sont
 * masquées ; seules les étapes de pipeline les reçoivent en clair.</p>
 */
@Service
public class OrganizationContextService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationContextService.class);
    private static final String MASK = "********";

    private final OrganizationRepository organizationRepository;
    private final EndpointRepository endpointRepository;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public OrganizationContextService(OrganizationRepository organizationRepository,
                                      EndpointRepository endpointRepository,
                                      CryptoService cryptoService,
                                      ObjectMapper objectMapper) {
        this.organizationRepository = organizationRepository;
        this.endpointRepository = endpointRepository;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    /** Contexte d'exécution d'un endpoint : organisation de l'API, ou contexte vide si non classée. */
    @Transactional(readOnly = true)
    public OrganizationContext forEndpoint(Long endpointId) {
        if (endpointId == null) {
            return OrganizationContext.empty(null);
        }
        Long organizationId = endpointRepository.findOrganizationIdById(endpointId).orElse(null);
        if (organizationId == null) {
            return OrganizationContext.empty(null);
        }
        Object[] row = organizationRepository.findContextById(organizationId).orElse(null);
        if (row == null) {
            return OrganizationContext.empty(null);
        }
        return parseContext(String.valueOf(row[0]), row[1] == null ? null : String.valueOf(row[1]));
    }

    /** Déchiffre les secrets pour les rendre disponibles aux seules étapes de pipeline. */
    public OrganizationContext forOrganization(Organization organization) {
        if (organization == null) {
            return OrganizationContext.empty(null);
        }
        return parseContext(organization.getCode(), organization.getContextConfig());
    }

    private OrganizationContext parseContext(String code, String storedJson) {
        if (storedJson == null || storedJson.isBlank()) {
            return OrganizationContext.empty(code);
        }
        try {
            JsonNode root = parse(storedJson);
            return new OrganizationContext(
                    code,
                    map(root.get("variables")),
                    decryptSecrets(root.get("secrets")),
                    map(root.get("overrides"))
            );
        } catch (RuntimeException e) {
            log.warn("Contexte illisible pour l'organisation {} : {}", code, e.getMessage());
            return OrganizationContext.empty(code);
        }
    }

    /**
     * Valide le JSON fourni, chiffre les secrets et conserve les valeurs masquées lors d'une
     * mise à jour (le studio renvoie le contexte tel qu'il l'a lu).
     */
    public String prepareForStorage(String json, String previousJson) {
        if (json == null || json.isBlank() || "null".equalsIgnoreCase(json.trim())) {
            return null;
        }
        JsonNode root = parse(json);
        if (!root.isObject()) {
            throw ApiException.badRequest("Le contexte de l'organisation doit être un objet JSON");
        }
        ObjectNode node = (ObjectNode) root;
        requireObject(node, "variables");
        requireObject(node, "overrides");
        JsonNode secrets = node.get("secrets");
        if (secrets != null) {
            if (!secrets.isObject()) {
                throw ApiException.badRequest("La section secrets du contexte doit être un objet JSON");
            }
            JsonNode previousSecrets = previousJson == null || previousJson.isBlank()
                    ? null : parse(previousJson).get("secrets");
            ObjectNode stored = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = secrets.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!field.getValue().isValueNode()) {
                    throw ApiException.badRequest("Les secrets doivent être des valeurs simples : " + field.getKey());
                }
                String value = field.getValue().isNull() ? "" : field.getValue().asText();
                if (MASK.equals(value) && previousSecrets != null && previousSecrets.has(field.getKey())) {
                    stored.set(field.getKey(), previousSecrets.get(field.getKey()));
                } else {
                    String encrypted = cryptoService.encrypt(value);
                    if (encrypted == null) {
                        stored.putNull(field.getKey());
                    } else {
                        stored.put(field.getKey(), encrypted);
                    }
                }
            }
            node.set("secrets", stored);
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("Contexte de l'organisation non sérialisable");
        }
    }

    /** Contexte lisible par le studio : les secrets sont remplacés par un masque. */
    public String redact(String storedJson) {
        if (storedJson == null || storedJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = parse(storedJson);
            JsonNode secrets = root.get("secrets");
            if (secrets != null && secrets.isObject()) {
                ObjectNode node = (ObjectNode) secrets;
                node.fieldNames().forEachRemaining(name -> node.put(name, MASK));
            }
            return objectMapper.writeValueAsString(root);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Contexte illisible lors de la lecture : {}", e.getMessage());
            return null;
        }
    }

    private void requireObject(ObjectNode node, String name) {
        JsonNode section = node.get(name);
        if (section != null && !section.isObject() && !section.isNull()) {
            throw ApiException.badRequest("La section " + name + " du contexte doit être un objet JSON");
        }
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw ApiException.badRequest("Contexte de l'organisation invalide : JSON illisible");
        }
    }

    private Map<String, Object> map(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> values = objectMapper.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() {
        });
        return values == null ? Map.of() : values;
    }

    private Map<String, Object> decryptSecrets(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(field -> {
            String stored = field.getValue().isNull() ? null : field.getValue().asText();
            values.put(field.getKey(), cryptoService.decrypt(stored));
        });
        return values;
    }
}
