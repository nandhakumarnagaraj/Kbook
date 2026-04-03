package com.khanabook.saas.entity;

import com.khanabook.saas.sync.entity.BaseSyncEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users", indexes = { @Index(name = "idx_users_tenant_updated", columnList = "restaurant_id, updated_at"),
		@Index(name = "idx_users_device", columnList = "restaurant_id, device_id, local_id"),
		@Index(name = "idx_users_whatsapp_number", columnList = "whatsapp_number"),
		@Index(name = "idx_users_google_email", columnList = "google_email") })
@Getter
@Setter
public class User extends BaseSyncEntity {

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "email", unique = true)
	private String email;

	@Column(name = "login_id", nullable = false, unique = true)
	private String loginId;

	@Column(name = "phone_number", unique = true)
	private String phoneNumber;

	@Column(name = "google_email")
	private String googleEmail;

	@jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
	@Column(name = "auth_provider", nullable = false)
	private AuthProvider authProvider = AuthProvider.PHONE;

	@com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
	@Column(name = "password_hash")
	private String passwordHash;

	@Column(name = "whatsapp_number")
	private String whatsappNumber;

	@jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
	@Column(name = "role", nullable = false)
	private UserRole role = UserRole.OWNER;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	/** Set on password reset to invalidate all tokens issued before this time. */
	@Column(name = "token_invalidated_at")
	private Long tokenInvalidatedAt;
}
