package com.apigen.repository;

import com.apigen.domain.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByEnabledTrueAndNextRunAtLessThanEqual(Instant now);

    Optional<Schedule> findByEndpointId(Long endpointId);

    boolean existsByEndpointId(Long endpointId);
}
