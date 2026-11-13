# Next Actions

Ordered by blocking value. Session references point at `DEVICE_SESSIONS/SESSION_LOG.md`.

## From the 2026-11-13 product audit (Session 007)

Findings and rationale: `docs/product-audit/`. These are additive; the pre-existing list below is unchanged.

A. **Commit the BUG-007 compile repair, then stop it recurring.**
   - The tree compiles (`:app:compileDebugKotlin` exit 0) and unit tests pass (`:app:testDebugUnitTest` exit 0) in the working tree, but `main` still does not compile from a clean checkout.
   - Then prevent recurrence: require the `CI` workflow on `main`, or add a pre-push `./gradlew :app:compileDebugKotlin --offline` check.
   - Status: repair uncommitted; gate open.

B. **Quick Start password (BUG-005)** — DECIDED AND IMPLEMENTED 2026-11-13:
   - Chosen: remove the field and the `canSubmit` password condition, together with `SessionManager.savePassword/getPassword` and the `google_password` preference.
   - Rationale: Google sign-in resolves accounts through `googleEmail`, so nothing depended on a separate password; the stored value was unread plaintext.
   - Status: done; compile + unit tests exit 0. Still to do: commit it.

C. **Unify the two OTP flows in `ShopConfigSection.kt`** (audit AUD-012/AUD-025):
   - Phone and Email each hand-roll Get OTP / 6-digit / timer / verified-badge, with different validity gates.
   - Extract one state holder + `OtpVerificationField`, then rewrite both.
   - Status: recommended only.

D. **Make the instrumented Android suite real** (audit AUD-007):
   - `android-ci.yml` runs `connectedDebugAndroidTest` with `continue-on-error: true` and states the suite has never executed, so 30 files give no protection.
   - Run it once, triage the real result, then remove the flag or delete the dead tests.
   - Status: no run performed.

E. **Measure before optimising** (audit AUD-021 and the sync path):
   - Record dashboard SQL statement count with Hibernate logging on.
   - Record `/sync/master/pull` payload size and apply time for a realistic tenant.
   - Status: unmeasured; do not optimise before these numbers exist.

F. **`TerminalManagementConcurrencyTest`** — FIXED 2026-11-13:
   - Its setup now creates a 5-terminal `ENTERPRISE` fixture plan, because `BaseIntegrationTest` uses `ddl-auto=create` and skips Flyway, so plan rows do not exist in that context.
   - Status: green.

G. **Full server suite: 955 tests, 2 failures, 0 errors** (down from 6 failures + 5 errors):
   - `ConcurrentBillPushTest.billPush_paymentStatus_lwwBehavior` and `TerminalAccessControlGapTest.kotOnlyTerminal_stillPushesBills` both return 401, and **both pass when run together in isolation**.
   - This is cross-class test interference (most likely a bumped `RestaurantTerminal.credentialVersion` or the token-revocation cache plus re-used restaurant ids), not a product defect.
   - Status: needs a bisect to find the polluting class, then `@DirtiesContext` or unique ids on it. Do not "fix" this by weakening the assertions.

---

Ordered by blocking value. Session references point at `DEVICE_SESSIONS/SESSION_LOG.md`.

1. **Configure SMTP, then re-verify Email OTP end-to-end (BUG-004)**:
   - Set `MAIL_PASSWORD` (and `MAIL_USERNAME` if not the default mailbox) on the server build the POS talks to.
   - With the app open on Restaurant Configuration (`main?tab=2&section=shop`), change the Email field to a fresh address and confirm the code arrives in the inbox rather than only the server log.
   - Confirm the inline `[ ➤ Send OTP ]` pill still appears inside the Email trailing icon when a valid new address is typed.
   - Confirm the server log shows `Verification OTP email sent to …` and not `Email OTP not delivered: spring.mail.password is empty`.
   - Status: code fixed and unit-tested 2026-11-13 (Session 006); delivery unverified.

2. **Execute Midnight Boundary Crossover Test on `ZA222J5KT7`**:
   - At `12:01 AM – 12:49 AM IST`: place a test bill and verify daily order sequence continues `#A05`.
   - At `12:51 AM IST`: place a test bill and verify daily order sequence resets to `#A01`.
   - Status: pending; still listed in `UNVERIFIED_ASSUMPTIONS.md`.

3. **Re-verify the Restaurant Configuration layout after the next APK install**:
   - Business Hours card opens collapsed; tapping the header reveals Opening / Closing time; closing-time changes update the reset subtitle live.
   - Billing Mode card surface matches Restaurant Details and Business Hours (`CardBG`, 25% `BorderGold`, `KhanaRadii.lg`).
   - Status: verified on device 2026-11-12 (Sessions 004–005); re-check only after further edits to `ShopConfigSection.kt` / `ShopConfigComponents.kt`.

4. **Verify Quick Start Screen UI on Device**:
   - Reset or trigger fresh onboarding state.
   - Verify `[Skip]` is invisible initially and fades in after 5 seconds.
   - Verify the WhatsApp number field reveals `Get OTP` and handles 6-digit verification.
   - Verify the bottom action row shows `Watch Tutorials` and `Start Billing` side by side.
   - Status: implemented and screenshot-verified 2026-11-12 (Session 002); the Skip timer and OTP path have not been re-run since.

5. **Redeploy the current build to OnePlus Nord**: it still runs a pre-Session-004 APK; connect it and `adb install -r`.

6. **Takeaway / Quick Bill Operational Walkthrough**:
   - Execute the Quick Bill workflow via UI automation on connected hardware.
   - Document it in `WORKFLOWS/COMPLETE_QUICK_BILL_WORKFLOW.md`.

7. **Gate the web-admin spec suite in CI (recommendation, not implemented)**:
   - `npx ng test --watch=false` is green at 350/350 (Session 006) but nothing runs it automatically, which is how 39 failures accumulated unnoticed.
   - Proposal: run it next to the ten `check:*` scripts on every change under `apps/web-admin`.
   - Status: awaiting authorization; no pipeline file was modified.
