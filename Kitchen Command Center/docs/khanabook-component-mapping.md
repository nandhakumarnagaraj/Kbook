# KhanaBook Component Mapping — Prototype → Angular

Suggested Angular selectors and structure for the shared components used
across the prototype. Angular team may reorganise, but the input/output
contracts and CSS-variable names below should be preserved so that a future
tokens refresh flows through without touching component code.

## Legend

- **Inputs** — @Input contract.
- **Outputs** — @Output events (EventEmitter).
- **CSS vars** — root `--kb-*` custom properties the component reads.
- **States** — every visual state that must be reachable.
- **Mobile** — behaviour under `md` (768 px).

---

## 1. `KbAppShellComponent`

Wraps the entire authenticated experience.

| Field    | Value                                                                                                                                            |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| Inputs   | `role: 'OWNER' \| 'SHOP_ADMIN' \| 'KBOOK_ADMIN'`, `restaurant: { name, branch }`, `user: { name, role, initials }`, `notificationCount?: number` |
| Outputs  | `(searchOpen)`, `(dateRangeChange)`, `(logoutClick)`                                                                                             |
| CSS vars | `--kb-shell-bg`, `--kb-topbar-height`, `--kb-sidebar-width`                                                                                      |
| States   | default, mobile-drawer-open                                                                                                                      |
| Mobile   | Sidebar becomes off-canvas drawer opened by a top-left menu button. Search collapses; only menu + brand + avatar remain in the topbar.           |

## 2. `KbSidebarComponent`

| Field    | Value                                                                              |
| -------- | ---------------------------------------------------------------------------------- |
| Inputs   | `role`, `collapsed?: boolean`, `currentUrl: string`                                |
| Outputs  | `(collapseToggle)`, `(navigate: string)`                                           |
| CSS vars | `--kb-sidebar-bg`, `--kb-sidebar-fg`, `--kb-sidebar-active`, `--kb-sidebar-border` |
| States   | default, item-hover, item-active (saffron 3-px indicator), collapsed (rail)        |
| Mobile   | Hidden below `lg`; rendered inside a CDK overlay drawer with focus trap.           |

## 3. `KbTopbarComponent`

| Field    | Value                                                      |
| -------- | ---------------------------------------------------------- |
| Inputs   | `restaurant`, `user`, `notificationCount?`                 |
| Outputs  | `(searchOpen)`, `(dateRangeChange)`, `(bellClick)`         |
| CSS vars | `--kb-topbar-bg`, `--kb-topbar-border`                     |
| States   | default, with-unread-badge, compact (mobile)               |
| Mobile   | Search + date collapse; only menu + brand + avatar remain. |

## 4. `KbPageHeaderComponent`

| Field    | Value                                                                                                          |
| -------- | -------------------------------------------------------------------------------------------------------------- |
| Inputs   | `title: string \| TemplateRef`, `subtitle?: string`, `actions?: TemplateRef`, `breadcrumbs?: {label, link?}[]` |
| Outputs  | –                                                                                                              |
| CSS vars | – (composes tokens)                                                                                            |
| States   | default, with-breadcrumbs, loading (skeleton subtitle)                                                         |
| Mobile   | Actions wrap below title using `grid-cols-[minmax(0,1fr)_auto]`. Title uses `truncate` inside `min-w-0`.       |

## 5. `KbKpiCardComponent`

| Field    | Value                                                                                      |
| -------- | ------------------------------------------------------------------------------------------ |
| Inputs   | `label`, `value`, `delta: number`, `spark: number[]`, `hero?: boolean`, `danger?: boolean` |
| Outputs  | –                                                                                          |
| CSS vars | `--kb-kpi-bg`, `--kb-kpi-fg`, `--kb-kpi-hero-gradient`                                     |
| States   | default (positive), negative-delta, hero (OWNER only), loading skeleton                    |
| Mobile   | Full-width on `<sm`, 2-col at `sm`, 4-col at `xl`. Value uses `tabular-nums`.              |

## 6. `KbStatusBadgeComponent`

