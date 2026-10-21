package com.example.minhaj.controllers;

import com.example.minhaj.exceptions.ApiException;
import com.example.minhaj.dtos.CompletionDTO;
import com.example.minhaj.dtos.CourseProgressDTO;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.Lesson;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.repositories.LessonProgressRepository;
import com.example.minhaj.repositories.LessonRepository;
import com.example.minhaj.repositories.UserRepository;
import com.example.minhaj.services.CourseCompletionService;
import com.example.minhaj.services.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.example.minhaj.config.SwaggerConfig.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Progress", description = "Lesson completion tracking for enrolled students.")
@SecurityRequirement(name = BEARER_AUTH)
public class ProgressController {

    private final LessonProgressRepository progressRepository;
    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CourseCompletionService completionService;
    private final QuizService quizService;

    public ProgressController(LessonProgressRepository progressRepository, LessonRepository lessonRepository,
                              CourseRepository courseRepository, UserRepository userRepository,
                              CourseCompletionService completionService, QuizService quizService) {
        this.progressRepository = progressRepository;
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.completionService = completionService;
        this.quizService = quizService;
    }

    @Operation(summary = "Mark a lesson completed",
            description = "Enrolled students. Idempotent; returns the caller's updated progress in the course. 403 if not enrolled, 404 if the lesson is not in the course. "
                    + "409 if the lesson has a quiz the caller has not passed (passing the quiz completes the lesson). "
                    + "When this completes the course (its last missing lesson), a congratulations email is sent; repeating the call sends nothing.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated course progress", content = @Content(schema = @Schema(implementation = CourseProgressDTO.class))),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND),
            @ApiResponse(responseCode = "409", ref = CONFLICT)
    })
    @PostMapping("/courses/{courseId}/lessons/{lessonId}/complete")
    public ResponseEntity<?> completeLesson(@PathVariable Long courseId, @PathVariable Long lessonId, Principal principal) {

        if (principal == null) {
            throw ApiException.unauthorized("You must be logged in to track your progress.");
        }

        User student = findStudent(principal);
        Course course = findCourse(courseId);

        if (!isEnrolled(student, courseId)) {
            throw ApiException.forbidden("You must be enrolled in this course to track your progress.");
        }

        Lesson lesson = findLessonInCourse(lessonId, courseId);

        // A lesson with a quiz is completed by passing the quiz; repeating the call on a completed lesson is fine.
        if (!completionService.isLessonCompleted(student, lesson) && quizService.hasQuiz(lesson)
                && !quizService.hasPassed(student, lesson)) {
            throw ApiException.conflict("Lesson " + lessonId + " has a quiz. Pass it (POST /api/courses/" + courseId
                    + "/lessons/" + lessonId + "/quiz/attempts) to complete the lesson.");
        }

        completionService.recordLessonCompleted(student, lesson);

        return ResponseEntity.ok(buildProgress(student, course));
    }

    @Operation(summary = "Mark a lesson not completed",
            description = "Enrolled students. Idempotent; returns the caller's updated progress in the course.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated course progress", content = @Content(schema = @Schema(implementation = CourseProgressDTO.class))),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @DeleteMapping("/courses/{courseId}/lessons/{lessonId}/complete")
    public ResponseEntity<?> uncompleteLesson(@PathVariable Long courseId, @PathVariable Long lessonId, Principal principal) {

        if (principal == null) {
            throw ApiException.unauthorized("You must be logged in to track your progress.");
        }

        User student = findStudent(principal);
        Course course = findCourse(courseId);

        if (!isEnrolled(student, courseId)) {
            throw ApiException.forbidden("You must be enrolled in this course to track your progress.");
        }

        Lesson lesson = findLessonInCourse(lessonId, courseId);

        progressRepository.findByUserIdAndLessonId(student.getId(), lesson.getId())
                .ifPresent(progressRepository::delete);

        return ResponseEntity.ok(buildProgress(student, course));
    }

    @Operation(summary = "Get my progress in a course",
            description = "Enrolled students. 403 if the caller is not enrolled.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Course progress", content = @Content(schema = @Schema(implementation = CourseProgressDTO.class))),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @GetMapping("/courses/{courseId}/progress")
    public ResponseEntity<?> getCourseProgress(@PathVariable Long courseId, Principal principal) {

        if (principal == null) {
            throw ApiException.unauthorized("You must be logged in to view your progress.");
        }

        User student = findStudent(principal);
        Course course = findCourse(courseId);

        if (!isEnrolled(student, courseId)) {
            throw ApiException.forbidden("You are not enrolled in this course.");
        }

        return ResponseEntity.ok(buildProgress(student, course));
    }

    @Operation(summary = "Get my progress in all enrolled courses",
            description = "Authenticated users. One entry per enrolled course, ordered by course id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Progress per course"),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
    })
    @GetMapping("/my-courses/progress")
    public ResponseEntity<List<CourseProgressDTO>> getMyProgress(Principal principal) {

        User student = findStudent(principal);

        List<CourseProgressDTO> progress = student.getEnrolledCourses().stream()
                .sorted(Comparator.comparing(Course::getId))
                .map(course -> buildProgress(student, course))
                .toList();

        return ResponseEntity.ok(progress);
    }

    @Operation(summary = "Get my completion certificate for a course",
            description = "Enrolled students who completed every lesson of the course. 404 if the course does not exist, "
                    + "the caller is not enrolled, or the course is not completed (including courses without lessons).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Completion details", content = @Content(schema = @Schema(implementation = CompletionDTO.class))),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @GetMapping("/courses/{courseId}/completion")
    public CompletionDTO getCompletion(@PathVariable Long courseId, Principal principal) {

        if (principal == null) {
            throw ApiException.unauthorized("You must be logged in to view your progress.");
        }

        User student = findStudent(principal);
        Course course = findCourse(courseId);

        // Not enrolled and not completed look the same: there is no certificate to show.
        Optional<CompletionDTO> completion = isEnrolled(student, courseId)
                ? completionService.findCompletion(student, course)
                : Optional.empty();
        return completion.orElseThrow(() -> new ResourceNotFoundException("You have not completed course " + courseId + "."));
    }

    @Operation(summary = "List my completed courses",
            description = "Authenticated users. Completion details for each enrolled course the caller has completed, "
                    + "ordered by course id; empty when none.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Completed courses", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CompletionDTO.class)))),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
    })
    @GetMapping("/my-courses/completed")
    public List<CompletionDTO> getMyCompletedCourses(Principal principal) {

        User student = findStudent(principal);

        return student.getEnrolledCourses().stream()
                .sorted(Comparator.comparing(Course::getId))
                .flatMap(course -> completionService.findCompletion(student, course).stream())
                .toList();
    }

    private CourseProgressDTO buildProgress(User student, Course course) {
        List<Long> completedLessonIds = progressRepository.findByUserIdAndLessonCourseId(student.getId(), course.getId())
                .stream()
                .map(progress -> progress.getLesson().getId())
                .sorted()
                .toList();

        int totalLessons = (int) lessonRepository.countByCourseId(course.getId());
        int completed = completedLessonIds.size();
        int percentage = totalLessons == 0 ? 0 : (int) Math.round(completed * 100.0 / totalLessons);

        Optional<LocalDateTime> completedAt = completionService.completedAt(student, course);

        return new CourseProgressDTO(course.getId(), course.getTitle(), completed, totalLessons, percentage, completedLessonIds,
                completedAt.isPresent(), completedAt.orElse(null));
    }

    private User findStudent(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found in the database."));
    }

    private Course findCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + courseId));
    }

    private Lesson findLessonInCourse(Long lessonId, Long courseId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with ID: " + lessonId));

        if (!lesson.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Lesson " + lessonId + " does not belong to course " + courseId);
        }
        return lesson;
    }

    private boolean isEnrolled(User student, Long courseId) {
        return student.getEnrolledCourses().stream().anyMatch(course -> course.getId().equals(courseId));
    }
}
