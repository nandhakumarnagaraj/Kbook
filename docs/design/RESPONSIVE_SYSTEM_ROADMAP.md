# KhanaBook Responsive System Roadmap

**Status:** Design contract — implementation follows this, not the reverse.
**Date:** 2026-08-21
**Rule:** No screen-by-screen visual tuning until this system is implemented.

---

## Core Principle

```
DEVICE
  ↓
Window information (widthDp, heightDp, orientation, fontScale, density)
  ↓
Responsive Tier (Compact / Medium / Expanded)
  ↓
Centralized Design Tokens
  ↓
Shared Components
  ↓
Screens
```

**Measurements are inputs, not design outputs.**

Runtime diagnostics tell us the device's tier. The tier determines the design rules.
Individual device identities (OnePlus, Moto, Lenovo) never directly control the UI.

---

## Hard Design Principles

### 1. Responsive ≠ scaling everything

**Things that scale:**
- Typography (via TypeScaleTier)
- Spacing (via tier-based sectionSpacing, contentPadding)
- Content width (fill on phone → bounded on tablet)
- Certain component dimensions (cards, icons)

**Things that remain structurally stable:**
- Login / SignUp → single form column
- Home actions → single column on phones (2-col landscape only)
- Logo → preserve natural aspect ratio
- Dialog → bounded width
- Tablet → constrained content, not full-width stretching

**Things that are bounded, not continuously scaled:**
- `maxContentWidth` (560dp medium, 720dp expanded)
- `dialogMaxWidth` (420/600/680dp)
- `maxSectionGap` (absorbs residual space without infinite growth)
- Hero elements (capped by min/max formulas)

### 2. Visual equivalence, not geometric identity

```
384dp phone          411dp phone           800dp tablet
┌──────────────┐    ┌────────────────┐    ┌──────────────────────────────┐
│    Logo      │    │     Logo       │    │                              │
│    Form      │    │     Form       │    │     ┌──────────────┐        │
│    Button    │    │     Button     │    │     │    Logo      │        │
└──────────────┘    └────────────────┘    │     │    Form      │        │
                                          │     │    Button    │        │
                                          │     └──────────────┘        │
                                          └──────────────────────────────┘
```

Same design language. Different available space.

### 3. Tokens are the design, not the code

If a token says `sectionSpacing = 16dp` for Medium tier, that's the design decision.
Screens consume the token. They don't recalculate it. They don't override it.

---

## Architecture

### Tier Resolution (already exists in Responsive.kt)

| Tier | Width | Height Modifier | TypeScale |
|------|-------|----------------|-----------|
| Compact | < 600dp | isCompactHeight < 640dp | CompactPhone or MediumPhone |
| Medium | 600–839dp | — | LargePhone or Tablet |
| Expanded | ≥ 840dp | — | Tablet |

Plus height-based adjustments:
- `isCompactHeight` (< 640dp): tighter spacing, smaller heroes
- `isTallScreen` (≥ 800dp): extra card padding, larger spacing

### Token Layers

```
Layer 1: Primitive values
    2dp, 4dp, 8dp, 12dp, 16dp, 20dp, 24dp, 32dp, 48dp, 64dp

Layer 2: Semantic spacing (per tier)
    formFieldSpacing     → spacing between form inputs
    sectionSpacing       → spacing between content sections
    actionSpacing        → spacing below primary CTA
    secondarySpacing     → spacing for secondary text/links
    dialogInternalSpacing → spacing inside dialogs

Layer 3: Component dimensions (per tier)
    logoSize             → brand logo container
    inputHeight          → form input fields
    buttonHeight         → primary/secondary buttons
    iconContainerSize    → action card icons
    dialogWidth          → dialog fraction + max cap
    contentMaxWidth      → content constraint on larger devices

Layer 4: Typography (per TypeScaleTier)
    Resolved from Material3 type scale
    4 fixed variants: CompactPhone, MediumPhone, LargePhone, Tablet
```

### Shared Components (the consumption layer)

```
KhanaBookLogo          → brand logo, natural aspect ratio, responsive container
AuthFormContainer      → width constraint + centering for Login/SignUp/ForgotPwd
KhanaBookLargeDialog   → dialog with centralized width/spacing tokens
StickyBottomScaffold   → transactional screens with pinned CTA
ScrollableCenteredLayout → message/success/error states
ListLayout             → filterable lists
KhanaBookCard          → card with tier-responsive padding
KhanaPrimaryButton     → primary CTA (consistent height, font, shape)
KhanaBookInputField    → form input (consistent height, styling)
```

---

## Phases

### Phase 1 — Brand + Authentication

**Goal:** Login, SignUp, and ForgotPassword share one responsive system.

