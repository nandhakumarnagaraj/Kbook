# Next Actions

Ordered by blocking value. Session references point at `DEVICE_SESSIONS/SESSION_LOG.md`.

## Device session — printing (2026-11-20, Session 078)
APK installed on `ZA222J5KT7` and exercised against the owner's real printer. Three defects fixed and device-verified; screenshots in `DEVICE_SESSIONS/screenshots/s078-*`…`s080-*`.
- **BUG-052 (P1) — FIXED, device-verified.** Print on an order opened a PDF chooser and never printed. Orders now reprints through `PrintCoordinator.printReceipt` (PDF only as the failure fallback); no chooser appeared afterwards and no exception was logged.
- **BUG-053 (P2) — FIXED, device-verified.** Test Printer flipped a healthy Bluetooth printer to "Not connected" because it disconnected in a `finally` block. Badge now stays **Connected** with the "Test print sent" toast.
- **BUG-054 (P3) — FIXED, device-verified.** A leftover Bluetooth kitchen profile was being probed in one-printer mode (two failed RFCOMM connects per refresh). Skipped now; 0 Bluetooth errors after the fix.
- **OWNER CONFIRMATION ONLY — app side proven.** Reprint of A01 (₹241.50, Tax Invoice A000075) from Orders → Print was written and flushed to `192.168.31.158:9100` (`deliver: successfully flushed 862 bytes`, `s081-print-done.png`). Only the physical slip content still needs the owner's eyes: check the shop header, the 5% GST line, and whether the logo is present — 862 bytes is small for a receipt with **Include Logo** on.
- **OPEN — no Google-review QR on receipts, by configuration.** The receipt prints the QR only when **Settings → Restaurant Configuration → Review Link** holds a valid `https://` URL; the shop has none, so the block is skipped silently (and the PDF path behaves the same). Needs the owner's Google Maps review link, then a reprint to confirm the QR renders; consider surfacing an inline warning in the field when the value is present but not a valid URL, since an invalid link currently disappears without feedback.
- **NOT RUN — GST-off reprint check.** The shop is GST-on and has no orders on 20 Nov; the check needs a temporary GST toggle plus an existing taxed bill, or a new bill. Left for the owner's go-ahead. Session 078 records a GST toggle that was made and restored.
- **NOT RUN — PIN lockout check.** Needs the app lock enabled with a known PIN.

## Code review findings — ALL FIXED (2026-11-20, Sessions 075–077)
Report: `RECOMMENDATIONS/CODE_REVIEW_2026-11-20.md`. Session 076 fixed the seven review findings; Session 077 fixed the four findings of the `AGENTS.md` pre-push review of those fixes. Full suite **556 tests, 0 failures**, `assembleDebug` green (APK 41,396,419 B); re-review of the fixed tree reports no findings on the changed lines. **Outstanding: install and confirm on the device** — the phone was not attached in either session.
- **Session 077 review (was: 1 major, 3 minor) — FIXED.** BUG-048 paper end/near-end masks were swapped (an empty roll read "Paper low", a low roll read "Paper out"); BUG-049 the PIN failure counter never reset, so one typo after any past lockout re-locked for 60s; BUG-050 refunded part-payment legs summed above their own mode row; BUG-051 receipt tax rows followed the current GST toggle, leaving a shared total with no visible component. **The reviewer was the same agent that wrote the code** (`--agent`, no independent reviewer can launch on this Windows machine) and none of the five scanners ran, so this is model-only — see `SESSION_LOG.md` Session 077.
- **BUG-041 (P1, money) — FIXED.** `BillCalculator.calculateBillTotals` is now the one computation behind the cart summary and the persisted-bill recalc, and the tax-inclusive flag is passed end to end, so ₹118 at 18% inclusive bills as ₹118, not ₹136. Confirm whether any live shop has `is_tax_inclusive` set; **historical bills are not retroactively corrected** — only bills computed after the install.
- **BUG-042 (P2, sync) — FIXED.** `RemoteStatusNormalizer` normalises the pulled bill fields and the enums read the same aliases, so `PAID`/`Completed` rows count as completed on every terminal. Worth a check with two terminals after install.
- **BUG-043 (P2, reporting) — FIXED.** Home and Reports both use `netAmount()` and COMPLETED-only, so a refund no longer makes the two screens disagree.
- **BUG-044 (P2, receipts) — FIXED.** Consolidation key now includes name and price; two deleted dishes stay two lines.
- **BUG-045 (P3, share text) — FIXED.** CGST/SGST print from the amounts, and non-GST shops get a sub-total plus the named custom-tax row.
- **BUG-046 (P2, security) — FIXED in code, UX unverified.** Five wrong PINs lock the screen for 1 minute, doubling to 16 minutes, persisted across restarts. **Needs a device check:** the lockout message and the keypad behaviour have not been seen on screen.
- **BUG-047 (P3, display) — FIXED.** Menu list uses `CurrencyUtils.formatPriceCompact`, so 149.50 shows as ₹ 149.50.

