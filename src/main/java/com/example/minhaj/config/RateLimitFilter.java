package com.example.minhaj.config;

import com.example.minhaj.exceptions.RateLimitExceededException;
import com.example.minhaj.services.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Set;

// Per-IP limits on the public auth endpoints, applied in the security chain before the JWT filter.
// Deliberately not a @Component: Boot would also register it as a plain servlet filter (see SecurityConfig).
// The per-email login limit needs the request body, so AuthController checks it.
public class RateLimitFilter extends OncePerRequestFilter {

    static final Set<String> LIMITED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/forgot-password",
            "/api/auth/reset-password");

    private final RateLimiter rateLimiter;
    private final HandlerExceptionResolver resolver;

    public RateLimitFilter(RateLimiter rateLimiter, HandlerExceptionResolver resolver) {
        this.rateLimiter = rateLimiter;
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            rateLimiter.checkIp(request.getRequestURI(), rateLimiter.clientIp(request));
        } catch (RateLimitExceededException ex) {
            // Same 429 Problem Details body (and Retry-After header) as when a controller throws it.
            resolver.resolveException(request, response, null, ex);
            return;
        }
        chain.doFilter(request, response);
    }
}
