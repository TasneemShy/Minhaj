package com.example.minhaj.service;

import com.example.minhaj.exceptions.RateLimitExceededException;
import com.example.minhaj.services.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    private static RateLimiter limiter(boolean trustForwardedFor) {
        return new RateLimiter(true, 2, 5, 3, Duration.ofMinutes(1), trustForwardedFor, 1000);
    }

    @Test
    void clientIpIsTheSocketAddressUnlessForwardedForIsTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "6.6.6.6, 198.51.100.7");

        assertThat(limiter(false).clientIp(request)).isEqualTo("10.0.0.1");
        // Behind one trusted proxy: the right-most hop, not the client-controlled left-most one
        assertThat(limiter(true).clientIp(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void loginEmailsAreComparedCaseInsensitively() {
        RateLimiter limiter = limiter(false);
        limiter.checkLogin("Sara@Example.com", "10.0.0.1");
        limiter.checkLogin(" sara@example.com", "10.0.0.1");

        assertThatThrownBy(() -> limiter.checkLogin("SARA@EXAMPLE.COM", "10.0.0.1"))
                .isInstanceOf(RateLimitExceededException.class)
                .extracting(ex -> ((RateLimitExceededException) ex).getRetryAfterSeconds())
                .satisfies(seconds -> assertThat((Long) seconds).isBetween(1L, 60L));
    }

    @Test
    void disabledLimiterNeverThrows() {
        RateLimiter limiter = new RateLimiter(false, 1, 1, 1, Duration.ofMinutes(1), false, 1000);
        for (int i = 0; i < 5; i++) {
            limiter.checkLogin("a@b.c", "10.0.0.1");
            limiter.checkIp("/api/auth/register", "10.0.0.1");
        }
    }
}
