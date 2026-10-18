package com.example.minhaj.dtos;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank(message = "Name cannot be empty")
        String name
) {}
