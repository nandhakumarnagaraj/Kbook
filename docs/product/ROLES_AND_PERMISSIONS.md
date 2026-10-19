# KhanaBook — Roles & Permissions

> Canonical role and permission model. The server is authoritative; this page states the
> model once so the four role/surface combinations and every permission key live in one
> place.
>
> Verified against `main` / HEAD `afa5cc86` on 2026-10-17. Sources of truth in code:
> `UserRole.java`, `PermissionKey.java`, `PermissionService.java`,
> `PermissionManager.kt`, `SessionManager.kt`, `SecurityConfig.java`, `role.guard.ts`.

## 1. Roles

| Role | Who | Notes |
|---|---|---|
| `OWNER` | shop owner | full access on Android and the business web console |
| `SHOP_STAFF` | waiters, cashiers, managers | fixed permission set; explicit grant rows can never expand it |
| `KBOOK_ADMIN` | platform operator | platform only: `/admin/**`; no `/business/**`, no Android POS |

There is no fourth role. `SHOP_ADMIN` appears in some older docs — it does not exist in
code (`docs/design/DESIGN_SYSTEM.md` still needs correcting).

## 2. Surface access

| Surface | Allowed roles | Enforcement |
|---|---|---|
| Android POS | `OWNER`, `SHOP_STAFF` | `MainActivity` → `SessionManager.canUsePos()`; anything else is routed to the role-access screen |
| Web admin — business pages | `OWNER` | route `data.roles` + `roleGuard`; server `/business/**` OWNER-only |
| Web admin — platform pages | `KBOOK_ADMIN` | route guard; server `/admin/**` hasRole(KBOOK_ADMIN) |
| Server | authority for all | URL rules + `@RequireRole` + `PermissionService` |
| Easebuzz onboarding | `OWNER` (Android only) | web has config read/write only |

`SHOP_STAFF` cannot sign in to web-admin at all — the server rejects the web surface
before issuing a token.

## 3. Permission model

- **Server keys: 27** (`PermissionKey` enum). **Android keys: 27**
  (`PermissionManager.ALL_PERMISSION_KEYS`).
- The former KOT family (`orders.kot_view`, `orders.kot_ready`, `orders.kot_void`) was
  removed server-side (migration V114): it had no Android equivalent, since KOT is
  device-local.
- **Staff are granted "everything except the owner-only config families."** The
  exclusion set has 9 keys and is identical on both sides:

  `menu.toggle_availability`, `menu.edit_price`, `menu.edit_full`, `menu.add_item`,
  `menu.delete_item`, `reports.export`, `settings.shop_profile`, `settings.payment`,
  `settings.gst`

  So `SHOP_STAFF` holds **21 keys server-side / 18 on Android**. New keys default to
  granted unless added to the exclusion set — the default is deliberate.
- `menu.edit_full` **satisfies** `menu.edit_price` and `menu.toggle_availability`
  (`MenuChangeType.satisfies`), on both sides.
- Owner and admin bypass key checks entirely (`isOwner()` short-circuits;
  `SyncPushGuard` lets OWNER/KBOOK_ADMIN through).

## 4. Keys by category

| Category | Keys | Staff has? | Android |
|---|---|---|---|
| Billing | `billing.create`, `billing.edit`, `billing.void`, `billing.discount`, `billing.refund`, `billing.settle` | ✅ | ✅ |
| Menu | `menu.view` | ✅ | ✅ |
| Menu | `menu.toggle_availability`, `menu.edit_price`, `menu.edit_full`, `menu.add_item`, `menu.delete_item` | ❌ owner-only | ✅ |
| Orders / KOT | `orders.view` | ✅ | ✅ |
| Reports | `reports.day_summary`, `reports.full`, `reports.gst` | ✅ | ✅ |
| Reports | `reports.export` | ❌ owner-only | ✅ |
| Staff | `staff.view`, `staff.add`, `staff.edit`, `staff.remove`, `staff.permissions` | ✅ | ✅ |
| Settings | `settings.printer`, `settings.terminal` | ✅ | ✅ |
| Settings | `settings.shop_profile`, `settings.payment`, `settings.gst` | ❌ owner-only | ✅ |

Android also gates master-data writes by **role**, not only by key:
`SessionManager.canWriteMasterData()` (menu) and `canWriteConfig()` (profile/payment/tax)
are owner-only; printer config is the one exception granted to staff.

## 5. Gates in the codebase

| Gate | Side | Effect |
|---|---|---|
| `SecurityConfig` URL rules | server | coarse role → path mapping |
| `@RequireRole` + `RequireRoleAspect` | server | per-method role requirement |
| `PermissionService.hasPermission` | server | key check; SHOP_STAFF pinned to the fixed set |
| `SyncPushGuard.requirePermission` | server | whole-batch 403 if the actor lacks the key |
| `MenuPushAuthorizer` / `MenuChangeType` | server | per-record menu edit revalidation |
| `PermissionManager.hasPermission` | Android | ignores explicit grants for staff |
| `SessionManager.canUsePos/canWriteMasterData/canWriteConfig` | Android | surface + write gates |
| `roleGuard` + `session.role` | web | route guard, redirects to the role's landing path |
| `*ngIf="isOwner"` | web | hides mutating controls (UI only, never the control) |

## 6. Offline authorization

An operation created offline is classified (`OfflineAuthClass`) as:

| Class | Treatment when it reaches the server |
|---|---|
| `OFFLINE_ALLOWED` | accept, no revalidation |
| `REVALIDATED_ON_SYNC` | allowed offline, server re-checks the permission |
| `ONLINE_ONLY` | quarantined |
| `NEVER_OFFLINE` | quarantined for review |

`OfflineAuthDecider.decide(...)` returns ACCEPT / REJECT / QUARANTINE and checks terminal
validity first. The device pulls granted keys plus a permission **revision**
(`permission_cache`), so a revoked permission stops working offline after the next pull.
Every grant/revoke bumps the revision.

## 7. Changing roles or permissions

1. **Adding a key:** add it to `PermissionKey` (server) and `ALL_PERMISSION_KEYS`
   (Android) in the same change, decide whether staff should hold it by role, then decide
   its `OfflineAuthClass`. Update §3–§4 here.
2. **Changing what staff can do** is a code change plus redeploy — there is no per-member
   grant UI, deliberately. Do not "fix" this by honouring grant rows for staff.
3. **Owner-only writes** must be enforced server-side. UI hiding (`*ngIf`, disabled
   buttons) is presentation, never the control.
4. **Removing a key** requires checking server push guards first, or old APKs will start
   receiving 403s for batches they still send.
5. Add a test: server-side authorization test for the new/changed key, and a
   `PermissionManager` test if Android behaviour changes.

## 8. Invariants — do not weaken

1. Staff permissions are **fixed by role**; explicit grant rows are ignored for
   `SHOP_STAFF` on both sides.
2. `KBOOK_ADMIN` never gains `/business/**` or Android POS access.
3. Terminal scoping is enforced in the DAO, not the UI.
4. Any endpoint without an explicit URL rule falls back to `authenticated()` — give new
   endpoints an explicit role or permission gate.
5. UI hiding is not authorization.
