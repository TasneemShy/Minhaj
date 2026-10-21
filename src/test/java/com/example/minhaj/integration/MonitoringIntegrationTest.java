package com.example.minhaj.integration;

import com.example.minhaj.config.RequestIdFilter;
import com.example.minhaj.support.IntegrationTestBase;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// /actuator/prometheus access, the app's own metrics and request IDs, end to end.
// The metrics token is the test one from application-test.properties.
class MonitoringIntegrationTest extends IntegrationTestBase {

    private static final String METRICS_TOKEN = "test-only-metrics-token-0123456789abcdef";

    @Autowired
    private MeterRegistry meterRegistry;

    private String scrape() throws Exception {
        return mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer " + METRICS_TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private double metric(String name, String... tags) {
        var counter = meterRegistry.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    // ---- access ----

    @Test
    void prometheusIsNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer wrong-" + METRICS_TOKEN))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(auth(get("/actuator/prometheus"), studentToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void prometheusIsReadableWithTheMetricsTokenOrAsAdmin() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer " + METRICS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
        mockMvc.perform(auth(get("/actuator/prometheus"), adminToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("application=\"minhaj\"")));
    }

    @Test
    void theMetricsTokenOpensNothingElse() throws Exception {
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + METRICS_TOKEN))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env").header("Authorization", "Bearer " + METRICS_TOKEN))
                .andExpect(status().isUnauthorized());
    }

    // ---- custom metrics ----

    @Test
    void authMetricsCountRegistrationsAndLogins() throws Exception {
        double registrations = metric("minhaj.auth.registrations");
        double successes = metric("minhaj.auth.logins", "result", "success");
        double failures = metric("minhaj.auth.logins", "result", "failure");

        String email = uniqueEmail("metrics");
        register("Metrics", email, PASSWORD);
        login(email, PASSWORD);
        mockMvc.perform(withJson(post("/api/auth/login"), null, json("email", email, "password", "wrong-password")))
                .andExpect(status().isUnauthorized());

        assertThat(metric("minhaj.auth.registrations")).isEqualTo(registrations + 1);
        assertThat(metric("minhaj.auth.logins", "result", "success")).isEqualTo(successes + 1);
        assertThat(metric("minhaj.auth.logins", "result", "failure")).isEqualTo(failures + 1);

        String metrics = scrape();
        assertThat(metrics).contains(
                "minhaj_auth_registrations_total{application=\"minhaj\"}",
                "minhaj_auth_logins_total{application=\"minhaj\",result=\"success\"}",
                "minhaj_auth_logins_total{application=\"minhaj\",result=\"failure\"}");
    }

    @Test
    void enrollmentsAndCourseSearchesAreMeasured() throws Exception {
        String admin = adminToken();
        Long courseId = createPublishedCourse(admin, "Metrics 101", true, 0, createCategory(admin, "Ops"));
        String student = studentToken();
        double enrollments = metric("minhaj.enrollments");

        enroll(student, courseId);
        enroll(student, courseId); // enrolling again is not a new enrollment
        mockMvc.perform(get("/api/courses").param("search", "metrics")).andExpect(status().isOk());

        assertThat(metric("minhaj.enrollments")).isEqualTo(enrollments + 1);
        assertThat(meterRegistry.get("minhaj.course.search").timer().count()).isPositive();
        assertThat(scrape()).contains("minhaj_enrollments_total{application=\"minhaj\"}",
                "minhaj_course_search_seconds_count{application=\"minhaj\"}");
    }

    @Test
    void httpMetricsUseUriTemplatesNotRawIds() throws Exception {
        String admin = adminToken();
        Long courseId = createPublishedCourse(admin, "Templated", true, 0, createCategory(admin, "Cardinality"));
        mockMvc.perform(get("/api/courses/" + courseId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/courses/987654")).andExpect(status().isNotFound());

        String metrics = scrape();
        assertThat(metrics).contains("uri=\"/api/courses/{id}\"");
        assertThat(metrics).doesNotContain("uri=\"/api/courses/" + courseId + "\"", "uri=\"/api/courses/987654\"");
    }

    // ---- request IDs ----

    @Test
    void aRequestIdIsGeneratedWhenMissing() throws Exception {
        String id = mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);

        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
    }

    @Test
    void aValidRequestIdIsEchoedAndAnInvalidOneReplaced() throws Exception {
        mockMvc.perform(get("/api/courses").header(RequestIdFilter.HEADER, "trace-From_Gateway-1"))
                .andExpect(header().string(RequestIdFilter.HEADER, "trace-From_Gateway-1"));

        String invalid = "has spaces & symbols";
        mockMvc.perform(get("/api/courses").header(RequestIdFilter.HEADER, invalid))
                .andExpect(header().string(RequestIdFilter.HEADER, not(invalid)))
                .andExpect(header().string(RequestIdFilter.HEADER, not(containsString(" "))));
        mockMvc.perform(get("/api/courses").header(RequestIdFilter.HEADER, "a".repeat(65)))
                .andExpect(header().string(RequestIdFilter.HEADER, not("a".repeat(65))));
    }

    @Test
    void errorResponsesIncludeTheRequestId() throws Exception {
        mockMvc.perform(get("/api/courses/987654").header(RequestIdFilter.HEADER, "quote-me-123"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.requestId").value("quote-me-123"));

        // Validation errors (built by the base handler) and Spring Security's 401 too
        mockMvc.perform(withJson(post("/api/auth/register"), null, "{}").header(RequestIdFilter.HEADER, "bad-body-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId").value("bad-body-1"));
        mockMvc.perform(get("/api/my-courses").header(RequestIdFilter.HEADER, "anon-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RequestIdFilter.HEADER, "anon-1"))
                .andExpect(jsonPath("$.requestId").value("anon-1"));
    }
}
