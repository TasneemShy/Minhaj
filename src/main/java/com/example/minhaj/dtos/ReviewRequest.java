package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReviewRequest(
        @NotBlank(message = "The review needs a comment")
        @Schema(description = "Review text", example = "Clear explanations and good exercises.")
        String comment,

        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating must be at most 5")
        @Schema(description = "Rating from 1 (worst) to 5 (best)", example = "5", minimum = "1", maximum = "5")
        Integer rating
) {}
