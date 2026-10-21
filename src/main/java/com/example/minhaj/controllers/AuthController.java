package com.example.minhaj.controllers;

import com.example.minhaj.exceptions.ApiException;
import com.example.minhaj.dtos.AuthResponse;
import com.example.minhaj.dtos.ForgotPasswordRequest;
import com.example.minhaj.dtos.LoginRequest;
import com.example.minhaj.dtos.RefreshTokenRequest;
import com.example.minhaj.dtos.RegisterRequest;
import com.example.minhaj.dtos.ResendVerificationRequest;
import com.example.minhaj.dtos.ResetPasswordRequest;
import com.example.minhaj.dtos.VerifyEmailRequest;
import com.example.minhaj.models.PasswordResetToken;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.PasswordResetTokenRepository;
import com.example.minhaj.repositories.UserRepository;
import com.example.minhaj.services.AuthTokenService;
import com.example.minhaj.services.EmailService;
import com.example.minhaj.services.EmailVerificationService;
import com.example.minhaj.services.RateLimiter;
import com.example.minhaj.services.ResetTokens;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.Map;
import java.util.Optional;

import static com.example.minhaj.config.SwaggerConfig.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Registration, login, token refresh, logout, email verification and password reset. "
        + "All endpoints are public; login, register, forgot-password, reset-password and resend-verification are rate "
        + "limited (429 with Retry-After).")
public class AuthController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final EmailService emailService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetTokenRepository tokenRepository;
    private final RateLimiter rateLimiter;

    public AuthController(UserRepository userRepository, AuthTokenService authTokenService, EmailService emailService,
                          EmailVerificationService emailVerificationService,
                          PasswordResetTokenRepository tokenRepository, RateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.authTokenService = authTokenService;
        this.emailService = emailService;
        this.emailVerificationService = emailVerificationService;
        this.tokenRepository = tokenRepository;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Operation(summary = "Register a student account",
            description = "Public. Creates an unverified STUDENT account, sends a welcome email with an email "
                    + "verification link and returns an access token and a refresh token, so the new user is logged in "
                    + "straight away. Until the email is verified, enrolling and posting reviews give 403.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registered; tokens issued",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "409", ref = CONFLICT),
            @ApiResponse(responseCode = "429", ref = TOO_MANY_REQUESTS)
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> registerUser(@Valid @RequestBody RegisterRequest request) {

        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw ApiException.conflict("Email is already registered!");
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole("STUDENT");
        user.setEmailVerified(false);

        userRepository.save(user);
        emailVerificationService.sendWelcome(user);

        return ResponseEntity.ok(authTokenService.issue(user, "User registered and logged in successfully"));
    }

    @Operation(summary = "Log in",
            description = "Public. Exchanges email and password for an access token (JWT, default 15 minutes) and a "
                    + "refresh token (default 14 days). Use `accessToken` with the **Authorize** button. "
                    + "Limited per email and client IP (default 5 per minute) and per IP (default 20 per minute).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logged in; tokens issued",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "429", ref = TOO_MANY_REQUESTS)
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> loginUser(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest httpRequest) {
        // The per-IP limit already ran in RateLimitFilter; this one needs the email from the body.
        rateLimiter.checkLogin(loginRequest.email(), rateLimiter.clientIp(httpRequest));

        var userOptional = userRepository.findByEmail(loginRequest.email());

        if (userOptional.isPresent()
                && passwordEncoder.matches(loginRequest.password(), userOptional.get().getPassword())) {
            return ResponseEntity.ok(authTokenService.issue(userOptional.get(), "Login successful"));
        }

        throw ApiException.unauthorized("Invalid email or password!");
    }

    @Operation(summary = "Get a new token pair with a refresh token",
            description = "Public. Exchanges a refresh token for a new access token and a new refresh token. Refresh tokens "
                    + "are single-use: the one sent is revoked. Sending a refresh token that was already used revokes all "
                    + "of the user's refresh tokens (it was probably stolen) and gives 401.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New tokens issued",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authTokenService.refresh(request.refreshToken()));
    }

    @Operation(summary = "Log out",
            description = "Public (the refresh token is the credential). Revokes the refresh token so it cannot be used "
                    + "again. Unknown or already revoked tokens are accepted too. The access token is not revoked; it "
                    + "stays valid until it expires, so clients should also discard it.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logged out"),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authTokenService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Verify the email address",
            description = "Public. Marks the account's email as verified using the token from the verification link. "
                    + "Tokens are single-use and valid for 24 hours; an invalid or expired token gives 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verified",
                    content = @Content(schema = @Schema(ref = MESSAGE_SCHEMA))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    })
    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
        return ResponseEntity.ok(Map.of("message", "Email verified successfully."));
    }

    @Operation(summary = "Resend the verification email",
            description = "Public. If the email belongs to an unverified account, emails a new verification link and "
                    + "invalidates the previous one. The response is the same whether or not the account exists or is "
                    + "already verified.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request accepted",
                    content = @Content(schema = @Schema(ref = MESSAGE_SCHEMA))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "429", ref = TOO_MANY_REQUESTS)
    })
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resend(request.email());
        return ResponseEntity.ok(Map.of("message",
                "If the account exists and is not verified yet, a verification link has been sent."));
    }

    @Operation(summary = "Request a password reset email",
            description = "Public. If the email belongs to an account, emails a single-use reset link to the frontend. "
                    + "The response is the same whether or not the account exists.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request accepted",
                    content = @Content(mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "If the email exists, a reset link has been sent."))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "429", ref = TOO_MANY_REQUESTS)
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
            description = "Public. Sets a new password using the token from the reset link and signs out every session "
                    + "(all refresh tokens are revoked). Following the emailed link also marks the email as verified. Tokens are single-use and expire; an invalid or expired token gives 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed",
                    content = @Content(mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Password has been reset successfully."))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "429", ref = TOO_MANY_REQUESTS)
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
        authTokenService.revokeAll(user);
        emailVerificationService.markVerified(user);

        tokenRepository.delete(resetToken);

        return ResponseEntity.ok("Password has been reset successfully.");
    }
}