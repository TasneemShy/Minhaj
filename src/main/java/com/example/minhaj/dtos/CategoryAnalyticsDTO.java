package com.example.minhaj.dtos;

public record CategoryAnalyticsDTO(
        Long categoryId,
        String categoryName,
        long courseCount,
        long enrollmentCount,
        double averageRating
) {}
