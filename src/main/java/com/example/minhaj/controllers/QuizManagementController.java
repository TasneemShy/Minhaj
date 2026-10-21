package com.example.minhaj.controllers;

import com.example.minhaj.dtos.QuizAnalyticsDTO;
import com.example.minhaj.dtos.QuizDTO;
import com.example.minhaj.dtos.QuizRequest;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.Lesson;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.services.CourseService;
import com.example.minhaj.services.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.example.minhaj.config.SwaggerConfig.*;

// Quiz authoring and analytics. Each endpoint answers under /api/instructor (INSTRUCTOR or ADMIN role, own courses
// only for instructors) and /api/admin (ADMIN role, any course), like lesson management.
@RestController
@RequestMapping("/api")
@Tag(name = "Quiz authoring", description = "Create, replace, view and delete a lesson's quiz, and see its statistics "
        + "(owning instructor or admin). Paths exist under both /api/instructor and /api/admin.")
@SecurityRequirement(name = BEARER_AUTH)
public class QuizManagementController {

    private static final String INSTRUCTOR_QUIZ = "/instructor/courses/{courseId}/lessons/{lessonId}/quiz";
    private static final String ADMIN_QUIZ = "/admin/courses/{courseId}/lessons/{lessonId}/quiz";

    private final CourseRepository courseRepository;
    private final CourseService courseService;
    private final QuizService quizService;

    public QuizManagementController(CourseRepository courseRepository, CourseService courseService, QuizService quizService) {
        this.courseRepository = courseRepository;
        this.courseService = courseService;
        this.quizService = quizService;
    }

    // Loads the lesson after checking that the current user owns its course (or is an admin).
    private Lesson findOwnedLesson(Long courseId, Long lessonId, Authentication authentication) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + courseId));
        if (!courseService.canManage(course, authentication)) {
            throw new AccessDeniedException("You can only manage your own courses.");
        }
        return quizService.findLessonInCourse(course, lessonId);
    }

    @Operation(summary = "Create or replace a lesson's quiz",
            description = "Owning instructor or admin. Saves the whole quiz in one request; questions and options keep the "
                    + "order they are sent in. Needs at least one question and 2-20 options per question; a SINGLE_CHOICE "
                    + "question needs exactly one correct option, a MULTIPLE_CHOICE question at least one (400 otherwise). "
                    + "`passPercentage` is 0-100, default 70. A quiz that already has attempts cannot be replaced (409); "
                    + "delete it first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The saved quiz, with correct answers", content = @Content(schema = @Schema(implementation = QuizDTO.class))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND),
            @ApiResponse(responseCode = "409", ref = CONFLICT)
    })
    @PutMapping({INSTRUCTOR_QUIZ, ADMIN_QUIZ})
    @Transactional
    public QuizDTO saveQuiz(@PathVariable Long courseId, @PathVariable Long lessonId,
                            @Valid @RequestBody QuizRequest request, Authentication authentication) {
        return QuizDTO.from(quizService.saveQuiz(findOwnedLesson(courseId, lessonId, authentication), request));
    }

    @Operation(summary = "Get a lesson's quiz with its answers",
            description = "Owning instructor or admin. Includes which options are correct. 404 if the lesson has no quiz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The quiz", content = @Content(schema = @Schema(implementation = QuizDTO.class))),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @GetMapping({INSTRUCTOR_QUIZ, ADMIN_QUIZ})
    @Transactional(readOnly = true)
    public QuizDTO getQuiz(@PathVariable Long courseId, @PathVariable Long lessonId, Authentication authentication) {
        return QuizDTO.from(quizService.findQuiz(findOwnedLesson(courseId, lessonId, authentication)));
    }

    @Operation(summary = "Delete a lesson's quiz",
            description = "Owning instructor or admin. Deletes the quiz with its questions and all attempts. Lessons "
                    + "students already completed stay completed; afterwards the lesson can be completed manually again.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deleted", content = @Content(schema = @Schema(ref = MESSAGE_SCHEMA))),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @DeleteMapping({INSTRUCTOR_QUIZ, ADMIN_QUIZ})
    public ResponseEntity<?> deleteQuiz(@PathVariable Long courseId, @PathVariable Long lessonId,
                                        Authentication authentication) {
        quizService.deleteQuiz(findOwnedLesson(courseId, lessonId, authentication));
        return ResponseEntity.ok(Map.of("message", "Quiz deleted successfully!"));
    }

    @Operation(summary = "Quiz statistics",
            description = "Owning instructor or admin. Attempts (retries included), distinct students who attempted and "
                    + "passed, pass rate (students who passed / students who attempted) and average score over all attempts.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quiz statistics", content = @Content(schema = @Schema(implementation = QuizAnalyticsDTO.class))),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @GetMapping({INSTRUCTOR_QUIZ + "/analytics", ADMIN_QUIZ + "/analytics"})
    public QuizAnalyticsDTO getQuizAnalytics(@PathVariable Long courseId, @PathVariable Long lessonId,
                                             Authentication authentication) {
        return quizService.analytics(quizService.findQuiz(findOwnedLesson(courseId, lessonId, authentication)));
    }
}
