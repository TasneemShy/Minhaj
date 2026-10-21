package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// A student's answers. Questions left out (or answered with no options) count as wrong.
public record QuizAttemptRequest(
        @NotNull(message = "answers is required")
        List<@Valid @NotNull(message = "Answer cannot be null") Answer> answers
) {

    public record Answer(
            @NotNull(message = "questionId is required")
            @Schema(example = "1")
            Long questionId,

            @NotNull(message = "optionIds is required")
            @Schema(description = "Selected options of this question", example = "[2]")
            List<@NotNull(message = "optionIds cannot contain null") Long> optionIds
    ) {}
}
