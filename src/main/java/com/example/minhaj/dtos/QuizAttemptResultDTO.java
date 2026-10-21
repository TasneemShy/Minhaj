package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

// The graded result of a new attempt.
public record QuizAttemptResultDTO(
        Long attemptId,
        Long quizId,
        Long lessonId,
        Long courseId,
        @Schema(description = "Correct questions / total questions x 100, rounded down", example = "66")
        int scorePercentage,
        int passPercentage,
        boolean passed,
        int correctQuestions,
        int totalQuestions,
        @Schema(description = "True when the lesson is marked completed after this attempt (always true when passed)")
        boolean lessonCompleted,
        LocalDateTime submittedAt,
        List<QuestionResult> questions
) {

    public record QuestionResult(
            Long questionId,
            boolean correct,
            List<Long> selectedOptionIds,
            @Schema(description = "The correct options; only revealed when the attempt passed, otherwise null")
            List<Long> correctOptionIds
    ) {}
}
