package com.example.minhaj.controllers;

import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.repositories.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.example.minhaj.dtos.CourseDTO;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class CourseController {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public CourseController(CourseRepository courseRepository, UserRepository userRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/courses")
    public ResponseEntity<Page<CourseDTO>> getAllCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Course> courses;

        if (search != null && !search.trim().isEmpty()) {
            courses = courseRepository.findByTitleContainingIgnoreCase(search, pageable);
        } else {
            courses = courseRepository.findAll(pageable);
        }

        Page<CourseDTO> courseDTOs = courses.map(course -> new CourseDTO(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getPrice(),
                course.isFree(),
                course.getAverageRating(),
                course.getReviewCount(),
                course.getImageUrl()
        ));

        return ResponseEntity.ok(courseDTOs);
    }

    @GetMapping("/courses/{id}")
    public ResponseEntity<Course> getCourseById(@PathVariable Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + id));
        return ResponseEntity.ok(course);
    }

    @PostMapping("/admin/courses")
    public Course createCourse(@Valid @RequestBody Course course) {
        if (course.isFree()) {
            course.setPrice(0.0);
        }
        return courseRepository.save(course);
    }

    @PutMapping("/admin/courses/{id}")
    public ResponseEntity<Course> updateCourse(@PathVariable Long id, @Valid @RequestBody Course courseDetails) {

        Course existingCourse = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot update. Course not found with ID: " + id));

        existingCourse.setTitle(courseDetails.getTitle());
        existingCourse.setDescription(courseDetails.getDescription());
        existingCourse.setFree(courseDetails.isFree());
        existingCourse.setPrice(courseDetails.isFree() ? 0.0 : courseDetails.getPrice());

        Course updatedCourse = courseRepository.save(existingCourse);
        return ResponseEntity.ok(updatedCourse);
    }

    @DeleteMapping("/admin/courses/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id) {

        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot delete. Course not found with ID: " + id));

        courseRepository.delete(course);
        return ResponseEntity.ok(Map.of("message", "Course deleted successfully!"));
    }

    @PostMapping("/courses/{id}/enroll")
    public ResponseEntity<?> enrollInCourse(@PathVariable Long id, java.security.Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "You must be logged in to enroll in a course."));
        }

        String studentEmail = principal.getName();
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found in the database."));

        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + id));

        student.getEnrolledCourses().add(course);
        userRepository.save(student);

        return ResponseEntity.ok(Map.of("message", "Successfully enrolled in course: " + course.getTitle()));
    }

    @GetMapping("/my-courses")
    public ResponseEntity<?> getMyCourses(java.security.Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "You must be logged in to view your courses."));
        }

        String studentEmail = principal.getName();
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found in the database."));

        Set<Course> myCourses = student.getEnrolledCourses();

        if (myCourses.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "You haven't enrolled in any courses yet."));
        }
        return ResponseEntity.ok(myCourses);
    }
}