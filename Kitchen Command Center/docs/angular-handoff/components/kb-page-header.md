# kb-page-header

## Purpose

Consistent page title block that appears at the top of each routed screen: title, optional description, breadcrumb, and right-aligned actions.

## Suggested selector

`kb-page-header`

## Suggested standalone component

`KbPageHeaderComponent`

## Inputs

- `title: string`
- `description?: string`
- `breadcrumb?: Array<{ label: string; route?: string }>`
- `variant?: 'default' | 'owner-welcome'` — `owner-welcome` swaps title to Instrument Serif (OWNER dashboard only).

## Outputs

None.

## Content projection

- `[slot="actions"]` — page-level primary/secondary buttons.
- `[slot="filters"]` — optional filter row rendered below the title block.

## Sizes

Min height ~`--kb-page-title-block` (80px). Padding uses `--kb-page-padding-y` and horizontal padding from parent.

## CSS classes

- `.kb-page-header`
- `.kb-page-header__title`
- `.kb-page-header__title--welcome` (Instrument Serif, only for OWNER dashboard)
- `.kb-page-header__description`
- `.kb-page-header__breadcrumb`
- `.kb-page-header__actions`

## CSS variables consumed

`--kb-fs-page-title`, `--kb-fs-welcome`, `--kb-font-sans`, `--kb-font-display`, `--kb-color-foreground`, `--kb-color-muted-foreground`.

## States

Static presentational component — no interactive states beyond hover/focus on breadcrumb links.

## Mobile behavior

- Title wraps to two lines instead of ellipsis.
- Actions stack below the title.
- Breadcrumb hides intermediate segments if space is tight.

## Keyboard behavior

Breadcrumb links reachable via Tab. Action slot follows its own tab order.

## Accessibility

- Title renders as `<h1>` (exactly one per page).
- Description is a `<p>`; associate via `aria-describedby` if wired to a form region.
- Breadcrumb uses `<nav aria-label="Breadcrumb">` with `<ol>`.

## Related prototype

`/proto/owner/dashboard` (welcome variant), all other prototype pages (default variant).
