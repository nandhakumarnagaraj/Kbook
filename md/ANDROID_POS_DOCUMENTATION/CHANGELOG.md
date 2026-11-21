# Changelog

## [2026-11-20] - Reduced-motion policy and dialog origin motion (SES-20261120-070/071)
### Added
- `core/designsystem/MotionPolicy.kt`: `rememberReducedMotion()` observes Android animator/transition duration scales; 120 ms shared reduced-motion fade; `motionDuration()` and `dialogEntrance()` helpers.
### Changed
- All loops and bounces now honour reduced motion: shimmer, closing-alert pulse, Home sync rotation/glow, splash logo pop/halo/particles/text slide, tab press, payment badge, PIN shake, route transitions, list staggers, setup crossfade and loading overlay.
- Dialogs use a short origin-linked entrance (0.94→1 scale + fade, 180 ms; fade-only 120 ms when reduced). Order Details grows from the tapped order row's vertical position.
### Verified
- `./gradlew :app:compileDebugKotlin --offline` → BUILD SUCCESSFUL; `./gradlew :app:assembleDebug --offline` → BUILD SUCCESSFUL.
- Debug APK SHA-256 `6590E4…CC582` installed on `ZA222J5KT7` (SES-20261120-071). No UI walk or screenshot; report §7 device checks remain open.
- Working-tree only — not committed or pushed (files also carry pre-existing owner work).

## [2026-11-20] - UX motion fixes UM-02..UM-09 (SES-20261120-068)
### Changed
- Route exit/pop-exit 300 → 120 ms with ease-in; tab icon uses a 120 ms area-preserving press instead of a 0.7→1.25 spring bounce.
- Shared cards use the theme ripple again (the 2% whole-card scale that shrank text was removed).
- Payment-success ripples/confetti shortened from a 1300 ms tail to ~420 ms; checkmark pop kept.
- Wrong-PIN shake 450 → 350 ms; Home/Orders entrance gaps 80 → 45 ms; sync-stage crossfade 400/300 → 250/120 ms; loading-overlay exit 200 → 100 ms.
- New Bill quantity controls reserve a 48 dp touch target around the 32 dp visual button.

### Verified
- `./gradlew :app:compileDebugKotlin --offline` → BUILD SUCCESSFUL. No device verification; see `RECOMMENDATIONS/UX_MOTION_REVIEW.md` §6.
- `./gradlew :app:assembleDebug --offline` → BUILD SUCCESSFUL; debug APK SHA-256 `0A3725…140E517` installed on `ZA222J5KT7` (SES-20261120-069). No UI walk or screenshot.
- Working-tree only — not committed or pushed (files also carry pre-existing owner work).

## [2026-11-20] - Portrait hardware feature optional; 1.0.40 bundle (SES-20261120-066)
### Fixed
- Play review blocked 1.0.39 because the Easebuzz `PWECheckoutActivity` portrait lock made `android.hardware.screen.portrait` implicitly required, hiding the app from landscape-only devices. The manifest now declares that feature `required="false"`; `screen.landscape` was already optional.
### Build
- New bundle: versionCode 40 / versionName 1.0.40, 17.9 MB, SHA-256 `250ca1b0…3f914076`, signed and zip-verified. VersionCode 39 cannot be reused.

## [2026-11-20] - Release bundle 1.0.39 (SES-20261120-065)
### Build
- `bundleRelease` produced `app-release.aab` (17.9 MB, SHA-256 `bceca6fb…42bd77f5`) signed with the release key, versionCode 39 / versionName 1.0.39.
- Version bump applied in git-ignored `local.properties` only; no source changes for this build.

## [2026-11-19] - Change Payment Mode filled in warm bronze (SES-20261119-064)
### Changed
- Change Payment Mode is now a solid `OrdersBronze #8A4B1F` button with white content, completing the filled action stack (gold Print, green Share, bronze payment, red Cancel). New `OrdersBronze` token.

### Verified
- Installed on `ZA222J5KT7` at 23:48:36, versionCode 38; screenshot `order-dialog-bronze-payment-2026-11-19.png`.

## [2026-11-19] - Filled Share/Cancel buttons in the order dialog (SES-20261119-063)
### Changed
- Share is now a solid `OrdersShareGreen` button and Cancel Order a solid `DangerRed` button, both with white content. Previously they were outlined with coloured text.

### Verified
- Installed on `ZA222J5KT7` at 23:35:22, versionCode 38; screenshot `order-dialog-green-red-actions-2026-11-19.png`.

## [2026-11-19] - Quiet-black Orders table (SES-20261119-062)
### Changed
- Borderless row cards (`OrdersRowCard #2A1213`) separated by 8 dp gaps; header band `OrdersHeaderBand #1F0C0D` closed by a 1 dp gold hairline. Removes the outline-defined card look.

### Verified
- Installed on `ZA222J5KT7` at 23:29:24, versionCode 38; screenshot `orders-table-quiet-black-2026-11-19.png`.

## [2026-11-19] - Orders header matches card fill (SES-20261119-061)
### Changed
- Header band background now `OrdersCard`, identical to the row cards (sampled `(38, 15, 16)` on device). No layout or text changes.

### Verified
- Installed on `ZA222J5KT7` at 23:25:53, versionCode 38; screenshot `orders-table-unified-black-2026-11-19.png`.

## [2026-11-19] - Orders table cards go black (SES-20261119-060)
### Changed
- Table header band now `DarkBrown1` and row cards now near-black `OrdersCard` with a thin `OrdersDivider` border, per the black report reference. White values and all layout unchanged.

### Verified
- Installed on `ZA222J5KT7` at 23:24:01, versionCode 38; screenshot `orders-table-black-cards-2026-11-19.png`.

## [2026-11-19] - Orders table uses report-style row cards (SES-20261119-059)
### Changed
- Removed the single brown table panel; the header is its own rounded band and each order is a separate rounded card with 4 dp gaps, matching the Orders Report table the owner referenced.
- Row values and `View` now pure white; headers stay gold; columns and interactions unchanged. Supersedes the panel fill from Session 058.

### Verified
- Installed on `ZA222J5KT7` at 23:18:59, versionCode 38; screenshots `orders-table-report-cards-2026-11-19.png` and `orders-row-tap-check-2026-11-19.png` (View opens the dialog).

## [2026-11-19] - Orders table panel matches Settings brightness (SES-20261119-058)
### Changed
- Orders table panel fill switched from `OrdersCard` near-black to `CardBG`, the same lighter brown Settings cards use (`#683B1B` rendered). Text colours and layout untouched.

### Verified
- Installed on `ZA222J5KT7` at 23:08:29, versionCode 38; screenshot `orders-table-settings-brightness-2026-11-19.png`; panel and Settings card sample the same pixel value.

## [2026-11-19] - Orders table uses Settings bright white (SES-20261119-057)
### Changed
- Table values (OrderNo, Amount, Time) and the View label now use `TextLight` (`#F5E6C8`), matching Settings, instead of the warmer `OrdersTextPrimary`. Headers remain gold.

### Verified
- Installed on `ZA222J5KT7` at 22:56:18, versionCode 38, SHA-256 `ed1b6a57…3733f061ec`; screenshot `orders-table-bright-2026-11-19.png`.

## [2026-11-19] - Table/dialog consistency review (SES-20261119-056)
### Changed
- Removed the last dim text in the dialog: item name alpha 0.92 → full, `x1` quantity and the `Items (n)` / `Amount` captions → `OrdersTextPrimary`.

### Verified
- Installed on `ZA222J5KT7` at 22:44:06, versionCode 38, SHA-256 `3ac66e81…bf061141`; screenshots `orders-review-table-2026-11-19.png`, `order-dialog-review-2026-11-19.png`.

## [2026-11-19] - Dialog Order No drops `#` (SES-20261119-055)
### Changed
- Order No row now shows `A02` instead of `#A02`, matching the Orders table; the table comment was corrected accordingly.

