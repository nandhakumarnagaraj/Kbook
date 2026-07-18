# Requirements Document

## Introduction

This document defines the requirements for expanding the KhanaBook Web Admin from a read-only monitoring panel to a full management console. The scope covers eight capability areas: roles and permissions enforcement, staff CRUD operations, dashboard interactivity, order enhancements, terminal reactivation, menu management from web, login flow improvements, and business lifecycle management. All changes span the Angular 18 frontend and the Spring Boot 3.5 backend.

## Glossary

- **Web_Admin**: The Angular 18.2 single-page application served at the KhanaBook web domain, used by platform administrators and restaurant owners to manage their businesses.
- **KBOOK_ADMIN**: Platform-level administrator role with access to all businesses and platform-wide operations.
- **OWNER**: Restaurant owner role with full access to their own business data and staff management.
- **SHOP_ADMIN**: Shop-level administrator role with access to terminal management and limited business operations.
- **Staff_Service**: The backend service responsible for creating, updating, and deactivating staff user accounts within a business.
- **Auth_Service**: The backend service handling authentication flows including login, password reset, and token management.
- **Terminal_Service**: The backend service managing POS terminal lifecycle including activation, deactivation, and reactivation.
- **Menu_Service**: The backend service managing menu items, categories, and availability for a business.
- **Business_Service**: The backend service managing restaurant business profiles including suspension and activation.
- **Dashboard_Component**: The Angular component rendering KPI stats, charts, and summary data for either the platform or a specific business.
- **Orders_Component**: The Angular component displaying order history with filtering, detail views, and export capabilities.
- **Permission_Guard**: The Angular route guard and backend authorization check that restricts access based on user role.

## Requirements

### Requirement 1: Role-Based Access Enforcement

**User Story:** As a platform administrator, I want role-based access control enforced on all web admin write operations, so that only authorized users can perform management actions.

#### Acceptance Criteria

1. THE Permission_Guard SHALL restrict Staff_Service write operations (create, edit, deactivate, role-change) to users with OWNER role.
2. THE Permission_Guard SHALL restrict Business_Service suspend and activate operations to users with KBOOK_ADMIN role.
3. THE Permission_Guard SHALL restrict Terminal_Service reactivation to users with OWNER or SHOP_ADMIN role.
4. THE Permission_Guard SHALL restrict Menu_Service write operations (create, edit, toggle-availability) to users with OWNER role.
5. IF an unauthorized user attempts a restricted operation, THEN THE Web_Admin SHALL display an access-denied message and prevent the request from reaching the server.
6. IF a request reaches the server without the required role, THEN THE Auth_Service SHALL return HTTP 403 with an error message identifying the missing permission.

### Requirement 2: Staff Creation and Invitation

**User Story:** As a restaurant owner, I want to add new staff members from the web admin, so that I can onboard team members without using the Android app.

#### Acceptance Criteria

1. WHEN the OWNER clicks the "Add Staff" button, THE Web_Admin SHALL display a form requesting name, phone number, role selection (OWNER, SHOP_ADMIN), and optional email.
2. WHEN the OWNER submits a valid staff creation form, THE Staff_Service SHALL create a new user account with a system-generated temporary password.
3. WHEN staff creation succeeds, THE Web_Admin SHALL display the temporary password to the OWNER once for sharing with the new staff member.
4. IF the phone number already exists in the system, THEN THE Staff_Service SHALL return an error indicating duplicate phone number.
5. THE Staff_Service SHALL validate that the phone number contains exactly 10 digits.
6. THE Staff_Service SHALL validate that the selected role is one of the permitted values (OWNER, SHOP_ADMIN).

### Requirement 3: Staff Edit and Role Change

**User Story:** As a restaurant owner, I want to edit staff details and change roles from the web admin, so that I can manage team access without needing physical device access.

#### Acceptance Criteria

1. WHEN the OWNER clicks "Edit" on a staff member row, THE Web_Admin SHALL display a form pre-filled with the staff member's current name, phone, email, and role.
2. WHEN the OWNER submits the edit form with valid data, THE Staff_Service SHALL update the staff member's profile.
3. WHEN the OWNER changes a staff member's role, THE Staff_Service SHALL update the role and invalidate any existing sessions for that staff member.
4. IF the OWNER attempts to change their own role, THEN THE Web_Admin SHALL prevent the action and display a message stating that self-role-change is not allowed.

### Requirement 4: Staff Deactivation

**User Story:** As a restaurant owner, I want to deactivate staff members from the web admin, so that I can immediately revoke access for departed team members.

