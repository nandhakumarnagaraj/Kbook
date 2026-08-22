# KhanaBook Android UI/UX — Device Coverage Audit & Fix Plan

## Target Range
- **Android versions:** 8.0 (API 26) → 16 (API 36)
- **Screen sizes:** All — from 320dp budget phones to 840dp+ tablets
- **Orientation:** Portrait-locked (manifest enforced)

---

## 1. Predictive Back Gesture (Android 13–16)

### Status: ✅ Already handled
- `android:enableOnBackInvokedCallback="true"` is set in AndroidManifest
- All screens use `onBack` lambda callbacks (never raw `onBackPressed()`)
- Navigation uses `navController.popBackStack()` consistently
- Compose Navigation handles predictive back animation natively

### Remaining check:
- Verify no `OnBackPressedCallback` usage that doesn't opt into predictive back
- Verify `BackHandler` composables (if any) work with the predictive animation

---

## 2. Device Testing Matrix

### Emulator Configs to Create

| Name | Width×Height dp | Density | SDK | TypeScaleTier | WidthTier | Represents |
|------|----------------|---------|-----|---------------|-----------|------------|
| `KBook_Small_320` | 320×569 | hdpi (240) | 26 | CompactPhone | Compact | Galaxy J2, Redmi Go |
| `KBook_Small_360` | 360×640 | xhdpi (320) | 28 | CompactPhone | Compact | Moto E, Realme C11 |
| `KBook_Mid_393` | 393×851 | xxhdpi (420) | 30 | MediumPhone | Compact | Pixel 4a, Moto G |
| `KBook_Mid_411` | 411×914 | xxhdpi (420) | 33 | MediumPhone | Compact | Pixel 6, Moto G34 |
| `KBook_Large_428` | 428×926 | xxxhdpi (460) | 34 | LargePhone | Compact | iPhone-sized Androids, Samsung A54 |
| `KBook_Large_480` | 480×1040 | xxxhdpi (560) | 35 | LargePhone | Compact | Samsung S24 Ultra |
| `KBook_Tablet_600` | 600×960 | mdpi (160) | 33 | Tablet | Medium | Galaxy Tab A7 Lite, Fire HD 8 |
| `KBook_Tablet_800` | 800×1280 | hdpi (240) | 36 | Tablet | Medium | Galaxy Tab A8, Lenovo Tab M10 |
| `KBook_Tablet_840` | 840×1200 | xhdpi (320) | 36 | Tablet | Expanded | Galaxy Tab S6 Lite |

### Critical Indian Market Devices (physical testing priority)
1. **Samsung Galaxy A03** — 320dp width, Android 12, budget segment
2. **Redmi Note 11** — 393dp, Android 11, mid-range king
3. **Samsung Galaxy M14** — 411dp, Android 13, popular POS device
4. **Realme Narzo 60** — 393dp, Android 14
5. **Samsung Galaxy Tab A7 Lite** — 600dp tablet, Android 12+

---

## 3. Screen-by-Screen Responsive Audit

### Priority 1 — Revenue-Critical Screens

| Screen | File | Risk | Test Focus |
|--------|------|------|------------|
| HomeScreen | `screens/home/HomeScreen.kt` | All 5 actions must fit without scroll on 360×640 | Height tiers, `sectionSpacing` |
| NewBillScreen | `screens/NewBillScreen.kt` + `screens/newbill/` | Menu grid + cart on 320dp; payment step on compact | Grid columns, keyboard overlap |
| LoginScreen | `screens/LoginScreen.kt` | Keyboard overlap, field visibility on 320dp height | `AuthFormContainer` scroll, IME |
| SearchScreen | `screens/SearchScreen.kt` | Bill results list on compact | List item density |

### Priority 2 — Daily Workflow Screens

| Screen | File | Risk | Test Focus |
|--------|------|------|------------|
| ActiveOrdersScreen | `screens/ActiveOrdersScreen.kt` | List density, action buttons | Touch targets ≥48dp |
| OrdersScreen | `screens/OrdersScreen.kt` | Table overflow on 320dp | Horizontal scroll or truncation |
| ReportsScreen | `screens/ReportsScreen.kt` | Charts/stats on compact | Stat cards wrapping |
| PrinterConfigSection | `screens/PrinterConfigSection.kt` | Bluetooth device list | Scrollable list |
| MainScreen | `screens/MainScreen.kt` | Bottom nav icons on 320dp | Icon spacing |

