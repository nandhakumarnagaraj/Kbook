# KhanaBook Web Admin — UI/UX Handoff

> **Design-only deliverable.** This document, the sibling markdown files in
> `/docs`, and the React prototype under `/proto` are a reference for the
> Angular implementation team. Do not treat the React prototype as production
> code. The production application remains the existing Angular Web Admin;
> the redesign preserves its routes, guards, services, and API contracts.

## 1. What was delivered

| Category                        | Count | Notes                                                                                  |
| ------------------------------- | ----: | -------------------------------------------------------------------------------------- |
| Prototype screens               |    12 | Login, Forgot/OTP, OWNER (7), SHOP_ADMIN (1), KBOOK_ADMIN (2), Design System reference |
| Drawer flows                    |     6 | Order details, Menu add/edit, Staff add/edit, Business details                         |
| Dialogs                         |    20 | See `khanabook-component-mapping.md`                                                   |
| Shared components               |    15 | See §4                                                                                 |
| State variants                  |     9 | ready, loading, empty, error, success, validation, disabled, confirmation, restricted  |
| Roles covered                   |     3 | OWNER, SHOP_ADMIN, KBOOK_ADMIN                                                         |
| Existing Angular routes covered |    11 | `/login`, `/limited-access`, `/business/*` (7), `/admin/*` (2)                         |
| Machine-readable tokens         |     1 | `src/proto/design-tokens.json`                                                         |

## 2. Design direction (locked)

**Base:** Concept 1 — _Service Pass_ (Balanced SaaS).
**Accent:** Concept 3 — _Warm Kitchen_, applied **only** to the OWNER dashboard.

| Surface                  | Rules                                                                                                                                                                                                         |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Foundation               | Warm off-white background (`#FAF7F2`), espresso sidebar (`#2A1F17`), saffron primary (`#E87A1E`), pure white surfaces, compact operational tables, unified status system, **Inter** everywhere                |
| **OWNER dashboard only** | May add: `--gradient-hero` on the single hero KPI, `--gradient-cream` on the Setup-progress card, `--shadow-warm`, and **Instrument Serif** for the welcome heading + hero KPI value + Setup-progress heading |
| **OWNER — other pages**  | Neutral SaaS: no gradient, no serif — Orders, Reports, Menu, Staff, Terminals, Marketplace all stay operational                                                                                               |
| **SHOP_ADMIN**           | Neutral, operational, single-screen device console — no hospitality decoration                                                                                                                                |
| **KBOOK_ADMIN**          | Executive: neutral, dense, structured — no serifs, no gradients, no hospitality accents                                                                                                                       |

Red is reserved for **destructive actions, severe failures, and critical warnings** — never as a decorative accent.

## 3. Screen inventory

Every route below is live at `/proto/*` and exposes its supported states via a fixed pill switcher (top or bottom).

### Authentication

- `/proto/login` — states: **ready · loading · error · success · disabled**
- `/proto/forgot` — steps: **request → otp → reset → done**

### OWNER

- `/proto/owner/dashboard` — states: **ready · loading · empty · error**
- `/proto/owner/reports` — states: **ready · loading · error**
- `/proto/owner/orders` — list states + drawer (order details) + refund dialog with **default · confirm · submitting · success · error · duplicate**
- `/proto/owner/menu` — list + add/edit drawer + delete confirm + OCR upload with **idle · validation_error · uploading · processing · success · partial · failed**
- `/proto/owner/staff` — directory + add/edit drawer + temporary-password dialog + deactivate confirm
- `/proto/owner/terminals` — tabs: Fleet · Requests · Recovery; sensitive actions: rename, deactivate, reject, approve-blocked-by-limit, recovery-input, recovery-token
- `/proto/owner/marketplace` — Zomato + Swiggy cards; save states: **idle · saving · saved · validation_error**

### SHOP_ADMIN

- `/proto/shop/terminals` — same Terminal console component, sidebar limited to Devices only

### KBOOK_ADMIN

- `/proto/admin/dashboard` — platform overview, states: **ready · loading · empty · error · restricted**
- `/proto/admin/businesses` — directory + drawer + activate/suspend dialogs

### Reference

- `/proto/system` — design system, states, Angular mapping, migration matrix

## 4. Shared component set (15)

`AppShell` (sidebar + mobile drawer + topbar + page header) · `KpiCard` · `TrendChart` · `Badge` (with `OrderStatusBadge` / `DeviceStatusBadge` / `MarketplaceStatusBadge` presets) · `Drawer` · `Dialog` · `Btn` · `EmptyState` · `ErrorState` · `Skeleton` · `INR` currency · `StateSwitcher` · **Form field pattern** · **Secret input pattern** · **Copy button pattern** (last three are patterns codified in `proto.owner.marketplace.tsx` and `proto.owner.staff.tsx` — Angular team ships them as reusable components).

