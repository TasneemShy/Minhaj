package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record CourseProgressDTO(
        Long courseId,
        String courseTitle,
        int completedLessons,
        int totalLessons,
        @Schema(description = "Completed lessons as a percentage of all lessons, 0-100", example = "50")
        int percentage,
        List<Long> completedLessonIds,
        @Schema(description = "True when the course has at least one lesson and every current lesson is completed. "
                + "Adding a lesson makes a completed course incomplete again.")
        boolean completed,
        @Schema(description = "When the last remaining lesson was completed; null unless `completed`")
        LocalDateTime completedAt
) {}
