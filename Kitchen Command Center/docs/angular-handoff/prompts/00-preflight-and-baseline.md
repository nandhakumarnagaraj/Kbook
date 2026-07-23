# Batch 00 — Preflight & baseline

## Scope

Confirm you are inside the real KhanaBook Angular repository and produce a factual baseline of git, toolchain, and existing tests. **No source-code changes in this batch.**

## Inputs to inspect

- `docs/angular-handoff/preflight.md` — run every check.
- `docs/angular-handoff/README.md`
- `angular.json`, `package.json`, `tsconfig*.json`
- `.git`, current branch, remotes

## Actions

1. Execute every command in `preflight.md`, including STOP conditions.
2. Record Node / npm / Angular versions, chart lib, dialog lib, toast lib, CDK availability.
3. List available scripts verbatim from `package.json` (`build`, `test`, `lint`, `e2e`).
4. Count existing `*.spec.ts` files.
5. Create a migration branch (e.g. `chore/service-pass-migration`) if not already on one.

## Preservation rules

No API, guard, payload, or validation changes. No dependencies added. No source modified.

## Tests to run

- `git status`, `git log -5 --oneline`
- `node --version && npm --version`
- The exact script names discovered — do NOT run yet; only enumerate them.

## Required report

```
Batch: 00 Preflight & baseline
Repo: <name> @ <sha>
Branch: <name>
Angular root: <path>
Angular version: <major>
CDK: yes|no · Material: yes|no · Chart lib: <name|none> · Toast lib: <name|none>
Available scripts: build=<cmd>, test=<cmd>, lint=<cmd|NOT AVAILABLE>, e2e=<cmd|NOT AVAILABLE>
Existing spec count: <n>
Preflight verdict: PASS|FAIL
Known issues: <list or none>
Commit: none (analysis only)
```

## Stop conditions

- Any STOP in `preflight.md` triggers.
- The repo is still the Lovable React prototype.
- No `angular.json` located.

## Suggested commit

None — batch 00 produces a written report only.
