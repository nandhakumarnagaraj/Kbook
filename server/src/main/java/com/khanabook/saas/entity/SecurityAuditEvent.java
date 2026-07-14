package com.khanabook.saas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Append-only audit log of security-relevant sync events (cross-terminal mutation
 * attempts, publicToken replay, disabled terminals, explicit transfers). Never
 * contains secrets, tokens, or OTPs.
 */
@Entity
@Table(name = "security_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class SecurityAuditEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "restaurant_id")
	private Long restaurantId;

	@Column(name = "user_id")
	private String userId;

	@Column(name = "terminal_id")
	private String terminalId;

	@Column(name = "terminal_series")
	private String terminalSeries;

	@Column(name = "target_canonical_id")
	private String targetCanonicalId;

	@Column(name = "target_owner_terminal")
	private String targetOwnerTerminal;

	@Column(name = "action", nullable = false)
	private String action;

	@Column(name = "outcome", nullable = false)
	private String outcome;

	@Column(name = "request_id")
	private String requestId;

	@Column(name = "created_at", nullable = false)
	private Long createdAt;

	public SecurityAuditEvent(Long restaurantId, String userId, String terminalId, String terminalSeries,
			String targetCanonicalId, String targetOwnerTerminal, String action, String outcome,
			String requestId, Long createdAt) {
		this.restaurantId = restaurantId;
		this.userId = userId;
		this.terminalId = terminalId;
		this.terminalSeries = terminalSeries;
		this.targetCanonicalId = targetCanonicalId;
		this.targetOwnerTerminal = targetOwnerTerminal;
		this.action = action;
		this.outcome = outcome;
		this.requestId = requestId;
		this.createdAt = createdAt;
	}
}
