package com.example.minhaj.controllers;

import com.example.minhaj.dtos.ChangePasswordRequest;
import com.example.minhaj.dtos.UpdateProfileRequest;
import com.example.minhaj.dtos.UserDTO;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.repositories.LessonProgressRepository;
import com.example.minhaj.repositories.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final LessonProgressRepository progressRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public ProfileController(UserRepository userRepository, CourseRepository courseRepository,
                             LessonProgressRepository progressRepository) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.progressRepository = progressRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @GetMapping
    public ResponseEntity<UserDTO> getProfile(Principal principal) {
        return ResponseEntity.ok(toDTO(findCurrentUser(principal)));
    }

    // Only the name can be changed here; email and role are deliberately not editable.
    @PutMapping
    public ResponseEntity<UserDTO> updateProfile(@Valid @RequestBody UpdateProfileRequest request, Principal principal) {
        User user = findCurrentUser(principal);
        user.setName(request.name().trim());
        return ResponseEntity.ok(toDTO(userRepository.save(user)));
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request, Principal principal) {
        User user = findCurrentUser(principal);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect."));
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password changed successfully."));
    }

    @DeleteMapping("/courses/{courseId}")
    @Transactional
    public ResponseEntity<?> unenroll(@PathVariable Long courseId, Principal principal) {
        User user = findCurrentUser(principal);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + courseId));

        boolean removed = user.getEnrolledCourses().removeIf(enrolled -> enrolled.getId().equals(courseId));
        if (!removed) {
            return ResponseEntity.badRequest().body(Map.of("error", "You are not enrolled in this course."));
        }

        userRepository.save(user);
        progressRepository.deleteAll(progressRepository.findByUserIdAndLessonCourseId(user.getId(), courseId));

        return ResponseEntity.ok(Map.of("message", "Successfully unenrolled from course: " + course.getTitle()));
    }

    private User findCurrentUser(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found in the database."));
    }

    private UserDTO toDTO(User user) {
        return new UserDTO(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
