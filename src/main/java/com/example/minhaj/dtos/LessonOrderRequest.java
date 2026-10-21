package com.example.minhaj.dtos;

import jakarta.validation.constraints.NotNull;

import java.util.List;

// Body for reordering a course's lessons: every lesson id of the course exactly once, in the new order.
public record LessonOrderRequest(
        @NotNull(message = "lessonIds is required")
        List<Long> lessonIds
) {}