## Owner-reported defects — FIXED, hardware confirmation outstanding (2026-11-20, Sessions 073–074)
- **BUG-032 (P2) — staff can star/unstar dishes. FIXED (Session 074).** The star now renders only when `canWrite` and `setLocalFavourite` is owner-gated. Open follow-up: confirm by logging in as staff on the device — the star should be gone and the dish list read-only.
- **BUG-033 (P1) — connected 58 mm Bluetooth printer shows "Printer error". FIXED (Session 074) with 13 unit cases, paper masks corrected in Session 077 (BUG-048).** Decoder now reads offline/recovery bits and DLE EOT 3's own error bits; stale status bytes are drained before each query. The paper end/near-end masks were inverted until Session 077, so the badge now also distinguishes "Paper low" from "Paper out" correctly. **Open follow-up:** connect the 58 mm printer and confirm the card shows Connected/healthy instead of Printer error. If it still errors, capture the raw status bytes — the remaining suspect is a model that answers with a non-standard byte.
- **BUG-034 (P1) — GSTIN prints with GST disabled. FIXED (Session 074).** Thermal header now gates `GSTIN:` on `gstEnabled`. **Open follow-up:** print one bill with GST disabled and confirm the GSTIN line is gone while `INVOICE NO` remains.
- **BUG-035 (P2) — no "Get Feedback" QR on the thermal copy. FIXED (Session 074).** The receipt now prints a `GS v 0` raster QR (192 dots on 58 mm / 240 dots on 80 mm) behind `isValidReviewUrl`. **Open follow-up:** print on the real 58 mm printer — if the model ignores raster commands the QR will not appear (the URL text is only printed as a fallback when encoding fails, not when raster is unsupported). That is the one remaining hardware risk; a text-URL fallback on a blank-raster printer would need a per-model flag.
- **BUG-036 (P1) — reprints use today's GST toggle. FIXED (Session 077) by splitting identity from amounts.** GST identity (GSTIN, SAC, State Code, CGST/SGST labels) still follows the shop setting, so nothing GST-flavoured prints while GST is off. The amounts follow the bill: an older custom-tax bill keeps its row after GST is enabled, and a bill charged GST that is shared with the toggle off gets one neutral `Taxes:` row, so its total reconciles. Shops already on this build are unaffected on new bills. **Still open in one place:** the PDF prints no neutral row for an old GST bill (its page-height maths cannot be checked without a device), so a PDF reprint of that edge case still shows a total with a hidden component.
- **BUG-037 (P2) — PDF had no custom-tax row / could print no invoice number. FIXED (Session 074).** Compile-verified only; this repo has no PDF render test. **Open follow-up:** generate a PDF for a shop with a custom tax and confirm the row and the arithmetic.
- **BUG-038 (P3) — Tax Configuration overwrote `country`. FIXED (Session 074).** Field now seeds from the stored profile.
- **Tax setting with no control (not a defect, decision needed):** `isTaxInclusive` changes bill math but is not exposed anywhere in the app — it can only be set from the server/web side. If the owner should toggle tax-inclusive pricing on the till, that UI does not exist yet.
- **BUG-039 (P3) — permission table grants staff menu writes. OPEN, needs a server-side decision.** Nothing acts on those keys today and the app is owner-only in practice, but the permission model should match the read-only design. The Android table mirrors the server's `PermissionService.SHOP_STAFF_GRANTED_KEYS`, so both sides must change together — say the word and I will prepare the Android half and list the exact server keys to move into the owner-only list.
- **BUG-040 (P3) — web admin dashboard preview prints a hardcoded GSTIN/FSSAI. OPEN.** `business-dashboard-page.component.ts:711` shows the same tax identity for every business. Needs either the dashboard payload to expose `gstEnabled`/`gstin` or the line removed; web-admin work, not started.
- **Build/test evidence:** full unit suite `:app:testDebugUnitTest` green — **556 tests, 12 skipped, 0 failures** across 75 classes (includes `EscPosStatusDecoderTest` 13/13, `InvoiceFormatterTest` 8/8, `GenerateBillTextTest` 2/2). `assembleDebug` successful; APK installed (`versionCode 40`, 2026-11-20 15:38:18). The only later change was one comment line, compile-verified; the phone dropped off USB before that rebuild could be installed. **No printer, no screen walk, no logcat**, so the four hardware follow-ups above remain unverified. Details: `SESSION_LOG.md` Sessions 073–074.

