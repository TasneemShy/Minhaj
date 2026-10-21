package com.example.minhaj.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;

    // Access tokens are not stored or revocable, so they are kept short-lived (app.jwt.access-ttl, default 15 minutes);
    // clients get new ones from POST /api/auth/refresh.
    private final Duration accessTtl;

    public JwtUtil(@Value("${jwt.secret}") String secret, @Value("${app.jwt.access-ttl:15m}") Duration accessTtl) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTtl = accessTtl;
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public String generateToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTtl.toMillis()))
                .signWith(key)
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