| Task | Description |
|------|-------------|
| Revise `KhanaBookLogo` | Preserve logo's natural aspect ratio inside a responsive container (not forced circle if the asset is rectangular) |
| Create `AuthFormContainer` | Shared width/centering logic: `fillMaxWidth(dialogWidthFraction).widthIn(max = dialogMaxWidth)` |
| Define semantic auth spacing | `formFieldSpacing`, `logoToTitleSpacing`, `titleToFormSpacing`, `formToActionSpacing`, `actionToFooterSpacing` |
| Align `KhanaBookLargeDialog` | Replace hardcoded fractions with `layout.dialogWidthFraction` and `layout.dialogMaxWidth` |
| Migrate Login | Consume AuthFormContainer + semantic spacing tokens |
| Migrate SignUp | Same container, same tokens |
| Migrate ForgotPasswordDialog | Dialog uses centralized tokens |
| Verify | All 3 screens on 360×640, 412×915, 800×1280 — visually equivalent, not identical |

**Success criteria:**
- Zero hardcoded dp values for width/spacing in auth screens
- One shared container for all three auth surfaces
- Dialog width matches the centralized token
- Logo preserves aspect ratio on all tiers

### Phase 2 — Shared UI System

**Goal:** Every screen consumes the same component library and spacing tokens.

| Task | Description |
|------|-------------|
| Audit all KhanaBookCard usages | Ensure `cardPaddingHorizontal`/`cardPaddingVertical` tokens are used, not raw dp |
| Audit button heights | All CTAs use `spacing.buttonHeight` / `buttonHeightCompact` / `buttonHeightLarge` |
| Audit headers | All Pattern C screens use consistent TopAppBar configuration |
| Audit empty states | All use `ScrollableCenteredLayout` with `heroImageSize` token |
| Audit list screens | All use `ListLayout` with `filterBar` pattern |
| Consolidate spacing aliases | If semantic spacing tokens from Phase 1 prove useful, extend them to all screens |

**Success criteria:**
- No screen has its own responsive width calculation
- All screens consume centralized tokens for every spacing/dimension value > 8dp
- No new `when(screenWidthDp)` or `if(isCompact)` branches that duplicate existing tokens

### Phase 3 — Screen-Level Responsive Audit

**Goal:** Remove all screen-specific responsive overrides.

| Task | Description |
|------|-------------|
| HomeScreen | Remove any per-device gap calculations; let tokens + bounded spacing work |
| ReportsScreen | Verify contentPadding, sectionSpacing consumed from tokens |
| Settings sub-screens | Verify consistent Pattern B/C usage |
| NewBill flow | Verify all steps use StickyBottomScaffold correctly |
| Tablet pass | Verify maxContentWidth is respected; content doesn't stretch |
| Landscape pass | Verify all screens reachable via scroll; no clipping |
| Font scale pass | 1.3× and 2.0× — verify ellipsis, no overlaps |

**Success criteria:**
- Install on any Android device 360dp–840dp wide → automatically looks right
- No device-specific branches remain outside of `ResponsiveLayout` itself
- Visual QA checklist passes on all 4 test devices (moto, OPPO, OnePlus, Lenovo)

---

## Anti-Patterns (do NOT do these)

| Anti-pattern | Why it's wrong | Correct approach |
|-------------|---------------|-----------------|
| `if (screenWidthDp == 384) spacing = 52.dp` | Device-specific, breaks on new devices | Use tier → token resolution |
| `Modifier.size(layout.logoSize).clip(CircleShape)` when logo is rectangular | Forces wrong aspect ratio | Container is responsive; logo fits naturally inside |
| Dialog with `fillMaxWidth(0.94f)` hardcoded | Doesn't adapt to tier changes | Use `layout.dialogWidthFraction` |
| Adding `Spacer(Modifier.height(X.dp))` between specific screens | Per-screen fix, not systematic | Define semantic spacing token, consume everywhere |
| Making tablet UI by scaling everything up | Looks bloated, not designed | Constrain content width, increase rhythm spacing only |
| Tuning gaps by running on one device until it "looks right" | Breaks on other devices | Define bounded token, verify on 3+ profiles |

---

## Evidence-Based Workflow

For any future responsive change:

```
1. Identify the visual problem (screenshot + device)
2. Determine which tier the device falls into
3. Check if a token already covers this case
4. If yes → the screen is consuming the token wrong → fix consumption
5. If no → propose a new token → add to ResponsiveLayout → consume everywhere
6. Verify on 3+ device profiles (compact, medium, tablet)
7. Never verify on only one device
```

---

## Related Documents

- `docs/design/KHANABOOK_RESPONSIVE_DESIGN_SPEC.md` — Token reference and migration map
- `docs/design/LayoutGuidelines.md` — Layout primitive rules
- `docs/design/ResponsiveLayoutMigration.md` — Migration history
- `docs/design/DESIGN_SYSTEM_FREEZE.md` — Locked palette, typography, shapes
- `docs/meta/ANDROID_UI_RULES.md` — Existing pattern preservation
- `Android/.../ui/theme/Responsive.kt` — Token implementation (source of truth)
