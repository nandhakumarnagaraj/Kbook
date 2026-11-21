# Frontend Design — KhanaBook Web Admin

## Framework Conventions

### Angular 18 (Standalone Components)

- **All components are `standalone: true`** — never use NgModules
- Use `inject()` over constructor injection
- Use `takeUntilDestroyed()` from `@angular/core/rxjs-interop` for subscription cleanup
- Use signals (`signal()`, `computed()`) for local component state
- Use `@Input()`/`@Output()` sparingly — prefer signals with `input()`, `output()`

### Project Structure

```
web-admin/src/
  app/
    pages/{page-name}/page.component.ts  — one file per page
    layout/                              — sidebar, header components
    core/
      auth/                              — AuthService, JWT interceptor
      models/                            — api.models.ts (all interfaces)
      services/                          — API services
      components/                        — shared components (ApiState)
    shared/                              — formatters, pipes, utilities
```

### Routing

- Admin routes: `/admin/dashboard`, `/admin/businesses`, `/admin/sub-merchants`, etc.
- Business routes: `/business/dashboard`, `/business/orders`, `/business/marketplace-setup`, etc.
- Configured in `app.routes.ts`

---

## Angular Material Usage

### Import Pattern

Import Material modules directly in each component:

```typescript
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule } from '@angular/material/dialog';
// ... etc
```

### Material Components Used

| Component | Usage |
|-----------|-------|
| `MatSidenavModule` | Sidebar layout |
| `MatToolbarModule` | Header toolbar |
| `MatListModule` | Navigation links |
| `MatIconModule` | All icons (Material Icons font) |
| `MatButtonModule` | Buttons (`mat-flat-button`, `mat-icon-button`, `mat-button`) |
| `MatCardModule` | Login, stat cards, panels |
| `MatMenuModule` | Profile dropdown, action menus |
| `MatDialogModule` | Modals (sub-merchant details, business actions) |
| `MatSnackBarModule` | Toast notifications |
| `MatTooltipModule` | Tooltips on icon buttons |
| `MatDividerModule` | Dividers between sections |
| `MatProgressSpinnerModule` | Loading states |
| `MatChipsModule` | Status badges |
| `MatTableModule` | Data tables |
| `MatSlideToggleModule` | Toggle switches (marketplace setup) |
| `MatCheckboxModule` | Checkboxes |
| `MatFormFieldModule` | Form fields |
| `MatInputModule` | Text inputs |
| `MatSelectModule` | Dropdown selects |
| `MatDatepickerModule` | Date pickers |
| `MatPaginatorModule` | Table pagination |
| `MatSortModule` | Table sorting |

### Material Theming

Angular Material theme is defined in `styles.scss`:

```scss
$khanabook-primary: mat.m2-define-palette(mat.$m2-orange-palette, 800);
$khanabook-accent: mat.m2-define-palette(mat.$m2-blue-grey-palette, A200, A100, A400);
$khanabook-warn: mat.m2-define-palette(mat.$m2-red-palette);
```

---

## CSS Variable System

### Always use CSS custom properties over hardcoded values

```css
/* ✅ CORRECT */
background: var(--panel);
color: var(--ink);
border: 1px solid var(--line);
border-radius: var(--radius-lg);
box-shadow: var(--shadow-md);

/* ❌ WRONG */
background: #ffffff;
color: #17130F;
border: 1px solid #e2e8f0;
border-radius: 14px;
```

### Available Tokens (from `styles.scss`)

```css
--bg, --bg-elevated, --panel, --panel-hover, --surface, --surface-hover
--ink, --ink-secondary, --muted
--line, --line-strong
--brand, --brand-light, --brand-dark, --brand-soft, --brand-glow
--accent, --accent-light, --accent-soft
--danger, --danger-soft
--warn, --warn-soft
--info, --info-soft
--purple, --purple-soft
--shadow-sm, --shadow-md, --shadow-lg, --shadow-xl
--radius-sm, --radius-md, --radius-lg, --radius-xl
```

