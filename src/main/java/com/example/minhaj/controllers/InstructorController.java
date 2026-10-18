package com.example.minhaj.controllers;

import com.example.minhaj.dtos.CourseDTO;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.Lesson;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.repositories.CourseSpecifications;
import com.example.minhaj.repositories.UserRepository;
import com.example.minhaj.services.CourseService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Course management for instructors. Instructors may only touch their own courses; admins may act on any course.
@RestController
@RequestMapping("/api/instructor")
public class InstructorController {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CourseService courseService;

    public InstructorController(CourseRepository courseRepository, UserRepository userRepository, CourseService courseService) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found in the database."));
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    // Loads the course and checks that the current user owns it (or is an admin).
    private Course findOwnedCourse(Long id, Authentication authentication) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + id));
        if (isAdmin(authentication)) {
            return course;
        }
        User instructor = course.getInstructor();
        if (instructor == null || !instructor.getId().equals(currentUser(authentication).getId())) {
            throw new AccessDeniedException("You can only manage your own courses.");
        }
        return course;
    }

    @GetMapping("/courses")
    public Page<CourseDTO> getMyCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        Long instructorId = currentUser(authentication).getId();
        return courseRepository.findAll(
                CourseSpecifications.withFilters(null, null, null, null, null, null, instructorId),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(CourseService::toDTO);
    }

    @PostMapping("/courses")
    public Course createCourse(@Valid @RequestBody Course course, Authentication authentication) {
        return courseService.create(course, currentUser(authentication));
    }

    @PutMapping("/courses/{id}")
    public Course updateCourse(@PathVariable Long id, @Valid @RequestBody Course courseDetails,
                               Authentication authentication) {
        return courseService.update(findOwnedCourse(id, authentication), courseDetails);
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id, Authentication authentication) {
        courseRepository.delete(findOwnedCourse(id, authentication));
        return ResponseEntity.ok(Map.of("message", "Course deleted successfully!"));
    }

    @PostMapping("/courses/{courseId}/lessons")
    public Lesson addLesson(@PathVariable Long courseId, @Valid @RequestBody Lesson lessonRequest,
                            Authentication authentication) {
        return courseService.addLesson(findOwnedCourse(courseId, authentication), lessonRequest);
    }
}