### Priority 3 — Setup/Onboarding (First Impression)

| Screen | File | Risk | Test Focus |
|--------|------|------|------------|
| BrandedStartFrame | `screens/BrandedStartFrame.kt` | Logo/CTA on very small screens | Logo size adaptive |
| QuickStartScreen | `screens/QuickStartScreen.kt` | Wizard steps on compact | Step indicator |
| SignUpScreen | `screens/auth/SignUpScreen.kt` | More fields than login | Form scroll |
| EasebuzzOnboardingScreen | `screens/EasebuzzOnboardingScreen.kt` | 33KB — complex form | Field density |

### Priority 4 — Settings & Config

| Screen | File | Risk | Test Focus |
|--------|------|------|------------|
| ShopConfigSection | `screens/ShopConfigSection.kt` | 24KB — many fields | Scroll + IME |
| MenuConfigurationScreen | `screens/MenuConfigurationScreen.kt` | Category/item grids | Grid layout |
| PaymentConfigSection | `screens/PaymentConfigSection.kt` | Toggle + info layout | Spacing |

---

## 4. Android Version-Specific Issues

### Android 8–9 (API 26–28) — Oldest Supported

| Issue | Impact | Fix |
|-------|--------|-----|
| No gesture navigation | System nav bar is always 48dp opaque | `navigationBarsPadding()` already handles this |
| No `WindowInsets` compose API on older compat | Need `WindowCompat.setDecorFitsSystemWindows(window, false)` | ✅ Already done via edge-to-edge |
| `isNavigationBarContrastEnforced` doesn't exist | `Build.VERSION.SDK_INT >= Q` check already guards this | ✅ |
| Google Fonts may fail (no GMS on some devices) | Font fallback to sans-serif | ⚠️ Need fallback font family |
| `BiometricPrompt` differences | Fingerprint API only on 26–27 | Verify biometric compat library handles it |

### Android 10–12 (API 29–32) — Mainstream

| Issue | Impact | Fix |
|-------|--------|-----|
| Scoped storage (API 29+) | File access restrictions | Already using FileProvider |
| `WRITE_EXTERNAL_STORAGE` deprecated 30+ | ✅ `maxSdkVersion="28"` already set |
| Bluetooth permission changes (API 31+) | ✅ `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` declared |
| Splash screen API (API 31+) | ✅ Using `core.splashscreen` compat library |

### Android 13–14 (API 33–34) — Modern

| Issue | Impact | Fix |
|-------|--------|-----|
| `POST_NOTIFICATIONS` runtime permission | ✅ Declared, need runtime request on first launch |
| Per-app language | Not relevant (single language) | No action |
| Predictive back | ✅ `enableOnBackInvokedCallback="true"` |
| Non-linear font scaling (API 34) | Text may be larger than expected | ✅ Respects `density.fontScale` |

### Android 15–16 (API 35–36) — Latest

| Issue | Impact | Fix |
|-------|--------|-----|
| Edge-to-edge enforced (API 35+) | ✅ Already edge-to-edge |
| 16KB page alignment (API 35+) | NDK only — `ndkVersion` set | Verify SQLCipher native lib is aligned |
| Predictive back mandatory (API 36) | ✅ Handled |
| Non-linear font scaling expanded | Layouts must accommodate 200% text | Need to test at max font scale |
| `targetSdk 36` restrictions | New background work limits | WorkManager handles it |

---

## 5. Font Fallback for Devices Without Google Play Services

Some budget Indian devices (Realme C series, Micromax) lack GMS or have unreliable Google Fonts downloading.

### Current Risk
```kotlin
private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)
```
If GMS is unavailable, font download fails silently and Compose falls back to default sans-serif. This isn't catastrophic but the UI will look different.

### Recommendation
Bundle a subset of Poppins as a resource fallback:
```kotlin
private val AppFontFamily = FontFamily(
    // Google Fonts (preferred — downloaded on-demand)
    Font(googleFont = PoppinsFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = PoppinsFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = PoppinsFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = PoppinsFont, fontProvider = fontProvider, weight = FontWeight.Bold),
    // Bundled fallback (always available — ~200KB per weight)
    Font(R.font.poppins_regular, weight = FontWeight.Normal),
    Font(R.font.poppins_medium, weight = FontWeight.Medium),
    Font(R.font.poppins_semibold, weight = FontWeight.SemiBold),
    Font(R.font.poppins_bold, weight = FontWeight.Bold),
)
```

