package com.example.minhaj.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "lesson_progress",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "lesson_id"}))
public class LessonProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(nullable = false)
    private LocalDateTime completedAt;

    public LessonProgress() {}

    public LessonProgress(User user, Lesson lesson) {
        this.user = user;
        this.lesson = lesson;
        // Postgres timestamp(6) keeps microseconds; truncate so the entity matches what is stored.
        this.completedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Lesson getLesson() { return lesson; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
