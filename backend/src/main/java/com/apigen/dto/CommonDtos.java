package com.apigen.dto;

import com.apigen.domain.RunStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class CommonDtos {

    private CommonDtos() {
    }

    public record MessageResponse(String message) {
    }

    public record DashboardResponse(
            long apiCount,
            long activeApiCount,
            long organizationCount,
            long endpointCount,
            long connectionCount,
            long runsToday,
            double successRate24h,
            Map<RunStatus, Long> runsByStatus,
            List<RunDtos.RunResponse> recentRuns
    ) {
    }

    public record AuditResponse(
            Long id,
            String actor,
            String action,
            String entityType,
            String entityId,
            String details,
            Instant createdAt
    ) {
    }

    public record ErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path,
            String correlationId
    ) {
    }
}
