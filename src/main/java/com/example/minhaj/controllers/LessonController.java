package com.example.minhaj.controllers;

import com.example.minhaj.models.Lesson;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.repositories.LessonRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LessonController {

    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;

    public LessonController(LessonRepository lessonRepository, CourseRepository courseRepository) {
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
    }

    @GetMapping("/courses/{courseId}/lessons")
    public ResponseEntity<List<Lesson>> getLessonsByCourse(@PathVariable Long courseId) {

        if (!courseRepository.existsById(courseId)) {
            return ResponseEntity.notFound().build();
        }

        List<Lesson> lessons = lessonRepository.findByCourseId(courseId);
        return ResponseEntity.ok(lessons);
    }

    @PostMapping("/admin/courses/{courseId}/lessons")
    public ResponseEntity<?> addLesson(@PathVariable Long courseId, @Valid @RequestBody Lesson lessonRequest) {

        return courseRepository.findById(courseId).map(course -> {

            lessonRequest.setCourse(course);
            Lesson savedLesson = lessonRepository.save(lessonRequest);

            course.setLessonCount(course.getLessonCount() + 1);
            courseRepository.save(course);

            return ResponseEntity.ok(savedLesson);

        }).orElseGet(() -> ResponseEntity.notFound().build());
    }
}