### Verified
- Installed on `ZA222J5KT7` at 22:38:36, versionCode 38, SHA-256 `7d52a937…4b60f76`; screenshot `order-dialog-no-hash-2026-11-19.png`.

## [2026-11-19] - Dialog field labels at normal opacity (SES-20261119-054)
### Changed
- DetailRow labels and the Total Amount label now use `OrdersTextPrimary` at medium weight instead of the muted secondary tone; values keep semibold emphasis.

### Verified
- Installed on `ZA222J5KT7` at 22:34:56, versionCode 38, SHA-256 `8ea85159…26a0fa84`; screenshot `order-dialog-labels-normal-2026-11-19.png`.

## [2026-11-19] - Time column matches other table columns (SES-20261119-053)
### Changed
- Orders table Time values now use `OrdersTextPrimary` and bold weight, matching OrderNo and Amount instead of the muted secondary tone.

### Verified
- Installed on `ZA222J5KT7` at 22:29:48, versionCode 38, SHA-256 `0da20bd8…c51fce5d7`; screenshot `orders-time-column-bright-2026-11-19.png`.

## [2026-11-19] - Order id moved into details card (SES-20261119-052)
### Changed
- Removed `#A02` from the dialog header; the card now starts with `Order No  #A02` above `Tax Invoice No`.

### Verified
- Installed on `ZA222J5KT7` at 22:25:33, versionCode 38, SHA-256 `300ae2e9…33367a6f`; screenshot `order-dialog-order-no-row-2026-11-19.png`.

## [2026-11-19] - Order id restored under centered title (SES-20261119-051)
### Changed
- The dialog header shows `#A02` in muted bodySmall under the centered "Order Details" title, with a 4 dp gap before the divider; the gold X is unchanged.

### Verified
- Installed on `ZA222J5KT7` at 22:19:21, versionCode 38, SHA-256 `67bda86c…429905cec`; screenshot `order-dialog-header-id-subtle-2026-11-19.png`.

## [2026-11-19] - Dialog header centered, gold close (SES-20261119-050)
### Changed
- "Order Details" is centered across the dialog header and the close X uses the same gold as the title instead of muted gray.

### Verified
- Installed on `ZA222J5KT7` at 22:14:15, versionCode 38, SHA-256 `50d62889…d69b982a`; screenshot `order-dialog-header-centered-gold-2026-11-19.png`.

## [2026-11-19] - Dialog header simplified (SES-20261119-049)
### Changed
- Order Details dialog header now contains only the gold "Order Details" title and the close X; the order-id subtitle and status chip were removed along with the unused chip composable.

### Verified
- Installed on `ZA222J5KT7` at 22:10:35, versionCode 38, SHA-256 `dc757b9d…49649c37`; screenshot `order-dialog-header-minimal-2026-11-19.png`.

## [2026-11-19] - Order Details dialog redesigned (SES-20261119-048)
### Changed
- Header now shows the order id and a status chip (no receipt glyph); identity rows use muted labels and white values; items get a count/Amount header and dividers; the total is a plain white row; Print is the only filled action, with Share/Change Payment Mode/Cancel as outlines.

### Verified
- Installed on `ZA222J5KT7` at 21:51:39, versionCode 38, SHA-256 `a85912df…ad34242`; screenshots `order-dialog-redesign-2026-11-19.png`, `order-dialog-payment-picker-2026-11-19.png`, `order-dialog-cancel-confirm-2026-11-19.png`.

## [2026-11-19] - Orders amount and View label white (SES-20261119-047)
### Changed
- Amount values are now `OrdersTextPrimary` instead of gold, matching the order ids; the View pill's label is white too, keeping its gold outline.

### Verified
- Installed on `ZA222J5KT7` at 21:42:52, versionCode 38, SHA-256 `56d1c0e7…ae378928`; screenshot `artifacts/android-ui-tests/orders-amount-view-white-2026-11-19.png`.

## [2026-11-19] - Compact black toasts (SES-20261119-046)
### Changed
- Snackbar toasts are now a compact WhatsApp-style bar: 48 dp tall, 24 dp medallion, 14 sp text, 14 dp corners, near-black `#1B1B1B` surface with white text and a 1 dp hairline. The push banner keeps the white 40 dp pill.

### Verified
- White compact toast measured 379.4×48.0 dp (was 72 dp); black version captured on Orders; final build installed 21:38:17, versionCode 38, SHA-256 `e2397475…6a20f34`.
- Screenshots: `orders-toast-black-2026-11-19.png`, `orders-toast-capture-2026-11-19.png`.

## [2026-11-19] - Orders title "Orders Details" (SES-20261119-045)
### Changed
- The Orders screen's top title now reads "Orders Details" instead of "Orders"; back and search buttons are unchanged.

### Verified
- Installed on `ZA222J5KT7` at 21:22:23, versionCode 38, SHA-256 `7b164205…dbb10212`; screenshot `artifacts/android-ui-tests/orders-title-details-2026-11-19.png`.

## [2026-11-19] - Orders column gaps evened out (SES-20261119-044)
### Changed
- Column weights are now 0.85 / 1.05 / 1.05 / 1.05, and OrderNo is centred, so the data-row gaps fall from 96/37/27 dp to 41–45/32–37/41 dp on the tested phone while keeping the OrderNo header readable on 320 dp screens.

### Verified
- Installed on `ZA222J5KT7` at 21:20:10, SHA-256 `96b3b7b4…65e08637`; screenshots `orders-gaps-even-final-2026-11-19.png`, `orders-gaps-even-details-2026-11-19.png`.

## [2026-11-19] - Orders header "OrderNo" (SES-20261119-042)
### Changed
- The Orders table's first header is now `OrderNo` (one word), matching the report tables. The four headers remain OrderNo, Amount, Time, Action, and sit on the same column weights and padding as the rows.

### Verified
- `:app:assembleDebug` exit 0; installed on `ZA222J5KT7` at 20:58:44, versionCode 38, SHA-256 `b576b377…a4ce06`.
- Screenshot: `artifacts/android-ui-tests/orders-header-orderno-2026-11-19.png`.

## [2026-11-19] - Orders action outlined "View" pill (SES-20261119-041)
### Changed
- The Orders action is now a 22 dp outlined pill — 1 dp gold border at 75% alpha, 5 dp corners, 8 dp horizontal padding, 12 sp bold "View" — inside the 48 dp click target, matching the report table's View button. Supersedes the plain label from SES-20261119-040.

### Verified
- `:app:assembleDebug` exit 0; installed on `ZA222J5KT7` at 19:41:18, versionCode 38, SHA-256 `a1b7c4cf…3c7872`.
- Screenshots: `artifacts/android-ui-tests/orders-view-pill-2026-11-19.png`, `orders-view-pill-details-2026-11-19.png`.

## [2026-11-19] - Orders action is text "View" (SES-20261119-040)
### Changed
- The Orders table's action is now a plain gold bold "View" label with a 48 dp hit area; the eye icon and its 36 dp disc were removed. `onClickLabel = "View order <id>"` keeps the TalkBack wording. Supersedes the eye from SES-20261119-039.

### Verified
- `:app:assembleDebug` exit 0; installed on `ZA222J5KT7` at 19:30:25, versionCode 38, SHA-256 `d0884f3c…46acc02`.
- Screenshots: `artifacts/android-ui-tests/orders-view-text-2026-11-19.png`, `orders-view-text-details-2026-11-19.png`.

## [2026-11-19] - Orders table view icon (SES-20261119-039)
### Changed
- The Orders table's action now uses `Icons.Filled.Visibility` instead of the vertical three-dot `MoreVert`, matching its existing "View order" description. The gold disc, 48 dp target and Order Details action are unchanged.

### Verified
- `:app:assembleDebug` exit 0; installed on `ZA222J5KT7` at 19:25:17, versionCode 38, SHA-256 `e20366a1…8c8bdb`.
- Screenshots: `artifacts/android-ui-tests/orders-view-icon-2026-11-19.png`, `orders-view-icon-details-2026-11-19.png`.

