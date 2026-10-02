package com.example.minhaj.controllers;

import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.Review;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.repositories.UserRepository;
import com.example.minhaj.repositories.ReviewRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public ReviewController(ReviewRepository reviewRepository, CourseRepository courseRepository, UserRepository userRepository){
        this.reviewRepository = reviewRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/courses/{courseId}/reviews")
    public ResponseEntity<List<Review>> getReviewsByCourse(@PathVariable Long courseId){
        if(!courseRepository.existsById(courseId)){

            throw new ResourceNotFoundException("Course not found with ID: " + courseId);
        }
        List<Review> reviews = reviewRepository.findByCourseId(courseId);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/users/{userId}/reviews")
    public ResponseEntity<List<Review>> getReviewsByUser(@PathVariable Long userId){
        if(!userRepository.existsById(userId)){

            throw new ResourceNotFoundException("User not found with ID: " + userId);
        }
        List<Review> reviews = reviewRepository.findByUserId(userId);
        return ResponseEntity.ok(reviews);
    }

    @PostMapping("/courses/{courseId}/reviews")
    public ResponseEntity<?> addReview(@PathVariable Long courseId, @Valid @RequestBody Review reviewRequest, java.security.Principal principal){

        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "You must be logged in to leave a review."));
        }

        String userEmail = principal.getName();
        User student = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found in the database."));

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + courseId));

        if (reviewRepository.existsByCourseIdAndUserId(courseId, student.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "You have already reviewed this course."));
        }

        reviewRequest.setCourse(course);
        reviewRequest.setUser(student);

        Review savedReview = reviewRepository.save(reviewRequest);

        double oldTotalScore = course.getAverageRating() * course.getReviewCount();
        double newTotalScore = oldTotalScore + reviewRequest.getRating();

        course.setReviewCount(course.getReviewCount() + 1);
        course.setAverageRating(newTotalScore / course.getReviewCount());

        courseRepository.save(course);

        return ResponseEntity.ok(savedReview);
    }
}