package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LessonRequest(
        @NotBlank(message = "Lesson title cannot be empty")
        @Schema(description = "Lesson title", example = "Variables and types")
        String title,

        @Schema(description = "Video URL", example = "https://videos.example.com/java/02")
        String videoUrl
) {}
