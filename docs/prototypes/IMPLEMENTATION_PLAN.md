# Prototype → live: implementation plan

How to move the agreed prototype states (`docs/prototypes/`) into the real Angular
web-admin. Ordered so the highest-severity, smallest change lands first.

Prototype reference: `docs/prototypes/index.html` (hub) → 15 pages on `_shared/proto.css` + `_shared/proto.js`.

---

## Phase 0 — approve the states (no code)

Agree the vocabulary from the prototype before touching the app:

| Decision | Prototype says |
|---|---|
| One load per surface owns `loading / loaded / error / empty` | shared `Proto` state machine |
| A failure with data on screen keeps the data + raises a stale banner | `Proto.staleBanner()`, "Couldn't update. … Showing figures from HH:MM. [Retry]" |
| A failure with no data replaces the view with a panel + one primary action | `Proto.errorPanel()` |
| Errors are announced (`role="alert"`, `aria-live="assertive"`); loading is `aria-busy` + `role="status"` | everywhere |
| Error toasts linger 7s; success toasts 3.5s | `Proto.toast()` |
| Failure copy is one sentence per kind (offline / 400 / 401 / 403 / 500) | hub "shared state vocabulary" table |
| Empty states always offer an action | `Proto.emptyBox(title, text, actionLabel, actionName)` |

Not in the product yet — decide keep/drop: Reports hourly tab, Platform search+filter+paging,
Inventory vendors/purchase orders, Businesses KPI tiles, Settings Merchant Agreement section,
Login mock accounts.

---

## Phase 1 — fix the repaint bug (do this first; it is the live defect)

**Measured on 2026-10-13 against the live API:** `POST /auth/login` → `400 {"error":"Invalid login ID or password"}`.
The component's fields updated (`loading:false, error:"Invalid login ID or password"`) but the DOM never
repainted: the button stayed on *"Signing in…"* disabled and `role="alert"` count stayed `0`.
Cause: `ChangeDetectionStrategy.OnPush` components assigning to **plain fields** inside async callbacks.
Signals repaint; plain fields do not.

1. Convert every async-mutated field on an OnPush component to a signal (or add `markForCheck()`):
   - `pages/login/login-page.component.ts` — `loading`, `error`, `googleLoading`, `googleError`, `forgotLoading`, `forgotError`, `loginSuccessMessage`
   - `pages/business-settings/password-change-section.component.ts` — `pwLoading`, `pwError`, `pwSuccess`
   - `pages/menu/menu-page.component.ts` — `loadError`, `loaded`
   - any others found by the Phase 5 guard
2. **Acceptance:** with a wrong password, the inline error renders, the button leaves "Signing in…",
   and it is usable again once the form is valid. Same for 500 and offline.
3. **Verification:** unit spec per component + the CDP probe used to find it (fill the form, submit,
   assert `aria-busy` is absent and the alert text matches the API sentence).

## Phase 2 — one error/loading component in the app

`core/components/api-state.component.ts` already exists (spinner + error banner + retry). Bring it up to the
prototype: title + message + meta, one primary action, offline/info variants, `role="alert"`.

Replace hand-rolled blocks with it:
- `pages/reports/reports-page.component.ts:358` — error rendered inside `class="panel loading"`
- `pages/platform-dashboard/platform-dashboard-page.component.ts:156,175,229,288` — four copies, same misuse
- `pages/business-settings/business-settings-page.component.ts:76` — same misuse
- `pages/orders/live-orders-tab.component.ts:43` — no retry, no `role="alert"`
- `pages/daily-closing/daily-closing-page.component.ts:107`, `pages/terminals/terminals-page.component.ts:105,290`, `pages/feature-flags/feature-flags-page.component.ts:35` — unify styling/copy
- `pages/business-dashboard/business-dashboard-page.component.ts:459` — keep its own retry, restyle to the shared panel

**Acceptance:** no error content rendered inside a loading class anywhere; every error has exactly one action.

## Phase 3 — global failure feedback

`core/auth/jwt.interceptor.ts` currently logs network (`0`) and 5xx to `console.error` only.
- 401 → notify ("Your session has expired. Please sign in again.") **before** redirecting to `/login`
- `0` → "No internet connection…"; 5xx → "The server could not complete this request…"
- **Exclude `/auth/*` requests** so a failed sign-in does not double-message (the login card already shows it)
- `core/services/toast.service.ts` — error duration 7000ms, success 3500ms

## Phase 4 — consistency + a11y sweep

- Loading: skeleton that matches the layout, `aria-busy`, `role="status"`, visually-hidden "Loading…"
- Stale-refresh pattern on every refetch (dashboard, reports, platform tabs, settings save)
- Success: toast everywhere; retire the inline-only success in `password-change-section.component.ts:45`
- Empty states: use `shared/empty-state.component.ts` everywhere (platform + live-orders have their own)
- Keep the prototype's mobile fix in mind: the fixed 236px rail overflowed a 420px viewport

## Phase 5 — tests and guardrails so it cannot regress

- Extend the suite (currently 107 tests / 18 spec files) with one state spec per page: loading renders
  skeleton, error renders alert + retry that refetches, empty renders empty state, success raises toast.
- Add a repo guard (same style as `.github/workflows/guards.yml`) that fails on:
  1. a plain field assigned inside `subscribe(`/`.then(` in a component declaring OnPush,
  2. `class="panel loading"` used on an element that also has `role="alert"`.
- Keep `npm run build` + `ng test` green; the existing CI Web Admin job covers this.

## Phase 6 — rollout

Ship in the Phase-1 order: login (the user-visible hang) → owner console pages → platform pages.
Each page is independent; no shared API or schema change is required anywhere in this plan.

## What is explicitly NOT in this plan

- No backend change. Every fix is client-side presentation and change-detection.
- The prototype's invented content (flag keys, mock accounts, extra tabs) is not a spec for new features.
- The prototype reads no API; nothing here proves an endpoint's shape — the app's own services remain the
  source of truth.
