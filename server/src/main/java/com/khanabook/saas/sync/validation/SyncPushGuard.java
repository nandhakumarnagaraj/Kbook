package com.khanabook.saas.sync.validation;

import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.PermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

public final class SyncPushGuard {

	private static final int MAX_PUSH_BATCH_SIZE = 200;

	private SyncPushGuard() {}

	public static <T> void validateBatchSize(List<T> payload) {
		if (payload == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}
		if (payload.size() > MAX_PUSH_BATCH_SIZE) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
					"Maximum " + MAX_PUSH_BATCH_SIZE + " records per push request, got " + payload.size());
		}
	}

	/**
	 * Coarse push gate for staff sync: OWNER/KBOOK_ADMIN always pass; every other
	 * actor must currently hold the required permission key or the whole batch is
	 * rejected (403). Fine-grained, per-record revision revalidation is layered on
	 * top where the offline-auth model applies (e.g. menu via MenuPushAuthorizer).
	 */
	public static void requirePermission(String permissionKey, PermissionService permissionService) {
		String role = TenantContext.getCurrentRole();
		if ("OWNER".equals(role) || "KBOOK_ADMIN".equals(role)) {
			return;
		}
		Long tenantId = TenantContext.getCurrentTenant();
		Long userId = TenantContext.getCurrentUserId();
		if (tenantId == null || userId == null
				|| !permissionService.hasPermission(tenantId, userId, permissionKey)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"Missing required permission: " + permissionKey);
		}
	}
}