#### Acceptance Criteria

1. WHEN the OWNER clicks "Deactivate" on an active staff member, THE Web_Admin SHALL display a confirmation dialog with the staff member's name.
2. WHEN the OWNER confirms deactivation, THE Staff_Service SHALL set the staff member's status to inactive and invalidate all active sessions.
3. IF the OWNER attempts to deactivate themselves, THEN THE Web_Admin SHALL prevent the action and display a message stating that self-deactivation is not allowed.
4. THE Web_Admin SHALL update the staff list immediately after successful deactivation without requiring a page reload.

### Requirement 5: Dashboard Refresh and Date Filtering

**User Story:** As a business owner or platform admin, I want to refresh dashboard data and filter by date range, so that I can view current metrics and analyze trends over specific periods.

#### Acceptance Criteria

1. THE Dashboard_Component SHALL display a "Refresh" button in the toolbar area.
2. WHEN the user clicks "Refresh", THE Dashboard_Component SHALL reload all dashboard data from the server and update the display.
3. THE Dashboard_Component SHALL display a date range selector with preset options: Today, This Week, This Month, and Custom.
4. WHEN the user selects a date range, THE Dashboard_Component SHALL request data for that period from the server and update all KPI cards and recent orders.
5. WHEN the user selects "Custom", THE Dashboard_Component SHALL display date picker inputs for start and end dates.
6. THE Dashboard_Component SHALL retain the selected date range across refreshes within the same session.

### Requirement 6: Dashboard Drill-Down Navigation

**User Story:** As a business owner, I want to click on dashboard stats to navigate to detailed views, so that I can quickly investigate specific metrics.

#### Acceptance Criteria

1. WHEN the user clicks the "POS Orders" stat card on the Business Dashboard, THE Web_Admin SHALL navigate to the Orders page.
2. WHEN the user clicks the "Staff / Menu" stat card on the Business Dashboard, THE Web_Admin SHALL navigate to the Staff page.
3. WHEN the user clicks a "Recent Orders" table row on the Business Dashboard, THE Web_Admin SHALL open the order detail modal for that order.
4. WHEN the KBOOK_ADMIN clicks a business count stat on the Platform Dashboard, THE Web_Admin SHALL navigate to the Businesses page.

### Requirement 7: Orders Date Filter

**User Story:** As a restaurant owner, I want to filter orders by date range, so that I can review transaction history for specific time periods.

#### Acceptance Criteria

1. THE Orders_Component SHALL display a date range filter with preset options: Today, This Week, This Month, and Custom.
2. WHEN the user selects a preset date range, THE Orders_Component SHALL filter the displayed orders to those created within that date range.
3. WHEN the user selects "Custom", THE Orders_Component SHALL display date picker inputs for start and end dates.
4. WHEN date filtering is active, THE Orders_Component SHALL display the active date range as a label near the filter controls.
5. THE Orders_Component SHALL combine the date filter with existing search, status, and source filters.

### Requirement 8: Order Detail Modal

**User Story:** As a restaurant owner, I want to view detailed order information including line items, so that I can review order composition without leaving the orders page.

#### Acceptance Criteria

1. WHEN the user clicks an order row in the orders table, THE Orders_Component SHALL open a modal displaying full order details.
2. THE order detail modal SHALL display: order code, customer name, contact, order status, payment method, payment status, creation date, and total amount.
3. THE order detail modal SHALL display all line items with item name, quantity, unit price, and line total.
4. THE order detail modal SHALL display a "Close" button that dismisses the modal.
5. WHILE the order detail modal is open, THE Web_Admin SHALL prevent scrolling of the background page.

### Requirement 9: Orders CSV Export

**User Story:** As a restaurant owner, I want to export filtered orders as a CSV file, so that I can analyze order data in spreadsheet tools.

#### Acceptance Criteria

1. THE Orders_Component SHALL display an "Export CSV" button in the toolbar area.
2. WHEN the user clicks "Export CSV", THE Orders_Component SHALL generate a CSV file containing all orders matching the current filter criteria.
3. THE CSV file SHALL include columns: Order Code, Source, Customer Name, Customer Contact, Order Status, Payment Method, Payment Status, Total Amount, Refund Amount, and Created Date.
4. THE CSV file SHALL use the filename format "orders_YYYY-MM-DD.csv" where the date is the export date.
5. IF no orders match the current filters, THEN THE Web_Admin SHALL disable the "Export CSV" button and display a tooltip indicating no data to export.

### Requirement 10: Terminal Reactivation

