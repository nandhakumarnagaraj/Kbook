# KhanaBook — Existing UI Pattern Preservation Rules

Before implementing any new screen or feature, you MUST inspect and follow the existing UI patterns documented below.

**Do not create your own header, bottom navigation, spacing, background, inset handling, or animation patterns when an existing pattern already applies.**

## 1. Header

Use the existing header patterns:

* `CenterAlignedTopAppBar`

  * Title: `PrimaryGold`
  * Container: `DarkBrown1`
  * Navigation icon: `ArrowBack`

OR

* `KhanaBookScreenScaffold` for simple back + title screens.

Do not create a custom header unless the existing patterns genuinely cannot support the requirement.

---

## 2. Bottom Navigation

* `MainScreen` is the only screen that uses the persistent `NavigationBar`.
* `NavigationBar` must use `navigationBarsPadding()`.

For all other screens:

* Do NOT add bottom navigation.
* For sticky actions/buttons, use the existing `StickyBottomScaffold`.

Do not introduce a new bottom navigation pattern.

---

## 3. Background

Preserve the existing KhanaBook background:

```kotlin
Brush.verticalGradient(
    listOf(
        DarkBrown1,
        DarkBrown2,
        RichEspresso
    )
)
```

Do not introduce a different background or gradient unless explicitly required by the product/design specification.

---

## 4. Window Insets

For Scaffold-based screens:

* Use the existing `contentWindowInsets` approach.
* Preserve the pattern:

```kotlin
contentWindowInsets = WindowInsets(0)
```

* Apply the Scaffold `paddingValues`.
* Use `consumeWindowInsets(paddingValues)` where applicable.

For full-screen layouts:

* `statusBarsPadding()`
* `navigationBarsPadding()`
* `imePadding()`

must be handled consistently with the existing implementation.

Do not invent a new inset strategy.

---

## 5. Spacing

**Never introduce arbitrary hardcoded `dp` values when an existing design token applies.**

Use:

```kotlin
KhanaBookTheme.spacing.*
```

Use the existing layout tokens, including:

```kotlin
layout.contentPadding
spacing.bottomListPadding
```

For lists, ensure sufficient bottom protection using the existing spacing pattern.

Before creating a new spacing value, first verify whether an existing spacing token already satisfies the requirement.

---

## 6. State Collection

For Flow/StateFlow collection in Compose, use:

```kotlin
collectAsStateWithLifecycle()
```

Do not introduce:

```kotlin
collectAsState()
```

unless there is a specific, documented reason.

---

## 7. Theme Tokens

Extract and use the appropriate theme/design tokens at the top of the composable rather than scattering raw values throughout the UI.

Prefer existing:

* Colors
* Typography
* Spacing
* Shapes
* Icon sizes
* Responsive values

over newly introduced values.

---

## 8. Animations

Preserve the existing animation language.

### Screen Entry

Use the established staggered entry pattern:

```kotlin
fadeIn(tween(350)) + slideInVertically(
    initialOffsetY = { it / 6 },
    animationSpec = tween(350, easing = FastOutSlowInEasing)
)
```

### Step Transitions

Use:

```kotlin
(fadeIn() + slideInHorizontally { it / 3 }) togetherWith
    (fadeOut() + slideOutHorizontally { -it / 3 })
```

Do not introduce unrelated animation styles without a specific requirement.

---

## 9. Layout Pattern Selection Guide

| Requirement | Pattern | Example |
|---|---|---|
| New tab inside MainScreen | Pattern A — no scaffold, parent handles insets | HomeScreen, ReportsScreen |
| New detail/form screen with back arrow | Pattern C — `Scaffold` + `CenterAlignedTopAppBar` + `DarkBrown1` | SearchScreen, ActiveOrdersScreen, CallCustomerScreen |
| Simple settings sub-screen | Pattern B — `KhanaBookScreenScaffold` | SettingsScreen sections |
| Auth/onboarding/full-screen message | Pattern D — Box + gradient + manual insets | LoginScreen, AppLockScreen, QuickStartScreen |
| Centered success/error/empty state | `ScrollableCenteredLayout` | InitialSyncScreen |
| Screen with sticky bottom button | `StickyBottomScaffold` | — |

---

## 10. Mandatory AI Workflow Before Coding

When implementing a new feature:

### Step 1 — Inspect
Inspect existing screens, navigation graph, scaffolds, header/bottom components, theme tokens, spacing, insets, and similar screens.

### Step 2 — Find the Closest Existing Pattern
Identify the screen structurally closest to the new requirement. State:
- Closest existing screen
- Scaffold/component to reuse
- Header, bottom, spacing, inset, navigation patterns

### Step 3 — Reuse
Reuse existing components and patterns wherever possible.

### Step 4 — Implement
Only after analysis, implement the feature.

### Step 5 — Verify
Verify the new screen uses the correct header, background, spacing tokens, insets, state collection, animations, and navigation.

---

## Critical Rule

**The AI must NOT make independent UI/UX decisions simply because it is implementing a new feature.**

The existing KhanaBook application is the source of truth.

If a new requirement conflicts with an existing pattern:
1. Identify the conflict.
2. Show the existing pattern.
3. Explain what the new requirement needs.
4. Ask for clarification before introducing a new pattern.

**Do not silently create a new UI pattern.**

The goal is to make every new screen feel like it was always part of the existing KhanaBook application.
