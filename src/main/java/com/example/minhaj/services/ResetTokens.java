package com.example.minhaj.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// Password reset tokens: the raw token only goes into the email; the database stores its SHA-256 hash,
// so reading the password_reset_token table is not enough to reset someone's password.
// Lookup is by hash, so no constant-time comparison is needed.
public final class ResetTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ResetTokens() {}

    // 32 random bytes (256 bits) as URL-safe base64 without padding: 43 characters, safe in a query string.
    public static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Lowercase hex SHA-256 (64 characters); this is what goes in the token column.
    public static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
