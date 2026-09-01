package com.khanabook.saas.sync.service;

import com.khanabook.saas.entity.AuthProvider;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.exception.BusinessRuleException;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.sync.entity.BaseSyncEntity;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * User-profile sync logic extracted from GenericSyncService.
 * Handles identity resolution, LWW merge protection, conflict
 * detection, and role enforcement for User records.
 */
@Service
@RequiredArgsConstructor
public class UserProfileSyncService {
    private static final Logger log = LoggerFactory.getLogger(UserProfileSyncService.class);

    /**
     * Finds an existing user in the database by matching identity fields
     * (server ID, loginId, email, googleEmail, whatsappNumber) scoped to the tenant.
     *
     * @return the matched user, or null if no existing user is found
     */
    public User findExistingUserByIdentity(Long tenantId, User incomingUser, UserRepository userRepository) {
        if (incomingUser.getId() != null) {
            Optional<User> byServerId = userRepository.findById(incomingUser.getId());
            if (byServerId.isPresent() && byServerId.get().getRestaurantId().equals(tenantId)) {
                return byServerId.get();
            }
        }

        if (incomingUser.getLoginId() != null && !incomingUser.getLoginId().isBlank()) {
            Optional<User> byLoginId = userRepository.findByLoginIdIgnoreCase(incomingUser.getLoginId());
            if (byLoginId.isPresent() && byLoginId.get().getRestaurantId().equals(tenantId)) {
                return byLoginId.get();
            }
        }

        if (incomingUser.getEmail() != null && !incomingUser.getEmail().isBlank()) {
            Optional<User> byEmail = userRepository.findByEmailIgnoreCase(incomingUser.getEmail());
            if (byEmail.isPresent() && byEmail.get().getRestaurantId().equals(tenantId)) {
                return byEmail.get();
            }
        }

        if (incomingUser.getGoogleEmail() != null && !incomingUser.getGoogleEmail().isBlank()) {
            Optional<User> byGoogleEmail = userRepository.findByGoogleEmailIgnoreCase(incomingUser.getGoogleEmail());
            if (byGoogleEmail.isPresent() && byGoogleEmail.get().getRestaurantId().equals(tenantId)) {
                return byGoogleEmail.get();
            }
        }

        if (incomingUser.getWhatsappNumber() != null && !incomingUser.getWhatsappNumber().isBlank()) {
            Optional<User> byWhatsapp = userRepository.findByWhatsappNumber(incomingUser.getWhatsappNumber());
            if (byWhatsapp.isPresent() && byWhatsapp.get().getRestaurantId().equals(tenantId)) {
                return byWhatsapp.get();
            }
        }

        return null;
    }

    /**
     * Preserves server-owned fields during LWW merge for User records.
     * Prevents a stale device push from overwriting password, login ID,
     * auth provider, email, or Google email with null/empty values.
     *
     * Also enforces Google-linked identity preservation: when a synced
     * mobile/profile payload carries a phone number, the Google-linked
     * email is preserved.
     */
    public void mergeUserFields(User incoming, User existing) {
        if (incoming.getPasswordHash() == null || incoming.getPasswordHash().isEmpty()) {
            incoming.setPasswordHash(existing.getPasswordHash());
        }
        if (incoming.getLoginId() == null || incoming.getLoginId().trim().isEmpty()) {
            incoming.setLoginId(existing.getLoginId());
        }
        if (incoming.getAuthProvider() == null) {
            incoming.setAuthProvider(existing.getAuthProvider() != null ? existing.getAuthProvider()
                    : AuthProvider.PHONE);
        }
        // Data Overwrite Protection: Don't overwrite email with null/empty from app
        if (incoming.getEmail() == null || incoming.getEmail().trim().isEmpty()) {
            incoming.setEmail(existing.getEmail());
        } else if ((existing.getAuthProvider() == AuthProvider.GOOGLE)
                && existing.getEmail() != null
                && !existing.getEmail().equalsIgnoreCase(incoming.getEmail())) {
            // Preserve Google-linked identity when a synced mobile/profile payload carries a phone number.
            incoming.setEmail(existing.getEmail());
        }

        if (incoming.getGoogleEmail() == null || incoming.getGoogleEmail().trim().isEmpty()) {
            incoming.setGoogleEmail(existing.getGoogleEmail());
        }
    }

    /**
     * Validates that identity fields (loginId, email, whatsappNumber) are unique
     * across the restaurant. Throws BusinessRuleException if a conflict is detected.
     */
    public void validateIdentityUniqueness(User incoming, User existing, UserRepository userRepo) {
        if (existing.getLoginId() != null && incoming.getLoginId() != null
                && !existing.getLoginId().equalsIgnoreCase(incoming.getLoginId())) {
            if (userRepo.existsByLoginId(incoming.getLoginId())) {
                throw new BusinessRuleException("Sync rejected: Login identity already exists for another user",
                        "SYNC_LOGIN_CONFLICT");
            }
        }

        if (existing.getEmail() != null && incoming.getEmail() != null
                && !existing.getEmail().equalsIgnoreCase(incoming.getEmail())) {
            if (userRepo.existsByEmail(incoming.getEmail())) {
                throw new BusinessRuleException("Sync rejected: Email/Phone already exists for another user",
                        "SYNC_EMAIL_CONFLICT");
            }
        }

        if (existing.getWhatsappNumber() != null && incoming.getWhatsappNumber() != null
                && !existing.getWhatsappNumber().equalsIgnoreCase(incoming.getWhatsappNumber())) {
            if (userRepo.existsByWhatsappNumber(incoming.getWhatsappNumber())) {
                throw new BusinessRuleException("Sync rejected: Whatsapp number already exists for another user",
                        "SYNC_WHATSAPP_CONFLICT");
            }
        }
    }

    /**
     * Enforces role and state for newly created users via sync.
     * Non-admin callers always create OWNER users with default state.
     */
    public void enforceNewUserRole(User newUser, boolean isKbookAdmin) {
        if (!isKbookAdmin) {
            newUser.setRole(UserRole.OWNER);
            newUser.setIsActive(true);
            newUser.setTokenInvalidatedAt(null);
        }
    }
}
