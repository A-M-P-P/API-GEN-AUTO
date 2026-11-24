package com.apigen.dto;

import com.apigen.domain.Direction;
import com.apigen.domain.RunStatus;
import com.apigen.domain.TriggerType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public final class RunDtos {

    private RunDtos() {
    }

    public record RunResponse(
            Long id,
            Long apiId,
            String apiCode,
            String apiName,
            Long endpointId,
            String endpointName,
            RunStatus status,
            Direction direction,
            TriggerType triggerType,
            String correlationId,
            String requestSummary,
            String responseSummary,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt,
            Long durationMs
    ) {
    }

    public record RunTriggerRequest(JsonNode payload) {
    }

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements, int totalPages) {
            return new PageResponse<>(content, page, size, totalElements, totalPages);
        }
    }
}
