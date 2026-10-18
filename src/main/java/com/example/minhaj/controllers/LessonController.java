package com.example.minhaj.controllers;

import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Lesson;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.repositories.LessonRepository;
import com.example.minhaj.services.CourseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LessonController {

    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final CourseService courseService;

    public LessonController(LessonRepository lessonRepository, CourseRepository courseRepository, CourseService courseService) {
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
        this.courseService = courseService;
    }

    @GetMapping("/courses/{courseId}/lessons")
    public ResponseEntity<List<Lesson>> getLessonsByCourse(@PathVariable Long courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Cannot fetch lessons. Course not found with ID: " + courseId);
        }
        return ResponseEntity.ok(lessonRepository.findByCourseId(courseId));
    }

    @PostMapping("/admin/courses/{courseId}/lessons")
    public ResponseEntity<?> addLesson(@PathVariable Long courseId, @Valid @RequestBody Lesson lessonRequest) {

        return courseRepository.findById(courseId)
                .<ResponseEntity<?>>map(course -> ResponseEntity.ok(courseService.addLesson(course, lessonRequest)))
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + courseId));
    }
}