package com.example.minhaj.controllers;

import com.example.minhaj.exceptions.ApiException;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.repositories.CourseSpecifications;
import com.example.minhaj.repositories.UserRepository;
import com.example.minhaj.services.CourseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.example.minhaj.dtos.CourseDTO;
import com.example.minhaj.dtos.CourseRequest;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CourseController {

    private static final List<String> SORTABLE_FIELDS = List.of("createdAt", "title", "price", "averageRating", "reviewCount");

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CourseService courseService;

    public CourseController(CourseRepository courseRepository, UserRepository userRepository, CourseService courseService) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
    }

    @GetMapping("/courses")
    public ResponseEntity<?> getAllCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean free,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Long instructorId,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw ApiException.badRequest("sortBy must be one of: " + String.join(", ", SORTABLE_FIELDS));
        }

        Sort sort = Sort.by("asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Course> courses = courseRepository.findAll(
                CourseSpecifications.withFilters(search, categoryId, free, minPrice, maxPrice, minRating, instructorId), pageable);

        Page<CourseDTO> courseDTOs = courses.map(CourseDTO::from);

        return ResponseEntity.ok(courseDTOs);
    }

    @GetMapping("/courses/{id}")
    public ResponseEntity<CourseDTO> getCourseById(@PathVariable Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + id));
        return ResponseEntity.ok(CourseDTO.from(course));
    }

    @PostMapping("/admin/courses")
    public CourseDTO createCourse(@Valid @RequestBody CourseRequest request) {
        return CourseDTO.from(courseService.create(request, null));
    }

    @PutMapping("/admin/courses/{id}")
    public ResponseEntity<CourseDTO> updateCourse(@PathVariable Long id, @Valid @RequestBody CourseRequest request) {

        Course existingCourse = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot update. Course not found with ID: " + id));

        return ResponseEntity.ok(CourseDTO.from(courseService.update(existingCourse, request)));
    }

    @DeleteMapping("/admin/courses/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id) {

        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot delete. Course not found with ID: " + id));

        courseService.deleteCourse(course);
        return ResponseEntity.ok(Map.of("message", "Course deleted successfully!"));
    }

    @PostMapping("/courses/{id}/enroll")
    public ResponseEntity<?> enrollInCourse(@PathVariable Long id, java.security.Principal principal) {

        if (principal == null) {
            throw ApiException.unauthorized("You must be logged in to enroll in a course.");
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
    public ResponseEntity<List<CourseDTO>> getMyCourses(java.security.Principal principal) {

        if (principal == null) {
            throw ApiException.unauthorized("You must be logged in to view your courses.");
        }

        String studentEmail = principal.getName();
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found in the database."));

        // An empty list when the student has not enrolled anywhere yet
        List<CourseDTO> myCourses = student.getEnrolledCourses().stream()
                .sorted(Comparator.comparing(Course::getId))
                .map(CourseDTO::from)
                .toList();
        return ResponseEntity.ok(myCourses);
    }
}