## UX motion review — implementation (2026-11-20, Sessions 067–068, 070–071)
- **DONE (compile-verified) — all ten findings UM-01 … UM-10.** UM-02 … UM-09 in Session 068; UM-01 (shared reduced-motion policy `MotionPolicy.kt`, all loops/bounces wired) and UM-10 (origin-linked dialog entrance, pivot from the tapped order row) in Session 070. Exact effects in `RECOMMENDATIONS/UX_MOTION_REVIEW.md` §6; `:app:compileDebugKotlin` exit 0 twice. Not committed — these files also carry pre-existing owner work.
- **DONE (Sessions 069, 071 + 072) — debug APK installed:** latest `app-debug.apk` SHA-256 `6590E4…CC582`, `adb install -r` Success on `ZA222J5KT7` (most recent `lastUpdateTime` 14:36:53). APK contains all working-tree UM fixes plus pre-existing owner work.
- **OPEN — device verification for all implemented items:** report §7 checks 1–5 — *Remove animations* on (UM-01), slow-motion tab tap (UM-02), payment-success badge timing (UM-05), New Bill 32 dp hit boxes at default/200% font (UM-06), and order-dialog growth from top vs bottom rows (UM-10). Link screenshots into the report and SESSION_LOG when run.
- **Still not done:** no app launch, UI walk, new screenshot, or timing measurement. Source edits remain working-tree only.

## UI polish (2026-11-19, Sessions 037–038)
- **DONE (device-verified) — Shop Profile header gold:** `ShopConfigSection.kt` now uses `TextGold.copy(alpha = 0.7f)` instead of `TextLight`; screenshot `shop-profile-gold-2026-11-19.png`.
- **DONE (device-verified) — Save Configuration green:** the CTA now matches `SettingsSaveBar` (`SuccessGreen`, white content, grey disabled); screenshot `save-config-green-2026-11-19.png`.
- **DONE → SUPERSEDED — Orders table eye action:** `ReportViews.kt` used `Icons.Filled.Visibility`; replaced by the text "View" in Session 040.
- **DONE → SUPERSEDED — Orders action text "View":** the plain label was replaced by the outlined pill in Session 041.
- **DONE (device-verified) — Orders action outlined View pill:** 22 dp pill, 1 dp gold border, 5 dp corners, 48 dp target, opens Order Details (`orders-view-pill-2026-11-19.png`, `orders-view-pill-details-2026-11-19.png`).
- **DONE (device-verified) — Orders header "OrderNo":** one word, with OrderNo / Amount / Time / Action aligned to the data rows (`orders-header-orderno-2026-11-19.png`).
- **DONE (measured) — Orders column gaps:** no overlap; narrowest gutter Time→Action ≈27 dp, widest OrderNo→Amount ≈92–96 dp in data rows. Header/data centres within 1 px (`orders-column-gaps-2026-11-19.png`, Session 043).
- **DONE (device-verified) — Orders gaps evened out:** weights 0.85/1.05/1.05/1.05 and centred OrderNo put the data gaps at ~41–45/32–37/41 dp; 0.85 keeps the header safe at 320 dp (`orders-gaps-even-final-2026-11-19.png`, Session 044).
- **DONE (device-verified) — Orders title "Orders Details":** `OrdersScreen.kt` title changed; no overlap with back/search (`orders-title-details-2026-11-19.png`, Session 045).
- **DONE (device-verified) — compact black toasts:** snackbar toasts are 48 dp tall, 14 sp, 24 dp medallion, `#1B1B1B` surface with white text (`orders-toast-black-2026-11-19.png`, Session 046).
- **DONE (device-verified) — amount and View label white:** amount values and the View label use `OrdersTextPrimary`; the View outline stays gold (`orders-amount-view-white-2026-11-19.png`, Session 047).
- **DONE (device-verified) — Order Details dialog redesign:** status chip, muted labels/white values, items with dividers, plain total, and outlined secondary actions; payment/cancel flows still open (`order-dialog-redesign-2026-11-19.png`, Session 048).
- **DONE (device-verified) — dialog header simplified:** only "Order Details" and X remain; order id and status chip removed (`order-dialog-header-minimal-2026-11-19.png`, Session 049).
- **DONE (device-verified) — header centered with gold close:** title centered and X gold (`order-dialog-header-centered-gold-2026-11-19.png`, Session 050).
- **DONE (device-verified) — order id under title:** muted `#A02` restored under the centered title (`order-dialog-header-id-subtle-2026-11-19.png`, Session 051).
- **DONE (device-verified) — order id in details card:** header has only title + X; card starts with `Order No #A02` above the invoice number (`order-dialog-order-no-row-2026-11-19.png`, Session 052).
- **DONE (device-verified) — Time column brightness:** Time values now use `OrdersTextPrimary` + bold like the other columns (`orders-time-column-bright-2026-11-19.png`, Session 053).
- **DONE (device-verified) — dialog label opacity:** field labels and Total Amount use full-opacity `OrdersTextPrimary` (`order-dialog-labels-normal-2026-11-19.png`, Session 054).
- **DONE (device-verified) — dialog Order No without `#`:** card reads `A02`, matching the table (`order-dialog-no-hash-2026-11-19.png`, Session 055).
- **DONE (device-verified) — table/dialog consistency review:** all remaining dim text removed from the dialog item block (`order-dialog-review-2026-11-19.png`, Session 056).
- **DONE (device-verified) — table uses Settings bright text:** values and View label now `TextLight` (`orders-table-bright-2026-11-19.png`, Session 057).
- **DONE (device-verified) — table panel brightness:** panel fill now `CardBG`, matching Settings cards pixel-for-pixel (`orders-table-settings-brightness-2026-11-19.png`, Session 058). Superseded by Session 059's row cards.
- **DONE (device-verified) — report-style table:** header band + separate rounded order cards, white values (`orders-table-report-cards-2026-11-19.png`, Session 059).
- **DONE (device-verified) — black table cards:** header `DarkBrown1`, row cards near-black `OrdersCard` with thin border (`orders-table-black-cards-2026-11-19.png`, Session 060).
- **DONE (device-verified) — unified header:** header now `OrdersCard`, pixel-identical to row cards (`orders-table-unified-black-2026-11-19.png`, Session 061).
- **DONE (device-verified) — quiet-black table:** borderless `#2A1213` cards, 8 dp gaps, `#1F0C0D` header with gold hairline (`orders-table-quiet-black-2026-11-19.png`, Session 062).
- **DONE (device-verified) — dialog actions filled:** Share solid green, Cancel Order solid red, white content (`order-dialog-green-red-actions-2026-11-19.png`, Session 063).
- **DONE (device-verified) — Change Payment Mode filled:** solid `OrdersBronze #8A4B1F` with white content (`order-dialog-bronze-payment-2026-11-19.png`, Session 064).
- **OPEN — test data:** order A02 is cancelled on the device (cause unknown); should be re-seeded if a live two-order list is needed for screenshots.
- **DONE — Play Store AAB built (1.0.40):** `apps/android/app/build/outputs/bundle/release/app-release.aab`, versionCode 40 / 1.0.40, signed and zip-verified (`250ca1b0…3f914076`, Session 066). Version 39 was blocked in review and its versionCode is spent.
- **OPEN — Play upload:** upload 1.0.40, attach `app/build/outputs/mapping/release/mapping.txt`, and confirm the portrait hardware-feature warning clears; release build has not been runtime-tested (no bundletool on the host).
- **OPEN (not implemented) — review follow-ups:** no status shown in dialog (by request), close X is a 36 dp box, search/share still prefix `#`, dialog values still warm `OrdersTextPrimary`.

