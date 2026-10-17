# KhanaBook Easebuzz Integration Review Package

This folder contains the PDF-ready Easebuzz integration review package for the `v2` branch.

## Files

- [easebuzz-integration-review-package.md](easebuzz-integration-review-package.md) - architecture, flow diagrams, production readiness, and compliance checklist.
- [easebuzz-review-email.md](easebuzz-review-email.md) - email draft for Easebuzz production/sandbox review.
- [OPS_RUNBOOK.md](OPS_RUNBOOK.md) - operational procedures across the sub-merchant lifecycle: onboarding, payment, post-split, settlement, reconciliation, refunds/chargebacks/clawbacks, suspension and closure.
- [INTEGRATION_TEST_CASE_AUDIT_2026-09-23.md](INTEGRATION_TEST_CASE_AUDIT_2026-09-23.md) - case-by-case mapping of the supplied Easebuzz checkout workbook to implementation, local test evidence, and production acceptance gaps.
- [diagrams/lifecycle-operations.mmd](diagrams/lifecycle-operations.mmd) - full lifecycle sequence diagram (onboarding → payment → settlement → money-back paths → closure); the other `diagrams/*.mmd` files cover the individual stages.
- [PRODUCTION_VALIDATION_RUNBOOK.md](PRODUCTION_VALIDATION_RUNBOOK.md) - ₹1 live-mode validation procedure.
- [OFFICIAL_DOC_CROSSCHECK_2026-09-20.md](OFFICIAL_DOC_CROSSCHECK_2026-09-20.md) - implementation vs. Easebuzz's published API docs.
- [external-share/](external-share/README.md) - material safe to hand to Easebuzz.

The implementation-side docs live one folder over, in [`../easebuzz/`](../easebuzz/EASEBUZZ.md).

## Dated files keep the paths they were written against

Files named with a `YYYY-MM-DD` date are evidence of what was true on that date. A pointer to a living doc was retargeted when the doc moved; a path that is itself part of a historical claim was left as written. So in `INTEGRATION_TEST_CASE_AUDIT_2026-09-23.md`, `docs/easebuzz/...` is what the public tree contained at that commit and now maps to [`../easebuzz/`](../easebuzz/EASEBUZZ.md). Policy: [`docs/evidence/README.md`](../../evidence/README.md).

## PDF Export Notes

The main package is written as Markdown with Mermaid diagrams. Export using a Markdown renderer that supports Mermaid, such as VS Code Markdown Preview Mermaid Support, Typora, Obsidian, or GitHub-compatible export tooling.

