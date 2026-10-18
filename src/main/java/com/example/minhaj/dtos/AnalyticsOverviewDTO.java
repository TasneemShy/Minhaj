package com.example.minhaj.dtos;

import java.util.Map;

public record AnalyticsOverviewDTO(
        long totalUsers,
        Map<String, Long> usersByRole,
        long totalCourses,
        long freeCourses,
        long paidCourses,
        long totalCategories,
        long totalLessons,
        long totalEnrollments,
        long totalReviews,
        double averageRating,
        long completedLessons
) {}