## Notification pill redesign (2026-11-19, Session 036)
- **DONE (compile-verified) — white KhanBook pill:** `KhanaNoticePill.kt` is shared by `KhanaBookSnackbar` and `InAppNotificationBanner`; 40 dp brand medallion with the monochrome mark, 16 sp standalone message (14 sp under a 14 sp bold title), 28 dp radius, 480 dp max width. Contrast pairs are recorded in `Color.kt` and CHANGELOG.
- **DONE (compile-verified) — P0/P1 accessibility:** banner live region; IME + nav-bar insets; 80 dp lift on `main/` routes; action-label contrast; length/accessibility-aware durations; dismissible errors; banner timeout pause and reachable exit. Non-action host visuals are `Indefinite`, so Material3's 4 s `Short` timeout no longer truncates the 2.5–6 s window (cross-check). `:app:compileDebugKotlin` exit 0.
- **DONE (device-verified) — visual, timer, close:** debug APK 18:54:00 (SHA `2433b013…6c757eb9`) on `ZA222J5KT7`. The Info pill cleared by 3.6 s; the offline-printer Error pill renders above the tab bar and its close X cleared it within 0.4 s. Screenshots: `notice-pill-home-toast-v2-2026-11-19.png`, `crosscheck-info-t0-2026-11-19.png`, `crosscheck-info-t36-2026-11-19.png`, `crosscheck-error-close-before-2026-11-19.png`, `crosscheck-error-close-after-2026-11-19.png`.
- **OPEN — remaining device checks:** the under-keyboard toast could not be triggered (printer Save is disabled for invalid input); the in-app push banner needs a real FCM message; TalkBack, 200 % font scale, a full landscape/tablet pass and RTL are untested.
- **OPEN — sticky bottom bars:** `new_bill`, `quick_start`, `role_access`, `online_payments_setup` and other `StickyBottomScaffold` screens still draw their CTA under the toast. Taps no longer pass through, but the lift is only wired for `main/`. Decide whether to publish a per-screen bottom obstruction or move the host into each scaffold.
- **DEFERRED (P2) — toast semantics:** global 2 s dedupe can suppress a repeated failure; a success still cancels an error; long messages truncate at four lines; foreground FCM still posts a tray notification and a banner for the same event.

