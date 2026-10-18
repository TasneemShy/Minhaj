package com.example.minhaj.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

// Body for creating or updating a course. Counters, rating, timestamps and the instructor are set by the server.
public record CourseRequest(
        @NotBlank(message = "Course title cannot be empty")
        String title,

        String description,

        @PositiveOrZero(message = "Price cannot be negative")
        Double price,

        Boolean free,

        String imageUrl,

        Long categoryId
) {}
