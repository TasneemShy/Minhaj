package com.example.minhaj.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

// Lets a Prometheus server scrape /actuator/prometheus with a static token (app.metrics.token / METRICS_TOKEN)
// instead of a short-lived user JWT. A matching "Authorization: Bearer <token>" authenticates the request as the
// METRICS role, which SecurityConfig allows on that one endpoint only. Any other request is left untouched.
// Not a @Component, like RateLimitFilter: Boot would also register it as a plain servlet filter.
public class MetricsTokenFilter extends OncePerRequestFilter {

    static final String PATH = "/actuator/prometheus";
    static final String ROLE = "METRICS";
    static final int MIN_TOKEN_LENGTH = 32;

    private final byte[] token;

    // An empty token turns the filter off: then only ADMIN JWTs can read the metrics.
    public MetricsTokenFilter(String token) {
        String trimmed = token == null ? "" : token.trim();
        if (!trimmed.isEmpty() && trimmed.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalStateException("app.metrics.token (METRICS_TOKEN) must be at least " + MIN_TOKEN_LENGTH
                    + " characters, e.g. from: openssl rand -hex 32");
        }
        this.token = trimmed.isEmpty() ? null : trimmed.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return token == null || !PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                // Constant-time comparison, so response timing doesn't reveal how much of the token matched
                && MessageDigest.isEqual(token, header.substring(7).trim().getBytes(StandardCharsets.UTF_8))) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "prometheus", null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
        }
        chain.doFilter(request, response);
    }
}
