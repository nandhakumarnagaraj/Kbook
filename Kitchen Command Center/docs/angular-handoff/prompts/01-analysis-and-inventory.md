# Batch 01 — Analysis & inventory

## Scope

Fill the migration inventory using **actual repository evidence** and produce the API baseline. **No source-code changes in this batch.**

## Inputs to inspect

- `docs/angular-handoff/angular-migration-inventory-template.md`
- `docs/angular-handoff/page-migrations/*.md`
- `docs/angular-handoff/prototype-angular-mapping.md`
- Application routing files, page components, shared components, services, DTOs, guards, interceptors.

## Actions

1. Confirm Batch 00 passed.
2. For every production route below, record: existing Angular component, existing service methods, existing guards, existing DTOs / payload builders, existing validators, existing tests. **Do not guess file names — search the repo.**
   - `/login`, `/limited-access`
   - `/business/dashboard`, `/business/reports`, `/business/orders`, `/business/menu`, `/business/staff`, `/business/terminals`, `/business/marketplace`
   - `/admin/dashboard`, `/admin/businesses`
3. Build the HTTP inventory:
   `Feature | Service.method | HTTP method | Relative endpoint | Payload type | Response type | Component usages`.
4. Document: auth token storage, `Authorization` header injection, 401 behavior, role guards, login redirection, logout, date query construction, marketplace secret handling, OCR polling lifecycle, duplicate-submit protection.
5. Produce the component reuse plan:
   `Existing component | Reuse | Refactor (presentation only) | Replace | Business logic retained | Tests required`.
6. Cross-check the handoff:
   - Reports prototype: `/proto/owner/reports` **exists** in the design prototype. Reference `page-migrations/04-reports.md` alongside it.
   - Note any other conflict or ambiguity in a "Handoff conflicts" section.

## Preservation rules

Unchanged. No source edits, no dependency changes.

## Tests to run

- `rg` searches per `preflight.md` §3 and §8.
- Do NOT run `npm test` / `build` yet; that starts in Batch 02.

## Required report

```
Batch: 01 Analysis & inventory
Route inventory: <filled table or link to committed markdown>
HTTP inventory: <filled table>
Component reuse plan: <filled table>
Auth / interceptor / guard notes: <summary>
Marketplace secret handling notes: <summary>
OCR polling notes: <summary>
Handoff conflicts: <list or none>
Type-check: NOT TESTED (analysis batch)
Unit tests: NOT TESTED
Build: NOT TESTED
Runtime tested: NOT TESTED
Responsive tested: NOT TESTED
Accessibility tested: NOT TESTED
Known issues: <list>
Commit: docs(web-admin): capture Service Pass migration inventory and API baseline
```

## Stop conditions

- Any production route cannot be located in the Angular repo — ask the user before inventing a path.
- Any listed service has no discernible caller — flag it, do not assume.
- Any DTO or guard is unclear — do not proceed to Batch 02 until the ambiguity is resolved.

## Suggested commit

`docs(web-admin): capture Service Pass migration inventory and API baseline`
(only the inventory markdown files change).
