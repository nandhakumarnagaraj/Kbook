# KhanaBook Web-Admin — Architecture Audit (prototype baselining, 2026-10-29)

Audited against `apps/web-admin` at commit `34435084` (branch `main`), **without disturbing uncommitted local changes**.

## 1. Application structure

- **Monorepo root**: `Khanabook/KhanaBook` with `apps/web-admin` (Angular 18.2, standalone components, zone.js) and `apps/android`.
- **App shell**: `src/app/app.config.ts` — `provideRouter(routes)`, `provideHttpClient(withInterceptors([jwtInterceptor]))`, service worker, Firebase init + notification service.
- **Styling**: global `src/styles.css` (design tokens `--kb-color-*`, `--kb-text-*`, `--kb-space-*`, `--kb-radius-*`, `--kb-z-*`; type scale + contrast rationale inline), plus `src/styles/bento.css` primitives and `src/styles/responsive.css`. Component styles are inline `styles = [...]` in each `.ts` file.
- **Icons**: `@material-design-icons/font` via shared `kb-icon` component (`shared/icon.component.ts`).
- **Auth**: `core/auth/auth.service.ts` (103 lines, JWT in session store), `core/auth/role.guard.ts` (`authGuard`, `roleGuard` with `data: { roles: [...] }`), JWT interceptor. Login page → role-based landing path.
- **Services**: `core/services/admin-api.service.ts` (dashboard summary, businesses list/create/suspend/activate, feature-flags list/audit/kill-switch/default/per-restaurant, sub-merchants, transactions, settlements, payment-metrics overview, webhooks health) and `core/services/business-api.service.ts` (owner-side APIs).

## 2. Reusable shared components (verified inputs)

| Component | Used for |
|---|---|
| `shared/page-header` | `title`, `subtitle`, `eyebrow`, `live` |
| `shared/kpi-row` | `items: KpiItem[]` (icon/value/label/delta pattern) |
| `shared/pagination-bar` | `currentPage`,`pageSize`,`totalItems` + `(pageChange)` |
| `shared/empty-state` | `icon`,`title`,`text`,`actionLabel` |
| `shared/confirm-dialog`, `shared/global-toast`, `shared/toast.service` | destructive/positive feedback |
| `shared/date-range-selector`, `shared/date-range-toolbar` | Reports/Sales ranges |
| `shared/refresh-button`, `shared/header-search`, `shared/notification-bell` | toolbar affordances |
| `shared/icon` | `name`,`size` (Material Design Icons font) |
| `layout/sidebar-layout` | shell + nav groups + user footer + Sign out |

## 3. Existing routes & guards (all under `authGuard`)

Admin (`KBOOK_ADMIN`): `/admin/dashboard`, `/admin/businesses`, `/admin/feature-flags`.
Owner (`OWNER`): `/business/{dashboard,reports,daily-closing,orders,menu,inventory,staff,terminals,settings,help}` (+ `active-orders` redirect), settings has `unsavedChangesGuard`.
Public: `/login`, `/limited-access`, `/404`, `**`. Root redirects to role landing path. **All 10 Owner references have existing routes; 10 of 13 Admin references have no route at all.**

## 4. Which reference screens can reuse existing pages

All Owner screens (O01–O10) map to existing implemented pages with real APIs — these pages are **already built and committed** (recent commits explicitly aligned them to the references: "complete dashboard restructuring to match Dashboard.png", Help & Support styling, orders tab restructuring).

## 5. New screens necessary (no route, no backend endpoint)

`admin/subscriptions`, `admin/users`, `admin/sales`, `admin/reports`, `admin/system-health`, `admin/activity-logs`, `admin/notifications`, `admin/settings`, `admin/backups`, `admin/integrations`.
Backend `admin-api.service.ts` exposes **no endpoints** for any of these ten. Per the prototype contract these pages will render the reference layout with an explicit, visually-labeled **prototype-data state** (marked "Prototype sample — no API" in-page) rather than fabricating "live" data. Where any data genuinely exists (businesses list, payment metrics) it will be wired to the real service.

## 6. Build/dependency issues found

- `npx ng build --configuration development` **passes** in ~13s (verified on basline before any edit).
- House guard scripts exist and are strict: `check:*` (type-scale, tokens, colors, contrast, spacing, z-index, accent budget, inline styles, motion). New code must consume tokens only — **no literal font sizes/colors/z-index** in new components.
- Uncommitted user work is present in 5 files (business-dashboard page, inventory page, live-orders tab + spec, notification-bell) and various new untracked test/docs assets; **these are prior/parallel work and will not be overwritten**. Owner-screen divergence from references in those files will be reported, not patched over.
- Karma + Playwright configured; `npm test` available (not run in background shell yet — will run targeted specs after edits).

## 7. Conventions new code must follow

1. Standalone component, inline template/styles, `OnPush` where the file already uses it.
2. Fonts/colors/spacing/z-index exclusively via `--kb-*` tokens.
3. `kb-icon` for icons (no emoji/unicode icons).
4. Lazy-loaded via `loadComponent` in `app.routes.ts`, guarded by existing `roleGuard`.
5. Nav links are declared in `sidebar-layout.component.ts` `NavGroup[]` (role-filtered), pointing to new routes.
