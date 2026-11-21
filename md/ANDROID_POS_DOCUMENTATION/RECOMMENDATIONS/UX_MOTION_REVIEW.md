# UX Motion Review — Android POS (static source audit)

**Review date:** 2026-11-20
**Session:** 067 (`SES-20261120-067`)
**Scope:** `apps/android/app/src/main/java` only. No server or web-admin code.
**Method:** Static source inspection plus the 20 principles in the `ux-motion` skill. No APK was built, installed, or exercised; no device was used and no new screenshots were captured.
**Status:** Findings recorded 2026-11-20. All ten findings (UM-01 … UM-10) implemented and compile-verified across Sessions 068 and 070; debug APK installed on `ZA222J5KT7` in Sessions 069 and 071. The §7 behavioural checks still need a device walk. See §6–§7 and `RECOMMENDATIONS/README.md`.

---

## 1. Evidence and limits

- Line numbers refer to the working tree at 2026-11-20 12:06 IST. The tree already contains uncommitted owner work; this review changed no source files.
- Every "observed" statement below means *observed in source*, not verified on hardware. Items needing a device check are labelled explicitly.
- Historical screenshots referenced here were captured in earlier sessions: `artifacts/android-ui-tests/orders-view-pill-2026-11-19.png` (48 dp View target), `order-dialog-bronze-payment-2026-11-19.png` (dialog origin), `notice-pill-home-toast-v2-2026-11-19.png` (toast), `touch-indicator-drag3-2026-11-19.png` (touch overlay). Device evidence archive: `DEVICE_SESSIONS/SESSION_LOG.md`.
- "Reduced motion" is labelled verification-needed: no app-level branch was found. Jetpack Compose may scale tween durations when Android's *Remove animations* setting is on, but that does not prove that springs, scale bounces, or infinite-repeat animations are swapped for fades. Confirm with the setting enabled before closing UM-01.

---

## 2. Findings

Priority reflects POS throughput: a cashier repeats tab switches, card taps, and payments many times per shift.