## [2026-11-19] - Save Configuration CTA green (SES-20261119-038)
### Changed
- Restaurant Configuration → "Save Configuration" now matches the shared Save button: `SuccessGreen` container, white content, grey disabled state. Shape, height and width are unchanged.

### Verified
- `:app:assembleDebug` exit 0; installed on `ZA222J5KT7` at 19:18:15, versionCode 38, SHA-256 `aabdafcd…7d25`.
- Screenshot: `artifacts/android-ui-tests/save-config-green-2026-11-19.png`.

## [2026-11-19] - Shop Profile header gold (SES-20261119-037)
### Changed
- Restaurant Configuration → "Shop Profile" now uses `TextGold.copy(alpha = 0.7f)` instead of `TextLight`, matching the Shop Name field label.

### Verified
- `:app:assembleDebug` exit 0; installed on `ZA222J5KT7` at 19:12:18, versionCode 38, SHA-256 `ee6df8d0…b0ec8e`.
- Screenshot: `artifacts/android-ui-tests/shop-profile-gold-2026-11-19.png` (before: `shop-profile-before-2026-11-19.png`).

## [2026-11-19] - White KhanBook notice pill + notification accessibility fixes (SES-20261119-036)
### Added
- `core/designsystem/KhanaNoticePill.kt`: one white floating pill used by both the snackbar host and the in-app push banner. 28 dp radius, 40 dp brand-purple medallion with the white-tinted monochrome KhanBook mark, 16 sp standalone message (14 sp message under a 14 sp bold title in the banner), optional title/action/close, 480 dp max width.
- Notice-only colour tokens in `core/theme/Color.kt`; every pair used on the white surface is verified at 4.5:1 or better (text 17.19:1, secondary 10.09:1, success 5.13:1, error 5.62:1, warning 5.02:1, info 7.10:1, close 7.62:1).

### Changed
- Snackbar durations are now length-aware and accessibility-aware: success/info 2.5 s, error/warning 4 s, +500 ms per five words over eight, capped at 6 s; `getRecommendedTimeoutMillis()` can extend them. Host visuals are `Indefinite` so Material3's own 4 s `Short` timeout cannot truncate that window. Error/Warning toasts are dismissible.
- Snackbar host insets use `navigationBars.union(ime)` and lift 80 dp on `main/` routes to clear the tab bar; the banner stays below the status bar.
- The banner maps six push accents to four notice kinds and gains a title, live-region semantics, TalkBack-aware auto-dismiss, pause on press/focus, and a reachable exit animation.

### Fixed
- TalkBack silence on in-app push banners (no live region) and under-keyboard snackbars (IME inset).
- The length-aware toast window was capped at 4 s in practice by Material3's `SnackbarDuration.Short`; non-action visuals now report `Indefinite` and the pill's `LaunchedEffect` is the only timer.
- Action-label contrast on the old dark toast (error 3.13:1, success 4.28:1) is replaced by the white pill's verified notice tokens.
- Toast taps passing through to unseen bottom-navigation/sticky-button controls.

### Verified
- `:app:compileDebugKotlin` (offline) exit 0; the cross-check rebuild also passed `:app:assembleDebug`.
- Contrast computed with WCAG 2.1 relative luminance; monochrome logo alpha verified.
- Cross-check measurements at 280 dpi: Info pill 379.4×64.0 dp, Error pill 379.4×72.0 dp, 16.0 dp margins, 40.0 dp medallion, 22.9×20.6 dp glyph; surface #FFFFFF, medallion #6835A8, text #201A17.

### Verified on device
- Debug APK built and installed on `ZA222J5KT7` (versionCode 38, `lastUpdateTime` 18:54:00, SHA-256 `2433b013…6c757eb9`). The Home back-exit Info pill was gone by 3.6 s; the offline-printer Error pill renders as the white KhanBook pill above the 80 dp tab bar and the close X cleared it within 0.4 s.
- Screenshots: `artifacts/android-ui-tests/notice-pill-home-toast-v2-2026-11-19.png`, `notice-pill-error-v2-2026-11-19.png`, `crosscheck-info-t0-2026-11-19.png`, `crosscheck-info-t36-2026-11-19.png`, `crosscheck-error-close-before-2026-11-19.png`, `crosscheck-error-close-after-2026-11-19.png`.

### Not verified
- The under-keyboard toast could not be triggered (the Wi-Fi printer Save is disabled for an invalid host); the in-app push banner still needs a real FCM message. TalkBack announcement, 200 % font scale, a full landscape/tablet pass, RTL and animation timing are untested; the 6 s cap itself needs a 43+ word toast to reach. Sticky billing bottom bars still overlap the toast (taps are blocked).

## [2026-11-19] - Tutorial status bar overlap fix (SES-20261119-035)
### Fixed
- The tutorial catalogue and in-app video player now apply `statusBarsPadding()`. These routes sit outside `MainScreen`, so unlike the other scaffold screens nothing else was supplying the top inset; previously the back arrow and title began at the status-bar edge (measured `[112,44][608,96]` on `ZA222J5KT7`).

### Verified
- `:app:assembleDebug` → BUILD SUCCESSFUL. APK SHA-256 `a90d4b2587ee037bc3ec5eb7ed7c2f75c00b2553706b31570cd7d4ef8d98f73b`.
- Source audit: `MainActivity` already calls `enableEdgeToEdge()` and the manifest already sets `adjustResize`; only the two tutorial scaffolds needed the inset.

### Not verified
- The patched APK was installed on `ZA222J5KT7` at 17:20:03, but the device dropped off ADB before the corrected header was measured. A valid post-fix screenshot is still pending.

## [2026-11-19] - In-app tutorial catalogue, EOD Reports label, stock traces
### Added
- `Watch Tutorials` (About App and Quick Start) now opens `Routes.TUTORIALS`, an in-app catalogue (`TutorialListScreen.kt`). Titles come from `https://kbook.iadv.cloud/cdn/tutorials/videos.json`; until that manifest is uploaded, a bundled fallback lists the three published videos. Tapping a title opens `Routes.tutorialVideo(fileName)` → `InAppWebViewScreen.kt`, which loads the CDN video URL directly in an Android `WebView`. No external browser is used; Privacy Policy and Terms of Use remain external-browser links.

### Changed (UI only)
- Notification Preferences now labels the settlement channel **EOD Reports**; the registered Android channel name/ID is untouched, so existing notification settings are preserved.
- The `Low Stock Alerts` row is hidden from Notification Preferences because the inventory feature was removed from Android. The OS channel stays registered since the server can still push `inventory_low`.
- Corrected the misleading "Out of stock" comment on the menu card scrim: it is driven by `item.isAvailable` (a manually hidden dish), not stock. Owner confirmed the scrim should be **kept** as the only cue for disabled dishes.

### Fixed
- WebView native state is released through `AndroidView.onRelease` instead of a `DisposableEffect` keyed on the mutable view reference, which destroyed the live view as soon as it was created. The earlier HTML5 `<video>` data-URL wrapper failed with `net::ERR_FAILED`; the player now loads the video URL directly.

### Verified
- `:app:compileDebugKotlin` and `:app:assembleDebug` → BUILD SUCCESSFUL. Debug APK installed on `ZA222J5KT7` (`lastUpdateTime=2026-11-19 16:50:59`, `1.0.38`).
- Device-verified: all three tutorial titles open and play in-app; Back returns to the list; no external browser; EOD Reports row visible and no Low Stock Alerts row. Screenshots in `artifacts/android-ui-tests/`.
- **Pending:** upload `ops/cdn/tutorials/videos.json` to `/var/www/cdn.kbook.iadv.cloud/tutorials/videos.json`; the app currently logs `Manifest HTTP 404` and uses the fallback list.

