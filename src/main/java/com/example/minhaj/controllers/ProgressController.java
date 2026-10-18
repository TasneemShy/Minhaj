package com.example.minhaj.controllers;

import com.example.minhaj.dtos.CourseProgressDTO;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.Lesson;
import com.example.minhaj.models.LessonProgress;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.repositories.LessonProgressRepository;
import com.example.minhaj.repositories.LessonRepository;
import com.example.minhaj.repositories.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProgressController {

    private final LessonProgressRepository progressRepository;
    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public ProgressController(LessonProgressRepository progressRepository, LessonRepository lessonRepository,
                              CourseRepository courseRepository, UserRepository userRepository) {
        this.progressRepository = progressRepository;
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/courses/{courseId}/lessons/{lessonId}/complete")
    public ResponseEntity<?> completeLesson(@PathVariable Long courseId, @PathVariable Long lessonId, Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "You must be logged in to track your progress."));
        }

        User student = findStudent(principal);
        Course course = findCourse(courseId);

        if (!isEnrolled(student, courseId)) {
            return ResponseEntity.status(403).body(Map.of("error", "You must be enrolled in this course to track your progress."));
        }

        Lesson lesson = findLessonInCourse(lessonId, courseId);

        if (!progressRepository.existsByUserIdAndLessonId(student.getId(), lesson.getId())) {
            progressRepository.save(new LessonProgress(student, lesson));
        }

        return ResponseEntity.ok(buildProgress(student, course));
    }

    @DeleteMapping("/courses/{courseId}/lessons/{lessonId}/complete")
    public ResponseEntity<?> uncompleteLesson(@PathVariable Long courseId, @PathVariable Long lessonId, Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "You must be logged in to track your progress."));
        }

        User student = findStudent(principal);
        Course course = findCourse(courseId);

        if (!isEnrolled(student, courseId)) {
            return ResponseEntity.status(403).body(Map.of("error", "You must be enrolled in this course to track your progress."));
        }

        Lesson lesson = findLessonInCourse(lessonId, courseId);

        progressRepository.findByUserIdAndLessonId(student.getId(), lesson.getId())
                .ifPresent(progressRepository::delete);

        return ResponseEntity.ok(buildProgress(student, course));
    }

    @GetMapping("/courses/{courseId}/progress")
    public ResponseEntity<?> getCourseProgress(@PathVariable Long courseId, Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "You must be logged in to view your progress."));
        }

        User student = findStudent(principal);
        Course course = findCourse(courseId);

        if (!isEnrolled(student, courseId)) {
            return ResponseEntity.status(403).body(Map.of("error", "You are not enrolled in this course."));
        }

        return ResponseEntity.ok(buildProgress(student, course));
    }

    @GetMapping("/my-courses/progress")
    public ResponseEntity<List<CourseProgressDTO>> getMyProgress(Principal principal) {

        User student = findStudent(principal);

        List<CourseProgressDTO> progress = student.getEnrolledCourses().stream()
                .sorted(Comparator.comparing(Course::getId))
                .map(course -> buildProgress(student, course))
                .toList();

        return ResponseEntity.ok(progress);
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

        return new CourseProgressDTO(course.getId(), course.getTitle(), completed, totalLessons, percentage, completedLessonIds);
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
