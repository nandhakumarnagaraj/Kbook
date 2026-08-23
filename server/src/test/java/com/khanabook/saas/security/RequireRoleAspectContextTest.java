package com.khanabook.saas.security;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.webadmin.controller.AdminDashboardController;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequireRoleAspectContextTest extends BaseIntegrationTest {

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;
    @Autowired
    private AdminDashboardController adminDashboardController;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void contextContainsActiveRequireRoleAspect() {
        Object bean = applicationContext.getBean("requireRoleAspect");
        assertNotNull(bean, "RequireRoleAspect bean must be present in the Spring context");
        assertTrue(
                bean.getClass().isAnnotationPresent(Aspect.class) || hasAspectMetaAnnotation(bean.getClass()),
                "RequireRoleAspect must be annotated @Aspect"
        );
    }

    @Test
    void suspendBusinessWithoutRole_isDenied() {
        Long restaurantId = persistRestaurant();
        TenantContext.clear();
        assertThrows(AccessDeniedException.class,
                () -> adminDashboardController.suspendBusiness(restaurantId));
    }

    @Test
    void suspendBusinessWithWrongRole_isDenied() {
        Long restaurantId = persistRestaurant();
        TenantContext.setCurrentRole(UserRole.OWNER.name());
        assertThrows(AccessDeniedException.class,
                () -> adminDashboardController.suspendBusiness(restaurantId));
    }

    @Test
    void suspendBusinessWithAdminRole_succeeds() {
        Long restaurantId = persistRestaurant();
        TenantContext.setCurrentRole(UserRole.KBOOK_ADMIN.name());
        adminDashboardController.suspendBusiness(restaurantId);
        RestaurantProfile profile = restaurantProfileRepository
                .findByRestaurantId(restaurantId)
                .orElseThrow();
        assertTrue(profile.getIsSuspended(), "profile must be suspended when aspect permits the call");
    }

    private static final java.util.concurrent.atomic.AtomicLong RESTAURANT_SEQ =
            new java.util.concurrent.atomic.AtomicLong(999_000L);

    private Long persistRestaurant() {
        RestaurantProfile profile = new RestaurantProfile();
        // Monotonic counter: nanoTime()%1000 collided on coarse Windows timers,
        // producing duplicate restaurant_ids and NonUniqueResult failures.
        profile.setRestaurantId(RESTAURANT_SEQ.incrementAndGet());
        profile.setLocalId(System.nanoTime());
        profile.setDeviceId("REQ-ROLE-ASPECT");
        profile.setCreatedAt(System.currentTimeMillis());
        profile.setUpdatedAt(System.currentTimeMillis());
        return restaurantProfileRepository.save(profile).getRestaurantId();
    }

    private static boolean hasAspectMetaAnnotation(Class<?> type) {
        return type.isAnnotationPresent(Aspect.class)
                || (type.getSuperclass() != null && type.getSuperclass().isAnnotationPresent(Aspect.class));
    }
}
