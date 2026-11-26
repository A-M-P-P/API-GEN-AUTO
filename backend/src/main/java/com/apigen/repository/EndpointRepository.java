package com.apigen.repository;

import com.apigen.domain.Endpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EndpointRepository extends JpaRepository<Endpoint, Long> {

    List<Endpoint> findByApiIdOrderByIdAsc(Long apiId);

    List<Endpoint> findByApiIdAndActiveTrue(Long apiId);

    Optional<Endpoint> findByApiIdAndMethodIgnoreCaseAndPath(Long apiId, String method, String path);

    @Query("select e from Endpoint e left join fetch e.api left join fetch e.schedule where e.id = :id")
    Optional<Endpoint> findByIdWithSchedule(@Param("id") Long id);

    /** Identifiant de l'organisation propriétaire de l'API, sans initialiser les relations lazy. */
    @Query("select e.api.organization.id from Endpoint e where e.id = :id")
    Optional<Long> findOrganizationIdById(@Param("id") Long id);

    @Query("select e from Endpoint e left join fetch e.schedule where e.api.id = :apiId order by e.id asc")
    List<Endpoint> findByApiIdWithSchedule(@Param("apiId") Long apiId);
}
