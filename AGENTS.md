# KhanaBook Project Guide for AI Agents

## Tech Stack
- **Android**: Kotlin, Jetpack Compose, Room + SQLCipher, Dagger Hilt, Retrofit, WorkManager
- **Server**: Java 17, Spring Boot 3.5.12, Maven, PostgreSQL, Flyway
- **Web Admin**: TypeScript, Angular 18.2, standalone components

## Build Commands

### Android (workdir: `Android/`)
| Task | Command |
|---|---|
| Debug APK | `./gradlew.bat assembleDebug` |
| Release APK | `./gradlew.bat assembleRelease` |
| Release bundle | `./gradlew.bat bundleRelease` |
| Clean | `./gradlew.bat clean` |
| Lint | `./gradlew.bat lint` |

### Server (workdir: `server/`)
| Task | Command |
|---|---|
| Build | `mvn clean package` |
| Build (skip tests) | `mvn clean package -DskipTests` |
| Run (dev) | `mvn spring-boot:run -Dspring-boot.run.profiles=dev` |
| Run (prod) | `mvn spring-boot:run -Dspring-boot.run.profiles=prod` |

### Web Admin (workdir: `web-admin/`)
| Task | Command |
|---|---|
| Install deps | `npm install` |
| Dev server | `npm start` or `ng serve` |
| Build (dev) | `ng build --configuration development` |
| Build (prod) | `npm run build` |
| Type-check | `tsc --noEmit` |

## Test Commands

| Module | Command |
|---|---|
| Android unit tests | `./gradlew.bat testDebugUnitTest` |
| Android all unit tests | `./gradlew.bat test` |
| Android instrumented | `./gradlew.bat connectedAndroidTest` |
| Server tests | `mvn test` |
| Server specific test | `mvn test -Dtest=TestClassName` |
| Web admin tests | `npm test` or `ng test` |

## Conventions
- Kotlin code style: official
- Angular: standalone components, strict templates, strict TypeScript
- No ESLint, Prettier, checkstyle, or ktlint configs exist (do not add them unless asked)
- Avoid adding explanatory comments to code unless asked
- Android: min SDK 26, target SDK 35
- Server: Java 17, Spring Boot 3.5.x, JPA/Hibernate, JWT auth
- Do not commit secrets (.env, local.properties, keystores, google-services.json)

## Android UI Conventions

### Theming
- **Material 3** exclusively — no Material 2 components
- **Dark theme only** — single `darkColorScheme` in `ui/theme/`
- **Custom theme tokens** via `CompositionLocal`:
  - `KhanaBookTheme.spacing` — never hardcode dp; use `spacing.small`, `spacing.medium`, etc.
  - `KhanaBookTheme.iconSize` — use `iconSize.small`, `iconSize.medium`, etc.
  - `KhanaBookTheme.layout` — responsive helpers: `isCompact`, `isMedium`, `isExpanded`, `menuGridColumns`, `contentPadding`, `dialogWidthFraction`
