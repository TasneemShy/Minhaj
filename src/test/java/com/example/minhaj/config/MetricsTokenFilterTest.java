package com.example.minhaj.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricsTokenFilterTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static Authentication run(MetricsTokenFilter filter, String path, String authorization) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void theRightTokenOnThePrometheusEndpointAuthenticatesAsMetrics() throws Exception {
        Authentication auth = run(new MetricsTokenFilter(TOKEN), "/actuator/prometheus", "Bearer " + TOKEN);

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_METRICS");
    }

    @Test
    void wrongMissingOrMisplacedTokensAuthenticateNothing() throws Exception {
        MetricsTokenFilter filter = new MetricsTokenFilter(TOKEN);

        assertThat(run(filter, "/actuator/prometheus", "Bearer " + TOKEN + "x")).isNull();
        assertThat(run(filter, "/actuator/prometheus", "Bearer " + TOKEN.substring(1))).isNull();
        assertThat(run(filter, "/actuator/prometheus", TOKEN)).isNull();
        assertThat(run(filter, "/actuator/prometheus", null)).isNull();
        // The token is only good for the metrics endpoint
        assertThat(run(filter, "/api/admin/users", "Bearer " + TOKEN)).isNull();
        assertThat(run(filter, "/actuator/env", "Bearer " + TOKEN)).isNull();
    }

    @Test
    void withoutATokenTheFilterIsOff() throws Exception {
        assertThat(run(new MetricsTokenFilter(""), "/actuator/prometheus", "Bearer ")).isNull();
        assertThat(run(new MetricsTokenFilter(null), "/actuator/prometheus", "Bearer null")).isNull();
    }

    @Test
    void shortTokensAreRejectedAtStartup() {
        assertThatThrownBy(() -> new MetricsTokenFilter("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("METRICS_TOKEN");
    }
}