## Tutorial status bar overlap (2026-11-19, Session 035)
- **DONE (compile-verified) — BUG-031:** `TutorialListScreen` and `InAppWebViewScreen` now apply `statusBarsPadding()`; they were the only `KhanaBookScreenScaffold` callers outside `MainScreen` without the top inset. `:app:assembleDebug` exit 0; APK `a90d4b25…d98f73b`.
- **DONE — install:** the 17:13 APK was installed on `ZA222J5KT7` at 17:20:03 (`adb install -r` Success).
- **OPEN — device check:** when ADB returns, open Watch Tutorials and a video, confirm the header starts below the status bar, and capture a screenshot. Pre-fix evidence is `artifacts/android-ui-tests/statusbar-initial-2026-11-19.png`; `current-after-tap-2026-11-19.png` is from an interrupted walk and is not proof.

## Notification Architecture & Preferences Audit (2026-11-19, Sessions 031–032)
- **INVALID — BUG-022:** `MainActivity.onNewIntent()` already calls `NotificationRouteManager.handleIntent(intent)`; nothing to fix.
- **DONE (compile-verified) — BUG-024:** banner gated by mapped channel with the money-critical lock list.
- **DONE (compile-verified) — BUG-025:** OS-blocked card + "Enable in Settings" on the preferences screen.
- **DONE (compile-verified) — BUG-026:** inventory channel restored; one `channelForType()` used by tray and banner.
- **DONE (compile-verified) — BUG-027:** inbox rows navigate through `routeForType()`.
- **DEFERRED — BUG-023 (P3):** replace delete/recreate channel toggles with an app-level preference; today it resets per-channel sound/vibration.
- **DEFERRED — BUG-028 (P1):** cloud-synced notification preferences endpoint + multi-terminal fan-out.
- **OPEN — BUG-029 (P2):** only `FssaiReminderWorker` bypasses Room (no closing-shift worker exists).
- **DONE — device:** APK `0b059e0c…` installed 16:05:01. Confirmed: Back from Notifications Preferences lands on App Settings; the dirty Restaurant Configuration header arrow opens "Unsaved Changes"; `khanabook_inventory_v2` exists. **Open:** the BUG-025 blocked card cannot be reached because `pm revoke POST_NOTIFICATIONS` reads `granted=true` again within seconds of the next launch; verify by turning the app off in Android Settings → Notifications. **Open:** BUG-020 Home → Sync Center → Back device walk (not reproducible today: Home only shows the card when sync issues exist).
- **OPEN — BUG-030 (P2):** `MainActivity` re-requests `POST_NOTIFICATIONS` on every launch; on `ZA222J5KT7` a revoked permission reads `granted=true` within seconds, silently undoing an OS-level block and making the BUG-025 card unreachable. Needs a decision: request once after onboarding, or only from an explicit "Enable notifications" action.

## Show Taps touch indicator (2026-11-19, Session 030)
- **DONE (static + compile):** permission-free touch dot implemented in `core/touch/` (config, draw-only View, Activity host, Compose overlay) and wired once in `MainActivity`; `:app:compileDebugKotlin` exit 0. Full architecture and flag usage: `OPERATIONS/TOUCH_INDICATOR_OVERLAY.md`.
- **DONE — device verification (partial):** APK `49640f2d…c8ad9135` installed on `ZA222J5KT7` and launched without FATAL. A scripted 12 s drag produced a ~100 px highlight at the exact interpolated finger position (screenshot `artifacts/android-ui-tests/touch-indicator-drag3-2026-11-19.png`).
- **OPEN — device verification (remaining):** two-finger simultaneity, fade-out timing, pass-through on scrollable lists/tab-swipe, and dots inside dialogs/bottom sheets.
- **DONE (compile-verified) — dialog coverage:** `TouchAwareDialog` added and adopted by the five shared Compose-UI `Dialog` wrappers (`KhanaBookDialog` → also covers `KhanaBookSelectionDialog`, `KhanaBookLargeDialog`, `CustomDateRangePickerDialog`, `KhanaTimePickerDialog`, `OrderDetailsDialog`); the two printer `ModalBottomSheet`s wrap their content with `TouchIndicatorOverlay`. `:app:compileDebugKotlin` exit 0 after the change.
- **OPEN — decision (remaining floating windows):** seven raw Material3 `AlertDialog` sites, the M3 `DatePickerDialog`, and DropdownMenu/Popup surfaces still have no dots. Covering them means either changing dialog layout or replacing the Material3 component; needs explicit authorisation. See `OPERATIONS/TOUCH_INDICATOR_OVERLAY.md`.
- **DONE (owner decision) — two toggles only:** a temporary "Show taps" switch was added to Interaction Feedback and device-verified (OFF→no dot, ON→dot, persisted across relaunch), then **removed** because it is a demo aid, not staff feedback. The screen is back to "Menu item sound" + "Touch feedback" only; final APK `5fe18ce2…096c64e7` installed 15:18:32 and device-verified.
- **DONE (owner follow-up) — default OFF in all builds:** the owner reported taps still showing after the switch was removed. `TouchIndicatorConfig.DEFAULT_ENABLED` is now `false` (no `BuildConfig.DEBUG` default) and the device pref was written to `false`. Final APK `83cce9a4…b470b94` installed 15:29:22. A/B held-touch verification: flag `true` → 11,661 gold dot pixels at the touch point; flag `false` → **0**. Device left off.
- **DONE (final owner decision, owner-tested) — "Touch feedback" controls the dots:** repurposed the existing second toggle in Settings → App Settings → Interaction Feedback: ON writes `show_touch_indicator=true` and shows the dot, OFF writes `false` and hides it. Description changed to "Show a dot under each finger on the screen". "Menu item sound" and "Voice Soundbox (Audio Alert)" (Payment Configuration) both default OFF. Final APK `aebb0c82…d5f38d4d5` installed 15:40:01; UI shows both Interaction Feedback switches unchecked by default and the ON/OFF dot behavior was device-verified; owner confirmed "all good" after their own test.
- **Default state:** OFF in debug and release for the dot toggle, menu item sound and voice announcement; programmatic control remains `TouchIndicatorConfig.setEnabled(context, true)`.

