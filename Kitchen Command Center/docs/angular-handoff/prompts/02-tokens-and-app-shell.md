# Batch 02 — Tokens, app shell & shared primitives

## Scope

Introduce the Service Pass design tokens and the shared presentational primitives the rest of the migration will depend on. **Do NOT migrate individual pages in this batch.**

## Inputs to inspect

- `docs/angular-handoff/_khanabook-tokens.css`
- `docs/angular-handoff/components/kb-app-shell.md`, `kb-sidebar.md`, `kb-mobile-drawer.md`, `kb-topbar.md`, `kb-page-header.md`, `kb-button.md`, `kb-status-badge.md`, `kb-empty-state.md`, `kb-error-state.md`, `kb-skeleton.md`, `kb-dialog.md`, `kb-drawer.md`
- Existing global styles, existing shell / sidebar / topbar / dialog / drawer components.

## Actions

1. Confirm Batch 01 committed. Reload the migration inventory.
2. Adapt `_khanabook-tokens.css` into the Angular style architecture. Produce a token mapping report:
   `Handoff token | Angular destination | Existing equivalent | Action (reuse | remap | new)`. Do NOT copy the file blindly. Reconcile with any existing Material theme.
3. Reuse the existing app shell / sidebar / topbar if present; refactor styles to consume `--kb-*` tokens. Add a mobile drawer only if the existing shell lacks one.
4. Introduce presentation-only shared components ONLY when there is no existing equivalent. Follow specs; use CDK `Overlay` + `FocusTrap` for dialog / drawer foundations if `@angular/cdk` is present.
5. Font loading: Inter from a non-blocking `<link rel="preload" as="style">` + `<link rel="stylesheet">`, or self-hosted with `font-display: swap`. Do not block first paint.

## Preservation rules

- Existing routes / guards / API services / payloads: **Unchanged**.
- No new HTTP calls introduced.
- No page component logic touched.

## Tests to run

- `npx tsc --noEmit` (or the equivalent typecheck).
- `npm test -- --watch=false` — narrowed to shared component specs if the suite supports pattern filtering.
- `npm run build` — must succeed.
- Manual keyboard smoke on the app shell: Tab through sidebar / topbar, Escape closes any newly introduced drawer, focus returns.

## Required report

```
Batch: 02 Tokens & shared primitives
Routes migrated: none (foundation batch)
Files changed: <n> — <list styles + shared component paths>
Shared components added: <list>
Shared components updated: <list>
API services changed: Unchanged
Payload builders changed: Unchanged
Routes or guards changed: Unchanged
Validation changed: Unchanged
Type-check: PASS|FAIL|BLOCKED
Unit tests: PASS|FAIL|NOT AVAILABLE|BLOCKED
Build: PASS|FAIL|BLOCKED
Runtime tested: STATICALLY REVIEWED (foundation batch — page smoke deferred to Batch 03+)
Responsive tested: NOT TESTED (per-page smoke starts Batch 03)
Accessibility tested: PARTIALLY TESTED (shell focus + Escape only)
Known issues: <list or none>
Commit: style(web-admin): add Service Pass tokens and shared shell primitives
```

## Stop conditions

- Token adoption would break existing Material theme without a documented remediation.
- Font loading blocks first paint.
- Any shared primitive would encode business logic — move that logic back into the domain component.

## Suggested commit

`style(web-admin): add Service Pass tokens and shared shell primitives`