## 5. Sensitive-action pattern

All destructive actions must follow this contract:

1. Trigger uses `outline` danger variant (never same colour as Save).
2. Opens `Dialog` with the **"Sensitive action"** strip at the top.
3. Includes the record identifier, amount, or entity name.
4. Financial actions add an explicit **Review → Confirm** step (see refund).
5. Confirm button label carries the verb: _Refund · Suspend · Deactivate · Delete · Reject · Recover_.
6. Confirm button **disables + shows spinner** during submit — duplicate submission blocked.
7. Success transitions to a success view with a reference id (`RFD-…`, `RCV-…`).
8. Error surfaces an inline banner with the error code, a "no charge occurred" statement, and a retry.

Full inventory of sensitive actions covered:

| Action                    | Screen      | Confirm             | Success | Error | Duplicate-guard   |
| ------------------------- | ----------- | ------------------- | ------- | ----- | ----------------- |
| Manual refund             | Orders      | ✓ two-step          | ✓       | ✓     | ✓                 |
| Delete menu item          | Menu        | ✓                   | –       | –     | –                 |
| Deactivate staff          | Staff       | ✓                   | –       | –     | –                 |
| Deactivate terminal       | Terminals   | ✓                   | –       | –     | –                 |
| Reject activation request | Terminals   | ✓ (reason required) | –       | –     | –                 |
| Approve blocked by limit  | Terminals   | –                   | –       | ✓     | ✓                 |
| Recover terminal          | Terminals   | ✓ (token)           | ✓       | –     | ✓ (10-min expiry) |
| Suspend business          | Businesses  | ✓ (reason required) | –       | –     | –                 |
| Replace marketplace creds | Marketplace | ✓                   | ✓       | ✓     | –                 |

## 6. Marketplace credential behaviour

Prototype clearly distinguishes:

- **Stored (masked)** — `••••••••7f2a` with a `Replace` button; never editable directly.
- **Replace mode** — masked value swaps for an empty input with an `Eye` reveal toggle; a `Cancel` reverts to the stored/masked state without saving.
- **Newly entered secret** — editable, revealable, may be saved.
- **Validation error** — inline banner citing the marketplace's rejection; keeps entered value intact for correction.
- **Successful save** — inline success + test-order confirmation; input returns to stored/masked mode.

**Masked values (`••••••••`) are never rendered as editable credential text.** This rule is enforced in `proto.owner.marketplace.tsx` and codified in `/proto/system` §7 Component reference and `khanabook-component-mapping.md`.

## 7. Handoff files

| File                                      | Purpose                                                       |
| ----------------------------------------- | ------------------------------------------------------------- |
| `docs/khanabook-ui-handoff.md`            | This document (executive overview)                            |
| `docs/khanabook-design-tokens.md`         | Human-readable token reference (mirrors `design-tokens.json`) |
| `docs/khanabook-component-mapping.md`     | Angular component-mapping table                               |
| `docs/khanabook-page-migration-matrix.md` | Route-by-route migration matrix                               |
| `docs/khanabook-responsive-spec.md`       | Breakpoint audit + patterns                                   |
| `docs/khanabook-accessibility-spec.md`    | A11y contract for the Angular build                           |
| `src/proto/design-tokens.json`            | Machine-readable tokens (design-tokens.community format)      |
| `/proto/system` (live route)              | Interactive design-system reference                           |
| `/proto` (live index)                     | Interactive screen inventory                                  |

## 8. What this handoff is not

- Not a rewrite of the Angular app.
- Not connected to `https://kbook.iadv.cloud/api/v1` — no production credentials, no API calls, no live data. All sample data lives in `src/proto/data.ts`.
- Not a claim of accessibility compliance — the prototype implements the a11y **contract** (semantic buttons, labelled inputs, focus rings, Escape-to-close, focus restore, status text + icon on every badge). The Angular team is responsible for keyboard-navigation, screen-reader, and colour-contrast audits against the shipped implementation.
- Not a scope expansion — no new backend endpoints or fictional metrics.

## 9. Known limitations

- Sparkline / trend chart is a lightweight SVG stub; the Angular build should use its existing chart library, keeping the same shape and colour treatment.
- Dark mode tokens are defined but the prototype does not include a dark theme surface (reserved for a future "Night Service" variant).
- The prototype does not include a live keyboard-navigation demo — see the a11y spec for the required contract.
- No i18n scaffolding — all copy is en-IN; the Angular build owns i18n.
- Prototype uses TanStack Router file-based routes; production routes are Angular Router paths and remain unchanged.
