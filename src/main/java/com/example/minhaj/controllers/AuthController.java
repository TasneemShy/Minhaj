package com.example.minhaj.controllers;

import com.example.minhaj.config.JwtUtil;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody User user) {

        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Error: Email is already registered!");
        }

        String hashedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(hashedPassword);

        if (user.getRole() == null || user.getRole().isEmpty()) {
            user.setRole("STUDENT");
        }

        userRepository.save(user);

        return ResponseEntity.ok(generateAuthResponse(user, "User registered and logged in successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody User loginRequest) {
        var userOptional = userRepository.findByEmail(loginRequest.getEmail());

        if (userOptional.isPresent()) {
            User existingUser = userOptional.get();

            if (passwordEncoder.matches(loginRequest.getPassword(), existingUser.getPassword())) {

                return ResponseEntity.ok(generateAuthResponse(existingUser, "Login successful"));

            } else {
                return ResponseEntity.status(401).body("Error: Invalid password!");
            }
        } else {
            return ResponseEntity.status(404).body("Error: User not found!");
        }
    }

    private Map<String, String> generateAuthResponse(User user, String message) {
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        response.put("token", token);
        response.put("role", user.getRole());
        return response;
    }
}