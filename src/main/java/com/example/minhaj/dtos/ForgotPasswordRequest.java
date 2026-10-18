package com.example.minhaj.dtos;

import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "Email cannot be empty")
        String email
) {}
