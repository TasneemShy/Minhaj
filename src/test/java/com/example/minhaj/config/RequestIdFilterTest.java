package com.example.minhaj.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    @Test
    void acceptsShortIdsOfLettersDigitsDashesAndUnderscores() {
        assertThat(RequestIdFilter.isValid("abc-123_XYZ")).isTrue();
        assertThat(RequestIdFilter.isValid(UUID.randomUUID().toString())).isTrue();
        assertThat(RequestIdFilter.isValid("a".repeat(64))).isTrue();
    }

    @Test
    void rejectsMissingLongOrUnsafeIds() {
        assertThat(RequestIdFilter.isValid(null)).isFalse();
        assertThat(RequestIdFilter.isValid("")).isFalse();
        assertThat(RequestIdFilter.isValid("a".repeat(65))).isFalse();
        // Characters that could break log lines or JSON fields
        assertThat(RequestIdFilter.isValid("abc def")).isFalse();
        assertThat(RequestIdFilter.isValid("abc\ninjected")).isFalse();
        assertThat(RequestIdFilter.isValid("abc\"}")).isFalse();
        assertThat(RequestIdFilter.isValid("ünïcode")).isFalse();
    }

    @Test
    void echoesAValidIncomingIdAndPutsItInTheMdcOnlyDuringTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/courses");
        request.addHeader(RequestIdFilter.HEADER, "client-id-42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();

        new RequestIdFilter().doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenInChain.set(MDC.get(RequestIdFilter.MDC_KEY));
            }
        });

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("client-id-42");
        assertThat(seenInChain.get()).isEqualTo("client-id-42");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesAUuidWhenTheHeaderIsMissingOrInvalid() throws Exception {
        for (String incoming : new String[] {null, "bad id with spaces", "x".repeat(65)}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/courses");
            if (incoming != null) {
                request.addHeader(RequestIdFilter.HEADER, incoming);
            }
            MockHttpServletResponse response = new MockHttpServletResponse();

            new RequestIdFilter().doFilter(request, response, new MockFilterChain());

            String id = response.getHeader(RequestIdFilter.HEADER);
            assertThat(id).isNotEqualTo(incoming);
            assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        }
    }
}
