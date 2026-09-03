package com.khanabook.saas.security.authz;

import com.khanabook.saas.entity.MenuItem;

import java.math.BigDecimal;
import java.util.function.Predicate;

/**
 * Authorizes a single menu-item push against the acting user's fine-grained
 * permissions. Server is the final authority — the Android UI gates
 * ({@code MenuViewModel.canEditMenu()} etc.) are advisory only.
 *
 * <p>Two responsibilities, both pure (no I/O — the caller supplies permission
 * facts, exactly like {@link OfflineAuthDecider}):
 * <ol>
 *   <li>{@link #detect(MenuItem, MenuItem)} — diff the incoming row against the
 *       existing server row to classify the {@link MenuChangeType}.</li>
 *   <li>{@link #authorize} — for every permission the change requires, run it
 *       through {@link OfflineAuthDecider} (which encodes the offline posture and
 *       Decision-A-strict revocation rule) and combine the results.</li>
 * </ol>
 *
 * <p>Combination rule across the (at most two) required keys: the strictest
 * outcome wins — any REJECT ⇒ REJECT; else any QUARANTINE ⇒ QUARANTINE; else
 * ACCEPT. This means a "price + availability" push where the user may change
 * price but not availability is rejected as a whole (the change is not split).
 */
public final class MenuPushAuthorizer {

    /**
     * Facts about the acting user's authorization for one permission key, at the
     * moment of sync. Supplied by the caller from persistent state.
     *
     * @param grantedNow           whether the user currently holds the key
     * @param lastRevokedRevision  revision at which the key was most recently revoked (null = never)
     */
    public record PermissionFacts(boolean grantedNow, Long lastRevokedRevision) {}

    /** Resolves live permission facts for a given permission key. */
    public interface FactsResolver {
        PermissionFacts resolve(String permissionKey);
    }

    public record Result(OfflineAuthDecider.Decision decision, String reason, MenuChangeType changeType) {
        public boolean isAccept() {
            return decision == OfflineAuthDecider.Decision.ACCEPT;
        }
    }

    private MenuPushAuthorizer() {}

    /**
     * Classify what the incoming push changes relative to {@code existing}.
     *
     * @param incoming the pushed row (never null)
     * @param existing the current server row, or null if this is a brand-new item
     */
    public static MenuChangeType detect(MenuItem incoming, MenuItem existing) {
        // A soft-delete push is a delete regardless of incidental field changes.
        boolean incomingDeleted = Boolean.TRUE.equals(incoming.getIsDeleted());
        boolean existingDeleted = existing != null && Boolean.TRUE.equals(existing.getIsDeleted());
        if (incomingDeleted && !existingDeleted) {
            return MenuChangeType.DELETE;
        }

        // No existing (non-deleted) row → this is a create.
        if (existing == null) {
            // A push that arrives already-deleted with no server row is a no-op.
            return incomingDeleted ? MenuChangeType.NONE : MenuChangeType.CREATE;
        }

        boolean priceChanged = priceChanged(existing.getBasePrice(), incoming.getBasePrice());
        boolean availabilityChanged = availabilityChanged(existing.getIsAvailable(), incoming.getIsAvailable());

        if (priceChanged && availabilityChanged) return MenuChangeType.PRICE_AND_AVAILABILITY;
        if (priceChanged) return MenuChangeType.PRICE;
        if (availabilityChanged) return MenuChangeType.AVAILABILITY;

        if (metadataChanged(incoming, existing)) return MenuChangeType.METADATA_ONLY;
        return MenuChangeType.NONE;
    }

