# KhanaBook Docs

All project documentation lives under `docs/` — single parent folder.

| Folder | Contents |
|---|---|
| `meta/` | Entry-point docs: `README.md`, `AGENTS.md`, `CLAUDE.md`, `ANDROID_UI_RULES.md` |
| `planning/` | Active planning: `PLAN.md`, `PLAN.archive.*`, `FIX_PLAN.md`, `ROOT_CAUSE_LOG.md`, `NEWBILLSCREEN_SPLIT_PLAN.md`, `billing-sync-fix-verification.md`, `post-all-phases-checklist.md` |
| `archive/` | Large dumps & indexes: `aboutProduct.txt`, `KHANABOOK_FULL_TECHNICAL_SPEC.txt`, `OPERATIONS_AND_ACTIONS.txt`, `UI_CAPABILITY_AUDIT.txt`, `VERSION_ANALYSIS.txt`, `DEEP_FILE_LIST.md`, `PROJECT_FILE_INDEX.md`, `Production_docs.txt` |
| `reviews/` | Dated audits: `KHANABOOK_API_REVIEW_VALIDATION_*.md`, `KHANABOOK_DIFFERENTIAL_REVIEW_*.md`, `KHANABOOK_EASEBUZZ_ERA_QA_*.md`, `KHANABOOK_FLOW_WIRING_AUDIT_*.md`, `KHANABOOK_PRODUCTION_RELIABILITY_AUDIT_*.md` |
| `design/` | Design system: `DESIGN_SYSTEM.md`, `DESIGN_SYSTEM_FREEZE.md`, `LayoutGuidelines.md`, `ResponsiveLayoutMigration.md` |
| `easebuzz/` | Easebuzz integration: `easebuzz-sdk-plan.md`, `easebuzz-*.txt`, sub-merchant docs, Postman collection |
| `easebuzz-review/` | External review package + diagrams |
| `screenshots/` | `Screenshot_*.png`, `Splash Screen.png` |
| `specs/` | `UX_SIMPLIFICATION.md`, `web-admin-v1-features.md`, `v2-third-party-integration-inventory.md` |
| `android/` | Android checklists & audits |
| `product/` | `PRODUCT.md`, `DESIGN.md`, feature suggestions |
| `api/` | `api-docs.json` |
| `baseline-provenance/` | Baseline commit & reconciliation docs |
| `mcp/` | MCP install links |
| `SECURITY_ROTATION_REQUIRED.md` | Active security action item (stays at `docs/` root) |

**Conventions:** `AGENTS.md` and `ANDROID_UI_RULES.md` in `meta/` are the source of truth for AI agents — tools should resolve `docs/meta/AGENTS.md` if not found at repo root.
