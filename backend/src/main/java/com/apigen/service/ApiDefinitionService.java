package com.apigen.service;

import com.apigen.domain.ApiDefinition;
import com.apigen.domain.ApiStatus;
import com.apigen.domain.Endpoint;
import com.apigen.dto.ApiDtos;
import com.apigen.repository.ApiDefinitionRepository;
import com.apigen.security.ApiKeyService;
import com.apigen.security.SecurityUtils;
import com.apigen.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class ApiDefinitionService {

    private final ApiDefinitionRepository repository;
    private final EndpointMapper endpointMapper;
    private final ApiKeyService apiKeyService;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    public ApiDefinitionService(ApiDefinitionRepository repository,
                                EndpointMapper endpointMapper,
                                ApiKeyService apiKeyService,
                                SecurityUtils securityUtils,
                                AuditService auditService) {
        this.repository = repository;
        this.endpointMapper = endpointMapper;
        this.apiKeyService = apiKeyService;
        this.securityUtils = securityUtils;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.ApiSummary> list() {
        return repository.findSummaries().stream()
                .map(this::toSummary)
                .sorted(Comparator.comparing(ApiDtos.ApiSummary::updatedAt).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public ApiDtos.ApiDetail get(Long id) {
        ApiDefinition api = requireEntity(id);
        return toDetail(api);
    }

    @Transactional(readOnly = true)
    public ApiDefinition requireEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("API introuvable : " + id));
    }

    @Transactional
    public ApiDtos.ApiDetail create(ApiDtos.ApiCreateRequest request) {
        if (repository.existsByCodeIgnoreCase(request.code())) {
            throw ApiException.conflict("Une API avec ce code existe déjà");
        }
        ApiDefinition api = new ApiDefinition();
        api.setCode(request.code().toLowerCase());
        api.setName(request.name().trim());
        api.setDescription(request.description());
        api.setVersion(request.version() == null || request.version().isBlank() ? "1.0" : request.version());
        api.setStatus(request.status() == null ? ApiStatus.DRAFT : request.status());
        api.setInboundAuth(request.inboundAuth() == null ? com.apigen.domain.InboundAuth.NONE : request.inboundAuth());
        api.setCreatedBy(securityUtils.currentUsername());
        ApiDefinition saved = repository.save(api);
        auditService.record("API_CREATE", "ApiDefinition", String.valueOf(saved.getId()), "Création de l'API " + saved.getCode());
        return toDetail(saved);
    }

    @Transactional
    public ApiDtos.ApiDetail update(Long id, ApiDtos.ApiUpdateRequest request) {
        ApiDefinition api = requireEntity(id);
        api.setName(request.name().trim());
        api.setDescription(request.description());
        if (request.version() != null && !request.version().isBlank()) {
            api.setVersion(request.version());
        }
        if (request.status() != null) {
            api.setStatus(request.status());
        }
        if (request.inboundAuth() != null) {
            api.setInboundAuth(request.inboundAuth());
            if (request.inboundAuth() != com.apigen.domain.InboundAuth.API_KEY) {
                api.setApiKeyHash(null);
            }
        }
        api.touch();
        ApiDefinition saved = repository.save(api);
        auditService.record("API_UPDATE", "ApiDefinition", String.valueOf(saved.getId()), "Mise à jour de l'API " + saved.getCode());
        return toDetail(saved);
    }

    @Transactional
    public void delete(Long id) {
        ApiDefinition api = requireEntity(id);
        repository.delete(api);
        auditService.record("API_DELETE", "ApiDefinition", String.valueOf(id), "Suppression de l'API " + api.getCode());
    }

    @Transactional
    public ApiDtos.ApiKeyResponse generateApiKey(Long id) {
        ApiDefinition api = requireEntity(id);
        String rawKey = apiKeyService.generate();
        api.setApiKeyHash(apiKeyService.hash(rawKey));
        api.setInboundAuth(com.apigen.domain.InboundAuth.API_KEY);
        api.touch();
        repository.save(api);
        auditService.record("API_KEY_GENERATE", "ApiDefinition", String.valueOf(id), "Régénération de la clé API de " + api.getCode());
        return new ApiDtos.ApiKeyResponse(id, api.getCode(), rawKey, api.getInboundAuth());
    }

    @Transactional
    public void revokeApiKey(Long id) {
        ApiDefinition api = requireEntity(id);
        api.setApiKeyHash(null);
        if (api.getInboundAuth() == com.apigen.domain.InboundAuth.API_KEY) {
            api.setInboundAuth(com.apigen.domain.InboundAuth.NONE);
        }
        api.touch();
        repository.save(api);
        auditService.record("API_KEY_REVOKE", "ApiDefinition", String.valueOf(id), "Révocation de la clé API de " + api.getCode());
    }

    private ApiDtos.ApiSummary toSummary(Object[] row) {
        return new ApiDtos.ApiSummary(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (String) row[4],
                ApiStatus.valueOf(String.valueOf(row[5])),
                com.apigen.domain.InboundAuth.valueOf(String.valueOf(row[6])),
                (String) row[7],
                instant(row[8]),
                instant(row[9]),
                ((Number) row[10]).longValue()
        );
    }

    private java.time.Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.time.Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return java.time.Instant.parse(value.toString());
    }

    private ApiDtos.ApiDetail toDetail(ApiDefinition api) {
        List<Endpoint> endpoints = api.getEndpoints().stream()
                .sorted(Comparator.comparing(Endpoint::getId))
                .toList();
        return new ApiDtos.ApiDetail(
                api.getId(),
                api.getCode(),
                api.getName(),
                api.getDescription(),
                api.getVersion(),
                api.getStatus(),
                api.getInboundAuth(),
                api.getCreatedBy(),
                api.getCreatedAt(),
                api.getUpdatedAt(),
                endpoints.stream().map(endpointMapper::toSummary).toList()
        );
    }
}
