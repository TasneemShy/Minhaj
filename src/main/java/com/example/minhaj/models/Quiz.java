package com.example.minhaj.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

// At most one per lesson. Questions (and their options) belong to the quiz and are saved and removed with it.
@Entity
@Table(name = "quizzes")
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, unique = true)
    private Lesson lesson;

    @Column(nullable = false)
    private String title;

    // Minimum score (0-100) that passes
    @Column(name = "pass_percentage", nullable = false)
    private int passPercentage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<QuizQuestion> questions = new ArrayList<>();

    protected Quiz() {}

    public Quiz(Lesson lesson) {
        this.lesson = lesson;
        // Postgres timestamp(6) keeps microseconds; truncate so the entity matches what is stored.
        this.createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() { return id; }
    public Lesson getLesson() { return lesson; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getPassPercentage() { return passPercentage; }
    public void setPassPercentage(int passPercentage) { this.passPercentage = passPercentage; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public List<QuizQuestion> getQuestions() { return questions; }

    public void addQuestion(QuizQuestion question) {
        question.setQuiz(this);
        questions.add(question);
    }
}