## App Settings navigation audit (2026-11-19, Session 029)
- **FIXED + DEVICE-VERIFIED:** Notification Preferences header wrapped to two lines; now `Notification Preferences` on one line (start-aligned, 20 sp compact style, `maxLines = 1` + ellipsis guard in `KhanaBookScreenScaffold`). Installed in APK `2182ee58…` (`lastUpdateTime` 14:13:26); the header node measures 43 px tall on the device.
- **DONE (compile-verified, device pending) — BUG-019:** the header back arrow on Restaurant Configuration bypasses `ShopConfigView`'s `BackHandler`/"Unsaved Changes" dialog and discards edits silently; system back asks. Needs the dirty state hoisted to `SettingsScreen`.
- **DONE (compile-verified, device pending) — BUG-021 (P1):** Back from Notifications Preferences lands on the Home tab instead of the App Settings list; reproduced twice on `ZA222J5KT7` (Session 029). `MainScreen` re-initialises from `tab=0` after the nav pop. Fix by routing the tab/section through the destination or popping to the exact `main/...` entry.
- **DONE (compile-verified, device pending) — BUG-020:** Back from a deep-linked subsection (Home → sync issues → Sync Center) always goes to the App Settings list, not to where the user entered. Needs the entry point recorded or the nav destination popped.
- **DEAD CODE:** the `"compliance_documents"` branch in `SettingsScreen` has no caller and is not in `settingsSubSections`.
- **BY DESIGN?** The bottom bar hides on `shop` and `menu_config`; tab state is retained when leaving and re-entering Settings from another tab (returns to the last subsection, not Settings home). Confirm the intended behaviour.

## Watch Tutorials catalogue + player (2026-11-19, Sessions 027 + 033 + 034)
- **DONE — in-app list:** `Watch Tutorials` (About App and Quick Start) navigates to `Routes.TUTORIALS` → `TutorialListScreen.kt`; selecting a title navigates to `Routes.tutorialVideo(fileName)` → `InAppWebViewScreen.kt`. No `ACTION_VIEW` on this path.
- **DONE — video URL loaded directly:** the HTML5 `<video>` data-URL wrapper failed with `net::ERR_FAILED`; the player now calls `WebView.loadUrl("https://kbook.iadv.cloud/cdn/tutorials/<fileName>")`. Only plain file names (`[A-Za-z0-9._-]+`, ≤120 chars, no `..`) are accepted.
- **DONE — lifecycle fix:** WebView release moved to `AndroidView.onRelease`; the previous `DisposableEffect(webView)` destroyed the live view when the key changed from `null` to the view. Latest logs show `screen entered → onPageStarted/onPageFinished → BackHandler invoked → navigateBack: canGoBack=false → WebView released → screen disposed`.
- **DONE — device-verified:** all three titles (`Create a Quick Bill`, `Cancel Bill / Change Payment Mode`, `Continue with Google`) opened and played on `ZA222J5KT7`; Back returned to the list; no external browser. Screenshots: `tutorial-webview-in-app-2-2026-11-19.png`, `tutorial-player-direct-2026-11-19.png`.
- **OPEN — owner action:** upload `ops/cdn/tutorials/videos.json` to `/var/www/cdn.kbook.iadv.cloud/tutorials/videos.json` and confirm `https://kbook.iadv.cloud/cdn/tutorials/videos.json` returns 200. Until then the app logs `Manifest HTTP 404` and shows the bundled fallback list. The available tools are read-only on the VPS.
- **OPEN — version bump:** still 38 / 1.0.38.
- **OPTIONAL:** remove the `KHANABOOK_WEB` diagnostic logs once no longer needed; they are harmless but noisy.