## [2026-11-19] - App Settings navigation + notification fixes (SES-20261119-032)
### Fixed
- Restaurant Configuration: the header arrow now shows the same "Unsaved Changes" dialog as system Back (BUG-019).
- Settings Back returns to the section actually entered from, and Home's sync/printer cards no longer push a second Main entry (BUG-020).
- Back from Notifications Preferences restores the Settings tab and App Settings section (BUG-021).
- Low Stock Alerts have their own channel again, with one `channelForType()` mapping shared by the tray and the in-app banner (BUG-026).
- In-app banners respect muted channels; payment, QR-order and cancelled-order alerts stay locked on (BUG-024).
- Notification Preferences shows a "Notifications are off" card with "Enable in Settings" when Android blocks the app (BUG-025).
- Inbox rows open the related order/payment/compliance screen instead of only marking read (BUG-027).

### Verified
- `NotificationChannelMappingTest` (5 tests) and `:app:assembleDebug` exit 0; APK `afdc2599…`.
- **Device-confirmed:** installed at 16:05:01; Back from Notifications Preferences returns to App Settings, and the dirty-shop header arrow opens the "Unsaved Changes" dialog. The OS-blocked card remains unverified.

## [2026-11-19] - In-app Show Taps touch indicator (SES-20261119-030)
### Added
- Permission-free touch visualiser: a draw-only translucent dot follows each finger, supports multi-touch by `pointerId`, and fades on release. No Developer Options, no `SYSTEM_ALERT_WINDOW`. Controlled by Settings → App Settings → Interaction Feedback → **"Touch feedback"** (ON shows dots, OFF hides them), persisted in `session_prefs` via `TouchIndicatorConfig.setEnabled(context, boolean)` and defaulting to **OFF in every build**. Interaction Feedback keeps two toggles ("Menu item sound", "Touch feedback"); "Voice Soundbox (Audio Alert)" in Payment Configuration also defaults OFF.
- One Activity-level hook in `MainActivity`; `TouchAwareDialog` plus the two printer bottom sheets extend it to the shared separate-window surfaces.

### Verified
- `:app:compileDebugKotlin` and `:app:assembleDebug` exit 0. APK `49640f2d…c8ad9135` installed on `ZA222J5KT7` (`lastUpdateTime` 14:48:59), launched, no FATAL in logcat.
- **Device-confirmed:** during a scripted 12 s drag along x=360 from y=700→1200, a ~100 px circular highlight was centered at (360, 825) at t=3 s — exactly the interpolated finger position. Screenshot: `artifacts/android-ui-tests/touch-indicator-drag3-2026-11-19.png`.
- **Toggle was device-confirmed then removed per owner decision:** a temporary "Show taps" switch was verified (OFF→no dot, ON→dot, persisted across relaunch) but was removed so Interaction Feedback has only two toggles. APK `5fe18ce2…096c64e7` installed 15:18:32 on `ZA222J5KT7`; `uiautomator` confirms only "Menu item sound" + "Touch feedback" on the screen. Screenshot `artifacts/android-ui-tests/interaction-feedback-two-toggles-2026-11-19.png`.
- **Owner follow-up — dots still visible after switch removal:** changed the flag default from `BuildConfig.DEBUG` to `false` in all builds and wrote `show_touch_indicator=false` on the device. APK `83cce9a4…b470b94` installed 15:29:22. A/B held-touch capture proves the fix: pref `true` → 11,661 gold dot pixels at (360,900); pref `false` → 0 (`ab-on-touch.png` vs `ab-off-touch.png`). Device left OFF.
- **Final owner decision — "Touch feedback" is the dot switch:** the owner tested and confirmed the second toggle must show dots when ON and hide them when OFF, with both it and the audio alerts OFF by default. Repurposed the toggle from haptic-only to `TouchIndicatorConfig`, changed "Menu item sound" and "Voice Soundbox (Audio Alert)" defaults to false, and cleared the leftover device `haptic_enabled` value. APK `aebb0c82…d5f38d4d5` installed 15:40:01; UI default OFF, ON produced 13,609 gold dot pixels, OFF produced none; owner confirmed "all good".

### Not verified
- Two-finger simultaneity, fade timing, and dots inside dialogs/bottom sheets were not separately exercised.

## [2026-11-19] - Notification Preferences header fits on one line (SES-20261119-028)
### Fixed
- The centred 24 sp header wrapped "Notifications Preferences" onto two lines on a phone. The screen now renders `Notification Preferences` start-aligned at 20 sp, with `maxLines = 1` + ellipsis via new optional `titleMaxLines` / `titleOverflow` parameters on `KhanaBookScreenScaffold` (defaults keep every other screen unchanged).

### Verified
- `:app:assembleDebug` exit 0; APK `2182ee58…f34ad62` installed on `ZA222J5KT7` (`lastUpdateTime` 14:13:26), launched, no FATAL in logcat.

## [2026-11-19] - App Settings navigation audit (SES-20261119-029)
### Found
- **BUG-019:** header back on Restaurant Configuration discards unsaved edits without the confirmation dialog.
- **BUG-020:** Back from a deep-linked subsection always lands on the App Settings list (source-level).
- **BUG-021 (device-confirmed):** Back from Notifications Preferences opens the Home tab instead of the App Settings list.

## [2026-11-19] - Watch Tutorials button on About App (SES-20261119-027)
### Added
- About App now lists **Watch Tutorials** below Privacy Policy and Terms of Use, with a play icon, opening `https://kbook.iadv.cloud/cdn/tutorials`.

### Verified
- `:app:assembleDebug` exit 0; APK `484b833a…e8226c70a` installed on `ZA222J5KT7` (`lastUpdateTime` 14:07:03), launched, no FATAL in logcat.

### Not verified / open
- The tutorial URL returns **404** (both `/cdn/tutorials` and `/cdn/tutorials/index.html`), so the button currently opens a dead page. Needs an index page or a direct video URL.
- The About App screen was not opened on the device.

## [2026-11-19] - Removed dead gap below Save Configuration (SES-20261119-026)
### Fixed
- Restaurant Configuration had a 48 dp spacer after the `Save Configuration` button, stacked on the card's 24 dp padding and the screen content padding (~70 dp of dead space). The trailing spacer is now 16 dp.

### Verified
- `:app:assembleDebug` exit 0; APK `fc918159…0478f80e62` installed on `ZA222J5KT7` (`lastUpdateTime` 14:02:10), launched, no FATAL in logcat.

### Not verified
- The spacing was not measured on screen; see Session 026.

## [2026-11-19] - Shop Profile logo header + larger placeholder (SES-20261119-025)
### Changed
- Restaurant Configuration → Card 1 header now reads only **Shop Profile**; the "Shop Logo" title and "Shown on bills and receipts" subtitle are gone.
- Logo placeholder enlarged from 56 dp to 112 dp; the empty-state storefront icon grew to 48 dp to match.

### Verified
- `:app:assembleDebug` exit 0; APK `a73ba789…a32e13b` installed on `ZA222J5KT7` (`lastUpdateTime` 13:58:33), launched to `MainActivity`, no FATAL in logcat.
- The Session 024 build (`b9c7ce46…c5f1a87b`) was also installed first, per the owner's "update the APK after every change" rule.

### Not verified
- The Restaurant Configuration screen was not navigated; the new label and preview size were not seen on hardware. Unused `ShopLogoSection` still has the old copy.

## [2026-11-19] - Business Hours toggle + Easebuzz UI hidden (SES-20261119-024)
### Changed
- Restaurant Configuration → Business Hours: the two option cards are replaced by a segmented `Default Time | Custom Time` toggle. The selected mode's panel is the only one composed; Default still writes `00:00–23:59`, Custom restores the remembered times, and read-only users cannot switch.
- Payment settings show a static **Online Payments / coming soon** card instead of the Easebuzz toggle, readiness message and onboarding hub (September Play Store launch gate re-applied exactly as reverted by `1b0d39be`).
- Billing payment-mode list no longer includes `PAYMENT_LINK`, so no Easebuzz entry point appears during billing. The profile flag, save flow and server contracts are unchanged.

