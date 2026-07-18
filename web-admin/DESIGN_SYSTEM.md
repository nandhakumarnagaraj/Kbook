# KhanaBook Web-Admin Design System & Build Conventions

> Distilled from shared design-fundamentals + "How to Prompt" references, adapted to
> this project's **Angular 18 standalone + plain CSS tokens** reality (NOT Lovable,
> NOT Tailwind/shadcn/Framer Motion). Use this as the single source of truth for UI
> work so the app stays consistent and accessible.

## 1. Planning Discipline (from the prompting guides)

- **Plan in chat, build one feature at a time.** Never scaffold five screens at once.
  Lock scope → implement → verify (`tsc` + `ng build`) → move on.
- **Prompt anatomy for any UI task:** `Action + What + Details + Context`.
  - *Action*: add / restyle / fix / audit
  - *What*: the specific component or page
  - *Details*: layout, states, copy, data fields
  - *Context*: which role (OWNER / SHOP_ADMIN / KBOOK_ADMIN), which breakpoint, tokens to reuse
- **Design-system-as-context:** every component must reuse tokens from `src/styles.css`
  (`.panel`, `.page-hero`, `.stat-card`, `.data-table`, `.chip`, `.modal-box`, `.toast`,
  `.primary-btn`/`.ghost-btn`, `.field-control`). Do NOT hardcode hex/spacing.
- **State coverage is mandatory** — see §4.

## 2. Design Tokens (the 60-30-10 rule)

Warm earthy palette is the brand. Apply 60/30/10:
- **60% background/surface** — `--bg`, `--panel`, `--panel-strong`
- **30% supporting** — `--line`, `--line-strong`, `--muted`, text `--ink`
- **10% accent** — `--brand` / `--brand-deep` (CTA, active), `--accent` (success/teal),
  `--danger` (errors/destructive)

Additive rules:
- Never introduce a new color outside `:root`. Extend tokens, don't bypass them.
- One primary action per view (the `--brand` gradient `.primary-btn`). Secondary =
  `.ghost-btn`. Destructive = `.danger-btn`.
- Typography: system stack already set in `:root`; use `clamp()` for fluid hero sizes
  (see `.page-hero h2`). No fixed px font sizes for headings.

## 3. Layout & Responsive

- Shell = `.page-shell` (grid, gap). Every page: `.page-hero` → filter/action panel →
  `.table-wrap` / `.stats-grid`.
- **Sidebar** (`sidebar-layout.component.ts`): hamburger + off-canvas drawer `<1024px`,
  sticky sidebar `≥1024px`. Keep `aria-expanded` / `aria-controls` / labelled toggle.
- Breakpoints already in `styles.css`: 900px (compact) and 480px (modal stack). Add new
  ones only with justification.
- Tables always inside `.table-wrap` (`overflow-x: auto`) — never let a table break layout.
- `min-height: 44px` on all interactive controls (tap target / a11y).

## 4. Required UI States (loading / empty / error / success)

Every data surface must handle all four. Reuse existing primitives:
- **Loading**: `<div class="panel loading">Loading…</div>` or `.spinner`. No indefinite
  hang — pair `HttpClient` calls with `catchError` + retry so the UI never freezes.
- **Empty**: centered message inside the surface, e.g. "No orders match the current
  filters." (use the existing business-dashboard empty-state pattern).
- **Error**: inline `.alert.error` + `GlobalToastComponent` for transient failures
  (e.g. failed row drill-down). Surfaces must degrade gracefully, not white-screen.
- **Success**: `GlobalToastComponent` confirmation; never rely on silent state change.

## 5. Components & Interaction

- **Modals**: use `.modal-backdrop` + `.modal-box` (shared). Buttons in `.modal-actions`
  stack full-width `<480px`. Prefer `ConfirmDialogComponent` over native `confirm()`.
- **Buttons**: `.primary-btn` (gradient, one per view), `.ghost-btn`, `.danger-btn`,
  `.success-btn`. All `border-radius: 12px`, `min-height: 44px`, lift on hover
  (`translateY(-1px)`), reduce on `:active`.
- **Forms**: `.field-control` / `.field-select`; focus ring = `box-shadow 0 0 0 3px`
  brand tint. Labels uppercase muted (`.filter-group label`).
- **Tables**: `.data-table`, sticky `thead` (blur), hover tint, `.clickable-row` with
  `:focus-visible` outline for keyboard users.
- **Chips**: `.chip` / `.chip.success` / `.chip.warn` / `.chip.danger` for status badges.
- **Icons**: emoji/unicode only (⚠️, 🙈, 👁️) — no icon library. Prefer short text labels
  (On/Off/Edit/Delete) over cryptic emoji in action columns.

## 6. Accessibility (non-negotiable)

- Keyboard-operable rows/links: `tabindex="0"`, `role`, `aria-label`, `keydown.enter`.
- Focus visible everywhere (`.clickable-row:focus-visible`, field focus rings).
- `prefers-reduced-motion` guard already global in `styles.css` — keep animations subtle
  (≤0.2s, ease) so the guard is meaningful.
- Color contrast: tokens chosen for AA on `--bg`. Don't pair `--muted` text on
  `--brand-soft` without checking.
- Semantic HTML + labelled controls; modals trap focus and set `aria-modal`.

## 7. Animation & Motion

- Subtle only: 0.15–0.2s transitions on hover/active; `.spinner` for async.
- No entrance choreography unless it respects `prefers-reduced-motion`.
- Micro-interactions (card lift, button press) are enough — avoid scroll-jacking.

## 8. Performance & Hygiene

- Client-side pagination/filtering (existing pattern) — slice arrays in memory.
- Reuse `toSignal()` for Observables; avoid manual subscribe leaks.
- No new CSS framework. Extend `styles.css` tokens; co-locate page-only styles in the
  component when they don't belong in the shared sheet.

## 9. Role Model (backend source of truth)

`UserRole`: `OWNER`, `SHOP_ADMIN`, `KBOOK_ADMIN`. Guard via `roleGuard` + `AuthService`
sidebar computation. ⚠️ Known inconsistency: limited-access copy lists only
KBOOK_ADMIN/OWNER while backend still issues SHOP_ADMIN — resolve before shipping
role-related features.

## 10. Verification Checklist (run after every UI change)

1. `npx tsc --noEmit` — no type errors.
2. `ng build --configuration development` — compiles.
3. Manually confirm: loading / empty / error / success states render.
4. Resize to <1024px (sidebar drawer) and <480px (modal stack) — no breakage.
5. Tab through interactive elements — visible focus, no traps.
6. No hardcoded hex/spacing outside `:root`/tokens.