| Field    | Value                                                                                                     |
| -------- | --------------------------------------------------------------------------------------------------------- |
| Inputs   | `tone: 'success' \| 'warning' \| 'danger' \| 'info' \| 'muted'`, `icon?: IconName`, `size?: 'sm' \| 'md'` |
| Outputs  | –                                                                                                         |
| CSS vars | `--kb-badge-*-bg`, `--kb-badge-*-fg` per tone                                                             |
| States   | all tones with icon + text (never colour alone)                                                           |
| Mobile   | Wraps in mobile card headers.                                                                             |

Presets: `OrderStatusBadge`, `DeviceStatusBadge`, `MarketplaceStatusBadge` —
mirror the switch tables in `src/proto/primitives.tsx`.

## 7. `KbDataTableComponent` + `KbMobileDataCardComponent`

| Field    | Value                                                                                                                                                                                                                                       |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Inputs   | `columns: KbColumnDef<T>[]`, `rows: T[]`, `empty: TemplateRef`, `loading: boolean`, `errorState?: TemplateRef`                                                                                                                              |
| Outputs  | `(rowClick: T)`, `(sortChange)`, `(pageChange)`                                                                                                                                                                                             |
| CSS vars | `--kb-table-header-bg`, `--kb-table-border`, `--kb-table-row-hover`                                                                                                                                                                         |
| States   | ready, loading (row skeletons), empty, error, row-hover                                                                                                                                                                                     |
| Mobile   | Below `md`, table markup is replaced by stacked `KbMobileDataCardComponent` items using the column templates. A "refunded" row uses a `border-l-2 border-l-danger` accent that must translate to a left-edge accent on the mobile card too. |

## 8. `KbFilterToolbarComponent`

| Field   | Value                                                          |
| ------- | -------------------------------------------------------------- |
| Inputs  | `searchPlaceholder`, `filters: KbFilterDef[]`, `activeFilters` |
| Outputs | `(searchChange)`, `(filterChange)`                             |
| States  | default, with active filter chips, loading (disabled)          |
| Mobile  | Wraps; search takes the full row on `<sm`.                     |

## 9. `KbConfirmationDialogComponent`

Backed by `CdkDialog`. Sensitive-action layout described in
`khanabook-ui-handoff.md §5`.

| Field    | Value                                                                                                                              |
| -------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| Inputs   | `title`, `body: TemplateRef`, `destructive?: boolean`, `confirmLabel`, `cancelLabel`, `width: 'sm' \| 'md' \| 'lg'`                |
| Outputs  | `(confirm)`, `(cancel)`                                                                                                            |
| CSS vars | `--kb-dialog-bg`, `--kb-dialog-danger-strip`                                                                                       |
| States   | idle, confirming (spinner in confirm button), success (transitions to success body), error (inline banner), disabled during submit |
| Mobile   | 16-px inset; full-width. `Escape` closes; focus returns to invoker.                                                                |

## 10. `KbFormDrawerComponent`

Backed by `CdkOverlay`. Right-hand slide-in used for entity add/edit.

| Field    | Value                                                                      |
| -------- | -------------------------------------------------------------------------- |
| Inputs   | `title`, `subtitle?`, `width: 'sm' \| 'md' \| 'lg'`, `footer: TemplateRef` |
| Outputs  | `(close)`, `(submit)`                                                      |
| CSS vars | `--kb-drawer-bg`, `--kb-drawer-shadow`                                     |
| States   | closed, open, loading content                                              |
| Mobile   | Full-width; slides from the right at `≥sm`. `Escape` closes.               |

## 11. `KbSecretInputComponent`

| Field    | Value                                                                                                         |
| -------- | ------------------------------------------------------------------------------------------------------------- |
| Inputs   | `stored: boolean`, `label`, `helpText?`                                                                       |
| Outputs  | `(reveal)`, `(replaceStart)`, `(cancelReplace)`, `(save: string)`, `(validationError)`                        |
| CSS vars | –                                                                                                             |
| States   | stored (masked + Replace), replacing (empty input + reveal + Cancel), revealed (Eye toggle), validation error |
| Mobile   | Same across breakpoints.                                                                                      |

