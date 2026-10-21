package com.example.minhaj.controllers;

import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.dtos.LessonDTO;
import com.example.minhaj.dtos.LessonOrderRequest;
import com.example.minhaj.dtos.LessonRequest;
import com.example.minhaj.models.Course;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.services.CourseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
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
    @GetMapping("/courses/{courseId}/lessons")
    public ResponseEntity<List<LessonDTO>> getLessonsByCourse(@PathVariable Long courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Cannot fetch lessons. Course not found with ID: " + courseId);
        }
        return ResponseEntity.ok(courseService.getLessons(courseId).stream().map(LessonDTO::from).toList());
    }

    @PostMapping("/admin/courses/{courseId}/lessons")
    public ResponseEntity<?> addLesson(@PathVariable Long courseId, @Valid @RequestBody LessonRequest request) {

        return courseRepository.findById(courseId)
                .<ResponseEntity<?>>map(course -> ResponseEntity.ok(LessonDTO.from(courseService.addLesson(course, request))))
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + courseId));
    }

    @PutMapping("/admin/courses/{courseId}/lessons/order")
    public List<LessonDTO> reorderLessons(@PathVariable Long courseId, @Valid @RequestBody LessonOrderRequest request) {
        return courseService.reorderLessons(findCourse(courseId), request.lessonIds()).stream().map(LessonDTO::from).toList();
    }

    @PutMapping("/admin/courses/{courseId}/lessons/{lessonId}")
    public LessonDTO updateLesson(@PathVariable Long courseId, @PathVariable Long lessonId,
                                  @Valid @RequestBody LessonRequest request) {
        return LessonDTO.from(courseService.updateLesson(findCourse(courseId), lessonId, request));
    }

    @DeleteMapping("/admin/courses/{courseId}/lessons/{lessonId}")
    public ResponseEntity<?> deleteLesson(@PathVariable Long courseId, @PathVariable Long lessonId) {
        courseService.deleteLesson(findCourse(courseId), lessonId);
        return ResponseEntity.ok(Map.of("message", "Lesson deleted successfully!"));
    }
}
