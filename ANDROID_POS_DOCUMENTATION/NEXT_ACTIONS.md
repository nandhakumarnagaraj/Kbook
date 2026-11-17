# Next Actions

Ordered by blocking value. Session references point at `DEVICE_SESSIONS/SESSION_LOG.md`.

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
