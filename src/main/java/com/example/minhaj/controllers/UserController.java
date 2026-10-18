package com.example.minhaj.controllers;

import com.example.minhaj.exceptions.ApiException;
import com.example.minhaj.dtos.UserDTO;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private static final List<String> ROLES = List.of("STUDENT", "INSTRUCTOR", "ADMIN");

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private static UserDTO toDTO(User user) {
        return new UserDTO(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }

    @GetMapping
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserController::toDTO)
                .toList();
    }

    // The user's current JWT still carries the old role; the new one applies after they log in again.
    @PutMapping("/{id}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String role = body.get("role");
        if (role == null || !ROLES.contains(role)) {
            throw ApiException.badRequest("role must be one of: " + String.join(", ", ROLES));
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
        user.setRole(role);
        return ResponseEntity.ok(toDTO(userRepository.save(user)));
    }
}
