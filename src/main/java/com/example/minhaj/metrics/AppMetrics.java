package com.example.minhaj.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

// The app's own Micrometer meters, next to the built-in HTTP, JVM and datasource ones. In /actuator/prometheus
// dots become underscores and counters get a _total suffix, e.g. minhaj_auth_logins_total{result="success"}.
// Tag values come from small fixed sets (never user input), so the number of series stays bounded.
@Component
public class AppMetrics {

    private final MeterRegistry registry;
    private final Counter loginSuccess;
    private final Counter loginFailure;
    private final Counter registrations;
    private final Counter enrollments;
    private final Timer courseSearch;

    public AppMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.loginSuccess = loginCounter("success");
        this.loginFailure = loginCounter("failure");
        this.registrations = Counter.builder("minhaj.auth.registrations")
                .description("Accounts created through POST /api/auth/register")
                .register(registry);
        this.enrollments = Counter.builder("minhaj.enrollments")
                .description("New course enrollments (re-enrolling in the same course is not counted)")
                .register(registry);
        this.courseSearch = Timer.builder("minhaj.course.search")
                .description("Time spent running the course catalog query (GET /api/courses)")
                .register(registry);
    }

    private Counter loginCounter(String result) {
        return Counter.builder("minhaj.auth.logins")
                .description("Login attempts by result; failure = wrong email or password")
                .tag("result", result)
                .register(registry);
    }

    public void loginSucceeded() {
        loginSuccess.increment();
    }

    public void loginFailed() {
        loginFailure.increment();
    }

    public void registered() {
        registrations.increment();
    }

    public void enrolled() {
        enrollments.increment();
    }

    // endpoint is one of RateLimitFilter's fixed paths; limit is "ip" or "email" (the per-email login limit).
    public void rateLimitRejected(String endpoint, String limit) {
        Counter.builder("minhaj.ratelimit.rejections")
                .description("Requests rejected with 429 by the auth rate limits")
                .tag("endpoint", endpoint)
                .tag("limit", limit)
                .register(registry)
                .increment();
    }

    public <T> T timeCourseSearch(Supplier<T> query) {
        return courseSearch.record(query);
    }
}
