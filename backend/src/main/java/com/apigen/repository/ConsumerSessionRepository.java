package com.apigen.repository;
import com.apigen.domain.ConsumerSession;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ConsumerSessionRepository extends JpaRepository<ConsumerSession, Long> {
    Optional<ConsumerSession> findByAccessHash(String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ConsumerSession> findByRefreshHash(String hash);
    List<ConsumerSession> findByProfileId(Long id);
    long deleteByRefreshExpiresBefore(java.time.Instant before);
}
