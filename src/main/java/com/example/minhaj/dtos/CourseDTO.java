package com.example.minhaj.dtos;

import com.example.minhaj.models.Course;
import com.example.minhaj.models.CourseStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record CourseDTO(
        Long id,
        String title,
        String description,
        double price,
        boolean isFree,
        @Schema(description = "Average review rating, 0-5; 0 when there are no reviews", example = "4.5")
        double averageRating,
        int reviewCount,
        int lessonCount,
        String imageUrl,
        Long categoryId,
        String categoryName,
        @Schema(description = "Instructor's user id; null for courses created by an admin")
        Long instructorId,
        String instructorName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        @Schema(description = "DRAFT courses are only visible to their instructor and admins")
        CourseStatus status,
        @Schema(description = "When the course was first published; null if it never was")
        LocalDateTime publishedAt
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
                course.getUpdatedAt(),
                course.getStatus(),
                course.getPublishedAt()
        );
    }
}
