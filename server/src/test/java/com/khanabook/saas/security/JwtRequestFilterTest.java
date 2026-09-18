package com.khanabook.saas.security;

import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.auth.repository.TokenBlocklistRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.core.utility.JwtUtility;
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

    @org.junit.jupiter.api.BeforeEach
    void clearContexts() {
        // SecurityContextHolder is thread-local static state; production no longer clears
        // it in the filter (see JwtRequestFilter finally block), so tests must isolate it.
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        com.khanabook.saas.core.security.TenantContext.clear();
    }

    @org.junit.jupiter.api.AfterEach
    void cleanupContexts() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        com.khanabook.saas.core.security.TenantContext.clear();
    }

    @Mock private JwtUtility jwtUtility;
    @Mock private UserRepository userRepository;
    @Mock private RestaurantProfileRepository restaurantProfileRepository;
    @Mock private TokenBlocklistRepository tokenBlocklistRepository;
    @Mock private TokenRevocationCache tokenRevocationCache;
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
        when(tokenRevocationCache.isRevoked("jti-1")).thenReturn(false);
        when(tokenBlocklistRepository.existsByJti("jti-1")).thenReturn(false);
        when(jwtUtility.extractRestaurantId(token)).thenReturn(42L);
        when(jwtUtility.extractUsername(token)).thenReturn("user@example.com");
        User user = new User();
        user.setLoginId("user@example.com");
        user.setRole(UserRole.OWNER);
        user.setIsActive(true);
        when(userRepository.findByAnyIdentifier("user@example.com")).thenReturn(java.util.Optional.of(user));

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
        when(tokenRevocationCache.isRevoked("jti-2")).thenReturn(false);
        when(tokenBlocklistRepository.existsByJti("jti-2")).thenReturn(false);
        when(jwtUtility.extractRestaurantId(token)).thenReturn(42L);
        User user = new User();
        user.setLoginId("user@example.com");
        user.setRole(UserRole.OWNER);
        user.setIsActive(true);
        when(userRepository.findByAnyIdentifier("user@example.com")).thenReturn(java.util.Optional.of(user));

        try {
            filter.doFilterInternal(request, response, throwingChain);
        } catch (Exception ignored) {}

        assertThat(TenantContext.getCurrentTenant()).isNull();
        assertThat(TenantContext.getCurrentRole()).isNull();
        // SecurityContext is intentionally NOT cleared by the filter anymore
        // (managed by Spring's SecurityContextHolderFilter) — only TenantContext must go.
    }

    @Test
    void suspendedBusiness_rejectsExistingTokenBeforeController() throws Exception {
        String token = "valid.jwt.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = mock(MockFilterChain.class);

        when(jwtUtility.isTokenExpired(token)).thenReturn(false);
        when(jwtUtility.extractJti(token)).thenReturn("jti-3");
        when(tokenRevocationCache.isRevoked("jti-3")).thenReturn(false);
        when(tokenBlocklistRepository.existsByJti("jti-3")).thenReturn(false);
        when(jwtUtility.extractRestaurantId(token)).thenReturn(42L);
        when(jwtUtility.extractUsername(token)).thenReturn("owner@example.com");

        User user = new User();
        user.setId(7L);
        user.setRole(UserRole.OWNER);
        user.setIsActive(true);
        when(userRepository.findByAnyIdentifier("owner@example.com")).thenReturn(java.util.Optional.of(user));

        com.khanabook.saas.entity.RestaurantProfile profile =
                new com.khanabook.saas.entity.RestaurantProfile();
        profile.setRestaurantId(42L);
        profile.setIsSuspended(true);
        when(restaurantProfileRepository.findByRestaurantId(42L)).thenReturn(java.util.Optional.of(profile));

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("BUSINESS_SUSPENDED");
        verifyNoInteractions(chain);
        assertThat(TenantContext.getCurrentTenant()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ── Device binding ────────────────────────────────────────────────────────

    @Test
    void deviceMismatch_rejectsToken() throws Exception {
        String token = "valid.jwt.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-Device-Id", "tablet-B");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = mock(MockFilterChain.class);

        stubValidToken(token, "jti-4", "staff@example.com");
        when(jwtUtility.extractDeviceId(token)).thenReturn("tablet-A");

        filter.doFilterInternal(request, response, chain);

        // A token lifted off tablet-A must not work from tablet-B.
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void matchingDevice_allowsToken() throws Exception {
        String token = "valid.jwt.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-Device-Id", "tablet-A");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = mock(MockFilterChain.class);

        stubValidToken(token, "jti-5", "staff@example.com");
        when(jwtUtility.extractDeviceId(token)).thenReturn("tablet-A");

        filter.doFilterInternal(request, response, chain);

        // Assert the chain actually ran — status 200 alone is the mock default and would
        // also hold if the filter had silently returned without authenticating.
        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void noDeviceHeader_allowsToken() throws Exception {
        // Browser clients (web admin) send no X-Device-Id, so binding must not apply
        // to them or the console would be locked out.
        String token = "valid.jwt.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = mock(MockFilterChain.class);

        stubValidToken(token, "jti-6", "owner@example.com");
        when(jwtUtility.extractDeviceId(token)).thenReturn("tablet-A");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void tokenIssuedBeforeRevocation_isRejected() throws Exception {
        String token = "valid.jwt.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = mock(MockFilterChain.class);

        long revokedAt = 2_000_000L;
        when(jwtUtility.isTokenExpired(token)).thenReturn(false);
        when(jwtUtility.extractJti(token)).thenReturn("jti-7");
        when(tokenRevocationCache.isRevoked("jti-7")).thenReturn(false);
        when(tokenBlocklistRepository.existsByJti("jti-7")).thenReturn(false);
        when(jwtUtility.extractRestaurantId(token)).thenReturn(42L);
        when(jwtUtility.extractUsername(token)).thenReturn("staff@example.com");
        when(jwtUtility.extractIssuedAt(token)).thenReturn(new java.util.Date(revokedAt - 1000L));

        User user = new User();
        user.setId(9L);
        user.setRole(UserRole.SHOP_STAFF);
        user.setIsActive(true);
        user.setTokenInvalidatedAt(revokedAt);
        when(userRepository.findByAnyIdentifier("staff@example.com")).thenReturn(java.util.Optional.of(user));

        filter.doFilterInternal(request, response, chain);

        // This is what "sign out all devices" relies on.
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    /** Stubs a structurally valid, non-revoked token for an active SHOP_STAFF user. */
    private void stubValidToken(String token, String jti, String username) {
        when(jwtUtility.isTokenExpired(token)).thenReturn(false);
        when(jwtUtility.extractJti(token)).thenReturn(jti);
        when(tokenRevocationCache.isRevoked(jti)).thenReturn(false);
        when(tokenBlocklistRepository.existsByJti(jti)).thenReturn(false);
        when(jwtUtility.extractRestaurantId(token)).thenReturn(42L);
        when(jwtUtility.extractUsername(token)).thenReturn(username);

        User user = new User();
        user.setId(11L);
        user.setLoginId(username);
        user.setRole(UserRole.SHOP_STAFF);
        user.setIsActive(true);
        when(userRepository.findByAnyIdentifier(username)).thenReturn(java.util.Optional.of(user));
    }
}
