package com.example.minhaj.dtos;

import com.example.minhaj.models.Course;

import java.time.LocalDateTime;

public record CourseDTO(
        Long id,
        String title,
        String description,
        double price,
        boolean isFree,
        double averageRating,
        int reviewCount,
        int lessonCount,
        String imageUrl,
        Long categoryId,
        String categoryName,
        Long instructorId,
        String instructorName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CourseDTO from(Course course) {
        return new CourseDTO(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getPrice(),
                course.isFree(),
                course.getAverageRating(),
                course.getReviewCount(),
                course.getLessonCount(),
                course.getImageUrl(),
                course.getCategory() != null ? course.getCategory().getId() : null,
                course.getCategory() != null ? course.getCategory().getName() : null,
                course.getInstructor() != null ? course.getInstructor().getId() : null,
                course.getInstructor() != null ? course.getInstructor().getName() : null,
                course.getCreatedAt(),
                course.getUpdatedAt()
        );
    }
}
