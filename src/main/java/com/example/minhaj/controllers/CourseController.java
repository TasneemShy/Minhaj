package com.example.minhaj.controllers;

import com.example.minhaj.models.Course;
import com.example.minhaj.repositories.CourseRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CourseController {

    private final CourseRepository courseRepository;
    private final com.example.minhaj.repositories.UserRepository userRepository;

    public CourseController(CourseRepository courseRepository, com.example.minhaj.repositories.UserRepository userRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/courses")
    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    @PostMapping("/admin/courses")
    public Course createCourse(@Valid @RequestBody Course course) {

        if (course.isFree()) {
            course.setPrice(0.0);
        }

        return courseRepository.save(course);
    }

    @PutMapping("/admin/courses/{id}")
    public ResponseEntity<?> updateCourse(@PathVariable Long id, @Valid @RequestBody Course courseDetails) {

        return courseRepository.findById(id).map(existingCourse -> {
            existingCourse.setTitle(courseDetails.getTitle());
            existingCourse.setDescription(courseDetails.getDescription());
            existingCourse.setFree(courseDetails.isFree());

            existingCourse.setPrice(courseDetails.isFree() ? 0.0 : courseDetails.getPrice());

            Course updatedCourse = courseRepository.save(existingCourse);
            return ResponseEntity.ok(updatedCourse);

        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/admin/courses/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id) {

        return courseRepository.findById(id).map(course -> {
            courseRepository.delete(course);
            return ResponseEntity.ok().body("Course deleted successfully!");
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/courses/{id}/enroll")
    public ResponseEntity<?> enrollInCourse(@PathVariable Long id, java.security.Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(401).body("You must be logged in to enroll in a course.");
        }

        String studentEmail = principal.getName();

        return courseRepository.findById(id).map(course -> {

            var userOptional = userRepository.findByEmail(studentEmail);
            if (userOptional.isPresent()) {
                var student = userOptional.get();

                student.getEnrolledCourses().add(course);
                userRepository.save(student);

                return ResponseEntity.ok().body("Successfully enrolled in course: " + course.getTitle());
            }

            return ResponseEntity.badRequest().body("Student not found.");

        }).orElseGet(() -> ResponseEntity.notFound().build());
    }
}