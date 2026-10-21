package com.example.minhaj.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

// Gives every request an ID: the caller's X-Request-Id when it is safe to reuse, otherwise a new UUID.
// The ID is echoed in the X-Request-Id response header, put in the MDC (so it is on every log line, also in JSON
// logs) and added to Problem Details error bodies as "requestId", so a user can quote it when reporting a problem.
// Registered first in the servlet filter chain by MonitoringConfig, ahead of Spring Security.
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    // Short and free of characters that could forge log lines or JSON fields
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".id";

    static boolean isValid(String requestId) {
        return requestId != null && VALID.matcher(requestId).matches();
    }

    // The current request's ID, or null outside a request.
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        // The container's /error dispatch runs after this filter has cleared the MDC; set it again for that too.
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = (String) request.getAttribute(ATTRIBUTE);
        if (requestId == null) {
            String incoming = request.getHeader(HEADER);
            requestId = isValid(incoming) ? incoming : UUID.randomUUID().toString();
            request.setAttribute(ATTRIBUTE, requestId);
        }
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
