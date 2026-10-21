package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "Email cannot be empty")
        @Schema(description = "Account email the reset link is sent to", example = "sara@example.com")
        String email
) {}
