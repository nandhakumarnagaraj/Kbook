package com.khanabook.saas.security;

import com.khanabook.saas.entity.User;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.TokenBlocklistRepository;
import com.khanabook.saas.repository.UserRepository;
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
    @Mock private UserRepository userRepository;
    @Mock private TokenBlocklistRepository tokenBlocklistRepository;
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
        when(jwtUtility.extractJti(token)).thenReturn("jti-1");
        when(tokenBlocklistRepository.existsByJti("jti-1")).thenReturn(false);
        when(jwtUtility.extractRestaurantId(token)).thenReturn(42L);
        when(jwtUtility.extractUsername(token)).thenReturn("user@example.com");
        User user = new User();
        user.setLoginId("user@example.com");
        user.setRole(UserRole.OWNER);
        user.setIsActive(true);
        when(userRepository.findByPhoneNumber("user@example.com")).thenReturn(java.util.Optional.empty());
        when(userRepository.findByLoginId("user@example.com")).thenReturn(java.util.Optional.of(user));

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
        when(jwtUtility.extractUsername(token)).thenReturn("user@example.com");
        when(jwtUtility.extractJti(token)).thenReturn("jti-2");
        when(tokenBlocklistRepository.existsByJti("jti-2")).thenReturn(false);
        when(jwtUtility.extractRestaurantId(token)).thenReturn(42L);
        User user = new User();
        user.setLoginId("user@example.com");
        user.setRole(UserRole.OWNER);
        user.setIsActive(true);
        when(userRepository.findByPhoneNumber("user@example.com")).thenReturn(java.util.Optional.empty());
        when(userRepository.findByLoginId("user@example.com")).thenReturn(java.util.Optional.of(user));

        try {
            filter.doFilterInternal(request, response, throwingChain);
        } catch (Exception ignored) {}

        assertThat(TenantContext.getCurrentTenant()).isNull();
        assertThat(TenantContext.getCurrentRole()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
