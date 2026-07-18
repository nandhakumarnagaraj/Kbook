# Implementation Plan: Web Admin Full Capability

## Overview

This plan transforms the KhanaBook Web Admin from a read-only monitoring panel into a full management console. Implementation spans the Spring Boot 3.5 backend (Java 17) and Angular 18 frontend (TypeScript), covering: role-based access enforcement, staff CRUD, dashboard interactivity, order enhancements, terminal reactivation, menu management, forgot password flow, and business lifecycle management.

The approach builds incrementally — security infrastructure first, then write services, then frontend features — ensuring each step integrates with previous work.

## Tasks

- [x] 1. Backend security infrastructure and role enforcement
  - [x] 1.1 Create `@RequireRole` annotation and AOP interceptor
    - Create `RequireRole.java` annotation in `security/` with `UserRole[] value()`
    - Create `RequireRoleAspect.java` AOP `@Around` advice that reads `TenantContext.getCurrentRole()` and throws `AccessDeniedException` if role not in allowed set
    - Create `AccessDeniedException.java` and map it to HTTP 403 in the global exception handler
    - Extend `TenantContext` to store and expose `currentRole` and `currentUserId` alongside `currentTenant`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.6_

  - [x] 1.2 Write property tests for role-based access control (jqwik)
    - **Property 1: Role-Based Access Control**
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.6**
    - Generate random (role, operation) pairs and verify access granted iff role is in allowed set, else 403

  - [x] 1.3 Add `is_suspended` column to `restaurantprofiles` via Flyway migration
    - Create Flyway migration: `ALTER TABLE restaurantprofiles ADD COLUMN is_suspended BOOLEAN NOT NULL DEFAULT FALSE`
    - Add index: `CREATE INDEX idx_restaurantprofiles_suspended ON restaurantprofiles (restaurant_id, is_suspended)`
    - Update `RestaurantProfile` entity to include `isSuspended` field
    - _Requirements: 14.3, 14.4, 14.6_

- [x] 2. Backend write services — Staff CRUD
  - [x] 2.1 Create `BusinessWriteService` with staff operations
    - Create `BusinessWriteService.java` in `webadmin/service/`
    - Implement `createStaff(Long restaurantId, CreateStaffRequest req)` — validate phone (10 digits), role, check duplicates, generate temp password, create user
    - Implement `updateStaff(Long restaurantId, Long userId, UpdateStaffRequest req)` — update fields, if role changed invalidate sessions via `tokenInvalidatedAt`
    - Implement `deactivateStaff(Long restaurantId, Long userId)` — set `isActive=false`, set `tokenInvalidatedAt` to now
    - _Requirements: 2.2, 2.3, 2.4, 2.5, 2.6, 3.2, 3.3, 4.2_

  - [x] 2.2 Create staff DTOs
    - Create `CreateStaffRequest.java` record (name, phone, role, email)
    - Create `StaffCreatedResponse.java` record (userId, name, phone, role, temporaryPassword)
    - Create `UpdateStaffRequest.java` record (name, phone, email, role)
    - _Requirements: 2.1, 2.2, 3.1, 3.2_

  - [x] 2.3 Add staff write endpoints to `BusinessAdminController`
    - `POST /business/staff` with `@RequireRole(OWNER)` → createStaff
    - `PUT /business/staff/{userId}` with `@RequireRole(OWNER)` → updateStaff
    - `POST /business/staff/{userId}/deactivate` with `@RequireRole(OWNER)` → deactivateStaff
    - _Requirements: 1.1, 2.2, 3.2, 4.2_

  - [x] 2.4 Write property tests for staff creation and validation (jqwik)
    - **Property 2: Staff Creation Produces Valid User**
    - **Property 3: Staff Input Validation**
    - **Property 4: Duplicate Phone Rejection**
    - **Validates: Requirements 2.2, 2.4, 2.5, 2.6**

  - [x] 2.5 Write property tests for staff edit and deactivation (jqwik)
    - **Property 5: Staff Edit Preserves Integrity**
    - **Property 6: Staff Deactivation Invalidates Sessions**
    - **Validates: Requirements 3.2, 4.2**

