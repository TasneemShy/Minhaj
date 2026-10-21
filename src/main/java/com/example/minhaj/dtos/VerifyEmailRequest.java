package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank(message = "Token cannot be empty")
        @Schema(description = "Token from the verification link (the `token` query parameter)")
        String token
) {}
