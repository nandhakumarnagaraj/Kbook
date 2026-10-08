# KhanaBook — About

> Canonical product + technical overview, mirroring the codebase as of the current
> main branch. Covers: what it is, roles, platforms, role×platform access,
> features/modules, sync architecture, and data residency.

## Two-liner

KhanaBook is an offline-first restaurant billing & operations SaaS: Android POS
terminals that keep working with zero connectivity, a web-admin console for the
owner, and a multi-tenant Spring Boot backend (`server/`) in production on
PostgreSQL. Billing, payments, menu, inventory, reports, staff and notifications
all flow through a batch-based, conflict-safe sync engine.

## Roles

Canonical source: `server/.../feature/auth/entity/UserRole.java`
(`OWNER`, `SHOP_STAFF`, `KBOOK_ADMIN`); Android mirrors the first two via
`feature/auth/domain/SessionManager.kt`.

| Role | Who | Notes |
|---|---|---|
| **OWNER** | Shop owner | All operations on every platform |
| **SHOP_STAFF** | Waiters / cashiers / managers | Fixed role-bound set — access is constant and can neither be expanded nor restricted per member |
| **KBOOK_ADMIN** | Platform operator | Server-side platform admin (feature flags, KYC, settlement, notifications) |

### Staff access is pinned (policy enforced in code)

- SHOP_STAFF get exactly `SHOP_STAFF_GRANTED_KEYS` — no per-operation grant/revoke
  rows are honored, on either side:
  - Server: `PermissionService.hasPermission` / `getGrantedPermissions` return the fixed
    role set only for SHOP_STAFF (`server/.../staff/service/PermissionService.java`).
  - Android: `PermissionManager.hasPermission` pins staff to the fixed set and ignores
    synced explicit grants (`Android/.../staff/domain/PermissionManager.kt`).
- Web-admin permission editor is read-only for SHOP_STAFF
  (`web-admin/.../staff/staff-permissions-modal.component.ts`).
- The owner CANNOT give a staff member "extra permissions". Role-set changes require
  a code change + redeploy (editing the config/grant sets).

## Platforms

| Platform | Stack | Purpose |
|---|---|---|
| **Android POS** (`Android/`) | Kotlin, Jetpack Compose, Room, Retrofit, WorkManager | On-device billing terminals; single-user POS; works offline |
| **Web-admin** (`web-admin/`) | Angular | Owner console: staff, reports, business/KYC, permissions, templates, notifications |
| **Backend** (`server/`) | Spring Boot, PostgreSQL, docker-compose (prod/staging) | Multi-tenant API + sync engine (source of truth) |

### Access by role × platform

