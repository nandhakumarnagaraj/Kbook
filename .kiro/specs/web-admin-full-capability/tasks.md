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

## OWNER UI Refinement — Seven Implementation Slices

- [ ] 17. PR 1 — Compact global page headers
  - [ ] 17.1 Replace oversized OWNER page hero cards with the shared compact title, subtitle, metadata, and action-slot pattern
    - Move recurring Refresh actions into the same header location
    - Convert decorative chips to plain metadata and retain chips only for real filters
    - Preserve responsive wrapping and existing page states
    - Validate with TypeScript and a development build
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 21.5, 21.7_

- [ ] 18. PR 2 — Reports KPI hierarchy
  - [ ] 18.1 Recompose the Reports KPI area around two primary financial cards and one compact secondary strip
    - Move date presets into an accessible segmented control in the KPI header
    - Replace the persistent report explanation with an accessible information control
    - Preserve custom-date behavior and responsive stacking
    - Validate keyboard operation, TypeScript, and a development build
    - _Requirements: 16.1, 16.2, 16.3, 16.4, 16.5, 16.6, 21.1, 21.7_

- [ ] 19. PR 3 — Dense Menu table and filters
  - [ ] 19.1 Refine Menu filtering, table columns, statuses, and row actions
    - Add the computed Needs attention filter and remove repeated missing-description copy
    - Conditionally collapse zero-only variants and show positive counts beside item names
    - Render switch, edit, and ghost-danger delete actions inline with accessible labels
    - Apply semantic warning/success colors and right-aligned tabular numbers
    - Validate responsive table behavior, TypeScript, and a development build
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5, 17.6, 17.7, 21.1, 21.2, 21.3_

- [ ] 20. PR 4 — Scannable Devices table
  - [ ] 20.1 Consolidate device state and compact terminal actions
    - Remove the redundant Active column and normalize the Status display
    - Truncate identifiers to eight characters with full tooltip and copy behavior
    - Render Rename, Recover, and Reactivate inline with accessible labels
    - Add zero-pending explanatory copy and plain metadata
    - Validate copy feedback, responsive table behavior, TypeScript, and a development build
    - _Requirements: 18.1, 18.2, 18.3, 18.4, 18.5, 18.6, 21.1, 21.2, 21.3_

- [ ] 21. PR 5 — Zomato and Swiggy integration cards
  - [ ] 21.1 Rebuild marketplace settings as equivalent provider cards
    - Keep the OWNER marketplace view limited to Zomato and Swiggy; do not add Easebuzz
    - Protect API key and secret fields with labelled show/hide controls
    - Normalize webhook URLs to exactly one `/api/v1` segment and add copy feedback
    - Track loaded values so Save is enabled only while valid changes are dirty
    - Validate both URL base forms, responsive cards, TypeScript, and a development build
    - _Requirements: 19.1, 19.2, 19.3, 19.4, 19.5, 19.6, 19.7, 21.1, 21.7_

- [ ] 22. PR 6 — Sidebar visual refinement
  - [ ] 22.1 Rebalance branding, active navigation, and Logout treatment
    - Reduce the logo tile to 40px
    - Add the primary left accent to the active item
    - Add a divider and muted default treatment above Logout
    - Preserve responsive drawer behavior, role metadata, and ARIA state
    - Validate desktop/mobile navigation, TypeScript, and a development build
    - _Requirements: 20.1, 20.2, 20.3, 20.4, 20.5, 21.7_

- [ ] 23. PR 7 — Accessibility, consistency, and design-system documentation
  - [ ] 23.1 Complete the cross-page accessibility and consistency pass
    - Standardize button radius, effective target size, focus state, tooltip, table hover, status semantics, and tabular numeric alignment
    - Recheck loading, error, empty, and success states on all affected pages
    - Update `web-admin/DESIGN_SYSTEM.md` with header, KPI, row-action, status-color, and icon-control patterns
    - Run `npx tsc --noEmit` and `ng build --configuration development`
    - _Requirements: 21.1, 21.2, 21.3, 21.4, 21.5, 21.6, 21.7_

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties (jqwik for Java backend, fast-check for TypeScript frontend)
- Unit tests validate specific examples and edge cases
- Backend tasks (1-7) should be completed before frontend tasks (8-15) to ensure APIs are available
- Shared UI components (task 8.4) are used across multiple page implementations
- The `@RequireRole` interceptor (task 1.1) is foundational — all subsequent write endpoints depend on it
- Tasks 17-23 are seven independently reviewable OWNER UI refinement slices; complete them in order because task 23 consolidates shared guidance
- No new dependencies are permitted for tasks 17-23

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
    { "id": 9, "tasks": ["14.3"] },
    { "id": 10, "tasks": ["17.1"] },
    { "id": 11, "tasks": ["18.1"] },
    { "id": 12, "tasks": ["19.1"] },
    { "id": 13, "tasks": ["20.1"] },
    { "id": 14, "tasks": ["21.1"] },
    { "id": 15, "tasks": ["22.1"] },
    { "id": 16, "tasks": ["23.1"] }
  ]
}
```
