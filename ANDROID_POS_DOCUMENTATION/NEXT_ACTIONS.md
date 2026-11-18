# Next Actions

Ordered by blocking value. Session references point at `DEVICE_SESSIONS/SESSION_LOG.md`.

## Cross-cutting copy & naming review (2026-11-18, Session 019 — static only)

- **DONE (static):** reviewed every Android input field/header, toast/snackbar, notification path, and the screen/function/route names in Android, `services/server`, and `apps/web-admin`. Full evidence and IDs: `RECOMMENDATIONS/FORMS_TOASTS_NAMING_REVIEW.md`.
- **Bugs registered:** BUG-010 (Change Password allows a 6-character password), BUG-011 (variant price truncates paise), BUG-012 (GST % rejects decimals).
- **Top actions, in order:** (1) fix IN-01 / BUG-010; (2) settle the tenant noun `shop`/`business`/`restaurant` (NM-12) before any renaming; (3) collapse the duplicate password-reset endpoint families (NM-06); (4) unify terminal recovery verbs (NM-07); (5) align required markers with `saveConfiguration()` (IN-02).
- **Not implemented:** every item is documented only. No source outside `ANDROID_POS_DOCUMENTATION/` was changed.
- **Already tracked, not duplicated:** item **C** below (unify the phone/email OTP flows) covers part of IN-07/IN-09; items **K/L/M** are unrelated and remain as they were.
- **IMPLEMENTED (2026-11-18, working tree, uncommitted):** BUG-010, BUG-011, BUG-012 and IN-01..IN-06 fixed. `:app:compileDebugKotlin` and the focused `ValidationUtilsTest` / `VariantDraftReconcilerTest` pass. **Not device-verified, not committed.** Next: run the app on `ZA222J5KT7` and check Change Password, GST 5.5, and a variant price of 12.5. Server-side password minimum (still 6) is a separate cross-client decision.

## Screenshot tests landed; three follow-ups (2026-11-18, Session 017)

- **DONE and verified (host):** design-system screenshot gate. `:app:recordRoborazziDebug` and
  `:app:verifyRoborazziDebug` both exit 0; 6 goldens committed under
  `apps/android/app/src/test/screenshots/`; test XML reports 6 tests / 0 failures. Detail:
  `DEVICE_SESSIONS/SESSION_LOG.md` → Session 017.
- **Follow-up 1 (needs eyes):** nobody has actually looked at the 6 PNGs. Open them once and
  confirm they show KhanaBook — dark background, gold accents, rounded cards. Only file size,
  dimensions and pass/fail were checked.
- **Follow-up 2 (needs a decision):** make the CI gate real. `android-screenshot-tests` in
  `.github/workflows/android-ci.yml` runs with `continue-on-error: true` because goldens were
  recorded on Windows and the runner is Linux. After one green CI run, delete that line.
- **Follow-up 3 (unresolved limitation):** Poppins never renders in goldens — it is a Google-Fonts
  downloadable font and Robolectric cannot fetch it, so goldens use the platform fallback
  typeface. Typeface regressions still need a device. If that matters, bundling the Poppins
  TTFs in `res/font/` (already suggested in `Type.kt`'s own comment and in
  `docs/android/DEVICE_COVERAGE_AUDIT.md` §5) would fix both the offline-fallback gap and the
  goldens at once.
- **Production change to review on device:** `core/theme/Theme.kt` no longer casts
  `view.context as Activity`; it uses a `ContextWrapper`-walking `findActivity()` and degrades to
  `Compact` instead of crashing. It compiles and all goldens render, but it has not been exercised
  on hardware — walk login → home → any Scaffold screen on the moto g34.
