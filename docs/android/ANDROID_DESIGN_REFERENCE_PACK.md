# KhanaBook Android Design Reference Pack

**Version:** 1.0
**Reviewed:** 2026-09-24

## Purpose

Use official Android guidance for layout behavior, navigation, accessibility,
window adaptation, and platform interactions. Keep KhanaBook's dark-brown/gold
identity, existing Compose components, and documented UI rules. Android examples
are references for behavior and composition, not instructions to copy Android
colors or replace KhanaBook's design system.

This pack supplements `docs/meta/ANDROID_UI_RULES.md` and
`docs/design/KHANABOOK_RESPONSIVE_DESIGN_SPEC.md`; it does not replace them.

## Core references

| Android guidance | KhanaBook use | Practical application |
|---|---|---|
| [Adapt layouts](https://developer.android.com/design/ui/mobile/guides/layout-and-content/adapt-layout) | All screens | Respond to the available window width first, then height. Reflow, reveal, or change presentation where useful instead of stretching a phone layout. |
| [Canonical layouts](https://developer.android.com/develop/ui/views/layout/canonical-layouts) | Home, Menu, Orders, Reports, New Bill | Treat feed, list-detail, and supporting-pane as proven options. Choose by content relationship; do not force a pattern onto every screen. |
| [Compose list-detail](https://developer.android.com/develop/adaptive-apps/guides/list-detail) | Active Orders; potentially Menu management | On expanded windows, a selected record can appear beside its list. On compact windows, users navigate between list and detail. Use when the detail is substantial enough to benefit from a persistent pane. |
| [Compose supporting pane](https://developer.android.com/develop/adaptive-apps/guides/build-a-supporting-pane-layout) | New Bill; potentially Reports | Keep related context, such as the current bill summary, alongside the primary workflow on a wide window. On compact windows, present panes sequentially. |
| [Adaptive navigation](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation) | App-wide top-level navigation | Consider window-adaptive navigation presentation. The existing responsive spec currently defers NavigationRail; revisit that product decision separately rather than silently changing it during screen work. |
| [Navigation bar](https://developer.android.com/develop/ui/compose/components/navigation-bar) and [Navigation rail](https://developer.android.com/develop/ui/compose/components/navigation-rail) | Main navigation | Bars fit three to five peer destinations in compact windows; rails fit three to seven top-level destinations on larger layouts. Use actual window size, not device model, to choose. |
| [Compose lazy lists and grids](https://developer.android.com/develop/ui/compose/lists) | Menu, Orders, Reports | Use `LazyColumn`/`LazyRow` for scrollable collections and lazy grids for larger collections. `GridCells.Adaptive` can fit as many columns as a minimum cell width allows. Preserve list presentation when scanning or comparing rows is better. |
| [Compose tabs](https://developer.android.com/develop/ui/compose/components/tabs) and [layout/navigation patterns](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns) | Menu categories, order statuses, report periods | Tabs are secondary navigation for sibling content. Long category sets need a clear scroll/selection affordance; keep category management actions distinct from category selection. |
| [Settings](https://developer.android.com/design/ui/mobile/guides/patterns/settings) | Settings | Group related options, use concise labels, add supporting text only when it clarifies a setting, and move large groups into detail screens. Settings are usually secondary unless central to the product's main workflow. |
| [Android system bars](https://developer.android.com/design/ui/mobile/guides/foundations/system-bars), [edge-to-edge design](https://developer.android.com/design/ui/mobile/guides/layout-and-content/edge-to-edge), and [Compose insets](https://developer.android.com/develop/ui/compose/system/insets) | All screens, dialogs, forms, bottom actions | Account for status/navigation bars, display cutouts, gesture areas, and the IME. Keep controls reachable and avoid applying an inset twice when a parent or Material component already consumes it. |
| [Material components overview](https://developer.android.com/design/ui/mobile/guides/components/material-overview) and [Compose bottom sheets](https://developer.android.com/develop/ui/compose/quick-guides/content/create-bottom-sheet) | Cards, dialogs, sheets, contextual actions | Choose containment based on the task. A sheet can hold secondary or longer contextual content; a focused form may remain a dialog if it fits the existing app pattern and handles the keyboard. Avoid stacking modal layers for a small subtask. |

## Screen mapping

| KhanaBook surface | Start with | Design decision to evaluate |
|---|---|---|
| Home | Adaptive layout; feed/list/grid | Reflow summary content to available width while preserving the established home action priorities. |
| New Bill | Lazy collections; supporting pane; insets | On wide windows, evaluate keeping the cart summary visible beside menu browsing. Keep the phone workflow sequential and preserve the sticky primary action. |
| Menu / Manual Entry | Lazy lists; secondary category navigation; responsive tokens | Keep categories readable and navigable, separate category creation from selection, and choose a list or grid based on item-scanning needs. |
| Active Orders | Tabs; list-detail; lazy list | A wide-window detail pane is useful when an order selection reveals enough information to justify simultaneous list and detail. |
| Reports | Adaptive feed/list; supporting pane where useful | Reflow report summaries and filters without assuming every report belongs in a grid or second pane. |
| Settings | Settings guidance; list-detail on wide windows | Keep groups concise; consider list-detail only when the settings catalog and selected section both benefit from the space. |
| Add/Edit Item | Material dialog or sheet; IME/insets | Preserve photo, validation, and save behavior. Keep the content scrollable and actions reachable with the keyboard open. |
| Add Variant | Inline form, dialog, or sheet as appropriate | Android does not prescribe inline variants. Prefer the existing single-flow inline editor here to avoid nested modal focus changes. |
| Payment / Printer | Settings and Material patterns; system bars | Use established KhanaBook components and check keyboard/system-bar behavior on the actual window size. |

## Project-specific implementation rules

1. Read `docs/meta/ANDROID_UI_RULES.md` and the relevant feature implementation
   before changing a screen.
2. Use `KhanaBookTheme.layout`, spacing, typography, colors, and existing
   scaffolds/components. Do not copy Android reference colors into KhanaBook.
3. Classify the available window using the existing responsive system; do not
   branch on a device brand or model.
4. Add a second pane or navigation rail only when the content relationship and
   available space support it, and account for the existing deferred decisions
   in `KHANABOOK_RESPONSIVE_DESIGN_SPEC.md`.
5. Check keyboard, system bars, touch targets, and scroll reachability for
   interactive screens. Avoid duplicating insets handled by Scaffold or Material.
6. Preserve current navigation, business logic, storage, and server behavior
   unless the task explicitly requires a change.

## Source notes

The links above point to first-party Android Developers documentation. The
Android guidance establishes adaptable patterns and platform behavior; the
screen mapping and adoption notes are recommendations for KhanaBook, based on
its existing responsive and UI rules. Patterns such as list-detail, supporting
panes, and navigation rails are conditional tools, not mandatory migrations.
