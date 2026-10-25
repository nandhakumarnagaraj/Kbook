package com.khanabook.saas.feature.auth.service;

import com.khanabook.saas.feature.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Web-session presence: records that a signed-in user's browser reached the API.
 *
 * <p>What this is: a timestamp, {@code users.last_seen_at} (V118), written only
 * from an authenticated request. It is the server's own observation, which is why
 * the console's presence dot can mean something — "this session reached KhanaBook
 * recently" — instead of painting a permanent green light that asserts nothing.
 *
 * <p>What this is not: a user directory of who is online. Reading presence for
 * <em>other</em> users would be a different feature (a per-user session read), and
 * this column is the foundation for it, not the feature itself.
 *
 * <p>Write cost is bounded in SQL, not here: the row is touched at most once a
 * minute per user, so a forgotten tab open all day is ~1440 UPDATEs rather than
 * one per poll.
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    /** How stale a timestamp may be before the row is written again. */
    public static final long HEARTBEAT_THROTTLE_MS = 60_000L;

    private final UserRepository userRepository;

    public PresenceService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Marks the user as seen. Never throws: a heartbeat is telemetry attached to a
     * request the caller actually needed, and a failed UPDATE must not turn a
     * working page into an error. The transaction boundary lives on the repository
     * method so this catch sits outside it — catching inside a @Transactional
     * method would leave that transaction marked rollback-only and fail the commit
     * with an UnexpectedRollbackException instead.
     */
    public void touch(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            userRepository.touchLastSeen(userId, now, now - HEARTBEAT_THROTTLE_MS);
        } catch (Exception e) {
            log.debug("Presence heartbeat skipped for user {}: {}", userId, e.getMessage());
        }
    }
}
