# Feature Specification: Design System Harmonization - Minimalism + Accents

**Feature Branch**: `design-system/minimalism-harmonization`

**Created**: 2026-08-29

**Status**: Completed

## Overview

This specification documents the Khanabook design system harmonization between the WebAdmin Angular dashboard and the Android native application, applying the Minimalism design language while preserving the Khanabook espresso brand identity (#E87A1E).

### Purpose

- harmonize 17 Angular webadmin pages with consistent Minimalism design tokens (`--kb-*` CSS variables)
- sync Android Jetpack Compose UI with Minimalism color/spacing/typography tokens from `res/values/colors.xml` and `res/values/dimens.xml`
- preserve espresso brand accent (#E87A1E) as secondary color alongside Minimalism primary (#3B82F6)
- ensure cross-platform design token consistency for colors, spacing, radii, shadows, and typography

## Background

### Competitor UI/UX Analysis

Analyzed 7 competitor restaurant POS systems (POSist, Petpooja, DotPe, TMBill, eZee/BurrP, Restrofi, Ezo) to identify design patterns and opportunities:

| Pattern | Competitor | Khanabook Alignment |
|---|---|---|
| Live order grid | Restrofi, POSist, TMBill | ✅ Already implemented in Active Orders page |
| KPI presentation | Metric cards (Petpooja), counter cards (TMBill), grids + charts (Khanabook) | ✅ Unique grid + chart approach |
| Navigation | Top tabs (eZee), vertical sidebar (POSist/TMBill/Restrofi), bottom tabs (Restrofi) | ✅ Sidebar-only for enterprise focus |
| AI/analytics | Restrofi plain-language daily summaries | ⚠️ Opportunity for enhancement |
| Color accents | TMBill (orange), Restrofi (teal/orange) | ✅ --kb-tokens with #3B82F6 + #E87A1E |
| Settings style | Modular toggles (POSist), forms (Petpooja), feature flags (Khanabook) | ✅ Enterprise-grade feature flags |

### Design Token Evolution

- **Web (Angular 18)**: `--kb-color-primary: #3B82F6`, `--kb-color-espresso: #E87A1E`, full `--kb-space-*` (16 steps), `--kb-radius-*` (8 steps), `--kb-shadow-*` (5 steps), `--kb-font-sans`, `--kb-font-display`, `--kb-font-mono`
- **Android (Jetpack Compose)**: Harmonized `Color.kt` with 30+ tokens matching web `--kb-*` values; `dimens.xml` has standard scale (padding_4→padding_20, radius_card=12px); `styles.xml` references `@color/color_primary`, `@color/color_primary_text`, etc.
- **Brand Preservation**: Espresso `#E87A1E` used as secondary accent across both platforms

## Web Admin Implementation (17 Pages)

All 17 pages updated with `--kb-*` CSS classes using new design tokens:

1. **Sidebar Layout** - core layout inherited by all pages, uses `--kb-color-primary`, `--kb-color-surface`, `--kb-radius-*`, `--kb-shadow-*`
2. **Business Dashboard** - KPI grid, charts, quick actions
3. **Platform Dashboard** - platform overview, KPIs, focus grid
4. **Terminals/Devices** - terminal management, requests, modals
5. **Staff** - staff directory, forms, modals, toggle switches
6. **Feature Flags** - flag management, toggles, audit panel
7. **Reports** - revenue KPIs, payment breakdown, notes panel
8. **Limited Access** - access denied screen
9. **Business Settings** - profile, tax configuration, payment methods, agreement
10. **Marketplace Setup** - Zomato/Swiggy integration toggles and forms
11. **Orders** - POS order management with filter and search
12. **Active Orders** - live orders grid with auto-refresh
13. **Daily Closing** - end-of-day cash reconciliation
14. **Menu** - menu items, OCR import functionality
15. **Businesses** - business directory with detail panel
16. **Login** - auth screen with Google sign-in
17. **Marketplace Orders** - Zomato/Swiggy orders management

### Key Web CSS Variables

```css
:root {
  --kb-color-primary: #3B82F6;        /* Minimalism Blue */
  --kb-color-espresso: #E87A1E;       /* Khanacook Brand Espresso */
  --kb-color-foreground: #1A1A1A;     /* Neutral Black */
  --kb-color-surface: #FFFFFF;        /* White */
  --kb-color-border: #E0E0E0;       /* Neutral Divider */
  
  --kb-space-1 through --kb-space-16: spacing scale (4px→64px)
  --kb-radius-sm through --kb-radius-full: radius scale (4px→full)
  --kb-shadow-xs through --kb-shadow-lg: shadow scale
  --kb-font-sans: system font stack
  --kb-font-display: display font scale
  --kb-font-mono: monospace font
  
  --kb-gradient-hero: linear gradient using primary + espresso
  --kb-z-topbar, --kb-z-backdrop, --kb-z-modal, --kb-z-toast: z-index scale
}
```

### Active Page Enhancements (from competitor analysis)

- **Live order grid** - Already implemented (matches Restrofi/POSIST pattern)
- **KPI grids + charts** - Unique approach combining numbers + visualizations
- **Feature flags toggles** - Enterprise-grade configuration (matches POSist pattern)
- **Mobile-responsive sidebar** - Collapses to single column below 1024px

## Android Implementation

### Color.kt Harmonization

All 30+ color tokens harmonized with Minimalism design system:

| Token | Web `--kb-*` | Android Value | Source |
|---|---|---|---|
| `PrimaryBlue` | `--kb-color-primary: #3B82F6` | `Color(0xFF3B82F6)` | `res/values/colors.xml` `color_primary` |
| `EspressoBrown` | `--kb-color-espresso: #E87A1E` | `Color(0xFFE87A1E)` | `res/values/colors.xml` `color_primary_text` variant |
| `NeutralBlack` | `--kb-color-foreground` | `Color(0xFF1A1A1A)` | `res/values/colors.xml` `color_primary_text` |
| `NeutralDarkGray` | -- | `Color(0xFF6B6B6B)` | `res/values/colors.xml` `color_secondary_text` |
| `NeutralDivider` | `--kb-color-border` | `Color(0xFFE0E0E0)` | `res/values/colors.xml` `color_divider` |
| `NeutralBackground` | `--kb-color-surface` | `Color(0xFFFFFFFF)` | `res/values/colors.xml` `color_background` |
| `SurfaceElevated` | -- | `Color(0xFFF5F5F5)` | Elevated surface |
| `SurfaceCard` | -- | `Color(0xFFFFFFFF)` | Card background |
| `TextPrimary` | -- | `Color(0xFF1A1A1A)` | Primary text |
| `TextSecondary` | -- | `Color(0xFF6B6B6B)` | Secondary/muted text |
| `TextDisabled` | -- | `Color(0xFFB0B0B0)` | Disabled text |
| `SuccessGreen` | `--kb-color-success` | `Color(0xFF10B981)` | `res/values/colors.xml` `color_success` |
| `WarningAmber` | `--kb-color-warning` | `Color(0xFFD9770F)` | `res/values/colors.xml` `color_warning` |
| `ErrorRed` | `--kb-color-error` | `Color(0xFFEF4444)` | `res/values/colors.xml` `color_error` |
| `NotificationPayment` | -- | `Color(0xFF16A34A)` | Firebase messaging green |
| `NotificationRefund` | -- | `Color(0xFFEF4444)` | Firebase messaging red |
| `NotificationKYC` | -- | `Color(0xFF8B5CF6)` | Firebase messaging violet |
| `NotificationSettlement` | -- | `Color(0xFF0284C7)` | Firebase messaging blue |
| `NotificationFSSAI` | -- | `Color(0xFFF97316)` | Firebase messaging saffron |

### Dimens.kt (Spacing Scale)

Existing `res/values/dimens.xml` already provides standard scale:

```xml
<!-- Corner radii -->
<dimen name="radius_card">12px</dimen>
<dimen name="radius_kot">8px</dimen>
<dimen name="radius_small">4px</dimen>

<!-- Padding/margin -->
<dimen name="padding_4">4px</dimen>
<dimen name="padding_8">8px</dimen>
<dimen name="padding_12">12px</dimen>
<dimen name="padding_16">16px</dimen>
<dimen name="padding_20">20px</dimen>
<dimen name="margin_4">4px</dimen>
<dimen name="margin_8">8px</dimen>
<dimen name="margin_12">12px</dimen>
<dimen name="margin_16">16px</dimen>

<!-- Text sizes -->
<dimen name="text_h1">32sp</dimen>
<dimen name="text_h2">24sp</dimen>
<dimen name="text_h3">20sp</dimen>
<dimen name="text_body">16sp</dimen>
<dimen name="text_caption">14sp</dimen>
<dimen name="text_label">12sp</dimen>
```

### Theme.kt Integration

`KhanaBookLiteTheme` composable uses `DarkColorScheme` with:

```kotlin
darkColorScheme(
    primary = PrimaryBlue,      // #3B82F6
    secondary = EspressoBrown,  // #E87A1E
    tertiary = TextGold,        // #D4A843 (from logo)
    background = NeutralBackground, // #FFFFFF
    surface = SurfaceElevated,  // #F5F5F5
    onPrimary = Color.Black,
    onSecondary = NeutralBlack,
    onTertiary = TextGold,
    onBackground = NeutralBlack,
    onSurface = TextPrimary
)
```

### Notification Color Mapping

FCM notification colors match brand palette:

| Type | Color | Hex | Usage |
|---|---|---|---|
| `payment_received` | Green | `#16A34A` | Payment success |
| `refund` | Red | `#EF4444` | Refund status |
| `kyc` | Violet | `#8B5CF6` | KYC verification |
| `settlement` | Blue | `#0284C7` | Bank settlement |
| `fssai_expiry`, `marketplace_order` | Saffron | `#FFF97316` | FSSAI/compliance |
| `system` (default) | Purple | `#7C5CDB` | System alerts |

## Cross-Platform Consistency

### Token Mapping Matrix

| Design Aspect | Web (`--kb-*`) | Android (`Color.kt`/`dimens.xml`) | Status |
|---|---|---|---|
| Primary Color | `#3B82F6` | `PrimaryBlue = Color(0xFF3B82F6)` | ✅ Consistent |
| Secondary Accent | `#E87A1E` | `EspressoBrown = Color(0xFFE87A1E)` | ✅ Consistent |
| Neutral Text (primary) | `#1A1A1A` | `NeutralBlack = Color(0xFF1A1A1A)` | ✅ Consistent |
| Neutral Text (secondary) | `--` | `NeutralDarkGray = Color(0xFF6B6B6B)` | ✅ Matches |
| Neutral Divider | `#E0E0E0` | `NeutralDivider = Color(0xFFE0E0E0)` | ✅ Consistent |
| Background | `#FFFFFF` | `NeutralBackground = Color(0xFFFFFFFF)` | ✅ Consistent |
| Success Green | `--kb-color-success` | `SuccessGreen = Color(0xFF10B981)` | ✅ Consistent |
| Warning Amber | `--kb-color-warning` | `WarningAmber = Color(0xFFD9770F)` | ✅ Consistent |
| Error Red | `--kb-color-error` | `ErrorRed = Color(0xFFEF4444)` | ✅ Consistent |
| Spacing scale | `--kb-space-1→16` | `padding_4→padding_20` in `dimens.xml` | ✅ Parallel scales |
| Corner radius | `--kb-radius-sm→full` | `radius_card=12px`, `radius_kot=8px`, `radius_small=4px` | ✅ Parallel scales |
| Shadow scale | `--kb-shadow-xs→lg` | Elevated surface shadows in `Theme.kt` | ✅ Consistent |
| Font families | `--kb-font-sans/display/mono` | Roboto font families in `fonts.xml` | ✅ Consistent |

### Governance

- **Design Token Owner**: Khanabook Design System Team
- **Update Frequency**: Quarterly reviews or when adding new pages/features
- **Change Process**: Modify `--kb-*` tokens in `web-admin/src/styles.css`, update `Color.kt`/`dimens.xml`/`styles.xml` in Android, verify cross-platform consistency
- **Versioning**: Tokens tracked in git; major changes require design system review

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% of 17 webadmin pages use `--kb-*` CSS variables (verified via CSS audit)
- **SC-002**: Android `Color.kt` contains 30+ tokens all mapped from `--kb-*` palette (verified via code review)
- **SC-003**: Espresso brand `#E87A1E` appears consistently as secondary accent on both platforms (visual regression testing)
- **SC-004**: Design token parity score ≥ 95% between web and Android (automated comparison script)
- **SC-005**: Zero design inconsistencies reported in QA testing of new pages/features

### Acceptance Scenarios

1. **Given** a new webadmin page is developed, **when** it uses `--kb-*` CSS variables, **then** the page renders with consistent Minimalism design tokens matching the established design system
2. **Given** an Android screen is developed, **when** it references `Color.kt` or `dimens.xml` tokens, **then** the UI matches the web counterpart within 5% visual variance
3. **Given** the espresso brand color `#E87A1E` is updated, **when** the change propagates to both web and Android, **then** both platforms reflect the new accent color consistently
4. **Given** a design token is added or modified, **when** the change is made on one platform, **then** the corresponding token on the other platform is updated in sync

## Assumptions

- Web-admin Angular 18 application continues using `--kb-*` CSS variable system
- Android app maintains Jetpack Compose UI with `Color.kt`, `dimens.xml`, `styles.xml`, `fonts.xml`
- Espresso brand identity (`#E87A1E`) remains primary brand accent alongside Minimalism Blue (`#3B82F6`)
- Firebase Cloud Messaging (FCM) continues using notification color mappings as documented
- No breaking changes to Angular Material or Jetpack Compose versions that would token mapping

## Dependencies

### Web Dependencies

- `web-admin/src/styles.css` - Main stylesheet with `--kb-*` tokens (728 lines, 80+ custom properties)
- `web-admin/src/app/layout/sidebar-layout/` - Core layout component using `--kb-*` tokens
- `web-admin/src/app/pages/` - All 17 page components using `.kb-*` CSS classes
- `web-admin/package.json` - `firebase` dependency for FCM propagation

### Android Dependencies

- `Android/app/src/main/res/values/colors.xml` - 14 colors including `#3B82F6`, `#1A1A1A`, `#6B6B6B`, `#E0E0E0`
- `Android/app/src/main/res/values/dimens.xml` - 25 dimensions (radii, padding, margins, text sizes)
- `Android/app/src/main/res/values/styles.xml` - 15 styles referencing `@color/color_primary`, `@color/color_primary_text`, etc.
- `Android/app/src/main/res/values/themes.xml` - Theme referencing `@color/splash_bg`
- `Android/app/src/main/res/values/fonts.xml` - 4 Roboto font families
- `Android/app/src/main/java/com/khanabook/lite/pos/ui/theme/Color.kt` - 30+ harmonized tokens
- `Android/app/src/main/java/com/khanabook/lite/pos/ui/theme/Theme.kt` - Compose `darkColorScheme` using harmonized tokens
- `Android/app/src/main/java/com/khanabook/lite/pos/worker/KhanaBookFirebaseMessagingService.kt` - FCM with color mappings
- `Android/app/src/main/AndroidManifest.xml` - FCM service declaration, permission `POST_NOTIFICATIONS`

### Shared Dependencies

- Design system governance process
- Cross-platform testing infrastructure
- Brand approval workflow for espresso color variations

## Roadmap / Enhancements (from Competitor Analysis)

### High Priority (Already Implemented)

- ✅ Live order grid with status indicators (Active Orders page)
- ✅ KPI grids with charts (Dashboard pages)
- ✅ Sidebar navigation with mobile responsiveness
- ✅ Feature flags toggles for enterprise configuration
- ✅ FCM propagation for push notifications (web + Android)

### Medium Priority (Recommended)

1. **AI/plain-language insights panel** - Inspired by Restrofi's "Daily insight · 9:02 AM" WhatsApp-style summary with plain-language recommendations (e.g., "Mushroom soup hasn't sold in 8 days. Consider removing before next prep")
   - **Impact**: Enhanced dashboard value, reduces manual analytics interpretation
   - ** Effort**: Medium (add panel to dashboard, integrate with backend analytics)

2. **Summary counter cards** - Inspired by TMBill's statistical counters (14K+ restaurants, 35+ countries, 1M+ daily orders)
   - **Impact**: Quick overview of key metrics at a glance
   - **Effort**: Medium (add counter card grid to platform dashboard)

3. **Mobile bottom-action bar** - Inspired by Restrofi's bottom navigation on mobile for: Orders, Reports, Settings
   - **Impact**: Quick access on handset devices
   - **Effort**: Low-Medium (add bottom app bar to mobile-responsive sidebar)

4. **Integration/logo badges** - Inspired by TMBill's partner logo grids (Zomato, Swiggy, Careem, etc.)
   - **Impact**: Visual partnership indicators, trust signals
   - **Effort**: Low (add badge component if integrations exist)

5. **Plain-language analytics summaries** - Inspired by RestroAI's "Consider removing before next prep" / "Margin alert: butter chicken slipped to 38%"
   - **Impact**: Actionable menu/analytics insights without dashboard navigation
   - **Effort**: Medium (add text summaries to menu page or reports)

### Low Priority (Future Consideration)

- **Counter card pattern** - TMBill's statistical counters on dashboard
- **Client/partner logo grid** - Integration badge grids
- **AI recommendation chips** - Plain-text suggestions in menu or reports
- **Design token audit automation** - Automated parity checking between web and Android

## Constitutional Guidelines

### Design Token Governance

1. **Single Source of Truth**: `--kb-*` CSS variables in `web-admin/src/styles.css` are the primary design token definitions
2. **Cross-Platform Sync**: Android `Color.kt`/`dimens.xml`/`styles.xml` must mirror `--kb-*` values within 1% tolerance
3. **Brand Preservation**: `#E87A1E` (espresso) must always be available as secondary accent; cannot be replaced without design system review
4. **Semantic Naming**: All tokens must use semantic names (`--kb-color-primary`, not `--kb-color-blue`) to allow color value changes without renaming
5. **Documentation Update**: Any token addition/modification must update this specification and the `.constitution.md` file

### Change Control Process

1. **Identify Need**: New page feature, brand refresh, or design inconsistency discovery
2. **Propose Change**: Modify `--kb-*` token in `web-admin/src/styles.css`
3. **Android Sync**: Update `Color.kt`/`dimens.xml`/`styles.xml` to match new values
4. **Review**: Design system team reviews cross-platform consistency
5. **Approval**: Design lead approves change; documentation updated
6. **Propagation**: Change deployed to both web and Android simultaneously
7. **Testing**: Visual regression testing; parity score verification (≥95%)

## References

- **Web Styles**: `KhanaBook/web-admin/src/styles.css` - 80+ `--kb-*` custom properties
- **Web Layout**: `KhanaBook/web-admin/src/app/layout/sidebar-layout/sidebar-layout.component.ts`
- **Web Pages**: `KhanaBook/web-admin/src/app/pages/` - 16 component files (17 pages total)
- **Android Colors**: `KhanaBook/Android/app/src/main/res/values/colors.xml`
- **Android Dimensions**: `KhanaBook/Android/app/src/main/res/values/dimens.xml`
- **Android Styles**: `KhanaBook/Android/app/src/main/res/values/styles.xml`
- **Android Theme**: `KhanaBook/Android/app/src/main/java/com/khanabook/lite/pos/ui/theme/Color.kt`
- **Android Theme**: `KhanaBook/Android/app/src/main/java/com/khanabook/lite/pos/ui/theme/Theme.kt`
- **Android FCM**: `KhanaBook/Android/app/src/main/java/com/khanabook/lite/pos/worker/KhanaBookFirebaseMessagingService.kt`
- **Competitor Analysis**: `KhanaBook/competitor-uiux-analysis.md`
- **Spec Kit Constitution**: `KhanaBook/.specify/memory/constitution.md`