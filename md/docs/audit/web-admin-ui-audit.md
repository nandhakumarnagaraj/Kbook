# Khanabook Web-Admin UI & Accessibility Audit Report

**Audit Date:** October 25, 2026  
**Environment:** Linux VPS (Ubuntu 24.04), Chrome Headless (v147.0.7727.55), Node.js v20.20.1  
**Backend:** Spring Boot SaaS Server (`127.0.0.1:8081` healthy, proxied via dev proxy)  
**Web App:** Angular 18 (`apps/web-admin`), served on `http://localhost:4200`  
**Auditor:** Automated Agentic Browser Audit Tooling (Puppeteer CDP)  

---

## Executive Summary

A comprehensive rendered browser audit, accessibility evaluation, state check, and source code audit was conducted on the Khanabook web-admin application across all specified administrative and merchant routes. Every route was rendered and evaluated at **1024px (desktop/tablet)** and **390px (mobile)** at 100% zoom.

### Key Outcomes:
1. **Layout & Responsiveness:** All routes passed document horizontal overflow tests (`docScrollWidth <= winWidth`). No horizontal page scroll or unexpected viewport clipping was detected. The mobile card list pattern (`.mobile-data-list`) correctly activates below 768px, replacing tabular data (`.data-table`) across all list screens.
2. **Accessibility:** 
   - The Skip to Main Content link is functional and becomes visible (`translateY(0)`) on Tab focus.
   - Visible focus indicators are active across buttons, links, and input elements.
   - Modal accessibility (WCAG 2.1 AA): All dialog modals now implement strict focus trapping (Tab cycle within dialog), initial focus management, Escape-key dismissal handlers, and focus restoration to the triggering element via `FocusTrapDirective`.
3. **State Management:**
   - Loading skeletons and shimmers are present across operational and list routes.
   - A verified bug was uncovered on `/business/orders` (Order History tab) where the empty state (`.empty-orders-view`) rendered concurrently underneath the error state banner when an API request failed. This was resolved with a verified in-scope fix.
4. **Source Audit:**
   - Confirmed palette freeze violations in `login-page.component.ts` (unapproved orange literals `#F58213`, `#C35500`, `#A94400`).
   - Confirmed hard-coded `#FFFFFF` outline values in `sidebar-layout.component.ts`.
   - Identified media query breakpoint divergence (767px in `styles.css` vs 768px in `bento.css` and multiple component stylesheets).

---

## Part 1: Rendered Route-by-Route Review

Every route was audited at two canonical viewports:
- **Desktop / Tablet:** 1024px × 768px
- **Mobile:** 390px × 844px (iPhone 12/13/14 scale)

---

### 1. Platform Admin Routes (Role: `KBOOK_ADMIN`)

#### Route: `/admin/dashboard`
- **Target Role:** `KBOOK_ADMIN`
- **Rendered URL:** `http://localhost:4200/admin/dashboard`
- **Screenshots:**
  - 1024px: `docs/audit/screenshots/_admin_dashboard_desktop_1024.png`
  - 390px: `docs/audit/screenshots/_admin_dashboard_mobile_390.png`
- **Measured Metrics:**
  - 1024px: `docScrollWidth = 1024px`, `hasOverflow = false`, clipped text = 0
  - 390px: `docScrollWidth = 390px`, `hasOverflow = false`, clipped text = 0
- **Layout Behavior:**
  - 1024px: Bento grid cards render cleanly; KPI header items stack without clipping.
  - 390px: Dashboard stacks into single-column cards. Navigation transitions into the mobile topbar with accessible hamburger toggle (`aria-label="Open navigation menu"`).
- **Findings:** None (Pass).

#### Route: `/admin/businesses`
- **Target Role:** `KBOOK_ADMIN`
- **Rendered URL:** `http://localhost:4200/admin/businesses`
- **Screenshots:**
  - 1024px: `docs/audit/screenshots/_admin_businesses_desktop_1024.png`
  - 390px: `docs/audit/screenshots/_admin_businesses_mobile_390.png`
- **Measured Metrics:**
  - 1024px: `docScrollWidth = 1024px`, `hasOverflow = false`, table rendered (`display: table`), mobile list hidden (`display: none`).
  - 390px: `docScrollWidth = 390px`, `hasOverflow = false`, table hidden (`display: none`), mobile card list rendered (`display: grid`).
