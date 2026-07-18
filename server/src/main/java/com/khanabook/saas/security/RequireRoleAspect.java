package com.khanabook.saas.security;

import com.khanabook.saas.entity.UserRole;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enforces role-based access control on methods annotated with {@link RequireRole}.
 * Reads the caller's role from {@link TenantContext} (set by JwtRequestFilter)
 * and throws AccessDeniedException (HTTP 403) if the role is not in the allowed set.
 */
@Aspect
@Component
public class RequireRoleAspect {

    private static final Logger log = LoggerFactory.getLogger(RequireRoleAspect.class);

    @Around("@annotation(requireRole)")
    public Object enforce(ProceedingJoinPoint joinPoint, RequireRole requireRole) throws Throwable {
        String currentRoleStr = TenantContext.getCurrentRole();
        Set<String> allowedRoles = Arrays.stream(requireRole.value())
                .map(UserRole::name)
                .collect(Collectors.toSet());

        if (currentRoleStr == null || !allowedRoles.contains(currentRoleStr)) {
            log.warn("Access denied: role={} not in allowed={} for {}",
                    currentRoleStr, allowedRoles, joinPoint.getSignature().toShortString());
            throw new AccessDeniedException(
                    "Required role: " + String.join(" or ", allowedRoles));
        }

        return joinPoint.proceed();
    }
}
