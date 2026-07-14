package com.khanabook.saas.security;

public class TenantContext {

	private static final ThreadLocal<Long> currentTenant = new ThreadLocal<>();
	private static final ThreadLocal<String> currentRole = new ThreadLocal<>();
	private static final ThreadLocal<String> currentTerminalId = new ThreadLocal<>();
	private static final ThreadLocal<String> currentTerminalSeries = new ThreadLocal<>();
	private static final ThreadLocal<String> currentTerminalDevice = new ThreadLocal<>();
	private static final ThreadLocal<Boolean> currentTerminalActive = new ThreadLocal<>();

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

	public static void setCurrentTerminalId(String terminalId) {
		currentTerminalId.set(terminalId);
	}

	public static String getCurrentTerminalId() {
		return currentTerminalId.get();
	}

	public static void setCurrentTerminalSeries(String terminalSeries) {
		currentTerminalSeries.set(terminalSeries);
	}

	public static String getCurrentTerminalSeries() {
		return currentTerminalSeries.get();
	}

	public static void setCurrentTerminalDevice(String deviceId) {
		currentTerminalDevice.set(deviceId);
	}

	public static String getCurrentTerminalDevice() {
		return currentTerminalDevice.get();
	}

	public static void setCurrentTerminalActive(Boolean active) {
		currentTerminalActive.set(active);
	}

	public static Boolean getCurrentTerminalActive() {
		return currentTerminalActive.get();
	}

	public static void clear() {
		currentTenant.remove();
		currentRole.remove();
		currentTerminalId.remove();
		currentTerminalSeries.remove();
		currentTerminalDevice.remove();
		currentTerminalActive.remove();
	}
}
