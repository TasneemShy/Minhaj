package com.example.minhaj.dtos;

public record CourseAnalyticsDTO(
        Long courseId,
        String title,
        String categoryName,
        long enrollmentCount,
        long lessonCount,
        long reviewCount,
        double averageRating,
        long completedLessons,
        double averageCompletionPercentage
) {}
