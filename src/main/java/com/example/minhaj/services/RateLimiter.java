package com.example.minhaj.services;

import com.example.minhaj.exceptions.RateLimitExceededException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

// Token buckets for the public auth endpoints, kept in memory (one instance of the app; no Redis).
// Each key (endpoint + IP, or email + IP for login) gets its own bucket of `limit` requests per window,
// refilled gradually. The cache is bounded, and a bucket left alone for a whole window is full again anyway,
// so dropping idle ones after a window changes nothing.
@Component
public class RateLimiter {

    private final boolean enabled;
    private final int loginPerEmail;
    private final int loginPerIp;
    private final int perIp;
    private final Duration window;
    private final boolean trustForwardedFor;
    private final Cache<String, Bucket> buckets;

    public RateLimiter(@Value("${app.rate-limit.enabled:true}") boolean enabled,
                       @Value("${app.rate-limit.login-per-email:5}") int loginPerEmail,
                       @Value("${app.rate-limit.login-per-ip:20}") int loginPerIp,
                       @Value("${app.rate-limit.per-ip:10}") int perIp,
                       @Value("${app.rate-limit.window:1m}") Duration window,
                       @Value("${app.rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor,
                       @Value("${app.rate-limit.max-tracked-keys:100000}") long maxTrackedKeys) {
        this.enabled = enabled;
        this.loginPerEmail = loginPerEmail;
        this.loginPerIp = loginPerIp;
        this.perIp = perIp;
        this.window = window;
        this.trustForwardedFor = trustForwardedFor;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(maxTrackedKeys)
                .expireAfterAccess(window)
                .build();
    }

    // Per client IP and endpoint. Login has its own, higher limit because many users can share one IP (NAT).
    public void checkIp(String endpoint, String clientIp) {
        int limit = endpoint.endsWith("/login") ? loginPerIp : perIp;
        consume("ip|" + endpoint + "|" + clientIp, limit);
    }

    // Login attempts per email and IP: slows password guessing against one account
    // without letting an attacker lock that account out from everywhere else.
    public void checkLogin(String email, String clientIp) {
        consume("login|" + email.trim().toLowerCase(Locale.ROOT) + "|" + clientIp, loginPerEmail);
    }

    // X-Forwarded-For is only read when app.rate-limit.trust-forwarded-for is on. Any client can send that header,
    // so trusting it without a proxy in front lets an attacker pick a fresh "IP" for every request and skip the
    // limits. Behind exactly one trusted proxy, the right-most entry is the address that proxy saw.
    public String clientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                return hops[hops.length - 1].trim();
            }
        }
        return request.getRemoteAddr();
    }

    // Forgets every bucket; used by tests so one test's requests don't count against the next.
    public void reset() {
        buckets.invalidateAll();
    }

    private void consume(String key, int limit) {
        if (!enabled) {
            return;
        }
        Bucket bucket = buckets.get(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(limit).refillGreedy(limit, window).build())
                .build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            // Whole seconds, rounded up: Retry-After has no fractions
            long seconds = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
            throw new RateLimitExceededException(seconds);
        }
    }
}
