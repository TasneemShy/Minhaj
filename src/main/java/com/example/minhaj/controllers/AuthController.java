package com.example.minhaj.controllers;

import com.example.minhaj.exceptions.ApiException;
import com.example.minhaj.config.JwtUtil;
import com.example.minhaj.dtos.ForgotPasswordRequest;
import com.example.minhaj.dtos.LoginRequest;
import com.example.minhaj.dtos.RegisterRequest;
import com.example.minhaj.dtos.ResetPasswordRequest;
import com.example.minhaj.models.PasswordResetToken;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.PasswordResetTokenRepository;
import com.example.minhaj.repositories.UserRepository;
import com.example.minhaj.services.EmailService;
import com.example.minhaj.services.ResetTokens;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.example.minhaj.config.SwaggerConfig.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Registration, login and password reset. All endpoints are public.")
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

    @Operation(summary = "Register a student account",
            description = "Public. Creates a STUDENT account, sends a welcome email and returns a JWT, so the new user is "
                    + "logged in straight away.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registered; JWT issued",
                    content = @Content(schema = @Schema(ref = AUTH_RESPONSE_SCHEMA))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "409", ref = CONFLICT)
    })
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest request) {

        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw ApiException.conflict("Email is already registered!");
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

    @Operation(summary = "Log in",
            description = "Public. Exchanges email and password for a JWT. Use the token with the **Authorize** button.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logged in; JWT issued",
                    content = @Content(schema = @Schema(ref = AUTH_RESPONSE_SCHEMA))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
    })
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@Valid @RequestBody LoginRequest loginRequest) {
        var userOptional = userRepository.findByEmail(loginRequest.email());

        if (userOptional.isPresent()
                && passwordEncoder.matches(loginRequest.password(), userOptional.get().getPassword())) {
            return ResponseEntity.ok(generateAuthResponse(userOptional.get(), "Login successful"));
        }

        throw ApiException.unauthorized("Invalid email or password!");
    }

    private Map<String, String> generateAuthResponse(User user, String message) {
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        response.put("token", token);
        response.put("role", user.getRole());
        return response;
    }

    @Operation(summary = "Request a password reset email",
            description = "Public. If the email belongs to an account, emails a single-use reset link to the frontend. "
                    + "The response is the same whether or not the account exists.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request accepted",
                    content = @Content(mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "If the email exists, a reset link has been sent."))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        Optional<User> userOptional = userRepository.findByEmail(request.email());

        if (userOptional.isEmpty()) {
            return ResponseEntity.ok("If the email exists, a reset link has been sent.");
        }

        User user = userOptional.get();

        // Only the hash is stored; the raw token exists only in the email.
        String rawToken = ResetTokens.newRawToken();

        tokenRepository.deleteByUser(user);
        tokenRepository.flush();

        PasswordResetToken resetToken = new PasswordResetToken(ResetTokens.hash(rawToken), user);
        tokenRepository.save(resetToken);

        emailService.sendPasswordResetEmail(user.getEmail(), rawToken);

        return ResponseEntity.ok("If the email exists, a reset link has been sent.");
    }

    @Operation(summary = "Reset the password with an emailed token",
            description = "Public. Sets a new password using the token from the reset link. Tokens are single-use and expire; an invalid or expired token gives 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed",
                    content = @Content(mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Password has been reset successfully."))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    })
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        Optional<PasswordResetToken> tokenOptional = tokenRepository.findByToken(ResetTokens.hash(request.token()));

        if (tokenOptional.isEmpty()) {
            throw ApiException.badRequest("Invalid Token.");
        }

        PasswordResetToken resetToken = tokenOptional.get();

        if (resetToken.isExpired()) {
            tokenRepository.delete(resetToken);
            throw ApiException.badRequest("Token has expired.");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        tokenRepository.delete(resetToken);

        return ResponseEntity.ok("Password has been reset successfully.");
    }
}