package com.example.minhaj.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Course title cannot be empty")
    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private boolean isFree;

    @PositiveOrZero(message = "Price cannot be negative")
    private double price;

    private int lessonCount = 0;

    @ManyToMany(mappedBy = "enrolledCourses", fetch = FetchType.LAZY)
    @JsonIgnore
    private java.util.Set<User> enrolledStudents = new java.util.HashSet<>();

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isFree() { return isFree; }
    public void setFree(boolean free) { isFree = free; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public int getLessonCount() { return lessonCount; }
    public void setLessonCount(int lessonCount) { this.lessonCount = lessonCount; }

    public java.util.Set<User> getEnrolledStudents() { return enrolledStudents; }
    public void setEnrolledStudents(java.util.Set<User> enrolledStudents) { this.enrolledStudents = enrolledStudents; }
}