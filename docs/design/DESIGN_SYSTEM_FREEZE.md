# Design System Freeze — KhanaBook

**Date frozen:** 2026-08-19
**Status:** LOCKED — no palette/theme changes without written UX justification

---

## Why This Freeze Exists

The KhanaBook codebase has had **40 commits changing the color palette/theme** across 5 months. The design system was rebuilt multiple times:

1. Initial dark theme (Mar 2026)
2. Teal brand (#0891B2) + pure neutrals (May 2026)
3. Saffron palette (#C85A00) + warm neutrals (May 2026)
4. Grounded Earth & Saffron dark mode (Jun 2026)
5. PrimaryGold (#C8960C) + DarkBrown (current — Aug 2026)

Each change touched 20–40 files and generated cascading bugs. **This stops now.**

---

## Current Locked Palette (Android)

| Token | Hex | Usage |
|---|---|---|
| `PrimaryGold` | #C8960C | Accents, headers, active states, CTAs |
| `DarkBrown1` | #1A0A0A | Screen backgrounds |
| `DarkBrown2` | #2D1010 | Surfaces, cards, input containers |
| `RichEspresso` | — | Gradient bottom |
| `TextLight` | — | Body text |
| `TextGold` | — | Secondary text |
| `BorderGold` | — | Input borders, dividers |
| `VegGreen` | — | Vegetarian indicators |
| `NonVegRed` | — | Non-veg indicators |
| `SuccessGreen` | — | Success states |
| `DangerRed` | — | Error, destructive actions |
| `WarningYellow` | — | Warning states |
| `ErrorPink` | — | Input errors |

## Current Locked Palette (Web Admin)

> **Palette revision (2026-10-25):** the ship palette is the **dark command centre**
> below — midnight canvas, navy surfaces, blue `#2563EB` action fill. Earlier
> revisions in this table (white/cream surfaces, purple `#6C27D9`, warm brown
> `#b56a2d`) are recorded only as migration history; none of them are in the code.
>
> The source of truth is one two-tier token set in
> `apps/web-admin/src/styles.css`: primitives (`--kb-blue-*`) and semantic aliases
> (`--kb-color-*`). The legacy short names (`--bg`, `--panel`, `--ink`, `--brand`, …)
> are pure aliases of the semantic layer. New work uses `--kb-color-*`.
>
> This revision also **deleted `styles/wave-c.css` and `styles/wave-de.css`**. They
> were the previous light theme, still loaded after `styles.css`, so their
> `background: #fff` rules beat the dark surfaces: white modals under near-white
> headings, white filter fields, cream skeletons and light stat cards. The part of
> those sheets that was layout now lives in `styles.css`, on tokens.

| Variable | Hex | Usage |
|---|---|---|
| `--kb-color-bg-app` | #0B0E1A | Page canvas |
| `--kb-color-surface` | #15192C | Card / panel / table |
| `--kb-color-surface-2` | #1F2440 | Table header, hover, nested tile |
| `--kb-color-surface-3` | #272D50 | Modal, popover, raised control |
| `--pos-sidebar-bg` | #12162A | Sidebar rail |
| `--kb-color-foreground` | #F3F6FB | Primary text |
| `--kb-color-foreground-2` | #C3CBDA | Values and table cells |
| `--kb-color-muted-foreground` | #93A0B8 | Secondary text |
| `--kb-color-border` / `-strong` | #343B66 / #4C5590 | Dividers / control borders |
| `--kb-color-primary` | **#2563EB** | Primary action fill (white on it 5.17:1) |
| `--kb-color-primary-hover` | #1D4ED8 | Fill hover |
| `--kb-color-link` / `-hover` | #60A5FA / #93C5FD | Brand colour used as **text** |
| `--kb-color-success` / `-soft` | #34D399 / 14% | Success / available / completed |
| `--kb-color-warning` / `-soft` | #FBBF24 / 14% | Warning / pending / low stock |
| `--kb-color-danger` / `-soft` | #F87171 / 14% | Error ink and tints |
| `--kb-color-danger-solid` | #DC2626 | Destructive **fill** with white content |
| `--kb-color-info` / `-soft` | #60A5FA / 14% | Informational states |

**Two colour rules that are now enforced by review:**

1. A colour is declared once, as a `--kb-*` token. Component stylesheets consume
   tokens; they do not carry their own palette. (Before this revision the app had
   177 hex and 181 `rgba()` literals inside component files, in four unrelated
   palettes.)
2. Ink and fill are different jobs. `--kb-color-primary` and
   `--kb-color-danger` are readable *inks* on their own tints; behind white text
   they fail, so fills use `--kb-color-primary` (5.17:1 with white) and
   `--kb-color-danger-solid` (4.83:1).

### Superseded (do not use)

| Variable | Hex | Note |
|---|---|---|
| `--kb-color-bg-app` (cream) | #FAF7F2 | Replaced by #0B0E1A |
| `--kb-color-surface` (white) | #FFFFFF | Replaced by #15192C |
| `--brand` (purple) | #6C27D9 | Replaced by #2563EB |
| `--brand` (brown) | #b56a2d | Replaced by #6C27D9, then by #2563EB |
| `--danger` (old red) | #C0392B | Replaced by #F87171 ink / #DC2626 solid |
| `--accent` (teal) | #0F766E | Removed; `--accent` now aliases `--kb-color-info` |

## Web Admin Layout Rules

These are locked alongside the palette so pages cannot drift apart again:

1. **One page shell.** Every routed page wraps its content in `.page-shell`
   (defined once in `apps/web-admin/src/styles.css`: max-width 1440px, padding
   `1.75rem 2rem 3.5rem`, grid gap `1.5rem`). Pages must **not** override it.
2. **One page header.** Use the shared `PageHeaderComponent`
   (`app-page-header`). Do not hand-roll `.header-left` / `.header-title-row` /
   `.header-sub` / `.header-right` markup or the `.dash-header*` variant.
3. **One shared vocabulary.** Card/button/chip/table styles live in
   `apps/web-admin/src/styles/bento.css`, loaded last so it wins everywhere.
   Use `.bento-tile`, `.intel-card`, `.header-action-btn`, `.btn-micro`,
   `.kpi-tag`, `.status-chip`, `.tactile-modal-*` instead of page-local
   equivalents. A class name must mean exactly one thing across the app (for
   example `.channel-chip` is a table badge; the dashboard's Live Floor filter
   buttons are `.floor-chip`).

   > **Vocabulary revision (2026-10-14):** tables, clickable rows, cards and
   > interactive chrome were unified, so the canonical names changed and the
   > old ones no longer exist:
   >
   > | Was | Is now | Defined in |
   > |---|---|---|
   > | `.orders-tactile-table`, `.admin-table`, `.report-table`, `table.styled`, `.order-modal__table` | `.data-table`, with `.table-wrap` chrome, `.table-totals-row`, `.th-sort`, `.num-cell` | `styles.css`, section "Tables" |
   > | `.order-row-interactive`, and per-cell click handlers with an inline `cursor: pointer` | `.clickable-row`, which also needs `tabindex="0"` plus a keydown handler | `styles.css`, section "Micro-interactions & Tactile Feedback" |
   > | `.close-btn`, `.modal-close-icon` | `.icon-btn` | same section |
   > | page-local card hover rules | `.card-lift` | same section |
   >
   > Motion, press, hover, focus-ring, disabled and invalid-field states are
   > declared once in that section and reach every component stylesheet through
   > zero-specificity `:where()` rules; interactive transitions are kept inside
   > 150-300ms and `prefers-reduced-motion` is honoured globally. This revision
   > added no palette, radius, spacing or typography token and changed no colour
   > value.

---

## Rules

1. **No color value changes** without a written justification linked to user research or accessibility audit.
2. **No new color tokens** — use existing semantic tokens. If you need a color, map it to an existing token.
3. **No theme mode changes** — Android is dark-only, web-admin is dark-only. This is final.
4. **Typography is locked** — Poppins font, 14 M3 type scale slots. No font changes.
5. **Shapes are locked** — `KhanaRadii.button`, `.card`, `.pill` etc. No radius changes.
6. **Spacing scale is locked** — 4/8/12/16/20/24/32/48/64dp. No new spacing tokens.

## How to Get a Change Approved

1. Open an issue titled `[Design] Proposed change: ...`
2. Include: screenshot of current, mockup of proposed, accessibility contrast ratio, affected screens count
3. Get explicit approval before touching any file in `ui/theme/`

---

## Flyway Migration Gaps

The following migration version numbers are intentionally skipped (deleted/squashed during development):

| Gap | Reason |
|---|---|
| V27 | Removed during v2→v3 port consolidation |
| V30–V39 | Reserved range from abandoned feature branch (marketplace v1) |
| V46–V47 | Squashed into V48 (feature flags + webhook inbox) |
| V69–V70 | Squashed into V71 (notification messages) |

**DO NOT create migrations with these version numbers.** Flyway will fail on production databases that already skipped them.

Next available version: **V76** (after V75__add_sub_merchant_virtual_account.sql)

---

## Room Schema Versioning (Android)

Current schema version: **67**

**Rules:**
- Batch schema changes before releases. Don't increment version for every column.
- Each migration MUST have a corresponding `Migration(X, X+1)` object in `DatabaseModule.kt`
- Export schemas (`exportSchema = true`) for CI validation
- Never use `fallbackToDestructiveMigration()` in release builds
