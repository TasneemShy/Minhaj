package com.example.minhaj.integration;

import com.example.minhaj.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Low limits for this class only (its own Spring context); every test starts with empty buckets.
@TestPropertySource(properties = {
        "app.rate-limit.login-per-email=3",
        "app.rate-limit.login-per-ip=5",
        "app.rate-limit.per-ip=4"
})
class RateLimitIntegrationTest extends IntegrationTestBase {

    private static final String DEFAULT_IP = "127.0.0.1";

    @Test
    void loginOverThePerEmailLimitIs429ProblemWithRetryAfter() throws Exception {
        String email = uniqueEmail("limited");
        studentToken(email);

        // Failed and successful attempts both count
        login(email, "wrong-password", DEFAULT_IP).andExpect(status().isUnauthorized());
        login(email, PASSWORD, DEFAULT_IP).andExpect(status().isOk());
        login(email, PASSWORD, DEFAULT_IP).andExpect(status().isOk());

        login(email, PASSWORD, DEFAULT_IP)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                // Whole seconds until a token is back: at most one 20-second refill step (window 60s / 3)
                .andExpect(header().string("Retry-After", matchesRegex("[1-9]|1[0-9]|20")))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.detail").value(startsWith("Too many requests.")))
                .andExpect(jsonPath("$.instance").value("/api/auth/login"));
    }

    @Test
    void perEmailLimitLeavesOtherEmailsAndOtherIpsAlone() throws Exception {
        String victim = uniqueEmail("victim");
        String other = uniqueEmail("other");
        studentToken(victim);
        studentToken(other);

        for (int i = 0; i < 3; i++) {
            login(victim, "guess-" + i, DEFAULT_IP).andExpect(status().isUnauthorized());
        }
        login(victim, PASSWORD, DEFAULT_IP).andExpect(status().isTooManyRequests());

        // Another account from the same IP (4th login from it, under the per-IP limit of 5)
        login(other, PASSWORD, DEFAULT_IP).andExpect(status().isOk());
        // The attacked account still works from somewhere else: no lock-out
        login(victim, PASSWORD, "10.0.0.2").andExpect(status().isOk());
    }

    @Test
    void perIpLoginLimitCoversAllEmails() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(uniqueEmail("spray"), PASSWORD, DEFAULT_IP).andExpect(status().isUnauthorized());
        }
        login(uniqueEmail("spray"), PASSWORD, DEFAULT_IP)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        login(uniqueEmail("spray"), PASSWORD, "10.0.0.3").andExpect(status().isUnauthorized());
    }

    @Test
    void otherAuthEndpointsAreLimitedPerIpAndEndpoint() throws Exception {
        for (int i = 0; i < 4; i++) {
            forgotPassword(DEFAULT_IP).andExpect(status().isOk());
        }
        forgotPassword(DEFAULT_IP)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.instance").value("/api/auth/forgot-password"));
        forgotPassword("10.0.0.4").andExpect(status().isOk());

        // Separate bucket per endpoint
        mockMvc.perform(withJson(post("/api/auth/reset-password"), null, json("token", "nope", "newPassword", "whatever1"))
                        .with(from(DEFAULT_IP)))
                .andExpect(status().isBadRequest());

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(withJson(post("/api/auth/reset-password"), null, json("token", "nope", "newPassword", "whatever1"))
                            .with(from(DEFAULT_IP)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(withJson(post("/api/auth/reset-password"), null, json("token", "nope", "newPassword", "whatever1"))
                        .with(from(DEFAULT_IP)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void registerIsLimitedPerIp() throws Exception {
        for (int i = 0; i < 4; i++) {
            register(DEFAULT_IP).andExpect(status().isOk());
        }
        register(DEFAULT_IP).andExpect(status().isTooManyRequests());
        register("10.0.0.5").andExpect(status().isOk());
    }

    @Test
    void resendVerificationIsLimitedPerIp() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(withJson(post("/api/auth/resend-verification"), null, json("email", uniqueEmail("rv"))))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(withJson(post("/api/auth/resend-verification"), null, json("email", uniqueEmail("rv"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void forwardedForHeaderIsIgnoredByDefault() throws Exception {
        // Rotating X-Forwarded-For must not give a fresh limit: the client IP is the socket address
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(withJson(post("/api/auth/forgot-password"), null, json("email", uniqueEmail("x")))
                            .header("X-Forwarded-For", "203.0.113." + i))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(withJson(post("/api/auth/forgot-password"), null, json("email", uniqueEmail("x")))
                        .header("X-Forwarded-For", "203.0.113.99"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void unlimitedEndpointsAreNotCounted() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/courses"))
                    .andExpect(status().isOk());
        }
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(withJson(post("/api/auth/login"), null, json("email", email, "password", password))
                .with(from(ip)));
    }

    private ResultActions forgotPassword(String ip) throws Exception {
        return mockMvc.perform(withJson(post("/api/auth/forgot-password"), null, json("email", uniqueEmail("fp")))
                .with(from(ip)));
    }

    private ResultActions register(String ip) throws Exception {
        String email = uniqueEmail("reg");
        return mockMvc.perform(withJson(post("/api/auth/register"), null, json("name", "Reg", "email", email, "password", PASSWORD))
                .with(from(ip)));
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
