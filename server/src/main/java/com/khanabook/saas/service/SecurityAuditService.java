package com.khanabook.saas.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.khanabook.saas.entity.SecurityAuditEvent;
import com.khanabook.saas.repository.SecurityAuditLogRepository;
import com.khanabook.saas.security.TenantContext;

/**
 * Records security-relevant sync events to the audit log and application logs.
 * Does not persist any secrets, tokens, or OTPs.
 */
@Service
public class SecurityAuditService {

	private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

	@Autowired
	private SecurityAuditLogRepository repository;

	/**
	 * Records an audit event using TenantContext for actor information.
	 * Use for request-scoped operations where context is reliably set.
	 */
	public void record(String action, String outcome, String targetCanonicalId, String targetOwnerTerminal) {
		Long userId = TenantContext.getCurrentUserId();
		String role = TenantContext.getCurrentRole();
		record(action, outcome, targetCanonicalId, targetOwnerTerminal,
				userId != null ? userId.toString() : null,
				role != null ? role : "UNKNOWN");
	}

	/**
	 * Records an audit event with an explicit actor. Use for security-sensitive
	 * operations, background jobs, or anywhere TenantContext may be stale or absent.
	 */
	public void record(String action, String outcome, String targetCanonicalId, String targetOwnerTerminal,
					   String actorUserId, String actorRole) {
		try {
			Long restaurantId = TenantContext.getCurrentTenant();
			String terminalId = TenantContext.getCurrentTerminalId();
			String terminalSeries = TenantContext.getCurrentTerminalSeries();
			SecurityAuditEvent event = new SecurityAuditEvent(restaurantId,
					actorUserId,
					terminalId, terminalSeries,
					targetCanonicalId, targetOwnerTerminal, action, outcome, UUID.randomUUID().toString(),
					System.currentTimeMillis());
			repository.save(event);
			log.warn("SECURITY_AUDIT action={} outcome={} restaurantId={} actor={}/{} terminalId={} target={} owner={}",
					action, outcome, restaurantId, actorUserId, actorRole, terminalId, targetCanonicalId, targetOwnerTerminal);
		} catch (Exception e) {
			log.error("Failed to persist security audit event action={} outcome={}", action, outcome, e);
		}
	}
}
