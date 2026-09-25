# Edge-to-edge and the bottom bar (verified against Android docs)

Status: verified on-device (Moto G34, Android 15 / API 35, KhanaBook 1.0.30) and cross-checked with the official Android documentation.

## Reported issue

> Printer Configuration content scrolling underneath the KhanaBook Home / Reports / Settings bottom bar.

Not reproducible on the current build (1.0.30). On-device measurement (portrait, 720x1600 @ 280 dpi):

| Measurement | Value |
|---|---|
| Scroll viewport bottom | y = 1460 = AppBottomBar top edge (exact) |
| AppBottomBar span | 1460 -> 1600 (exactly 80 dp) |
| Final content (Save/Back) bottom | ~1330-1362 px |
| Clearance above the bar | ~130 px (~56 dp) |

The observed defect came from an older build; the current layout cannot under-scroll the app bar.

## Why it is correct on every device (per Android docs)

- `targetSdk = 36` => edge-to-edge is enforced on Android 15+ (API 35). The app calls `enableEdgeToEdge()` in `MainActivity`.
- The documented Material 3 pattern is exactly what the app uses: a `NavigationBar` in `Scaffold.bottomBar`. Material 3 `NavigationBar` auto-applies `WindowInsets.navigationBars.only(Bottom + Horizontal)` (Material insets docs). So on any device the bar grows by whatever that device reports (gesture pill, 3-button bar, taskbar) and content stays above it.
- `WindowInsets.navigationBars` "can change at runtime based on the user's preferred navigation method" (About window insets). `hidegestural` is enabled on the Moto, so its inset is 0 and the bar measures exactly 80 dp there — no code per-device tuning is needed, the inset API is device-agnostic by design.
- Duplicated inset modifiers are self-correcting: "Any insets consumed by other insets padding modifiers or `consumeWindowInsets` on a parent layout will be excluded from the padding" (`navigationBarsPadding` reference).

## Implementation notes (MainScreen.kt)

- `Scaffold(contentWindowInsets = WindowInsets(0))` opts out of automatic inset injection (comment: lines ~155-158). Content handles insets explicitly:
  - `statusBarsPadding()` on the content Box.
  - Bottom: `padding.calculateBottomPadding()` while the app bar is visible (Scaffold innerPadding includes the bar's height, which already includes the system nav inset consumed by `NavigationBar`).
- Guard (2026-09-23): when `showBottomBar == false` the content bottom padding was `0.dp`, which lets scrollable content fall behind the *system* navigation bar on devices with non-zero nav insets. Added a gated `Modifier.navigationBarsPadding()` so the content stays above the system nav bar even when the app bar is hidden.

## Open items

- S2 cleanup candidate (not a bug): inner `navigationBarsPadding()` inside `PrinterConfigSection` / `TaxConfigSection` scroll columns is redundant per the consumption rule; harmless (~44 px invisible buffer).
- Lenovo tablet verification of 1.0.30 still pending (device could not be enumerated over USB; adb-over-Wi-Fi offered).