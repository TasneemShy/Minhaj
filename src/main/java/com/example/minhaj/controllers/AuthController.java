package com.example.minhaj.controllers;

import com.example.minhaj.config.JwtUtil;
import com.example.minhaj.dtos.LoginRequest;
import com.example.minhaj.dtos.RegisterRequest;
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
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest request) {

        if (userRepository.findByEmail(request.email()).isPresent()) {
            return ResponseEntity.badRequest().body("Error: Email is already registered!");
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole("STUDENT");

        userRepository.save(user);
        emailService.sendWelcomeEmail(user.getEmail(), user.getName());

        return ResponseEntity.ok(generateAuthResponse(user, "User registered and logged in successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@Valid @RequestBody LoginRequest loginRequest) {
        var userOptional = userRepository.findByEmail(loginRequest.email());

        if (userOptional.isPresent()
                && passwordEncoder.matches(loginRequest.password(), userOptional.get().getPassword())) {
            return ResponseEntity.ok(generateAuthResponse(userOptional.get(), "Login successful"));
        }

        return ResponseEntity.status(401).body("Error: Invalid email or password!");
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

        tokenRepository.deleteByUser(user);
        tokenRepository.flush();

        PasswordResetToken resetToken = new PasswordResetToken(token, user);
        tokenRepository.save(resetToken);

        emailService.sendPasswordResetEmail(user.getEmail(), token);

        return ResponseEntity.ok("If the email exists, a reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        String newPassword = request.get("newPassword");

        // Same rule as RegisterRequest; a missing password used to fail with a 500
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body("Error: Password must be at least 6 characters long");
        }

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