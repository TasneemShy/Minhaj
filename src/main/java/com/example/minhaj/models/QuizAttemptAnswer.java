package com.example.minhaj.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// One selected option of an attempt (a row of quiz_attempt_answers).
@Embeddable
public record QuizAttemptAnswer(
        @Column(name = "question_id", nullable = false) Long questionId,
        @Column(name = "option_id", nullable = false) Long optionId
) {}
