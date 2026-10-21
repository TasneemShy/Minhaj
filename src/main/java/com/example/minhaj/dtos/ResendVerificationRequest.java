package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(
        @NotBlank(message = "Email cannot be empty")
        @Schema(description = "Account email the new verification link is sent to", example = "sara@example.com")
        String email
) {}
