# Next Actions

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
