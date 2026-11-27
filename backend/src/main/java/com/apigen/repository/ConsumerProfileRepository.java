package com.apigen.repository;
import com.apigen.domain.ConsumerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ConsumerProfileRepository extends JpaRepository<ConsumerProfile, Long> {
    List<ConsumerProfile> findByOrganizationId(Long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<ConsumerProfile> findByOrganizationIdAndCode(Long id, String code);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from ConsumerProfile p where p.id = :id")
    Optional<ConsumerProfile> lockById(@org.springframework.data.repository.query.Param("id") Long id);
}