### Fixed
- Removed the unused `BusinessHoursOptionCard` and its stale “retained until the caller is migrated” KDoc; the Screen 020 half-applied business-hours refactor is resolved in favour of the toggle.

### Verified
- `:app:compileDebugKotlin` exit 0; `PaymentModeManagerTest` exit 0 (assertions updated to the hidden behaviour); no `BusinessHoursOptionCard` references remain.

### Not verified
- Nothing exercised on hardware; see Session 024 in `DEVICE_SESSIONS/SESSION_LOG.md`.

## [2026-11-19] - Printer-fix debug APK installed (SES-20261119-023)
### Verified
- `:app:assembleDebug` exit 0 (35s); APK 41,138,803 bytes, SHA-256 `c1540b33…e4a79819` installed on `ZA222J5KT7`.
- Device: install Success, app launched to `MainActivity`, no crash in logcat; package `lastUpdateTime` 2026-11-19 13:46:29. Screenshot `artifacts/android-ui-tests/printer-config-fixes-2026-11-19.png`.
- `versionCode`/`versionName` unchanged at 38 / 1.0.38.

### Not verified
- No screen was navigated; the removed Print Options card, kitchen persistence, hostname Wi-Fi save and Test Printer were not exercised on hardware.

## [2026-11-19] - Printer Configuration fixes + Print Options card removed (SES-20261119-022)
### Changed
- Removed the "Print Options" card from Settings → Printer: the `Mask Customer Phone` toggle and the Back/Save action row are gone. Printer options now persist as they are changed, including kitchen Enable and Paper Size.
- The Wi-Fi printer field accepts a hostname as well as an IPv4 address; helper text distinguishes an incomplete address from an invalid hostname.
- A configured-but-offline printer reads "Not connected", and "Network Mismatch" wins over it, matching the red dot / warning badge.

### Fixed
- Kitchen printer changes were only local until Save and were discarded on Back; Test Printer also used the stored paper size.
- The Wi-Fi dialog closed before its save finished: the saving spinner was unreachable and a failed save lost the typed address.
- The selected Bluetooth device's border was a discarded `Modifier` and was never drawn.
- Removed `SettingsViewModel.savePrinterSettingsLocally`, whose only caller was the deleted Save action.

### Verified
- `:app:compileDebugKotlin` exit 0.

### Not verified
- Nothing was exercised on hardware; see Session 022 in `DEVICE_SESSIONS/SESSION_LOG.md`.

## [2026-11-19] - Debug APK rebuilt and installed (SES-20261119-020)
### Fixed
- Build blocker: uncommitted `ShopConfigComponents.kt` had removed `BusinessHoursOptionCard` while `ShopConfigSection.kt:815,830` still called it. Re-added the composable verbatim from HEAD (owner chose "Restore old card"); no UI behaviour change.

### Verified
- `:app:assembleDebug` exit 0; APK SHA-256 `018E7833…D057C6` installed on `ZA222J5KT7`.
- Device: install succeeded, app launched to `MainActivity`, no crash in logcat; UI dump shows Restaurant Configuration with the **old** `Default Time` / `Custom Time` business-hours cards (not the segmented toggle). Screenshot `artifacts/android-ui-tests/debug-apk-update-2026-11-19.png`.
- `versionCode`/`versionName` unchanged at 38 / 1.0.38 (version not bumped in the working tree).

### Not verified
- Screenshot not visually inspected (model limitation); UI state confirmed via `uiautomator` dump. No business-hours/save/logo-upload flow exercised. Nord device not updated. Source fix not committed.

## [2026-11-18] - Menu dish-photo UX fixes
### Added
- Menu item rows now expose **Add Photo / Change Photo** in the More menu, and show a spinner over the thumbnail while that item's photo is uploading or being removed.
- Edit Item photo preview now has versioned cache keys, a loading indicator and an error placeholder.

### Fixed
- `isPhotoUploading` had no UI consumer; replaced by `uploadingPhotoItemId` so the status appears on the correct row.
- The per-row quick-photo callback was dead wiring; the new-item "tap Add Photo to retry" recovery path now exists.
- Edit Item previously showed only "PNG, JPG, or WebP"; it now states the 10 MB cap too.

### Verified
- `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:assembleDebug` all exit 0 (online — Roborazzi deps from concurrent work needed resolving); APK SHA-256 `a46f6e33…119e69c` installed on `ZA222J5KT7`.
- Device: Add Photo / Change Photo labels and updated Edit Item copy verified; no crash. Spinner and error-placeholder states were not triggered.

## [2026-11-18] - Shop Logo upload experience redesign
### Added
- Restaurant Configuration → Shop Logo: info tooltip, contextual single primary action (`Upload Logo` / `Change Logo`), responsive square preview with aspect-preserving scaling, compact upload requirements with progressive disclosure, inline uploading / success / error panels with Retry, pending-image removal, and filename + size after a confirmed upload.
- `LogoFileInfo` utility for picked-file display metadata.

### Fixed
- Logo-upload validation messages were being replaced by a generic error; they now reach the owner verbatim.
- The uploading state was never published by `saveProfileWithLogo`; it now is, with navigation blocked mid-upload.
- A successful shop save no longer navigates away before the logo status can be read; a picked logo is retained until the backend confirms, so Retry works.

### Verified
- `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:assembleDebug` exit 0; APK SHA-256 `87a337a4…47d6fd` installed on `ZA222J5KT7`.
- Device: existing-logo state, single primary action, info tooltip and File details expansion verified; no crash. Real upload/success/failure states and invoice rendering were not exercised on device. Screenshots `artifacts/android-ui-tests/shop-logo-*-2026-11-18.png`.

## [2026-11-18] - Business Hours: segmented mode switch
### Changed
- Restaurant Configuration → Business Hours: the always-visible `Default Time` / `Custom Time` option cards were replaced by a single `Default Time | Custom Time` segmented toggle. Only the selected mode's content is composed: Default shows a read-only `12:00 AM – 11:59 PM / Full day (Midnight reset)` summary, Custom shows the `Opening Time` / `Closing Time` pickers plus the duration and overnight hints. Mode-switch and save semantics are unchanged.

### Verified
- `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:assembleDebug` exit 0; APK SHA-256 `88ebd62f…09e436d` installed on `ZA222J5KT7` (and on the Nord after the device flap).
- Device check: Custom active → pickers + hints only; Default active → summary only, subtitle follows `1:30 AM` → `12:00 AM`; saved profile intact after Discard; no crash entry for the package. Screenshots `artifacts/android-ui-tests/rest-config-segmented-*-2026-11-18.png`.

## [2026-11-14] - Shop Logo header, Order Details redesign, dialog refresh fix
### Added
- Restaurant Configuration: `Shop Logo` / `Shown on bills and receipts` header above the logo and Change Logo row.
- Payment mode picker entries now show the mode icon; the shared selection dialog (`KhanaBookSelectionDialog`) gained an optional `leadingIcon` and a selected check, so Cancel Order reasons get the same treatment.

### Changed
- Order Details dialog: order number, invoice, type and date are grouped in one soft card; Print and Share are side by side; split-amount dialog (`PartAmountDialog`) has an "Order total" strip, a sum hint and a filled gold Confirm button.

### Fixed
- **Order Details showed stale data after edits.** The dialog renders a snapshot, so changing payment mode left the old mode on screen and a cancelled order could still show live actions until the dialog was reopened. `ReportsViewModel.refreshOpenBill` now re-reads the bill after `updatePaymentMode` / `cancelOrder`.

