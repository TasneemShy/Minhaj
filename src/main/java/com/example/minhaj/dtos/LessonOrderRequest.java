package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// Body for reordering a course's lessons: every lesson id of the course exactly once, in the new order.
public record LessonOrderRequest(
        @NotNull(message = "lessonIds is required")
        @Schema(description = "Every lesson id of the course exactly once, in the new order; positions become 1..n", example = "[12, 10, 11]")
        List<Long> lessonIds
) {}