## Notification labels and stock traces (2026-11-19, Sessions 033 + 034)
- **DONE (UI only) — EOD Reports:** `NotificationChannelCatalog` maps `khanabook_settlement_v2` to the title `EOD Reports` and description `Daily EOD report and payout notifications`. The OS channel name/ID is unchanged, so saved per-channel settings are not reset.
- **DONE (UI only) — inventory row removed:** the catalog filters out `CHANNEL_INVENTORY`, so `Low Stock Alerts` no longer appears in Notification Preferences. The channel remains registered because the server still sends `inventory_low`.
- **DONE — owner decision (2026-11-19):** the menu-card grey scrim is driven by `item.isAvailable` (a manually disabled dish), not stock. Owner chose **Keep it**; only the misleading comment was corrected. Do not remove the indicator.
- **DONE — device-verified:** Notification Preferences shows `EOD Reports` with `Daily EOD report and payout notifications` and no `Low Stock Alerts` row (`notification-prefs-eod-reports-2026-11-19.png`).

## Save Configuration bottom gap (2026-11-19, Session 026)
- **DONE:** trailing `spacing.huge` (48 dp) after the CTA replaced with `spacing.medium` (16 dp); built and installed (`fc918159…`, `lastUpdateTime` 14:02:10), launched with no crash.
- **OPEN — visual check:** open Restaurant Configuration, scroll to the bottom, and confirm the gap under `Save Configuration` now matches the screen's normal bottom margin. Consider whether the card's own 24 dp bottom padding plus 16 dp still reads as too much on a tall phone.
- **OPEN — version bump:** still 38 / 1.0.38 across today's APKs.

## Shop Profile header + bigger logo (2026-11-19, Session 025)
- **DONE:** Card 1 header is now a single `Shop Profile` title (the bills/receipts subtitle is gone) and the logo placeholder is 112 dp with a 48 dp empty-state icon. Built and installed twice this session (`b9c7ce46…` then `a73ba789…`); app launched with no crash.
- **OPEN — visual check:** open Restaurant Configuration on `ZA222J5KT7` and confirm the 112 dp preview plus `Change Logo` still sit side by side on a phone width, and that long shop names below are unaffected.
- **OPEN — dead copy:** `ShopLogoSection` in `ShopConfigComponents.kt` is still unused and still says `Shop Logo` / `Shown on bills and receipts`. Either migrate the caller to it or delete it; do not let two versions of this copy exist.
- **OPEN — version bump:** still 38 / 1.0.38; two different APKs now share the same version number.

## Business Hours toggle + Easebuzz launch gate (2026-11-19, Session 024)
- **DONE (uncommitted):** `BusinessHoursModeToggle` is now the live Business Hours control; `BusinessHoursOptionCard` is deleted. This resolves the Session 020 “decide the business-hours refactor” item in favour of migrating the caller (option a) — `ShopLogoSection` remains unused and is a separate decision.
- **DONE (uncommitted):** Easebuzz is hidden in the UI exactly like the September Play Store gate: no `PAYMENT_LINK` in the billing mode list and a static “Online Payments / coming soon” card in Payment settings. `1b0d39be` is the commit that previously restored it; re-apply the same one-line + block swap to show it again.
- **OPEN — device verification:** on `ZA222J5KT7`, check Business Hours toggles Default ↔ Custom with the right panel showing and the reset subtitle updating; check Payment settings shows no Easebuzz toggle/hub; check the billing payment-mode list has no “Send Payment Link”.
- **OPEN — docs consistency:** the Play Store checklist does not mention this UI gate; decide whether the gate belongs there or in a feature flag.
- **OPEN — version bump:** still 38 / 1.0.38.

## Printer-fix debug APK installed (2026-11-19, Session 023)
- **DONE:** `:app:assembleDebug` → BUILD SUCCESSFUL; APK SHA-256 `c1540b33…e4a79819` installed on `ZA222J5KT7` (`lastUpdateTime` 2026-11-19 13:46:29). App launched to `MainActivity`, no crash in logcat. Screenshot `artifacts/android-ui-tests/printer-config-fixes-2026-11-19.png`.
- **OPEN — the actual UI check:** open Settings → Printer on the device and confirm the screen now ends after the printer cards (no "Print Options" card, no Back/Save row), kitchen Enable/Paper Size survive leaving the screen, and an offline printer reads "Not connected". Build + launch do not prove any of this.
- **OPEN — version bump:** still 38 / 1.0.38, same as the previous build; bump before any owner-facing distribution.
- **OPEN — Nord device:** online during this session but not targeted. Ask before updating it.

