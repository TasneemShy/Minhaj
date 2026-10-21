package com.example.minhaj.controllers;

import com.example.minhaj.dtos.CourseDTO;
import com.example.minhaj.dtos.CourseRequest;
import com.example.minhaj.dtos.LessonDTO;
import com.example.minhaj.dtos.LessonOrderRequest;
import com.example.minhaj.dtos.LessonRequest;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.CourseStatus;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.repositories.CourseSpecifications;
import com.example.minhaj.repositories.UserRepository;
import com.example.minhaj.services.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.example.minhaj.config.SwaggerConfig.*;

// Course management for instructors. Instructors may only touch their own courses; admins may act on any course.
@RestController
@RequestMapping("/api/instructor")
@Tag(name = "Instructor", description = "Course and lesson management for instructors (INSTRUCTOR or ADMIN role).")
@SecurityRequirement(name = BEARER_AUTH)
public class InstructorController {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CourseService courseService;

    public InstructorController(CourseRepository courseRepository, UserRepository userRepository, CourseService courseService) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found in the database."));
    }

    // Loads the course and checks that the current user owns it (or is an admin).
    private Course findOwnedCourse(Long id, Authentication authentication) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + id));
        if (!courseService.canManage(course, authentication)) {
            throw new AccessDeniedException("You can only manage your own courses.");
        }
        return course;
    }

    @Operation(summary = "List my courses",
            description = "Instructors and admins. A page of the courses the caller teaches, drafts included, newest first.")
    @Parameter(name = "status", description = "Only courses with this status; omit for all")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of courses"),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN)
    })
    @GetMapping("/courses")
    public Page<CourseDTO> getMyCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) CourseStatus status,
            Authentication authentication) {
        Long instructorId = currentUser(authentication).getId();
        return courseRepository.findAll(
                CourseSpecifications.withFilters(null, null, null, null, null, null, instructorId)
                        .and(CourseSpecifications.hasStatus(status)),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(CourseDTO::from);
    }

    @Operation(summary = "Create a course",
            description = "Instructors and admins. The caller becomes the course's instructor. The course starts as a DRAFT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The created course"),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @PostMapping("/courses")
    public CourseDTO createCourse(@Valid @RequestBody CourseRequest request, Authentication authentication) {
        return CourseDTO.from(courseService.create(request, currentUser(authentication)));
    }

    @Operation(summary = "Update my course",
            description = "Owning instructor or admin. Instructors may only manage their own courses (403 otherwise); admins may act on any course.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated course"),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @PutMapping("/courses/{id}")
    public CourseDTO updateCourse(@PathVariable Long id, @Valid @RequestBody CourseRequest request,
                                  Authentication authentication) {
        return CourseDTO.from(courseService.update(findOwnedCourse(id, authentication), request));
    }

    @Operation(summary = "Delete my course",
            description = "Owning instructor or admin. Deletes the course with its lessons, reviews, enrollments and progress.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deleted", content = @Content(schema = @Schema(ref = MESSAGE_SCHEMA))),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @DeleteMapping("/courses/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id, Authentication authentication) {
        courseService.deleteCourse(findOwnedCourse(id, authentication));
        return ResponseEntity.ok(Map.of("message", "Course deleted successfully!"));
    }

    @Operation(summary = "Publish my course",
            description = "Owning instructor or admin. Makes the course PUBLISHED; 400 if it has no lessons. Publishing a "
                    + "published course has no effect, and `publishedAt` keeps the first publish time.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The published course"),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @PostMapping("/courses/{id}/publish")
    public CourseDTO publishCourse(@PathVariable Long id, Authentication authentication) {
        return CourseDTO.from(courseService.publish(findOwnedCourse(id, authentication)));
    }

    @Operation(summary = "Unpublish my course",
            description = "Owning instructor or admin. Moves the course back to DRAFT. Enrolled students keep their "
                    + "enrollment and progress.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The course, now a draft"),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @PostMapping("/courses/{id}/unpublish")
    public CourseDTO unpublishCourse(@PathVariable Long id, Authentication authentication) {
        return CourseDTO.from(courseService.unpublish(findOwnedCourse(id, authentication)));
    }

    @Operation(summary = "Add a lesson to my course",
            description = "Owning instructor or admin. Appends the lesson to the end of the course.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The created lesson"),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @PostMapping("/courses/{courseId}/lessons")
    public LessonDTO addLesson(@PathVariable Long courseId, @Valid @RequestBody LessonRequest request,
                               Authentication authentication) {
        return LessonDTO.from(courseService.addLesson(findOwnedCourse(courseId, authentication), request));
    }

    @Operation(summary = "Reorder my course's lessons",
            description = "Owning instructor or admin. `lessonIds` must list every lesson of the course exactly once, in the new order; otherwise 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lessons in their new order"),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @PutMapping("/courses/{courseId}/lessons/order")
    public List<LessonDTO> reorderLessons(@PathVariable Long courseId, @Valid @RequestBody LessonOrderRequest request,
                                          Authentication authentication) {
        return courseService.reorderLessons(findOwnedCourse(courseId, authentication), request.lessonIds())
                .stream().map(LessonDTO::from).toList();
    }

    @Operation(summary = "Update a lesson in my course",
            description = "Owning instructor or admin. 404 if the lesson is not in the course.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated lesson"),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @PutMapping("/courses/{courseId}/lessons/{lessonId}")
    public LessonDTO updateLesson(@PathVariable Long courseId, @PathVariable Long lessonId,
                                  @Valid @RequestBody LessonRequest request, Authentication authentication) {
        return LessonDTO.from(courseService.updateLesson(findOwnedCourse(courseId, authentication), lessonId, request));
    }

    @Operation(summary = "Delete a lesson from my course",
            description = "Owning instructor or admin. Removes the lesson and its progress records; later lessons move up one position.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deleted", content = @Content(schema = @Schema(ref = MESSAGE_SCHEMA))),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @DeleteMapping("/courses/{courseId}/lessons/{lessonId}")
    public ResponseEntity<?> deleteLesson(@PathVariable Long courseId, @PathVariable Long lessonId,
                                          Authentication authentication) {
        courseService.deleteLesson(findOwnedCourse(courseId, authentication), lessonId);
        return ResponseEntity.ok(Map.of("message", "Lesson deleted successfully!"));
    }
}
