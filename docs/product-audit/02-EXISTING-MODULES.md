# 02 — Existing Modules

**Method:** every module was located in source, its responsibilities described from the actual classes, and its dependencies traced. Behaviour is not claimed unless marked [V]. There are 16 server modules, 11 Android features and 30 web-admin pages.

## 1. Server modules (`services/server/.../feature/*`)

All 16 follow `controller / service / data|entity|repository / data|dto`. The uniformity is deliberate and makes the map below reliable.

| # | Module | Responsibility (from code) | Depends on |
| --- | --- | --- | --- |
| 1 | `auth` | login, signup+OTP, Google token exchange, refresh/logout, password reset, web-admin OTP reset, JWT + blocklist, presence | `notifications`, `platform` |
| 2 | `billing` | bills, line items, GST computation, daily order numbering, refunds | `menu`, `restaurants` |
| 3 | `business` | owner console aggregate APIs: dashboard, profile, refunds, staff, menu, orders (`/business/**`) | nearly all |
| 4 | `compliance` | regulatory/GST compliance records | `billing` |
| 5 | `inventory` | stock items, adjustments, consumption | `menu` |
| 6 | `menu` | categories, items, variants, pricing, extraction from image | `restaurants` |
| 7 | `notifications` | email/SMS/WhatsApp dispatch, templates under `resources/templates/email` | — |
| 8 | `onboarding` | business onboarding / verification flow | `restaurants`, `subscription` |
| 9 | `payments` | Easebuzz integration, sub-merchant KYC, webhooks, settlements, refunds | `billing`, `subscription` |
| 10 | `platform` | KBOOK_ADMIN surface: businesses, feature flags, system health, activity logs | all |
| 11 | `reports` | sales, daily closing, commission, export | `billing`, `payments` |
| 12 | `restaurants` | tenant profile, terminals, logo upload, business-day settings | — |
| 13 | `shifts` | cashier shift open/close, cash reconciliation | `staff` |
| 14 | `staff` | staff records, roles, per-key permission grants | `restaurants` |
| 15 | `subscription` | plans, entitlements, trials | `platform` |
| 16 | `sync` | generic push/pull for every syncable entity, per-key permission enforcement, terminal auth | all |

**Observation [C]:** `business` and `platform` are aggregation layers that re-expose other modules' data. They are the two places where a change in a leaf module surfaces as a compile error in a module that has nothing to do with the change. That is normal for a modular monolith, but it is why `GenericSyncService` (1,704 lines) and `BusinessWriteService` carry so much traffic.

**Observation [V]:** `sync` is the security-critical module. The `SecurityConfig` comments state that `/sync/**` would otherwise let a `SHOP_STAFF` token re-point the owner's login identifier, which is why four paths carry an explicit `OWNER` matcher plus `@RequireRole(OWNER)` (`REQUIREMENT_IMPLEMENTATION_GAPS.md` §2 records the fix).

## 2. Android features (`apps/android/.../feature/*`)

Each has `ui / viewmodel / data / domain`.

| # | Feature | Responsibility | Notes |
| --- | --- | --- | --- |
| 1 | `auth` | login, Google sign-in, OTP, change password, app-lock PIN, session | `ChangePasswordView.kt` had the AUD-001 syntax break |
| 2 | `billing` | new bill, cart, KOT, settlement, daily numbering | `BillingViewModel.kt` 1,786 lines, `BillDao.kt` 1,459 |
| 3 | `menu` | categories, items, variants, images | |
| 4 | `notifications` | FCM registration + in-app notifications | device token in prefs |
| 5 | `onboarding` | device setup / activation | |
| 6 | `payments` | cash, UPI QR, card, split tender | |
| 7 | `printing` | ESC/POS thermal output, KOT + receipt templates | queued when no printer paired |
| 8 | `reports` | sales, daily closing on device | |
| 9 | `settings` | restaurant configuration, business hours, quick start | `ShopConfigSection.kt` (973 lines) is the densest screen |
| 10 | `staff` | staff list, permissions (read-only on POS by design) | |
| 11 | `sync` | Room ⇄ server master push/pull, conflict handling | `MasterSyncProcessor.kt` 2,054 lines |

**Cross-cutting Android core** (`core/`): `database` (Room, 18 entities, 2,387-line `AppDatabase.kt`), `network` (`KhanaBookApi`), `designsystem` (`KhanaBookScreenScaffold`, tokens, dialogs, toasts), `theme`, `components` (`ParchmentTextField`, `KhanaTimePickerField`), `util` (`ValidationUtils`, `UserMessageSanitizer`).

## 3. Web admin pages (`apps/web-admin/src/app/pages/*`)

30 route components: `login`, `business-dashboard`, `business-settings`, `businesses`, `daily-closing`, `reports`, `sales-overview`, `orders`, `menu`, `inventory`, `staff`, `terminals`, `users`, `integrations`, `notifications`, `support-tickets`, `activity-logs`, `backups`, `feature-flags`, `platform-dashboard`, `platform-reports`, `platform-settings`, `system-health`, `help`, `limited-access`, `not-found`, plus four cross-cutting specs (`form-validation`, `micro-interactions`, `responsive-layout`, `refresh-reload`, `table-design`).

Two page components are outsized (AUD-009): `business-dashboard` 3,901 lines and `menu` 2,398 lines.

## 4. Dependency risks found

| Risk | Level | Detail |
| --- | --- | --- |
| `sync` ↔ everything | Medium | `GenericSyncService` and `MasterSyncProcessor` switch over every syncable entity. Adding a field to any entity touches the largest files in both codebases. |
| `auth` → `notifications` | Medium | OTP delivery failure surfaces as an auth error. `ANDROID_POS_DOCUMENTATION` BUG-004 is exactly this: code correct, SMTP unset. |
| `payments` → provider | High | Easebuzz is a single external dependency for money movement; webhook endpoints are intentionally `permitAll` and must be signature-verified downstream. |
| Google/Firebase config | Low | `google-services.json` is per-developer and gitignored; CI synthesises a stub. Any feature that assumed real Firebase at build time breaks CI. |

## 5. Module-level recommendations

1. Do not add modules. The 16/11/30 split already matches the domain.
2. Add a **tenant-isolation guard** at the ORM boundary rather than reviewing 46 controllers by hand (`08`, `09`).
3. Split `AppDatabase.kt` and `MasterSyncProcessor.kt` along their existing entity boundaries; both are mechanical moves with tests already available as the safety net.
4. Retire the dead `UserServiceImpl.completePasswordReset` (AUD-011) so the module map stops advertising a capability that no endpoint reaches.
