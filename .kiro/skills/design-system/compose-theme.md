# Compose Design System & Theme

## Trigger Conditions
- Creating or modifying the app's theme
- Adding new color tokens or typography styles
- User asks about MaterialTheme extensions
- Implementing dark/light mode support
- Ensuring consistent spacing and shape across screens

---

## Color Tokens (KhanaBook Palette)

```kotlin
// ui/theme/Color.kt
object KhanaBookColors {
    // Primary: Burgundy
    val Burgundy900 = Color(0xFF4A0E1B)
    val Burgundy800 = Color(0xFF6B1527)
    val Burgundy700 = Color(0xFF8B1A32)  // Primary
    val Burgundy600 = Color(0xFFA52040)
    val Burgundy500 = Color(0xFFBF264D)
    val Burgundy100 = Color(0xFFFDE8EC)

    // Secondary: Gold
    val Gold900 = Color(0xFF5C4300)
    val Gold700 = Color(0xFFA87C00)
    val Gold500 = Color(0xFFD4A017)      // Secondary
    val Gold300 = Color(0xFFE8C84A)
    val Gold100 = Color(0xFFFFF8E1)

    // Neutrals
    val Neutral900 = Color(0xFF1A1A1A)
    val Neutral700 = Color(0xFF424242)
    val Neutral500 = Color(0xFF757575)
    val Neutral300 = Color(0xFFBDBDBD)
    val Neutral100 = Color(0xFFF5F5F5)
    val White = Color(0xFFFFFFFF)

    // Semantic
    val Success = Color(0xFF2E7D32)
    val Error = Color(0xFFC62828)
    val Warning = Color(0xFFEF6C00)
    val Info = Color(0xFF1565C0)
}

// Light color scheme
val KhanaBookLightColors = lightColorScheme(
    primary = KhanaBookColors.Burgundy700,
    onPrimary = KhanaBookColors.White,
    primaryContainer = KhanaBookColors.Burgundy100,
    secondary = KhanaBookColors.Gold500,
    onSecondary = KhanaBookColors.Neutral900,
    background = KhanaBookColors.White,
    surface = KhanaBookColors.White,
    error = KhanaBookColors.Error,
    onBackground = KhanaBookColors.Neutral900,
    onSurface = KhanaBookColors.Neutral900
)

// Dark color scheme
val KhanaBookDarkColors = darkColorScheme(
    primary = KhanaBookColors.Burgundy500,
    onPrimary = KhanaBookColors.White,
    primaryContainer = KhanaBookColors.Burgundy900,
    secondary = KhanaBookColors.Gold300,
    onSecondary = KhanaBookColors.Neutral900,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    error = Color(0xFFEF5350)
)
```

## Typography Scale

```kotlin
// ui/theme/Type.kt
val KhanaBookTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
)
```

## Spacing System

```kotlin
// ui/theme/Spacing.kt
data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

// Access: MaterialTheme.spacing.md
val MaterialTheme.spacing: Spacing
    @Composable @ReadOnlyComposable
    get() = LocalSpacing.current
```

## Shape System

```kotlin
// ui/theme/Shape.kt
val KhanaBookShapes = Shapes(
    small = RoundedCornerShape(4.dp),   // Chips, small cards
    medium = RoundedCornerShape(8.dp),  // Cards, dialogs
    large = RoundedCornerShape(16.dp)   // Bottom sheets, large containers
)
```

## Custom MaterialTheme Extension

```kotlin
// ui/theme/Theme.kt
@Composable
fun KhanaBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) KhanaBookDarkColors else KhanaBookLightColors

    CompositionLocalProvider(
        LocalSpacing provides Spacing()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KhanaBookTypography,
            shapes = KhanaBookShapes,
            content = content
        )
    }
}

// Usage in Composables
@Composable
fun BillCard(bill: Bill) {
    Card(
        modifier = Modifier.padding(MaterialTheme.spacing.md),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Text(
            text = bill.customerName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
```

## Component Tokens (KhanaBook-specific)

```kotlin
// Extended semantic colors for POS-specific UI
object KhanaBookSemanticColors {
    val BillPending = Color(0xFFFFF3E0)     // Orange tint
    val BillPaid = Color(0xFFE8F5E9)        // Green tint
    val BillCancelled = Color(0xFFFFEBEE)   // Red tint
    val SyncPending = Color(0xFFFFF8E1)     // Gold tint
    val SyncComplete = Color(0xFFE8F5E9)    // Green tint
}
```

---

## Anti-patterns
- ❌ Hardcoding colors (Color(0xFF...) in composables — use theme tokens
- ❌ Using sp for non-text sizes (use dp)
- ❌ Mixing Material2 and Material3 components
- ❌ Creating new TextStyles inline instead of using typography scale
- ❌ Ignoring dark mode (all custom colors need dark variants)
- ❌ Magic numbers for padding (use spacing system)

## Verification Checklist
- [ ] All colors defined as theme tokens (no hardcoded hex in composables)
- [ ] Typography scale covers all text sizes used in app
- [ ] Spacing system used consistently (no arbitrary dp values)
- [ ] Dark mode tested and all text is readable
- [ ] Contrast ratios pass WCAG AA (4.5:1 normal, 3:1 large text)
- [ ] Theme applied at app root via KhanaBookTheme {}
- [ ] Preview composables use KhanaBookTheme wrapper
