# Preflight — Confirm you are inside the real KhanaBook Angular repository

> **Passing this preflight only confirms the correct repository is available. It does NOT prove the migration is safe.** Every batch that follows still has to inspect real code, preserve real APIs, and run real tests before it commits anything.

Run this file before touching source. If any **STOP** condition triggers, halt and report — do not fabricate route, API, guard, or component inventories.

## 0. Are you actually in the KhanaBook Angular repo?

```bash
git status
git branch --show-current
git remote -v
```

Expected: a KhanaBook remote (e.g. `origin git@…:khanabook/…`), a clean or intentional working tree, a known branch (usually `main` / `develop` / your migration branch).

**STOP** if:

- `git remote -v` shows a Lovable / TanStack / prototype repo (`tanstack_start_ts`, anything under `lovable-projects`, or the Lovable design prototype). This is the wrong repo — the handoff package must be copied out of Lovable into the real Angular repo before you can proceed.
- No git repository is detected.

## 1. Angular project detection

```bash
find . -maxdepth 3 -name angular.json
find . -maxdepth 4 -name package.json
rg -n '"@angular/core"' .
```

Record the located `angular.json` path (e.g. `./angular.json` or `./web-admin/angular.json`) — treat that directory as the Angular application root for every subsequent command.

**STOP** if:

- No `angular.json` is found.
- No `package.json` contains `"@angular/core"`.
- More than one Angular workspace is present and it isn't clear which one is the Web Admin. Ask the user before proceeding.

## 2. Application source directory

Using the located Angular root, verify the source tree exists:

```bash
ls <angular-root>/src
ls <angular-root>/src/app
```

**STOP** if `src/app` (or the project's equivalent per `angular.json` `projects.*.sourceRoot`) does not exist.

## 3. Routing, guards, interceptors, services

```bash
rg -n "provideRouter|RouterModule\.forRoot|Routes\s*=|loadChildren|loadComponent" <angular-root>/src
rg -n "CanActivate(Fn)?|CanMatch(Fn)?|HttpInterceptor(Fn)?|HTTP_INTERCEPTORS" <angular-root>/src
rg -n "HttpClient|http\.(get|post|put|patch|delete)" <angular-root>/src
rg -n "apiBaseUrl|API_BASE_URL|/api/v1|/auth/|/business/|/admin/|/menus/" <angular-root>/src
```

Confirm at least one match for each concept (routing, guards, HTTP client). If any concept is missing, note it — the migration inventory in Batch 1 will need to explain the gap before code changes start.

**STOP** if none of these searches return results (this is not the Angular Web Admin).

## 4. Handoff package present

```bash
ls docs/angular-handoff/
ls docs/angular-handoff/components/
ls docs/angular-handoff/page-migrations/
ls docs/angular-handoff/prompts/
```

Expected top-level files:

- `README.md`
- `_khanabook-tokens.css`
- `angular-migration-inventory-template.md`
- `angular-acceptance-checklist.md`
- `prototype-angular-mapping.md`
- `preflight.md` (this file)

**STOP** if `docs/angular-handoff/` is missing — copy the folder from the Lovable design project before continuing.

## 5. Working-tree and branch discipline

```bash
git status
git log -5 --oneline
```

**STOP** if:

- The working tree has uncommitted user changes you do not understand. Ask before touching them; do not run `git reset --hard` or `git clean -fd`.
- You are on `main`/`master` without an explicit migration branch. Create one (e.g. `chore/service-pass-migration`) before writing code.

## 6. Toolchain

```bash
node --version
npm --version
```

Compare to `engines` in `package.json` (if present). Mismatched Node major versions can cause silent build differences — fix the environment before proceeding.

## 7. Available scripts

```bash
cat <angular-root>/package.json | sed -n '/"scripts"/,/}/p'
```

Record the actual command names for:

- Type-check (usually `npm run build` — Angular CLI runs a typecheck as part of build; or `npx tsc --noEmit` if a plain tsconfig is set up).
- Unit tests (`npm test`, `ng test`, `karma`, `jest`).
- Lint (`ng lint`, `npm run lint`).
- E2E (`ng e2e`, `cypress`, `playwright`, or none).
- Build (`ng build`, `npm run build`).

Any script that is not present must be reported as `NOT AVAILABLE` in downstream batch reports — not silently skipped.

## 8. Existing UI infrastructure

```bash
rg -n "@angular/cdk|@angular/material" <angular-root>/package.json
rg -n "MatDialog|CdkOverlay|CdkPortal|A11yModule|FocusTrap" <angular-root>/src
rg -n "chartjs|ng2-charts|highcharts|@swimlane/ngx-charts|d3" <angular-root>/package.json
rg -n "SnackBar|ToastrService|hot-toast|ngx-toastr" <angular-root>/package.json <angular-root>/src
```

Record which of these already exist — the migration MUST prefer the existing dialog / overlay / chart / toast system rather than introduce a new one.

## 9. Existing tests

```bash
find <angular-root>/src -name "*.spec.ts" | head -20
```

Record the count and framework. Migration must not delete existing tests. Any test that breaks because of a presentation change needs to be updated inside the same batch that made the change.

## 10. Preflight verdict

Only proceed to `prompts/01-analysis-and-inventory.md` when **every** section above passes. Write a short preflight report at the top of your migration branch's changelog with:

```
Preflight
- Repo: <name> @ <sha>
- Angular root: <path>
- Angular version: <major>
- CDK present: yes|no
- Material present: yes|no
- Chart lib: <name or none>
- Toast lib: <name or none>
- Available scripts: build=<cmd>, test=<cmd>, lint=<cmd|NOT AVAILABLE>, e2e=<cmd|NOT AVAILABLE>
- Existing spec count: <n>
- Branch: <name>
- Handoff package: present
Verdict: PASS
```

If verdict is `FAIL`, stop and ask the user for direction.
