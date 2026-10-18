package com.example.minhaj.dtos;

import com.example.minhaj.models.Lesson;

public record LessonDTO(
        Long id,
        String title,
        String videoUrl,
        Long courseId
) {
    public static LessonDTO from(Lesson lesson) {
        return new LessonDTO(lesson.getId(), lesson.getTitle(), lesson.getVideoUrl(), lesson.getCourse().getId());
    }
}
