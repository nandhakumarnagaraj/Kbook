package com.khanabook.saas.security;

public class TenantContext {

	private static final ThreadLocal<Long> currentTenant = new ThreadLocal<>();
	private static final ThreadLocal<String> currentRole = new ThreadLocal<>();

	public static void setCurrentTenant(Long tenantId) {
		currentTenant.set(tenantId);
	}

	public static Long getCurrentTenant() {
		return currentTenant.get();
	}

	public static void setCurrentRole(String role) {
		currentRole.set(role);
	}

	public static String getCurrentRole() {
		return currentRole.get();
	}

	public static void clear() {
		currentTenant.remove();
		currentRole.remove();
	}
}
