package com.apigen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class OrganizationDtos {

    private OrganizationDtos() {
    }

    public record OrganizationSummary(
            Long id,
            String code,
            String name,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long apiCount
    ) {
    }

    public record OrganizationResponse(
            Long id,
            String code,
            String name,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            List<ApiDtos.ApiSummary> apis
    ) {
    }

    public record OrganizationRequest(
            @NotBlank
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9-]{1,59}$",
                    message = "le code doit contenir 2 à 60 caractères : lettres, chiffres et tirets")
            String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 4000) String description,
            Boolean active
    ) {
    }
}
