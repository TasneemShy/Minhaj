package com.example.minhaj.services;

import com.example.minhaj.exceptions.ApiException;
import com.example.minhaj.models.EmailVerificationToken;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.EmailVerificationTokenRepository;
import com.example.minhaj.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

// New accounts start unverified and get an emailed link (<frontend-url>/verify-email?token=...).
// Unverified users can log in and use the API, except for the actions that call requireVerified():
// enrolling in a course and posting a review.
@Service
public class EmailVerificationService {

    static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository, UserRepository userRepository,
                                    EmailService emailService) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    public static void requireVerified(User user, String action) {
        if (!user.isEmailVerified()) {
            throw ApiException.forbidden("Verify your email address to " + action
                    + ". Use the link we emailed you, or request a new one with POST /api/auth/resend-verification.");
        }
    }

    // Welcome email for a new account, with its first verification link.
    @Transactional
    public void sendWelcome(User user) {
        emailService.sendWelcomeEmail(user.getEmail(), user.getName(), newToken(user));
    }

    // Replaces any earlier link. Does nothing for unknown or already verified emails; the caller answers the same
    // either way so the endpoint does not reveal which emails are registered.
    @Transactional
    public void resend(String email) {
        userRepository.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> emailService.sendVerificationEmail(user.getEmail(), newToken(user)));
    }

    // noRollbackFor: an expired token is deleted even though the request ends in a 400.
    @Transactional(noRollbackFor = ApiException.class)
    public void verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByTokenHash(ResetTokens.hash(rawToken))
                .orElseThrow(() -> ApiException.badRequest("Invalid verification token."));
        User user = token.getUser();

        if (token.isExpired(Instant.now())) {
            tokenRepository.delete(token);
            throw ApiException.badRequest("Verification token has expired. Request a new one.");
        }

        user.setEmailVerified(true);
        userRepository.save(user);
        tokenRepository.deleteByUser(user);
    }

    // A completed password reset proves the user controls the mailbox as well.
    @Transactional
    public void markVerified(User user) {
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            userRepository.save(user);
            tokenRepository.deleteByUser(user);
        }
    }

    private String newToken(User user) {
        tokenRepository.deleteByUser(user);
        String raw = ResetTokens.newRawToken();
        Instant now = Instant.now();
        tokenRepository.save(new EmailVerificationToken(user, ResetTokens.hash(raw), now, now.plus(TOKEN_TTL)));
        return raw;
    }
}
