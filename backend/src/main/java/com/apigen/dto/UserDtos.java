package com.apigen.dto;

import com.apigen.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class UserDtos {

    private UserDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, String tokenType, long expiresIn, UserResponse user) {
    }

    public record UserResponse(
            Long id,
            String username,
            String fullName,
            Role role,
            boolean enabled,
            Instant createdAt
    ) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 80) String username,
            @NotBlank @Size(min = 8, max = 100) String password,
            @Size(max = 150) String fullName,
            Role role
    ) {
    }

    public record UpdateUserRequest(
            @Size(max = 150) String fullName,
            Role role,
            Boolean enabled
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100) String newPassword
    ) {
    }
}
