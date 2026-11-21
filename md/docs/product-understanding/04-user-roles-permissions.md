# 04 - User Roles & Permission Architecture
> **KhanaBook SaaS Platform Discovery & Architecture Specification**  
> **Repository:** `/var/www/kbook.iadv.cloud`  
> **Document:** `docs/product-understanding/04-user-roles-permissions.md`  
> **Status:** Active / Production  

---

## 1. The Three Canonical Roles

KhanaBook enforces an intentional, streamlined security architecture with strictly three canonical roles, defined in `services/server/src/main/java/com/khanabook/saas/feature/auth/entity/UserRole.java`:

```mermaid
graph TD
    subgraph Roles["Canonical User Roles"]
        Owner["OWNER<br/>(Restaurant Owner)"]
        Staff["SHOP_STAFF<br/>(Waiters / Cashiers / Managers)"]
        Admin["KBOOK_ADMIN<br/>(Platform Operator)"]
    end

    subgraph Surfaces["Accessible Surfaces"]
        AndroidPOS["Android POS Terminal"]
        WebOwner["Web Admin — Business Console<br/>(/business/**)"]
        WebPlatform["Web Admin — Platform Console<br/>(/admin/**)"]
    end

    Owner -->|Full Access| AndroidPOS
    Owner -->|Full Access| WebOwner
    Staff -->|Role-Pinned Access| AndroidPOS
    Staff -.->|BLOCKED (HTTP 403)| WebOwner
    Admin -->|Platform Operations| WebPlatform
    Admin -.->|BLOCKED| AndroidPOS
```

### 1.1 `OWNER` (Restaurant Proprietor / Store Administrator)
- **Authority:** Full, unrestricted control over the restaurant tenant.
- **Accessible Surfaces:**
  - **Android POS:** All screens, menu price overrides, payment setup, printer configurations, and data export.
  - **Web Admin Portal (`/business/**`):** Real-time Live Floor, order cancellation/refund, menu catalog CRUD, staff hiring/firing, device terminal approval, GST reports export, and daily drawer closing.
- **Data Scope:** Full access to all terminals, financial records, staff actions, and settings within their own `restaurantId` tenant boundary.

### 1.2 `SHOP_STAFF` (Cashiers, Waiters, Floor Staff)
- **Authority:** Pinned, constant role-bound operations.
- **Accessible Surfaces:**
  - **Android POS:** Billing, cart management, line-item modifiers, split tendering, KOT kitchen printing, ticket reprinting, and terminal day summary reports.
  - **Web Admin Portal:** **STRICTLY PROHIBITED.** Any login attempt by a `SHOP_STAFF` credential to the Web Admin console is rejected at the authentication gateway (`AuthServiceImpl.java`).
- **Data Scope:** Restricted by terminal isolation (`created_terminal_id`). Staff members only see bills originating from their designated counter on the active POS screen.
- **Architectural Policy:** In Flyway migration V124, dynamic custom permissions and role templates were permanently retired. The permissions for `SHOP_STAFF` are **constant and immutable** in code. The owner cannot grant extra privileges (e.g. menu pricing) to staff without a core code release.

### 1.3 `KBOOK_ADMIN` (KhanaBook Platform Super Administrator)
- **Authority:** SaaS platform maintenance and cross-tenant governance.
- **Accessible Surfaces:**
  - **Web Admin Portal (`/admin/**`):** Platform metrics, tenant onboarding inspection, business activation/suspension, feature flag switches, system health telemetry, and broadcast announcements.
  - **Android POS:** **STRICTLY PROHIBITED.** Cannot log in to POS billing terminals.
- **Data Scope:** Global cross-tenant metadata; platform administration only. No direct tampering with daily restaurant till operations.

---

## 2. Pinned Role-Bound Staff Permissions (The 18 Action Keys)

Both the Spring Boot backend (`PermissionService.java`) and the Android client (`PermissionManager.kt`) hardcode the exact 18 capabilities granted to `SHOP_STAFF`:

| # | Permission Key | Category | Allowed for Staff? | Business Meaning |
|---|---|---|:---:|---|
| 1 | `billing.create` | Billing | **YES** | Create new dine-in, takeaway, and counter bills |
| 2 | `billing.edit` | Billing | **YES** | Add/remove items from an active draft bill |
| 3 | `billing.void` | Billing | **YES** | Void an unpaid or draft order with audit reason |
| 4 | `billing.discount` | Billing | **YES** | Apply percentage or fixed discounts to a bill |
| 5 | `billing.refund` | Billing | **YES** | Process refunds on settled bills |
| 6 | `billing.settle` | Billing | **YES** | Collect payment (Cash, UPI QR, Split) and mark bill paid |
| 7 | `menu.view` | Menu | **YES** | View categories, dishes, variants, and prices in POS |
| 8 | `orders.view` | Orders | **YES** | View active orders and order history for current terminal |
| 9 | `reports.day_summary` | Reports | **YES** | View the current terminal's daily Z-report |
| 10 | `reports.full` | Reports | **YES** | View sales summary for the current shift |
| 11 | `reports.gst` | Reports | **YES** | View local GST breakdown |
| 12 | `staff.view` | Staff | **YES** | View cashier list on terminal |
| 13 | `staff.add` | Staff | **YES** | Create a local staff profile (requires owner PIN) |
| 14 | `staff.edit` | Staff | **YES** | Edit staff display name |
| 15 | `staff.remove` | Staff | **YES** | Deactivate a staff member |
| 16 | `staff.permissions` | Staff | **YES** | View current permission matrix |
| 17 | `settings.printer` | Hardware | **YES** | Configure local Bluetooth/USB thermal receipt and KOT printers |
| 18 | `settings.device` | Hardware | **YES** | Complete initial device activation |
| 19 | `menu.edit` | Menu | **NO (Owner Only)** | Change dish prices, toggle availability, add/delete dishes |
| 20 | `reports.export` | Reports | **NO (Owner Only)** | Download sales ledgers and financial CSV files |
| 21 | `settings.profile` | Settings | **NO (Owner Only)** | Edit restaurant name, address, FSSAI number, business hours |
| 22 | `settings.payment` | Settings | **NO (Owner Only)** | Modify UPI handles, cash drawer settings, payment accounts |
| 23 | `settings.gst` | Settings | **NO (Owner Only)** | Modify GSTIN, GST percentages, tax-inclusive flags |

