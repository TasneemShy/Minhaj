package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Name cannot be empty")
        @Schema(description = "Display name", example = "Sara Ahmed")
        String name,

        @NotBlank(message = "Email cannot be empty")
        @Email(message = "Invalid email format")
        @Schema(description = "Login email; must not be registered yet (409)", example = "sara@example.com")
        String email,

        @NotBlank(message = "Password cannot be empty")
        @Size(min = 6, message = "Password must be at least 6 characters long")
        @Schema(description = "At least 6 characters", example = "secret123")
        String password
) {}