- **Layout Behavior:**
  - 1024px: `.data-table.businesses-table` renders within `.table-wrap`. Columns for Business, Owner, Orders, Menu, Staff, Joined, and Status fit cleanly.
  - 390px: Mobile card list (`.mobile-data-card`) provides structured cards displaying shop name, owner, and inline statistics without horizontal scrolling.
- **Findings:**
  - **R-01 (Medium):** "Add Restaurant" modal does not trap keyboard focus or close on Escape key. (See Accessibility section).

#### Route: `/admin/feature-flags`
- **Target Role:** `KBOOK_ADMIN`
- **Rendered URL:** `http://localhost:4200/admin/feature-flags`
- **Screenshots:**
  - 1024px: `docs/audit/screenshots/_admin_feature-flags_desktop_1024.png`
  - 390px: `docs/audit/screenshots/_admin_feature-flags_mobile_390.png`
- **Measured Metrics:**
  - 1024px: `docScrollWidth = 1024px`, `hasOverflow = false`
  - 390px: `docScrollWidth = 390px`, `hasOverflow = false`
- **Layout Behavior:**
  - Feature flag toggle rows align cleanly. Empty state renders gracefully when no overrides are configured.
- **Findings:** None (Pass).

---

### 2. Merchant Owner Routes (Role: `OWNER`)

#### Route: `/business/dashboard`
- **Target Role:** `OWNER`
- **Rendered URL:** `http://localhost:4200/business/dashboard`
- **Screenshots:**
  - 1024px: `docs/audit/screenshots/_business_dashboard_desktop_1024.png`
  - 390px: `docs/audit/screenshots/_business_dashboard_mobile_390.png`
- **Measured Metrics:**
  - 1024px: `docScrollWidth = 1024px`, `hasOverflow = false`, clipped text = 0
  - 390px: `docScrollWidth = 390px`, `hasOverflow = false`, clipped text = 0
- **Layout Behavior:**
  - 1024px: Hourly revenue curve spans full width (`.dashboard-chart-row`) above the split operational intelligence grid (`.dashboard-operational-grid`). "View as table" accessible modal toggle works.
  - 390px: KPI cards, hourly velocity bars, live floor cards, and inventory low stock indicators stack vertically with adequate touch targets (>= 44px).
- **Findings:** None (Pass).

#### Route: `/business/reports`
- **Target Role:** `OWNER`
- **Rendered URL:** `http://localhost:4200/business/reports`
- **Screenshots:**
  - 1024px: `docs/audit/screenshots/_business_reports_desktop_1024.png`
  - 390px: `docs/audit/screenshots/_business_reports_mobile_390.png`
- **Measured Metrics:**
  - 1024px: `docScrollWidth = 1024px`, `hasOverflow = false`
  - 390px: `docScrollWidth = 390px`, `hasOverflow = false`
- **Layout Behavior:**
  - Date range selector fits cleanly in the header. Analytics cards display turnover, tax breakdowns, and settlement statistics without overflow.
- **Findings:** None (Pass).

#### Route: `/business/daily-closing`
- **Target Role:** `OWNER`
- **Rendered URL:** `http://localhost:4200/business/daily-closing`
- **Screenshots:**
  - 1024px: `docs/audit/screenshots/_business_daily-closing_desktop_1024.png`
  - 390px: `docs/audit/screenshots/_business_daily-closing_mobile_390.png`
- **Measured Metrics:**
  - 1024px: `docScrollWidth = 1024px`, `hasOverflow = false`
  - 390px: `docScrollWidth = 390px`, `hasOverflow = false`
- **Layout Behavior:**
  - Summary metrics display expected cash vs recorded settlements. Payment breakdown chips remain legible.
- **Findings:** None (Pass).

#### Route: `/business/orders`
- **Target Role:** `OWNER`
- **Rendered URL:** `http://localhost:4200/business/orders`
- **Screenshots:**
  - 1024px: `docs/audit/screenshots/_business_orders_desktop_1024.png`
  - 390px: `docs/audit/screenshots/_business_orders_mobile_390.png`
- **Measured Metrics:**
  - 1024px: `docScrollWidth = 1024px`, `hasOverflow = false`
  - 390px: `docScrollWidth = 390px`, `hasOverflow = false`
- **Layout Behavior:**
  - Tab pills toggle between "Live orders" and "Order history".
  - Live orders tab displays live floor cards with elapsed time pills and status badges.
  - Order history tab displays paginated orders table at 1024px and `.mobile-data-list` cards at 390px.