## Printer Configuration fixes done; device check outstanding (2026-11-19, Session 022)
- **DONE (uncommitted):** BUG-013 … BUG-018 fixed. The "Print Options" card (Mask Customer Phone + Back/Save) was removed at the owner's request; every printer option now persists as it is changed. `:app:compileDebugKotlin` exit 0.
- **Behaviour change:** the printer screen has no Save/Back action row any more. Leaving is via the screen header back arrow; there is no discard path because nothing is staged.
- **OPEN — device verification:** on `ZA222J5KT7` walk Settings → Printer and check: customer and kitchen toggles/paper size survive closing and reopening; Test Printer works on both roles; a Wi-Fi printer saves by IPv4 and by hostname; a failing Wi-Fi save leaves the dialog open with the values; an offline printer reads "Not connected".
- **OPEN — `maskCustomerPhone`:** removing the toggle left `SettingsViewModel.updateMaskCustomerPhone` with no caller and made the setting non-editable on Android. Decide whether it moves to Web Admin or leaves the synced profile.
- **OPEN — recommendation:** the Bluetooth picker still has no "No printers found" empty state (unchanged this session).

## Printer Configuration UI defects (2026-11-19, Session 021 — static only)
- **DONE (static):** reviewed the printer-configuration screen and registered BUG-013 … BUG-018. No source outside `ANDROID_POS_DOCUMENTATION/` was changed; nothing was implemented.
- **Decide first — BUG-014:** the kitchen card's Enable and Paper Size write only local Compose state, while the customer card writes to Room immediately. Either persist kitchen changes on toggle (mirror the customer path) or make the whole screen Save-gated. Today, Back silently discards kitchen changes, and **Test Printer** on the kitchen card prints with the stored paper size, not the one just selected.
- **Then — BUG-013:** `maskPhone` at `PrinterConfigSection.kt:153` is the only field whose `remember` has no key, so it can save `maskCustomerPhone = true` over a stored `false` when the profile arrives after first composition (the same late-profile window the Save button comment documents) or after a master pull while the screen is open.
- **Cheap UI fixes:** BUG-015 (close the Wi-Fi dialog only after `WifiSaved`/`WifiSaveFailed`, so the spinner can show and typed values survive a failure), BUG-016 (allow hostnames or relabel the field "IP address"), BUG-017 (offline label should be "Not connected"/"Offline", not "Ready"), BUG-018 (apply the border to the card instead of discarding the Modifier).
- **Recommendation:** add a "No printers found" state to the Bluetooth sheet; a zero-result scan currently looks like a broken screen.
- **Verification needed:** none of the six has been reproduced on hardware. Walk Settings → Printer on `ZA222J5KT7` before closing any of them.

## Debug APK update + half-applied refactor (2026-11-19, Session 020)
- **DONE:** rebuilt `:app:assembleDebug` from the current working tree and installed on `ZA222J5KT7` (`versionCode=38`, `lastUpdateTime=2026-11-19 13:25:47`). App launched to `MainActivity`, no crash. Evidence: `SESSION_LOG.md` Session 020, screenshot `artifacts/android-ui-tests/debug-apk-update-2026-11-19.png`.
- **Build blocker resolved (owner-authorised):** re-added `BusinessHoursOptionCard` verbatim into `ShopConfigComponents.kt` so the unchanged caller `ShopConfigSection.kt:815,830` compiles. This source edit is **not committed**.
- **OPEN — decide the business-hours refactor:** `ShopConfigComponents.kt` (uncommitted) contains `BusinessHoursModeToggle` + `ShopLogoSection`, but `ShopConfigSection.kt` still uses the old `BusinessHoursOptionCard` pair and inline Shop Logo block. Both implementations now exist; the new ones are unused. Owner previously chose "restore old card" to unblock the install. Either (a) migrate the caller to the segmented toggle + `ShopLogoSection` (Sessions 014/015 design; needs device verification) or (b) drop the unused new components. Do not ship both paths.
- **OPEN — version bump:** working tree did not bump `versionCode`/`versionName` (still 38 / 1.0.38), so the installed APK is indistinguishable by version from the prior one. Bump before owner distribution.
- **OPEN — Nord device:** `adb-dbd06e58-…` (Nord AC2001) was offline at first check; not updated. Ask owner before targeting it.
- **Reality check:** the 2026-11-18 CHANGELOG "segmented mode switch" entry no longer matches the live device — commit `17750b4c` restored the old cards. The live APK shows old cards.

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