**Contract:** The stored/masked view is a display element, never an
editable input; entering a new value requires the Replace transition.

## 12. `KbFileUploadComponent`

| Field    | Value                                                                                                                                    |
| -------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| Inputs   | `accept: string[]`, `maxSizeMb: number`, `processingHint?: string`                                                                       |
| Outputs  | `(files)`, `(validationError)`, `(uploadProgress: number)`, `(processing)`, `(success)`, `(partial)`, `(failed)`                         |
| CSS vars | –                                                                                                                                        |
| States   | idle (dashed border), dragover, validation-error, uploading (determinate progress), processing (indeterminate), success, partial, failed |
| Mobile   | Dashed area shrinks; buttons stack under `sm`.                                                                                           |

## 13. `KbEmptyStateComponent`

| Field   | Value                                                            |
| ------- | ---------------------------------------------------------------- |
| Inputs  | `icon: IconName`, `title`, `description`, `action?: TemplateRef` |
| Outputs | –                                                                |
| States  | default                                                          |
| Mobile  | Centre-aligned; single column.                                   |

## 14. `KbErrorStateComponent`

| Field   | Value                                          |
| ------- | ---------------------------------------------- |
| Inputs  | `title?`, `description?`, `retry?: () => void` |
| Outputs | `(retry)`                                      |
| States  | default                                        |
| Mobile  | Same.                                          |

## 15. `KbSkeletonComponent`

| Field  | Value                                             |
| ------ | ------------------------------------------------- |
| Inputs | `class` (`h-*`, `w-*` Tailwind or explicit sizes) |
| States | default (animate-pulse)                           |
| Mobile | Same.                                             |

## 16. `KbToastComponent` + `KbToastService`

| Field   | Value                                                                          |
| ------- | ------------------------------------------------------------------------------ |
| Inputs  | `tone`, `title`, `description?`, `action?: {label, handler}`                   |
| Outputs | `(dismiss)`                                                                    |
| States  | info, success, warning, danger                                                 |
| Mobile  | Bottom-centre; `≥md` top-right. Auto-dismiss 5 s; destructive: manual dismiss. |

## 17. `KbButtonDirective` (`[kbButton]`)

| Field   | Value                                                                                              |
| ------- | -------------------------------------------------------------------------------------------------- |
| Inputs  | `[kbButton]='primary' \| 'secondary' \| 'ghost' \| 'danger' \| 'outline'`, `[kbSize]='sm' \| 'md'` |
| Outputs | –                                                                                                  |
| States  | default, hover, focus (4-px saffron ring), active (0.98 scale), disabled                           |
| Mobile  | Primary CTAs `min-h-11 min-w-11` to clear 44-px targets.                                           |

## 18. `KbCopyButtonComponent`

| Field   | Value                                                                       |
| ------- | --------------------------------------------------------------------------- |
| Inputs  | `value: string`, `label?`                                                   |
| Outputs | `(copied)`                                                                  |
| States  | idle (Copy icon), copied (Check + "Copied" via `role="status"` live region) |
| Mobile  | Same.                                                                       |

## 19. `KbStateSwitcherComponent` (prototype only)

Fixed floating pill used inside `/proto` to reveal state variants. **Not
required in production** — the Angular build owns real state via services
and reactive streams.

---

## Angular-migration guardrails

- **CSS variables ship first.** Move `src/styles.css` `:root` variables into
  `src/styles/tokens.scss` with the same names. Every component SCSS should
  reference `var(--kb-*)` — never a raw hex.
- **shadcn/Radix ≈ Angular CDK.** Prototype uses hand-rolled `Drawer` +
  `Dialog` for design fidelity; Angular ships them via `@angular/cdk/dialog`
  and `@angular/cdk/overlay` with focus trap and Escape handling built in.
- **Do not port `.tsx` files.** They are reference only.
- **Preserve services.** No component in this document requires a new API
  surface — every screen consumes existing `AuthService`,
  `BusinessApiService`, `AdminApiService` payloads.
