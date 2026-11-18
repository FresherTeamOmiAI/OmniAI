package com.omniai.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/** Token service-to-service của /api/internal/** (decisions #48). */
class InternalTokenFilterTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blankServerTokenRejectsEverything() {
        for (String configured : new String[] {null, "", "   "}) {
            InternalTokenFilter filter = new InternalTokenFilter(configured);
            assertThat(filter.configured()).isFalse();
            assertThat(filter.matches("")).isFalse();
            assertThat(filter.matches("   ")).isFalse();
            assertThat(filter.matches(null)).isFalse();
        }
    }

    @Test
    void onlyExactTokenMatches() {
        InternalTokenFilter filter = new InternalTokenFilter("bi-mat-123");
        assertThat(filter.matches("bi-mat-123")).isTrue();
        assertThat(filter.matches("bi-mat-12")).isFalse();
        assertThat(filter.matches("bi-mat-1234")).isFalse();
        assertThat(filter.matches("BI-MAT-123")).isFalse();
    }

    @Test
    void matchingHeaderAuthenticatesAsInternal() throws Exception {
        InternalTokenFilter filter = new InternalTokenFilter("bi-mat-123");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/internal/customers/1/profile");
        request.addHeader(InternalTokenFilter.HEADER, "bi-mat-123");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_INTERNAL");

        SecurityContextHolder.clearContext();
        MockHttpServletRequest wrong = new MockHttpServletRequest("GET", "/api/internal/customers/1/profile");
        wrong.addHeader(InternalTokenFilter.HEADER, "sai");
        filter.doFilter(wrong, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
