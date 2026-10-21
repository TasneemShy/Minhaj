package com.example.minhaj.service;

import com.example.minhaj.exceptions.RateLimitExceededException;
import com.example.minhaj.metrics.AppMetrics;
import com.example.minhaj.services.RateLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AppMetrics metrics = new AppMetrics(registry);

    private RateLimiter limiter(boolean trustForwardedFor) {
        return new RateLimiter(true, 2, 5, 3, Duration.ofMinutes(1), trustForwardedFor, 1000, metrics);
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
        RateLimiter limiter = new RateLimiter(false, 1, 1, 1, Duration.ofMinutes(1), false, 1000, metrics);
        for (int i = 0; i < 5; i++) {
            limiter.checkLogin("a@b.c", "10.0.0.1");
            limiter.checkIp("/api/auth/register", "10.0.0.1");
        }
    }

    @Test
    void rejectionsAreCountedPerEndpointAndLimit() {
        RateLimiter limiter = limiter(false);
        for (int i = 0; i < 3; i++) {
            limiter.checkIp("/api/auth/register", "10.0.0.1");
        }
        assertThatThrownBy(() -> limiter.checkIp("/api/auth/register", "10.0.0.1"))
                .isInstanceOf(RateLimitExceededException.class);
        limiter.checkLogin("a@b.c", "10.0.0.1");
        limiter.checkLogin("a@b.c", "10.0.0.1");
        assertThatThrownBy(() -> limiter.checkLogin("a@b.c", "10.0.0.1"))
                .isInstanceOf(RateLimitExceededException.class);

        assertThat(rejections("/api/auth/register", "ip")).isEqualTo(1.0);
        assertThat(rejections("/api/auth/login", "email")).isEqualTo(1.0);
        assertThat(registry.find("minhaj.ratelimit.rejections").tag("endpoint", "/api/auth/login").tag("limit", "ip")
                .counter()).isNull();
    }

    private double rejections(String endpoint, String limit) {
        return registry.get("minhaj.ratelimit.rejections").tag("endpoint", endpoint).tag("limit", limit)
                .counter().count();
    }
}
