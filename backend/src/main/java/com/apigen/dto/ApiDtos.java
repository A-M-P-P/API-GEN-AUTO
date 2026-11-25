package com.apigen.dto;

import com.apigen.domain.ApiStatus;
import com.apigen.domain.Direction;
import com.apigen.domain.InboundAuth;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

public final class ApiDtos {

    private ApiDtos() {
    }

    public record ApiSummary(
            Long id,
            String code,
            String name,
            String description,
            String version,
            ApiStatus status,
            InboundAuth inboundAuth,
            String createdBy,
            Instant createdAt,
            Instant updatedAt,
            long endpointCount,
            Long organizationId,
            String organizationCode,
            String organizationName
    ) {
    }

    public record ApiDetail(
            Long id,
            String code,
            String name,
            String description,
            String version,
            ApiStatus status,
            InboundAuth inboundAuth,
            String createdBy,
            Instant createdAt,
            Instant updatedAt,
            List<EndpointSummary> endpoints,
            Long organizationId,
            String organizationCode,
            String organizationName
    ) {
    }

    public record ApiCreateRequest(
            @NotBlank
            @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,79}$",
                    message = "le code doit contenir 2 à 80 caractères : minuscules, chiffres et tirets")
            String code,
            @NotBlank String name,
            String description,
            String version,
            ApiStatus status,
            InboundAuth inboundAuth,
            Long organizationId
    ) {
    }

    public record ApiUpdateRequest(
            @NotBlank String name,
            String description,
            String version,
            ApiStatus status,
            InboundAuth inboundAuth,
            Long organizationId
    ) {
    }

    public record ApiKeyResponse(Long apiId, String code, String apiKey, InboundAuth inboundAuth) {
    }

    public record EndpointSummary(
            Long id,
            String name,
            String method,
            String path,
            Direction direction,
            boolean active,
            ScheduleResponse schedule
    ) {
    }

    public record EndpointResponse(
            Long id,
            Long apiId,
            String name,
            String method,
            String path,
            Direction direction,
            String description,
            JsonNode pipeline,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            ScheduleResponse schedule
    ) {
    }

    public record EndpointRequest(
            @NotBlank String name,
            @NotBlank
            @Pattern(regexp = "^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)$", message = "méthode HTTP invalide")
            String method,
            @NotBlank
            @Pattern(regexp = "^/[A-Za-z0-9_\\-/{}.*]*$", message = "le chemin doit commencer par /")
            String path,
            Direction direction,
            String description,
            JsonNode pipeline,
            Boolean active
    ) {
    }

    public record ScheduleRequest(
            @NotBlank String cronExpression,
            Boolean enabled,
            JsonNode payload
    ) {
    }

    public record ScheduleResponse(
            Long id,
            String cronExpression,
            boolean enabled,
            JsonNode payload,
            Instant nextRunAt,
            Instant lastRunAt
    ) {
    }
}
