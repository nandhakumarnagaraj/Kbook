# KhanaBook Docs

All project documentation lives under `docs/`. Two kinds of content live here, and the
difference matters when you cite something:

- **Living docs** — kept current, corrected in place. If a living doc disagrees with the code, it
  is a bug in the doc.
- **Evidence** — dated snapshots that record what was true on a date. Never edited retroactively
  (see `evidence/README.md`).

## Start here

| I want to… | Read |
|---|---|
| Understand the product | `product/PRODUCT.md` → `product/FEATURE.md` → `product/PROBLEM.md` |
| Change code safely | `engineering/ARCHITECTURE.md` (blast-radius map) → `engineering/SYNC_CONTRACT.md` if the change touches data |
| Know who can do what | `product/ROLES_AND_PERMISSIONS.md` → `engineering/SECURITY.md` |
| Ship it | `runbooks/DEPLOYMENT.md` → `qa/TESTING.md` |
| Compare us to competitors | `product/COMPETITORS.md` (entry point; the dated teardowns are in `evidence/rnd/`) |
| Work the Easebuzz integration | `integrations/easebuzz/EASEBUZZ.md` |

## Living docs

| Folder | Contents |
|---|---|
| `meta/` | Agent and contributor rules: `AGENTS.md`, `CLAUDE.md`, `ANDROID_UI_RULES.md`, `KNOWN_ISSUES.md`, `SOLO_ENGINEERING_BOUNDARIES.md`, `LOCAL_DEV_BOUNDARIES.md`, `KHANABOOK_DEEP_ANALYSIS.md` |
| `engineering/` | Canonical engineering docs: `ARCHITECTURE.md` (blast radius), `SYNC_CONTRACT.md` (sync rules), `DATA_MODEL.md` (Room + Postgres), `SECURITY.md` (secrets + posture), `VERTICAL_SLICES_BLUEPRINT.md` (package restructure rules), `CREDENTIAL_ROTATION_PLAN.md`, `SECURITY_ROTATION_REQUIRED.md` (active action item) |
| `product/` | `PRODUCT.md` (About KhanaBook), `FEATURE.md` (verified inventory), `PROBLEM.md` (problem statement + open issues), `ROLES_AND_PERMISSIONS.md`, `COMPETITORS.md` (competitor entry point), `MARKETPLACE_INTEGRATIONS.md` (Swiggy/Zomato not built + legacy schema), `DESIGN.md`, `POST_V2_FEATURE_SUGGESTIONS.md`, `competitor-uiux-analysis.md` (raw UX benchmark), `replit-redesign-prompt.md` |
| `qa/` | `TESTING.md` (suites, commands, CI gates), `PRE_MARKETING_5POS_ACCEPTANCE_SUITE.md`, `STAGING_SETUP_RUNBOOK.md`, `DISTRIBUTED_STATE_PRODUCT_DECISIONS.md`, `v97-backfill-dry-run.sql` |
| `runbooks/` | `DEPLOYMENT.md` (build → deploy → rollback), `KYC_EXPOSURE_REMEDIATION.md`, `PRODUCTION_REMEDIATION_HANDOFF_2026-09-27.md` |
| `api/` | `API_REFERENCE.md` (endpoint map) and `api-docs.json` |
| `design/` | Design system: `DESIGN_SYSTEM.md`, `DESIGN_SYSTEM_FREEZE.md`, `LayoutGuidelines.md`, `ResponsiveLayoutMigration.md`, `KHANABOOK_RESPONSIVE_DESIGN_SPEC.md`, `RESPONSIVE_SYSTEM_ROADMAP.md`, `MENU_ITEM_MODEL_INDIA_FIT_GAP.md` (menu/variant/pricing model + India fit-gap) |
| `integrations/easebuzz/` | Easebuzz integration: **`EASEBUZZ.md` (start here)** — lifecycle, code map, config, gaps; then `easebuzz-sdk-plan.md`, `sub-merchant-apis.md`, `easebuzz-live-test-plan.md`, Postman collection |
| `integrations/easebuzz-review/` | External review package (`README.md` first), `OPS_RUNBOOK.md`, `PRODUCTION_VALIDATION_RUNBOOK.md`, diagrams, review email, `external-share/` |
| `android/` | Android checklists & audits: `PLAYSTORE_RELEASE_CHECKLIST.md`, `MIGRATION_TESTING_PLAYBOOK.md`, `ACCESSIBILITY_CHECKLIST.md`, `DEVICE_COVERAGE_AUDIT.md`, `EDGE_TO_EDGE_BOTTOM_BAR.md`, conformance/redesign audits |
| `planning/` | Active plans only: `PLAN.md`, `FIX_PLAN.md`, `ROOT_CAUSE_LOG.md`, `OWNER_EDIT_BILL_AND_CONFIG_PLAN.md`, `BILLINGVM_SPLIT_PLAN.md`, `SYNC_PROCESSOR_SPLIT_PLAN.md`, `NEWBILLSCREEN_SPLIT_PLAN.md`, `SAAS_SLICE_*` records, `feature-plans/` |
| `prototypes/` | HTML state prototypes for web-admin pages + `IMPLEMENTATION_PLAN.md` |
| `specs/` | `UX_SIMPLIFICATION.md`, `EASEBUZZ_CPV_HARDENING.md`, `MENU_CONFIG_UX_SPEC.md`, `web-admin-v1-features.md`, `v2-third-party-integration-inventory.md` (note: new files here are git-ignored by the unanchored `specs/` rule) |
| `baseline-provenance/` | Baseline commit & reconciliation docs, `residual-provenance-risk.md` |
| `mcp/` | MCP install links |

