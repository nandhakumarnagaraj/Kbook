# Next Actions

1. **Verify Modernized Restaurant Configuration Layout on Hardware (`ZA222J5KT7`)**:
   - Connect target device via USB (`adb devices`).
   - Install newly assembled APK: `adb -s ZA222J5KT7 install -r apps/android/app/build/outputs/apk/debug/app-debug.apk`.
   - Open Settings → Restaurant Configuration (`main?tab=2&section=shop`).
   - Verify Top Bar header is aligned with the back button `(←)`.
   - Verify Card 1 (Restaurant Details) displays storefront icon, subtitle, logo changer, and leading icons for all 6 inputs.
   - Verify compact gold pill `[ ➤ Send OTP ]` button inside Email field when email is modified.
   - Verify Card 2 (Business Hours) is collapsed by default (clock icon + title + `"Daily Order Number will reset at <time>"` + chevron only), and that tapping the header reveals side-by-side Opening & Closing time fields.
   - Change Closing Time and confirm the collapsed subtitle updates live (+30 min buffer).
   - Confirm the separate `Business Day` card is gone (merged into Business Hours).
   - Verify Card 3 (Billing Mode) and the full-width rounded gold pill button for `Save Configuration`.
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
