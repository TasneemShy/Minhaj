package com.example.minhaj.controllers;

import com.example.minhaj.dtos.QuizAttemptRequest;
import com.example.minhaj.dtos.QuizAttemptResultDTO;
import com.example.minhaj.dtos.QuizAttemptSummaryDTO;
import com.example.minhaj.dtos.QuizForStudentDTO;
import com.example.minhaj.exceptions.ApiException;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.Lesson;
import com.example.minhaj.models.Quiz;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.QuizAttemptRepository;
import com.example.minhaj.repositories.UserRepository;
import com.example.minhaj.services.CourseService;
import com.example.minhaj.services.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

import static com.example.minhaj.config.SwaggerConfig.*;

// Taking quizzes. Same access rule as lesson progress (enrolled students), and only on PUBLISHED courses.
@RestController
@RequestMapping("/api")
@Tag(name = "Quizzes", description = "Lesson quizzes for enrolled students: view, submit attempts (graded by the server), "
        + "and attempt history. Passing a quiz completes its lesson.")
@SecurityRequirement(name = BEARER_AUTH)
public class QuizController {

    private final UserRepository userRepository;
    private final QuizAttemptRepository attemptRepository;
    private final CourseService courseService;
    private final QuizService quizService;

    public QuizController(UserRepository userRepository, QuizAttemptRepository attemptRepository,
                          CourseService courseService, QuizService quizService) {
        this.userRepository = userRepository;
        this.attemptRepository = attemptRepository;
        this.courseService = courseService;
        this.quizService = quizService;
    }

    private User findStudent(Principal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("You must be logged in to take quizzes.");
        }
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found in the database."));
    }

    // Drafts look missing (404), like everywhere else students look at courses.
    private Quiz findQuizForStudent(User student, Long courseId, Long lessonId) {
        Course course = courseService.findPublishedCourse(courseId);
        boolean enrolled = student.getEnrolledCourses().stream().anyMatch(c -> c.getId().equals(courseId));
        if (!enrolled) {
            throw ApiException.forbidden("You must be enrolled in this course to take its quizzes.");
        }
        Lesson lesson = quizService.findLessonInCourse(course, lessonId);
        return quizService.findQuiz(lesson);
    }

    @Operation(summary = "Get a lesson's quiz",
            description = "Enrolled students, PUBLISHED courses only. Questions and options without the correct answers. "
                    + "403 if not enrolled; 404 if the course is a draft or missing, or the lesson has no quiz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The quiz", content = @Content(schema = @Schema(implementation = QuizForStudentDTO.class))),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @GetMapping("/courses/{courseId}/lessons/{lessonId}/quiz")
    @Transactional(readOnly = true)
    public QuizForStudentDTO getQuiz(@PathVariable Long courseId, @PathVariable Long lessonId, Principal principal) {
        return QuizForStudentDTO.from(findQuizForStudent(findStudent(principal), courseId, lessonId));
    }

    @Operation(summary = "Submit a quiz attempt",
            description = "Enrolled students, PUBLISHED courses only. Graded on the server: a question scores only if the "
                    + "selected options are exactly its correct options; unanswered questions score 0. "
                    + "score = correct questions / total x 100, rounded down; passed = score >= passPercentage. "
                    + "Every question gets correct/incorrect; the correct options are revealed only when the attempt passes. "
                    + "Passing marks the lesson completed (and can complete the course, sending its email). "
                    + "Attempts are unlimited and all are kept. 400 for unknown question ids, options of another question "
                    + "or a question answered twice.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The graded attempt", content = @Content(schema = @Schema(implementation = QuizAttemptResultDTO.class))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @PostMapping("/courses/{courseId}/lessons/{lessonId}/quiz/attempts")
    @Transactional
    public QuizAttemptResultDTO submitAttempt(@PathVariable Long courseId, @PathVariable Long lessonId,
                                              @Valid @RequestBody QuizAttemptRequest request, Principal principal) {
        User student = findStudent(principal);
        return quizService.submit(findQuizForStudent(student, courseId, lessonId), student, request);
    }

    @Operation(summary = "List my quiz attempts",
            description = "Authenticated users. The caller's own attempts, newest first; empty when none.")
    @Parameter(name = "courseId", description = "Only attempts on quizzes of this course; omit for all courses")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Attempts", content = @Content(array = @ArraySchema(schema = @Schema(implementation = QuizAttemptSummaryDTO.class)))),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
    })
    @GetMapping("/my-quiz-attempts")
    public List<QuizAttemptSummaryDTO> getMyAttempts(@RequestParam(required = false) Long courseId, Principal principal) {
        User student = findStudent(principal);
        return attemptRepository.findHistory(student.getId(), courseId).stream()
                .map(QuizAttemptSummaryDTO::from)
                .toList();
    }
}