- [x] 3. Backend write services — Menu CRUD and Terminal Reactivation
  - [x] 3.1 Add menu CRUD operations to `BusinessWriteService`
    - Implement `createMenuItem(Long restaurantId, CreateMenuItemRequest req)` — validate name non-empty, basePrice > 0, create item
    - Implement `updateMenuItem(Long restaurantId, Long menuItemId, UpdateMenuItemRequest req)` — validate and update
    - Implement `deleteMenuItem(Long restaurantId, Long menuItemId)` — soft delete: set `isDeleted=true`, `isAvailable=false`
    - Implement `toggleMenuItemAvailability(Long restaurantId, Long menuItemId)` — flip `isAvailable` boolean
    - _Requirements: 11.2, 12.2, 12.4, 12.6, 12.7_

  - [x] 3.2 Create menu DTOs and add menu write endpoints
    - Create `CreateMenuItemRequest.java` record (name, categoryId, foodType, basePrice, description)
    - Create `UpdateMenuItemRequest.java` record (name, categoryId, foodType, basePrice, description)
    - Add `POST /business/menu` with `@RequireRole(OWNER)` → createMenuItem
    - Add `PUT /business/menu/{menuItemId}` with `@RequireRole(OWNER)` → updateMenuItem
    - Add `DELETE /business/menu/{menuItemId}` with `@RequireRole(OWNER)` → deleteMenuItem
    - Add `POST /business/menu/{menuItemId}/toggle-availability` with `@RequireRole(OWNER)` → toggleMenuItemAvailability
    - _Requirements: 1.4, 11.2, 12.2, 12.4, 12.6_

  - [x] 3.3 Add terminal reactivation to `BusinessWriteService` and controller
    - Implement `reactivateTerminal(Long restaurantId, Long terminalId)` — check active count < 5, set status ACTIVE, increment credentialVersion
    - Add `POST /business/terminals/{terminalId}/reactivate` with `@RequireRole({OWNER, SHOP_ADMIN})` → reactivateTerminal
    - _Requirements: 1.3, 10.3, 10.4_

  - [x] 3.4 Write property tests for menu and terminal operations (jqwik)
    - **Property 11: Terminal Reactivation State Transition**
    - **Property 12: Menu Item Availability Toggle**
    - **Property 13: Menu Item Creation**
    - **Property 14: Menu Item Soft Delete**
    - **Property 15: Menu Item Validation**
    - **Validates: Requirements 10.3, 11.2, 12.2, 12.6, 12.7**

- [x] 4. Checkpoint - Ensure all backend write service tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Backend — Admin write service and business lifecycle
  - [x] 5.1 Create `AdminWriteService` with suspend/activate operations
    - Create `AdminWriteService.java` in `webadmin/service/`
    - Implement `suspendBusiness(Long restaurantId)` — set `isSuspended=true` on RestaurantProfile
    - Implement `activateBusiness(Long restaurantId)` — set `isSuspended=false` on RestaurantProfile
    - _Requirements: 14.3, 14.4_

  - [x] 5.2 Add suspend/activate endpoints to `AdminDashboardController`
    - `POST /admin/businesses/{restaurantId}/suspend` with `@RequireRole(KBOOK_ADMIN)` → suspendBusiness
    - `POST /admin/businesses/{restaurantId}/activate` with `@RequireRole(KBOOK_ADMIN)` → activateBusiness
    - _Requirements: 1.2, 14.3, 14.4_

  - [x] 5.3 Add suspended-business login check to AuthService
    - During login, after credential validation, check `restaurantProfile.isSuspended`
    - If suspended, return 403 with error `BUSINESS_SUSPENDED`
    - _Requirements: 14.6_

  - [x] 5.4 Write property tests for business lifecycle (jqwik)
    - **Property 17: Business Suspend/Activate Round-Trip**
    - **Property 18: Suspended Business Blocks Login**
    - **Validates: Requirements 14.3, 14.4, 14.6**

- [x] 6. Backend — Forgot password flow and order detail
  - [x] 6.1 Create forgot password endpoints in AuthController
    - Create `RequestOtpRequest.java`, `VerifyOtpRequest.java`, `VerifyOtpResponse.java`, `ResetPasswordRequest.java` DTOs
    - `POST /auth/forgot-password/request-otp` — validate phone, send OTP via existing WhatsApp infrastructure
    - `POST /auth/forgot-password/verify-otp` — validate OTP, return temp token
    - `POST /auth/forgot-password/reset-password` — validate temp token, check password >= 6 chars, update password
    - _Requirements: 13.3, 13.4, 13.5, 13.6, 13.7_

  - [x] 6.2 Write property test for password reset validation (jqwik)
    - **Property 16: Password Reset Validation**
    - **Validates: Requirements 13.6**

  - [x] 6.3 Add order detail endpoint to BusinessAdminController
    - Add `getOrderDetail(Long restaurantId, Long billId)` to `BusinessReadService` — fetch bill + BillItems
    - Create `OrderDetailResponse.java` and `OrderLineItemResponse.java` DTOs
    - Add `GET /business/orders/{billId}` endpoint
    - _Requirements: 8.2, 8.3_

  - [x] 6.4 Add date range parameters to dashboard and orders endpoints
    - Overload `getDashboard(Long restaurantId, LocalDate from, LocalDate to)` for KPI recalculation
    - Add optional `from` and `to` query params to `GET /business/dashboard`
    - _Requirements: 5.4_

