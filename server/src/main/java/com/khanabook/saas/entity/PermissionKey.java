package com.khanabook.saas.entity;

public enum PermissionKey {
    // Billing
    BILLING_CREATE("billing.create", "Create Bills", "BILLING"),
    BILLING_EDIT("billing.edit", "Edit Open Bills", "BILLING"),
    BILLING_VOID("billing.void", "Cancel/Void Bills", "BILLING"),
    BILLING_DISCOUNT("billing.discount", "Apply Discounts", "BILLING"),
    BILLING_REFUND("billing.refund", "Process Refunds", "BILLING"),
    BILLING_SETTLE("billing.settle", "Mark Payment Received", "BILLING"),

    // Menu
    MENU_VIEW("menu.view", "View Menu Items", "MENU"),
    MENU_TOGGLE_AVAILABILITY("menu.toggle_availability", "Toggle Item Availability", "MENU"),
    MENU_EDIT_PRICE("menu.edit_price", "Change Prices", "MENU"),
    MENU_ADD_ITEM("menu.add_item", "Add New Items", "MENU"),
    MENU_DELETE_ITEM("menu.delete_item", "Remove Items", "MENU"),

    // Orders / KOT
    ORDERS_VIEW("orders.view", "View Order List", "ORDERS"),
    ORDERS_KOT_VIEW("orders.kot_view", "See Kitchen Queue", "ORDERS"),
    ORDERS_KOT_READY("orders.kot_ready", "Mark Items Ready", "ORDERS"),
    ORDERS_KOT_VOID("orders.kot_void", "Void KOT Items", "ORDERS"),

    // Reports
    REPORTS_DAY_SUMMARY("reports.day_summary", "Today's Sales Summary", "REPORTS"),
    REPORTS_FULL("reports.full", "Full Revenue Reports", "REPORTS"),
    REPORTS_GST("reports.gst", "GST/Tax Reports", "REPORTS"),
    REPORTS_EXPORT("reports.export", "Export/Download Data", "REPORTS"),

    // Staff
    STAFF_VIEW("staff.view", "View Staff List", "STAFF"),
    STAFF_ADD("staff.add", "Add New Staff", "STAFF"),
    STAFF_EDIT("staff.edit", "Edit Staff Details", "STAFF"),
    STAFF_REMOVE("staff.remove", "Deactivate Staff", "STAFF"),
    STAFF_PERMISSIONS("staff.permissions", "Manage Permissions", "STAFF"),

    // Settings
    SETTINGS_SHOP_PROFILE("settings.shop_profile", "Edit Shop Profile", "SETTINGS"),
    SETTINGS_PAYMENT("settings.payment", "Bank/UPI Settings", "SETTINGS"),
    SETTINGS_PRINTER("settings.printer", "Printer Configuration", "SETTINGS"),
    SETTINGS_TERMINAL("settings.terminal", "Manage Devices", "SETTINGS"),
    SETTINGS_GST("settings.gst", "GST/FSSAI Settings", "SETTINGS");

    private final String key;
    private final String displayName;
    private final String category;

    PermissionKey(String key, String displayName, String category) {
        this.key = key;
        this.displayName = displayName;
        this.category = category;
    }

    public String getKey() { return key; }
    public String getDisplayName() { return displayName; }
    public String getCategory() { return category; }

    public static PermissionKey fromKey(String key) {
        for (PermissionKey pk : values()) {
            if (pk.key.equals(key)) return pk;
        }
        return null;
    }
}
