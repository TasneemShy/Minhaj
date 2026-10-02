package com.example.minhaj.dtos;

public record CourseDTO(
        Long id,
        String title,
        String description,
        double price,
        boolean isFree,
        double averageRating,
        int reviewCount,
        String imageUrl
) {}