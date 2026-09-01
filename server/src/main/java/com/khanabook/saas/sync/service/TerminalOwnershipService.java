package com.khanabook.saas.sync.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.RestaurantTerminal;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.SecurityAuditService;
import com.khanabook.saas.sync.entity.BaseSyncEntity;
import com.khanabook.saas.util.BillTerminalUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.springframework.http.HttpStatus.*;

/**
 * Resolves terminal ownership context and enforces cross-terminal access rules
 * for sync operations (bills, bill items, bill payments).
 */
@Component
public class TerminalOwnershipService {
    private static final Logger log = LoggerFactory.getLogger(TerminalOwnershipService.class);

    private final RestaurantTerminalRepository terminalRepository;
    private final BillRepository billRepository;
    private final SecurityAuditService securityAuditService;

    public TerminalOwnershipService(RestaurantTerminalRepository terminalRepository,
                                     BillRepository billRepository,
                                     SecurityAuditService securityAuditService) {
        this.terminalRepository = terminalRepository;
        this.billRepository = billRepository;
        this.securityAuditService = securityAuditService;
    }

    /** Resolved terminal identity from the authenticated X-Terminal-Token. */
    public static class TerminalContext {
        public final String terminalId;
        public final String terminalSeries;
        public final String deviceId;

        public TerminalContext(String terminalId, String terminalSeries, String deviceId) {
            this.terminalId = terminalId;
            this.terminalSeries = terminalSeries;
            this.deviceId = deviceId;
        }
    }

    /**
     * Resolves and validates the terminal from the current JWT context.
     * Returns null if no terminal token is present (legacy client).
     */
    public TerminalContext resolveTerminal(Long tenantId, boolean isAdmin) {
        if (isAdmin) return null;

        String authTerminalId = TenantContext.getCurrentTerminalId();
        String authTerminalSeries = TenantContext.getCurrentTerminalSeries();
        String authDeviceId = TenantContext.getCurrentTerminalDevice();

        if (authTerminalId == null && authTerminalSeries == null) return null;

        RestaurantTerminal terminal = (authTerminalSeries != null)
                ? terminalRepository.findByRestaurantIdAndTerminalSeries(tenantId, authTerminalSeries).orElse(null)
                : terminalRepository.findById(Long.valueOf(authTerminalId)).orElse(null);

        if (terminal == null) {
            securityAuditService.record("SYNC_PUSH", "TERMINAL_UNKNOWN", null, authTerminalId);
            throw new ResponseStatusException(FORBIDDEN, "Terminal is not registered for this restaurant");
        }
        if (Boolean.FALSE.equals(terminal.getIsActive())) {
            securityAuditService.record("SYNC_PUSH", "TERMINAL_DISABLED", null,
                    terminal.getId() != null ? terminal.getId().toString() : authTerminalSeries);
            throw new ResponseStatusException(FORBIDDEN, "Terminal is disabled");
        }

        String resolvedId = terminal.getId() != null ? terminal.getId().toString() : terminal.getTerminalSeries();
        String resolvedSeries = terminal.getTerminalSeries();
        String resolvedDeviceId = terminal.getDeviceId() != null ? terminal.getDeviceId() : authDeviceId;

        return new TerminalContext(resolvedId, resolvedSeries, resolvedDeviceId);
    }

    /**
     * Enforces strict mode: rejects transactional payloads without a terminal token.
     */
    public void enforceStrictMode(boolean hasTransactional, boolean hasTerminalContext, boolean strictMode) {
        if (hasTransactional && !hasTerminalContext && strictMode) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Terminal identity required for sync: activate a terminal and send X-Terminal-Token");
        }
    }

    /**
     * Checks if a child record (BillItem/BillPayment) may be written against its parent bill.
     */
    public boolean isChildOwnershipAllowed(BaseSyncEntity record, Long tenantId,
                                            String trustedTerminalId, String trustedDeviceId,
                                            boolean isAdmin) {
        Long serverBillId = resolveParentBillId(record);
        if (serverBillId == null) return true;

        Optional<Bill> parent = billRepository.findById(serverBillId)
                .filter(b -> b.getRestaurantId().equals(tenantId));
        if (parent.isEmpty()) return true;

        Bill parentBill = parent.get();
        if (BillTerminalUtil.isFinalized(parentBill)) {
            return isOwnerTerminalOrAdmin(parentBill, trustedTerminalId, trustedDeviceId, isAdmin);
        }
        return BillTerminalUtil.isModifiableByTerminal(parentBill, trustedTerminalId, trustedDeviceId, isAdmin);
    }

    /**
     * Checks if the terminal owns the bill (or is admin, or bill is legacy).
     */
    public boolean isOwnerTerminalOrAdmin(Bill parent, String trustedTerminalId,
                                          String trustedDeviceId, boolean isAdmin) {
        if (isAdmin) return true;
        String owner = BillTerminalUtil.ownerTerminalId(parent);
        if (owner == null) return true;
        if (BillTerminalUtil.LEGACY_UNRESOLVED.equals(owner)) {
            return trustedDeviceId != null && trustedDeviceId.equals(parent.getCreatedDeviceId());
        }
        if (trustedTerminalId == null) return true; // legacy no-token client
        return owner.equals(trustedTerminalId);
    }

    /** Returns the public token of the parent bill for audit logging. */
    public String childParentToken(BaseSyncEntity record) {
        Long serverBillId = resolveParentBillId(record);
        if (serverBillId == null) return null;
        return billRepository.findById(serverBillId)
                .map(b -> b.getPublicToken() != null ? b.getPublicToken().toString() : null)
                .orElse(null);
    }

    /** Returns the owner terminal ID of the parent bill for audit logging. */
    public String childOwnerTerminal(BaseSyncEntity record, Long tenantId) {
        Long serverBillId = resolveParentBillId(record);
        if (serverBillId == null) return null;
        return billRepository.findById(serverBillId)
                .filter(b -> b.getRestaurantId().equals(tenantId))
                .map(BillTerminalUtil::ownerTerminalId)
                .orElse(null);
    }

    private Long resolveParentBillId(BaseSyncEntity record) {
        if (record instanceof com.khanabook.saas.entity.BillItem bi) {
            return bi.getServerBillId();
        } else if (record instanceof com.khanabook.saas.entity.BillPayment bp) {
            return bp.getServerBillId();
        }
        return null;
    }
}
