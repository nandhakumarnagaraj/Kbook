# KhanaBook Easebuzz Integration Review Package

This folder contains the PDF-ready Easebuzz integration review package for the `v2` branch.

## Files

- [easebuzz-integration-review-package.md](easebuzz-integration-review-package.md) - architecture, flow diagrams, production readiness, and compliance checklist.
- [easebuzz-review-email.md](easebuzz-review-email.md) - email draft for Easebuzz production/sandbox review.
- [OPS_RUNBOOK.md](OPS_RUNBOOK.md) - operational procedures across the sub-merchant lifecycle: onboarding, payment, post-split, settlement, reconciliation, refunds/chargebacks/clawbacks, suspension and closure.
- [INTEGRATION_TEST_CASE_AUDIT_2026-09-23.md](INTEGRATION_TEST_CASE_AUDIT_2026-09-23.md) - case-by-case mapping of the supplied Easebuzz checkout workbook to implementation, local test evidence, and production acceptance gaps.
- [diagrams/lifecycle-operations.mmd](diagrams/lifecycle-operations.mmd) - full lifecycle sequence diagram (onboarding → payment → settlement → money-back paths → closure).

## PDF Export Notes

The main package is written as Markdown with Mermaid diagrams. Export using a Markdown renderer that supports Mermaid, such as VS Code Markdown Preview Mermaid Support, Typora, Obsidian, or GitHub-compatible export tooling.

