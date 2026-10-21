package com.example.minhaj.dtos;

import com.example.minhaj.models.QuestionType;
import com.example.minhaj.models.Quiz;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

// The author's view of a quiz, including which options are correct. Students get QuizForStudentDTO instead.
public record QuizDTO(
        Long id,
        Long lessonId,
        Long courseId,
        String title,
        @Schema(description = "Minimum score (0-100) that passes", example = "70")
        int passPercentage,
        LocalDateTime createdAt,
        List<Question> questions
) {

    public record Question(Long id, int position, String text, QuestionType type, List<Option> options) {}

    public record Option(Long id, int position, String text, boolean correct) {}

    public static QuizDTO from(Quiz quiz) {
        return new QuizDTO(
                quiz.getId(),
                quiz.getLesson().getId(),
                quiz.getLesson().getCourse().getId(),
                quiz.getTitle(),
                quiz.getPassPercentage(),
                quiz.getCreatedAt(),
                quiz.getQuestions().stream()
                        .map(question -> new Question(question.getId(), question.getPosition(), question.getText(),
                                question.getType(),
                                question.getOptions().stream()
                                        .map(option -> new Option(option.getId(), option.getPosition(), option.getText(),
                                                option.isCorrect()))
                                        .toList()))
                        .toList());
    }
}
