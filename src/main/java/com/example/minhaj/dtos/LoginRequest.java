package com.example.minhaj.dtos;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email cannot be empty")
        String email,

        @NotBlank(message = "Password cannot be empty")
        String password
) {}