- **Color palette** (`ui/theme/Color.kt`):
  - `PrimaryGold` (#C8960C) for accents, headers, active states
  - `DarkBrown1` (#1A0A0A) for screen backgrounds
  - `DarkBrown2` (#2D1010) for surfaces/cards
  - `RichEspresso` for gradient bottoms
  - `TextLight` for body text, `TextGold` for secondary text, `BorderGold` for borders
  - Semantic colors: `VegGreen`, `NonVegRed`, `SuccessGreen`, `DangerRed`, `ErrorPink`, `WarningYellow`
- **Typography**: Poppins font via Google Fonts, all 14 M3 type scale slots filled
- **Shapes**: Use `KhanaShapes` — `button`, `input`, `card`, `cardLarge`, `modal`, `pill`
- **Background gradient**: `Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso))`

### Screen Structure
- Screens receive navigation callbacks (`onBack`, `onNavigateToX`) — never touch NavController directly
- ViewModels obtained via `hiltViewModel()` at screen level
- Collect state at top: `val state by viewModel.someFlow.collectAsStateWithLifecycle()`
- Extract theme tokens early: `val spacing = KhanaBookTheme.spacing`
- Use `KhanaBookScreenScaffold` for simple screens; `Scaffold` with `containerColor = DarkBrown1` for complex ones

### State Management
- **Sealed class pattern** in ViewModels:
  ```kotlin
  sealed class SomeState {
      object Loading : SomeState()
      data class Success(val data: ...) : SomeState()
      data class Error(val message: String) : SomeState()
  }
  ```
- Expose via `MutableStateFlow` / `StateFlow` with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)`
- Collect with `collectAsStateWithLifecycle()` (not plain `collectAsState()`)
- One-shot events via `MutableSharedFlow`
- Local UI state: `remember { mutableStateOf(...) }`

### Design System Components
Use these custom components from `ui/designsystem/`:
- `KhanaBookScreenScaffold` — column scaffold with title + back arrow
- `KhanaBookCard` — press-animated card wrapper
- `KhanaBookInputField` — branded OutlinedTextField (DarkBrown2 container, Gold borders/cursor)
- `KhanaBookSwitch` — custom animated toggle (green when checked)
- `KhanaBookDialog` / `KhanaBookLargeDialog` / `KhanaBookSelectionDialog` — dialog variants
- `KhanaToast.show(message, kind)` — snackbar with Success/Error/Warning/Info kinds
- `KhanaBookLoadingOverlay` — Lottie-animated fullscreen loading with message
- Shimmer skeletons: `SkeletonListItem`, `SkeletonCard`, `SkeletonTableRow`, `SkeletonReportScreen`, `SkeletonMenuScreen`

### Loading / Error / Empty
- Loading: `KhanaBookLoadingOverlay` for full-screen, `ShimmerBox` skeletons for content-placeholder
- Errors: `KhanaToast.show(msg, kind = Error)` for toasts; `supportingText` on input fields for inline errors
- Empty: Icon + text message in center of content area

### Navigation
- Jetpack Navigation Compose, animated transitions (`fadeIn + slideInHorizontally`, `tween(300)`, `FastOutSlowInEasing`)
- Tabs via `NavigationBar` + `AnimatedContent` in MainScreen (Home/Reports/Orders/Profile)
- Double-back-to-exit on main tabs

### Lists & Grids
- Menu grid: `LazyVerticalGrid` with responsive `columns = GridCells.Fixed(menuGridColumns)`
- Cart/lists: `LazyColumn`
- Tablet split-view: check `isWideScreen` for side-panel layout

### Forms
- Local `remember { mutableStateOf("") }` for field values
- Input filtering with `.filter { ch.isDigit() }.take(10)` for phone
- Validation via `ValidationUtils.isValidPhone()` / `isValidEmail()`
- Submit disabled when `!isValid || isLoading`
- `KeyboardActions(onNext = ...)` for focus progression

## Web Admin UI Conventions

### Theming & CSS
- **Plain CSS** with CSS Custom Properties (no SCSS, Tailwind, or frameworks)
- **Warm earthy palette** in `src/styles.css`:
  - `--bg: #f6f1e8` page background
  - `--panel: #fffdf8` card/panel background
  - `--ink: #24170f` text color
  - `--muted: #7d6b5f` secondary text
  - `--line: #e9dcc9` borders
  - `--brand: #b56a2d` primary brand (warm brown/orange)
  - `--accent: #1d7b5f` accent teal/green
  - `--danger: #a6372f` error red
- **No CSS framework or UI library** — hand-rolled components only
- **No icon library** — use emoji/unicode icons (⚠️, 🙈, 👁️, 📋) or simple styled elements
- **BEM-like class naming**: `.page-shell`, `.page-hero`, `.hero-meta`, `.stat-card`, `.filter-group`
- **Responsive breakpoints** (in `styles.css`): 480px, 768px, 1024px, 1440px
- **CSS Grid** for layout: `grid-template-columns: repeat(auto-fit, minmax(min(100%, 180px), 1fr))`

### Component Structure
- **Standalone components** with inline template + inline styles (single `.ts` file per component)
- **Lazy-loaded** via `loadComponent` in routes
- **Page folder per route** under `pages/`
- No child/feature sub-components — each page is one flat component

### Page Layout Pattern
Every page follows this shell:
```html
<div class="page-shell">
  <section class="panel page-hero">
    <h2>Title</h2>
    <p class="muted">Description</p>
    <div class="hero-meta">
      <span class="chip">Tag</span>
    </div>
  </section>
  <section class="panel filter-panel">...</section>
  <div class="panel table-wrap">
    <table class="data-table">...</table>
    <div class="pagination-bar">...</div>
  </div>
</div>
```

### Key CSS Utility Classes
- `.page-shell` — grid container for page
- `.panel` — card with border-radius, border, shadow
- `.page-hero` — page header with gradient
- `.muted` — secondary text
- `.chip` / `.chip.success` / `.chip.warn` / `.chip.danger` — pill badges
- `.stats-grid` / `.stat-card` — dashboard stats
- `.table-wrap` / `.data-table` — scrollable tables
- `.filter-panel` / `.filter-grid` / `.filter-group` — filter forms
- `.primary-btn` / `.ghost-btn` — buttons
- `.field-control` / `.field-select` — form inputs
- `.pagination-bar` / `.pagination-controls` — pagination
- `.loading` — loading/empty state block
- `.toolbar` — top action bar (heading + button)
- `.alert` / `.alert.error` — error banners
- `.responsive-stack` — flex wrap row

### Layout Patterns
- **Responsive sidebar** (`sidebar-layout`):
  - `<1024px`: hamburger + off-screen sidebar with backdrop overlay
  - `>=1024px`: `grid-template-columns: 280px 1fr`, sidebar sticky
  - `>=1440px`: sidebar 300px, max shell 1680px
- **Data tables**: wrap in `.table-wrap` with `overflow-x: auto`
- **Modals**: fixed `inset: 0`, flex centered, max-width 460px
- **Buttons**: at `max-width: 480px` use `width: 100%`

### State Management
- Use **Angular Signals** (`signal()`, `computed()`, `toSignal()`, `input()`, `output()`)
- Services return `Observables`; convert to signals at component level via `toSignal()`
- Session state via `AuthService` signal

### Loading / Error / Empty
- **Loading**: `<div class="panel loading">Loading [entity]...</div>` with `*ngIf="data() as data; else loading"`
- **Empty**: `<div class="panel loading">No [items] match the current filters.</div>`
- **Error**: inline `error` variable shown in loading area; toast-style alerts
- **Button loading**: show "Loading..."/"Saving..." text + `[disabled]`
- `ApiStateComponent` (`<app-api-state>`) available for wrapping async content with loading/error/retry

### Forms
- **Reactive Forms** (`FormBuilder`) for complex data-entry forms
- **Template-driven** (`ngModel`) for simple search/filter forms
- Field styles: `class="field-control"` for text inputs, `class="field-select"` for selects
- Consistent field shape: `border-radius: 12px`, `min-height: 44px`, brand-colored focus ring

### Data Tables
- **Client-side pagination** — slice arrays in memory with page size selector
- **Client-side filtering** — `Array.filter()` in getter/computed properties
- Table structure: `<table class="data-table">` inside `.table-wrap`
- Pagination: `.pagination-bar` with page info + Previous/Next buttons
- Responsive: horizontal scroll on overflow

### Navigation & Routing
- All routes in `app.routes.ts`, lazy-loaded with `loadComponent`
- Guards: `authGuard` (JWT presence), `roleGuard` (KBOOK_ADMIN vs OWNER)
- Sidebar links computed from role
- Post-login: `AuthService.navigateByRole()` routes based on role
- Query params for filter state on list pages

### Shared Utilities
- `formatCurrency(value)` — formats INR via `Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' })`
- `formatDate(value)` — formats epoch ms in Indian locale

## Android Responsive UI Best Practices
- **Never hardcode dp/sp** — always use `KhanaBookTheme.spacing.*`, `KhanaBookTheme.iconSize.*`, and `MaterialTheme.typography.*`
- **Use `verticalScroll` + `imePadding()`** on form screens (`LoginScreen`, `SignUpScreen`, `SettingsScreen`) to prevent keyboard overlap
- **Use `Arrangement.Top`** (not `Arrangement.Center`) in scrollable form columns — centered content gets pushed off-screen when keyboard opens or in landscape
- **Scaffold `contentWindowInsets = WindowInsets(0)`** + manual `statusBarsPadding()`/`navigationBarsPadding()`/`imePadding()` — avoids double-inset bugs on Android 16+
- **`consumeWindowInsets(padding)`** after `Scaffold` content padding to prevent nested inset accumulation
- **Responsive breakpoints**: Use `KhanaBookTheme.layout` (`isCompact`/`isMedium`/`isExpanded`/`isWideListDetail`/`menuGridColumns`/`contentPadding`/`dialogWidthFraction`)
- **Tablet split-view**: Check `layout.isWideListDetail` (`>=840dp`) for side-panel layouts (see `NewBillScreen.MenuSelectionStep`)
- **Hardcoded `fontSize` in `sp`** causes accessibility issues — always prefer `MaterialTheme.typography.*` styles; reserve `fontSize` overrides only for critical spacing-constrained layouts (badges, chips)
- **`BringIntoViewRequester`** for fields that scroll into view when focused inside a scrollable container (see `NewBillScreen.PaymentStep`)
- **Phones bottom cart**: Use `bottomListPadding` (88.dp) or `navigationBarsPadding()` to prevent nav-bar occlusion
- **Landscape**: Avoid fixed-height containers (`height(56.dp)` is fine for buttons, but avoid `.fillMaxHeight()` without scroll context)

## Key Paths
- Android app: `Android/app/src/main/java/com/khanabook/pos/`
- Server source: `server/src/main/java/com/khanabook/saas/`
- Web admin source: `web-admin/src/`
- Android tests: `Android/app/src/test/` (unit), `Android/app/src/androidTest/` (instrumented)
- Server tests: `server/src/test/java/com/khanabook/saas/`

## gstack Routing

gstack is installed globally for OpenCode under `C:\Users\nandh\.config\opencode\skills`.
When the user asks for a gstack workflow, read and follow the matching skill from that
directory before acting. Use the `gstack-*` skill names if the host exposes prefixed
commands.

Route common requests this way:
- Product ideas, brainstorming, or "is this worth building" -> `gstack-office-hours`
- Backlog-ready specs, issues, or tickets -> `gstack-spec`
- Strategy and scope review -> `gstack-plan-ceo-review`
- Architecture review -> `gstack-plan-eng-review`
- Design plan review -> `gstack-plan-design-review`
- Full planning review -> `gstack-autoplan`
- Bugs, errors, or broken behavior -> `gstack-investigate`
- Careful/safety mode before risky changes -> `gstack-careful` or `gstack-guard`
- QA, browser checks, screenshots, or staging validation -> `gstack-qa` or `gstack-qa-only`
- Code review or diff review -> `gstack-review`
- Visual polish or UI audit -> `gstack-design-review`
- Developer experience review -> `gstack-devex-review`
- Security, OWASP, or threat-model review -> `gstack-cso`
- Ship, push, PR, or deploy flow -> `gstack-ship` or `gstack-land-and-deploy`
- Release documentation -> `gstack-document-release`
- Retrospective or learnings -> `gstack-retro` or `gstack-learn`

If no gstack workflow matches, follow the normal KhanaBook project guide above.
