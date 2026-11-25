package com.apigen.repository;

import com.apigen.domain.ApiDefinition;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ApiDefinitionRepository extends JpaRepository<ApiDefinition, Long> {

    Optional<ApiDefinition> findByCodeIgnoreCaseAndOrganizationId(String code, Long organizationId);

    Optional<ApiDefinition> findByCodeIgnoreCaseAndOrganizationIsNull(String code);

    boolean existsByCodeIgnoreCaseAndOrganizationId(String code, Long organizationId);

    boolean existsByCodeIgnoreCaseAndOrganizationIsNull(String code);

    long countByStatus(com.apigen.domain.ApiStatus status);

    @Override
    @EntityGraph(attributePaths = "endpoints")
    Optional<ApiDefinition> findById(Long id);

    @Query("select a.id, a.code, a.name, a.description, a.version, a.status, a.inboundAuth, " +
            "a.createdBy, a.createdAt, a.updatedAt, size(a.endpoints), " +
            "a.organization.id, a.organization.code, a.organization.name " +
            "from ApiDefinition a order by a.updatedAt desc")
    List<Object[]> findSummaries();
}
