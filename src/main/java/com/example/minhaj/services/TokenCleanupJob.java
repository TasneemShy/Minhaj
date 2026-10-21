package com.example.minhaj.services;

import com.example.minhaj.repositories.EmailVerificationTokenRepository;
import com.example.minhaj.repositories.PasswordResetTokenRepository;
import com.example.minhaj.repositories.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

// Tokens are only revoked or deleted when used, so without this the token tables grow forever.
// Revoked refresh tokens are kept for a grace period on purpose: if one comes back, AuthTokenService
// recognises the reuse and revokes all of the user's tokens. Expired ones get the same grace period,
// so a client that comes back late still hears "expired" rather than "invalid".
@Component
public class TokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupJob.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final boolean enabled;
    private final Duration revokedRetention;

    public TokenCleanupJob(RefreshTokenRepository refreshTokenRepository,
                           EmailVerificationTokenRepository verificationTokenRepository,
                           PasswordResetTokenRepository resetTokenRepository,
                           @Value("${app.token-cleanup.enabled:true}") boolean enabled,
                           @Value("${app.token-cleanup.revoked-retention:7d}") Duration revokedRetention) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.enabled = enabled;
        this.revokedRetention = revokedRetention;
    }

    @Scheduled(cron = "${app.token-cleanup.cron:0 0 * * * *}")
    public void scheduledPurge() {
        if (enabled) {
            purge();
        }
    }

    // Each delete runs in its own transaction (see the repositories); they don't depend on each other.
    public void purge() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(revokedRetention);
        logDeleted("expired refresh tokens", refreshTokenRepository.deleteExpiredBefore(cutoff));
        logDeleted("revoked refresh tokens", refreshTokenRepository.deleteRevokedBefore(cutoff));
        logDeleted("expired email verification tokens", verificationTokenRepository.deleteExpiredBefore(now));
        logDeleted("expired password reset tokens", resetTokenRepository.deleteExpiredBefore(LocalDateTime.now()));
    }

    private static void logDeleted(String what, int count) {
        if (count > 0) {
            log.info("Token cleanup deleted {} {}", count, what);
        } else {
            log.debug("Token cleanup deleted no {}", what);
        }
    }
}