- [x] 7. Checkpoint - Ensure all backend tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Frontend — API service extensions and shared components
  - [x] 8.1 Extend `BusinessApiService` with write methods
    - Add `createStaff`, `updateStaff`, `deactivateStaff` methods
    - Add `createMenuItem`, `updateMenuItem`, `deleteMenuItem`, `toggleMenuItemAvailability` methods
    - Add `reactivateTerminal` method
    - Add `getDashboard(from?, to?)`, `getOrderDetail(billId)` methods with optional date params
    - _Requirements: 2.2, 3.2, 4.2, 5.4, 8.2, 10.3, 11.2, 12.2_

  - [x] 8.2 Extend `AdminApiService` with suspend/activate methods
    - Add `suspendBusiness(restaurantId)` and `activateBusiness(restaurantId)` methods
    - _Requirements: 14.3, 14.4_

  - [x] 8.3 Extend `AuthService` with forgot password methods
    - Add `requestPasswordOtp(phone)`, `verifyPasswordOtp(phone, otp)`, `resetPassword(tempToken, newPassword)` methods
    - _Requirements: 13.3, 13.5, 13.6_

  - [x] 8.4 Create shared UI components
    - Create `ConfirmDialogComponent` — reusable confirmation modal with title, message, confirm/cancel buttons
    - Create `DateRangeSelectorComponent` — preset buttons (Today, This Week, This Month, Custom) + date pickers
    - Create `OrderDetailModalComponent` — full order view with line items table
    - _Requirements: 4.1, 5.3, 5.5, 7.1, 7.3, 8.1, 8.2, 8.3, 8.4, 8.5_

- [x] 9. Frontend — Staff page CRUD
  - [x] 9.1 Implement staff creation form and flow in `StaffPageComponent`
    - Add "Add Staff" button visible only to OWNER role
    - Create staff form modal with fields: name, phone (10-digit validation), role selector, optional email
    - On submit, call `BusinessApiService.createStaff()`, display temp password on success
    - Handle duplicate phone error display
    - _Requirements: 1.1, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [x] 9.2 Implement staff edit and deactivation in `StaffPageComponent`
    - Add "Edit" action button per staff row → open pre-filled edit form modal
    - Prevent self-role-change with disabled button + tooltip
    - Add "Deactivate" action button → confirmation dialog → call deactivateStaff
    - Prevent self-deactivation with disabled button + tooltip
    - Update staff list reactively after successful operations
    - _Requirements: 1.1, 3.1, 3.2, 3.3, 3.4, 4.1, 4.2, 4.3, 4.4_

- [x] 10. Frontend — Dashboard interactivity
  - [x] 10.1 Add refresh and date filtering to `BusinessDashboardPageComponent`
    - Add "Refresh" button in toolbar that reloads all dashboard data
    - Integrate `DateRangeSelectorComponent` with presets: Today, This Week, This Month, Custom
    - On date range change, call `getDashboard(from, to)` for server-side KPI recalculation
    - Retain selected date range in session (signal state)
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

  - [x] 10.2 Add drill-down navigation to dashboard stat cards
    - Make "POS Orders" stat card clickable → navigate to `/business/orders`
    - Make "Staff / Menu" stat card clickable → navigate to `/business/staff`
    - Make recent orders table rows clickable → open `OrderDetailModalComponent`
    - For KBOOK_ADMIN platform dashboard: business count stat → navigate to `/admin/businesses`
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

- [x] 11. Frontend — Orders enhancements
  - [x] 11.1 Add date filtering and order detail modal to `OrdersPageComponent`
    - Integrate `DateRangeSelectorComponent` for client-side date filtering
    - Display active date range label near filter controls
    - Combine date filter with existing search/status/source filters (AND logic)
    - Make order rows clickable → open `OrderDetailModalComponent` with full line items
    - Prevent background scrolling while modal is open
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 8.1, 8.2, 8.3, 8.4, 8.5_

  - [x] 11.2 Implement CSV export in `OrdersPageComponent`
    - Add "Export CSV" button in toolbar
    - Generate CSV with columns: Order Code, Source, Customer Name, Customer Contact, Order Status, Payment Method, Payment Status, Total Amount, Refund Amount, Created Date
    - Use filename format `orders_YYYY-MM-DD.csv`
    - Disable button with tooltip when no orders match filters
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

  - [x] 11.3 Write property tests for date filtering and CSV export (fast-check)
    - **Property 7: Date Range Filtering**
    - **Property 8: Filter Composition (AND Logic)**
    - **Property 10: CSV Export Correctness**
    - **Validates: Requirements 5.4, 7.2, 7.5, 9.2, 9.3**

