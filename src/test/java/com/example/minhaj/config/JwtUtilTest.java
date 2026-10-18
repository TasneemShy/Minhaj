package com.example.minhaj.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "bWluaGFqLXVuaXQtdGVzdC1zZWNyZXQta2V5LWF0LWxlYXN0LTI1Ni1iaXRzISE=";
    private static final String OTHER_SECRET = "YW5vdGhlci1jb21wbGV0ZWx5LWRpZmZlcmVudC1zZWNyZXQta2V5LTI1Ni1iaXQ=";

    // Minted with jjwt 0.11.5 (the version before the 0.12 upgrade) using SECRET:
    // sub=legacy@minhaj.test, role=INSTRUCTOR, iat=2025-10-09, exp=2100-01-01.
    private static final String LEGACY_0_11_TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsZWdhY3lAbWluaGFqLnRlc3QiLCJyb2xlIjoiSU5TVFJVQ1RPUiIsImlhdCI6MTc2MDAwMDAwMCwiZXhwIjo0MTAyNDQ0ODAwfQ.aOmFDjY9KZM2udWSHmL_95OgC3Gp_zDXVdUOhCzLEc0";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET);

    @Test
    void roundTripPreservesSubjectAndRole() {
        String token = jwtUtil.generateToken("student@minhaj.test", "STUDENT");

        Claims claims = jwtUtil.extractAllClaims(token);

        assertThat(claims.getSubject()).isEqualTo("student@minhaj.test");
        assertThat(claims.get("role", String.class)).isEqualTo("STUDENT");
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                .isEqualTo(86_400_000L);
    }

    @Test
    void tokenIssuedByJjwt011WithSameSecretStillValidates() {
        Claims claims = jwtUtil.extractAllClaims(LEGACY_0_11_TOKEN);

        assertThat(claims.getSubject()).isEqualTo("legacy@minhaj.test");
        assertThat(claims.get("role", String.class)).isEqualTo("INSTRUCTOR");
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = Jwts.builder()
                .subject("student@minhaj.test")
                .claim("role", "STUDENT")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();

        assertThatThrownBy(() -> jwtUtil.extractAllClaims(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        String foreign = new JwtUtil(OTHER_SECRET).generateToken("admin@minhaj.test", "ADMIN");

        assertThatThrownBy(() -> jwtUtil.extractAllClaims(foreign))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void tamperedPayloadIsRejected() {
        String[] parts = jwtUtil.generateToken("student@minhaj.test", "STUDENT").split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forgedPayload = Encoders.BASE64URL.encode(
                payload.replace("\"STUDENT\"", "\"ADMIN\"").getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtUtil.extractAllClaims(tampered))
                .isInstanceOf(SignatureException.class);
    }
}
