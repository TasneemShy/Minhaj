package com.example.minhaj.models;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quiz_questions")
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    // 1-based order inside the quiz
    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 1000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionType type;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<QuizOption> options = new ArrayList<>();

    protected QuizQuestion() {}

    public QuizQuestion(int position, String text, QuestionType type) {
        this.position = position;
        this.text = text;
        this.type = type;
    }

    public Long getId() { return id; }

    public Quiz getQuiz() { return quiz; }
    void setQuiz(Quiz quiz) { this.quiz = quiz; }

    public int getPosition() { return position; }
    public String getText() { return text; }
    public QuestionType getType() { return type; }
    public List<QuizOption> getOptions() { return options; }

    public void addOption(QuizOption option) {
        option.setQuestion(this);
        options.add(option);
    }
}
