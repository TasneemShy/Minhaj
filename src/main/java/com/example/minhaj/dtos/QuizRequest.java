package com.example.minhaj.dtos;

import com.example.minhaj.models.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// Body for creating or replacing a lesson's quiz. Questions and options keep the order they are sent in.
// The number of correct options per question type is checked by QuizService (400).
public record QuizRequest(
        @NotBlank(message = "Quiz title cannot be empty")
        @Size(max = 255, message = "Quiz title must be at most 255 characters")
        @Schema(description = "Quiz title", example = "Check your understanding")
        String title,

        @Min(value = 0, message = "passPercentage must be between 0 and 100")
        @Max(value = 100, message = "passPercentage must be between 0 and 100")
        @Schema(description = "Minimum score that passes, 0-100; omitted means 70", example = "70", minimum = "0", maximum = "100")
        Integer passPercentage,

        @NotEmpty(message = "A quiz needs at least one question")
        @Size(max = 100, message = "A quiz can have at most 100 questions")
        List<@Valid @NotNull(message = "Question cannot be null") Question> questions
) {

    public static final int DEFAULT_PASS_PERCENTAGE = 70;

    public int passPercentageOrDefault() {
        return passPercentage == null ? DEFAULT_PASS_PERCENTAGE : passPercentage;
    }

    public record Question(
            @NotBlank(message = "Question text cannot be empty")
            @Size(max = 1000, message = "Question text must be at most 1000 characters")
            @Schema(example = "Which keyword declares a constant in Java?")
            String text,

            @NotNull(message = "Question type is required (SINGLE_CHOICE or MULTIPLE_CHOICE)")
            @Schema(description = "SINGLE_CHOICE: exactly one correct option. MULTIPLE_CHOICE: at least one.")
            QuestionType type,

            @NotNull(message = "A question needs at least two options")
            @Size(min = 2, max = 20, message = "A question needs between 2 and 20 options")
            List<@Valid @NotNull(message = "Option cannot be null") Option> options
    ) {}

    public record Option(
            @NotBlank(message = "Option text cannot be empty")
            @Size(max = 1000, message = "Option text must be at most 1000 characters")
            @Schema(example = "final")
            String text,

            @Schema(description = "Whether selecting this option is part of the right answer")
            boolean correct
    ) {}
}
