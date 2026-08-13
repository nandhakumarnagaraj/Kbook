# Replit Ghostwriter — KhanaBook Android POS Redesign Prompt

## Project Overview

KhanaBook is an offline-first restaurant billing and POS Android app with integrated Easebuzz payment processing. Target: WCAG 2.2 AA, API 26–36, mobile + tablet.

**Brand**: Premium Saffron (#F97316 primary), Midnight Purple (#1E1035→#0F081D headers), Warm neutral backgrounds (#FAF8F5 light / #0F081D dark). Font: Poppins. All corners rounded (6dp–50dp).

**Design System Location**: `Android/app/src/main/java/com/khanabook/lite/pos/ui/theme/`
- `Color.kt` — Full palette, MaterialTheme extension properties (kbTextPrimary, kbBgCard, kbHeaderGradient, etc.)
- `Theme.kt` — Dark + Light color schemes, theme provider
- `Type.kt` — Poppins font, Material 3 type scale
- `Shape.kt` — Rounded corner tokens (6/10/14/18/24dp)
- `KbTokens.kt` — Button sizes, opacity tokens, elevation, icon sizes
- `Spacing.kt` — Spacing scale (2/4/8/12/16/20/24/32/48/64dp)

**Key Rule**: Use `MaterialTheme.kb*` extension properties instead of hardcoded hex colors. E.g., use `MaterialTheme.kbTextPrimary` not `Color(0xFF1F2937)`.

---

## Current App State (17 Screens)

| # | Screen | Status | File |
|---|--------|--------|------|
| 1 | SplashScreen | ✅ Complete | `ui/screens/SplashScreen.kt` |
| 2 | LoginScreen | ✅ Complete | `ui/screens/LoginScreen.kt` |
| 3 | SignUpScreen | ✅ Complete | `ui/screens/SignUpScreen.kt` |
| 4 | OtpVerificationScreen | ✅ Complete | `ui/screens/OtpVerificationScreen.kt` |
| 5 | InitialSyncScreen | ✅ Complete | `ui/screens/InitialSyncScreen.kt` |
| 6 | HomeScreen | ✅ Complete | `ui/screens/HomeScreen.kt` |
| 7 | OrdersScreen | ✅ Complete | `ui/screens/OrdersScreen.kt` |
| 8 | NewBillScreen | ⚠️ **1300-line god file — needs refactor** | `ui/screens/NewBillScreen.kt` |
| 9 | MenuConfigurationScreen | ✅ Complete | `ui/screens/MenuConfigurationScreen.kt` |
| 10 | EasebuzzPaymentScreen | ✅ Complete | `ui/screens/EasebuzzPaymentScreen.kt` |
| 11 | EasebuzzKycScreen | ✅ Complete | `ui/screens/EasebuzzKycScreen.kt` |
| 12 | MarketplaceOrdersScreen | ✅ Complete | `ui/screens/MarketplaceOrdersScreen.kt` |
| 13 | ReportsScreen | ✅ Complete | `ui/screens/ReportsScreen.kt` |
| 14 | SettingsScreen | ✅ Complete | `ui/screens/SettingsScreen.kt` |
| 15 | SearchScreen | ✅ Complete | `ui/screens/SearchScreen.kt` |
| 16 | CallCustomerScreen | ✅ Complete | `ui/screens/CallCustomerScreen.kt` |
| 17 | AppLockScreen | ✅ Complete | `ui/screens/AppLockScreen.kt` |

## Screens Already Redesigned
These screens already follow the Premium Saffron + Midnight Purple design system:
- All auth screens (Splash, Login, SignUp, OTP) — midnight purple gradient header, white bottom sheet form, lavender accents
- HomeScreen — saffron header strip with greeting, metric cards with saffron top border, quick action buttons
- OrdersScreen — filter chips, search bar, order cards with status chips, animated headers
- ReportsScreen — date range selector, stat cards, bar charts, popular items list
- MenuConfigurationScreen — mode selection (manual/AI), category chips, item cards, FAB
- EasebuzzPayment, KycScreen, MarketplaceOrders — premium timeline steppers, payment method grids, platform badges

## Already Done (Do NOT redo)
1. ✅ **Gradient unification** — All screen headers use `MaterialTheme.kbHeaderGradient` (midnight purple)
2. ✅ **Legacy alias migration** — CardBG→kbBgCard, TextLight→kbTextPrimary, TextMuted→kbTextSecondary migrated across all files
3. ✅ **Hex color replacements** — KbBrandSaffron, KbError, KbSuccess, KbPurpleAccent, KbLavender, etc. replaced across 10+ files
4. ✅ **Navigation tabs fixed** — Bottom nav now shows Home/Orders/Menu/Settings with proper icons
5. ✅ **Shared components** — KhanaBookCard, KhanaBookGlassCard, KhanaBookSelectionDialog, KhanaBookSwitch, KhanaBookSnackbar exist

---

## REMAINING WORK — Do These

### TASK 1: REFACTOR NewBillScreen.kt (~1300 lines)
**File**: `app/src/main/java/com/khanabook/lite/pos/ui/screens/NewBillScreen.kt`

Extract the 3 sub-steps into separate files:
1. **CustomerInfoStep** — customer selection/search, name, phone fields
2. **MenuSelectionStep** — category chips, menu item grid, cart panel
3. **PaymentStep** — payment mode selection, amount display, place order button

Each extracted composable should go in a new file under `ui/screens/newbill/` directory.

**Signature for NewBillScreen stays the same** — just delegate to the extracted components.

Pattern: Each sub-step composable takes the relevant state + callbacks as parameters.

Add `import com.khanabook.lite.pos.ui.theme.*` to each new file for theme access.

### TASK 2: REPLACE REMAINING HARDCODED HEX COLORS
Search across ALL screen files for remaining `Color(0xFF...)` and replace:

| Old Hex | Replacement | When |
|---------|-------------|------|
| `0xFF1F2937` | `MaterialTheme.kbTextPrimary` | Text color on light bg |
| `0xFF6B7280` | `MaterialTheme.kbTextSecondary` | Secondary text |
| `0xFF9CA3AF` | `MaterialTheme.kbTextTertiary` | Tertiary/muted text |
| `0xFFE5E7EB` | `MaterialTheme.kbOutlineSubtle` | Borders, dividers |
| `0xFF0F172A` | `MaterialTheme.kbTextPrimary` | Dark text on white |
| `0xFF64748B` | `MaterialTheme.kbTextSecondary` | Muted text |
| `0xFF334155` | `MaterialTheme.kbTextPrimary` | Form labels |
| `0xFF94A3B8` | `MaterialTheme.kbTextTertiary` | Placeholder text |
| `0xFF475569` | `MaterialTheme.kbTextSecondary` | +91 prefix text |
| `0xFFCBD5E1` | `MaterialTheme.kbOutlineSubtle` | Dividers, disabled bg |
| `0xFFF5F3FF` | `MaterialTheme.kbBgSecondary` | Input background |
| `0xFFFFF1F2` | `MaterialTheme.kbBgSecondary.copy(alpha = 0.8f)` | Error container |
| `0xFFE2E8F0` | `MaterialTheme.kbOutlineSubtle` | Divider lines |
| `0xFF0F081D` | `Color(0xFF0F081D)` — keep (base bg) | Page background |
| `0xFFFFFFFF` / `Color.White` | `Color.White` — keep | White surfaces |
| `0xFF2C2448` | `MaterialTheme.kbBgSecondary` | Dark containers |
| `0xFF453A68` | `MaterialTheme.kbOutlineSubtle` | Dark borders |
| `0xFF7C6B9F` | `MaterialTheme.kbTextTertiary` | Inactive text |
| `0xFFF0EEF6` | `MaterialTheme.kbBgSecondary` | Light lavender bg |

**Important**: Use `allowMultiple: true` when bulk-replacing. Only replace in screen/component files, NOT in `Color.kt` or `Theme.kt`.

### TASK 3: MIGRATE REMAINING LEGACY ALIASES
Search for these legacy aliases still in use and replace:

| Alias | Replace With | Files Affected |
|-------|-------------|----------------|
| `DarkBrown1` | Context-dependent: `MaterialTheme.kbTextPrimary` (text) or `MaterialTheme.kbBgSecondary` (container) | CallCustomerScreen, NewBillScreen, MenuConfigurationScreen, OcrScannerScreen, ReprintKdsScreen |
| `DarkBrown2` | `MaterialTheme.kbOutlineSubtle` or `MaterialTheme.kbBgSecondary` | OrdersScreen, NewBillScreen, MenuConfigurationScreen, SearchScreen, SettingsScreen |
| `BorderGold` | `MaterialTheme.kbOutlineSubtle` | KhanaBookSelectionDialog, ReprintKdsScreen, SearchScreen, SettingsScreen |
| `TextGold` | `MaterialTheme.kbSecondary` | ReprintKdsScreen, SettingsScreen, SearchScreen, TaxConfigSection |
| `RichEspresso` | `MaterialTheme.kbBgPrimary` | OrdersScreen |
| `Brown500` | `MaterialTheme.kbOutlineSubtle` or `MaterialTheme.kbBgSecondary` | OrdersScreen, PrinterConfigSection |
| `ParchmentBG` | `MaterialTheme.kbPrimaryBold` | OcrScannerScreen |
| `BrownSelected` | `MaterialTheme.kbPrimaryBold` | NewBillScreen, ReportsScreen |
| `BrandPurple` | `MaterialTheme.kbTertiary` | SettingsSharedComponents |
| `DarkBrownSheet` | `MaterialTheme.kbBgSecondary` | PrinterConfigSection |
| `ErrorPink` | `KbError` (already same value) | LoginScreen |

**Important**: Each replacement is context-dependent. When an alias is used as `color = DarkBrown1` on a Text composable, use `MaterialTheme.kbTextPrimary`. When used as `containerColor = DarkBrown1`, use `MaterialTheme.kbBgSecondary`.

### TASK 4: ANIMATION STANDARDIZATION
Unify animations across screens to use spring-based specs:
```kotlin
// Standard enter animation
val enterSpec = fadeIn(tween(350)) + slideInVertically(
    initialOffsetY = { it / 6 },
    animationSpec = tween(350, easing = FastOutSlowInEasing)
)
val exitSpec = fadeOut(tween(200))
```

Apply this pattern to any screen that uses inline animation specs (check for `tween`, `spring`, or custom easing in each screen file).

### TASK 5 (Optional): NewBillScreen UX Polish
In the NewBillScreen, improve:
- Cart panel: Add haptic feedback on quantity changes (already done partially)
- Category chips: Use saffron active state consistently
- Order total: Larger display font, saffron accent
- Place Order button: 48dp height, saffron with white text, rounded 14dp

---

## Design Tokens Quick Reference

### Colors (use via MaterialTheme.kb*)
```
kbBgPrimary, kbBgSecondary, kbBgCard    — backgrounds
kbPrimary, kbSecondary, kbTertiary      — brand colors
kbTextPrimary, kbTextSecondary, kbTextTertiary — text
kbOutlineSubtle, kbOutlineBold          — borders
kbHeaderGradient                        — screen header gradient (DO use this!)
kbTextOnBrand                           — text on brand buttons
```

### Direct Named Colors
```
KbBrandSaffron (#F97316), KbError (#DC2626), KbSuccess (#16A34A)
KbPurpleAccent (#7C3AED), KbLavender (#A78BFA)
KbWhatsAppGreen (#22C55E), KbGray300 (#BDBDBD), KbGray500 (#757575)
```

### Spacing
Use `KhanaBookTheme.spacing.small/medium/large/extraLarge` etc.
Values: 8/16/24/32dp etc.

### Shapes
Use `KbShape.Small/Medium/Large/ExtraLarge` for rounded corners.
Values: 10/14/18/50dp.

### Button Height
Use `KbButtonSize.HeightLarge` (48dp) for primary CTAs.

---

## File Access for MCP Tools

Use the MCP tools to read and write files:
- `list_directory(path: ".")` — browse project structure
- `read_file(path: "Android/app/src/...")` — read files
- `search_code(pattern: "Color\\(0xFF", flags: "-g *.kt")` — find hardcoded colors
- `search_code(pattern: "DarkBrown1|DarkBrown2|BorderGold|TextGold", flags: "-g *.kt")` — find legacy aliases
- `write_file(path: "Android/app/src/...", content: "...")` — write changes
- `run_shell(command: "cd Android && bash gradlew compileDebugKotlin 2>&1 | tail -40")` — verify compilation

---

## Quality Checks

After each change:
1. Run `cd Android && bash gradlew compileDebugKotlin 2>&1 | tail -40` — must say BUILD SUCCESSFUL
2. Fix any Unresolved reference errors by adding missing imports
3. Fix any @Composable invocation errors by adding/removing @Composable annotations
4. Never modify `Color.kt` or `Theme.kt` — only screen/component files
5. Keep the midnight purple header gradient (`MaterialTheme.kbHeaderGradient`) on every screen

---

## IMPORTANT: DO NOT CHANGE
- Server code (Java files in `server/`)
- Web admin (files in `web-admin/`)
- Android theme files (`Color.kt`, `Theme.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt`, `KbTokens.kt`)
- Android data layer (`data/`, `domain/`, `worker/`, `viewmodel/` directories)
- MainActivity.kt, Navigation setup, DI modules
- Easebuzz SDK integration code
- Any business logic or API calls
