package com.example.minhaj.dtos;

import com.example.minhaj.models.Lesson;
import io.swagger.v3.oas.annotations.media.Schema;

public record LessonDTO(
        Long id,
        String title,
        String videoUrl,
        Long courseId,
        @Schema(description = "1-based position of the lesson in its course", example = "1")
        int position
) {
    public static LessonDTO from(Lesson lesson) {
        return new LessonDTO(lesson.getId(), lesson.getTitle(), lesson.getVideoUrl(), lesson.getCourse().getId(),
                lesson.getPosition());
    }
}
