# 11 — UI/UX Audit

**Evidence basis:** source review plus the screenshots and session records already committed under `docs/audit/`, `artifacts/android-ui-tests/` and `ANDROID_POS_DOCUMENTATION/DEVICE_SESSIONS/SESSION_LOG.md`. **No new screenshots were taken for this audit**; no browser/emulator run, no tap-through.

## 1. Workflow completeness

Assessed against the user's actual job — a cashier during service — not against screens existing.

| Workflow | State | Evidence |
| --- | --- | --- |
| Open shift → take order → fire KOT → settle cash/UPI → reprint | Complete and device-verified | Session 001, `WORKFLOWS/COMPLETE_DINE_IN_WORKFLOW.md` |
| Daily closing / midnight rollover | Implemented, boundary unverified | `NEXT_ACTIONS.md` item 2; `UNVERIFIED_ASSUMPTIONS.md` |
| Owner edits shop config (hours, logo, invoice footer) | Complete, device-verified layout | Sessions 004–005 |
| Owner changes WhatsApp login number | Complete via OTP | `ShopConfigSection.kt` |
| Owner changes email | Code complete, delivery blocked on SMTP | BUG-004, `NEXT_ACTIONS.md` item 1 |
| First-run onboarding (Quick Start) | Implemented, one credential step not honest (AUD-002) | `QuickStartScreen.kt` |
| Quick bill / takeaway | Not verified end-to-end | `NEXT_ACTIONS.md` item 6 |
| Staff management | Correctly absent on POS | `KNOWN_LIMITATIONS.md` §2 |

## 2. Android UX findings

### AUD-002 (P1) — UI demands a password that goes nowhere [V]

For a Google-auth owner the Quick Start screen shows "Set a password", refuses to enable "Start Billing" until it is filled, then silently stores it locally and never uses it. From the user's side: they typed a password, expected it to matter, and it does not. From the security side, it is plaintext at rest.

**Copy and behaviour must match the implementation** (`AI_ENGINEERING_RULES.md`: implementation is the source of truth). Either remove the field, or make it real. The current state is the one option that is wrong under every reading.

### AUD-025 (P2) — two OTP flows with slightly different behaviour [V]

In the Restaurant Configuration screen, the WhatsApp and Email fields each implement Get OTP → 6-digit verify → verified-lock icon. They differ in real ways:

- phone verification is re-triggered from the field's trailing icon and the code field appears below it;
- email verification only offers "Get OTP" when the address is *valid*, and clears `emailOtpSent` when the text changes;
- the phone path re-checks user existence on every keystroke (`checkUserExists`), the email path does not.

To a shopkeeper these are the same task. Divergent rules in the same card read as bugs ("why does it not let me send a code for this one?").

**Recommendation:** one shared component with one rule set. If email genuinely needs the validity gate, make it a parameter, not a second implementation.

### AUD-026 — WITHDRAWN, not a bug

This finding was checked against the implementation and does not hold. See the note at the end of this section.

### (original entry, retained for history) verification state resets on profile refresh [C]

`LaunchedEffect(profile)` sets `isOtpVerified = true` and `isEmailOtpVerified = true` whenever the profile object changes — including after an unrelated save. A user mid-verification who triggers a profile refresh loses their "verified" indicator into a state they did not earn. Low frequency, but it is exactly the class of bug the file's own comments warn about ("Save needs two clicks").

**Recommendation:** key the reset on the identity fields only (`whatsappNumber`, `email`), not the whole profile object.

**Withdrawn (2026-11-13):** `LaunchedEffect(profile)` does not only touch the flags — it repopulates `name`, `address`, `whatsapp`, `email`, `reviewUrl`, `invoiceFooter` and both times from the profile. So a profile refresh also discards the user's in-progress edits; setting the verified flags back to `true` matches the values actually on screen. The consistent fix is not to key the flags on identity fields, and the behaviour is not the bug it first appeared to be. If the form-clobbering itself is ever addressed, this reasoning must be revisited with it.

### Strengths [V]

- **Destructive actions are guarded.** Unsaved changes trigger a `BackHandler` + `KhanaBookDialog` with "Keep Editing" / "Discard".
- **Irreversible change is PIN-gated.** Switching billing mode prompts for the app-lock PIN when one is set.
- **Errors are actionable, not raw.** Sanitised messages with a fallback like "Couldn't save profile. Please try again."
- **Supporting text is used for validation**, not colour alone: `isError` plus `supportingText` on the same field.
- **Compact-width layout is a first-class concept** (`layout.isCompactForm`, `maxContentWidth`), so tablets and phones are both designed for.
- **Non-breaking-space fix for time labels** is documented in code — the kind of detail that only comes from watching a real screen.

## 3. Web Admin UX

Prior audits already exist and should be treated as the record: `docs/audit/web-admin-ui-audit.md` and `docs/audit/web-admin-consistency-audit-2026-10-25.md`, with baseline screenshots at 375/1024/1440 in `docs/audit/baseline-phase0/`.

What this audit adds:

- **Consistency is machine-enforced** for colour, contrast, spacing, type scale, z-index, motion, breakpoints, accent budget, inline styles and token fallbacks (`npm run check:*`). This is stronger than a documented convention.
- **AUD-010 (P3):** support tickets are mirrored to `localStorage`, so a different admin on the same browser sees a previous admin's tickets, and tickets deleted server-side reappear. Either key the cache per user and clear it on logout, or drop the cache.
- **AUD-009 (P3):** dashboard and menu pages are too large to review; UX changes there are high-risk edits, which is itself a UX risk because it discourages fixing small papercuts.

## 4. Consistency across surfaces

| Concern | Android | Web Admin | Verdict |
| --- | --- | --- | --- |
| Brand colour | `PrimaryGold` token | `check:colors` script | Consistent [C] |
| Terminology | "Restaurant Details", "Business Hours", "Billing Mode" | same domain terms | Consistent [C] |
| Identity change rule | OTP required for phone/email | server rejects with 409 unless OTP (`REQUIREMENT_IMPLEMENTATION_GAPS.md` §1) | Consistent rule, different presentation — acceptable |
| Error surfacing | toast + inline supporting text | inline + toast | Both present; not reviewed side by side [U] |
| Empty/loading states | `CircularProgressIndicator` on the field that is loading | present per page | Android is notably good here |

## 5. Recommendations (no new screens)

1. **Resolve AUD-002** — the only finding where the UI actively misleads.
2. **Unify the two OTP flows** (AUD-025) — same task, one behaviour.
3. Fix the verification-reset keying (AUD-026).
4. Bound or remove the web-admin ticket cache (AUD-010).
5. When the dashboard/menu components are split (`09`), fix papercuts in the same pass — but take before/after screenshots so the change is evidenced, not assumed.
6. Do **not** redesign. Every finding above is a correction, not a direction change.