- **Findings:**
  - **R-02 (High — VERIFIED & FIXED):** In `apps/web-admin/src/app/pages/orders/order-history-tab.component.ts`, when an API failure occurs (`ordersError`), `<app-api-state>` rendered, but `<ng-container aria-label="Order history">` also evaluated the empty template branch (`emptyOrdersTable`) and rendered the "Kitchen & Floor Counter Clear" empty state below the error banner. Fixed by adding `*ngIf="!ordersError"` to the container.

#### Route: `/business/menu`
- **Target Role:** `OWNER`
- **Rendered URL:** `http://localhost:4200/business/menu`
- **Screenshots:**
  - 1024px: `docs/audit/screenshots/_business_menu_desktop_1024.png`
  - 390px: `docs/audit/screenshots/_business_menu_mobile_390.png`
- **Measured Metrics:**
  - 1024px: `docScrollWidth = 1024px`, `hasOverflow = false`, table rendered, mobile list hidden
  - 390px: `docScrollWidth = 390px`, `hasOverflow = false`, table hidden, mobile card list rendered
- **Layout Behavior:**
  - Menu summary tiles, category filter chips, and vegetarian indicator dots render correctly.
  - Availability toggle switches are reachable by keyboard and show `:focus-visible` rings.
- **Findings:** None (Pass).

#### Route: `/business/inventory`
- **Target Role:** `OWNER`
- **Rendered URL:** `http://localhost:4200/business/inventory`
- **Screenshots:**
  - 1024px: `docs/audit/screenshots/_business_inventory_desktop_1024.png`
  - 390px: `docs/audit/screenshots/_business_inventory_mobile_390.png`
- **Measured Metrics:**
  - 1024px: `docScrollWidth = 1024px`, `hasOverflow = false`
  - 390px: `docScrollWidth = 390px`, `hasOverflow = false`
- **Layout Behavior:**
  - Tabs for Materials, Recipes, and Vendors. Low-stock badges display with distinct warning tones.
- **Findings:** None (Pass).

#### Route: `/business/staff`
- **Target Role:** `OWNER`
- **Rendered URL:** `http://localhost:4200/business/staff`
- **Screenshots:**
  - 1024px: `docs/audit/screenshots/_business_staff_desktop_1024.png`
  - 390px: `docs/audit/screenshots/_business_staff_mobile_390.png`
- **Measured Metrics:**
  - 1024px: `docScrollWidth = 1024px`, `hasOverflow = false`, table rendered, mobile list hidden
  - 390px: `docScrollWidth = 390px`, `hasOverflow = false`, table hidden, mobile card list rendered
- **Layout Behavior:**
  - Staff role badges (`OWNER`, `SHOP_STAFF`) render with distinct colors. Action buttons (Permissions, Reset PIN, Deactivate) fit without overlapping.
- **Findings:** None (Pass).

#### Route: `/business/terminals`
- **Target Role:** `OWNER`
- **Rendered URL:** `http://localhost:4200/business/terminals`
- **Screenshots:**
  - 1024px: `docs/audit/screenshots/_business_terminals_desktop_1024.png`
  - 390px: `docs/audit/screenshots/_business_terminals_mobile_390.png`
- **Measured Metrics:**
  - 1024px: `docScrollWidth = 1024px`, `hasOverflow = false`
  - 390px: `docScrollWidth = 390px`, `hasOverflow = false`
- **Layout Behavior:**
  - Hardware terminal device cards, pairing code generator, and registration request queues display properly.
- **Findings:** None (Pass).

#### Route: `/business/settings`
- **Target Role:** `OWNER`
- **Rendered URL:** `http://localhost:4200/business/settings`
- **Screenshots:**
  - 1024px: `docs/audit/screenshots/_business_settings_desktop_1024.png`
  - 390px: `docs/audit/screenshots/_business_settings_mobile_390.png`
- **Measured Metrics:**
  - 1024px: `docScrollWidth = 1024px`, `hasOverflow = false`
  - 390px: `docScrollWidth = 390px`, `hasOverflow = false`
- **Layout Behavior:**
  - Form sections for shop profile, billing configuration, and operating hours. Unsaved changes guard (`unsavedChangesGuard`) is attached.
- **Findings:** None (Pass).

---

### 3. General & Fallback Routes

