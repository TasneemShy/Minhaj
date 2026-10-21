package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

public record QuizAnalyticsDTO(
        Long quizId,
        Long lessonId,
        Long courseId,
        String title,
        @Schema(description = "All attempts, retries included")
        long attempts,
        @Schema(description = "Distinct students with at least one attempt")
        long studentsAttempted,
        @Schema(description = "Distinct students with at least one passing attempt")
        long studentsPassed,
        @Schema(description = "studentsPassed as a percentage of studentsAttempted, 0-100 with one decimal; 0 with no attempts", example = "50.0")
        double passRate,
        @Schema(description = "Average score over all attempts, 0-100 with one decimal; 0 with no attempts", example = "72.5")
        double averageScore
) {}
