package com.example.minhaj.controllers;

import com.example.minhaj.exceptions.ApiException;
import com.example.minhaj.dtos.ChangePasswordRequest;
import com.example.minhaj.dtos.UpdateProfileRequest;
import com.example.minhaj.dtos.UserDTO;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.repositories.LessonProgressRepository;
import com.example.minhaj.repositories.UserRepository;
import com.example.minhaj.services.AuthTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

import static com.example.minhaj.config.SwaggerConfig.*;

@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profile", description = "The caller's own account: profile, password and enrollments.")
@SecurityRequirement(name = BEARER_AUTH)
public class ProfileController {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final LessonProgressRepository progressRepository;
    private final AuthTokenService authTokenService;
    private final BCryptPasswordEncoder passwordEncoder;

    public ProfileController(UserRepository userRepository, CourseRepository courseRepository,
                             LessonProgressRepository progressRepository, AuthTokenService authTokenService) {
        this.userRepository = userRepository;
        this.authTokenService = authTokenService;
        this.courseRepository = courseRepository;
        this.progressRepository = progressRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Operation(summary = "Get my profile",
            description = "Authenticated users.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's profile"),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
    })
    @GetMapping
    public ResponseEntity<UserDTO> getProfile(Principal principal) {
        return ResponseEntity.ok(toDTO(findCurrentUser(principal)));
    }

    // Only the name can be changed here; email and role are deliberately not editable.
    @Operation(summary = "Update my name",
            description = "Authenticated users. Only the name can be changed; email and role are not editable here.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated profile"),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
    })
    @PutMapping
    public ResponseEntity<UserDTO> updateProfile(@Valid @RequestBody UpdateProfileRequest request, Principal principal) {
        User user = findCurrentUser(principal);
        user.setName(request.name().trim());
        return ResponseEntity.ok(toDTO(userRepository.save(user)));
    }

    @Operation(summary = "Change my password",
            description = "Authenticated users. Signs out every session: all of the user's refresh tokens are revoked, "
                    + "so each device logs in again once its access token expires. "
                    + "400 if the current password is wrong or the new one fails validation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed", content = @Content(schema = @Schema(ref = MESSAGE_SCHEMA))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
    })
    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request, Principal principal) {
        User user = findCurrentUser(principal);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw ApiException.badRequest("Current password is incorrect.");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        authTokenService.revokeAll(user);

        return ResponseEntity.ok(Map.of("message", "Password changed successfully."));
    }

    @Operation(summary = "Unenroll from a course",
            description = "Authenticated users. Also deletes the caller's progress in the course. 400 if not enrolled.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unenrolled", content = @Content(schema = @Schema(ref = MESSAGE_SCHEMA))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @DeleteMapping("/courses/{courseId}")
    @Transactional
    public ResponseEntity<?> unenroll(@PathVariable Long courseId, Principal principal) {
        User user = findCurrentUser(principal);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + courseId));

        boolean removed = user.getEnrolledCourses().removeIf(enrolled -> enrolled.getId().equals(courseId));
        if (!removed) {
            throw ApiException.badRequest("You are not enrolled in this course.");
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
