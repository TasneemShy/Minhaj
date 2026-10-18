package com.example.minhaj.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "reviews")
@EntityListeners(AuditingEntityListener.class)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "The review needs a comment")
    @Column(nullable = false)
    private String comment;

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private int rating;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    @JsonIgnore
    private Course course;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"email", "role", "password"})
    private User user;

    @CreatedDate
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(updatable = false)
    private java.time.LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id){ this.id = id; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }

}
