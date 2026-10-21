package com.example.minhaj.controllers;

import com.example.minhaj.exceptions.ApiException;
import com.example.minhaj.dtos.AnalyticsOverviewDTO;
import com.example.minhaj.dtos.CategoryAnalyticsDTO;
import com.example.minhaj.dtos.CourseAnalyticsDTO;
import com.example.minhaj.dtos.TopCourseDTO;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Course;
import com.example.minhaj.repositories.AnalyticsRepository;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.repositories.LessonRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.minhaj.config.SwaggerConfig.*;

@RestController
@RequestMapping("/api/admin/analytics")
@Transactional(readOnly = true)
@Tag(name = "Admin – Analytics", description = "Platform statistics (ADMIN role). Ratings with no reviews are reported as 0.")
@SecurityRequirement(name = BEARER_AUTH)
public class AnalyticsController {

    private static final List<String> TOP_COURSE_ORDERS = List.of("enrollments", "rating", "reviews");

    private final AnalyticsRepository analyticsRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;

    public AnalyticsController(AnalyticsRepository analyticsRepository, CourseRepository courseRepository,
                               LessonRepository lessonRepository) {
        this.analyticsRepository = analyticsRepository;
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
    }

    @Operation(summary = "Platform overview",
            description = "Admin only. Totals for users (also per role), courses (free/paid, published/draft), categories, lessons, enrollments, reviews and completed lessons.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Overview"),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN)
    })
    @GetMapping("/overview")
    public AnalyticsOverviewDTO getOverview() {
        Map<String, Long> usersByRole = new LinkedHashMap<>();
        analyticsRepository.countUsersByRole()
                .forEach(row -> usersByRole.put(row.getRole(), row.getCount()));

        long totalCourses = analyticsRepository.countCourses();
        long freeCourses = analyticsRepository.countFreeCourses();
        long publishedCourses = analyticsRepository.countPublishedCourses();

        return new AnalyticsOverviewDTO(
                analyticsRepository.countUsers(),
                usersByRole,
                totalCourses,
                freeCourses,
                totalCourses - freeCourses,
                publishedCourses,
                totalCourses - publishedCourses,
                analyticsRepository.countCategories(),
                analyticsRepository.countLessons(),
                analyticsRepository.countEnrollments(),
                analyticsRepository.countReviews(),
                roundRating(analyticsRepository.averageRating()),
                analyticsRepository.countCompletedLessons()
        );
    }

    @Operation(summary = "Top courses",
            description = "Admin only. Courses ranked by enrollments, average rating or review count.")
    @Parameter(name = "by", in = ParameterIn.QUERY, description = "Ranking metric",
            schema = @Schema(type = "string", defaultValue = "enrollments", allowableValues = {"enrollments", "rating", "reviews"}))
    @Parameter(name = "limit", in = ParameterIn.QUERY, description = "Number of courses, 1-50",
            schema = @Schema(type = "integer", defaultValue = "5", minimum = "1", maximum = "50"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Top courses", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TopCourseDTO.class)))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN)
    })
    @GetMapping("/top-courses")
    public ResponseEntity<?> getTopCourses(@RequestParam(defaultValue = "enrollments") String by,
                                           @RequestParam(defaultValue = "5") int limit) {

        if (!TOP_COURSE_ORDERS.contains(by)) {
            throw ApiException.badRequest("by must be one of: " + String.join(", ", TOP_COURSE_ORDERS));
        }
        if (limit < 1 || limit > 50) {
            throw ApiException.badRequest("limit must be between 1 and 50");
        }

        Pageable pageable = PageRequest.of(0, limit);
        List<AnalyticsRepository.CourseStats> rows = switch (by) {
            case "rating" -> analyticsRepository.findTopCoursesByRating(pageable);
            case "reviews" -> analyticsRepository.findTopCoursesByReviews(pageable);
            default -> analyticsRepository.findTopCoursesByEnrollments(pageable);
        };

        List<TopCourseDTO> topCourses = rows.stream()
                .map(row -> new TopCourseDTO(
                        row.getId(),
                        row.getTitle(),
                        row.getCategoryName(),
                        row.getEnrollmentCount(),
                        roundRating(row.getAverageRating()),
                        row.getReviewCount()))
                .toList();

        return ResponseEntity.ok(topCourses);
    }

    @Operation(summary = "Per-category statistics",
            description = "Admin only. One row per category plus a final Uncategorized row (categoryId null).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category statistics"),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN)
    })
    @GetMapping("/categories")
    public List<CategoryAnalyticsDTO> getCategoryStats() {
        List<CategoryAnalyticsDTO> stats = new ArrayList<>(analyticsRepository.findCategoryStats().stream()
                .map(row -> new CategoryAnalyticsDTO(
                        row.getId(),
                        row.getName(),
                        row.getCourseCount(),
                        row.getEnrollmentCount(),
                        roundRating(row.getAverageRating())))
                .toList());

        stats.add(new CategoryAnalyticsDTO(
                null,
                "Uncategorized",
                analyticsRepository.countUncategorizedCourses(),
                analyticsRepository.countUncategorizedEnrollments(),
                roundRating(analyticsRepository.averageUncategorizedRating())));

        return stats;
    }

    @Operation(summary = "Course statistics",
            description = "Admin only. Enrollments, lessons, reviews and the share of lessons completed by enrolled students.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Course statistics"),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @GetMapping("/courses/{id}")
    public CourseAnalyticsDTO getCourseStats(@PathVariable Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + id));

        long enrollments = analyticsRepository.countEnrollmentsByCourseId(id);
        long lessons = lessonRepository.countByCourseId(id);
        long completed = analyticsRepository.countCompletedLessonsByEnrolledStudents(id);

        // Share of (enrolled student, lesson) pairs that are completed
        long possible = enrollments * lessons;
        double completionPercentage = possible == 0 ? 0.0 : Math.round(completed * 1000.0 / possible) / 10.0;

        return new CourseAnalyticsDTO(
                course.getId(),
                course.getTitle(),
                course.getCategory() == null ? null : course.getCategory().getName(),
                enrollments,
                lessons,
                analyticsRepository.countReviewsByCourseId(id),
                roundRating(analyticsRepository.averageRatingByCourseId(id)),
                completed,
                completionPercentage
        );
    }

    // Averages are null when there are no reviews; report those as 0 like Course.averageRating does.
    private static double roundRating(Double average) {
        return average == null ? 0.0 : Math.round(average * 100.0) / 100.0;
    }
}
