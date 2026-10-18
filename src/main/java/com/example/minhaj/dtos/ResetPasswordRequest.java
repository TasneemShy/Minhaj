package com.example.minhaj.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Same password rule as RegisterRequest.
public record ResetPasswordRequest(
        @NotBlank(message = "Token cannot be empty")
        String token,

        @NotBlank(message = "Password cannot be empty")
        @Size(min = 6, message = "Password must be at least 6 characters long")
        String newPassword
) {}
