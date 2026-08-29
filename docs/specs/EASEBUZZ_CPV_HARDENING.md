# Easebuzz Sub-Merchant Onboarding / CPV Hardening

Status: PLAN (not yet implemented). Server-side only. No DB schema/migration.

## Context

Easebuzz Compliance flagged sub-merchant onboarding issues (email from Jonathan S.,
Deputy VP–Business, 2026-08). The recurring failure is a **negative CPV report** caused
by a business-name mismatch, plus general KYC field completeness. This spec hardens the
submit path so submissions that would fail Easebuzz CPV are blocked client/server-side
before they reach Easebuzz.

Priority order (KhanaBook priorities): financial/data integrity > functional correctness >
security > lifecycle > UI > performance > accessibility > maintainability.

## Existing implementation (verified in codebase, read-only)

Already present and working:
- `EasebuzzSubMerchant` entity stores: `businessName`, `legalEntityName`, `businessType`,
  `pan`, `gst`, bank fields, `businessAddress`, `state`, `fssaiNumber`, `fssaiExpiryDate`,
  `contactEmail`, `contactPhone`, two business-proof slots
  (`businessProof1Type/Url`, `businessProof2Type/Url`), KYC status fields.
- FSSAI validated as 14-digit license (not acknowledgment):
  `EasebuzzPaymentService` (~line 422) and `GstFssaiLookupService` (~line 50).
- Proprietorship two-business-proof rule ENFORCED at submit:
  `SubMerchantService.submitToEasebuzz` throws `BusinessRuleException("BUSINESS_PROOFS_REQUIRED")`
  for `SOLE_PROPRIETORSHIP` without both proofs. Covered by
  `EasebuzzIntegrationTest.proprietorshipRequiresTwoBusinessProofsForSubmission`.
- `legalEntityName` is plumbed to Easebuzz in `EasebuzzApiClient` (~line 320):
  `legalName = (legalEntityName not blank) ? legalEntityName : subMerchantName`.

Gaps (root causes of the compliance findings):
1. **`legalEntityName` silently falls back to the trade name** when blank →
   CPV name mismatch → negative report. Not required at submit.
2. **No full mandatory-field completeness gate** before submit — only proofs +
   FSSAI length are checked; blank `businessAddress`/`state`/`pan`/`gst`/bank/contact
   can still submit.
3. **Business-proof types are free-text** (`String`, length 100). No validation that the
   two proofs are present, distinct, and from an accepted document-type set.

Not code (out of scope): CPV re-conduct (ops, re-initiated), Partner Referral Model
(commercial), KhanaBook↔merchant agreement (Sejda-signed PDF is a KhanaBook-side legal
record — NOT part of the Easebuzz onboarding API payload).

## Plan — 3 submit-gate changes (no schema change; fields already exist)

### Change 1 — Require legal entity name at submit (highest impact)
- In `SubMerchantService.submitToEasebuzz`, throw
  `BusinessRuleException("LEGAL_ENTITY_NAME_REQUIRED")` if `legalEntityName` is null/blank.
- Stop the silent trade-name fallback for CPV-critical submission.
- Risk: LOW — only blocks submissions that would fail CPV anyway.

### Change 2 — Mandatory-field completeness gate
- One validation in `submitToEasebuzz`: assert all mandatory KYC fields non-blank
  (pan, businessAddress, state, bank account/ifsc/name, contactEmail, contactPhone;
  gst conditionally if GST-registered). Throw `MANDATORY_FIELDS_MISSING` listing the gaps.
- Risk: LOW — purely additive validation.

### Change 3 — Distinct + valid business proof types
- Validate the two proofs are (a) both present, (b) different types, (c) from an allowed
  set. Enforce present+distinct now; constrain to an accepted enum once Easebuzz confirms
  the authoritative list.
- Risk: LOW-MEDIUM — needs the accepted-types list from Easebuzz/Jonathan.

## Execution order (one change -> verify -> next)

```
1. Read submitToEasebuzz() fully (confirm existing validations)   [read-only]
2. Change 1: legalEntityName required  -> unit test -> build
3. Change 2: mandatory-field gate      -> unit test -> build
4. Change 3: distinct-proof validation -> unit test -> build
5. Run full server test suite (mvn test)
6. Review diff -> commit (server only; no DB migration)
```

## Files expected to change
- `server/.../service/SubMerchantService.java` — submit-gate validations (Changes 1–3).
- `server/.../service/EasebuzzApiClient.java` — fallback becomes unreachable for valid submits (Change 1).
- `server/.../service/EasebuzzIntegrationTest.java` — new tests: legal-name-required,
  mandatory-fields-missing, distinct-proofs (mirror existing proof test style).
- (Follow-up, separate change) Android `EasebuzzOnboardingViewModel`/screen — surface new
  error codes; make legal entity name a required form field client-side.

## Explicitly NOT in scope
- No DB schema/migration (fields already exist).
- No change to terminal/payment/settlement/financial logic.
- No merchant-agreement (Sejda) storage — separate KhanaBook-side track.
- No change to CPV re-conduct / referral model.

## Verification
- Server: `mvn test` (unit + `EasebuzzIntegrationTest`) — runs without Docker.
- Manual: submit sub-merchant with blank legal name -> expect `LEGAL_ENTITY_NAME_REQUIRED`;
  all fields present -> proceeds.

## Open items needed before Change 3
1. Authoritative list of Easebuzz-accepted business-proof document types (from Jonathan).
2. Confirm mandatory-field set per entity type (is GST mandatory for all, or only when
   `gst_enabled`? Tax config has a `gst_enabled` flag).

## Risk & regression
- Overall risk: LOW. Submit-time validations only; block only submissions that would fail
  Easebuzz CPV. No data mutation, no schema change, no payment-flow change.
- Regression area: sub-merchant submission path only; guarded by existing + new
  `EasebuzzIntegrationTest` cases.

## Reusable prompt (paste to an agent to execute this plan)

```
Implement the Easebuzz CPV hardening per docs/specs/EASEBUZZ_CPV_HARDENING.md.
Server-side only. No DB schema/migration. Do NOT touch terminal/payment/settlement logic,
the merchant-agreement track, or unrelated code.

Step 1 (read-only): read SubMerchantService.submitToEasebuzz() and confirm which
validations already exist. Report before editing.

Step 2: Change 1 — in submitToEasebuzz, throw BusinessRuleException("LEGAL_ENTITY_NAME_REQUIRED")
when legalEntityName is null/blank. Add a unit test in EasebuzzIntegrationTest mirroring
proprietorshipRequiresTwoBusinessProofsForSubmission. Build.

Step 3: Change 2 — add a mandatory-field completeness gate (pan, businessAddress, state,
bank account/ifsc/beneficiary, contactEmail, contactPhone; gst only when GST-registered).
Throw MANDATORY_FIELDS_MISSING listing gaps. Add test. Build.

Step 4: Change 3 — validate the two business proofs are present and of DISTINCT types
(defer the accepted-types enum until the list is confirmed). Add test. Build.

Step 5: run `mvn test`; report pass/fail counts.
Step 6: show `git diff --stat`; do NOT commit. Wait for confirmation.

One change -> build -> test -> report, then next. Stop and report if any test fails.
Never claim a test ran unless it actually ran.
```
