package com.example.minhaj.controllers;

import com.example.minhaj.config.JwtUtil;
import com.example.minhaj.models.PasswordResetToken;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.PasswordResetTokenRepository;
import com.example.minhaj.repositories.UserRepository;
import com.example.minhaj.services.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Autowired
    private EmailService emailService;
    @Autowired
    private PasswordResetTokenRepository tokenRepository;

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
        emailService.sendWelcomeEmail(user.getEmail(), user.getName());

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

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            return ResponseEntity.ok("If the email exists, a reset link has been sent.");
        }

        User user = userOptional.get();

        String token = java.util.UUID.randomUUID().toString();

        PasswordResetToken resetToken = new PasswordResetToken(token, user);
        tokenRepository.save(resetToken);

        emailService.sendPasswordResetEmail(user.getEmail(), token);

        return ResponseEntity.ok("If the email exists, a reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        String newPassword = request.get("newPassword");

        Optional<PasswordResetToken> tokenOptional = tokenRepository.findByToken(token);

        if (tokenOptional.isEmpty()) {
            return ResponseEntity.badRequest().body("Error: Invalid Token.");
        }

        PasswordResetToken resetToken = tokenOptional.get();

        if (resetToken.isExpired()) {
            tokenRepository.delete(resetToken);
            return ResponseEntity.badRequest().body("Error: Token has expired.");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenRepository.delete(resetToken);

        return ResponseEntity.ok("Password has been reset successfully.");
    }
}