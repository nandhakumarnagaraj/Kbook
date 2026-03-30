package com.khanabook.saas.security;

import com.khanabook.saas.utility.JwtUtility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtRequestFilterTest {

    @Mock private JwtUtility jwtUtility;
    @InjectMocks private JwtRequestFilter filter;

    @Test
    void validToken_setsSecurityContextAndTenantContext() throws Exception {
        String token = "valid.jwt.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
                assertThat(TenantContext.getCurrentTenant()).isEqualTo(42L);
                assertThat(TenantContext.getCurrentRole()).isEqualTo("OWNER");
            }
        };

        when(jwtUtility.isTokenExpired(token)).thenReturn(false);
        when(jwtUtility.extractRestaurantId(token)).thenReturn(42L);
        when(jwtUtility.extractUsername(token)).thenReturn("user@example.com");
        when(jwtUtility.extractRole(token)).thenReturn("OWNER");

        filter.doFilterInternal(request, response, chain);
    }

    @Test
    void tenantContextAlwaysClearedAfterRequest() throws Exception {
        String token = "valid.jwt.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain throwingChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                throw new RuntimeException("Downstream exploded");
            }
        };

        when(jwtUtility.isTokenExpired(token)).thenReturn(false);
        when(jwtUtility.extractRestaurantId(token)).thenReturn(42L);
        when(jwtUtility.extractUsername(token)).thenReturn("user@example.com");
        when(jwtUtility.extractRole(token)).thenReturn("OWNER");

        try {
            filter.doFilterInternal(request, response, throwingChain);
        } catch (Exception ignored) {}

        assertThat(TenantContext.getCurrentTenant()).isNull();
        assertThat(TenantContext.getCurrentRole()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