#### Route: `/login`
- **Target Role:** Unauthenticated (`ANON`)
- **Rendered URL:** `http://localhost:4200/login`
- **Screenshots:**
  - 1024px: `docs/audit/screenshots/_login_desktop_1024.png`
  - 390px: `docs/audit/screenshots/_login_mobile_390.png`
- **Measured Metrics:**
  - 1024px: `docScrollWidth = 1024px`, `hasOverflow = false`
  - 390px: `docScrollWidth = 390px`, `hasOverflow = false`
- **Layout Behavior:**
  - Split auth layout collapses to single column on mobile.
- **Findings:**
  - **S-01 (High — SOURCE):** Palette tokens overridden with unapproved orange literals (`#F58213`, `#C35500`, `#A94400`). (See Source Audit).

#### Route: `/limited-access`
- **Target Role:** `SHOP_STAFF` (staff role blocked from web admin)
- **Rendered URL:** `http://localhost:4200/limited-access`
- **Screenshots:**
  - 1024px: `docs/audit/screenshots/_limited-access_desktop_1024.png`
  - 390px: `docs/audit/screenshots/_limited-access_mobile_390.png`
- **Measured Metrics:**
  - 1024px: `docScrollWidth = 1024px`, `hasOverflow = false`
  - 390px: `docScrollWidth = 390px`, `hasOverflow = false`
- **Layout Behavior:**
  - Centered card explaining POS-only access for staff accounts with a Sign Out action. No privileged routes or sidebar links leaked.
- **Findings:** None (Pass).

#### Route: `/404` and Unknown Path (`/this-path-does-not-exist-xyz`)
- **Target Role:** Authenticated user
- **Rendered URL:** `http://localhost:4200/404` (unknown paths successfully catch-all redirect to `NotFoundPageComponent`)
- **Screenshots:**
  - 1024px: `docs/audit/screenshots/_404_desktop_1024.png`
  - 390px: `docs/audit/screenshots__this-path-does-not-exist-xyz_mobile_390.png`
- **Measured Metrics:**
  - 1024px: `docScrollWidth = 1024px`, `hasOverflow = false`
  - 390px: `docScrollWidth = 390px`, `hasOverflow = false`
- **Layout Behavior:**
  - Clean 404 message with "Back to safety" navigation button.
- **Findings:** None (Pass).

---

## Part 2: Accessibility Audit

### 1. Keyboard Navigation & Tab Order
- **Skip Link:**
  - Selector: `a.skip-link`
  - Measured state: On page load, positioned off-screen (`transform: translateY(-110%)`). On Tab key press, it receives focus and transitions to `transform: translateY(0)`, appearing at `top: 0, left: 1rem` with white text on brand blue and a 2px outline.
  - Link target: `#main-content`, focusing the main container.
- **Sidebar & Content Order:**
  - At desktop widths (> 1024px), tab order flows: Skip link -> Sidebar rail links -> User profile & sign-out -> Main content controls.
  - At mobile/tablet widths (<= 1024px), tab order flows: Skip link -> Mobile topbar hamburger button -> Main content controls.
- **Focus Indicators:**
  - Evaluated on all interactive elements. Standard controls show a 2px solid ring (`var(--kb-color-primary)` or `var(--brand)`) with 2px offset.
  - **R-03 (Medium — VERIFIED & FIXED):** `.mobile-data-card__primary` previously declared only `:focus-visible`, causing programmatic `.focus()` in unit test environments and certain browser focus states to not show the focus outline. Resolved by declaring `.mobile-data-card__primary:focus, .mobile-data-card__primary:focus-visible`.

### 2. Dialog Modals (`role="dialog"`)
- **Evaluation:** Tested `ConfirmDialogComponent`, `OrderDetailModalComponent`, and inline modal implementations across all pages.
- **Findings & Fixes:**
   - `ConfirmDialogComponent` and `OrderDetailModalComponent` already conformed to WCAG requirements: autofocusing initial action, capturing `Escape` key via `@HostListener('document:keydown')`, trapping Tab focus, and restoring focus to `previouslyFocused` on destroy.
   - **A-01 (High — VERIFIED & FIXED):** Inline modals previously lacked focus trapping, initial focus placement, Escape key dismissal, and focus restoration upon closing.
   - **Fix Applied:** Upgraded `FocusTrapDirective` (`appFocusTrap`) with initial focus placement on `ngOnInit`, `@Output() escape` emitting on `Escape` keydown, and focus restoration to the trigger element on destroy. Bound `appFocusTrap` and `(escape)=...` across all 11 inline modal backdrops in Businesses, Staff (form & permissions), Inventory (add/edit & detail), Menu (add/edit, categories, bulk move), Orders (refund & cancel), Dashboard (terminals & low stock), and Terminals (recovery & challenge approve).

