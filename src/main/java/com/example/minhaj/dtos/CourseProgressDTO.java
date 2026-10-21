package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CourseProgressDTO(
        Long courseId,
        String courseTitle,
        int completedLessons,
        int totalLessons,
        @Schema(description = "Completed lessons as a percentage of all lessons, 0-100", example = "50")
        int percentage,
        List<Long> completedLessonIds
) {}