### Verified
- `:app:compileDebugKotlin`, `:app:assembleDebug`, `:app:testDebugUnitTest` exit 0; APK SHA-256 `f6c0a4c6…79ed5e36b` installed on `ZA222J5KT7`.
- Live: A01 Cash + UPI → Cash updated the dialog immediately; split dialog confirmed 183.75 / 183.75 and reverted the bill to Cash + UPI; Cancel Order opened with the selected-reason check and was dismissed via Keep Order, leaving data untouched. Screenshots under `artifacts/android-ui-tests/` (`shop-logo-header-*`, `order-details-redesign-*`, `payment-mode-picker-*`, `part-amount-dialog-new-*`, `payment-mode-refresh*`, `cancel-order-dialog-*`).

## [2026-11-14] - Orders table no longer stretches; Restaurant Configuration header removed
### Changed
- **Orders table (`ReportViews.kt`)**: the panel used `fillMaxSize()`, so two orders left a full-height empty card. It now measures inside `BoxWithConstraints` with `heightIn(max = maxHeight)` and a `weight(1f, fill = false)` list — it hugs the rows when short and still fills and scrolls when long. Empty results keep a 180 dp empty state. Header label `OrderNo` → `Order No`. Chips, columns and row actions untouched.
- **Restaurant Configuration (`ShopConfigSection.kt`)**: removed the in-card header (storefront icon, "Restaurant Details", "Configure your restaurant information"). The card starts with the logo / Change Logo row; fields and lower cards are unchanged.

### Verified
- `:app:compileDebugKotlin`, `:app:assembleDebug` and `:app:testDebugUnitTest` exit 0; APK SHA-256 `0ab8c374…039bc4b9`, installed on `ZA222J5KT7`.
- Device check: Orders panel ends right under `A01` with `A02` still shown as cancelled; Restaurant Configuration shows the logo row at the top of the card. Screenshots `artifacts/android-ui-tests/orders-table-fixed-2026-11-14.png` and `restaurant-config-no-header-2026-11-14.png`.

## [2026-11-14] - Launcher icon modernization
### Changed
- Adaptive launcher icon moved out of `res/mipmap-anydpi-v26/` into `res/mipmap/` (the `v26` folder is deleted; minSdk 26 makes the qualifier redundant).
- Added the Android 13 `<monochrome>` layer to both `ic_launcher.xml` and `ic_launcher_round.xml`, backed by new `ic_launcher_monochrome.png` assets at mdpi–xxxhdpi. The mask is generated from the existing foreground: near-white pixels become transparent, everything else solid black, so the logo silhouette drives themed icons.
- Removed the legacy square `ic_launcher.png` / `ic_launcher_round.png` from all density folders (adaptive icon always wins at minSdk 26).
- Deduplicated the brand logo: `HelpSupportView` uses `R.drawable.khanabook_logo`, `about_app_logo.png` deleted, and `khanabook_logo.png` moved to `drawable-nodpi/` to satisfy the densityless-bitmap rule.

### Verified
- `:app:assembleDebug`, `:app:testDebugUnitTest` and `:app:lintDebug` all exit 0; lint icon warnings (`MonochromeLauncherIcon`, `IconLauncherShape`, `IconLocation`, `IconDuplicates`, folder `ObsoleteSdkInt`) are all 0 and total warnings fell 469 → 458.
- APK SHA-256 `7c8ac92e…6238aac21c5` installed on `ZA222J5KT7`: launcher shows the adaptive icon in the drawer and the app opens to Home. Screenshots `artifacts/android-ui-tests/launcher-icon-final-2026-11-14.png` and `launcher-icon-home-final-2026-11-14.png`.
- **Limitation:** themed-icon rendering itself was not screenshotted; this launcher has no themed-icon toggle. The monochrome layer is verified by APK contents, lint and the generated silhouette only.

## [2026-11-14] - Lint to zero errors, dead resources removed
### Fixed
- **Wi-Fi printer subnet prefill on Android 8.0–9:** `RouteInfo.hasGateway()` (API 29) replaced with `it.gateway != null`, so gateway detection no longer throws and silently returns nothing on API 26–28.
- **Locale-dependent formatting:** `String.format` now passes `Locale.US` for currency amounts and OTP countdowns (`CurrencyUtils`, `SignUpScreen`, `QuickStartScreen`, `ShopConfigSection`).
- **Dead SDK branches:** removed API-26 guards and redundant `@RequiresApi(O)` annotations from `NotificationHelper` / `NotificationChannels`, and the always-true `SDK_INT >= O` branches in `PrintService`.
- **Misindented block:** comment + `val isInitialLoading` in `MenuSelectionStep` re-indented into their enclosing scope (formatting only).

### Removed
- `res/values/styles.xml` (dead Views-era styles that carried the API 28 `lineHeight` and API 31 `clipToOutline` errors) and `res/values/fonts.xml` (invalid, inert `<font>` entries).
- Unused resources: `raw/anim_payment_success.json`, `drawable/ic_google_logo.xml`, the unused drawable `ic_launcher_foreground.xml`, `splash_footer.png`, `splash_full_brand.png`, `splash_logo_with_card.xml`, `splash_logo_with_glow.xml`, plus 18 colors, 18 dimens and 45 strings with no references.

### Verified
- `:app:lintDebug` went from **12 errors / 605 warnings** to **0 errors / 469 warnings** and now passes; `:app:compileDebugKotlin`, `:app:assembleDebug` and `:app:testDebugUnitTest` all exit 0.
- APK SHA-256 `9ea11e38…77a3f48ad` installed on `ZA222J5KT7`: cold-start splash and Home verified. Screenshots `artifacts/android-ui-tests/lint-cleanup-*-2026-11-14.png`.

## [2026-11-14] - Dead-code removal across Android screens
### Removed
- Report export in its entirety: `ReportsViewModel.exportReport` / `buildShareText`, `ReportDownloadBottomBar`, and `feature/reports/domain/ReportExporter.kt` (PDF/CSV generation). None of it had a UI caller, so the app no longer offers report download or export share text.
- Orders row-action plumbing: `onStatusChange` / `onPayModeChange` / `onRequestCancel` and the now-unused `enabledModes` parameter on `OrderTableRow` and `OrderHistoryTableComponent`, the callbacks in `OrdersScreen`, and `ReportsViewModel.updateOrderStatus`. Order editing remains available in `OrderDetailsDialog`.
- Four `ReportViews` compatibility aliases (`OrderDateFilterTabs`, `OrderRowItem`, `OrderHistoryTable`, `OrderLevelView`), `DetailStatusRow` + local `formatDate` in `OrderDetailsDialog`.
- `feature/billing/ui/ActiveOrderComponents.kt` (`BillInfoHeader`, `ActiveOrderItemCard`, `CompactMenuSection`), `feature/billing/ui/PaymentSection.kt` (`BillSummaryDisplay`, `SummaryRow`, `PaymentModeSelector`, `loadShopLogoBlockingSection`), `OrderTableComponents.TableHeader`, `MenuSelectionStep.FoodTypeIcon`, `OnlinePaymentsSetupScreen.NotStartedContent`, `ForgotPasswordDialog.loginTextFieldColors`.
- `AppInfoSection` and `CustomerNumberCollectorSelector` from settings, `KhanaInvoiceShareSheet.kt`, `SignaturePad.kt`, `Utils.directPrint`, `PaymentColors.payModeColor` / `getPayModeIconTint`.

### Kept deliberately
- The three `AppLockScreen` biometric callbacks: they are `BiometricPrompt.AuthenticationCallback` overrides called by the framework, not dead code (grep false positive).
- `OrderDateFilterComponent` and its `Today` / `Yesterday` / `Custom` chips (frozen by product decision), `getPayModeColor` / `getPayModeIcon`, and the `reports.full` role gate.

### Verified
- `:app:compileDebugKotlin --offline`, `:app:testDebugUnitTest --offline` and `:app:assembleDebug --offline` all exit 0. APK SHA-256 `9423d557…47777c894e`, installed on `ZA222J5KT7` at 2026-11-14 17:18:40.
- Live check: Home, Orders (chips `14 Nov`, table with cancelled `A02` struck through and `A01`), Order Details dialog (Print / Share / Change Payment Mode / Cancel) and Settings (config rows, App Settings, Sign Out) all render normally. Screenshots in `artifacts/android-ui-tests/deadcode-*-2026-11-14.png`.

