package com.example.minhaj.dtos;

import com.example.minhaj.models.Review;

import java.time.LocalDateTime;

// The author is exposed by id and name only, never by email.
public record ReviewDTO(
        Long id,
        Long courseId,
        Long authorId,
        String authorName,
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
