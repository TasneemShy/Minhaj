package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

// Returned by register, login and refresh.
@Schema(description = "A new access token (JWT) and refresh token pair.")
public record AuthResponse(
        @Schema(example = "Login successful")
        String message,

        @Schema(description = "Short-lived JWT for the Authorization header (`Bearer <accessToken>`)",
                example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "Long-lived, single-use token for POST /api/auth/refresh and /logout. "
                + "Each refresh returns a new one; store it securely.",
                example = "q3J0dX4m1YvR8c2l0nP6sT9wZbE5aHkLxGfUoNiCjDy")
        String refreshToken,

        @Schema(description = "Lifetime of the access token in seconds", example = "900")
        long expiresIn,

        @Schema(allowableValues = {"STUDENT", "INSTRUCTOR", "ADMIN"}, example = "STUDENT")
        String role,

        // Kept for clients written before refresh tokens; remove in the release after next.
        @Deprecated
        @Schema(description = "Deprecated: same value as `accessToken`, kept for one release. Use `accessToken`.",
                deprecated = true, example = "eyJhbGciOiJIUzI1NiJ9...")
        String token
) {
    public AuthResponse(String message, String accessToken, String refreshToken, long expiresIn, String role) {
        this(message, accessToken, refreshToken, expiresIn, role, accessToken);
    }
}
