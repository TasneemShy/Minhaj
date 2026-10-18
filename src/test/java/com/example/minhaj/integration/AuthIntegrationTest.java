package com.example.minhaj.integration;

import com.example.minhaj.models.PasswordResetToken;
import com.example.minhaj.repositories.PasswordResetTokenRepository;
import com.example.minhaj.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

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
    void protectedEndpointWithoutOrWithBadTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/profile")).andExpect(status().isForbidden());
        mockMvc.perform(auth(get("/api/profile"), "not-a-jwt")).andExpect(status().isForbidden());
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        String email = uniqueEmail("dup");
        studentToken(email);

        mockMvc.perform(withJson(post("/api/auth/register"), null, json("name", "Again", "email", email, "password", PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerValidatesBody() throws Exception {
        mockMvc.perform(withJson(post("/api/auth/register"), null, json("name", "", "email", "nope", "password", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.email").value("Invalid email format"))
                .andExpect(jsonPath("$.password").value("Password must be at least 6 characters long"));
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
    void forgotThenResetPasswordChangesPasswordAndConsumesToken() throws Exception {
        String email = uniqueEmail("reset");
        studentToken(email);

        // Asking twice replaces the first token instead of failing on the unique user_id
        mockMvc.perform(withJson(post("/api/auth/forgot-password"), null, json("email", email))).andExpect(status().isOk());
        mockMvc.perform(withJson(post("/api/auth/forgot-password"), null, json("email", email))).andExpect(status().isOk());

        List<PasswordResetToken> tokens = tokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        String token = tokens.get(0).getToken();

        mockMvc.perform(withJson(post("/api/auth/reset-password"), null, json("token", token, "newPassword", "brand-new-pass")))
                .andExpect(status().isOk());

        login(email, "brand-new-pass");
        mockMvc.perform(withJson(post("/api/auth/login"), null, json("email", email, "password", PASSWORD)))
                .andExpect(status().isUnauthorized());

        // The token is single-use
        mockMvc.perform(withJson(post("/api/auth/reset-password"), null, json("token", token, "newPassword", "another-pass")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPasswordAppliesTheSamePasswordRuleAsRegister() throws Exception {
        String email = uniqueEmail("weak");
        studentToken(email);
        mockMvc.perform(withJson(post("/api/auth/forgot-password"), null, json("email", email))).andExpect(status().isOk());
        String token = tokenRepository.findAll().get(0).getToken();

        mockMvc.perform(withJson(post("/api/auth/reset-password"), null, json("token", token, "newPassword", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Error: Password must be at least 6 characters long"));
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
                .andExpect(content().string("Error: Invalid Token."));

        String email = uniqueEmail("expired");
        studentToken(email);
        PasswordResetToken expired = new PasswordResetToken("expired-token", userRepository.findByEmail(email).orElseThrow());
        expired.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        tokenRepository.save(expired);

        mockMvc.perform(withJson(post("/api/auth/reset-password"), null, json("token", "expired-token", "newPassword", "whatever1")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Error: Token has expired."));
        assertThat(tokenRepository.count()).isZero();
        login(email, PASSWORD);
    }
}
