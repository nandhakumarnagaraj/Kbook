# In-App Touch Indicator ("Show Taps")

## Purpose

A draw-only overlay that displays a translucent dot under every active finger, including
multi-touch, and fades each dot out on release. It is entirely in-process: no Developer Options,
no `SYSTEM_ALERT_WINDOW`, no runtime permission and no manifest change. Intended for demos and
screen recordings.

## Source of truth (implementation)

| File | Responsibility |
| :--- | :--- |
| `apps/android/app/src/main/java/com/khanabook/lite/pos/core/touch/TouchIndicatorConfig.kt` | Feature flag (`session_prefs` / `show_touch_indicator`, default **false** in every build) and `TouchDotStyle`. |
| `.../touch/TouchIndicatorView.kt` | Draw-only Android `View`; `dispatchTouchEvent` always returns `false`; accepts `MotionEvent` (Activity) or Compose `PointerEvent` (dialog) snapshots. |
| `.../touch/TouchIndicatorHost.kt` | Single attachment to the Activity window decor; feeds `MainActivity.dispatchTouchEvent`. |
| `.../touch/TouchIndicatorOverlay.kt` | `@Composable` variant for surfaces that own a separate window. |
| `.../touch/TouchAwareDialog.kt` | Drop-in `Dialog` wrapper that adds the overlay inside the dialog's own window. |
| `.../core/app/MainActivity.kt` | `attach` after `enableEdgeToEdge()`, forward in `dispatchTouchEvent`, `detach` in `onDestroy`. |

## Verified architecture facts (source audit, 2026-11-19)

- Single Activity: `MainActivity : FragmentActivity`, `@AndroidEntryPoint`, `launchMode="singleTask"`.
  No `BaseActivity` exists. `androidTest` manifest declares no Activity.
- 100% Jetpack Compose UI; no `res/layout/`, no `findViewById`/`setContentView`. The only
  `AndroidView` in production wraps CameraX `PreviewView` in `OcrComponents.kt:63`.
- Root wrapper: `KhanaBookLiteTheme` (`core/theme/Theme.kt:67-161`), called from
  `MainActivity.kt:256`. The single root overlay stack is `MainActivity.kt:358-427` (NavHost,
  splash, snackbar, in-app banner). `MainScreen` is a NavHost destination, not the host.
- `enableEdgeToEdge()` at `MainActivity.kt:199`; system-bar styling in `Theme.kt:118-137`.
- Compose BOM `2024.12.01` → Compose UI `1.7.6`; Kotlin `2.0.21`; AGP `8.9.1`; minSdk 26,
  targetSdk 36. `PointerId.value` is `Long` in this version.
- Existing flag precedent: `session_prefs` holds `display_scale` and `pin_lock_enabled`;
  `BuildConfig.DEBUG` gates are used across the app.

## Touch pass-through / edge-case behaviour

- The overlay View is added last to the window decor and overrides `dispatchTouchEvent` to
  return `false`, so it is hit-tested but never consumes. `MainActivity` forwards a synchronous
  read of the event and then returns `super.dispatchTouchEvent(ev)` unchanged.
- Multi-touch is keyed by `pointerId` (`MotionEvent.getPointerId` / `PointerId.value`), not by
  the primary pointer. `ACTION_POINTER_DOWN/UP` refresh the other pointers in the same event.
- `ACTION_CANCEL` fades out all tracked pointers.
- No Compose state is written by the touch stream, so `ACTION_MOVE` does not trigger
  recomposition; dots are mutated in pre-allocated objects and drawn by `invalidate()`.

## Floating windows (verified limitation + applied coverage)

Compose `Dialog`, Material3 `AlertDialog`/`DatePickerDialog`, `ModalBottomSheet`, and
DropdownMenu/Popup create separate native windows with their own `ViewRootImpl`. Touch events in
those windows never reach `Activity.dispatchTouchEvent`, so the Activity-level overlay alone does
**not** draw there. Each such surface needs its own overlay.

**Coverage applied (Session 030 follow-up).** `TouchAwareDialog` wraps the Compose-UI `Dialog` in a
`Box` and adds `TouchIndicatorOverlay(Modifier.matchParentSize())` as its last child. The five
shared wrappers now use it, which covers all of their call sites:

- `core/designsystem/KhanaBookDialog.kt` (also covers `KhanaBookSelectionDialog`, which delegates to it)
- `core/designsystem/KhanaBookLargeDialog.kt`
- `core/components/CustomDateRangePickerDialog.kt`
- `core/components/CommonComponents.kt` (`KhanaTimePickerDialog` only)
- `feature/reports/ui/OrderDetailsDialog.kt`

The two `ModalBottomSheet`s in `feature/printing/ui/PrinterConfigSection.kt` wrap their content in
a `Box` with the same overlay.

