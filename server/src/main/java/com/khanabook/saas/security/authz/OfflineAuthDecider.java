package com.khanabook.saas.security.authz;

/**
 * Pure decision engine for offline-created operations reaching the server at sync.
 *
 * <p>Server is the final authority. This decides ACCEPT / REJECT / QUARANTINE for a
 * single operation given its required permission and the current server-side
 * authorization facts. It performs NO I/O — callers supply the facts — so it is
 * fully unit-testable and free of side effects (Step 2). Wiring into the sync path
 * and capturing the real revision from Android come in later steps.
 */
public final class OfflineAuthDecider {

    public enum Decision { ACCEPT, REJECT, QUARANTINE }

    public record Result(Decision decision, String reason) {}

    private OfflineAuthDecider() {}

    /**
     * @param permissionKey             the permission the operation requires (null → no permission gate)
     * @param permissionRevisionAtCreation revision captured on the device when the op was created
     *                                     (may be null if the client did not/could not provide it)
     * @param grantedNow                whether the user currently holds the permission
     * @param lastRevokedRevision       revision at which the key was most recently revoked (null = never)
     * @param terminalValid             whether the terminal credential is currently valid (independent axis)
     */
    public static Result decide(String permissionKey,
                                Long permissionRevisionAtCreation,
                                boolean grantedNow,
                                Long lastRevokedRevision,
                                boolean terminalValid) {
        // 0. Terminal authority is an independent security axis; it fails first.
        if (!terminalValid) {
            return new Result(Decision.REJECT, "TERMINAL_REVOKED");
        }

        // No permission gate on this operation → accept (e.g. unclassified/none).
        if (permissionKey == null) {
            return new Result(Decision.ACCEPT, "NO_PERMISSION_REQUIRED");
        }

        OfflineAuthClass cls = OfflineAuthClass.forKey(permissionKey);
        switch (cls) {
            case OFFLINE_ALLOWED:
                return new Result(Decision.ACCEPT, "OFFLINE_ALLOWED");

            case NEVER_OFFLINE:
                // Should never have been created offline; preserve for admin review.
                return new Result(Decision.QUARANTINE, "NEVER_OFFLINE");

            case ONLINE_ONLY:
                // Requires live authorization; an offline-created op cannot be auto-decided.
                return new Result(Decision.QUARANTINE, "ONLINE_ONLY");

            case REVALIDATED_ON_SYNC:
                return revalidate(permissionRevisionAtCreation, grantedNow, lastRevokedRevision);

            default:
                // Defensive: unknown class → safest posture.
                return new Result(Decision.QUARANTINE, "UNCLASSIFIED");
        }
    }

    /**
     * Decision A (strict): an operation created at revision R is unauthorized if the
     * permission was revoked at any revision >= R (even if later re-granted), or if it
     * is not currently granted.
     */
    private static Result revalidate(Long createdRevision, boolean grantedNow, Long lastRevokedRevision) {
        if (!grantedNow) {
            return new Result(Decision.REJECT, "PERMISSION_NOT_GRANTED");
        }
        // If the client could not supply a creation revision, we cannot prove the op
        // pre-dated a revocation; treat conservatively as quarantine for review.
        if (createdRevision == null) {
            return new Result(Decision.QUARANTINE, "MISSING_CREATION_REVISION");
        }
        if (lastRevokedRevision != null && lastRevokedRevision >= createdRevision) {
            return new Result(Decision.REJECT, "REVOKED_AFTER_CREATION");
        }
        return new Result(Decision.ACCEPT, "CONTINUOUSLY_AUTHORIZED");
    }
}
