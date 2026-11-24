package com.apigen.dto;

import com.apigen.domain.ConnectionType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class ConnectionDtos {

    private ConnectionDtos() {
    }

    public record ConnectionRequest(
            @NotBlank String name,
            @NotNull ConnectionType type,
            String description,
            JsonNode baseConfig,
            JsonNode credentials,
            Boolean active
    ) {
    }

    public record ConnectionResponse(
            Long id,
            String name,
            ConnectionType type,
            String description,
            JsonNode baseConfig,
            boolean hasCredentials,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ConnectionTestRequest(
            ConnectionType type,
            JsonNode baseConfig,
            JsonNode credentials
    ) {
    }

    public record ConnectionTestResponse(boolean ok, String message, long latencyMs) {
    }
}
