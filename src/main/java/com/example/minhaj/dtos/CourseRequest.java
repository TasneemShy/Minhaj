package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

// Body for creating or updating a course. Counters, rating, timestamps and the instructor are set by the server.
public record CourseRequest(
        @NotBlank(message = "Course title cannot be empty")
        @Schema(description = "Course title", example = "Java for Beginners")
        String title,

        @Schema(description = "Course description", example = "Learn the basics of Java step by step.")
        String description,

        @PositiveOrZero(message = "Price cannot be negative")
        @Schema(description = "Price; stored as 0 when free is true or price is omitted", example = "19.99", minimum = "0")
        Double price,

        @Schema(description = "Whether the course is free; omitted means false", example = "false")
        Boolean free,

        @Schema(description = "Cover image URL", example = "https://example.com/images/java.png")
        String imageUrl,

        @Schema(description = "Id of an existing category (404 if it does not exist); omit or null for no category", example = "1")
        Long categoryId
) {}
