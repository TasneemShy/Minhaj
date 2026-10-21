package com.example.minhaj.integration;

import com.example.minhaj.models.PasswordResetToken;
import com.example.minhaj.repositories.PasswordResetTokenRepository;
import com.example.minhaj.services.ResetTokens;
import com.example.minhaj.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

    // app.frontend-url in application-test.properties is "https://app.minhaj.test/" (trailing slash on purpose)
    private static final Pattern RESET_LINK = Pattern.compile("(\\S*/reset-password\\?token=)(\\S+)");

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Test
    void registerIgnoresRoleInBodyAndCreatesStudent() throws Exception {
        String email = uniqueEmail("sneaky");
        mockMvc.perform(withJson(post("/api/auth/register"), null,
                        "{\"name\":\"Sneaky\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.token").isNotEmpty());

        assertThat(userRepository.findByEmail(email).orElseThrow().getRole()).isEqualTo("STUDENT");
        verify(mailSender, timeout(2000)).send(any(SimpleMailMessage.class));
    }

    @Test
    void registeredTokenWorksOnProtectedEndpoint() throws Exception {
        String email = uniqueEmail("me");
        String token = studentToken(email);

        mockMvc.perform(auth(get("/api/profile"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void protectedEndpointWithoutOrWithBadTokenIs401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Authentication is required to access this resource."))
                .andExpect(jsonPath("$.instance").value("/api/profile"));
        mockMvc.perform(auth(get("/api/profile"), "not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void registerRejectsDuplicateEmailWith409() throws Exception {
        String email = uniqueEmail("dup");
        studentToken(email);

        mockMvc.perform(withJson(post("/api/auth/register"), null, json("name", "Again", "email", email, "password", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Email is already registered!"))
                .andExpect(jsonPath("$.instance").value("/api/auth/register"));
    }

    @Test
    void registerValidatesBody() throws Exception {
        mockMvc.perform(withJson(post("/api/auth/register"), null, json("name", "", "email", "nope", "password", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Request validation failed."))
                .andExpect(jsonPath("$.instance").value("/api/auth/register"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").value("Invalid email format"))
                .andExpect(jsonPath("$.errors.password").value("Password must be at least 6 characters long"));
    }

    @Test
    void loginWrongPasswordAndUnknownEmailGiveSame401() throws Exception {
        String email = uniqueEmail("login");
        studentToken(email);

        String wrongPassword = mockMvc.perform(withJson(post("/api/auth/login"), null, json("email", email, "password", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = mockMvc.perform(withJson(post("/api/auth/login"), null, json("email", uniqueEmail("ghost"), "password", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownEmail).contains("Invalid email or password");
    }

    @Test
    void loginReturnsTokenWithRole() throws Exception {
        String email = uniqueEmail("ok");
        studentToken(email);

        mockMvc.perform(withJson(post("/api/auth/login"), null, json("email", email, "password", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void forgotPasswordForUnknownEmailGivesSameAnswerAndNoToken() throws Exception {
        mockMvc.perform(withJson(post("/api/auth/forgot-password"), null, json("email", uniqueEmail("nobody"))))
                .andExpect(status().isOk())
                .andExpect(content().string("If the email exists, a reset link has been sent."));
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void forgotPasswordEmailsFrontendLinkAndStoresOnlyTheTokenHash() throws Exception {
        String email = uniqueEmail("link");
        studentToken(email);

        String text = forgotPasswordEmails(email, 1).get(0);
        Matcher link = RESET_LINK.matcher(text);
        assertThat(link.find()).as(text).isTrue();
        assertThat(link.group(1)).isEqualTo("https://app.minhaj.test/reset-password?token=");
        String rawToken = URLDecoder.decode(link.group(2), StandardCharsets.UTF_8);
        // 32 random bytes, URL-safe base64 without padding
        assertThat(rawToken).matches("[A-Za-z0-9_-]{43}");

        List<String> stored = jdbc.queryForList("SELECT token FROM password_reset_token", String.class);
        assertThat(stored).containsExactly(ResetTokens.hash(rawToken));
        assertThat(stored.get(0)).matches("[0-9a-f]{64}").isNotEqualTo(rawToken);
    }

    @Test
    void forgotThenResetPasswordChangesPasswordAndConsumesToken() throws Exception {
        String email = uniqueEmail("reset");
        studentToken(email);

        // Asking twice replaces the first token instead of failing on the unique user_id
        List<String> emails = forgotPasswordEmails(email, 2);
        String firstToken = tokenFrom(emails.get(0));
        String token = tokenFrom(emails.get(1));
        assertThat(tokenRepository.findAll()).hasSize(1);

        // The replaced token no longer works
        mockMvc.perform(withJson(post("/api/auth/reset-password"), null, json("token", firstToken, "newPassword", "brand-new-pass")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid Token."));

        mockMvc.perform(withJson(post("/api/auth/reset-password"), null, json("token", token, "newPassword", "brand-new-pass")))
                .andExpect(status().isOk());

        login(email, "brand-new-pass");
        mockMvc.perform(withJson(post("/api/auth/login"), null, json("email", email, "password", PASSWORD)))
                .andExpect(status().isUnauthorized());

        // The token is single-use
        mockMvc.perform(withJson(post("/api/auth/reset-password"), null, json("token", token, "newPassword", "another-pass")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid Token."));
    }

    @Test
    void resetPasswordRejectsTheStoredHash() throws Exception {
        String email = uniqueEmail("leak");
        studentToken(email);
        forgotPasswordEmails(email, 1);
        // Someone who can read the table only gets the hash, which is not a valid token
        String storedHash = tokenRepository.findAll().get(0).getToken();

        mockMvc.perform(withJson(post("/api/auth/reset-password"), null, json("token", storedHash, "newPassword", "hijacked1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid Token."));
        login(email, PASSWORD);
    }

    @Test
    void resetPasswordAppliesTheSamePasswordRuleAsRegister() throws Exception {
        String email = uniqueEmail("weak");
        studentToken(email);
        String token = tokenFrom(forgotPasswordEmails(email, 1).get(0));

        mockMvc.perform(withJson(post("/api/auth/reset-password"), null, json("token", token, "newPassword", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").value("Password must be at least 6 characters long"));
        mockMvc.perform(withJson(post("/api/auth/reset-password"), null, json("token", token)))
                .andExpect(status().isBadRequest());

        // The token was not used up by the rejected attempts
        mockMvc.perform(withJson(post("/api/auth/reset-password"), null, json("token", token, "newPassword", "long-enough")))
                .andExpect(status().isOk());
    }

    @Test
    void resetPasswordRejectsUnknownAndExpiredTokens() throws Exception {
        mockMvc.perform(withJson(post("/api/auth/reset-password"), null, json("token", "made-up", "newPassword", "whatever1")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid Token."));

        String email = uniqueEmail("expired");
        studentToken(email);
        PasswordResetToken expired = new PasswordResetToken(ResetTokens.hash("expired-token"), userRepository.findByEmail(email).orElseThrow());
        expired.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        tokenRepository.save(expired);

        mockMvc.perform(withJson(post("/api/auth/reset-password"), null, json("token", "expired-token", "newPassword", "whatever1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Token has expired."));
        assertThat(tokenRepository.count()).isZero();
        login(email, PASSWORD);
    }

    // Calls forgot-password `times` times and returns the texts of the reset emails, in order.
    // Emails are sent @Async; the welcome email from register is skipped.
    private List<String> forgotPasswordEmails(String email, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            mockMvc.perform(withJson(post("/api/auth/forgot-password"), null, json("email", email)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("If the email exists, a reset link has been sent."));
            // Wait for each email before the next request so the list keeps request order
            verify(mailSender, timeout(2000).times(i + 1)).send(argThat((SimpleMailMessage m) -> isResetEmail(m)));
        }

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        return captor.getAllValues().stream()
                .filter(AuthIntegrationTest::isResetEmail)
                .map(SimpleMailMessage::getText)
                .toList();
    }

    private static boolean isResetEmail(SimpleMailMessage message) {
        return message != null && message.getSubject() != null && message.getSubject().startsWith("Password Reset");
    }

    private static String tokenFrom(String emailText) {
        Matcher link = RESET_LINK.matcher(emailText);
        assertThat(link.find()).as(emailText).isTrue();
        return URLDecoder.decode(link.group(2), StandardCharsets.UTF_8);
    }
}
