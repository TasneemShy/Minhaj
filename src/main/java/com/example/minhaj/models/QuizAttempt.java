package com.example.minhaj.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

// One graded submission. The score is computed by QuizService when the attempt is created and never changes.
@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "score_percentage", nullable = false)
    private int scorePercentage;

    @Column(nullable = false)
    private boolean passed;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    // The options the student selected
    @ElementCollection
    @CollectionTable(name = "quiz_attempt_answers", joinColumns = @JoinColumn(name = "attempt_id"))
    private Set<QuizAttemptAnswer> answers = new HashSet<>();

    protected QuizAttempt() {}

    public QuizAttempt(Quiz quiz, User user, int scorePercentage, boolean passed, Set<QuizAttemptAnswer> answers) {
        this.quiz = quiz;
        this.user = user;
        this.scorePercentage = scorePercentage;
        this.passed = passed;
        this.answers = new HashSet<>(answers);
        // Postgres timestamp(6) keeps microseconds; truncate so the response matches what is stored.
        this.submittedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() { return id; }
    public Quiz getQuiz() { return quiz; }
    public User getUser() { return user; }
    public int getScorePercentage() { return scorePercentage; }
    public boolean isPassed() { return passed; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public Set<QuizAttemptAnswer> getAnswers() { return answers; }
}
