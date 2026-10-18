package com.example.minhaj.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password cannot be empty")
        String currentPassword,

        @NotBlank(message = "New password cannot be empty")
        @Size(min = 6, message = "Password must be at least 6 characters long")
        String newPassword
) {}