**Trade-off:** +800KB APK size vs. guaranteed visual consistency on all devices.

---

## 6. Accessibility Testing Matrix

| Test | Method | Pass Criteria |
|------|--------|---------------|
| Font scale 100% | Default | All screens render correctly |
| Font scale 130% | Settings → Display → Font size Large | No clipping, all buttons visible |
| Font scale 200% | Settings → Accessibility → Display size Largest | Scrollable, no overlaps |
| TalkBack navigation | Enable TalkBack | All interactive elements announced, logical order |
| Touch target size | Layout inspector | All buttons/icons ≥48dp×48dp |
| Color contrast | Accessibility Scanner | All text meets 4.5:1 ratio (body) or 3:1 (large text) |
| Switch Access | Enable Switch Access | All actions reachable sequentially |

### Known Contrast Concerns
- `TextGold (0xFFD4A843)` on `DarkBrown2 (0xFF2D1010)` — verify ratio
- `TextMuted (0xFF8D6E63)` on `DarkBrown1 (0xFF1A0A0A)` — may fail 4.5:1
- `BorderGold (0x4DC8963C)` — 30% alpha, decorative only (OK)

---

## 7. Action Items (Ordered by Impact)

### Immediate (before next release)

- [ ] **Test on 320dp emulator** — create `KBook_Small_320` AVD and run full app flow
- [ ] **Test at 200% font scale** — verify no hard crashes or unreadable UI
- [ ] **Verify SQLCipher native libs** — 16KB page alignment for API 35+ (`ndkVersion = "26.1.10909125"` should be fine)
- [ ] **Test biometric on API 26–27** — fingerprint-only devices (no face unlock)

### Short-term (next sprint)

- [ ] **Bundle Poppins font fallback** — download 4 weights, add to `res/font/`
- [ ] **Audit touch targets** — run Accessibility Scanner on all screens
- [ ] **Contrast audit** — verify TextMuted and TextGold ratios
- [ ] **Test NewBillScreen at 320dp** — menu grid columns, cart overlay, payment keyboard

### Medium-term (v2 planning)

- [ ] **Landscape support evaluation** — portrait-locked is fine for POS but tablets may benefit
- [ ] **NavigationRail for expanded** — `useBottomNavigation = true` is noted as v1 decision
- [ ] **Foldable testing** — Z Fold fold/unfold mid-session
- [ ] **Add UI tests** — Maestro flows for critical paths on multiple screen sizes

---

## 8. Emulator Setup Commands

```bash
# Create AVDs (run from Android SDK tools or via Android Studio)

# Small phone — Indian budget segment
avdmanager create avd -n KBook_Small_320 -k "system-images;android-26;google_apis;x86_64" -d "3.2in HVGA slider (ADP1)"

# Standard phone — most common
avdmanager create avd -n KBook_Mid_393 -k "system-images;android-30;google_apis;x86_64" -d "Pixel 4a"

# Large phone — flagship
avdmanager create avd -n KBook_Large_428 -k "system-images;android-34;google_apis_playstore;x86_64" -d "Pixel 7 Pro"

# Tablet — restaurant counter use
avdmanager create avd -n KBook_Tablet_800 -k "system-images;android-36;google_apis;x86_64" -d "Nexus 10"

# Latest SDK
avdmanager create avd -n KBook_API36 -k "system-images;android-36;google_apis;x86_64" -d "Pixel 8"
```

---

## Summary

Your responsive system is **solid** — the TypeScaleTier + ResponsiveLayout + AuthFormContainer pattern covers the critical device range well. The main gaps are:

1. **No font fallback** for GMS-less devices (visual, not functional)
2. **Untested at 320dp** — smallest mainstream Indian phones
3. **Untested at 200% font scale** — accessibility requirement
4. **No contrast audit** — some muted colors may fail WCAG
5. **Portrait-only is intentional** — correct for POS but document the decision

The architecture doesn't need restructuring — it needs testing and minor hardening.
