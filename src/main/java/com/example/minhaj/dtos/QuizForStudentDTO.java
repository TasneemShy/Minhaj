package com.example.minhaj.dtos;

import com.example.minhaj.models.QuestionType;
import com.example.minhaj.models.Quiz;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// A quiz as students see it: no correct flags anywhere.
public record QuizForStudentDTO(
        Long id,
        Long lessonId,
        Long courseId,
        String title,
        @Schema(description = "Minimum score (0-100) that passes", example = "70")
        int passPercentage,
        List<Question> questions
) {

    public record Question(Long id, int position, String text,
                           @Schema(description = "SINGLE_CHOICE: select one option. MULTIPLE_CHOICE: select every correct option.")
                           QuestionType type,
                           List<Option> options) {}

    public record Option(Long id, int position, String text) {}

    public static QuizForStudentDTO from(Quiz quiz) {
        return new QuizForStudentDTO(
                quiz.getId(),
                quiz.getLesson().getId(),
                quiz.getLesson().getCourse().getId(),
                quiz.getTitle(),
                quiz.getPassPercentage(),
                quiz.getQuestions().stream()
                        .map(question -> new Question(question.getId(), question.getPosition(), question.getText(),
                                question.getType(),
                                question.getOptions().stream()
                                        .map(option -> new Option(option.getId(), option.getPosition(), option.getText()))
                                        .toList()))
                        .toList());
    }
}
