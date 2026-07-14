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

	public void record(String action, String outcome, String targetCanonicalId, String targetOwnerTerminal) {
		try {
			Long restaurantId = TenantContext.getCurrentTenant();
			String terminalId = TenantContext.getCurrentTerminalId();
			String terminalSeries = TenantContext.getCurrentTerminalSeries();
			SecurityAuditEvent event = new SecurityAuditEvent(restaurantId, null, terminalId, terminalSeries,
					targetCanonicalId, targetOwnerTerminal, action, outcome, UUID.randomUUID().toString(),
					System.currentTimeMillis());
			repository.save(event);
			log.warn("SECURITY_AUDIT action={} outcome={} restaurantId={} terminalId={} terminalSeries={} target={} owner={}",
					action, outcome, restaurantId, terminalId, terminalSeries, targetCanonicalId, targetOwnerTerminal);
		} catch (Exception e) {
			log.error("Failed to persist security audit event action={} outcome={}", action, outcome, e);
		}
	}
}
