package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password cannot be empty")
        @Schema(description = "The caller's current password", example = "secret123")
        String currentPassword,

        @NotBlank(message = "New password cannot be empty")
        @Size(min = 6, message = "Password must be at least 6 characters long")
        @Schema(description = "At least 6 characters", example = "newSecret456")
        String newPassword
) {}