## Evidence (read-only)

Dated, immutable records. Do not "fix" them to match today's tree — that destroys the audit
trail. Policy and citation rules: `evidence/README.md`.

| Folder | Contents |
|---|---|
| `evidence/reviews/` | Dated audits and review findings, plus the two root-level audit reports (`FEATURE_AUDIT_REPORT.txt`, `FEATURE_CROSS_CHECK_AND_SOLUTIONS.txt`); `design-codebase-2026-09-12/` holds that review's diagrams |
| `evidence/rnd/` | Competitor teardowns + raw APK evidence: Arow (`arow-*-2026-09-08.*`), Khide (`khide-*-2026-09-10.*`), product/design research, `next_version_bom/`, `tools/` |
| `evidence/planning/` | Dated plans and reviews that are no longer active (`EASEBUZZ_*_2026-09-2x.md`, `deep-codebase-analysis-2026-09-18.md`, `PLAN.archive.20260717.md`) |
| `evidence/reports/` | One-off investigation write-ups (`ACTUAL_PROBLEM_WiFi_Printer_AutoScan.txt`) |
| `evidence/logs/` | Session logs (`SESSION_LOG_2026-08-21.txt`) |
| `evidence/archive/` | Large historical dumps & indexes (`KHANABOOK_FULL_TECHNICAL_SPEC.txt`, `OPERATIONS_AND_ACTIONS.txt`, `VERSION_ANALYSIS.txt`, `DEEP_FILE_LIST.md`, `PROJECT_FILE_INDEX.md`, `aboutoursaas.txt`, `agent-outputs/`) |

## Conventions

- `meta/AGENTS.md` and `meta/ANDROID_UI_RULES.md` are the source of truth for AI agents — tools
  should resolve `docs/meta/AGENTS.md` if not found at repo root.
- Cross-doc references use relative paths so a folder move only needs one edit; references from
  evidence files are left as written (they describe the tree at their date).
- The code lives in `apps/android/`, `apps/web-admin/` and `services/server/` (moved from
  `Android/`, `web-admin/`, `server/` in October 2026). Files in `evidence/` and
  `baseline-provenance/` still cite the old paths on purpose — map `Android/` → `apps/android/`,
  `web-admin/` → `apps/web-admin/`, `server/` → `services/server/`.
- New evidence goes in `evidence/<kind>/` with a `YYYY-MM-DD` in the filename; new living docs go
  in the folder for the question they answer.
