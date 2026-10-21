package com.example.minhaj.dtos;

import com.example.minhaj.models.Lesson;
import com.example.minhaj.models.QuizAttempt;

import java.time.LocalDateTime;

// One entry of a student's quiz history.
public record QuizAttemptSummaryDTO(
        Long attemptId,
        Long quizId,
        String quizTitle,
        Long lessonId,
        String lessonTitle,
        Long courseId,
        int scorePercentage,
        boolean passed,
        LocalDateTime submittedAt
) {
    public static QuizAttemptSummaryDTO from(QuizAttempt attempt) {
        Lesson lesson = attempt.getQuiz().getLesson();
        return new QuizAttemptSummaryDTO(attempt.getId(), attempt.getQuiz().getId(), attempt.getQuiz().getTitle(),
                lesson.getId(), lesson.getTitle(), lesson.getCourse().getId(), attempt.getScorePercentage(),
                attempt.isPassed(), attempt.getSubmittedAt());
    }
}
