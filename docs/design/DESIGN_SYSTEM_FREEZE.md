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

| Variable | Hex | Usage |
|---|---|---|
| `--bg` | #f6f1e8 | Page background |
| `--panel` | #fffdf8 | Card/panel background |
| `--ink` | #24170f | Text color |
| `--muted` | #7d6b5f | Secondary text |
| `--line` | #e9dcc9 | Borders |
| `--brand` | #b56a2d | Primary brand |
| `--accent` | #1d7b5f | Accent teal/green |
| `--danger` | #a6372f | Error red |

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
