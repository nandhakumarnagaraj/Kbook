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

> **Palette revision (2026-10-13):** the ship palette is **purple `#6C27D9`**, not the
> warm brown `#b56a2d` this table previously listed. The brown entry was stale — the
> code (`web-admin/src/styles.css`, `index.html` favicon) had already moved to purple
> and the doc had not been updated. Purple is canonical; the brown values below are
> recorded only for the migration history.
>
> Also note the source of truth is now a two-tier token set: the legacy short names
> (`--bg`, `--brand`, …) and the semantic `--kb-color-*` aliases. New work should use
> `--kb-color-*`.

| Variable | Hex | Usage |
|---|---|---|
| `--bg` / `--kb-color-bg-app` | #FAF7F2 | Page background |
| `--panel` / `--kb-color-surface` | #FFFFFF | Card/panel background |
| `--kb-color-surface-2` | #F8F5EF | Recessed surface / tab rail |
| `--ink` / `--kb-color-foreground` | #1C1A17 | Text color |
| `--muted` / `--kb-color-muted-foreground` | #6B655C | Secondary text |
| `--line` / `--kb-color-border` | #EFE9DE | Borders |
| `--brand` / `--kb-color-primary` | **#6C27D9** | Primary brand (purple, canonical) |
| `--accent` | #0F766E | Accent teal/green |
| `--kb-color-success` | #2F855A | Success states |
| `--kb-color-warning` | #B7791F | Warning states |
| `--danger` / `--kb-color-danger` | #C0392B | Error red |

### Superseded (do not use)

| Variable | Hex | Note |
|---|---|---|
| `--brand` (brown) | #b56a2d | Replaced by #6C27D9 |
| `--bg` (brown) | #f6f1e8 | Replaced by #FAF7F2 |
| `--ink` (brown) | #24170f | Replaced by #1C1A17 |
| `--muted` (brown) | #7d6b5f | Replaced by #6B655C |
| `--line` (brown) | #e9dcc9 | Replaced by #EFE9DE |
| `--accent` (teal) | #1d7b5f | Replaced by #0F766E |
| `--danger` (red) | #a6372f | Replaced by #C0392B |

## Web Admin Layout Rules

These are locked alongside the palette so pages cannot drift apart again:

1. **One page shell.** Every routed page wraps its content in `.page-shell`
   (defined once in `web-admin/src/styles.css`: max-width 1440px, padding
   `1.75rem 2rem 3.5rem`, grid gap `1.5rem`). Pages must **not** override it.
2. **One page header.** Use the shared `PageHeaderComponent`
   (`app-page-header`). Do not hand-roll `.header-left` / `.header-title-row` /
   `.header-sub` / `.header-right` markup or the `.dash-header*` variant.
3. **One shared vocabulary.** Card/button/chip/table styles live in
   `web-admin/src/styles/bento.css`, loaded last so it wins everywhere.
   Use `.bento-tile`, `.intel-card`, `.header-action-btn`, `.btn-micro`,
   `.kpi-tag`, `.status-chip`, `.orders-tactile-table`, `.tactile-modal-*`
   instead of page-local equivalents. A class name must mean exactly one thing
   across the app (for example `.channel-chip` is a table badge; the dashboard's
   Live Floor filter buttons are `.floor-chip`).

---

## Rules

1. **No color value changes** without a written justification linked to user research or accessibility audit.
2. **No new color tokens** — use existing semantic tokens. If you need a color, map it to an existing token.
3. **No theme mode changes** — Android is dark-only, web-admin is light-only. This is final.
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
