package com.apigen.service;

import com.apigen.domain.ApiDefinition;
import com.apigen.domain.Organization;
import com.apigen.dto.ApiDtos;
import com.apigen.dto.OrganizationDtos;
import com.apigen.repository.OrganizationRepository;
import com.apigen.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class OrganizationService {

    private final OrganizationRepository repository;
    private final ApiDefinitionService apiService;
    private final AuditService auditService;
    private final OrganizationContextService contextService;
    private final ConsumerAuthService consumerAuth;

    public OrganizationService(OrganizationRepository repository,
                               ApiDefinitionService apiService,
                               AuditService auditService,
                               OrganizationContextService contextService, ConsumerAuthService consumerAuth) {
        this.repository = repository;
        this.apiService = apiService;
        this.auditService = auditService;
        this.contextService = contextService;
        this.consumerAuth = consumerAuth;
    }

    @Transactional(readOnly = true)
    public List<OrganizationDtos.OrganizationSummary> list() {
        return repository.findSummaries().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizationDtos.OrganizationResponse get(Long id) {
        return toResponse(requireEntity(id));
    }

    @Transactional(readOnly = true)
    public Organization requireEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Organisation introuvable : " + id));
    }

    @Transactional
    public OrganizationDtos.OrganizationResponse create(OrganizationDtos.OrganizationRequest request) {
        if (repository.existsByCodeIgnoreCase(request.code())) {
            throw ApiException.conflict("Une organisation avec ce code existe déjà");
        }
        Organization organization = new Organization();
        organization.setCode(request.code().trim());
        organization.setName(request.name().trim());
        organization.setDescription(request.description());
        organization.setContextConfig(contextService.prepareForStorage(request.contextConfig(), null));
        organization.setActive(request.active() == null || request.active());
        Organization saved = repository.save(organization);
        consumerAuth.ensureDefault(saved.getId());
        auditService.record("ORGANIZATION_CREATE", "Organization", String.valueOf(saved.getId()),
                "Création de l'organisation " + saved.getCode());
        return toResponse(saved);
    }

    @Transactional
    public OrganizationDtos.OrganizationResponse update(Long id, OrganizationDtos.OrganizationRequest request) {
        Organization organization = requireEntity(id);
        organization.setName(request.name().trim());
        organization.setDescription(request.description());
        organization.setContextConfig(contextService.prepareForStorage(
                request.contextConfig(), organization.getContextConfig()));
        organization.setActive(request.active() == null || request.active());
        organization.touch();
        Organization saved = repository.save(organization);
        auditService.record("ORGANIZATION_UPDATE", "Organization", String.valueOf(saved.getId()),
                "Mise à jour de l'organisation " + saved.getCode());
        return toResponse(saved);
    }

    /**
     * Supprime l'organisation. Les API qu'elle contenait ne sont pas supprimées :
     * elles redeviennent « non classées » (clé étrangère à SET NULL).
     */
    @Transactional
    public void delete(Long id) {
        Organization organization = requireEntity(id);
        long affected = organization.getApis().size();
        repository.delete(organization);
        auditService.record("ORGANIZATION_DELETE", "Organization", String.valueOf(id),
                "Suppression de l'organisation " + organization.getCode()
                        + " (" + affected + " API(s) déclassée(s))");
    }

    private OrganizationDtos.OrganizationSummary toSummary(Object[] row) {
        return new OrganizationDtos.OrganizationSummary(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                (String) row[3],
                Boolean.TRUE.equals(row[4]),
                instant(row[5]),
                instant(row[6]),
                ((Number) row[7]).longValue(),
                row[8] == null ? null : contextService.redact(String.valueOf(row[8]))
        );
    }

    private OrganizationDtos.OrganizationResponse toResponse(Organization organization) {
        List<ApiDtos.ApiSummary> apis = organization.getApis().stream()
                .sorted(Comparator.comparing(ApiDefinition::getId))
                .map(apiService::toSummary)
                .toList();
        return new OrganizationDtos.OrganizationResponse(
                organization.getId(),
                organization.getCode(),
                organization.getName(),
                organization.getDescription(),
                organization.isActive(),
                organization.getCreatedAt(),
                organization.getUpdatedAt(),
                contextService.redact(organization.getContextConfig()),
                apis
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
}