**User Story:** As a restaurant owner, I want to reactivate deactivated terminals from the web admin, so that I can restore device access without creating a new terminal.

#### Acceptance Criteria

1. WHEN a terminal has status "DEACTIVATED", THE Web_Admin SHALL display a "Reactivate" button in the terminal's action column.
2. WHEN the user clicks "Reactivate", THE Web_Admin SHALL display a confirmation dialog stating the terminal will become active again.
3. WHEN the user confirms reactivation, THE Terminal_Service SHALL set the terminal status to ACTIVE and increment the credential version.
4. IF the restaurant already has 5 active terminals, THEN THE Terminal_Service SHALL return an error indicating the terminal limit is reached.
5. WHEN reactivation succeeds, THE Web_Admin SHALL update the terminal list immediately and display a success notification.

### Requirement 11: Menu Item Availability Toggle

**User Story:** As a restaurant owner, I want to toggle menu item availability from the web admin, so that I can quickly mark items as unavailable during stockouts.

#### Acceptance Criteria

1. THE Web_Admin SHALL display a toggle switch in each menu item row to control availability status.
2. WHEN the user toggles availability, THE Menu_Service SHALL update the item's available field and persist the change.
3. WHEN availability is toggled to unavailable, THE Web_Admin SHALL display the item with a visual indicator (chip styled as "danger").
4. THE Web_Admin SHALL update the menu item display immediately after a successful toggle without requiring a page reload.

### Requirement 12: Menu Item CRUD

**User Story:** As a restaurant owner, I want to create, edit, and delete menu items from the web admin, so that I can manage the menu catalog without the Android app.

#### Acceptance Criteria

1. WHEN the OWNER clicks "Add Item", THE Web_Admin SHALL display a form requesting: name, category, food type (veg/non-veg), base price, and optional description.
2. WHEN the OWNER submits a valid menu item form, THE Menu_Service SHALL create the item and return it with a generated ID.
3. WHEN the OWNER clicks "Edit" on a menu item row, THE Web_Admin SHALL display a pre-filled edit form.
4. WHEN the OWNER submits a valid edit form, THE Menu_Service SHALL update the item's fields.
5. WHEN the OWNER clicks "Delete" on a menu item, THE Web_Admin SHALL display a confirmation dialog before proceeding.
6. WHEN deletion is confirmed, THE Menu_Service SHALL soft-delete the item by marking it as unavailable and hidden.
7. THE Menu_Service SHALL validate that item name is not empty and base price is greater than zero.

### Requirement 13: Forgot Password Flow

**User Story:** As a web admin user, I want to reset my password from the login page, so that I can regain access to my account without needing the Android app.

#### Acceptance Criteria

1. THE Web_Admin login page SHALL display a "Forgot Password?" link below the password field.
2. WHEN the user clicks "Forgot Password?", THE Web_Admin SHALL display a form requesting the user's registered phone number.
3. WHEN the user submits a valid phone number, THE Auth_Service SHALL send an OTP to the registered WhatsApp number.
4. THE Web_Admin SHALL display an OTP entry form with a 4-digit input field after successful OTP dispatch.
5. WHEN the user enters a valid OTP, THE Web_Admin SHALL display a new password form requiring password and confirm-password fields.
6. WHEN the user submits matching passwords of at least 6 characters, THE Auth_Service SHALL update the password and return a success response.
7. IF the OTP is invalid or expired, THEN THE Auth_Service SHALL return an error and THE Web_Admin SHALL display "Invalid or expired OTP".

### Requirement 14: Business Suspend and Activate

**User Story:** As a platform administrator, I want to suspend and activate businesses from the web admin, so that I can manage restaurant lifecycle from the admin panel.

#### Acceptance Criteria

1. WHEN the KBOOK_ADMIN views the businesses table, THE Web_Admin SHALL display a "Suspend" button for active businesses and an "Activate" button for suspended businesses.
2. WHEN the KBOOK_ADMIN clicks "Suspend" on a business, THE Web_Admin SHALL display a confirmation dialog with the business name.
3. WHEN suspension is confirmed, THE Business_Service SHALL set the business status to suspended and prevent all POS operations for that business.
4. WHEN the KBOOK_ADMIN clicks "Activate" on a suspended business, THE Business_Service SHALL restore the business to active status.
5. THE Web_Admin SHALL update the businesses table immediately after a successful status change without requiring a page reload.
6. IF a suspended business's staff attempts to log in, THEN THE Auth_Service SHALL return an error indicating the business is suspended.
