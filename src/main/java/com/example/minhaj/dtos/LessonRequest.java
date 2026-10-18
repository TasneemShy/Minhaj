package com.example.minhaj.dtos;

import jakarta.validation.constraints.NotBlank;

public record LessonRequest(
        @NotBlank(message = "Lesson title cannot be empty")
        String title,

        String videoUrl
) {}
