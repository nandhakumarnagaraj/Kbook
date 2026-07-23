# Batch 11 — Regression & final audit

## Scope

Full-suite verification of the completed migration. Produce the final report defined in the master prompt.

## Inputs to inspect

- `docs/angular-handoff/angular-acceptance-checklist.md`
- All previous batch reports.
- Full application in a safe (non-production) environment.

## Actions

1. Confirm Batch 10 committed and passing.
2. Run the full test suite: `npx tsc --noEmit`, `npm test -- --watch=false`, `npm run lint` (if available), `npm run build`, `npm run e2e` (if available).
3. Walk every migrated route in a browser for each applicable role (OWNER, SHOP_ADMIN, KBOOK_ADMIN):
   - Capture network requests for a smoke journey on each route.
   - Compare each captured request against the Batch 01 API baseline — endpoint / method / query / payload / headers must be identical.
4. Run the responsive matrix on every migrated route: 320 / 375 / 430 / 768 / 1024 / 1366 / 1440 / 1920.
5. Run the accessibility matrix: keyboard-only walkthrough of each route; screen-reader spot-check of dialogs, drawers, forms; contrast spot-check of primary CTAs, focus rings, status badges; verify `prefers-reduced-motion` disables non-essential motion.
6. Confirm removal: no state-switcher pill, no prototype route index, no Lovable mock data, no React / TanStack deps, no fabricated metrics.
7. Fill and commit the final audit report.

## Preservation rules

Same as prior batches. If any regression is found, do not paper over it — file a follow-up ticket and mark the verdict `REGRESSION FOUND` or `VERIFIED WITH MINOR ISSUES` with a documented list.

## Tests to run

Whatever `package.json` supports. Report each as `PASS`, `FAIL`, `NOT AVAILABLE`, or `BLOCKED`.

## Required report

Use the final report shape from the master prompt (§20):

```
Git state
- Branch: <name>
- Commit range: <first>..<last>
- Working-tree status: clean|dirty (why)
- Changed files: <count>
- New dependencies: <list or none>

Route inventory (route | component | guards | roles): <table>
API equivalence (before | after | evidence | result): <table>
Page migration status (route | UI | API | validation | responsive | runtime): <table>
Test results:
  - Dependency install: PASS|FAIL
  - Type-check: PASS|FAIL
  - Unit tests: PASS|FAIL|NOT AVAILABLE
  - Build: PASS|FAIL
  - Lint: PASS|FAIL|NOT AVAILABLE
  - E2E: PASS|FAIL|NOT AVAILABLE
  - Browser network checks: PASS|PARTIALLY TESTED|NOT TESTED
Accessibility:
  - Keyboard: PASS|FAIL|PARTIALLY TESTED
  - Focus: PASS|FAIL
  - Dialogs: PASS|FAIL
  - Drawers: PASS|FAIL
  - Forms: PASS|FAIL
  - Contrast: PASS|FAIL|PARTIALLY TESTED
  - Reduced motion: PASS|FAIL
Known limitations: <list>

Final verdict: VERIFIED | VERIFIED WITH MINOR ISSUES | NOT VERIFIED | REGRESSION FOUND
```

## Stop conditions

- Any API equivalence row fails.
- Any critical journey (login, refund, OCR upload, terminal recovery, marketplace save, admin suspend) fails runtime testing.
- Any prototype-only artifact is still present.

Do NOT record `VERIFIED` unless critical flows, network requests, role restrictions, responsive behavior, and accessibility were actually exercised — not just source-reviewed.

## Suggested commit

`test(web-admin): capture Service Pass migration audit and regression evidence`