---

## 3. Role-Versus-Feature Access Matrix

| Feature / Capability | `OWNER` (Android) | `OWNER` (Web) | `SHOP_STAFF` (Android) | `SHOP_STAFF` (Web) | `KBOOK_ADMIN` (Web) |
|---|:---:|:---:|:---:|:---:|:---:|
| **Take Orders & Cart** | ✅ Full | ➖ | ✅ Full | ❌ Blocked | ❌ Blocked |
| **Split / Part Payment** | ✅ Full | ➖ | ✅ Full | ❌ Blocked | ❌ Blocked |
| **Apply Discounts** | ✅ Full | ➖ | ✅ Full | ❌ Blocked | ❌ Blocked |
| **KOT Kitchen Printing** | ✅ Full | ➖ | ✅ Full | ❌ Blocked | ❌ Blocked |
| **KOT Reprint / Call Customer** | ✅ Full | ➖ | ✅ Full | ❌ Blocked | ❌ Blocked |
| **Live Floor / Active Tabs** | ✅ Full | ✅ Full | ✅ Terminal Scoped | ❌ Blocked | ❌ Blocked |
| **Edit Menu Prices / Items** | ✅ Full | ✅ Full | ❌ Blocked | ❌ Blocked | ❌ Blocked |
| **OCR Menu Scanner** | ✅ Full | ✅ Full | ❌ Blocked | ❌ Blocked | ❌ Blocked |
| **Daily Drawer Closing** | ✅ Full | ✅ Full | ❌ Blocked | ❌ Blocked | ❌ Blocked |
| **Export Financial CSVs** | ✅ Full | ✅ Full | ❌ Blocked | ❌ Blocked | ❌ Blocked |
| **Configure Hardware Printers** | ✅ Full | ➖ | ✅ Full | ❌ Blocked | ❌ Blocked |
| **Terminal Fleet Approval** | ➖ | ✅ Full | ❌ Blocked | ❌ Blocked | ❌ Blocked |
| **Store Branding & Business Hours**| ✅ Full | ✅ Full | ❌ Blocked | ❌ Blocked | ❌ Blocked |
| **Tenant Suspension / Activation**| ❌ Blocked | ❌ Blocked | ❌ Blocked | ❌ Blocked | ✅ Full |
| **Platform Feature Flags** | ❌ Blocked | ❌ Blocked | ❌ Blocked | ❌ Blocked | ✅ Full |
| **System Health & Logs** | ❌ Blocked | ❌ Blocked | ❌ Blocked | ❌ Blocked | ✅ Full |

---

## 4. Authentication & Authorization Mechanisms

### 4.1 Dual-Token Security Model
All requests originating from the Android POS terminal must present dual cryptographic tokens:
```http
POST /sync/bills/push HTTP/1.1
Host: api.khanabook.com
Authorization: Bearer <USER_JWT_TOKEN>
X-Terminal-Token: <TERMINAL_HARDWARE_JWT_TOKEN>
Content-Type: application/json
```

1. **User JWT (`Authorization: Bearer <token>`):**
   - Issued by `/auth/login` or `/auth/verify-otp`.
   - Claims: `sub` (User Login ID), `restaurantId` (Tenant ID), `role` (`OWNER` or `SHOP_STAFF`), `exp` (Access expiration).
   - Validated by `JwtFilter.java` and injected into `SecurityContextHolder` and `TenantContext`.
2. **Terminal Hardware Token (`X-Terminal-Token: <token>`):**
   - Issued by `/sync/terminal/activate` after the owner approves the physical Android device.
   - Claims: `deviceId` (Hardware UUID), `terminalSeries` (`A`..`E`), `restaurantId` (Tenant ID).
   - Validated by `TerminalOwnershipService.java`. Ensures that even with a stolen user password, an unauthorized rogue phone cannot push transactions into the restaurant's financial ledger.

### 4.2 Web Admin Session Security
- Web Admin uses single User Bearer JWT authentication.
- Angular route guards (`authGuard` and `roleGuard` in `apps/web-admin/src/app/core/auth/role.guard.ts`) inspect the decoded JWT token:
  - If role is not `OWNER`, routes under `business/**` are immediately blocked.
  - If role is not `KBOOK_ADMIN`, routes under `admin/**` are immediately blocked.
  - If role is `SHOP_STAFF`, login redirects to `/limited-access` with an error indicating that staff accounts must use the Android POS app.

---

## 5. Security & Permission Parity Verification

- **IDOR Protection:** Every JPA query and sync processor method in `services/server` explicitly filters on `WHERE restaurantId = :restaurantId`. A tenant cannot view, mutate, or delete another tenant's bills or menu items.
- **Offline Permission Integrity:** In the Android app, `PermissionManager.kt` checks permissions against the hardcoded role set. Even if an attacker modifies the local SQLite database cache, the server rejects unprivileged requests during delta sync.
- **Fast Transactions:** All external network calls (WhatsApp API, JavaMail SMTP, Firebase FCM) are executed asynchronously outside of `@Transactional` boundaries, preventing HikariCP database pool exhaustion.
