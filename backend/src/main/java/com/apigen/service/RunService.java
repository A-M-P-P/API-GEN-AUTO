package com.apigen.service;

import com.apigen.domain.ExchangeRun;
import com.apigen.domain.RunStatus;
import com.apigen.dto.RunDtos;
import com.apigen.repository.ExchangeRunRepository;
import com.apigen.web.ApiException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class RunService {

    private final ExchangeRunRepository repository;

    public RunService(ExchangeRunRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public RunDtos.PageResponse<RunDtos.RunResponse> list(Long apiId, Long endpointId, RunStatus status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "startedAt"));
        Specification<ExchangeRun> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (apiId != null) {
                predicates.add(criteriaBuilder.equal(root.get("api").get("id"), apiId));
            }
            if (endpointId != null) {
                predicates.add(criteriaBuilder.equal(root.get("endpoint").get("id"), endpointId));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        Page<ExchangeRun> result = repository.findAll(specification, pageable);
        List<RunDtos.RunResponse> content = result.getContent().stream().map(this::toResponse).toList();
        return RunDtos.PageResponse.of(content, safePage, safeSize, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public RunDtos.RunResponse get(Long id) {
        ExchangeRun run = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Exécution introuvable : " + id));
        return toResponse(run);
    }

    @Transactional(readOnly = true)
    public List<RunDtos.RunResponse> recent(int limit) {
        return repository.findTop10ByOrderByStartedAtDesc().stream()
                .limit(limit)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RunDtos.RunResponse toResponse(ExchangeRun run) {
        var api = run.getApi();
        var endpoint = run.getEndpoint();
        return new RunDtos.RunResponse(
                run.getId(),
                api == null ? null : api.getId(),
                api == null ? null : api.getCode(),
                api == null ? null : api.getName(),
                endpoint == null ? null : endpoint.getId(),
                endpoint == null ? null : endpoint.getName(),
                run.getStatus(),
                run.getDirection(),
                run.getTriggerType(),
                run.getCorrelationId(),
                run.getRequestSummary(),
                run.getResponseSummary(),
                run.getErrorMessage(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs()
        );
    }
}
