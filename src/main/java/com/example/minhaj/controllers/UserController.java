package com.example.minhaj.controllers;

import com.example.minhaj.exceptions.ApiException;
import com.example.minhaj.dtos.UpdateRoleRequest;
import com.example.minhaj.dtos.UserDTO;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.example.minhaj.config.SwaggerConfig.*;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin – Users", description = "User administration (ADMIN role).")
@SecurityRequirement(name = BEARER_AUTH)
public class UserController {

    private static final List<String> ROLES = List.of("STUDENT", "INSTRUCTOR", "ADMIN");

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private static UserDTO toDTO(User user) {
        return new UserDTO(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isEmailVerified());
    }

    @Operation(summary = "List users",
            description = "Admin only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All users"),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN)
    })
    @GetMapping
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserController::toDTO)
                .toList();
    }

    // The user's current access token still carries the old role; the new one applies from their next refresh or login.
    @Operation(summary = "Change a user's role",
            description = "Admin only. role must be STUDENT, INSTRUCTOR or ADMIN. The user's current JWT keeps the old role until they log in again.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated user", content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @PutMapping("/{id}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id, @RequestBody UpdateRoleRequest request) {
        String role = request.role();
        if (role == null || !ROLES.contains(role)) {
            throw ApiException.badRequest("role must be one of: " + String.join(", ", ROLES));
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
        user.setRole(role);
        return ResponseEntity.ok(toDTO(userRepository.save(user)));
    }
}