| ID | P | Principle | Location | Problem | Fix |
| --- | --- | --- | --- | --- | --- |
| **UM-01** | P1 | 20, 8 | Whole module; examples `HomeComponents.kt:48-62`, `ClosingAlertBanner.kt:59-67`, `MainScreen.kt:318-331` | No explicit reduced-motion path. Three infinite animations (shimmer 1200 ms, sync rotation 2000/1000 ms, closing-alert pulse 1000 ms, plus the launch glow at 2200 ms) and all spring/scale bounces have no fade-only alternative. | Add one shared motion policy that reads the OS animator scale / accessibility state and collapses transforms and infinite repetitions to a 100–150 ms fade. Verify with *Remove animations* on before calling it fixed. |
| **UM-02** | P2 | 1, 17, 20 | `ui/screens/MainScreen.kt:318-331` (`AnimatedTabIcon`) | Every tab selection jumps to `scale = 0.7f`, springs to `1.25f`, then to `1f`. Uniform scale does not preserve area (70%→125%), it restarts from a fixed value rather than the current one, and the tab is one of the most-used controls in the app. All three tabs use this path: `NavigationItems.kt:27-29` sets no `lottieRes`, so the early Lottie return at `MainScreen.kt:304-315` never fires. | Retarget from the current value with one `animateTo`, and use a small area-preserving press (e.g. scaleX 1.08 / scaleY 0.92 over 100–150 ms) or drop the bounce and keep only selection colour. |
| **UM-03** | P2 | 5, 18 | `core/navigation/AppNavGraph.kt:66-93` | Global NavHost exit and pop-exit still occupy 300 ms (slide) — the same as entry — and use `FastOutSlowInEasing` for leaving. Every route push/pop inherits this. | Exit/pop-exit 100–150 ms with an ease-in curve; keep enter 250–300 ms ease-out. Highest-leverage single change in this review. |
| **UM-04** | P2 | 1, 8, 16 | `core/designsystem/KhanaBookCard.kt:38-57` | Clickable cards set `indication = null` and rely on a 2% (`0.98f`) scale with the default spring. Feedback is very subtle, has no explicit 100–150 ms bound, and uniformly scales the card's text/numbers. Shared by ~35 call sites (Home, Settings, Sync, Notifications, Order Summary). | Restore a ripple or use an explicit `tween(100-150 ms)` with area-preserving X/Y scale on the container only; keep text unscaled. |
| **UM-05** | P2 | 8, 19, 20 | `feature/billing/ui/OrderConfirmationSection.kt:343-397` (`PaymentSuccessBadge`) | Ripples run 800 ms, the second starts at +200 ms, and confetti ends at 300 + 1000 = **1300 ms**. There is no tap-to-skip. This is a good peak-end moment but sits on the highest-frequency flow in the app. | Keep the checkmark pop; cut ripples/confetti to ≤400–500 ms total or make the badge dismissible on tap/Back. Reserve the long version for the first payment of a shift if a celebration is wanted. |
| **UM-06** | P2 | 12 | `feature/billing/ui/NewBillScreen.kt:506-529` (`QuantitySelector`) | The `Add` button is pinned to 32 dp and both `+`/`−` `IconButton`s are 32 dp (`IconSize.kt:13`) with no explicit `minimumInteractiveComponentSize()`. The other quantity stepper already solves this at `MenuSelectionStep.kt:609-676` and `878-916`. | Apply the same "reserve 48 dp first, draw 32 dp inside" pattern. **Device check needed:** Material3 may already enforce a 48 dp hit area internally, so this is a visual/consistency defect until measured. |
| **UM-07** | P3 | 9 | `feature/auth/ui/AppLockScreen.kt:62-69` | Wrong PIN shakes 4 × (50 + 50) + 50 = **450 ms / 8 oscillations**. The principle's reference is ~3 shakes over ~300 ms. | `repeat(3)` at 50 ms per leg → ~300–350 ms. |
| **UM-08** | P3 | 3 | `feature/reports/ui/HomeScreen.kt:95-103`; `feature/reports/ui/OrdersScreen.kt:82-86` | Entrance cascades use 80 ms gaps (4 groups = 240 ms on Home). The recommended band is 30–60 ms; total is already under 300 ms, so only the gap is off. | 40–50 ms gaps. No other change needed. |
| **UM-09** | P3 | 18 | `feature/sync/ui/DeviceSetupScreen.kt:167-172`; `core/designsystem/KhanaBookLoadingOverlay.kt:83-87` | Stage text crossfade is 400 ms in / 300 ms out (exit is 75% of entry). Loading overlay fades 200/200 (exit equals entry). | 250 ms in / 100–150 ms out for the stage text; 100 ms exit for the overlay. |
| **UM-10** | P3 | 11 | `core/designsystem/KhanaBookDialog.kt:46-53` and its callers (e.g. `OrderDetailsDialog.kt:69`) | Dialogs use the default Material3 dialog transition and appear centred; they do not grow from the row/card that opened them. | Lower priority for POS: if taking it on, animate the dialog surface from the tapped row's bounds (shared-element style) or at least establish a shorter origin-linked scale. |

---

## 3. Code-observed UI operations (newly catalogued, not device-verified)

These are motion/interaction operations found in source that were not previously itemised in `03_UI_NAVIGATION_MAP.md` or `04_SCREEN_INVENTORY.md`:

1. Every route push/pop: 300 ms fade + 20%-width horizontal slide; pop reverses direction (`AppNavGraph.kt:63-93`).
2. Every bottom-tab selection: vector icon 0.7→1.25→1.0 spring bounce; no Lottie asset is configured for the three tabs (`MainScreen.kt:301-343`, `NavigationItems.kt:27-29`).
3. Clickable shared card press: 2% scale-down, ripple suppressed (`KhanaBookCard.kt:38-57`).
4. Step 4 payment success: spring checkmark, two expanding/fading ripple rings, delayed confetti burst (`OrderConfirmationSection.kt:342-399`).
5. Wrong app-lock PIN: 450 ms horizontal shake (`AppLockScreen.kt:60-70`).
6. Home entrance: 4-group cascade at 80 ms gaps; Orders entrance: 2 groups at 80 ms (`HomeScreen.kt:88-103`, `OrdersScreen.kt:75-86`).
7. Device setup: changing stage label crossfades 400/300 ms (`DeviceSetupScreen.kt:167-180`).
8. In-app notification banner: 180 ms slide/fade in, 140 ms out; timer pauses on press, focus, or touch exploration (`InAppNotificationBanner.kt:89-106`, `:155-157`).
9. Closing-alert banner: infinite 1000 ms alpha pulse while an alert is present (`ClosingAlertBanner.kt:52-68`).
10. Global loading overlay: 200 ms fade, full-screen touch-blocking scrim, looping Lottie (`KhanaBookLoadingOverlay.kt:83-124`).
11. Launch frame: 80 ms handoff, 260 ms delayed logo pop, title/tagline/version at 280/440/640 ms delays, 2200 ms glow loop; shown only while initial routing is unresolved (`MainActivity.kt:385-398`, `BrandedStartFrame.kt:52-118`).
12. `Add` / quantity stepper on New Bill step 2: 32 dp controls, distinct from the 48 dp-reserved steppers in the cart panel (`NewBillScreen.kt:506-529`, `MenuSelectionStep.kt:609-676`).

