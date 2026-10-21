package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

public record CourseAnalyticsDTO(
        Long courseId,
        String title,
        String categoryName,
        long enrollmentCount,
        long lessonCount,
        long reviewCount,
        double averageRating,
        long completedLessons,
        double averageCompletionPercentage,
        @Schema(description = "Enrolled students who completed every current lesson")
        long completions,
        @Schema(description = "completions as a percentage of enrollments, 0-100 with one decimal; 0 with no enrollments", example = "50.0")
        double completionRate
) {}
