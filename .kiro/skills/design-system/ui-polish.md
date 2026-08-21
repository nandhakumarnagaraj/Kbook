# UI Polish Audit

## Trigger Conditions
- Screen implementation is functionally complete
- User asks to "polish", "refine", or "review UI"
- Before shipping any user-facing screen
- When accessibility audit is needed
- Reviewing loading, empty, and error states

---

## Spacing Consistency Audit

### Rules
- Use only spacing tokens: 4, 8, 16, 24, 32, 48dp
- Screen padding: 16dp horizontal, consistent vertical
- Card internal padding: 16dp
- List item spacing: 8dp between items
- Section spacing: 24dp between logical sections

### Checklist
```
□ All padding uses Spacing tokens (no 5dp, 10dp, 12dp, 15dp)
□ Horizontal screen padding is 16dp consistently
□ Card padding matches across all cards on screen
□ List items have uniform spacing
□ No adjacent elements touching without spacing
□ Bottom navigation has proper top padding/divider
```

---

## Touch Targets (48dp Minimum)

### Rules
- ALL interactive elements: minimum 48x48dp touch area
- IconButtons: use Modifier.size(48.dp) minimum
- Text buttons: minimum height 48dp
- List items: minimum height 56dp (Material spec)
- Spacing between adjacent targets: minimum 8dp

```kotlin
// GOOD: Proper touch target
IconButton(
    onClick = { /* ... */ },
    modifier = Modifier.size(48.dp)  // Meets minimum
) {
    Icon(Icons.Default.Delete, contentDescription = "Delete item")
}

// BAD: Icon without proper touch area
Icon(
    Icons.Default.Delete,
    modifier = Modifier
        .size(24.dp)
        .clickable { /* ... */ }  // Touch target too small!
)

// Fix small elements with padding
Modifier
    .minimumInteractiveComponentSize()  // Material3 helper, ensures 48dp
```

### KhanaBook Specific
- Menu item grid: each cell minimum 48x48dp
- Bill item row: swipe/tap area full row height (56dp+)
- Quantity +/- buttons: 48dp circles with clear separation
- Print/Pay FABs: 56dp (Material default, already compliant)

---

## Contrast Ratios (WCAG AA)

### Requirements
```
Normal text (<18sp):     4.5:1 minimum contrast ratio
Large text (≥18sp bold): 3.0:1 minimum contrast ratio
UI components:           3.0:1 against background
```

### KhanaBook Verification
```
Token                  | On Background | Ratio  | Pass?
-----------------------|---------------|--------|------
Burgundy700 on White   | #FFFFFF       | 7.2:1  | ✅
Gold500 on White       | #FFFFFF       | 2.8:1  | ❌ (use Gold700)
Neutral500 on White    | #FFFFFF       | 4.6:1  | ✅
White on Burgundy700   | #8B1A32       | 7.2:1  | ✅
```

**Tools:** Use Android Studio Accessibility Scanner or online contrast checkers.

---

## Animation Curves

### Standard Durations
```kotlin
object KhanaBookMotion {
    const val SHORT = 150   // Simple state changes (color, opacity)
    const val MEDIUM = 300  // Screen transitions, card expand
    const val LONG = 500    // Complex choreography

    val EaseOut = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)   // Enter
    val EaseIn = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)    // Exit
    val Standard = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)  // Move
}
```

### Animation Rules
- Appear: fade in + scale up from 95% → 100% (EaseOut, MEDIUM)
- Disappear: fade out (EaseIn, SHORT)
- Move/resize: Standard curve, MEDIUM duration
- Loading shimmer: infinite, 1000ms cycle
- Never block user interaction with animations
- Respect `reduceMotion` accessibility setting

```kotlin
// Respect reduced motion
val reduceMotion = LocalReduceMotion.current
val animDuration = if (reduceMotion) 0 else KhanaBookMotion.MEDIUM
```

---

## Loading States

```kotlin
// Skeleton/Shimmer pattern for lists
@Composable
fun BillListSkeleton() {
    Column(modifier = Modifier.padding(MaterialTheme.spacing.md)) {
        repeat(5) {
            ShimmerCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(vertical = MaterialTheme.spacing.xs)
            )
        }
    }
}

// Button loading state
Button(
    onClick = { /* submit */ },
    enabled = !isLoading
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Spacer(Modifier.width(8.dp))
    }
    Text(if (isLoading) "Creating Bill..." else "Create Bill")
}
```

---

## Empty States

Every list/screen MUST handle empty state:

```kotlin
@Composable
fun EmptyBillsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_receipt_empty),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(MaterialTheme.spacing.md))
        Text(
            text = "No bills yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Create your first bill to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
```

---

## Error States

```kotlin
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(MaterialTheme.spacing.sm))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(MaterialTheme.spacing.md))
        OutlinedButton(onClick = onRetry) { Text("Retry") }
    }
}
```

---

## Anti-patterns
- ❌ Missing loading state (blank screen while fetching)
- ❌ Missing empty state (blank screen when no data)
- ❌ Error shown as Toast only (user misses it)
- ❌ Touch targets smaller than 48dp
- ❌ Arbitrary spacing values (10dp, 15dp, 22dp)
- ❌ Animations without reduced motion respect
- ❌ Low contrast text on colored backgrounds

## Verification Checklist
- [ ] Every screen has: loading, content, empty, error states
- [ ] All spacing uses design tokens only
- [ ] All touch targets ≥48dp (verified with Layout Inspector)
- [ ] Contrast ratios pass WCAG AA
- [ ] Animations respect reduced motion setting
- [ ] Tested with font size: largest (accessibility)
- [ ] Tested with TalkBack enabled
- [ ] No text truncation at largest font size
