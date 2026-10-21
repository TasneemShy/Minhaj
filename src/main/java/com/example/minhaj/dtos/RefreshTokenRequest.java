package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// Body of POST /api/auth/refresh and POST /api/auth/logout.
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token cannot be empty")
        @Schema(description = "Refresh token from login, register or a previous refresh",
                example = "q3J0dX4m1YvR8c2l0nP6sT9wZbE5aHkLxGfUoNiCjDy")
        String refreshToken
) {}
