package com.example.minhaj.services;

import com.example.minhaj.config.JwtUtil;
import com.example.minhaj.dtos.AuthResponse;
import com.example.minhaj.exceptions.ApiException;
import com.example.minhaj.models.RefreshToken;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

// Issues access + refresh token pairs, rotates refresh tokens and revokes them.
// Access tokens (JWTs) stay stateless: revoking refresh tokens does not cut off an access token already issued,
// it just expires within app.jwt.access-ttl. Refresh tokens are opaque, single-use and stored as hashes.
@Service
public class AuthTokenService {

    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTtl;

    public AuthTokenService(JwtUtil jwtUtil, RefreshTokenRepository refreshTokenRepository,
                            @Value("${app.jwt.refresh-ttl:14d}") Duration refreshTtl) {
        this.jwtUtil = jwtUtil;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTtl = refreshTtl;
    }

    @Transactional
    public AuthResponse issue(User user, String message) {
        return respond(user, message, newRefreshToken(user, Instant.now()));
    }

    // Rotation: the presented token is revoked and replaced. Presenting a token that was already revoked means
    // it was copied (the client would only hold the newest one), so every refresh token of the user is revoked.
    // noRollbackFor: that mass revocation must be committed even though the request ends in a 401.
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(ResetTokens.hash(rawRefreshToken))
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token."));
        Instant now = Instant.now();

        if (current.isRevoked()) {
            refreshTokenRepository.revokeAllForUser(current.getUser().getId(), now);
            throw ApiException.unauthorized("Refresh token was already used. All sessions have been signed out; log in again.");
        }
        if (current.isExpired(now)) {
            throw ApiException.unauthorized("Refresh token has expired. Log in again.");
        }

        User user = current.getUser();
        IssuedRefreshToken next = newRefreshToken(user, now);
        current.setRevokedAt(now);
        current.setReplacedBy(next.entity());
        return respond(user, "Token refreshed", next);
    }

    // Unknown, expired or already revoked tokens are ignored: logging out twice is not an error,
    // and logout must not trigger the reuse detection above.
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(ResetTokens.hash(rawRefreshToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    // After a password change or reset: every device has to log in again once its access token expires.
    @Transactional
    public void revokeAll(User user) {
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
    }

    private IssuedRefreshToken newRefreshToken(User user, Instant now) {
        String raw = ResetTokens.newRawToken();
        RefreshToken saved = refreshTokenRepository.save(
                new RefreshToken(user, ResetTokens.hash(raw), now, now.plus(refreshTtl)));
        return new IssuedRefreshToken(raw, saved);
    }

    private AuthResponse respond(User user, String message, IssuedRefreshToken refreshToken) {
        String accessToken = jwtUtil.generateToken(user.getEmail(), user.getRole());
        return new AuthResponse(message, accessToken, refreshToken.raw(),
                jwtUtil.getAccessTtl().toSeconds(), user.getRole());
    }

    private record IssuedRefreshToken(String raw, RefreshToken entity) {}
}