## [2026-11-14] - Orders Screen: One Table, Selected Chip Shows Its Date
### Changed
- `OrdersScreen.kt` no longer has a `reportType == "Payment"` branch: the screen renders the first-load skeleton and then `OrderHistoryTableComponent`, nothing else. The branch was unreachable (`reportType` is always `"Order"`) but it was the only path that could replace the table.
- The selected date chip now shows the day it resolves to — `Today` → `14 Nov`, `Yesterday` → `13 Nov` — while unselected chips keep their names. `Custom` keeps its existing behaviour (`Custom` until a range is picked, then `12 Nov – 14 Nov`).
- `ReportsViewModel.dateFilterLabels` derives those labels from `BusinessDate.businessDate(now, profile)`, the same business date that selects the query window, and refreshes on filter change, on resume and when the profile re-emits.

### Removed (same day, 16:10)
- `PaymentLevelView` and its orphaned `PaymentModeItem` / `PartPaymentCard` / `ReportTypeToggle` helpers in `ReportViews.kt`.
- `ReportsViewModel._reportType` / `reportType` / `setReportType`, the `"Payment"` branches in `exportReport` and `buildShareText`, and the `reportType` parameter of `ReportExporter.exportToPdf` / `exportToCsv`. Both exports now always title as `Orders Report`; `paymentBreakdown` is kept because it feeds the payment summary inside them.

### Verified
- `:app:compileDebugKotlin --offline` and `:app:assembleDebug --offline` exit 0.
- Installed on `ZA222J5KT7` (`lastUpdateTime` 2026-11-14 16:10:35, APK SHA-256 `3a7c98f5…d313abc6d`) and checked by uiautomator: `Today` chip `14 Nov`, `Yesterday` chip `13 Nov`, `Custom` chip unchanged and its picker opens, table-only content.

### Fixed (16:2x)
- **Orders table columns were misaligned.** The row's order-number cell had no weight while the header cell did, so after the details line was removed every cell after it shifted left and stopped sitting under its centred header. The cell now uses `weight(COL_ORDER)` and the same start inset as the header; verified on `ZA222J5KT7` with matching header/row bounds (`[49,336][225,362]` vs `[49,411][225,436]`, `[225,336][392,362]` vs `[225,411][392,436]`).
- **Order number shows no `#`** — the table prints `A02` at the product owner's request (16:3x); the details dialog, search and share text still prefix it. A `#` was briefly added and removed the same session.
- The `Today` / `Yesterday` / `Custom` chips are frozen at the product owner's request — not touched by this change.

### Rebuilt (16:4x)
- Orders table rebuilt in place: a single `OrderTableCell` primitive now builds every column for both the header and the rows, so alignment comes from the shared column weights instead of per-cell padding. Rows use the existing responsive `tableCellFontSize` token (previously unused); the header keeps `tableHeaderFontSize`.
- Row density: 52 dp minimum on phones, 60 dp on the wide layout, with no vertical padding (the 48 dp action target sets the content height).
- The row action is a 36 dp disc in a 48 dp hit area and its description now reads `View order A02` — it opens the details dialog, it is not a menu. No menu was added.
- `LazyColumn` rows are keyed by `billId`, so refreshes update rows in place. Columns, data, empty state, hairlines and cancelled-row styling are otherwise unchanged; the frozen chips were not touched.
- Verified on `ZA222J5KT7` (APK SHA-256 `f153eedb…3d8b1ac903`): header and row cell centres agree (`Amount` 310 vs 309.5, `Time` 473 vs 472.5), order cells share left edge 49, 53 dp row pitch, 48×48 dp action hit area.

### Not Verified / Not Changed
- A Custom range was not re-picked after `Apply` on device (path unchanged since 12:20).
- Tablet/wide layout (60 dp rows) and a cancelled row were not exercised on device.
- The `canViewFullReports` gate and the row-level callbacks are unchanged (item K).

## [2026-11-13] - Web Admin Spec Suite Repaired (39 Failures → 0)
### Fixed
- `apps/web-admin` Karma suite reported **309 SUCCESS / 39 FAILED**; it now reports **350 SUCCESS / 0 FAILED** (`npx ng test --watch=false`, exit 0).
- 38 failures were stale `AdminApiService` spy mocks: the specs never added `getProfile` (business dashboard) or `getDashboardSummary` (businesses), which the pages now call on load. The mocks now return `of({})` for both.
- `feature-flags-page.component.spec.ts` asserted a hardcoded 28-flag catalogue that the page no longer contains — it renders `AdminApiService.getFeatureFlags()`. Rewritten against the API-driven behaviour rather than deleted.
- `form-validation.spec.ts` expected a `/prototype sample data shown/` toast from the Businesses header action; the page now starts the real onboarding workflow and shows `'Restaurant onboarding workflow initiated.'`. The assertion was updated to the current message and the test renamed to match.
- Dashboard legibility: `.dash-eyebrow`, `.printer-station__label`, `.printer-station__sub` and `.printer-tactile-btn` moved from `--kb-text-2xs` (11px) to `--kb-text-sm` (13px). The simulated 80mm receipt body (`.receipt-inner`) is exempted from the floor check because it draws thermal print output rather than screen copy.

### Verified
- `npm run build:dev` exit 0; all ten `check:*` design scripts exit 0; Karma 350/350.

### Not Changed
- No production behaviour was altered to make tests pass; only spec fixtures, one spec rewritten to the shipped behaviour, and four dashboard font sizes raised to the existing 13px floor.

## [2026-11-13] - Email OTP No Longer Fails On Unconfigured SMTP (BUG-004)
### Fixed
- `EmailNotificationService.sendVerificationOtp` treated any configured-looking mail host as deliverable. Because `application.properties` defaults `khanabook.email.enabled=true` and a real `spring.mail.username` while leaving `spring.mail.password` empty, every Email `Get OTP` tap on such a server attempted an authenticated SMTP send, threw, and rolled back the OTP row inside `PasswordResetOtpService.issueEmailOtp` — surfacing "Failed to send verification email. Please try again." to the POS for a perfectly valid address.
- The method now also checks `spring.mail.password`; when it is blank it logs the generated code with a warning that names `MAIL_PASSWORD` and returns without contacting the mail server, matching the documented development fallback. A send failure on a genuinely configured host still throws `IllegalStateException`.
- Added `EmailNotificationServiceTest` covering all four branches (unconfigured, disabled, delivered, transport failure): 4/4 pass.

### Not Changed
- No Android code was modified; the POS already displays the server's message and needs no client-side change.

### Remaining Deployment Action
- Verified by unit test only. Actual delivery still requires `MAIL_PASSWORD` on the target server; until it is set, codes appear in the server log and never reach the inbox. End-to-end re-verification on device is pending.

## [2026-11-12] - Billing Mode Card Matched To The Other Cards
### Fixed
- `ShopConfigComponents.kt` (`RestaurantPaymentFlowSelector`): the Billing Mode panel painted its own `DarkBrown2.copy(alpha = 0.45f)` surface with a 45%-alpha gold border, so it appeared as a darker maroon block next to the `CardBG` (`0xCC78461E`) Restaurant Details and Business Hours cards.
- It now uses the same surface language: `CardBG` fill, `BorderGold` at 25% alpha, `KhanaRadii.lg` corners, `spacing.large` padding.
- Header aligned too: 28 dp gold icon, `titleMedium` bold title in `TextLight`, and a `bodySmall` `TextGold` subtitle ("Choose when customers settle the bill").
- Business Hours subtitle: the rollover time now uses a non-breaking space internally (`12:30\u00A0AM`) so it no longer wraps between the minutes and the AM/PM suffix.

