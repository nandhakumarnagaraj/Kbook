package com.khanabook.saas;

import com.khanabook.saas.entity.AuthProvider;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.utility.JwtUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired protected UserRepository userRepository;
    @Autowired protected RestaurantProfileRepository restaurantProfileRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtUtility jwtUtility;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      () -> "jdbc:h2:mem:khanabook_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");

        registry.add("JWT_SECRET",       () -> "integration-test-secret-64-chars-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        registry.add("GOOGLE_CLIENT_ID", () -> "test-google-client-id");
        registry.add("whatsapp.meta.fixed-otp", () -> "123456");

        registry.add("spring.flyway.enabled",             () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto",    () -> "create-drop");
    }

    protected String persistUserAndGetToken(String loginId, Long restaurantId, UserRole role) {
        return persistUserAndGetToken(loginId, restaurantId, restaurantId, role);
    }

    protected String persistUserAndGetToken(String loginId, Long persistedRestaurantId, Long tokenRestaurantId, UserRole role) {
        persistUser(loginId, persistedRestaurantId, role);
        return jwtUtility.generateToken(loginId, tokenRestaurantId, role.name());
    }

    protected User persistUser(String loginId, Long restaurantId, UserRole role) {
        if (restaurantId != null && restaurantProfileRepository.findByRestaurantId(restaurantId).isEmpty()) {
            RestaurantProfile profile = new RestaurantProfile();
            profile.setRestaurantId(restaurantId);
            profile.setLocalId(1L);
            profile.setDeviceId("TEST_DEVICE");
            profile.setShopName("Test Shop " + restaurantId);
            profile.setCreatedAt(System.currentTimeMillis());
            profile.setUpdatedAt(System.currentTimeMillis());
            profile.setServerUpdatedAt(System.currentTimeMillis());
            restaurantProfileRepository.save(profile);
        }

        User user = new User();
        user.setName("Test User");
        user.setLoginId(loginId);
        user.setPhoneNumber(loginId.matches("^\\d{10}$") ? loginId : null);
        user.setWhatsappNumber(loginId.matches("^\\d{10}$") ? loginId : null);
        user.setEmail(loginId.contains("@") ? loginId : null);
        user.setAuthProvider(loginId.contains("@") ? AuthProvider.GOOGLE : AuthProvider.PHONE);
        user.setPasswordHash(passwordEncoder.encode("pass123"));
        user.setRestaurantId(restaurantId);
        user.setDeviceId("TEST_DEVICE");
        user.setLocalId(1L);
        user.setRole(role);
        user.setIsActive(true);
        user.setCreatedAt(System.currentTimeMillis());
        user.setUpdatedAt(System.currentTimeMillis());
        user.setServerUpdatedAt(System.currentTimeMillis());
        return userRepository.save(user);
    }
}
