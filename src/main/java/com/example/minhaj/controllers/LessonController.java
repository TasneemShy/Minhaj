package com.example.minhaj.controllers;

import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.dtos.LessonDTO;
import com.example.minhaj.dtos.LessonOrderRequest;
import com.example.minhaj.dtos.LessonRequest;
import com.example.minhaj.models.Course;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.services.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.example.minhaj.config.SwaggerConfig.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Lessons", description = "Ordered course lessons: public listing, admin management. Instructors manage their own lessons under Instructor.")
public class LessonController {

    private final CourseRepository courseRepository;
    private final CourseService courseService;

    public LessonController(CourseRepository courseRepository, CourseService courseService) {
        this.courseRepository = courseRepository;
        this.courseService = courseService;
    }

    private Course findCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + courseId));
    }

    // Lessons come back in course order (position 1..n).
    @Operation(summary = "List a course's lessons", description = "Public. Lessons in course order (`position` 1..n).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lessons in order"),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @GetMapping("/courses/{courseId}/lessons")
    public ResponseEntity<List<LessonDTO>> getLessonsByCourse(@PathVariable Long courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Cannot fetch lessons. Course not found with ID: " + courseId);
        }
        return ResponseEntity.ok(courseService.getLessons(courseId).stream().map(LessonDTO::from).toList());
    }

    @Operation(summary = "Add a lesson (admin)",
            description = "Admin only. Appends the lesson to the end of any course.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The created lesson", content = @Content(schema = @Schema(implementation = LessonDTO.class))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @PostMapping("/admin/courses/{courseId}/lessons")
    public ResponseEntity<?> addLesson(@PathVariable Long courseId, @Valid @RequestBody LessonRequest request) {

        return courseRepository.findById(courseId)
                .<ResponseEntity<?>>map(course -> ResponseEntity.ok(LessonDTO.from(courseService.addLesson(course, request))))
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + courseId));
    }

    @Operation(summary = "Reorder lessons (admin)",
            description = "Admin only. `lessonIds` must list every lesson of the course exactly once, in the new order; otherwise 400.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lessons in their new order"),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @PutMapping("/admin/courses/{courseId}/lessons/order")
    public List<LessonDTO> reorderLessons(@PathVariable Long courseId, @Valid @RequestBody LessonOrderRequest request) {
        return courseService.reorderLessons(findCourse(courseId), request.lessonIds()).stream().map(LessonDTO::from).toList();
    }

    @Operation(summary = "Update a lesson (admin)",
            description = "Admin only. 404 if the course does not exist or the lesson is not in it.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated lesson"),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @PutMapping("/admin/courses/{courseId}/lessons/{lessonId}")
    public LessonDTO updateLesson(@PathVariable Long courseId, @PathVariable Long lessonId,
                                  @Valid @RequestBody LessonRequest request) {
        return LessonDTO.from(courseService.updateLesson(findCourse(courseId), lessonId, request));
    }

    @Operation(summary = "Delete a lesson (admin)",
            description = "Admin only. Removes the lesson and its progress records; later lessons move up one position.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deleted", content = @Content(schema = @Schema(ref = MESSAGE_SCHEMA))),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @DeleteMapping("/admin/courses/{courseId}/lessons/{lessonId}")
    public ResponseEntity<?> deleteLesson(@PathVariable Long courseId, @PathVariable Long lessonId) {
        courseService.deleteLesson(findCourse(courseId), lessonId);
        return ResponseEntity.ok(Map.of("message", "Lesson deleted successfully!"));
    }
}
