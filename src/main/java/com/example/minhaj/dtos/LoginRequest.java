package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email cannot be empty")
        @Schema(example = "sara@example.com")
        String email,

        @NotBlank(message = "Password cannot be empty")
        @Schema(example = "secret123")
        String password
) {}
