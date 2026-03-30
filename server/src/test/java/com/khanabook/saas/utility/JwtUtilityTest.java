package com.khanabook.saas.utility;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class JwtUtilityTest {

    private JwtUtility jwtUtility;

    private static final String SECRET =
        "test-secret-key-that-is-at-least-32-chars-long-xxxxxxxxxxx";

    @BeforeEach
    void setUp() {
        jwtUtility = new JwtUtility();
        ReflectionTestUtils.setField(jwtUtility, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtility, "expirationMs", 3_600_000L); 
    }

    @Test
    void generateToken_extractUsername_roundtrip() {
        String token = jwtUtility.generateToken("user@test.com", 100L, "OWNER");
        assertThat(jwtUtility.extractUsername(token)).isEqualTo("user@test.com");
    }

    @Test
    void generateToken_extractRestaurantId_roundtrip() {
        String token = jwtUtility.generateToken("user@test.com", 99L, "OWNER");
        assertThat(jwtUtility.extractRestaurantId(token)).isEqualTo(99L);
    }

    @Test
    void generateToken_extractRole_roundtrip() {
        String token = jwtUtility.generateToken("user@test.com", 100L, "KBOOK_ADMIN");
        assertThat(jwtUtility.extractRole(token)).isEqualTo("KBOOK_ADMIN");
    }

    @Test
    void freshToken_isNotExpired() {
        String token = jwtUtility.generateToken("user@test.com", 1L, "OWNER");
        assertThat(jwtUtility.isTokenExpired(token)).isFalse();
    }

    @Test
    void expiredToken_detectedCorrectly() throws Exception {
        ReflectionTestUtils.setField(jwtUtility, "expirationMs", 1L);
        String token = jwtUtility.generateToken("user@test.com", 1L, "OWNER");
        Thread.sleep(10); 
        assertThat(jwtUtility.isTokenExpired(token)).isTrue();
    }

    @Test
    void differentRestaurantIds_produceDifferentTokens() {
        String t1 = jwtUtility.generateToken("user@test.com", 1L, "OWNER");
        String t2 = jwtUtility.generateToken("user@test.com", 2L, "OWNER");
        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void tampered_token_throwsException() {
        String token = jwtUtility.generateToken("user@test.com", 1L, "OWNER");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> jwtUtility.extractRestaurantId(tampered))
            .isInstanceOf(Exception.class);
    }

    @Test
    void shortSecret_stillProducesValidToken() {
        ReflectionTestUtils.setField(jwtUtility, "secret", "short");
        String token = jwtUtility.generateToken("user@test.com", 55L, "OWNER");
        assertThat(jwtUtility.extractRestaurantId(token)).isEqualTo(55L);
    }

    @Test
    void largeRestaurantId_preservedExactly() {
        Long bigId = Long.MAX_VALUE - 1;
        String token = jwtUtility.generateToken("x@x.com", bigId, "OWNER");
        assertThat(jwtUtility.extractRestaurantId(token)).isEqualTo(bigId);
    }
}