- **Reviewed and rejected:** `com.android.compose.screenshot` (Google's standalone plugin) is
  unmaintained and its successor needs AGP 9.5+; this project is on AGP 8.9.1. Do not add it
  during an AGP upgrade without re-checking the compatibility table.

## Current deployed build (2026-11-18, Sessions 012 / 014 / 015)

- The latest debug APK is SHA-256 `A46F6E334ADAE5120E531E9309C2E56AE249C3FE70E2B4A8903BEB770119E69C`
  (menu photo fixes, Session 018), installed on `ZA222J5KT7`. Earlier builds this day were
  `87A337A4…D6FDC54` (Shop Logo), `88EBD62F…09E436D` (Business Hours toggle) and
  `A9D1BF92…A652D4C94` (plain update).
- The Session 014 build was also installed on the OnePlus Nord `AC2001` when devices flapped, so
  **item 5 (Nord redeploy) was satisfied at that point**; the Nord has not received the Session 015
  Shop Logo build.
- `ZA222J5KT7`: Session 012 install returned Success (`lastUpdateTime` 17:18:11) and the app launched
  to Home with no crash entry. Session 014 added the Business Hours change; compile, unit tests and
  `assembleDebug` all exit 0, both toggle states verified on device. Details: `DEVICE_SESSIONS/SESSION_LOG.md`
  → Sessions 012 and 014.
- The "after the next APK install" trigger for item 3 below has fired, but item 3's collapse/expand and
  Billing Mode surface assertions were **not** executed; it stays open.

## Restaurant Configuration UX check and change (2026-11-18, Sessions 013–014)

- Owner asked whether `Default Time` and `Custom Time` are both shown at once. **Answer (before the change): yes for the two selector cards — they were one radio-style `Row`; no for the time inputs — `Opening Time` / `Closing Time` were conditional.** Full field inventory and evidence: `DEVICE_SESSIONS/SESSION_LOG.md` → Session 013.
- Owner then authorised a change and chose a **segmented toggle**. **DONE in Session 014:** the two cards were replaced by a `Default Time | Custom Time` segmented control and only the selected mode's panel is composed. Verified on device; no source from Session 013 remains as a pending recommendation.
- **Shop Logo redesign DONE in Session 015** (full audit, files, verification and limitations in `DEVICE_SESSIONS/SESSION_LOG.md`). Remaining verification, not done on the shared device: empty state, real upload success/failure/retry, invalid-file validation and invoice/receipt rendering.
- **Menu food-item pictures: audit (Session 016) and fixes (Session 018).** `MenuItemThumbnail` was already resilient. Fixed: upload status now shows on the correct row via `uploadingPhotoItemId`; the row's More menu now exposes a real **Add Photo / Change Photo** action (so the new-item "tap Add Photo to retry" message is valid); Edit Item preview has versioned cache keys, a loading spinner and an error placeholder, plus accurate file requirements. Still open: an inline **Retry** action for failed dish-photo uploads, and device verification of the spinner/error states. Full evidence in `DEVICE_SESSIONS/SESSION_LOG.md` → Sessions 016 and 018.
- Build note: concurrent Session 017 added Roborazzi/Robolectric; `--offline` builds fail until those artifacts are cached, so build online or after warming the cache.
- **Identified backend/printing limitations (not changed):** server validates only non-empty / ≤2 MiB / `image/*` MIME; no dimension or magic-byte checks. No upload-progress API. No per-bill logo snapshot — reprints use the current logo; ESC/POS thermal prints no logo; UPI QR overlay scales the logo square (aspect not preserved). These need separate product decisions; do not change invoice logic from the Shop Logo work.

## From the 2026-11-14 lint cleanup (addendum 18:0x)

S. **Launcher icon warnings** — **DONE 2026-11-14 19:0x.** Adaptive icon merged into `res/mipmap/`
   (the `v26` folder is gone), monochrome layer added, legacy square PNGs removed, duplicate logo
   drawable deduped. All icon warnings are at 0. Remaining gap: themed-icon rendering was not
   visually confirmed because this launcher exposes no themed-icon toggle; re-check on an
   Android 13+ launcher with themed icons enabled before claiming that behaviour.

T. **Deprecated Google sign-in** — `CredentialManagerSignInWithGoogle` (2). Lint points at the
   Credential Manager migration for the auth flow; out of scope for a cleanup pass and needs its own
   test plan against the backend.

U. **Remaining style-only warnings** — `UseKtx` (76), `UseTomlInstead` (14). Mechanical churn with no
   behaviour change; do only when touching those files.

## From the 2026-11-14 dead-code audit (Session 011 addendum 17:0x)

Full verified list: `DEVICE_SESSIONS/SESSION_LOG.md` → "Addendum 17:0x". Nothing was deleted in
the audit; both items below are decisions.

Q. **Delete the verified-dead UI symbols.** 20 declarations with no caller anywhere, including
   `ReportDownloadBottomBar`, the four `ReportViews` aliases (`OrderHistoryTable`, `OrderLevelView`,
   `OrderRowItem`, `OrderDateFilterTabs`), `KhanaInvoiceShareSheet`, `SignaturePad`, `directPrint`,
   `payModeColor` / `getPayModeIconTint`, `BillInfoHeader` / `ActiveOrderItemCard` /
   `CompactMenuSection`, `TableHeader`, `BillSummaryDisplay` / `PaymentModeSelector` /
   `loadShopLogoBlockingSection`, `AppInfoSection`, `CustomerNumberCollectorSelector`,
   `NotStartedContent`, `DetailStatusRow`, `loginTextFieldColors`, three `AppLockScreen` callbacks.
   - Status: **DONE (2026-11-14 17:2x)** — owner approved all groups; deleted and verified. See
     `DEVICE_SESSIONS/SESSION_LOG.md` addendum 17:2x. One audit entry was a false positive and was
     kept: the three `AppLockScreen` biometric callbacks are framework overrides.

R. **Decide the hidden functionality before deleting it.** Some dead symbols are the *only* route
   to a feature, so deleting them removes that feature rather than dead weight:
   - `exportReport` + `buildShareText` + `ReportDownloadBottomBar` — PDF/CSV export and share text
     have no button anywhere. Delete, or surface a download/share action on the Orders screen?
   - `DetailStatusRow` — no order status is shown in `OrderDetailsDialog`.
   - `BillInfoHeader` / `ActiveOrderItemCard` / `CompactMenuSection` — the active-order detail screen
     does not render them.
   - `AppInfoSection` and `CustomerNumberCollectorSelector` — no settings entry for about/app info
     and none for customer-number collection.
   - Status: **DONE (2026-11-14 17:2x)** — owner chose delete for all five groups. Consequence:
     report PDF/CSV export and share text no longer exist anywhere in the app; order status is still
     not shown in the details dialog (its status row was removed with the dead component); the
     active-order detail screen and settings are unchanged visually because none of the removed
     pieces were rendered. If export is wanted later it must be rebuilt on top of `ReportGenerator`
     (the exporter was deleted too).

## From the 2026-11-14 Orders screen cleanup (Session 011)

O. **Verify the new chip labels on device** — DONE 2026-11-14 16:1x (Session 011 addendum):
   `Today` → `14 Nov`, `Yesterday` → `13 Nov` observed on `ZA222J5KT7` (PKG installed 16:10:35);
   the table was the only content in every state and the Custom picker still opens.
   - Still unscheduled: re-pick a Custom range after `Apply` and check the label fits one line at
     the largest type scale (`FilterChip` is `maxLines = 1` with ellipsis).

P. **Fate of the unreachable `PaymentLevelView`** — DONE 2026-11-14 16:10 (Session 011
   addendum): deleted, together with its orphaned `PaymentModeItem` / `PartPaymentCard` /
   `ReportTypeToggle` helpers, `_reportType` / `reportType` / `setReportType`, and the `reportType`
   parameter of `ReportExporter.exportToPdf` / `exportToCsv`. `paymentBreakdown` was kept because
   it feeds the live payment summary inside both exports.

## From the 2026-11-14 Orders screen review (Session 009)

Gap analysis from Session 009. **H, I, J implemented and verified on device in Session 010.**
H was then superseded at 12:45 (caption removed at the owner's request); I and J stand.
K, L, M remain open decisions — nothing in them was implemented.

H. **Make the business window visible on the Orders screen** — SUPERSEDED 2026-11-14 12:45
   (Session 010 addendum): implemented in Session 010, then **removed at the product owner's
   request for all filters**. `publishWindow`/`windowCaption` are gone. The business-hours-aware
   queries and the `Date/Time` column remain, so the list still *is* business-day scoped — it is
   just no longer annotated. If the ambiguity ever needs explaining again, restore a caption
   from `BusinessDate.windowStart`; the picker/`Custom` chip code was kept.
   - Historical description of what shipped between 12:20 and 12:45:
     caption under the chips stating the queried window, e.g.
     `Business day 14 Nov, 12:30 AM → 15 Nov, 12:30 AM`, resolved in the shop's timezone;
     hidden when the window started at calendar midnight and the filter was not `Custom`;
     `Custom` chip carried the picked range (`12 Nov - 14 Nov`).
   - Still live from that work: `ReportsViewModel.formatCustomRange` / `customRangeLabel`.

I. **Add day context to the table** — DONE 2026-11-14 (Session 010):
   - `Yesterday`/`Custom` render `dd MMM` + `hh:mm a` on two lines inside the existing
     `Time` column and the header reads `Date/Time`; `Today` stays time-only.
   - Column weights unchanged (1.15 / 1.05 / 1.05 / 0.75); verified live with a
     12–14 Nov Custom range.
   - Note (14 Nov 2026): per product request, `includeDate` is now `false` for all
     filters, so the header always reads `Time` and rows show only `hh:mm a` — no
     date prefix in any filter.
     (`dd MMM / hh:mm a`) and the header reads `Date/Time`.

J. **Fix the row time zone** — DONE 2026-11-14 (Session 010):
   - Row timestamps now use `BusinessDate.zoneOf(profile.timezone)`, the same zone as the
     window calculation, instead of `ZoneId.systemDefault()`.

K. **Row-level modifications.** `OrderTableRow` still declares `onStatusChange`,
   `onPayModeChange` and `onRequestCancel`; they are now threaded through
   `OrderHistoryTableComponent(includeDate = …)` but still never invoked from the row.
   Decide: wire them to a row action menu, or delete the dead parameters and keep edits
   inside `OrderDetailsDialog`.
   - Status: **CLOSED 2026-11-14 17:2x** — owner chose to delete the dead parameters. The row no
     longer carries status/payment/cancel callbacks; `OrderDetailsDialog` is the only editing path.
   - 2026-11-14 16:2x: the visible row action is a `MoreVert` (⋮) button described as
     "More options", but it opens `OrderDetailsDialog` exactly like a row tap. Either it becomes
     the real menu this item needs, or it should read as a "view" affordance. Not changed.
   - **Filter freeze:** `Today` / `Yesterday` / `Custom` were approved as shipped on 2026-11-14
     and are not to be edited again.

L. **Status change is unreachable** from this screen (`updateOrderStatus` has no caller here). Fold into K.
   - Status: open decision.

M. **Optional period summary** (orders billed / gross / cancelled) above the table for the selected window.
   - Status: proposed; confirm value before building.

N. **Closing-time edits are retroactive for historical windows.** `BusinessDate.windowStart(date)`
   applies the *current* `openingTime`/`closingTime` to every business date, while
   `businessDayCutoffTime` has `cutoffEffectiveFrom` to pin history. So changing the closing time
   re-buckets past days in `Today`/`Yesterday`/`Custom` (an order after the old boundary can move
   to the adjacent day's list), even though stored bills keep their stamped business date.
   Decide: add an effective-from date for closing-time changes (Android + server
   `BusinessDay.closingBoundary` must move together), or accept and document the re-bucketing.
   - Status: open decision; **not implemented**, no behaviour changed in Session 010.

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
   - **2026-11-14 (Session 008):** the install that triggers this re-check has now happened on `ZA222J5KT7` (versionCode 38 / 1.0.38, `lastUpdateTime 2026-11-14 11:57:06`, `apps/android` clean at `176b2b34`, so the shipped code is unchanged since Sessions 004–005). The app was confirmed to open on Restaurant Configuration, but the collapse/expand and Billing Mode surface assertions were **not** executed. Still open.

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