### 3. Accessible Names on Icon-Only Buttons
- Evaluated icon buttons across all screens (`.hamburger`, `.refresh-btn`, `.icon-btn`, `.filter-pill`, `.modal-close`).
- Result: **0 missing accessible names detected**.
  - Hamburger: `aria-label="Open navigation menu"` / `aria-label="Close navigation menu"`
  - Refresh buttons: `ariaLabel="Refresh live metrics"` / `aria-label="Refresh businesses"`
  - Notifications: `aria-label="Notifications"`
  - Terminal status: `[attr.aria-label]="onlineTerminalsCount() + ' active...'"`
  - Modal close: `aria-label="Close dialog"`

### 4. Color Contrast (WCAG 2.1 AA Compliance)
- **Primary text:** `--kb-color-foreground` (`#111827`) on white canvas (`#FFFFFF`): **16.9:1** (Exceeds 4.5:1 requirement).
- **Secondary text:** `--kb-color-muted-foreground` (`#556377`) on white canvas: **6.1:1** (Exceeds 4.5:1 requirement).
- **Action buttons:** White text (`#FFFFFF`) on `--kb-color-primary` (`#4F46E5` / `#2563EB`): **5.17:1** (Exceeds 4.5:1 requirement).
- **Status Chips:**
  - Success chip: `#166534` text on 10% soft tint: **6.8:1** (AA Pass).
  - Warning chip: `#9A3F08` text on 10% soft tint: **5.9:1** (AA Pass).
  - Danger chip: `#B91C1C` text on 7% soft tint: **6.4:1** (AA Pass).

---

## Part 3: State Checks (Loading, Empty, Error, Success)

| Route | Loading State | Empty State | Error State |
|---|---|---|---|
| `/admin/businesses` | 5 Skeleton rows (`.skeleton-stack`) | `<app-empty-state icon="🏪">` when filtered results = 0 | `<app-api-state>` with retry button |
| `/business/dashboard` | `.posSkeleton` with shimmering bento tiles | Empty operational tiles with "No active orders" | `.state-panel` with "We couldn't load your dashboard" + retry |
| `/business/orders` | Skeleton stack on order history tab | `.empty-orders-view` with "Kitchen & Floor Counter Clear" | `<app-api-state>` with retry button (**Fixed concurrent render**) |
| `/business/menu` | 5 Skeleton rows | `<app-empty-state>` with "No menu items match your filter" | `<app-api-state>` with retry button |
| `/business/inventory` | Skeleton rows per material tab | Empty state with "Add your first raw material" | `<app-api-state>` with retry button |
| `/business/terminals` | `.compact-loading` indicator | `.empty-state` with "No terminals found" | `<app-api-state>` with retry button |

---

## Part 4: Source-Only Findings

### Finding S-01: Login Page Color Palette Tokens Override
- **Location:** `apps/web-admin/src/app/pages/login/login-page.component.ts:405-407`
- **Severity:** High (Design System Freeze Violation)
- **Evidence:**
  ```css
  :host {
    display: block;
    --login-orange: #F58213;
    --login-orange-action: #C35500;
    --login-orange-hover: #A94400;
    --kb-color-foreground: #172347;
    --kb-color-muted-foreground: #7180A2;
    --kb-color-border: #D8E0F1;
  ```
- **Analysis:**
  `docs/design/DESIGN_SYSTEM_FREEZE.md` strictly locks the web-admin palette. Orange `#E87A1E` and `#B45309` (`--brand-orange-ink`) are approved only as accents, while blue `#2563EB` / `#4F46E5` is the primary interactive brand color. The login component overrides core neutral tokens and introduces unapproved orange literals (`#F58213`, `#C35500`, `#A94400`).
- **Proposed Action:** Await user decision. If approved, migrate login tokens to the shared `--kb-color-*` semantic tokens.

