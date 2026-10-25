# Web Admin UI/UX Audit — 2026-10-25

**Scope:** `apps/web-admin` frontend only. No backend, API, schema, palette or business-logic changes.
**Status:** Partial. Source-level discovery and targeted fixes are done. A full rendered, responsive and keyboard review in a browser has **not** been done.

## Pages discovered (from `app.routes.ts`)

Admin (KBOOK_ADMIN): `admin/dashboard`, `admin/businesses`, `admin/feature-flags`.
Business (OWNER): `business/dashboard`, `reports`, `daily-closing`, `orders` (Live Floor and History tabs), `menu`, `inventory`, `staff`, `terminals`, `settings`.
Public/other: `login`, `limited-access`, `404`, `**`.

## Pages reviewed

- **Source-reviewed:** all 22 page components under `src/app/pages/*/`. Each one uses the shared `app-page-header` (confirmed by search), except the daily-closing page, which documents its own exception.
- **Rendered in a browser:** none. The user asked for no visual checks.

## Verified findings

| Priority | Finding | Root cause | Status |
|---|---|---|---|
| High (a11y) | Two icon-only close buttons on the business dashboard had no accessible name. | Written without `aria-label`. | **Fixed** (`Close dialog`). |
| High (consistency) | Pagination was split across pages (infinite scroll, load-more, numbered). | Built per page by different tools. | **Fixed earlier** (commit `9e3aef1e`, shared `PaginationBarComponent`). |
| Medium (tokens) | Component files still contain hex literals, mostly as `var(--token, #fallback)` fallbacks, e.g. `menu-page.component.ts` lines 945–953. | Fallbacks written before tokens existed. | **Open.** Changing them is a token-hygiene refactor. Each fallback has a token, so the visible colour is unchanged. |
| Medium (tokens) | `login-page.component.ts` redefines `--kb-color-foreground`, `--kb-color-muted-foreground`, `--kb-color-border`, `--kb-color-surface` locally (lines ~408–411). | Local theme override. | **Open.** Needs a check that the login page stays intentional. |
| Low (dead CSS) | `.menu-load-more-unused` in the Menu page. | Left after the pagination change. | Already removed; no references remain. |

## Palette

The palette was revised on 2026-10-25 and is locked in `docs/design/DESIGN_SYSTEM_FREEZE.md`. Orange `#FF851B` used outside the accent role conflicts with that doc. **No palette change was made.** Any change needs written justification under the freeze doc and your approval.

## Not yet audited

- Rendered layout at 100% zoom, 1024px and mobile widths.
- Keyboard focus order and focus rings across modals.
- Contrast of status chips and badges in the rendered DOM. The existing `design-tokens.spec.ts` covers the token pairs only.
- Loading, empty and error states on each page.
- Duplicate or conflicting styles in `bento.css` and `styles.css`.

## Verification

- `npx ng build --configuration development`: exit 0.
- `npx ng test --watch=false`: 270 of 270 pass, after the label fix.
