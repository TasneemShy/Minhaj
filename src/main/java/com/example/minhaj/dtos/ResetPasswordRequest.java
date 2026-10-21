package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Same password rule as RegisterRequest.
public record ResetPasswordRequest(
        @NotBlank(message = "Token cannot be empty")
        @Schema(description = "Token from the reset link (the `token` query parameter)")
        String token,

        @NotBlank(message = "Password cannot be empty")
        @Size(min = 6, message = "Password must be at least 6 characters long")
        @Schema(description = "At least 6 characters", example = "newSecret456")
        String newPassword
) {}
