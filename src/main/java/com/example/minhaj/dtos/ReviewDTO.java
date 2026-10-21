package com.example.minhaj.dtos;

import com.example.minhaj.models.Review;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// The author is exposed by id and name only, never by email.
public record ReviewDTO(
        Long id,
        Long courseId,
        Long authorId,
        String authorName,
        @Schema(description = "Rating from 1 to 5", example = "5")
        int rating,
        String comment,
        LocalDateTime createdAt
) {
    public static ReviewDTO from(Review review) {
        return new ReviewDTO(
                review.getId(),
                review.getCourse().getId(),
                review.getUser().getId(),
                review.getUser().getName(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
