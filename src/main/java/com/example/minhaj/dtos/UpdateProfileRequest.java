package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank(message = "Name cannot be empty")
        @Schema(description = "New display name; surrounding whitespace is trimmed", example = "Sara A.")
        String name
) {}
