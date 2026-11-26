package com.apigen.repository;

import com.apigen.domain.Organization;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Override
    @EntityGraph(attributePaths = "apis")
    Optional<Organization> findById(Long id);

    @Query("select o.id, o.code, o.name, o.description, o.active, o.createdAt, o.updatedAt, size(o.apis), o.contextConfig " +
            "from Organization o order by o.name asc")
    List<Object[]> findSummaries();

    /** Lecture légère du contexte, sans charger la collection d'API. */
    @Query("select o from Organization o where o.id = :id")
    Optional<Organization> findContextById(@Param("id") Long id);
}
