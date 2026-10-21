package com.example.minhaj.controllers;

import com.example.minhaj.dtos.ReviewDTO;
import com.example.minhaj.dtos.ReviewRequest;
import com.example.minhaj.exceptions.ApiException;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.Review;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.repositories.UserRepository;
import com.example.minhaj.repositories.ReviewRepository;
import com.example.minhaj.services.EmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.example.minhaj.config.SwaggerConfig.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Reviews", description = "Course reviews and ratings (1-5). The course rating is recomputed after every change.")
public class ReviewController {

    // Newest first; rows from before createdAt existed have null and go last, ordered by id.
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt").nullsLast(), Sort.Order.desc("id"));

    private final ReviewRepository reviewRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public ReviewController(ReviewRepository reviewRepository, CourseRepository courseRepository, UserRepository userRepository){
        this.reviewRepository = reviewRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @Operation(summary = "List a course's reviews",
            description = "Public. A page of reviews, newest first. 400 if page < 0 or size < 1.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of reviews"),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @GetMapping("/courses/{courseId}/reviews")
    public ResponseEntity<?> getReviewsByCourse(@PathVariable Long courseId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size){
        if (page < 0 || size < 1) {
            throw ApiException.badRequest("page must be >= 0 and size must be >= 1.");
        }
        if(!courseRepository.existsById(courseId)){

            throw new ResourceNotFoundException("Course not found with ID: " + courseId);
        }
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        return ResponseEntity.ok(reviewRepository.findByCourseId(courseId, pageable).map(ReviewDTO::from));
    }

    @Operation(summary = "List a user's reviews",
            description = "Authenticated users. A page of the user's reviews, newest first. 400 if page < 0 or size < 1.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of reviews"),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @GetMapping("/users/{userId}/reviews")
    public ResponseEntity<?> getReviewsByUser(@PathVariable Long userId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size){
        if (page < 0 || size < 1) {
            throw ApiException.badRequest("page must be >= 0 and size must be >= 1.");
        }
        if(!userRepository.existsById(userId)){

            throw new ResourceNotFoundException("User not found with ID: " + userId);
        }
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        return ResponseEntity.ok(reviewRepository.findByUserId(userId, pageable).map(ReviewDTO::from));
    }

    @Operation(summary = "Review a course",
            description = "Authenticated users with a verified email (403 otherwise). One review per user and course; "
                    + "a second review gives 400.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The created review", content = @Content(schema = @Schema(implementation = ReviewDTO.class))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @PostMapping("/courses/{courseId}/reviews")
    public ResponseEntity<?> addReview(@PathVariable Long courseId, @Valid @RequestBody ReviewRequest request, java.security.Principal principal){

        if (principal == null) {
            throw ApiException.unauthorized("You must be logged in to leave a review.");
        }

        String userEmail = principal.getName();
        User student = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found in the database."));
        EmailVerificationService.requireVerified(student, "post reviews");

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + courseId));

        if (reviewRepository.existsByCourseIdAndUserId(courseId, student.getId())) {
            throw ApiException.badRequest("You have already reviewed this course.");
        }

        Review review = new Review();
        review.setComment(request.comment());
        review.setRating(request.rating());
        review.setCourse(course);
        review.setUser(student);

        Review savedReview = reviewRepository.save(review);
        refreshCourseRating(course);

        return ResponseEntity.ok(ReviewDTO.from(savedReview));
    }

    @Operation(summary = "Edit my review",
            description = "Review author only (403 for anyone else). 404 if the review is not in the course.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated review", content = @Content(schema = @Schema(implementation = ReviewDTO.class))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @PutMapping("/courses/{courseId}/reviews/{reviewId}")
    public ResponseEntity<?> updateReview(@PathVariable Long courseId, @PathVariable Long reviewId,
                                          @Valid @RequestBody ReviewRequest request, java.security.Principal principal){

        if (principal == null) {
            throw ApiException.unauthorized("You must be logged in to edit a review.");
        }

        Review review = reviewRepository.findByIdAndCourseId(reviewId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with ID: " + reviewId + " in course " + courseId));

        if (!review.getUser().getEmail().equals(principal.getName())) {
            throw ApiException.forbidden("You can only edit your own review.");
        }

        review.setComment(request.comment());
        review.setRating(request.rating());

        Review savedReview = reviewRepository.save(review);
        refreshCourseRating(review.getCourse());

        return ResponseEntity.ok(ReviewDTO.from(savedReview));
    }

    @Operation(summary = "Delete a review",
            description = "Review author or admin (403 for anyone else). 404 if the review is not in the course.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deleted", content = @Content(schema = @Schema(ref = MESSAGE_SCHEMA))),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @DeleteMapping("/courses/{courseId}/reviews/{reviewId}")
    public ResponseEntity<?> deleteReview(@PathVariable Long courseId, @PathVariable Long reviewId, Authentication authentication){

        if (authentication == null) {
            throw ApiException.unauthorized("You must be logged in to delete a review.");
        }

        Review review = reviewRepository.findByIdAndCourseId(reviewId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with ID: " + reviewId + " in course " + courseId));

        boolean isAuthor = review.getUser().getEmail().equals(authentication.getName());
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));

        if (!isAuthor && !isAdmin) {
            throw ApiException.forbidden("You can only delete your own review.");
        }

        Course course = review.getCourse();
        reviewRepository.delete(review);
        refreshCourseRating(course);

        return ResponseEntity.ok(Map.of("message", "Review deleted successfully."));
    }

    // Recompute from the reviews table instead of adjusting a running total, so the values can't drift.
    private void refreshCourseRating(Course course) {
        Double average = reviewRepository.findAverageRatingByCourseId(course.getId());
        long count = reviewRepository.countByCourseId(course.getId());

        course.setReviewCount((int) count);
        course.setAverageRating(average == null ? 0.0 : Math.round(average * 10) / 10.0);
        courseRepository.save(course);
    }
}
