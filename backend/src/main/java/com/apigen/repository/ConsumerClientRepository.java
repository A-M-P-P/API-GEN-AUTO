package com.apigen.repository;
import com.apigen.domain.ConsumerClient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ConsumerClientRepository extends JpaRepository<ConsumerClient, Long> {
    List<ConsumerClient> findByProfileId(Long id);
    Optional<ConsumerClient> findByProfileIdAndClientId(Long id, String clientId);
}
