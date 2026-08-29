package com.khanabook.saas.security.authz;

import org.junit.jupiter.api.Test;

import static com.khanabook.saas.security.authz.OfflineAuthDecider.Decision.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Step 2: proves the offline-authorization decision engine (ACCEPT/REJECT/QUARANTINE)
 * against the critical distributed-state scenarios. Pure unit tests — no I/O.
 */
class OfflineAuthDeciderTest {

    private static final String SETTLE = "billing.settle";   // REVALIDATED_ON_SYNC
    private static final String CREATE = "billing.create";   // OFFLINE_ALLOWED
    private static final String REFUND = "billing.refund";   // ONLINE_ONLY
    private static final String STAFF  = "staff.permissions"; // NEVER_OFFLINE

    @Test
    void allPermissionKeysAreClassified() {
        assertTrue(OfflineAuthClass.allKeysClassified(), "every PermissionKey must have an explicit offline class");
    }

    @Test
    void offlineAllowed_accepts() {
        var r = OfflineAuthDecider.decide(CREATE, 5L, true, null, true);
        assertEquals(ACCEPT, r.decision());
    }

    @Test
    void grantThenOfflineOp_thenReconnectNoRevoke_accepts() {
        // op created at rev 5, still granted, never revoked
        var r = OfflineAuthDecider.decide(SETTLE, 5L, true, null, true);
        assertEquals(ACCEPT, r.decision());
        assertEquals("CONTINUOUSLY_AUTHORIZED", r.reason());
    }

    @Test
    void grantRevokeRestore_thenReconnect_rejects() {
        // op created at rev 5; permission revoked at rev 6 (>=5), later restored (granted now).
        // Decision A strict → REJECT even though currently granted.
        var r = OfflineAuthDecider.decide(SETTLE, 5L, true, 6L, true);
        assertEquals(REJECT, r.decision());
        assertEquals("REVOKED_AFTER_CREATION", r.reason());
    }

    @Test
    void revokedBeforeCreation_doesNotReject() {
        // revoked at rev 3, op created later at rev 5 while granted → the revocation
        // predates the op, so it is not the reason to reject; continuously authorized since.
        var r = OfflineAuthDecider.decide(SETTLE, 5L, true, 3L, true);
        assertEquals(ACCEPT, r.decision());
    }

    @Test
    void currentlyRevoked_rejects() {
        var r = OfflineAuthDecider.decide(SETTLE, 5L, false, 6L, true);
        assertEquals(REJECT, r.decision());
        assertEquals("PERMISSION_NOT_GRANTED", r.reason());
    }

    @Test
    void onlineOnlyCreatedOffline_quarantines() {
        var r = OfflineAuthDecider.decide(REFUND, 5L, true, null, true);
        assertEquals(QUARANTINE, r.decision());
        assertEquals("ONLINE_ONLY", r.reason());
    }

    @Test
    void neverOfflineCreatedOffline_quarantines() {
        var r = OfflineAuthDecider.decide(STAFF, 5L, true, null, true);
        assertEquals(QUARANTINE, r.decision());
        assertEquals("NEVER_OFFLINE", r.reason());
    }

    @Test
    void terminalRevoked_rejectsIndependentlyOfPermission() {
        // Even a fully-authorized OFFLINE_ALLOWED op fails if the terminal is revoked.
        var r = OfflineAuthDecider.decide(CREATE, 5L, true, null, false);
        assertEquals(REJECT, r.decision());
        assertEquals("TERMINAL_REVOKED", r.reason());
    }

    @Test
    void missingCreationRevision_onRevalidated_quarantines() {
        var r = OfflineAuthDecider.decide(SETTLE, null, true, null, true);
        assertEquals(QUARANTINE, r.decision());
        assertEquals("MISSING_CREATION_REVISION", r.reason());
    }

    @Test
    void unknownKey_defaultsToNeverOffline() {
        assertEquals(OfflineAuthClass.NEVER_OFFLINE, OfflineAuthClass.forKey("some.new.permission"));
    }
}
