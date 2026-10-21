package com.example.minhaj.integration;

import com.example.minhaj.services.TokenCleanupJob;
import com.example.minhaj.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Scheduling is off in the test profile (app.token-cleanup.enabled=false), so the job only runs when called here.
// Times are computed in Java, not with SQL now(): expiry_date has no time zone and is compared with the JVM's clock.
class TokenCleanupIntegrationTest extends IntegrationTestBase {

    private static final Duration DAY = Duration.ofDays(1);

    @Autowired
    private TokenCleanupJob job;

    @Test
    void purgeDeletesOnlyTokensPastTheirRetention() {
        Instant now = Instant.now();
        long user = insertUser("cleanup");

        // Rotation chain X -> Y: X was rotated 22 days ago, Y expired 8 days ago without being used.
        // Deleting Y first (as expired) must not be blocked by X.replaced_by_id pointing at it.
        long y = insertRefreshToken(user, "y", now.minus(DAY.multipliedBy(8)), null, null);
        insertRefreshToken(user, "x", now.plus(DAY.multipliedBy(30)), now.minus(DAY.multipliedBy(22)), y);

        // Chain A -> B -> C: A revoked 8 days ago (past retention), B revoked yesterday (kept for reuse
        // detection), C is the live token.
        long c = insertRefreshToken(user, "c", now.plus(DAY.multipliedBy(13)), null, null);
        long b = insertRefreshToken(user, "b", now.plus(DAY.multipliedBy(12)), now.minus(DAY), c);
        insertRefreshToken(user, "a", now.plus(DAY.multipliedBy(6)), now.minus(DAY.multipliedBy(8)), b);

        // Expired 6 days ago: still inside the grace period
        insertRefreshToken(user, "recently-expired", now.minus(DAY.multipliedBy(6)), null, null);

        insertVerificationToken(user, "verify-expired", now.minusSeconds(60));
        insertVerificationToken(user, "verify-valid", now.plus(DAY));

        // password_reset_token allows one row per user
        insertResetToken(user, "reset-expired", LocalDateTime.now().minusMinutes(1));
        insertResetToken(insertUser("cleanup-2"), "reset-valid", LocalDateTime.now().plusMinutes(10));

        job.purge();

        assertThat(hashes("refresh_tokens", "token_hash")).containsExactly("b", "c", "recently-expired");
        assertThat(jdbc.queryForObject("SELECT replaced_by_id FROM refresh_tokens WHERE token_hash = 'b'", Long.class))
                .isEqualTo(c);
        assertThat(hashes("email_verification_tokens", "token_hash")).containsExactly("verify-valid");
        assertThat(hashes("password_reset_token", "token")).containsExactly("reset-valid");

        // A second run finds nothing more to delete
        job.purge();
        assertThat(count("refresh_tokens")).isEqualTo(3);
    }

    private long insertUser(String prefix) {
        return jdbc.queryForObject("INSERT INTO users (name, email, password, role, email_verified) "
                + "VALUES ('Cleanup', ?, 'x', 'STUDENT', TRUE) RETURNING id", Long.class, uniqueEmail(prefix));
    }

    // Hashes are short labels here; the cleanup never looks at them.
    private long insertRefreshToken(long userId, String hash, Instant expiresAt, Instant revokedAt, Long replacedById) {
        return jdbc.queryForObject("INSERT INTO refresh_tokens (user_id, token_hash, created_at, expires_at, "
                        + "revoked_at, replaced_by_id) VALUES (?, ?, ?, ?, ?, ?) RETURNING id", Long.class,
                userId, hash, Timestamp.from(expiresAt.minus(DAY.multipliedBy(14))), Timestamp.from(expiresAt),
                revokedAt == null ? null : Timestamp.from(revokedAt), replacedById);
    }

    private void insertVerificationToken(long userId, String hash, Instant expiresAt) {
        jdbc.update("INSERT INTO email_verification_tokens (user_id, token_hash, created_at, expires_at) "
                + "VALUES (?, ?, ?, ?)", userId, hash, Timestamp.from(expiresAt.minus(DAY)), Timestamp.from(expiresAt));
    }

    private void insertResetToken(long userId, String hash, LocalDateTime expiryDate) {
        jdbc.update("INSERT INTO password_reset_token (user_id, token, expiry_date) VALUES (?, ?, ?)",
                userId, hash, Timestamp.valueOf(expiryDate));
    }

    private List<String> hashes(String table, String column) {
        return jdbc.queryForList("SELECT " + column + " FROM " + table + " ORDER BY " + column, String.class);
    }
}