---

## 4. What is already right (keep it)

- **Fast closing on most screens:** Home, Orders, Settings, Search, Call Customer and New Bill use ~350 ms enter / 200 ms exit (`HomeScreen.kt:111-115`, `OrdersScreen.kt:77-81`, `SettingsScreen.kt:95-99`, `SearchScreen.kt:135-139`, `CallCustomerScreen.kt:97-101`, `NewBillScreen.kt:124-126,371-389`). Shop config: 220/180 (`ShopConfigSection.kt:830-833`).
- **Notification banner:** 180/140 ms with an ease-in exit and accessibility-aware pause — a good reference implementation (`InAppNotificationBanner.kt:96-106,143-157`).
- **Toasts:** timeout is extended by `AccessibilityManager.getRecommendedTimeoutMillis` (`KhanaBookSnackbar.kt:170-183`).
- **Touch targets in the cart:** `minimumInteractiveComponentSize()` before the 28/34 dp visual size is the correct pattern (`MenuSelectionStep.kt:617-621,640-642,884-885,903-905`).
- **`ViewOrderAction`:** 22 dp visual pill inside a 48 dp clickable box (`ReportViews.kt:281-292`) — matches Fitts's law; historical screenshot `orders-view-pill-2026-11-19.png`.
- **Skeletons:** `Shimmer.kt` and `ShimmerBox` are used in order-details loading (`OrderDetailsDialog.kt:113-126`), so slow loads have feedback rather than a blank screen.
- **Launch animation is genuinely one-off:** the splash overlay only mounts while `startDestination == null` (`MainActivity.kt:387-398`), so its ~1 s intro is not repeated during normal billing.

---

## 5. Verification plan (device, not performed here)

1. Turn on *Remove animations* in Developer options; open Home → Orders → Settings and watch tab icons, route transitions, shimmer, closing-alert pulse, and payment success. Record which keep moving (UM-01).
2. Slow-motion video of a tab tap: does the icon jump to 0.7 before animating, and can a second tap retarget it mid-flight? (UM-02)
3. Measure the New Bill `Add`/`+`/`−` hit boxes with the touch overlay (`OPERATIONS/TOUCH_INDICATOR_OVERLAY.md`) at default and 200% font scale (UM-06).
4. Time the payment success badge from first frame to last frame at 1× playback (UM-05).
5. Capture screenshots into `artifacts/android-ui-tests/` and link them here and in `DEVICE_SESSIONS/SESSION_LOG.md` when any item is fixed.

**Do not implement any finding without explicit owner authorization.**

---

## 6. Implementation status (Sessions 068 + 070, 2026-11-20)

Owner authorization was given to implement the review findings. UM-02 … UM-09 were applied in Session 068; UM-01 and UM-10 were completed in Session 070 after the user said "go ahead solve it". All ten are now in the working tree.

