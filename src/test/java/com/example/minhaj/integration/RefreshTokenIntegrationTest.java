package com.example.minhaj.integration;

import com.example.minhaj.models.PasswordResetToken;
import com.example.minhaj.repositories.PasswordResetTokenRepository;
import com.example.minhaj.services.ResetTokens;
import com.example.minhaj.support.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefreshTokenIntegrationTest extends IntegrationTestBase {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    @Test
    void loginReturnsTokenPairWithDeprecatedTokenAlias() throws Exception {
        String email = uniqueEmail("pair");
        studentToken(email);

        String body = loginResponse(email, PASSWORD);
        String accessToken = JsonPath.read(body, "$.accessToken");
        String refreshToken = JsonPath.read(body, "$.refreshToken");

        assertThat((Integer) JsonPath.read(body, "$.expiresIn")).isEqualTo(900);
        assertThat((String) JsonPath.read(body, "$.role")).isEqualTo("STUDENT");
        assertThat((String) JsonPath.read(body, "$.token")).isEqualTo(accessToken);
        assertThat(refreshToken).matches("[A-Za-z0-9_-]{43}");

        // app.jwt.access-ttl defaults to 15 minutes
        Claims claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret))).build()
                .parseSignedClaims(accessToken).getPayload();
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(900_000L);

        // Only hashes are stored: one from register, one from this login
        List<String> stored = jdbc.queryForList("SELECT token_hash FROM refresh_tokens ORDER BY id", String.class);
        assertThat(stored).hasSize(2).contains(ResetTokens.hash(refreshToken)).doesNotContain(refreshToken);
        assertThat(stored).allMatch(hash -> hash.matches("[0-9a-f]{64}"));
        assertThat(jdbc.queryForObject("SELECT expires_at - created_at = interval '14 days' FROM refresh_tokens "
                + "WHERE token_hash = ?", Boolean.class, ResetTokens.hash(refreshToken))).isTrue();
    }

    @Test
    void registerReturnsTokenPairToo() throws Exception {
        mockMvc.perform(withJson(post("/api/auth/register"), null, json("name", "New", "email", uniqueEmail("new"), "password", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void refreshRotatesTheRefreshToken() throws Exception {
        String email = uniqueEmail("rotate");
        studentToken(email);
        String first = refreshTokenOf(loginResponse(email, PASSWORD));

        String body = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andReturn().getResponse().getContentAsString();
        String second = refreshTokenOf(body);
        String accessToken = JsonPath.read(body, "$.accessToken");
        assertThat(second).isNotEqualTo(first);
        assertThat((String) JsonPath.read(body, "$.token")).isEqualTo(accessToken);

        mockMvc.perform(auth(get("/api/profile"), accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        // The old row is revoked and points at its replacement
        Long secondId = jdbc.queryForObject("SELECT id FROM refresh_tokens WHERE token_hash = ?", Long.class, ResetTokens.hash(second));
        assertThat(jdbc.queryForMap("SELECT revoked_at, replaced_by_id FROM refresh_tokens WHERE token_hash = ?",
                ResetTokens.hash(first)))
                .satisfies(row -> assertThat(row.get("revoked_at")).isNotNull())
                .satisfies(row -> assertThat(row.get("replaced_by_id")).isEqualTo(secondId));

        // The new one works once more
        refresh(second).andExpect(status().isOk());
    }

    @Test
    void reusingARevokedRefreshTokenRevokesAllOfTheUsersTokens() throws Exception {
        String email = uniqueEmail("theft");
        studentToken(email);
        String laptop = refreshTokenOf(loginResponse(email, PASSWORD));
        String phone = refreshTokenOf(loginResponse(email, PASSWORD));
        String otherUser = refreshTokenOf(loginResponse(registerAndReturnEmail("bystander"), PASSWORD));

        String rotated = refreshTokenOf(refresh(laptop).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        // An attacker replays the copied, already rotated token
        refresh(laptop)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "Refresh token was already used. All sessions have been signed out; log in again."));

        // Every token of that user is now dead, including the legitimate rotated one and other devices
        refresh(rotated).andExpect(status().isUnauthorized());
        refresh(phone).andExpect(status().isUnauthorized());
        assertThat(activeRefreshTokens(email)).isZero();

        // Other users are untouched
        refresh(otherUser).andExpect(status().isOk());
    }

    @Test
    void refreshRejectsUnknownExpiredAndBlankTokens() throws Exception {
        refresh("made-up-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid refresh token."));

        String email = uniqueEmail("expired");
        studentToken(email);
        String token = refreshTokenOf(loginResponse(email, PASSWORD));
        jdbc.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 minute' WHERE token_hash = ?",
                ResetTokens.hash(token));
        refresh(token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Refresh token has expired. Log in again."));

        mockMvc.perform(withJson(post("/api/auth/refresh"), null, json("refreshToken", " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.refreshToken").value("Refresh token cannot be empty"));
    }

    @Test
    void storedHashIsNotAUsableRefreshToken() throws Exception {
        String email = uniqueEmail("leak");
        studentToken(email);
        loginResponse(email, PASSWORD);
        String storedHash = jdbc.queryForList("SELECT token_hash FROM refresh_tokens", String.class).get(0);

        refresh(storedHash).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshTokenAndIsIdempotent() throws Exception {
        String email = uniqueEmail("logout");
        studentToken(email);
        String token = refreshTokenOf(loginResponse(email, PASSWORD));

        logout(token).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM refresh_tokens WHERE token_hash = ?",
                Boolean.class, ResetTokens.hash(token))).isTrue();
        refresh(token).andExpect(status().isUnauthorized());

        logout(token).andExpect(status().isNoContent());
        logout("never-issued").andExpect(status().isNoContent());
        mockMvc.perform(withJson(post("/api/auth/logout"), null, "{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changingThePasswordRevokesAllRefreshTokens() throws Exception {
        String email = uniqueEmail("change");
        studentToken(email);
        String body = loginResponse(email, PASSWORD);
        String accessToken = JsonPath.read(body, "$.accessToken");
        String refreshToken = refreshTokenOf(body);

        mockMvc.perform(withJson(put("/api/profile/password"), accessToken,
                        json("currentPassword", PASSWORD, "newPassword", "changed-pass")))
                .andExpect(status().isOk());

        refresh(refreshToken).andExpect(status().isUnauthorized());
        assertThat(activeRefreshTokens(email)).isZero();
        // Logging in with the new password gives a working pair again
        refresh(refreshTokenOf(loginResponse(email, "changed-pass"))).andExpect(status().isOk());
    }

    @Test
    void resettingThePasswordRevokesAllRefreshTokens() throws Exception {
        String email = uniqueEmail("reset");
        studentToken(email);
        String refreshToken = refreshTokenOf(loginResponse(email, PASSWORD));
        resetTokenRepository.save(new PasswordResetToken(ResetTokens.hash("reset-token"),
                userRepository.findByEmail(email).orElseThrow()));

        mockMvc.perform(withJson(post("/api/auth/reset-password"), null, json("token", "reset-token", "newPassword", "reset-pass")))
                .andExpect(status().isOk());

        refresh(refreshToken).andExpect(status().isUnauthorized());
        assertThat(activeRefreshTokens(email)).isZero();
    }

    @Test
    void expiredAccessTokenIs401() throws Exception {
        String email = uniqueEmail("old");
        studentToken(email);
        String expired = Jwts.builder()
                .subject(email)
                .claim("role", "STUDENT")
                .issuedAt(new Date(System.currentTimeMillis() - 16 * 60_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret)))
                .compact();

        mockMvc.perform(auth(get("/api/profile"), expired))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(withJson(post("/api/auth/refresh"), null, json("refreshToken", refreshToken)));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mockMvc.perform(withJson(post("/api/auth/logout"), null, json("refreshToken", refreshToken)));
    }

    private String registerAndReturnEmail(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        studentToken(email);
        return email;
    }

    private long activeRefreshTokens(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM refresh_tokens t JOIN users u ON u.id = t.user_id "
                + "WHERE u.email = ? AND t.revoked_at IS NULL", Long.class, email);
    }

    private static String refreshTokenOf(String responseBody) {
        return JsonPath.read(responseBody, "$.refreshToken");
    }
}
