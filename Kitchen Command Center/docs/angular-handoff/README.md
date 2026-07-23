# KhanaBook Web Admin — Angular handoff package

> **This package was generated from the Lovable React design prototype. It has not been integrated into, compiled against, or tested in the KhanaBook Angular Web Admin repository. Routes, APIs, guards, payloads, and business logic must be verified inside the actual Angular project before implementation.**

## Purpose

This folder is a portable design & implementation reference for the coding agent (or engineer) performing the real migration inside the KhanaBook Angular Web Admin. It contains:

- Design tokens as Angular-ready CSS custom properties.
- Per-component implementation specifications.
- Per-page migration specifications.
- A prototype-to-Angular mapping table.
- Blank inventory templates to fill in during migration.
- An acceptance checklist to gate every batch.
- Optional non-production reference scaffolds.

## Files

### Design tokens

| File                                               | Contains                                                                                                                              | How to use                                                                                                                                                                                       |
| -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| [`_khanabook-tokens.css`](./_khanabook-tokens.css) | Full colour, typography, spacing, radius, shadow, gradient, breakpoint, motion, z-index, layout tokens as `--kb-*` custom properties. | Copy into the Angular project (e.g. `src/styles/_khanabook-tokens.css`) and import from global styles. Map any existing project variables that overlap; do not maintain two systems in parallel. |

### Component specifications (`components/`)

One markdown file per shared component. Each covers purpose, suggested selector, standalone component name, inputs, outputs, projection, variants, sizes, CSS classes, CSS variables, all interactive states, mobile / keyboard / accessibility requirements, and a related prototype route.

- `kb-app-shell.md`, `kb-sidebar.md`, `kb-mobile-drawer.md`, `kb-topbar.md`
- `kb-page-header.md`
- `kb-kpi-card.md`, `kb-status-badge.md`, `kb-currency.md`
- `kb-data-table.md`, `kb-mobile-data-card.md`, `kb-filter-toolbar.md`
- `kb-button.md`
- `kb-dialog.md`, `kb-drawer.md`, `kb-confirmation-dialog.md`
- `kb-form-field.md`, `kb-secret-input.md`, `kb-copy-button.md`, `kb-file-upload.md`
- `kb-empty-state.md`, `kb-error-state.md`, `kb-skeleton.md`, `kb-toast.md`

Prefer these specifications over the reference scaffolds — the specifications encode intent; the scaffolds are only skeletons.

### Reference scaffolds (`reference-components/`)

Optional, non-production Angular skeletons. Each scaffold uses separate `.component.ts`, `.component.html`, `.component.scss` files and carries the header:

```
REFERENCE ONLY — NOT IMPLEMENTED OR VERIFIED IN THE KHANABOOK ANGULAR APPLICATION.
```

They contain no API calls, no auth, no route guards, no state management, no mock data, and no React dependencies. Two scaffolds are included to illustrate the pattern (`kb-status-badge`, `kb-kpi-card`); use the specifications to author the rest.

### Page migrations (`page-migrations/`)

One markdown file per screen: prototype route, existing production route, layout anatomy at desktop / tablet / mobile, shared components required, loading / empty / error / validation / success states, sensitive-action rules, accessibility requirements, existing functionality that must be retained, visual acceptance criteria.

| File                     | Existing route                     | Prototype route                 |
| ------------------------ | ---------------------------------- | ------------------------------- |
| `01-login.md`            | `/login`                           | `/proto/login`, `/proto/forgot` |
| `02-limited-access.md`   | `/limited-access`                  | (reuses login layout)           |
| `03-owner-dashboard.md`  | `/business/dashboard`              | `/proto/owner/dashboard`        |
| `04-reports.md`          | `/business/reports`                | `/proto/owner/reports`          |
| `05-orders.md`           | `/business/orders`                 | `/proto/owner/orders`           |
| `06-menu.md`             | `/business/menu`                   | `/proto/owner/menu`             |
| `07-staff.md`            | `/business/staff`                  | `/proto/owner/staff`            |
| `08-owner-terminals.md`  | `/business/terminals` (OWNER)      | `/proto/owner/terminals`        |
| `09-shop-terminals.md`   | `/business/terminals` (SHOP_ADMIN) | `/proto/shop/terminals`         |
| `10-marketplace.md`      | `/business/marketplace`            | `/proto/owner/marketplace`      |
| `11-admin-dashboard.md`  | `/admin/dashboard`                 | `/proto/admin/dashboard`        |
| `12-admin-businesses.md` | `/admin/businesses`                | `/proto/admin/businesses`       |

### Prototype → Angular mapping

[`prototype-angular-mapping.md`](./prototype-angular-mapping.md) — table mapping each prototype element to its Angular counterpart, inputs, outputs, CSS variables, responsive behavior, and accessibility contract. Includes a "removed by design" section for prototype-only helpers.

### Templates

- [`angular-migration-inventory-template.md`](./angular-migration-inventory-template.md) — copy into the Angular repo and fill in per route and per shared component before writing code.
- [`angular-acceptance-checklist.md`](./angular-acceptance-checklist.md) — gate every batch behind this list.

### Downstream execution

- [`preflight.md`](./preflight.md) — run FIRST inside the real KhanaBook Angular repo to confirm the environment before writing any code. Contains explicit STOP conditions.
- [`prompts/`](./prompts/) — one prompt file per batch (`00-preflight-and-baseline.md` → `11-regression-and-final-audit.md`). Each is independently usable and enforces the "confirm previous batch → inspect → smallest safe change → narrow tests → typecheck + build → honest report → commit" cycle. Result statuses are constrained to `PASS`, `FAIL`, `STATICALLY REVIEWED`, `PARTIALLY TESTED`, `NOT TESTED`, `NOT AVAILABLE`, `BLOCKED`.

## Suggested workflow for the coding agent

1. Read the top-level Lovable handoff docs in `../docs/khanabook-*.md` for context.
2. Run `preflight.md` inside the Angular repo. Do not proceed if any STOP condition triggers.
3. Read `_khanabook-tokens.css` and add it to the Angular project's global styles.
4. Fill in `angular-migration-inventory-template.md` — do not begin work until the inventory row is complete.
5. Work through `prompts/00` → `prompts/11` in order. Each prompt has its own scope, preservation rules, tests, report format, and stop conditions.
6. Run the acceptance checklist per batch; do not merge until it is green.

## What this package does NOT do

- It does not modify or claim to modify the Angular repository.
- It does not run Angular typechecks, tests, or builds — the Lovable environment only builds the React prototype.
- It does not verify API equivalence — that must happen in the Angular project against real services.
- It does not include real credentials, mock backend data, or production endpoints.

## Prototype build status (Lovable side only)

The React prototype in this Lovable project builds cleanly and served the screens used to generate these specifications. Any build/test results reported apply to the prototype, not to the Angular application.