- [x] 12. Frontend — Menu management and terminal reactivation
  - [x] 12.1 Implement menu CRUD in `MenuPageComponent`
    - Add "Add Item" button → form modal with fields: name, category, food type (veg/non-veg), base price, description
    - Add "Edit" action per menu item row → pre-filled edit form modal
    - Add "Delete" action per menu item → confirmation dialog → soft delete
    - Add availability toggle switch per item row with optimistic UI update (revert on error)
    - Display unavailable items with danger-styled chip
    - _Requirements: 1.4, 11.1, 11.2, 11.3, 11.4, 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7_

  - [x] 12.2 Implement terminal reactivation in `TerminalsPageComponent`
    - Show "Reactivate" button for terminals with status DEACTIVATED
    - On click → confirmation dialog → call `reactivateTerminal`
    - Handle terminal limit error (5 active max) with error toast
    - Update terminal list and show success notification
    - _Requirements: 1.3, 10.1, 10.2, 10.3, 10.4, 10.5_

  - [x] 12.3 Write property test for order detail completeness (fast-check)
    - **Property 9: Order Detail Completeness**
    - **Validates: Requirements 8.2, 8.3**

- [x] 13. Checkpoint - Ensure all frontend tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 14. Frontend — Forgot password and business lifecycle
  - [x] 14.1 Implement forgot password flow in `LoginPageComponent`
    - Add "Forgot Password?" link below password field
    - Step 1: Phone number input form → call `requestPasswordOtp`
    - Step 2: 4-digit OTP entry form → call `verifyPasswordOtp`
    - Step 3: New password + confirm password form (min 6 chars, must match) → call `resetPassword`
    - Handle invalid/expired OTP error display
    - On success, navigate back to login with success message
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7_

  - [x] 14.2 Implement business suspend/activate in `BusinessesPageComponent`
    - Show "Suspend" button for active businesses, "Activate" for suspended ones
    - On suspend click → confirmation dialog with business name → call `suspendBusiness`
    - On activate click → call `activateBusiness`
    - Update businesses table immediately after status change
    - _Requirements: 1.2, 14.1, 14.2, 14.3, 14.4, 14.5_

  - [x] 14.3 Write property test for password validation (fast-check)
    - **Property 16: Password Reset Validation (frontend)**
    - **Validates: Requirements 13.6**

- [x] 15. Frontend — Client-side role enforcement for write actions
  - [x] 15.1 Add role-based UI guards for write operations
    - Hide/disable staff CRUD buttons for non-OWNER users
    - Hide/disable menu write buttons for non-OWNER users
    - Hide/disable business suspend/activate for non-KBOOK_ADMIN users
    - Show terminal reactivation only for OWNER and SHOP_ADMIN
    - Display access-denied toast if unauthorized action attempted via URL manipulation
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [x] 16. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties (jqwik for Java backend, fast-check for TypeScript frontend)
- Unit tests validate specific examples and edge cases
- Backend tasks (1-7) should be completed before frontend tasks (8-15) to ensure APIs are available
- Shared UI components (task 8.4) are used across multiple page implementations
- The `@RequireRole` interceptor (task 1.1) is foundational — all subsequent write endpoints depend on it

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.3", "2.2"] },
    { "id": 1, "tasks": ["1.2", "2.1", "5.1"] },
    { "id": 2, "tasks": ["2.3", "2.4", "2.5", "3.1", "3.3", "5.2", "5.3"] },
    { "id": 3, "tasks": ["3.2", "3.4", "5.4", "6.1", "6.3", "6.4"] },
    { "id": 4, "tasks": ["6.2", "8.1", "8.2", "8.3"] },
    { "id": 5, "tasks": ["8.4", "9.1"] },
    { "id": 6, "tasks": ["9.2", "10.1", "10.2", "11.1"] },
    { "id": 7, "tasks": ["11.2", "11.3", "12.1", "12.2"] },
    { "id": 8, "tasks": ["12.3", "14.1", "14.2", "15.1"] },
    { "id": 9, "tasks": ["14.3"] }
  ]
}
```
