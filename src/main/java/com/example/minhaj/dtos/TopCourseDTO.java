package com.example.minhaj.dtos;

public record TopCourseDTO(
        Long id,
        String title,
        String categoryName,
        long enrollmentCount,
        double averageRating,
        long reviewCount
) {}