### Verified on device (`ZA222J5KT7`, 2026-11-12 18:55 IST)
- `artifacts/android-ui-tests/cards_aligned.png` shows Restaurant Details, Business Hours and Billing Mode sharing one card surface, with the subtitle wrapping as "Daily Order Number will reset at" / "12:30 AM".

## [2026-11-12] - Business Hours Card Collapses (Times Hidden Until Tapped)
### Changed
- `ShopConfigSection.kt`: the Business Hours card now opens **collapsed**. Only the clock icon, the `Business Hours` title, the rollover subtitle and a chevron are visible; the Opening Time / Closing Time fields are hidden by default.
- Tapping anywhere on the card header expands the two time fields (220 ms `expandVertically` + `fadeIn`); tapping again collapses them (180 ms `shrinkVertically` + `fadeOut`). Chevron flips between down/up with content descriptions "Show opening and closing time" / "Hide opening and closing time".
- `BusinessDate.closingBoundary`-derived rollover subtitle stays visible while collapsed, so the reset time is readable without expanding.

### Verified on device (`ZA222J5KT7`, 2026-11-12 18:43-18:47 IST)
- Collapsed: `artifacts/android-ui-tests/after_restart.png`
- Expanded: `artifacts/android-ui-tests/business_hours_expanded.png`
- Closing Time dialog: `artifacts/android-ui-tests/closing_time_picker.png`
- Live recalculation after picking `11:30 AM`: `artifacts/android-ui-tests/closing_time_changed.png` (subtitle became "Daily Order Number will reset at 12:00 AM")

## [2026-11-12] - Business Day Card Merged Into Business Hours
### Changed
- Removed the separate `Business Day` card from Restaurant Configuration (`ShopConfigSection.kt`); its rollover information now lives in the **Business Hours** card subtitle so the schedule and the daily order-number reset read as one setting.
- Business Hours subtitle changed from the generic "Set your restaurant operating hours" to `"Daily Order Number will reset at <time>"`, derived live from the Closing Time picker (+30-minute settlement buffer, or the server-activated cutoff when one is in force).
- Opening Time and Closing Time time pickers are unchanged.
- Replaced the `BusinessDayInfoCard` composable in `ShopConfigComponents.kt` with the pure helper `businessDayRolloverLabel(cutoffTime, cutoffEffectiveFrom, openingTime, closingTime)`.

## [2026-11-12] - In-Flight Loading State, Persistent Inline Errors & Inline Resend OTP
### Added
- Added `OtpVerificationResult.Loading` state in `AuthViewModel.kt` for reactive in-flight network observation.
- Added in-flight loading spinners on both WhatsApp and Email `[ Get OTP ]` buttons (`isOtpLoading` / `isEmailOtpLoading`) to eliminate duplicate taps.
- Added persistent inline field errors (`phoneServerError` / `emailServerError`) directly beneath the WhatsApp and Email inputs, maintaining red error borders until the input is edited.
- Added inline `[ Resend ]` button inside the 6-digit OTP prompt trailing icon area when the 120-second countdown reaches `00:00`.

## [2026-11-12] - Restaurant Configuration Modern Card Architecture Redesign
### Changed
- Redesigned Restaurant Configuration screen (`ShopConfigSection.kt` and `ShopConfigComponents.kt`) matching high-craft visual spec:
  - **Top Bar Header**: Aligned title `"Restaurant Configuration"` directly adjacent to the back button `(←)` using `titleAlignment = TextAlign.Start` in `KhanaBookScreenScaffold.kt` and `SettingsScreen.kt`.
  - **Card 1 (Restaurant Details)**:
    - Added bold title with gold Storefront hero icon and descriptive subtitle.
    - Added compact logo thumbnail with "Change Logo" button.
    - Integrated leading icons for all 6 inputs: `Storefront` (Shop Name), `LocationOn` (Shop Address), `Phone` (WhatsApp Number), `Email` (Email), `Link` (Review Link), `Description` (Invoice Footer).
    - Compact gold pill button `[ ➤ Send OTP ]` positioned inside the Email trailing icon space.
  - **Card 2 (Business Hours)**:
    - Gold clock icon, descriptive subtitle, and side-by-side time picker fields (`KhanaTimePickerField`) for Opening and Closing time.
  - **Card 3 (Business Day)**:
    - Refactored `BusinessDayInfoCard` with gold Info icon, clean rollover summary, and collapsible settlement buffer drawer toggled via chevron (`KeyboardArrowDown` / `KeyboardArrowUp`).
  - **Card 4 (Billing Mode)**:
    - Retained PIN-protected `RestaurantPaymentFlowSelector` styled cohesively with card surface.
  - **Bottom Action Button**:
    - Replaced dual back/save row with a single full-width rounded gold pill button for `Save Configuration` (`height = 52.dp`, `shape = KhanaRadii.xl`).
  - **Scrollbar**: Slender 3.dp vertical scrollbar thumb.
- Updated `SCR-08` in `04_SCREEN_INVENTORY.md`.

## [2026-11-12] - Email OTP Protection & Quick Start Wizard Onboarding Polish
### Added
- Backend Email OTP endpoints: `/api/v1/sync/config/users/update-email/request` and `/api/v1/sync/config/users/update-email`.
- Flyway migration `V121__widen_otp_requests_phone_number.sql` widening `otp_requests.phone_number` to `VARCHAR(255)` to support email OTP challenge storage.
- Verification email dispatch in `EmailNotificationService.java` with development logging fallback when SMTP is unconfigured.
- Unit tests `UserServiceEmailUpdateTest.java` (backend) and `EmailOtpViewModelTest.kt` (Android POS).

### Changed
- Added Email OTP verification guard to `ShopConfigSection.kt`:
  - Enforced Google account OAuth email immutability (`enabled = !isGoogleAuth`).
  - Added email format validation (`ValidationUtils.isValidEmail`).
  - Added inline `Send OTP` button in trailing icon when a valid new email is entered.
  - Added inline 6-digit OTP verification field with countdown timer and auto-submission on the 6th digit.
  - Guarded profile save action: rejects save if email is altered but unverified.
- Updated `SCR-08` in `04_SCREEN_INVENTORY.md`.
- Added WhatsApp number field with inline OTP verification (`Get OTP` / 6-digit confirmation) under Shop Name in `QuickStartScreen.kt`, auto-propagating to `RestaurantProfileEntity` and `UserEntity`.
- Refactored bottom action layout in `QuickStartScreen.kt` to a dual-button row: removed arrow symbol from `Start Billing` and added `Watch Tutorials` button side by side.
- Updated `QuickStartScreen.kt` to delay the appearance of the `[Skip]` action button by 5 seconds with a smooth fade-in (`AnimatedVisibility`), ensuring first-time restaurant owners review the initial shop and menu setup before skipping to the POS billing grid.
- Added and updated `SCR-09` (Quick Start Setup Wizard) in `04_SCREEN_INVENTORY.md`.

## [2026-11-11] - Session 001 Verification
### Added
- Created complete `ANDROID_POS_DOCUMENTATION/` knowledge base tree.
- Documented operational manuals for Dine-In, Payments, and Order History.
- Documented complete Dine-In sequence workflow from seating to cash checkout.
- Added verified test cases for functional billing and business logic.
- Recorded live audit log for orders `#A01`, `#A02`, `#A03`, `#A04` on device `ZA222J5KT7`.
- Added `Android POS UI Operations Documentation Policy` to `AGENTS.md`.

### Verified & Resolved
- 30-minute settlement buffer confirmed across Android POS and Server core logic.
- Dynamic UPI QR rendering and cashier cash tendered calculation confirmed on physical hardware.
- Fixed BUG-003: Aligned `ReportsViewModel.kt` date filtering (`Today`, `Yesterday`, `Custom`) to `BusinessDate.windowStart / windowEnd`, preventing midnight shift fragmentation.
- Added dining table identifier (`customerName`) to the Orders list row in `ReportViews.kt`.
