# UI Engineering — Jetpack Compose & Material 3

## When to Trigger

- Creating or modifying any Compose `@Composable` function
- Designing new screens, bottom sheets, dialogs, or navigation flows
- Implementing Material 3 theming, color schemes, or typography
- Handling state in UI (state hoisting, ViewModel integration)
- Optimizing recomposition performance in lists or animations
- Building KhanaBook POS screens (menu grid, order summary, billing)

## Stack Context

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| State | ViewModel + StateFlow/SharedFlow |
| Navigation | Compose Navigation (type-safe) |
| Async | Coroutines + Flow |

---

## Step-by-Step Workflow

### 1. Screen Architecture

Every screen follows this layered pattern:

```kotlin
// 1. Route-level composable (navigation graph entry)
@Composable
fun OrderScreenRoute(
    viewModel: OrderViewModel = hiltViewModel(),
    onNavigateToPayment: (orderId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    OrderScreen(
        uiState = uiState,
        onAddItem = viewModel::addItem,
        onRemoveItem = viewModel::removeItem,
        onCheckout = { onNavigateToPayment(uiState.orderId) }
    )
}

// 2. Stateless screen composable (testable, previewable)
@Composable
fun OrderScreen(
    uiState: OrderUiState,
    onAddItem: (MenuItem) -> Unit,
    onRemoveItem: (String) -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = { OrderTopBar(tableNumber = uiState.tableNumber) },
        bottomBar = { OrderBottomBar(total = uiState.total, onCheckout = onCheckout) }
    ) { padding ->
        OrderContent(
            modifier = Modifier.padding(padding),
            items = uiState.items,
            onAddItem = onAddItem,
            onRemoveItem = onRemoveItem
        )
    }
}
```

### 2. State Hoisting Pattern

```kotlin
// UiState sealed hierarchy
sealed interface OrderUiState {
    data object Loading : OrderUiState
    data class Success(
        val orderId: String,
        val tableNumber: Int,
        val items: List<OrderItem>,
        val total: BigDecimal
    ) : OrderUiState
    data class Error(val message: String) : OrderUiState
}

// ViewModel exposes a single StateFlow
@HiltViewModel
class OrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<OrderUiState>(OrderUiState.Loading)
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            orderRepository.getActiveOrder()
                .catch { _uiState.value = OrderUiState.Error(it.message ?: "Unknown error") }
                .collect { order ->
                    _uiState.value = OrderUiState.Success(
                        orderId = order.id,
                        tableNumber = order.tableNumber,
                        items = order.items,
                        total = order.total
                    )
                }
        }
    }
}
```

### 3. Material 3 Theming

```kotlin
// Theme.kt — KhanaBook theme
@Composable
fun KhanaBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled for brand consistency
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KhanaBookTypography,
        shapes = KhanaBookShapes,
        content = content
    )
}

// Use semantic colors, never hardcoded
Text(
    text = "₹${item.price}",
    color = MaterialTheme.colorScheme.primary,
    style = MaterialTheme.typography.titleMedium
)
```

### 4. Recomposition Optimization

```kotlin
// Use stable types for list items
@Immutable
data class MenuItemUi(
    val id: String,
    val name: String,
    val price: BigDecimal,
    val imageUrl: String?,
    val isVeg: Boolean
)

// Use key() in LazyColumn to help diffing
LazyColumn {
    items(
        items = menuItems,
        key = { it.id } // Stable key prevents unnecessary recomposition
    ) { item ->
        MenuItemCard(
            item = item,
            onAdd = { onAddItem(item) }
        )
    }
}

// Derive state to minimize recomposition scope
val showEmptyState by remember {
    derivedStateOf { uiState.items.isEmpty() }
}
```

### 5. Accessibility

```kotlin
// Always provide content descriptions for icons
Icon(
    imageVector = Icons.Default.Add,
    contentDescription = stringResource(R.string.cd_add_item, item.name)
)

// Use semantics for complex components
Box(
    modifier = Modifier.semantics(mergeDescendants = true) {
        contentDescription = "Order item: ${item.name}, quantity ${item.quantity}, price ₹${item.totalPrice}"
    }
)

// Minimum touch target 48dp
IconButton(
    onClick = onRemove,
    modifier = Modifier.size(48.dp)
) { /* ... */ }
```

---

## Anti-Patterns to Avoid

| Anti-Pattern | Correct Approach |
|-------------|-----------------|
| Passing ViewModel to child composables | Pass only data + lambdas (state hoisting) |
| Using `mutableStateOf` in ViewModel | Use `MutableStateFlow` + `collectAsStateWithLifecycle()` |
| Hardcoding colors (`Color(0xFF...)`) | Use `MaterialTheme.colorScheme.*` |
| Creating objects inside composition | Use `remember { }` or hoist creation |
| Nested scrollable containers without `nestedScroll` | Use `Modifier.nestedScroll()` or flatten |
| Using `LaunchedEffect(Unit)` for ViewModel init | Do initialization in ViewModel `init {}` |
| Passing unstable lambdas causing recomposition | Use `remember { }` or method references |
| Reading Flow with `.collectAsState()` | Use `.collectAsStateWithLifecycle()` for lifecycle safety |

---

## Verification Checklist

- [ ] Screen composable is stateless (no ViewModel dependency)
- [ ] Route composable handles ViewModel + navigation
- [ ] UiState is a sealed interface with Loading/Success/Error
- [ ] `collectAsStateWithLifecycle()` used (not `collectAsState()`)
- [ ] All colors from `MaterialTheme.colorScheme`
- [ ] All text styles from `MaterialTheme.typography`
- [ ] LazyColumn items use stable `key`
- [ ] Touch targets ≥ 48dp
- [ ] Content descriptions on all icons/images
- [ ] Preview functions exist for Success/Loading/Error states
- [ ] No unstable lambdas in hot recomposition paths
- [ ] Compose BOM version aligned across all Compose dependencies

---

## KhanaBook-Specific Patterns

### POS Menu Grid
```kotlin
@Composable
fun MenuGrid(
    categories: List<CategoryWithItems>,
    onItemTap: (MenuItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                CategoryHeader(name = category.name)
            }
            items(
                items = category.items,
                key = { it.id }
            ) { menuItem ->
                MenuItemCard(item = menuItem, onTap = { onItemTap(menuItem) })
            }
        }
    }
}
```

### Order Summary Bottom Sheet
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderSummarySheet(
    sheetState: SheetState,
    order: OrderUiState.Success,
    onDismiss: () -> Unit,
    onPay: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Order Summary", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            order.items.forEach { item ->
                OrderItemRow(item = item)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            TotalRow(total = order.total)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onPay,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pay ₹${order.total}")
            }
        }
    }
}
```