| ID | Status | What changed (working tree) |
| --- | --- | --- |
| UM-01 | DONE (compile-verified) | New shared `core/designsystem/MotionPolicy.kt`: `rememberReducedMotion()` observes `Settings.Global.ANIMATOR_DURATION_SCALE` / `TRANSITION_ANIMATION_SCALE`; `REDUCED_MOTION_MS = 120`; `motionDuration()` and `dialogEntrance()` helpers. Session 070 wired it into every looping/bounce site: shimmer, closing-alert pulse, Home sync rotation + glow, splash logo pop + halo + particles + text slide, tab-icon press, payment badge, PIN shake, route transitions, list staggers, setup crossfade and loading overlay. Infinite loops become static; transforms become fades or are dropped. |
| UM-02 | DONE (compile-verified) | `MainScreen.kt` `AnimatedTabIcon`: removed `snapTo(0.7f)` and the two spring bounces; area-preserving `scaleX 1.08 / scaleY 0.92` press retargets from the current value, 120 ms out + 120 ms back. |
| UM-03 | DONE (compile-verified) | `AppNavGraph.kt`: exit and pop-exit now 120 ms with `FastOutLinearInEasing`; enter/pop-enter stay 300 ms ease-out. |
| UM-04 | DONE (compile-verified) | `KhanaBookCard.kt`: removed the 0.98f whole-card scale and restored the theme's default ripple, so card text/numbers no longer shrink. |
| UM-05 | DONE (compile-verified) | `OrderConfirmationSection.kt`: checkmark spring kept; ripple 800 → 360 ms, second ripple +200 → +100 ms / 800 → 320 ms, confetti +300 → +120 ms / 1000 → 300 ms. Both tails now end at ~420 ms. |
| UM-06 | DONE (compile-verified) | `NewBillScreen.kt` `QuantitySelector`: 48 dp reserved before the 32 dp visual size for `Add`, `+` and `−`, matching the cart stepper in `MenuSelectionStep.kt`. On-device hit-box measurement is still outstanding. |
| UM-07 | DONE (compile-verified) | `AppLockScreen.kt`: `repeat(4)` → `repeat(3)`; 3 × (50 + 50) + 50 = 350 ms. |
| UM-08 | DONE (compile-verified) | `HomeScreen.kt` and `OrdersScreen.kt`: stagger gaps 80 → 45 ms. |
| UM-09 | DONE (compile-verified) | `DeviceSetupScreen.kt` stage crossfade 400/300 → 250/120 ms; `KhanaBookLoadingOverlay.kt` exit 200 → 100 ms. |
| UM-10 | DONE (compile-verified) | `MotionPolicy.kt` `Modifier.dialogEntrance(origin)`: 0.94 → 1 scale + fade over 180 ms (120 ms fade-only under reduced motion), pivot taken from the caller. Applied in the shared `KhanaBookDialog.kt` (new `transformOrigin` parameter) and `OrderDetailsDialog.kt`. `OrderTableRow` in `ReportViews.kt` records its vertical centre as a 0..1 fraction via `onGloballyPositioned { boundsInWindow() }`; `OrdersScreen.kt` stores it in `dialogOriginY` and passes it as `originY`, so the details card grows from the tapped row instead of the screen centre. |

Verification performed: `./gradlew :app:compileDebugKotlin --offline` → **BUILD SUCCESSFUL** (18 tasks executed; warnings were pre-existing deprecations/opt-ins, none on the changed lines). No APK was built or installed, no device was used, and no new screenshot was captured in Session 068. Timing values in the table are read from the edited source and are arithmetic checks, not measured playback.

The source edits are in the working tree alongside pre-existing owner work and were **not committed or pushed** by Sessions 068 or 070. In Session 069 the Session-068 working-tree debug APK (SHA-256 `0A3725…140E517`) was installed on `ZA222J5KT7`; in Session 071 the updated APK including UM-01 and UM-10 (SHA-256 `6590E4…CC582`) was built and installed. Neither session launched the app, walked the UI, captured a screenshot or measured playback, so none of the §7 device checks are closed yet.

---

## 7. Verification still required on device (all findings)

These checks were **not** performed — Sessions 069 and 071 were install-only at the user's request ("update only apk debug only"). The code is compile-verified, not behaviour-verified.

1. **UM-01** — in Developer options set *Remove animations* (animator/transition duration scale = 0), then open Home → Orders → Settings and the splash/billing screens. Expected: no shimmer sweep, no closing-alert pulse, no sync rotation/glow, no splash logo pop or particles, no tab-icon scale, no PIN shake; dialogs and screen changes are short fades. Confirm the app picks up the setting while already open (the policy observes the settings URIs).
2. **UM-02** — tap a bottom tab in slow motion; confirm a single 120 ms area-preserving press, no 0.7→1.25 bounce.
3. **UM-05** — time the payment-success badge; the checkmark pops but ripples/confetti finish at ~420 ms.
4. **UM-06** — with *Show layout bounds* and 200% font size, confirm `Add`/`+`/`−` in New Bill expose 48 dp hit boxes.
5. **UM-10** — open an order from a row near the top and one near the bottom of the list; confirm the details card grows from that row's vertical position rather than the screen centre.

Link screenshots/logs into `artifacts/android-ui-tests/` and `DEVICE_SESSIONS/SESSION_LOG.md` when these checks are run.
