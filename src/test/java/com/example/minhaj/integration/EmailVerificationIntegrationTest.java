package com.example.minhaj.integration;

import com.example.minhaj.services.ResetTokens;
import com.example.minhaj.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailVerificationIntegrationTest extends IntegrationTestBase {

    // app.frontend-url in application-test.properties is "https://app.minhaj.test/" (trailing slash on purpose)
    private static final Pattern VERIFY_LINK = Pattern.compile("(\\S*/verify-email\\?token=)(\\S+)");
    private static final String RESEND_ANSWER = "If the account exists and is not verified yet, a verification link has been sent.";

    @Test
    void registrationStartsUnverifiedAndEmailsAFrontendLinkStoringOnlyTheHash() throws Exception {
        String email = uniqueEmail("new");
        String accessToken = registerUnverified("New", email, PASSWORD);

        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerified()).isFalse();
        mockMvc.perform(auth(get("/api/profile"), accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(false));

        String text = emails(email, isWelcome(), 1).get(0);
        Matcher link = VERIFY_LINK.matcher(text);
        assertThat(link.find()).as(text).isTrue();
        assertThat(link.group(1)).isEqualTo("https://app.minhaj.test/verify-email?token=");
        String rawToken = URLDecoder.decode(link.group(2), StandardCharsets.UTF_8);
        assertThat(rawToken).matches("[A-Za-z0-9_-]{43}");

        List<String> stored = jdbc.queryForList("SELECT token_hash FROM email_verification_tokens", String.class);
        assertThat(stored).containsExactly(ResetTokens.hash(rawToken));
        OffsetDateTime expiresAt = jdbc.queryForObject("SELECT expires_at FROM email_verification_tokens", OffsetDateTime.class);
        assertThat(expiresAt).isCloseTo(OffsetDateTime.now().plus(Duration.ofHours(24)), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void verifyMarksTheEmailVerifiedAndTheTokenIsSingleUse() throws Exception {
        String email = uniqueEmail("verify");
        String accessToken = registerUnverified("Verify", email, PASSWORD);
        String token = tokenFrom(emails(email, isWelcome(), 1).get(0));

        verifyEmail(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully."));

        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerified()).isTrue();
        assertThat(count("email_verification_tokens")).isZero();
        mockMvc.perform(auth(get("/api/profile"), accessToken))
                .andExpect(jsonPath("$.emailVerified").value(true));

        verifyEmail(token)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid verification token."));
    }

    @Test
    void verifyRejectsUnknownExpiredBlankAndHashedTokens() throws Exception {
        verifyEmail("made-up").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid verification token."));
        mockMvc.perform(withJson(post("/api/auth/verify-email"), null, json("token", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.token").value("Token cannot be empty"));

        String email = uniqueEmail("late");
        registerUnverified("Late", email, PASSWORD);
        String token = tokenFrom(emails(email, isWelcome(), 1).get(0));

        // Someone who can read the table only gets the hash, which is not a valid token
        String storedHash = jdbc.queryForObject("SELECT token_hash FROM email_verification_tokens", String.class);
        verifyEmail(storedHash).andExpect(status().isBadRequest());

        jdbc.update("UPDATE email_verification_tokens SET expires_at = now() - interval '1 minute'");
        verifyEmail(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Verification token has expired. Request a new one."));
        assertThat(count("email_verification_tokens")).isZero();
        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    void resendReplacesTheLinkAndAnswersTheSameForEveryEmail() throws Exception {
        String email = uniqueEmail("resend");
        registerUnverified("Resend", email, PASSWORD);
        String first = tokenFrom(emails(email, isWelcome(), 1).get(0));

        resend(email).andExpect(status().isOk()).andExpect(jsonPath("$.message").value(RESEND_ANSWER));
        String second = tokenFrom(emails(email, isVerification(), 1).get(0));
        assertThat(count("email_verification_tokens")).isOne();

        // The earlier link no longer works; the new one does
        verifyEmail(first).andExpect(status().isBadRequest());
        verifyEmail(second).andExpect(status().isOk());

        // Already verified and unknown emails: same answer, no email
        resend(email).andExpect(status().isOk()).andExpect(jsonPath("$.message").value(RESEND_ANSWER));
        resend(uniqueEmail("ghost")).andExpect(status().isOk()).andExpect(jsonPath("$.message").value(RESEND_ANSWER));
        verify(mailSender, after(300).times(1)).send(argThat((SimpleMailMessage m) -> isVerification().test(m)));
        assertThat(count("email_verification_tokens")).isZero();
    }

    @Test
    void unverifiedUsersCannotEnrollOrReviewButCanUseTheRest() throws Exception {
        String admin = adminToken();
        Long courseId = createCourse(admin, "Gated", true, 0, null);
        String email = uniqueEmail("gated");
        String token = registerUnverified("Gated", email, PASSWORD);

        mockMvc.perform(auth(post("/api/courses/" + courseId + "/enroll"), token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(startsWith(
                        "Verify your email address to enroll in courses.")))
                .andExpect(jsonPath("$.instance").value("/api/courses/" + courseId + "/enroll"));
        mockMvc.perform(withJson(post("/api/courses/" + courseId + "/reviews"), token, "{\"rating\":5,\"comment\":\"Great\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(startsWith(
                        "Verify your email address to post reviews.")));
        assertThat(count("student_courses")).isZero();
        assertThat(count("reviews")).isZero();

        // Not gated: profile, catalogue, own courses, changing the name
        mockMvc.perform(auth(get("/api/profile"), token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/courses/" + courseId)).andExpect(status().isOk());
        mockMvc.perform(auth(get("/api/my-courses"), token)).andExpect(status().isOk());
        mockMvc.perform(withJson(put("/api/profile"), token, json("name", "Still Me"))).andExpect(status().isOk());

        // The same access token works once the email is verified: the flag is read from the database
        verifyEmail(tokenFrom(emails(email, isWelcome(), 1).get(0))).andExpect(status().isOk());
        enroll(token, courseId);
        review(token, courseId, 5, "Great");
    }

    @Test
    void completingAPasswordResetAlsoVerifiesTheEmail() throws Exception {
        String email = uniqueEmail("reset");
        registerUnverified("Reset", email, PASSWORD);
        mockMvc.perform(withJson(post("/api/auth/forgot-password"), null, json("email", email))).andExpect(status().isOk());
        String text = emails(email, m -> m.getSubject().startsWith("Password Reset"), 1).get(0);
        Matcher link = Pattern.compile("reset-password\\?token=(\\S+)").matcher(text);
        assertThat(link.find()).isTrue();

        mockMvc.perform(withJson(post("/api/auth/reset-password"), null,
                        json("token", URLDecoder.decode(link.group(1), StandardCharsets.UTF_8), "newPassword", "fresh-pass")))
                .andExpect(status().isOk());

        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerified()).isTrue();
        assertThat(count("email_verification_tokens")).isZero();
    }

    @Test
    void verifiedFlagIsOnTheAdminUserList() throws Exception {
        String unverified = uniqueEmail("plain");
        registerUnverified("Plain", unverified, PASSWORD);

        mockMvc.perform(auth(get("/api/admin/users"), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '" + unverified + "')].emailVerified").value(false));
    }

    private ResultActions verifyEmail(String token) throws Exception {
        return mockMvc.perform(withJson(post("/api/auth/verify-email"), null, json("token", token)));
    }

    private ResultActions resend(String email) throws Exception {
        return mockMvc.perform(withJson(post("/api/auth/resend-verification"), null, json("email", email)));
    }

    private static Predicate<SimpleMailMessage> isWelcome() {
        return m -> m.getSubject().startsWith("Welcome");
    }

    private static Predicate<SimpleMailMessage> isVerification() {
        return m -> m.getSubject().startsWith("Confirm your email");
    }

    // Waits for `expected` matching emails to `to` (they are sent @Async) and returns their texts in order.
    private List<String> emails(String to, Predicate<SimpleMailMessage> kind, int expected) {
        Predicate<SimpleMailMessage> matches = m -> m != null && m.getSubject() != null && m.getTo() != null
                && to.equals(m.getTo()[0]) && kind.test(m);
        verify(mailSender, timeout(2000).atLeast(expected)).send(argThat((SimpleMailMessage m) -> matches.test(m)));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        return captor.getAllValues().stream().filter(matches).map(SimpleMailMessage::getText).toList();
    }

    private static String tokenFrom(String emailText) {
        Matcher link = VERIFY_LINK.matcher(emailText);
        assertThat(link.find()).as(emailText).isTrue();
        return URLDecoder.decode(link.group(2), StandardCharsets.UTF_8);
    }
}