### Finding S-02: Hardcoded `#FFFFFF` Outlines in Sidebar Layout
- **Location:** `apps/web-admin/src/app/layout/sidebar-layout/sidebar-layout.component.ts:205, 291, 403, 508, 671`
- **Severity:** Low (Code Hygiene)
- **Evidence:**
  ```css
  .nav-link:focus-visible { outline: 2px solid #FFFFFF; outline-offset: 2px; }
  .logout-btn:focus-visible { outline: 2px solid #FFFFFF; outline-offset: 2px; }
  .sidebar-toggle:focus-visible { outline: 2px solid #FFFFFF; outline-offset: 2px; }
  .sidebar-close-btn:focus-visible { outline: 2px solid #FFFFFF; outline-offset: 2px; }
  ```
- **Analysis:**
  The sidebar is dark navy (`#0F1020` / `#12162A`). White outlines provide essential high-contrast focus rings (3.6:1+). However, using hex literal `#FFFFFF` directly bypasses token definitions.
- **Proposed Action:** Standardize on `var(--pos-sidebar-ink)` or define `--pos-sidebar-focus-ring: #FFFFFF;` in `:root`.

### Finding S-03: Media Query Breakpoint Inconsistency
- **Location:** `apps/web-admin/src/styles.css` vs `apps/web-admin/src/styles/bento.css` and component TS files.
- **Severity:** Medium (Maintainability / Boundary Glitches)
- **Evidence:**
  - `styles.css`: `@media (max-width: 767px)` (table hiding / mobile data list)
  - `bento.css`: `@media (max-width: 768px)`
  - `styles.css`: `@media (max-width: 900px)`
  - `bento.css`: `@media (max-width: 960px)`
  - Component files declare ad-hoc breakpoints at 1100px, 1080px, 860px, 720px, 680px, 640px, 560px, 520px, 480px.
- **Analysis:**
  At exactly `768px`, rules in `bento.css` and components fire, while `styles.css` (767px) does not, creating an 1px viewport boundary discrepancy.
- **Proposed Action:** Standardize breakpoint variables (e.g. mobile: 767px or 768px) across all stylesheets.

---

## Part 5: Summary of Fixes Applied

1. **`apps/web-admin/src/styles.css`**:
   - Added `.mobile-data-card__primary:focus` alongside `:focus-visible` to ensure focus rings are visible regardless of programmatic or keyboard focus trigger.
2. **`apps/web-admin/src/app/pages/orders/order-history-tab.component.ts`**:
   - Added `*ngIf="!ordersError"` to `<ng-container aria-label="Order history">` to prevent concurrent rendering of the empty table view when an API error state is active.
3. **`apps/web-admin/src/app/core/components/focus-trap.directive.ts`**:
   - Added `@Output() escape = new EventEmitter<void>();` to emit on Escape keydown.
   - Added automatic initial focus (`setTimeout(() => this.focusFirstFocusable(), 0)`).
   - Preserved background overflow suppression and focus restoration on destroy.
4. **Modal Accessibility across All Pages**:
   - Bound `appFocusTrap` and `(escape)="dismissHandler()"` across:
     - `BusinessesPageComponent` (Add Restaurant modal)
     - `StaffFormModalComponent` (Staff add/edit modal)
     - `StaffPermissionsModalComponent` (Permissions modal & role template creation modal)
     - `InventoryPageComponent` (Add/Edit Material & Material Stock Detail modals)
     - `BusinessDashboardPageComponent` (Terminal Fleet & Low Stock Raw Ingredients modals)
     - `MenuPageComponent` (Add/Edit Dish, Manage Categories, & Bulk Move modals)
     - `OrderHistoryTabComponent` (Record Manual Refund & Cancel/Void Order modals)
     - `LiveOrdersTabComponent` (Cancel/Void Order modal)
     - `TerminalsPageComponent` (Recover Terminal Access & Confirm Device Challenge modals)

---

## Part 6: Verification

- **Production Build:** `cd apps/web-admin && npx ng build` -> **Exit Code 0 (Success)**.
- **Karma Unit Tests:** `cd apps/web-admin && CHROME_BIN=/tmp/chrome-no-sandbox.sh npx ng test --watch=false` -> **270/270 passed (100% Success)**.
- **Rendered Verification:** Verified across all routes at 1024px and 390px using Chrome headless tooling. Screenshots archived in `docs/audit/screenshots/`.
- **Rendered Browser Verification:** All 16 routes and viewports verified via Puppeteer CDP test runner with screenshot artifacts preserved in `docs/audit/screenshots/`.
