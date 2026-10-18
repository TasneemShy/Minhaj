package com.example.minhaj.dtos;

import java.util.List;

public record CourseProgressDTO(
        Long courseId,
        String courseTitle,
        int completedLessons,
        int totalLessons,
        int percentage,
        List<Long> completedLessonIds
) {}
