package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(
        @NotBlank(message = "Category name cannot be empty")
        @Schema(description = "Category name, unique ignoring case", example = "Programming")
        String name,

        @Schema(description = "Category description", example = "Software development courses")
        String description
) {}
