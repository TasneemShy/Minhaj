package com.example.minhaj.models;

import jakarta.persistence.*;

@Entity
@Table(name = "quiz_options")
public class QuizOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    // 1-based order inside the question
    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 1000)
    private String text;

    // Never sent to students (see QuizForStudentDTO)
    @Column(nullable = false)
    private boolean correct;

    protected QuizOption() {}

    public QuizOption(int position, String text, boolean correct) {
        this.position = position;
        this.text = text;
        this.correct = correct;
    }

    public Long getId() { return id; }

    public QuizQuestion getQuestion() { return question; }
    void setQuestion(QuizQuestion question) { this.question = question; }

    public int getPosition() { return position; }
    public String getText() { return text; }
    public boolean isCorrect() { return correct; }
}