---

## Responsive Design Rules

### Breakpoints

| Breakpoint | Width | Behavior |
|------------|-------|----------|
| Mobile | < 480px | Minimal padding, full-width cards, stacked layout |
| Tablet | 480–768px | Compact layout, 2-column grids |
| Small desktop | 768–960px | Sidebar collapses to overlay |
| Desktop | 960–1200px | Full layout, 3-column grids |
| Wide | > 1200px | Max-width 1480px centered content |

### Key Patterns

```css
/* Page shell — centers content with max-width */
.page-shell {
  width: 100%;
  max-width: 1480px;
  margin: 0 auto;
  padding: 1.5rem;
  display: grid;
  gap: 1.5rem;
}

/* Responsive grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 1.5rem;
}
```

### Dark Mode

- Toggle via `ThemeService` which adds `.dark-theme` class to `<body>`
- Angular Material dark theme is activated via `.dark-theme { @include mat.all-component-colors($dark-theme); }`
- Custom CSS variables are overridden in `.dark-theme { ... }` block
- Glass panels use darker backgrounds in dark mode

### Important Notes

- Angular Emulated ViewEncapsulation adds `_ngcontent-xxx` selectors
- Global overrides may need `!important` when competing with component-level `.class` rules
- Always use `100dvh` instead of `100vh` for mobile viewport
- Google Sign-In button uses `max-width: 100%` for responsive

---

## State Management Patterns

### Component State (Signals)

```typescript
import { signal, computed } from '@angular/core';

@Component({...})
export class MyComponent {
  isLoading = signal(false);
  error = signal<string | null>(null);
  data = signal<MyType | null>(null);

  readonly derivedValue = computed(() => {
    const d = this.data();
    return d ? transform(d) : null;
  });
}
```

### API Calls with takeUntilDestroyed

```typescript
import { inject, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';

export class MyComponent {
  private readonly destroyRef = inject(DestroyRef);

  loadData(): void {
    this.isLoading.set(true);
    this.apiService.getData().pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.isLoading.set(false))
    ).subscribe({
      next: (res) => this.data.set(res),
      error: (err) => this.error.set(err.message)
    });
  }
}
```

### Error Handling

- Use `catchError(() => of(null))` for non-critical background requests
- Show errors via `MatSnackBar` or inline error banners
- Use `app-api-state` component wrapper for consistent loading/error states

---

## Component Patterns

### Page Shell Pattern

Every page should follow this structure:

```html
<div class="page-shell">
  <div class="page-hero" *ngIf="showHero">
    <h1>Page Title</h1>
    <p>Page subtitle or description</p>
  </div>

  <app-api-state [loading]="isLoading()" [error]="error()" (retry)="loadData()">
    <!-- page content -->
  </app-api-state>
</div>
```

### Status Badge Pattern

Use `MatChipsModule` or inline styled spans:

```html
<span class="status-badge" [class.active]="isActive">{{ status }}</span>
```

```css
.status-badge {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 700;
  background: var(--surface);
  color: var(--ink-secondary);
}
.status-badge.active { background: var(--accent-soft); color: var(--accent); }
```

### Table Pattern

```html
<table mat-table [dataSource]="dataSource()" class="data-table">
  <ng-container matColumnDef="name">
    <th mat-header-cell *matHeaderCellDef>Name</th>
    <td mat-cell *matCellDef="let item">{{ item.name }}</td>
  </ng-container>
  ...
</table>
```

---

## Performance Guidelines

- Use `trackBy` with `*ngFor` for list rendering
- Use `ChangeDetectionStrategy.OnPush` where needed (default is fine for most cases)
- Lazy-load page components via Angular routing
- Avoid heavy computations in templates — use computed signals
- ⚠️ The codebase currently uses `*ngIf` / `*ngFor` consistently — maintain this pattern when editing existing components
