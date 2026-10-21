package com.example.minhaj.models;

import jakarta.persistence.*;

@Entity
@Table(name = "lessons")
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String videoUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    // 1-based order inside the course; unique per course (checked at commit, see V2__lesson_position.sql)
    @Column(nullable = false)
    private int position;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}