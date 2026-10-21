package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Proof that a student completed a course, for a frontend to render as a certificate")
public record CompletionDTO(
        Long courseId,
        String courseTitle,
        String studentName,
        @Schema(description = "When the last remaining lesson was completed")
        LocalDateTime completedAt,
        int lessonCount,
        @Schema(description = "Instructor's name; null for courses created by an admin")
        String instructorName
) {}
