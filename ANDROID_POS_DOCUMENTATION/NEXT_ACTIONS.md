# Next Actions

1. **Verify Restaurant Configuration Email OTP Guard on Hardware (`ZA222J5KT7`)**:
   - Open Settings → Restaurant Configuration (`main?tab=2&section=shop`).
   - For phone/credential accounts: modify email address to a valid new address and tap `Send OTP`.
   - Verify 6-digit OTP prompt appears with 2-minute countdown timer.
   - Enter the 6-digit verification code and verify lock icon with `Verified` toast appears.
   - Attempt saving with unverified or invalid email to confirm the save guard toast displays.
   - For Google-authenticated accounts: confirm email field remains locked with label `Email (Google account)`.
2. **Verify Quick Start Screen UI on Device**:
   - Reset or trigger fresh user onboarding state.
   - Verify `[Skip]` button is invisible initially and smoothly fades in after 5 seconds.
   - Verify entering a WhatsApp number reveals `Get OTP` and handles 6-digit verification.
   - Verify bottom action row displays `Watch Tutorials` and `Start Billing` side-by-side.
2. **Execute Midnight Boundary Crossover Test on `ZA222J5KT7`**:
   - At `12:01 AM – 12:49 AM IST`: Place a test bill and verify daily order sequence continues `#A05`.
   - At `12:51 AM IST`: Place a test bill and verify daily order sequence resets to `#A01`.
3. **Takeaway / Quick Bill Operational Walkthrough**:
   - Execute Quick Bill workflow via UI automation on connected hardware.
   - Document in `WORKFLOWS/COMPLETE_QUICK_BILL_WORKFLOW.md`.