**Android POS:**
- **OWNER** — full set of operations.
- **SHOP_STAFF** — fixed set of 18 (app's role set):
  - Billing: create, edit, void, discount, refund, settle
  - Menu: view
  - Orders: view
  - Reports: day summary, full, GST
  - Staff: view, add, edit, remove, manage permissions
  - Settings: printer config, device management
  - Owner-only (pinned): menu edits (toggle availability / change price / full edit / add / delete), export data, shop profile, payment settings, GST/FSSAI settings.
  - First login: poller shows "pending owner approval" until the owner approves the
    device (`TerminalPendingApprovalException`, `InitialSyncScreen.kt`).
- **KBOOK_ADMIN** — not an app role; platform work happens server-side.

**Web-admin:** owner console (staff CRUD + read-only permission view, reports,
KYC/business, templates, notifications). SHOP_STAFF do not sign in here for
management.

**Backend:** role checks server-side (source of truth); `KBOOK_ADMIN` for platform
operations; explicit rows for SHOP_STAFF never change access (fixed role set).

## Features / modules

| Module | Android | Server / web-admin |
|---|---|---|
| Billing | Bills, KOT/orders on bill items, split (part cash/UPI) payments, discounts, refunds, void, e-invoice | Bill sync/conflict engine, invoice HTML (`E000030` style), payment validation |
| Menu | Categories, items, variants, availability, recipes | Menu sync, menu-edit authorization/stamping, extraction jobs |
| Inventory | — | Raw materials, purchase orders, vendors, stock movements/logs |
| Payments | Payment modes (cash/UPI/part), printed receipts | Easebuzz sub-merchants, hosted checkout, webhooks, refunds (durable), payouts, settlement, chargebacks |
| Reports | Day summary, full, GST (export owner-only) | Admin dashboards, commission, settlement |
| Staff | Permission cache + `PermissionManager` (role-pinned) | Staff CRUD, permission rows/templates, revision stamping |
| Auth | OTP/login, device-terminal activation approval, refresh | JWT + refresh, roles, rate limits, security audit, OTP |
| Sync | `SyncManager` + `MasterSyncProcessor`, paged push/pull | `/sync/*` push/pull endpoints, offline-auth decider |
| Notifications | Local/remote + sync | Admin notification broadcasts (incl. permission_request type), KYC/FSSAI alerts |
| Compliance | — | GST/FSSAI trackers, merchant KYC agreement/onboarding |
| Settings/Printing | Shop profile, USB/Bluetooth/Ethernet printers, KOT | Restaurant profile (incl. printer flags) |
| Platform | — | Feature flags, KBOOK_ADMIN dashboards |

## Design architecture — sync

**Offline-first:** Room is the local source of truth for transactions; all writes
happen on-device and sync in the background. The POS never requires connectivity.

**One serialized engine:** `SyncManager` runs on `Dispatchers.IO`; a single
`syncMutex` serializes full sync cycles; the entire push+pull is wrapped in
`withContext(NonCancellable)` — acknowledged work must never be lost to cancellation.

**Push = batch + paged:**
- Master-data order follows FK dependencies (categories → menu → items → bills →
  bill items → bill payments, ...), sequential per family.
- Bills are pushed in **pages of 100**; bill items & bill payments in **pages of
  200** — large unsynced backlogs drain incrementally, fixing multi-hour syncs and
  push-time freezes (the old code loaded the whole backlog at once).
- Each batch returns `PushSyncResponse { successfulLocalIds, failedLocalIds,
  localToServerIdMap }`; only acknowledged ids are marked synced, and each gets its
  server id mapped back.
- Optimistic locking: an ack is honored only if the local row still matches what was
  pushed; a row that changed mid-push is re-pushed. Stale `updatedAt` echoes → 409.
- Conflict isolation: a 409 on one record does not discard the batch's other
  acknowledged work. Bill-family conflicts are resolved by re-push; other families
  escalate to a timestamp=0 recovery next cycle.
- Guards: `restaurantId` filtering, tenant scoping, clock-skew tolerance under
  offline grace, device/terminal registration before login.

**Pull = paged, background:** server truth re-imported page by page; staff
`grantedPermissions` + permission `revision` come down with pull and feed offline
authorization revalidation (so a stripped-away permission cannot keep working
offline forever). Permission cache survives process death (Room `permission_cache`).

## Data residency

### SQLite only (on-device, never written to Postgres)

| Table | Holds |
|---|---|
| `printer_profiles` | Local printer device config (role→printer, name, MAC, connection type, host/port, paper size, auto-print, logo, copies). Printer flags are mirrored inside the synced restaurant profile, but the device binding is local. |
| `kitchen_print_queue` | Pending KOT print jobs. Pure runtime queue. |
| `kot_events` | Immutable KOT print/state event log (NEW/ADD/VOID/REPRINT/CANCEL, kotRevision, item snapshot JSON). Local audit for reprints. |
| `terminal_daily_counter` | Per-terminal, per-day bill numbering. Rebuilt/seeded from synced bills. |
| `sync_quarantine_records`, `permission_cache`, `notifications` | Sync sandbox, offline-authorization mirror, local notification log. |

### PostgreSQL (production, `server/`)

Bills, bill items & bill payments (**KOT state rides on bill items** as
`kotState`/`kotStatus`), menu (categories/items/variants), restaurant profile,
users/staff + permission rows & revision, stock logs + inventory, payments/Easebuzz
records, tokens, rate-limit + security-audit events, terminals/devices, notification
broadcasts, templates.

### The KOT nuance

- KOT **state on a bill item syncs** into Postgres (`BillItemDTO`) so reports and
  reconciliation see kitchen progress.
- KOT **printing runtime stays local** (`kitchen_print_queue` jobs, `kot_events`).

## Brand & design (summary)

Warm, professional, grounded. Saffron/amber palette, dark mode in warm browns and
creams, dense but breathable dashboards, status-at-a-glance chips. Offline
confidence is a first-class design principle (live sync state, last-synced stamps,
unsynced counts). See `docs/design/DESIGN_SYSTEM.md` for the full design system.