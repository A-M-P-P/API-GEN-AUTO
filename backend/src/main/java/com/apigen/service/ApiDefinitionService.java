package com.apigen.service;

import com.apigen.domain.ApiDefinition;
import com.apigen.domain.ApiStatus;
import com.apigen.domain.Endpoint;
import com.apigen.domain.Organization;
import com.apigen.dto.ApiDtos;
import com.apigen.repository.ApiDefinitionRepository;
import com.apigen.repository.OrganizationRepository;
import com.apigen.security.ApiKeyService;
import com.apigen.security.SecurityUtils;
import com.apigen.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class ApiDefinitionService {

    private final ApiDefinitionRepository repository;
    private final EndpointMapper endpointMapper;
    private final ApiKeyService apiKeyService;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;
    private final OrganizationRepository organizationRepository;

    public ApiDefinitionService(ApiDefinitionRepository repository,
                                EndpointMapper endpointMapper,
                                ApiKeyService apiKeyService,
                                SecurityUtils securityUtils,
                                AuditService auditService,
                                OrganizationRepository organizationRepository) {
        this.repository = repository;
        this.endpointMapper = endpointMapper;
        this.apiKeyService = apiKeyService;
        this.securityUtils = securityUtils;
        this.auditService = auditService;
        this.organizationRepository = organizationRepository;
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
        Organization organization = resolveOrganization(request.organizationId());
        String code = request.code().trim().toLowerCase(Locale.ROOT);
        ensureCodeAvailable(code, organization == null ? null : organization.getId());
        ApiDefinition api = new ApiDefinition();
        api.setCode(code);
        api.setName(request.name().trim());
        api.setDescription(request.description());
        api.setVersion(request.version() == null || request.version().isBlank() ? "1.0" : request.version());
        api.setStatus(request.status() == null ? ApiStatus.DRAFT : request.status());
        api.setInboundAuth(request.inboundAuth() == null ? com.apigen.domain.InboundAuth.NONE : request.inboundAuth());
        api.setCreatedBy(securityUtils.currentUsername());
        api.setOrganization(organization);
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
        // null explicite = retirer l'API de son organisation
        Organization organization = resolveOrganization(request.organizationId());
        Long currentOrganizationId = api.getOrganization() == null ? null : api.getOrganization().getId();
        Long targetOrganizationId = organization == null ? null : organization.getId();
        if (!Objects.equals(currentOrganizationId, targetOrganizationId)) {
            ensureCodeAvailable(api.getCode(), targetOrganizationId);
        }
        api.setOrganization(organization);
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
                ((Number) row[10]).longValue(),
                row[11] == null ? null : ((Number) row[11]).longValue(),
                (String) row[12],
                (String) row[13]
        );
    }

    /** Surcharge utilisée pour composer une organisation avec ses API. */
    public ApiDtos.ApiSummary toSummary(ApiDefinition api) {
        return new ApiDtos.ApiSummary(
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
                api.getEndpoints().size(),
                api.getOrganization() == null ? null : api.getOrganization().getId(),
                api.getOrganization() == null ? null : api.getOrganization().getCode(),
                api.getOrganization() == null ? null : api.getOrganization().getName()
        );
    }

    /** Le code d'une API est unique dans son organisation (ou parmi les API non classées). */
    private void ensureCodeAvailable(String code, Long organizationId) {
        boolean exists = organizationId == null
                ? repository.existsByCodeIgnoreCaseAndOrganizationIsNull(code)
                : repository.existsByCodeIgnoreCaseAndOrganizationId(code, organizationId);
        if (exists) {
            throw ApiException.conflict("Une API avec ce code existe déjà dans cette organisation");
        }
    }

    private com.apigen.domain.Organization resolveOrganization(Long organizationId) {
        if (organizationId == null) {
            return null;
        }
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> ApiException.notFound("Organisation introuvable : " + organizationId));
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
                endpoints.stream().map(endpointMapper::toSummary).toList(),
                api.getOrganization() == null ? null : api.getOrganization().getId(),
                api.getOrganization() == null ? null : api.getOrganization().getCode(),
                api.getOrganization() == null ? null : api.getOrganization().getName()
        );
    }
}