    /**
     * Authorize the push.
     *
     * @param incoming            the pushed row
     * @param existing            the current server row (null = create)
     * @param revisionAtCreation  permission revision captured on-device when the op was
     *                            created (P1 plumbing; null today)
     * @param terminalValid       whether the calling terminal credential is currently valid
     * @param resolver            supplies live permission facts per key
     *
     * <p><b>P0-without-P1 posture:</b> until the device stamps a creation revision (P1),
     * {@code revisionAtCreation} is null. Under strict Decision-A this would QUARANTINE
     * every edited row ({@code MISSING_CREATION_REVISION}), which would break all
     * legitimate staff/owner menu edits. So while the revision is null we enforce the
     * <em>grant</em> gate only: a currently-granted permission ACCEPTs, a missing/ungranted
     * permission REJECTs. Once P1 supplies the revision, full Decision-A strict
     * (reject if {@code lastRevokedRevision >= revisionAtCreation}) applies automatically.
     */
    public static Result authorize(MenuItem incoming,
                                   MenuItem existing,
                                   Long revisionAtCreation,
                                   boolean terminalValid,
                                   FactsResolver resolver) {
        MenuChangeType changeType = detect(incoming, existing);

        if (changeType.requiredPermissionKeys().isEmpty()) {
            // No-op push: nothing to authorize, but a revoked terminal still fails.
            var r = OfflineAuthDecider.decide(null, revisionAtCreation, true, null, terminalValid);
            return new Result(r.decision(), r.reason(), changeType);
        }

        OfflineAuthDecider.Result combined = null;
        for (String key : changeType.requiredPermissionKeys()) {
            PermissionFacts facts = resolver.resolve(key);
            OfflineAuthDecider.Result r = OfflineAuthDecider.decide(
                    key,
                    revisionAtCreation,
                    facts.grantedNow(),
                    facts.lastRevokedRevision(),
                    terminalValid);
            // P0-without-P1: a QUARANTINE caused solely by the absent creation revision
            // is downgraded to ACCEPT when the key is currently granted. The grant gate
            // is still fully enforced (ungranted keys already REJECTed inside decide()).
            if (r.decision() == OfflineAuthDecider.Decision.QUARANTINE
                    && "MISSING_CREATION_REVISION".equals(r.reason())
                    && facts.grantedNow()
                    && terminalValid) {
                r = new OfflineAuthDecider.Result(
                        OfflineAuthDecider.Decision.ACCEPT, "GRANTED_NO_REVISION_YET");
            }
            combined = strictest(combined, r);
            // Short-circuit on the hardest outcome.
            if (combined.decision() == OfflineAuthDecider.Decision.REJECT) break;
        }
        return new Result(combined.decision(), combined.reason(), changeType);
    }

    // ── diff helpers ────────────────────────────────────────────────────────

    private static boolean priceChanged(BigDecimal existing, BigDecimal incoming) {
        if (incoming == null) return false; // client omitted price → not a price edit
        if (existing == null) return true;
        return existing.compareTo(incoming) != 0; // value compare, ignores scale
    }

    private static boolean availabilityChanged(Boolean existing, Boolean incoming) {
        if (incoming == null) return false; // client omitted availability → not a toggle
        boolean existingVal = existing == null || existing; // server default is available
        return existingVal != incoming;
    }

    private static boolean metadataChanged(MenuItem incoming, MenuItem existing) {
        return changed(incoming.getName(), existing.getName())
                || changed(incoming.getDescription(), existing.getDescription())
                || changed(incoming.getFoodType(), existing.getFoodType())
                || changed(incoming.getBarcode(), existing.getBarcode())
                || changed(incoming.getServerCategoryId(), existing.getServerCategoryId());
    }

    /** Changed only when the client SENT a value that differs (null = not provided). */
    private static <V> boolean changed(V incoming, V existing) {
        if (incoming == null) return false;
        return !incoming.equals(existing);
    }

    private static OfflineAuthDecider.Result strictest(OfflineAuthDecider.Result a, OfflineAuthDecider.Result b) {
        if (a == null) return b;
        return rank(b.decision()) > rank(a.decision()) ? b : a;
    }

    private static int rank(OfflineAuthDecider.Decision d) {
        return switch (d) {
            case ACCEPT -> 0;
            case QUARANTINE -> 1;
            case REJECT -> 2;
        };
    }

    /** Convenience for callers holding a simple predicate + revocation lookup. */
    public static FactsResolver factsResolver(Predicate<String> grantedNow,
                                              java.util.function.Function<String, Long> lastRevokedRevision) {
        return key -> new PermissionFacts(grantedNow.test(key), lastRevokedRevision.apply(key));
    }
}