**Still not covered:** seven raw Material3 `AlertDialog` call sites (`PermissionBlockedDialog.kt`,
`PaymentStep.kt:303,1099`, `NewBillScreen.kt:183`, `ActiveOrderDetailScreen.kt:193`,
`PaymentConfigSection.kt:297`, `ShopConfigSection.kt:999`), the Material3 `DatePickerDialog` at
`CommonComponents.kt:87`, and the DropdownMenu/Popup surfaces. These were deliberately left alone:
their APIs expose only narrow slots (`title`/`text`/buttons), so an overlay cannot cover the whole
surface without either changing dialog layout or replacing the Material3 component with a custom
one. Adding them would be a visual-behaviour change, which needs separate authorisation.

## User toggle: "Touch feedback" (Settings → App Settings → Interaction Feedback)

**Final decision (owner, 2026-11-19):** Interaction Feedback keeps **two** toggles:
"Menu item sound" (audio alert) and **"Touch feedback"**, which now controls the visible tap dots —
ON shows a dot under every finger, OFF shows nothing. It replaced the old haptic-only meaning. The
third "Show taps" row was removed rather than added alongside it. Both toggles default **OFF**.
"Voice Soundbox (Audio Alert)" in Payment Configuration also defaults OFF.

`InteractionFeedbackView` reads `TouchIndicatorConfig.enabledState` and writes through
`TouchIndicatorConfig.setEnabled(context, it)`; the preference remains `session_prefs` /
`show_touch_indicator`. Programmatic control still works for demos:

```kotlin
TouchIndicatorConfig.setEnabled(context, true)   // dots on
TouchIndicatorConfig.setEnabled(context, false)  // dots off
TouchIndicatorConfig.style = TouchDotStyle(radiusDp = 22f, fadeDurationMs = 300L)
```

`TouchIndicatorConfig` keeps two mirrored views of the flag: a `@Volatile isEnabled` read on every
touch (no recomposition in the hot path) and a Compose `State enabledState` read by the switch and
by `TouchIndicatorOverlay`. `initialize()` registers one application-lifetime `SharedPreferences`
listener, so a change is visible immediately in the switch, the Activity host (next touch) and any
dialog/bottom sheet already open.

## Verification status

- **VERIFIED (source):** architecture facts above, all call sites and window boundaries.
- **VERIFIED (build):** `:app:compileDebugKotlin` → BUILD SUCCESSFUL after the implementation.
  Two pre-existing `scaledDensity` deprecation warnings in `MainActivity.kt` are unrelated.
- **VERIFIED (device):** APK `49640f2d…c8ad9135` installed on `ZA222J5KT7`, launched with no FATAL.
  During a scripted 12 s drag along x=360 from y=700→1200, a ~100 px circular highlight was
  centered at (360, 825) at t≈3 s — the exact interpolated finger position. Screenshots:
  `artifacts/android-ui-tests/touch-indicator-drag3-2026-11-19.png` and `...-idle-...png`.
- **VERIFIED (device, temporary toggle then removed):** while the switch existed, OFF wrote
  `show_touch_indicator=false` and eliminated the dot, ON wrote `true` and restored it, and the
  value survived force-stop/relaunch (`show-taps-toggle-2026-11-19.png`). Per owner decision the row
  was then removed; APK `5fe18ce2…096c64e7` installed 15:18:32. `uiautomator` confirms only
  "Menu item sound" and "Touch feedback" on that screen — screenshot
  `artifacts/android-ui-tests/interaction-feedback-two-toggles-2026-11-19.png`.
- **VERIFIED (final default-off build):** after the owner reported taps still appearing, the debug
  default was changed to `false` and the device pref written to `false`. Final APK
  `83cce9a4…b470b94` (40,979,383 bytes) installed 15:29:22 on `ZA222J5KT7`. A/B held-touch capture
  at (360, 900) with the same APK: pref `true` → 11,661 gold dot pixels in the 120×120 touch region
  (`ab-on-touch.png`); pref `false` → **0** gold pixels (`ab-off-touch.png`). Baseline/touch pairs:
  `ab-on-base.png` / `ab-on-touch.png`, `ab-off-base.png` / `ab-off-touch.png`. Device left with the
  flag `false`; no FATAL in logcat.
- **VERIFIED (final APK, owner-tested):** APK `aebb0c82…d5f38d4d5` (41,523,233 bytes) installed
  15:40:01 on `ZA222J5KT7`. "Touch feedback" now drives the dots: ON via the UI wrote
  `show_touch_indicator=true`, switch `checked=true`, 13,609 gold dot pixels at the held touch point;
  OFF returned the flag to `false` with no dot. The owner also reported a successful hands-on test.
  "Menu item sound" and "Voice Soundbox (Audio Alert)" both read `checked=false` by default.
  Screenshot: `artifacts/android-ui-tests/interaction-feedback-final-2026-11-19.png`.
- **NOT VERIFIED:** two-finger simultaneity, fade timing, pass-through on scrollable lists, live
  updates inside an already-open dialog, and dots inside dialogs/bottom sheets.
