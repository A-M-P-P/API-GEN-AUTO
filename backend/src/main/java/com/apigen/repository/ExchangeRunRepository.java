package com.apigen.repository;

import com.apigen.domain.ExchangeRun;
import com.apigen.domain.RunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;

public interface ExchangeRunRepository extends JpaRepository<ExchangeRun, Long>, JpaSpecificationExecutor<ExchangeRun> {

    List<ExchangeRun> findTop10ByOrderByStartedAtDesc();

    long countByStatus(RunStatus status);

    long countByStartedAtAfterAndStatus(Instant since, RunStatus status);

    long countByApiId(Long apiId);

    Page<ExchangeRun> findByApiIdOrderByIdDesc(Long apiId, Pageable pageable);
}
