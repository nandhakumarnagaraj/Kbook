# Web Admin — Design Consistency & Accessibility Audit

**Date:** 2026-10-25
**Scope:** `apps/web-admin` only. The audit itself was read-only; §7–§7o now record the fixes applied on top of it.
**Method:** source measurement (CSS + inline component styles) plus a full `ng test` run.
**Auditor:** OpenCode agent

**Status:** fifteen batches applied — duplicate stylesheets (§7) and the `max-width` decision (§7n), the type scale (§7b) and the card-title band (§7o), token fallbacks and touch targets (§7c), breakpoints and the vanished Order History table (§7d), radius and gap (§7e), padding and margin (§7f), one z-index ladder plus a drawer painted under the topbar (§7g), white literals plus colour-token twins (§7h), menu inline styles (§7i), staff permissions (§7j), inventory (§7k), payment methods (§7l) and the final ten inline-style pages (§7m). The remaining findings are still marked open in §2.

**Prior work this builds on**

- `docs/audit/web-admin-ui-audit.md` (2026-10-25, rendered browser pass) — routes, focus trap, empty/error states.
- `docs/design/WEB_ADMIN_AUDIT.md` (2026-10-25, source pass) — explicitly left these **not audited**: *"Duplicate or conflicting styles in `bento.css` and `styles.css`"*, rendered contrast, keyboard order.
- `docs/design/DESIGN_SYSTEM_FREEZE.md` — the standards used here as the spec.

This report answers the open items and quantifies drift the existing suite cannot see.

---

## 1. Test suite status

```
npx ng test --watch=false
TOTAL: 274 SUCCESS   (Chrome Headless 155, exit 0)
```

The `ERROR: '[KhanaBook] …'` lines in the log are intentional error-path tests, not failures. The count moved 273 → 274 when §7g added the drawer layering spec.

**What the suite already protects:** token contrast pairs (`design-tokens.spec.ts`), one table design (`table-design.spec.ts`), phone overflow + tab scrolling (`responsive-layout.spec.ts`), the drawer's computed layer order at 390 px (`sidebar-layout.component.spec.ts`), motion band 150–300 ms and keyboard rings (`micro-interactions.spec.ts`), forms, auth, guards.

**What it does not machine-check:** the `max-width` number. Type scale, radius/spacing, breakpoint set, token fallback literals, colour literals, `z-index` layering and static inline styles are each held by a Node check, because a Karma spec cannot read the stylesheet sources - it only sees the rendered DOM. The eight scripts are `check:type`, `check:tokens`, `check:breakpoints`, `check:spacing`, `check:z-index`, `check:colors`, `check:inline-styles` and the pre-existing `check:motion`. `max-width` now has one source and a matching doc, but no check pins the number. The duplicate-selector finding was fixed in batch 1 (§7), but no check detects a new copy; the `Compare-Object` one-liner in §8 is still the only detector.

---

## 2. Findings, ranked

### P1 — Two stylesheets define the same components → **fixed, see §7**

`bento.css` is loaded **after** `styles.css` (`angular.json`) and is documented as the layer that "wins everywhere". It redefines components that already exist:

| Selector | `styles.css` | `bento.css` | Effect |
|---|---|---|---|
| `.ghost-btn` | `styles.css:570` — padding `.5/.85rem`, radius `--r-lg` (8px), `0.84rem`/500 | `bento.css:822` — `min-height:34px`, padding `6px 13px`, radius **9px**, `12.5px`/**700** | The rendered ghost button is bento's: heavier text, 1px more radius. Two "definitions" of one control. |
| `.spinning` | `styles.css:620` scoped to `.refresh-btn` | `bento.css:234` global, `1s` vs `800ms` | Same class, two spin speeds. |
| `.bento-tile` | `styles.css` (card shell) | `bento.css:245` | Duplicate base + its own `:active { scale(.98) }`, which contradicts the global press `scale(.97)`. |
| `.kpi-tag` | — | `bento.css:268` **and** `:273`, back to back | The first (3-line, white-on-translucent) is dead on arrival. Harmless today, but it is exactly how a future edit silently reverts a badge. |

**Fix applied (batch 1):** the four `styles.css` copies were deleted; `bento.css` is now the sole definition of `.ghost-btn`, `.spinning`, `.bento-tile` and `.kpi-tag`, and the press scale is 0.97. The table above records the state the audit found.

### P1 — `var()` fallbacks carried a retired palette → **fixed, see §7c**

Measured 119 fallbacks in 16 files (`bento.css` held 48), most of them from the **warm/blue
theme that no longer exists**:

```
bento.css:680  var(--danger,  #C0392B)      /* old red */
bento.css:720  var(--warning, #B7791F)      /* old amber */
bento.css:730  var(--info,    #2B6CB0)      /* old blue */
var(--line-strong, #E1D9CA)                 /* cream border, 3× */
var(--panel-2, #F8F5EF)                     /* cream surface */
var(--ink-2,   #2A2A28)                     /* warm ink, 3× */
var(--kb-color-primary, #0066CC) and var(--kb-color-primary, #4F46E5)
                                            /* two different guesses at one token */
var(--kb-color-burnt, #d97706)              /* burnt is #B91C1C, so the fallback was amber */
```

Freeze rule 1 says *"component stylesheets consume tokens; they do not carry their own palette."*
Every one of those tokens resolves, so none of them painted — which is what made it dangerous:
nothing on screen would have shown the app was one renamed token from repainting itself in a
theme nobody had reviewed. All 119 are gone and `npm run check:tokens` fails on a new one.

### P1 — Order History hid its table on phones with nothing in its place → **fixed, see §7d**

`order-history-tab.component.ts` carried a full set of card styles (`.mobile-order-list`,
`.mobile-order-card__title/customer/amount/meta/footer`, `--refunded`) but **no card markup** in
the template. Its own media block hid `.table-wrap > .data-table` anyway, so at ≤768px Order
History rendered an empty panel. The phone-layout spec still passed: a hidden table cannot
overflow, and the spec only measured overflow. This is the worst shape of the "11 of 18 tables
have no mobile card fallback" finding — those scroll; this one disappeared.

### P1 — 114 hex literals in component styles → **58 whites tokenised, see §7h**

| File | Hex |
|---|---|
| `sidebar-layout.component.ts` | 41 |
| `login-page.component.ts` | 19 |
| `pagination-bar.component.ts` | 7 |
| `not-found-page.component.ts` | 6 |
| `menu-page.component.ts` | 6 |

The login-page orange set (`#F58213`, `#C35500`, `#A94400`) and the sidebar `#FFFFFF` outlines are already logged as open in `WEB_ADMIN_AUDIT.md`. **New here:** `pagination-bar` and `not-found-page` — the pagination bar is shared chrome on every list page and is the highest-leverage one to tokenise.

§7h tokenised all **58 white usages**, so the sidebar outlines and the pagination/404 whites are gone. Under the guard's stricter definition (CSS usage, not every occurrence) **21 literal usages remain** — 20 budgeted by value plus one pinned high-contrast ring. The login orange set and the chart/status chip inks are among them.

### P2 — Typography had no scale → **fixed, see §7b**

Measured at 65 distinct `font-size` values. Fifteen sat inside a single visual step:

```
0.68 0.70 0.72 0.74 0.75 0.76 0.78 0.80 0.8125 0.82 0.83 0.84 0.85 0.86 0.88 rem
```

`0.8125rem` (34 uses) and `0.82rem` (26 uses) were the same 13px written two ways; `0.85rem`
(38 uses) was the de-facto body size, named nowhere. Freeze rule 4 locks typography, and the code
had no scale to lock.

There is one now: **nine steps** (`--kb-text-2xs` 11px … `--kb-text-4xl` 32px) declared once in
`styles.css`, with **352 of 397 declarations (89%)** consuming them and a check that fails on a
new literal (`npm run check:type`). 19 off-scale sizes remain — 36 declarations — because
snapping the rest is a per-role design decision, not a rename. The card-title band is resolved in
§7o; the historical list is in §7b.

### P2 — Radius and spacing scales are not used → **radius and gap swept, see §7e**

| Measure | Distinct values | Token uses |
|---|---|---|
| `border-radius` | **47** | 71 |
| `gap` | **52** | — |
| `padding` | rem in 182 places, px in 48 | 108 `--kb-space-*` |

Examples of the same idea spelled three ways: `999px` (35), `var(--kb-radius-full)` (11); `8px` (21), `var(--kb-radius-lg)` (13), `var(--r-lg)` (8); `12px` (20), `var(--r-xl)` (9), `var(--kb-radius-card)` (3).

Freeze rule 6 locks spacing to 4/8/12/16/20/24/32/48/64. The token set exists; adoption was
roughly 30%.

Now: **403 declarations renamed** across §7e and §7f (radius 73% tokenised, gap 53%, padding 44%,
margin 20%), the five `--r-*` aliases deleted, and `npm run check:spacing` fails on a literal that
equals a step, on an unbudgeted value, and on a `var(--r-*)`. `margin`'s 20% is the shape of
`margin`: 70% of its parts are `0` or `auto`. See §7e and §7f.

### P2 — 14 breakpoints → **one ladder + ratchet, see §7d**

```
480  520  560  640  680  720  767  768  860  900  1024  1025  1080  1100
```

`767` (styles.css) vs `768` (bento + components) was a one-pixel disagreement: at exactly 768 a
table stayed a table while the grid above it had already collapsed. The ladder is now
**480 / 640 / 768 / 1024 / 1100**, with `min-width: 1025` as the complement of 1024 (that pair
was already correct — max 1024 plus min 1025 partitions cleanly). Thirteen queries in eight
off-ladder widths remain, budgeted by `npm run check:breakpoints`. They are all "collapse to one
column" rules, and moving a threshold restyles a laptop, so that is a rendered pass rather than
a rename.

### P2 — `max-width` disagreed in three places → **resolved, see §7n**

| Source | Audit-time value | Now |
|---|---|---|
| `DESIGN_SYSTEM_FREEZE.md:152` ("locked") | **1440px** | **1680px**, with a dated revision note |
| `styles.css:311` `.page-shell` | **1680px** (literal) | consumes `var(--kb-content-max-width)` |
| `styles.css:248` `--kb-content-max-width` | **1400px** — declared, never used | **1680px**, the single source |

Freeze rule 1 said every page wraps in `.page-shell` with max-width 1440; the shipped shell was 1680 and the unused 1400 token guaranteed a third answer. The owner chose **1680** on 2026-10-25, so the code kept every rendered pixel and the doc was revised to match. At a 1920 CSS-px viewport with the 240px rail expanded, 1680 fills the 1680px workspace (1616px content after padding); 1440 would leave 120px gutters per side and 1376px content.

### P2 — `z-index` had five unrelated layers — **fixed, see §7g**

`9999`, `1100` (toast), `1000` (modal ×4), `130/110/101/100/99`, `60`, `30`, `10`, `2`, `1`, `0` — the token scale (`--kb-z-topbar/backdrop/modal/toast` = 30/40/50/60) was used **3 times**, all three on `--kb-z-topbar`, and one of those on a *bottom* dock. A dialog at 1000 and a toast at 1100 meant the documented scale was decorative. The cost was not hypothetical: the mobile drawer (100) painted under the sticky topbar (101), so the topbar's surface covered the drawer's own header and close button. §7g replaces all of it with one ladder and a guard.

### P3 — Token values contradict each other (latent) → **channel twin fixed, see §7h**

| Token | Hex | Its own `-rgb` twin / doc |
|---|---|---|
| `--kb-color-info` | `#4338CA` | `--kb-color-info-rgb` is now `67,56,202` = `#4338CA` (**fixed in §7h**; was `29,78,216`) |
| `--kb-color-link` | `#4338CA` | doc says `#1D4ED8` |
| `--pos-sidebar-bg` | `#0F1020` | doc says `#12162A` |
| `--brand-orange` | `#E87A1E` | doc claims 3.1:1 on white; measured **2.90:1** |

`--kb-color-info-rgb` has exactly one consumer (`bento.css:729`, a 0.3-alpha border), so the mismatch was live but narrow: that border was `#1D4ED8` while the info ink and `--kb-color-info-soft` beside it are `#4338CA`. Two of the three declarations agree on `#4338CA` (which is also `--kb-blue-600`), so §7h aligns the triplet to the pair — the border moves to the same blue as its ink. `npm run check:colors` now fails if any `-rgb` channel projection or `-soft` tint stops matching its solid token, so this class cannot return. The doc staleness below (`link`, `sidebar-bg`, `brand-orange`) needs the same owner decision, not a sweep.

Doc staleness worth one commit: `Freeze` rule 3 still says *"web-admin is dark-only"* (line 189) while the locked palette on the same page is a light workspace.

### P3 — Inline styles bypass the system → **134 → 0, see §7i/§7j/§7k/§7l/§7m**

**134 literal `style="…"` attributes at audit time:** menu 54, staff-permissions 26, inventory 20, payment-methods 10. Examples: `style="display:flex;gap:0.5rem;align-items:center;"` (`menu-page:372`), `style="margin:0;font-weight:700;"` (`menu-page:452`), `style="font-size:0.75rem;color:var(--kb-color-primary);…"` (`staff-permissions:52`).

Batch 10 converted the 54 menu attributes, batch 11 the 26 staff-permissions attributes, batch 12 the 20 inventory attributes, batch 13 the 10 payment-methods attributes and batch 14 the final 24 across ten files; **0 remain**. Static layout and type in attributes cannot be tokenised, cannot be themed, and are invisible to every spec in the suite. **Fix:** five arrangement-only utilities (`.row`, `.row--between`, `.row--wrap`, `.stack`, `.flex-1`) plus one-off component classes; `check:inline-styles` now keeps an empty budget and all 14 converted pages at zero, so a converted page cannot regrow one.

---

## 3. Accessibility — measured, mostly clean

Every contrast pair the app renders was computed from the frozen values:

| Pair | Ratio | AA 4.5 |
|---|---|---|
| body ink `#111827` on white | 16.9 | ✅ |
| table ink `#334155` on `#F1F5F9` | 9.45 | ✅ |
| secondary ink `#556377` on white / on table header | 6.11 / 5.58 | ✅ |
| link `#4338CA` on white | 7.90 | ✅ |
| white on primary `#4F46E5` | 6.29 | ✅ |
| success / warning / danger ink | 7.13 / 6.80 / 6.47 | ✅ |
| control border `#8A94A6` (non-text, 3:1) | 3.06 | ✅ |
| sidebar text `#A7B1C6` on `#0F1020` | 8.74 | ✅ |
| orange `#E87A1E` on white | **2.90** | ❌ as text — and it is used as text nowhere ✅ |

**Present and working:** skip link (`sidebar-layout:25`, visible on focus), `<nav aria-label="Main">`, `aria-modal` on all 17 dialogs, 19 `aria-live`, 21 `role="alert"`, 39 `:focus-visible` rules, 45 `scope=`, 28 `aria-describedby`, 10 tab/`aria-selected` pairs, 105 `aria-hidden`, `prefers-reduced-motion` and `prefers-contrast: more` both handled, one `<h1>` per page via `PageHeaderComponent`, all `<img>` have `alt` (0 missing), every form associates an error message.

**Gaps**

1. **Active nav item is visual only.** The rail uses `RouterLinkActive` with a CSS class; `aria-current` appears once in the whole app. Screen readers hear 11 identical links. One line: `ariaCurrentWhenActive="page"` on the `routerLinkActive` anchors.
2. **Touch targets below 44 px.** `.icon-btn` 32×32 (`styles.css:1071`), `.cat-fav-btn` 28×28, `.toggle-btn` 42×24, `.availability-toggle` min-height 30, `bento.css` `.ghost-btn` min-height 34. All pass WCAG 2.2 **2.5.8 (24×24)** — none meet the 44 px comfortable target the Android app holds itself to. Relevant on tablets behind a counter.
3. **11 of 18 tables have no phone equivalent.** No `.mobile-data-list` in `platform-dashboard` (3), `business-dashboard` (3), `reports` (2), `order-history` (1), `inventory` (1). They degrade to horizontal scroll inside `.table-wrap`. Acceptable, but inconsistent with the 7 tables that do switch to cards.
4. **Sticky header + `border-collapse: collapse`.** `styles.css:440-446` sticks `thead th` inside a collapsed-border table; Chrome drops the cell borders while stuck, so the header floats borderless over the rows. Known engine behaviour — needs either `border-collapse: separate` + `border-spacing: 0` or a `box-shadow` hairline on the sticky cell.

---

## 4. What is genuinely good (do not "fix" these)

- The palette is disciplined: **1,135 `var(--kb-color-*)` uses** against 21 remaining component hex literals (the other 58 whites were tokenised in §7h), and every semantic pair clears AA.
- One table design, one page shell, one page header, one badge vocabulary, all with the drift they replaced written down next to them.
- Focus, press, hover, disabled and invalid state are declared once with `:where()` and reach components without editing them.
- 270 green tests including purpose-built design specs.

This is a mature system with **scale drift**, not a broken one. The problem is that nothing measures the scale.

---

## 5. Suggested order of work

| # | Change | Effort | Removes |
|---|---|---|---|
| 1 | Delete the duplicate `.ghost-btn` / `.spinning` / `.bento-tile` copies; drop the dead first `.kpi-tag` | 30 min | Two definitions of one control, 2 contradictory press scales |
| 2 | Decide `max-width` (1440 vs 1680), delete the unused `--kb-content-max-width` or use it, align the freeze doc | 30 min | 3 disagreeing numbers — **done, see §7n**: 1680 chosen, token consumed, freeze doc aligned |
| 3 | Declare `--kb-text-*` + sweep the 15 near-identical sizes down to the steps | 1 day | 65 → 9 font sizes — **done, see §7b**; the card-title band is resolved in **§7o** (16px cards / 20px sections and modals). 36 declarations in 19 sizes remain as non-title values, icons and body copy; those need separate calls |
| 4 | Collapse 14 breakpoints to 4 (fix 767/768 first) | half day | Per-page meaning of "tablet" - **partly done, see §7d**: one ladder declared, 767 fixed, 13 queries ratcheted; the collapse itself needs a rendered pass. Also fixed an Order History table that vanished on phones |
| 5 | Add `ariaCurrentWhenActive="page"`; raise `.icon-btn` hit areas to 44 px | 1 h | Silent nav for AT; small targets — **done, see §7c** (`aria-current` was already on the desktop nav; added to the mobile bar. `.toggle-btn`'s target is its whole `.toggle-wrap` label, so it was left alone.) |
| 6 | Replace stale palette fallbacks in `bento.css` with token-only declarations | 2 h | A retired theme waiting to repaint the app — **done, see §7c** (119, not 42: the audit only counted `bento.css`) |
| 7 | Inject the utility classes for the 134 inline styles (menu first) | 1 day | Untokenisable layout — **all 134 converted, see §7i/§7j/§7k/§7l/§7m**: 134 attributes → 0; five utilities plus `.textarea-address`; 14 pages at 0 with an empty budget |
| 8 | Add a `scale.spec.ts`: assert distinct font-size/radius/breakpoint sets stay ≤ the agreed counts | half day | Silent return of all of the above — **done as eight Node checks, see §7b/§7d/§7e/§7g/§7h/§7i/§7j/§7k/§7l/§7m**: `check:type`, `check:spacing`, `check:breakpoints`, `check:tokens`, `check:z-index`, `check:colors`, `check:inline-styles`, `check:motion` |
| 9 | Declare one z ladder and point every overlay at it | 2 h | Five unrelated layers, two modal systems at 100/1000, two toasts at 1100/9999, and a drawer painted under the topbar — **done, see §7g** |
| 10 | Tokenise white and make every alpha twin carry its solid token's channels | 2 h | 58 retyped `#FFFFFF` rules and one `-rgb` projection in a different blue from its own ink — **done, see §7h** |

Item 8 is the one that makes the rest stick — the suite is green today because nothing asserts what "one design" means at the scale level. It is eight scripts under `apps/web-admin/scripts/` rather than a Karma spec, because the assertion has to read the stylesheet sources and a browser spec only sees the rendered DOM. `max-width` now has one source and a matching doc; unlike the scales, no check pins its value.

---

## 7. Change log — first batch applied

Two files changed: `src/styles.css`, `src/styles/bento.css`. No templates, no logic.

| Fix | What was done |
|---|---|
| Duplicate `.ghost-btn` | Deleted the `styles.css` copy (570–592). The `bento.css` copy is now the single definition and absorbed the structural props the old one was still supplying: `display:inline-flex`, `align-items`, `justify-content`, `gap` — required because `.ghost-btn` is also used on `<label>` (photo picker, settings file picker) and `<a>` (404 page), which the global `button` rules never reach. Added `:active` (0.97), `:focus-visible` and `[disabled]` so nothing was lost. `transition: all` became an explicit property list. |
| Duplicate `@keyframes spin` | Deleted the second, identical copy in `styles.css` (the partial `to {…}` one). One remains, next to the first control that uses it. |
| Dead `.kpi-tag` | Deleted the 3-line translucent-white block that was overridden by the definition three lines below it. |
| Contradictory press | `.bento-tile:active` 0.98 → 0.97, matching the global press. |
| `max-width` | `.page-shell` now reads `var(--kb-content-max-width)`; the token is 1680px (the shipped width, chosen on 2026-10-25). One number in code instead of three; the freeze doc is aligned, see §7n. |

**Kept deliberately (not duplicates):** `.refresh-btn .spinning` at 800 ms is a *scoped* override of the global 1 s spin — a different intent, not a conflict. `.bento-tile` appears in `styles.css` only inside `@media print`, where flattening every card is the point.

**Verification**

```
npx ng build --configuration development   →  exit 0, 21.2 s
npx ng test --watch=false                  →  TOTAL: 270 SUCCESS
```

**Resolved from this table of decisions:** `DESIGN_SYSTEM_FREEZE.md` rule 1 said 1440px while the code shipped 1680. The owner chose 1680 on 2026-10-25; the doc now matches and the code still has exactly one place to change the measure. See §7n.

---

## 7b. Change log — type scale applied

34 files: the two global sheets, 30 component stylesheets/templates, one spec, one npm script.
The change is value substitution only — no selector, template, or logic edits — so any single
file can be reverted on its own with `git checkout -- <path>`.

### The nine steps

| Token | Size | Role | Absorbed (count) |
|---|---|---|---|
| `--kb-text-2xs` | 11px | eyebrow, uppercase table header, micro label | `0.68` (9), `0.7` (7), `0.72` (32), `0.65` (3), `0.66` (1) |
| `--kb-text-xs` | 12px | caption, chip, hint | `0.74` (12), `0.76` (17), `0.75` (19), `12px` (3) |
| `--kb-text-sm` | 13px | dense table cell, secondary body | `0.78` (26), `0.8` (22), `0.82` (26), `0.83` (1), `0.84` (13), `0.8125` (34), `12.5px` (2), `13px` (9) |
| `--kb-text-md` | 14px | body copy, the default | `0.85` (38), `0.86` (11), `0.88` (16), `0.9` (4), `0.875` (5), `13.5px` (2), `14px` (9) |
| `--kb-text-lg` | 16px | card title, emphasised value | `0.98` (1), `1rem` (5), `16px` (1) |
| `--kb-text-xl` | 20px | section heading | `1.25rem` (4), `20px` (1) |
| `--kb-text-2xl` | 24px | page heading | `1.5rem` (1), `24px` (1) |
| `--kb-text-3xl` | 28px | KPI figure | `1.75rem` (1), `28px` |
| `--kb-text-4xl` | 32px | hero figure | `2rem` (2), `32px` |

**Every shift is within ±0.6px.** The largest are `0.85rem → 14px` (+0.4, 38 declarations — the
de-facto body size finally named) and `0.65rem → 11px` (+0.6); the largest downward is
`0.72rem → 11px` (−0.52). Anything further from a step was left alone rather than rounded,
because past ±0.6px it stops being a rename and starts being a redesign.

**Result: 65 → 20 distinct sizes.** 342 of 409 `font-size` declarations (84%) are tokenised;
8 are fluid `clamp()`/`calc()` ranges and 1 is `font-size: 0`.

### Deliberately still off-scale at this point — 58 declarations, 20 sizes

| Size | px | Uses | Nearest step |
|---|---|---|---|
| `0.95rem` | 15.2 | 11 | lg 16 (+0.8) or md 14 (−1.2) |
| `1.05rem` | 16.8 | 11 | lg 16 (−0.8) |
| `1.1rem` | 17.6 | 6 | lg 16 or xl 20 |
| `0.92rem` | 14.72 | 4 | md 14 |
| `1.15rem` | 18.4 | 4 | xl 20 |
| `15px` | 15 | 4 | md 14 or lg 16 |
| `0.65rem` | 10.4 | 3 | 2xs 11 |
| `1.2rem` | 19.2 | 3 | xl 20 |
| `0.6` `1.12` `1.3` `1.35` `1.4` `1.45` `1.55` `1.8` `1.85` rem, `17px`, `19px`, `0.66rem` | — | 1 each | 11/16/20/24/28/32 |

These cluster in the 15–19px "card title" band, and the question there is not how to spell the
number — it is whether a card title is 16 or 20. That is a design decision about hierarchy, not
a rename. Left for the owner with a rendered page in front of them.

> **Later decision (2026-10-25):** §7o resolved 22 of these declarations — 19 snapped and 3
> removed — leaving 36 in 19 sizes. The table above records the state at §7b.

### The ratchet

`scripts/check-type-scale.mjs` + `npm run check:type`, following the `check:motion` precedent
(a spec can only measure what it mounts; this reads every stylesheet and inline template).
It fails on:

1. a literal that equals a step (`font-size: 12px` → must be `var(--kb-text-xs)`);
2. an off-scale literal the budget does not name;
3. a budgeted size used more often than its budget.

The budget is the 20-size table above with today's counts. Shrinking a number is the work;
raising one to make the build pass is the regression it exists to catch. Fluid ranges and
`font-size: 0` are exempt.

### Spec change

`shared-vocabulary.spec.ts` pinned `.kpi-tag` at `rem(0.74)` — 11.84px, the page-local size the
shared class was supposed to have escaped. It now asserts `tokenSize('--kb-text-xs')`, mirroring
the existing `tokenColor()` helper: the guarantee is "paints from the shared step", not "is
11.84px". Pinning the literal is what let the drift survive in the first place.

### Deleted

| Token | Why |
|---|---|
| `--kb-font-caption` | `0.75rem` — the same size as `--kb-text-xs` under a second name. Its 3 consumers (bottom action bar) now read the step. |
| `--kb-font-size-display` | Declared, never read. |
| `--kb-font-size-h1` | Declared, never read (it had already been pointed at `--kb-text-3xl`). |

### Verification

```
npm run check:type                        →  342/409 tokenised, 58 off-scale (all budgeted)
ng build --configuration development      →  exit 0, 14.1 s
ng test --watch=false                     →  TOTAL: 270 SUCCESS
npm run check:motion                      →  111 durations in band
```

The suite caught one real thing: the `.kpi-tag` spec went red because the tag's size moved
11.84 → 12px. That is the spec doing its job.

---

## 7c. Change log — token fallbacks and touch targets

### 119 fallbacks removed, 16 files

Every `var(--token, <literal>)` is now `var(--token)`. Before deleting anything I checked that
all 57 tokens involved are declared in a globally-loaded sheet, so **no fallback was load-bearing
and nothing on screen changes**. The sweep was paren-aware, so `rgba()` and nested
`var(--a, var(--b))` survived intact.

Two things it fixed beyond the cosmetic:

- `--kb-color-primary` had **two** fallbacks in the tree, `#0066CC` and `#4F46E5`, and neither is
  the indigo the token resolves to. The same decision, three answers, none of them live.
- `--kb-color-burnt`'s fallback was amber (`#d97706`) while the token is deep red (`#B91C1C`) —
  a fallback that would have repainted the "burnt" state in the wrong hue on the one day it fired.

Added `npm run check:tokens` (`scripts/check-token-fallbacks.mjs`): any new `var(--x, …)` fails,
with a note to declare the token in `:root` instead. Comment-aware, so prose about fallbacks is
not flagged.

### The `font:` shorthand

`date-range-selector` used `font: 500 0.78rem/1.2 var(--kb-font-sans)` — a size the `font-size`
walk could never see, which is how 0.78rem survived the type sweep. It is longhand now
(`font-size: var(--kb-text-sm)`, 12.48 → 13px), and `check:type` fails on any `font:` shorthand
that carries a size.

### Touch targets

- `ariaCurrentWhenActive="page"` added to the **mobile** bottom bar. The desktop sidebar already
  had it (it was in the pre-existing working-tree changes), so the audit item was half-done.
- `.icon-btn` keeps its 32px ring and gains a 44px (`--kb-touch-min`) hit area through an
  `::after` overlay — **only under `@media (pointer: coarse)`**. On a mouse, crowded toolbars are
  comfortable at 32 and an overlapping ring would swallow a neighbour's click; on the terminals
  and tablets this app actually runs on, the cross is now thumb-sized. Nothing reflows.
- `.toggle-btn` was left alone on purpose: its real target is the `.toggle-wrap` label around it.

### Verification

```
npm run check:type    →  343/410 tokenised, 58 off-scale (all budgeted), no font: shorthand
npm run check:tokens  →  no var() fallbacks in src
npm run check:motion  →  111 durations in band
ng build --configuration development  →  exit 0, 14.1 s
ng test --watch=false                 →  TOTAL: 270 SUCCESS
```

## 7d. Change log — breakpoints, and an order list that vanished on phones

### The bug the breakpoint work found

`order-history-tab.component.ts` had a complete mobile card design — `.mobile-order-list`,
`.mobile-order-card`, title/customer/amount/meta/footer, a `--refunded` border — and **no card
markup in the template**. Its own media block hid `.table-wrap > .data-table` regardless, so at
≤768px Order History was an empty panel. The existing phone spec ("fits inside a phone viewport
without sideways page scroll") stayed green throughout, because a hidden table overflows nothing
and the spec measured only overflow.

The fix does not invent the missing markup. It stops hiding the table, so the shared rule
(`.table-wrap:not(:has(.mobile-data-list))` → `overflow-x: auto`) scrolls it, exactly like the
other tables with no card fallback. The card styles stay, with a comment saying the markup must
land before the hide rule comes back.

Three specs now lock it down:

- at 390px the order table must not be `display: none` unless a `.mobile-order-list` exists —
  the assertion the old spec was missing;
- a `.table-wrap` containing both a table and a `.mobile-data-list` hides the table and shows
  the cards, which is the mechanism the other pages depend on;
- 768px enters the narrow layout and 769px leaves it, so the boundary cannot drift a pixel again
  without a failure.

Proven by re-adding the hide rule: `14 SUCCESS, 1 FAILED` — the new spec, and only it.

### 767 → 768

The only literal off-by-one. Both rules meant "tablet", so the tablet step wins. No `min-width:
768` existed to create an overlap. The stale `767px` comment in `responsive-layout.spec.ts` now
says 768.

### The ladder, and a ratchet instead of a guess

```
480   phone
640   phone landscape
768   tablet — where a table gives way to cards
1024  laptop — paired with min-width: 1025 for the nav drawer
1100  the dashboards' widest collapse
```

39 width queries exist. 26 are on the ladder, 13 are budgeted across eight off-ladder widths
(520, 560, 680, 720, 860, 900, 960, 1080). `npm run check:breakpoints` fails on `max-width: 767`,
on a `1025` that is not `min-width`, and on any width that is new or over budget; it prints the
remaining histogram every run.

Why not collapse the thirteen here? Every one is a "collapse to one column" rule. Moving a
`max-width` **up** is overflow-safe — the element gets more room than it was designed for — but it
still restyles a laptop (the global `900` block would stack `.filter-grid` into one full-width
column at 1000px). Moving one **down** can overflow. Both are decisions made by looking at a
screen, so the ratchet holds the line and the mapping is documented rather than guessed.

### Verification

```
npm run check:type         →  343/410 tokenised, 58 off-scale (all budgeted)
npm run check:tokens       →  no var() fallbacks in src
npm run check:breakpoints  →  26/39 on the ladder, 13 budgeted in 8 widths
npm run check:motion       →  111 durations in band
ng build --configuration development  →  exit 0, 15.3 s
ng test --watch=false                 →  TOTAL: 273 SUCCESS
```

## 7e. Change log — radius and gap join the scale

### 231 declarations renamed in 30 files, 28 alias references retired

Only literals that already equalled a step were touched, so this paints the same pixels:

| Literal | Token |
|---|---|
| `4px` / `0.25rem` | `--kb-radius-sm` |
| `6px` / `0.375rem` | `--kb-radius-md` |
| `8px` / `0.5rem` | `--kb-radius-lg` |
| `12px` / `0.75rem` | `--kb-radius-xl` |
| `16px` / `1rem` | `--kb-radius-2xl` |
| `999px` / `9999px` | `--kb-radius-full` |
| `4px` / `0.25rem` | `--kb-space-1` |
| `8px` / `0.5rem` | `--kb-space-2` |
| `12px` / `0.75rem` | `--kb-space-3` |
| `16px` / `1rem` | `--kb-space-4` |
| `20px` / `1.25rem` | `--kb-space-5` |
| `24px` / `1.5rem` | `--kb-space-6` |
| `32px` / `2rem` | `--kb-space-8` |
| `40px` / `2.5rem` | `--kb-space-10` |
| `48px` / `3rem` | `--kb-space-12` |
| `64px` / `4rem` | `--kb-space-16` |

The `999px` pill deserves a note: the token is `9999px`, and 999px is already more than half of
every box in this app, so the corners are identical. Two specs had pinned the literal — they now
resolve `tokenLength('--kb-radius-full')`, the same pattern `tokenSize` already used, because
pinning `999px` is how the value kept drifting from the token it was supposed to be.

### The second radius ladder is gone

`--r-sm/md/lg/xl/2xl` were the same five values as `--kb-radius-*`. Their 28 references now read
the canonical token and the five declarations are deleted. freeze rule 1 says a decision is
declared once; this was the same decision under two names, plus a third (`--kb-radius-card` →
`--kb-radius-xl`).

### Adoption

Measured the same way on both sides — every part of every `border-radius` / `gap` declaration,

with the HEAD tree checked out into a throwaway worktree for the "before" column:

| | Before (HEAD) | After |
|---|---|---|
| `border-radius` parts using a token | 35% (99/283) | **73%** (207/282) |
| `gap` parts using a token | 14% (45/322) | **53%** (171/321) |

Still off-scale and budgeted: **39 radius parts in 12 values** (10px ×19, 14px ×4, 9px ×3,
18px ×3, 7px ×2, 2px ×2, then singles) and **145 gap parts in 24 values** (0.35rem ×21,
0.4rem ×18, 0.65rem ×15, 0.45rem ×15, 0.6rem ×12, 6px ×11, 0.85rem ×11, 0.3rem ×10, …).

Those gap values are not noise: 0.35rem (5.6px), 0.45rem (7.2px) and 0.65rem (10.4px) are a
deliberate tighter rhythm than the 4/8/12 ladder. Snapping them to 4/8/12 changes the density of
every toolbar in the app — a design decision of the same kind as the 15–19px card-title band, so
they stay budgeted rather than guessed.

### `npm run check:radius-gap`

(Now `npm run check:spacing` — the same script, renamed when `padding` and `margin` joined
it. See §7f.)

Fails on:

- a literal that equals a step (`gap: 8px` → `--kb-space-2`), because that is the exact
  regression that re-grows the old 52-value set;
- an off-scale literal that is new, or above its budget;
- `var(--r-*)`, which no longer exists.

Proven with four injected violations — step literal, unbudgeted value, deleted alias, budget
overrun — then reverted byte-identically.

### Verification

```
npm run check:type         →  343/410 tokenised, 58 off-scale (all budgeted)
npm run check:tokens       →  no var() fallbacks in src
npm run check:breakpoints  →  26/39 on the ladder, 13 budgeted in 8 widths
npm run check:radius-gap   →  radius 207/282 (73%), gap 171/321 (53%)
npm run check:motion       →  111 durations in band
ng build --configuration development  →  exit 0, 15.8 s
ng test --watch=false                 →  TOTAL: 273 SUCCESS
```

---

## 7f. Change log — padding and margin join the scale

### 172 declarations renamed in 29 files

The same rule as §7e: only a literal that already uses a step was replaced, part by part, so a
four-value shorthand keeps its off-scale parts:

```css
/* before                                  after */
padding: 3rem 1rem;                     →  padding: var(--kb-space-12) var(--kb-space-4);
padding: 0.65rem 1rem 0.65rem 2.2rem;   →  padding: 0.65rem var(--kb-space-4) 0.65rem 2.2rem;
margin: 0.25rem 0;                      →  margin: var(--kb-space-1) 0;
padding: 1rem 1.1rem;                   →  padding: var(--kb-space-4) 1.1rem;
```

Exact-step parts converted: 169 in `padding` (1rem ×44, 0.5rem ×20, 1.5rem ×19, 0.75rem ×18,
1.25rem ×17, 8px ×12, …) and 36 in `margin` (1rem ×10, 0.25rem ×9, 0.75rem ×5, …).

### Adoption

Measured on the same parts basis as §7e, HEAD into a throwaway worktree for the "before" column:

| | Before (HEAD) | After |
|---|---|---|
| `padding` parts using a token | 11% (58/511) | **44%** (226/508) |
| `margin` parts using a token | 5% (13/250) | **20%** (49/250) |

`margin` looks low because 175 of its 250 parts are `0` or `auto` — an exempt 70%. Counting only
the parts that carry a length, margin went from 13/74 to 49/74. Overall spacing/radius adoption
across all four properties is **653 of 1361 parts (48%)**, up from 215 of 1366 (16%).

Still off-scale and budgeted: **232 padding parts in 41 values** and **25 margin parts in 13
values** — 0.85rem ×19, 0.55rem ×18, 0.65rem ×15, 0.2rem ×15, 2px ×14 in padding; 0.35rem ×7
plus the negative offsets (`-1px`, `-0.25rem`, `-1.5rem`) in margin. Those are the same tighter
rhythm §7e describes, not a second accident.

### The check learned two lessons

`check-spacing.mjs` now covers four properties and refuses to judge a declaration unless every
part is CSS. That matters because of two real strings in the tree:

```
qr-code.util.ts   margin: 2,        // a QRCode library option, not CSS
payment-methods   style="margin:2px 0 0">You can still retry…
```

The first is a JS object property; the second runs into template text until a semicolon. Both were
counted as spacing by the first draft. A check that fails on the QR code's `margin` is a check
people learn to ignore, so it skips them and reports `7 declaration(s) skipped as non-CSS`.

Units other than px/rem (`margin: 14mm` in the print stylesheet) are counted separately, not
budgeted: they are not drift on this ladder.

### One caveat this batch makes visible

The type tokens are `rem`, so text grows with the reader's root font size; the spacing tokens are
`px`. Converting `1rem` of padding to `var(--kb-space-4)` therefore pins it to 16px even if the
reader sets a 20px default. That is the freeze's ladder and the app's existing direction (57
padding parts were already tokens), but the *unit* question should be decided once at the token
declaration — `--kb-space-1: 4px` → `0.25rem` would restore root-size scaling everywhere in one
edit. That is an owner decision, not a sweep, so it stays open here.

### Verification

```
npm run check:spacing  →  radius 207/282 (73%), gap 171/321 (53%),
                         padding 226/508 (44%), margin 49/250 (20%)
ng build --configuration development  →  exit 0, 8.0 s
ng test --watch=false                 →  TOTAL: 273 SUCCESS
```

The four failure modes were re-proven after the rewrite (step literal, unbudgeted value,
`var(--r-*)`, budget overrun → 4 problems), plus a non-CSS decoy that must *not* fire.

---

## 7g. Change log — one z ladder, and a drawer painted under the topbar

### The bug: the drawer's own header was behind the topbar

On a phone the shell stacked its layers by hand:

```
.sidebar-backdrop  99      .sidebar  100      .topbar  101
```

All three are children of `.layout-shell`. The drawer is `position: fixed; top: 0; height: 100vh`,
so it starts at the top of the screen — underneath the sticky, opaque topbar at 101. The drawer's
brand row and its close button live in that first ~70 px. While the menu was open the topbar was
`inert`, so taps fell through to the close button and the drawer still *worked*, but its header was
painted over. That is how the ordering survived: it looked like a layering opinion, and nothing
measured it.

### The ladder

Eleven rungs, declared once in `src/styles.css`, low to high:

| Rung | Value | Owns |
|---|---|---|
| `--kb-z-raised` | 1 | sticky `thead` inside its scroll container |
| `--kb-z-tooltip` | 10 | staff-page tooltip chip |
| `--kb-z-dock` | 30 | bottom action bar, sticky save dock |
| `--kb-z-dropdown` | 60 | notification flyout |
| `--kb-z-topbar` | 100 | mobile topbar |
| `--kb-z-backdrop` | 110 | drawer scrim (dims the inert topbar too) |
| `--kb-z-sidebar` | 120 | drawer, above its scrim |
| `--kb-z-skip` | 130 | skip link |
| `--kb-z-peek` | 140 | collapsed-rail hover chip |
| `--kb-z-modal` | 1000 | every modal overlay, whichever component owns it |
| `--kb-z-toast` | 1100 | every toast, above an open modal |

The three dead tokens (`backdrop: 40`, `modal: 50`, `toast: 60`) are gone. `--kb-z-topbar` now means
the topbar; the two bottom docks that had been borrowing it moved to `--kb-z-dock` at the *same*
value (30), so nothing on the bottom of the screen moved a pixel.

### What moved, and why the order still holds

- **Shell (the fix).** topbar `101 → 100`, backdrop `99 → 110`, sidebar `100 → 120`, skip `110 → 130`,
  peek `130 → 140`. The drawer is now above its scrim and the scrim above the topbar, which is the
  conventional drawer order. Relative order inside the rest of the shell is unchanged: skip link
  above the drawer, peek chip above the skip link, docks below everything.
- **Modals.** `.tactile-modal-overlay` was at 100 while the four `*.modal-backdrop` /
  `*.confirm-dialog__overlay` / `.order-modal__overlay` rules were at 1000. All five now read
  `--kb-z-modal`. A tactile modal opened from the rail can no longer land on the same rung as the
  sidebar.
- **Toasts.** `.global-toast` was at `9999` and `.toast` at `1100`; both read `--kb-z-toast` now.
  Nothing existed above 1100, so the effective order is unchanged — there is simply one answer.
- **Unchanged values.** raised 1, tooltip 10, dock 30, dropdown 60. The login page's 0/1/2 art stack
  is local stacking inside one card, not a page layer, and was left alone (byte-identical).

### The guard — `npm run check:z-index`

`scripts/check-z-index.mjs` fails when:

1. a `z-index` is not `var(--kb-z-*)` (including a fallback, `calc()` or a bare number);
2. the ladder is not declared in ascending order, or two rungs share a value;
3. a declared rung is never used (the dead-token failure);
4. a rung reference names a token that is not declared.

The auth card's nine 0/1/2 declarations are the single budgeted exception, named by file. It reports
`27 declaration(s) - 18 on the ladder in 11 rung(s), 9 local (auth card art stack)`.

Proven by injection, then restored byte-identically:

```
literal z-index outside the auth card      caught  (bento.css:1134, z-index: 42)
two rungs sharing a value                  caught  (--kb-z-toast: 1000 = --kb-z-modal)
a declared rung that nothing uses          caught  (--kb-z-dead: 150)
a fallback instead of the bare rung        caught  (var(--kb-z-peek, 140))
```

### Spec

`sidebar-layout.component.spec.ts` gained one spec. It drives the Karma iframe to 390 px, opens the
drawer, and asserts the **computed** z-index order `topbar < backdrop < sidebar` — the regression that
the old numbers encoded. It is the first spec in that file to need a real viewport, so it reuses the
iframe technique from `responsive-layout.spec.ts`.

### Verification

```
npm run check:z-index   →  27 declarations: 18 on the ladder in 11 rungs, 9 local
npm run check:motion    →  111 transition durations in band
npm run check:type      →  343/410 tokenised, 58 budgeted
npm run check:tokens    →  no var() fallbacks
npm run check:breakpoints → 26/39 on the ladder, 13 budgeted
npm run check:spacing   →  radius 73%, gap 53%, padding 44%, margin 20%
ng build --configuration development  →  exit 0, 7.2 s
ng test --watch=false                 →  TOTAL: 274 SUCCESS
```

---

## 7h. Change log — white joins the palette, and a twin that disagreed

### 58 retyped whites

`#FFFFFF` had **eleven names** in `styles.css` (`surface`, `surface-3`, `input`,
`foreground-contrast`, `on-primary`, `primary-foreground` and their aliases) and was still retyped
**58 times** in rules across 14 files:

| Property | Uses | Consumes |
|---|---:|---|
| `color` | 33 | `foreground-contrast` 24, `on-primary` 9 |
| `background` | 14 | `surface` 12, `input` 2 |
| `outline` / `outline-color` | 6 | `foreground-contrast` |
| gradient stops (`.zigzag-edge--top/bottom`) | 4 | `surface` |
| `border-top-color` (`.btn-spinner`) | 1 | `foreground-contrast` |

The mapping follows what the white paints, not the property name:

- **`on-primary`** — ink on a fill painted by `--kb-color-primary` / `--brand`: the skip link,
  `.primary-btn`, `.refresh-btn`, `.toggle-btn--on`, `.header-action-btn--primary`,
  `.btn-micro--primary`, the active tab pill and the active page number.
- **`surface`** — a white plane: the brand logo circle, `.peak-dot`, the QR media tile, the
  receipt card **and its four zigzag stops** (the notch has to follow the card it is cut from),
  `.card-action-btn`, `.ghost-btn`, `.tab-pill`, `.pagination-page`, `.toggle-knob`, `.no-print`,
  `.audit-stat`, the body canvas.
- **`input`** — the two field backgrounds: `.cash-count-field` and `.field-select, .field-control`.
- **`foreground-contrast`** — ink and focus rings on dark, danger, warning, success and brand
  fills: the sidebar's ink and rings (14), row actions, the login footer and disabled button, the
  notification badge, the state icons, retry, and the spinner arc.

Every one of those names resolves to `#FFFFFF`, so the swap is a rename, not a repaint. The one
deliberate pixel change is the info twin below.

### The twin that disagreed

`--kb-color-info-rgb: 29, 78, 216` projected `#1D4ED8`, while `--kb-color-info` and
`--kb-color-info-soft` both sit on `#4338CA` (also `--kb-blue-600`). Its only consumer,
`bento.css:729`, drew a 0.3-alpha border in the wrong blue. Aligning the triplet to the pair moves
that one border from `#1D4ED8` to `#4338CA` — the blue of the ink and tint beside it. Two of three
declarations plus an existing palette step agree; this is a correction of the outlier, not a new
colour.

The print rule `.data-table thead th` also moved from `#F1F5F9` to
`var(--kb-color-surface-2)` — same value, and the printed header now follows the table-header
token.

### The guard — `npm run check:colors`

Three rules, all read from source:

1. **An alpha twin carries its solid token's channels.** Every `--x-rgb: r, g, b` must equal the
   resolved `--x`, and every `--x-soft: rgba(r, g, b, a)` must tint the channels of `--x`.
   **12 twins** are checked (7 `-rgb`, 5 `-soft`).
2. **White is a token, not a value.** No `#fff` / `#ffffff` outside a custom-property declaration —
   budget 0. The palette defines white; rules consume it. `qr-code.util.ts` is exempt because it
   passes colour strings to the encoder, not CSS.
3. **A literal keeps its budget.** A literal equal to exactly one token's value fails (there is a
   name for it); values shared by several names, or held by none, stay in a per-value budget that
   can only shrink. One pinned exception: the `prefers-contrast` focus ring's near-black `#0F172A`,
   which must not follow a theme.

Text is not CSS. `Terminal #01 • Kitchen Ticket #404` is a ticket number; only declaration values,
inline style attributes and SVG paint attributes are counted. One hex-like string was ignored.

**Remaining:** 20 usages across 14 values — `#000000` ×5 (contrast overrides), `#34D399` ×2,
`#FF851B` ×2, then single login/auth brand stops, chip inks, the unsaved badge, the audit edge and
the KPI track. Each is either a deliberate contrast pin, a page-local brand value, or a value three
token names share (so a mechanical pick would invent intent).

### What was proven

```
literal white in a rule                caught  (styles.css:1542, color: #FFFFFF)
-rgb twin drifts from its solid         caught  (info-rgb 29,78,216 vs #4338CA)
-soft tint drifts from its solid        caught  (success-soft rgb(23,…) vs #166534)
literal with exactly one token name     caught  (color: #A5B4FC → --kb-blue-300)
unbudgeted literal value                caught  (color: #ABCDEF)
```

All five were restored byte-identically afterwards (sha256 match).

### Verification

```
npm run check:colors     →  12 channel twins agree, 0 white literals, 20 budgeted + 1 pinned
npm run check:z-index    →  27 declarations: 18 on the ladder in 11 rungs, 9 local
npm run check:motion     →  111 transition durations in band
npm run check:type       →  343/410 tokenised, 58 budgeted
npm run check:tokens     →  no var() fallbacks
npm run check:breakpoints → 26/39 on the ladder, 13 budgeted
npm run check:spacing    →  radius 73%, gap 53%, padding 44%, margin 20%
ng build --configuration development  →  exit 0, 7.3 s
ng test --watch=false                 →  TOTAL: 274 SUCCESS
```

---

## 7i. Change log — inline styles: the menu page, and a ratchet

### 54 attributes, 44 classes, five utilities

The menu page was the largest single source of static inline styles: **54 `style="…"` attributes**, all in the Add/Edit item modal and the category-management modal. They moved into the component's own `styles` block as 44 role-named classes (`.variants-section`, `.variant-row`, `.category-create-row`, `.cat-action-btn`, …). The page now has **zero** static style attributes.

Five utilities were added to `bento.css`, next to the shared vocabulary:

| Utility | Declaration | Use |
|---|---|---|
| `.row` | `display:flex; align-items:center` | a horizontal line of controls |
| `.row--between` | `justify-content:space-between` | header row: title left, action right |
| `.row--wrap` | `flex-wrap:wrap` | a row that may wrap |
| `.stack` | `display:flex; flex-direction:column` | a vertical list |
| `.flex-1` | `flex:1` | the child that takes the rest |

They carry **arrangement only** — no `gap`, `margin`, ink or box. A utility cannot silently reshape a page, and the component still owns the rhythm inside and around it. This is why `.row` does not set a gap: the rows that used `gap:0.5rem` get it from `.menu-category-picker`, `.new-category-row` and `.category-create-row`, while the off-scale gaps (0.35/0.4/0.6rem) stay visible as local declarations rather than being rounded.

### Declaration-for-declaration proof, not a visual guess

A temporary transformer rewrote the enclosing tags; a second script compared every inline record to its class set **declaration for declaration**. Tokens were resolved to px (`0.5rem` ≡ `var(--kb-space-2)` = 8px), and implicit flex defaults were added (`display:flex` without `align-items` means `stretch`), so a token substitution could not hide a value change. All **54/54** matched. This is a declaration proof, not a rendered screenshot: the new selectors carry Angular's `_ngcontent` attribute (0,2,0), so they still outrank the base element/class rules the inline block used to override; the global overrides were checked (`.modal-box h3`, `.field label`, `.modal-actions`, the button/input base classes).

The comparison caught two kinds of mistake that a visual pass would have missed:

- **four rows had no `align-items`** in the attribute (`new-category-row`, `category-create-row`, `cat-actions`, `cat-edit-row`). `.row` centers, but the original stretched; those four kept local `display:flex; align-items:stretch`. On the create rows that is a real difference — the button was matching the input's height.
- two elements already carried a class name (`cat-list-wrap`, `cat-list-row`), so the new rules attached to those names instead of adding another.

Off-scale values were preserved, not snapped: `font-size: 0.92rem` on the category name, `border-radius: 10px` on its row, and the 0.35/0.4/0.6rem gaps. The type check already read every inline `font-size`; the spacing check already read inline `gap`, `border-radius` and `padding`/`margin` shorthands, so the move did not raise either count. Consolidating duplicate inline copies lowered the totals (404 font-size declarations, 337 tokenised; 1,347 spacing parts, 649 tokenised). Longhands (`margin-top`, `padding-left`, …) remain outside `check:spacing` — that limitation is unchanged — but they are now in the stylesheet, where a future check can see them, instead of in an attribute no check reads.

### The guard — `npm run check:inline-styles`

`scripts/check-inline-styles.mjs` counts static `style="…"` attributes by walking tags, not by scanning raw text: a stylesheet comment that mentions the pattern is not an attribute, and a `>` inside `[disabled]="count > 0"` does not end the tag. `[style.prop]` bindings are not counted — they are a dynamic value, not a hand-written declaration block.

The budget is per file and it is a **true ratchet**:

```
staff-permissions  26/26     inventory  20/20     payment-methods  10/10
business-dashboard  6/6      businesses  3/3      live-orders       3/3
order-history       3/3      three files 2/2      three files       1/1
menu                0/0      (converted; any attribute fails twice: no budget + converted)
```

- a file with no budget may not contain one;
- a budgeted file may not exceed its count;
- a budget **above** the current count also fails (`lower the budget`), because slack is how a ratchet slips backwards;
- a converted page that still has a stale budget fails (`remove the budget and add it to CONVERTED`);
- a budgeted file that no longer exists fails (`remove or rename the entry`).

At the end of this batch, the remaining 80 attributes sat in 13 files; the next largest was `staff-permissions-modal.component.ts` (26).

### What was proven

```
menu page + one style attribute       caught  (no budget, and "converted page must stay at 0")
staff-permissions 26 → 27             caught  (over budget)
```

Both files were restored byte-identically afterwards (menu sha256 `660F1A94…`, staff `985E1C39…`).

### Verification

```
npm run check:inline-styles →  80 static attributes in 13 files; menu 0; every budget exact
npm run check:type          →  337/404 tokenised, 58 off-scale in 20 sizes (all budgeted)
npm run check:spacing       →  radius 206/281 (73%), gap 171/320 (53%),
                               padding 224/500 (45%), margin 48/246 (20%); overall 649/1347 (48%)
npm run check:breakpoints   →  26/39 on the ladder, 13 budgeted
npm run check:z-index       →  27 declarations: 18 on the ladder in 11 rungs, 9 local
npm run check:colors        →  12 channel twins agree, 0 white literals, 20 budgeted + 1 pinned
npm run check:tokens        →  no var() fallbacks
npm run check:motion        →  111 durations in 106 files in band
ng build --configuration development  →  exit 0
ng test --watch=false                 →  TOTAL: 274 SUCCESS
```

The 80 remaining attributes were the next batch; staff-permissions followed in §7j. The guard makes sure neither converted page can regrow one.

---

## 7j. Change log — staff permissions: 26 attributes, 24 rules

The staff permissions modal was the second-largest inline-style file: **26 `style="…"` attributes**, all in the permissions editor and the Save-as-Template dialog. They became **24 component rules** — 26 attributes map to 24 because the two row types share `.permissions-row` and the three template buttons share `.permissions-template-btn`. The file now has **zero** static style attributes.

Three centered rows use the shared `.row .row--between` utilities plus a local rule for padding and border; the rest are local. The 0.5/0.75/1/0.25rem values became `--kb-space-2/3/4/1`; the off-scale 0.6/0.4/0.3rem margins and paddings stayed literal.

### Proof and cascade

The same comparator as §7i resolved tokens to px and added implicit flex defaults: **26/26** records match declaration for declaration. Two cascade points needed an explicit order:

- `.role-disabled-note` already sets `margin-top: var(--kb-space-1)` (4px). The inline `margin:0 0 0.75rem` overrode it. `.permissions-fixed-note` sets the full `0 0 12px` and is declared after `.role-disabled-note`, so it still wins at equal specificity.
- `.modal-content` sets `max-width: 560px`. The inline `max-width:400px` overrode it. `.permissions-create-modal` is declared after `.modal-content`, so the 400px width survives.
- `.permissions-template-btn--dashed` follows `.permissions-template-btn`, and both follow the global `.ghost-btn`; the dashed variant keeps the border it had.

### The counts moved, and why

Three mechanical consolidations changed the guard totals; none is drift:

- type **404 → 402** (337 → 335 tokenised): the three template buttons all declared `font-size: var(--kb-text-sm)`; one `.permissions-template-btn` rule replaces three declarations. Off-scale stays **58 in 20 sizes**.
- padding **500 → 498**: the two identical `padding: 0.4rem 0` rows now share one rule; the off-scale padding budget falls **226 → 225**.
- margin **246 → 255** (48 → 51 tokenised): three inline `margin: 0 0 …` shorthands were previously skipped as non-CSS because the value ended at the attribute quote; in the stylesheet they are visible. This is the spacing check seeing more declarations, not new margin drift.

Colours are unchanged; `var(--line)` is still the border token, and no new literal entered the file.

### Guard proof

The file moved from a 26/26 budget to `CONVERTED`. Injecting one attribute into the converted page produced exactly the two expected failures — no budget, and "this page was converted and must stay at 0" — exit 1. Restored byte-identically afterwards (sha256 `4b0a46a2…`).

### Verification

```
npm run check:inline-styles →  54 static attributes in 12 files; 2 converted pages at 0
npm run check:type          →  335/402 tokenised, 58 off-scale in 20 sizes
npm run check:spacing       →  radius 206/281 (73%), gap 171/320 (53%),
                               padding 224/498 (45%), margin 51/255 (20%); overall 652/1354 (48%)
npm run check:breakpoints   →  26/39 on the ladder, 13 budgeted
npm run check:z-index       →  27 declarations: 18 on the ladder in 11 rungs, 9 local
npm run check:colors        →  12 channel twins agree, 0 white literals, 20 budgeted + 1 pinned
npm run check:tokens        →  no var() fallbacks
npm run check:motion        →  111 durations in 106 files in band
ng build --configuration development  →  exit 0
ng test --watch=false                 →  TOTAL: 274 SUCCESS
```

At the end of this batch, the remaining 54 attributes were in 12 files; inventory (20) was the next largest and followed in §7k.

---

## 7k. Change log — inventory: 20 attributes, 14 rules

The inventory page was the third inline-style file: **20 `style="…"` attributes** across the material cards, stock-movement history, physical-count form and material-detail modal. They became **14 new component rules** plus one existing rule reused; the file now has **zero** static style attributes.

Three empty-state paragraphs share `.inventory-empty-note`; the two movement timestamps share `.movement-time`; the two variance fields share `.variance-field`; the two detail numbers share `.material-detail-number`; the physical-count note reuses the existing `.inventory-note` because its declarations were identical. The PO line modifier, `.workflow-row--po-line`, is declared after `.workflow-row`; it restores the row's full width and left justification without restating the row's padding or border.

The 1rem and 0.5rem values became `--kb-space-4` and `--kb-space-2`; the off-scale 0.45rem toggle margin stayed literal. The `300px` and `520px` widths are unchanged.

### Proof and cascade

The comparator resolved tokens to px: **20/20** records match declaration for declaration, with no property added that the inline rule did not have. Four overrides depend on order:

- `.workflow-row--po-line` after `.workflow-row` (width/justify override the base row).
- `.variance-field` after `.form-group` (the `margin: 0` shorthand overrides `.form-group`'s 1rem bottom margin).
- `.material-detail-modal` after `.modal-content` (520px overrides 480px).
- `.detail-tabs` after `.tabs` (1rem overrides 1.25rem).

`.vendor-toggle` and `.material-delete-btn` are local classes that override the global `.ghost-btn` margin/colour; `.inventory-note` is reused unchanged.

### Guard proof

The file moved from a 20/20 budget to `CONVERTED`. Injecting one attribute produced exactly the two expected failures (no budget; converted page must stay at 0), exit 1. Restored byte-identically (sha256 `6411c5cc…`).

### Verification

```
npm run check:inline-styles →  34 static attributes in 11 files; 3 converted pages at 0
npm run check:type          →  333/400 tokenised, 58 off-scale in 20 sizes
npm run check:spacing       →  radius 206/281 (73%), gap 171/320 (53%),
                               padding 224/498 (45%), margin 50/251 (20%); overall 651/1350 (48%)
npm run check:breakpoints   →  26/39 on the ladder, 13 budgeted
npm run check:z-index       →  27 declarations: 18 on the ladder in 11 rungs, 9 local
npm run check:colors        →  12 channel twins agree, 0 white literals, 20 budgeted + 1 pinned
npm run check:tokens        →  no var() fallbacks
npm run check:motion        →  111 durations in 106 files in band
ng build --configuration development  →  exit 0
ng test --watch=false                 →  TOTAL: 274 SUCCESS
```

### The counts moved, and why

- type **402 → 400** (335 → 333 tokenised): `.movement-time` replaces two identical `font-size: var(--kb-text-sm)` declarations, and the physical-count note's sm size was already in `.inventory-note`, so removing its inline copy drops one more. Off-scale stays 58 in 20.
- margin **255 → 251** (51 → 50 tokenised): three identical inline `margin:0` empty-state paragraphs share one rule (−2 parts); the physical-count note's `margin: 0 0 var(--kb-space-4)` was already in `.inventory-note`, so its duplicate inline copy goes (−3 parts, −1 token); two inline `margin:0` variance fields that the checker had skipped at the attribute quote are now visible as one rule (+1 part).
- padding, radius, gap and colours unchanged. No new colour literal or off-scale type value entered the file.

At the end of this batch, the remaining 34 attributes were in 11 files; payment-methods (10) was the next largest and followed in §7l.

---

## 7l. Change log — payment methods: 10 attributes, 6 rules

The payment-methods section had **10 `style="…"` attributes**, all in the Easebuzz config: the error note, the sub-merchant card, its three label/value rows, and the onboarding notice. They became **6 new component rules** plus the shared `.row`/`.row--between` utilities; the file now has **zero** static style attributes.

The three labels share `.submerchant-label`; the two rows share `.row .row--between .submerchant-row`; the status row uses `.row .row--between` alone. `.submerchant-card` and `.submerchant-onboarding` carry their own padding/border/radius; `.submerchant-value` keeps the monospace family and md size.

The 1rem values became `--kb-space-4`; 0.85rem, 10px and 0.4rem are pre-existing off-scale values preserved exactly. `rgba(183,121,31,0.08)` is unchanged and still budgeted by the colour check.

### Proof and cascade

- 10/10 declaration-for-declaration (tokens resolved; the `.row` utilities included).
- `.submerchant-row` only adds the 0.4rem bottom margin; display, justify and centring come from `.row .row--between`, the same vocabulary as staff permissions. `.submerchant-card` and `.submerchant-onboarding` are new classes with no competing rule.
- `.config-error-note` only sets the 2px top margin; `.muted` still owns the colour.

### The 2px decision

The error note's `margin:2px 0 0` was the one declaration that could not move without a budget change: the check had always skipped it because the value ran into template text, and the margin `2px` budget was already full at 2/2 (the two `margin: 0 0 2px` declarations). With your approval the budget is now **3**, with a comment in `check-spacing.mjs`; the pixels are unchanged. The budget is still a hard cap: a temporary fourth `margin: 2px` failed with "used 4× but the budget allows 3×", then was restored byte-identically.

### Guard proof

Injecting one attribute into the converted page produced exactly the two expected failures (no budget; converted page must stay at 0), exit 1. Restored byte-identically (sha256 `ae096c70…`).

### Verification

```
npm run check:inline-styles →  24 static attributes in 10 files; 4 converted pages at 0
npm run check:type          →  333/400 tokenised, 58 off-scale in 20 sizes
npm run check:spacing       →  radius 206/281 (73%), gap 171/320 (53%),
                               padding 224/498 (45%), margin 50/254 (20%); overall 651/1353 (48%)
                               1 declaration skipped as non-CSS
npm run check:breakpoints   →  26/39 on the ladder, 13 budgeted
npm run check:z-index       →  27 declarations: 18 on the ladder in 11 rungs, 9 local
npm run check:colors        →  12 channel twins agree, 0 white literals, 20 budgeted + 1 pinned
npm run check:tokens        →  no var() fallbacks
npm run check:motion        →  111 durations in 106 files in band
ng build --configuration development  →  exit 0
ng test --watch=false                 →  TOTAL: 274 SUCCESS
```

### The counts moved, and why

- type **400 → 400** (333 tokenised): the three `font-size: var(--kb-text-md)` declarations moved one-for-one into `.submerchant-card`, `.submerchant-value` and `.submerchant-onboarding`; no consolidation, so the total and the 58 off-scale stay put.
- margin **251 → 254** (50 tokenised): the error note's 2px shorthand is now visible (+3 parts, no token); the other nine attributes were longhands or already-counted shorthands, so nothing else moved.
- padding, radius, gap and colours unchanged.

---

## 7m. Change log — the final 24 attributes, ten files

The last ten files held **24 `style="…"` attributes**: business-dashboard 6, businesses 3, live-orders 3, order-history 3, business-settings 2, password-change 2, platform-dashboard 2, login 1, reports 1, order-detail-modal 1. They became **21 component rules**, one shared helper (`.textarea-address`), and two existing utilities (`.row .row--wrap`); every one of the ten files is now at **zero** static style attributes.

- **Dashboard (6):** `.state-panel--offset`, `.skeleton-shimmer--header`, `.skeleton-shimmer--kpi`, `.skeleton-shimmer--intel` and `.bento-intel-grid--spaced`. The two 260px intel skeletons share the last modifier, so four inline `border-radius: var(--kb-radius-2xl)` declarations became three.
- **Businesses (3):** the action group uses `.row .row--wrap .businesses-actions` (the utilities supply flex, centring and wrapping; the local class only adds the 8px gap), the note becomes `.businesses-note`, and the address textarea reuses the new global `.textarea-address`.
- **Business settings (2):** `.logo-file-input` hides the file input; the address textarea uses the same `.textarea-address`.
- **Live orders and order history (3 each):** `.cancel-modal-title`, `.cancel-modal-note` and `.cancel-modal-body` are declared locally in both tabs. Keeping them local means the two tabs stay count-neutral and each component owns its dialog; the comments in both files say the three rules are twins.
- **Password change (2):** `.password-form` carries the 440px cap; `.password-otp-field` keeps the flex-end alignment without inheriting `.row`'s centre alignment.
- **Platform dashboard (2):** `.kpi-mini-value--success` preserves `var(--success)`; `.kpi-row--spaced` carries the loading row's top margin.
- **Login (1), reports (1), order-detail modal (1):** `.login-button--link`, `.rank-col` and `.order-modal__value--strong`.

`.textarea-address` lives in `bento.css` because the create-business and shop-settings address fields were byte-identical (`resize: vertical; min-height: 72px; font-family: inherit`). It is a shared form helper, not a page class, and consumes no spacing or colour token.

### Proof and cascade

- **24/24 declaration-for-declaration** before the transform, with tokens resolved to px and the `.row` utilities included.
- The dashboard's sixth attribute sat on the continuation line of a multi-line `<section>` tag. It was folded by hand — `state-panel--offset` joined the class list and the attribute line became `>` — preserving the line count so the other 23 mappings kept their coordinates.
- The transformer removed the other 23 attributes; a per-file count afterwards showed **0** in all ten files.
- `.businesses-actions` only adds `gap`; `.row` supplies `display:flex` and `align-items:center`; `.row--wrap` supplies `flex-wrap:wrap`. No competing rule sets those properties on the element.
- `.cancel-modal-title` is local, so its attribute selector outranks the global `.modal-box h3` margin; `.cancel-modal-note` adds margin and size while `.muted` still owns the colour. `.textarea-address` only adds `resize`, `min-height` and the inherited font family to `.field-control`.
- Every off-scale value is preserved exactly: 0.35rem, 60px, 140px, 260px, 440px, 60px, 72px. The 1rem/1.5rem/2rem values became `--kb-space-4/6/8`, and `var(--success)` is unchanged.

### Guard proof

`BUDGET` is now empty and all ten files moved to `CONVERTED`; the check prints 0 attributes, 14 converted pages and "Remaining debt: none." Injecting one attribute into `businesses-page.component.ts` produced exactly the two expected failures (no budget; converted page must stay at 0), exit 1. Restored byte-identically (sha256 `737c911d…`).

### Verification

```
npm run check:inline-styles →  0 static attributes in 0 files; 14 converted pages at 0
npm run check:type          →  333/400 tokenised, 58 off-scale in 20 sizes
npm run check:spacing       →  radius 205/280 (73%), gap 171/320 (53%),
                               padding 224/498 (45%), margin 50/254 (20%); overall 650/1352 (48%)
                               1 declaration skipped as non-CSS
npm run check:breakpoints   →  26/39 on the ladder, 13 budgeted
npm run check:z-index       →  27 declarations: 18 on the ladder in 11 rungs, 9 local
npm run check:colors        →  12 channel twins agree, 0 white literals, 20 budgeted + 1 pinned
npm run check:tokens        →  no var() fallbacks
npm run check:motion        →  111 durations in 106 files in band
ng build --configuration development  →  exit 0
ng test --watch=false                 →  TOTAL: 274 SUCCESS
```

### The counts moved, and why

- radius **206/281 → 205/280** (−1 part, −1 tokenised): the dashboard's four inline 2xl radii became three declarations because the two 260px skeletons share `.skeleton-shimmer--intel`. The 2xl value is already a token, so off-scale stays 39 in 12.
- type, margin, gap, padding and colours unchanged. The remaining attributes were longhands (`margin-top`, `margin-bottom`, `max-width`, `width`), non-spacing properties, or moved one-for-one; the duplicated cancel-dialog rules stay local in each tab, so their counted declarations do not consolidate.
- Overall **651/1353 → 650/1352**.

This closes the inline-style debt: **134 audit-time attributes → 0**, 14 pages converted, empty budget.

---

## 7n. Change log — max-width: 1680, one source

The audit found three values: `--kb-content-max-width: 1400px` (unused), `.page-shell { max-width: 1680px; }` (shipped literal), and freeze rule 1 `1440px`. The working tree had already collapsed the first two by making the shell consume the token at 1680. On 2026-10-25 the owner chose to keep **1680** and align the document, so no rendered pixel changed.

- `--kb-content-max-width: 1680px` remains the only declaration; `.page-shell` remains its only reader.
- Freeze rule 1 now says 1680px with a dated revision note; padding `1.75rem 2rem 3.5rem` and gap `1.5rem` are unchanged.
- The token comment no longer presents 1440 as an open alternative.
- No max-width check was added: the value is now one source and the doc agrees, but the number itself is still a design decision.

### Why 1680

| CSS viewport | Workspace (expanded rail) | 1680 content (gutter/side) | 1440 content (gutter/side) |
|---:|---:|---:|---:|
| 1440 | 1200 | 1136 (0) | 1136 (0) |
| 1680 | 1440 | 1376 (0) | 1376 (0) |
| 1920 | 1680 | **1616 (0)** | **1376 (120px)** |
| 2560 | 2320 | **1616 (320px)** | **1376 (440px)** |

CSS pixels, 240px expanded rail, 32px shell padding. With the expanded rail, the two values are identical at 1680 and below; the difference begins above it. A collapsed rail moves the threshold 176px left, so at 1920 collapsed 1680 leaves 88px/side while 1440 leaves 208px/side.

- 1680 is the only value that has rendered; 1400 was a dead token and 1440 was never code.
- The shared KPI rows are four tiles, so both widths keep four columns; tile width changes from roughly 334px to 394px.
- The widest tables get 240px more room before `.table-wrap` scrolls.
- Prose stays capped by the page-header's 75ch measure, so the wider shell does not create long text lines.
- At 2560, 1680 still leaves 320px gutters; the layout is not full-bleed at every size.

### Verification

```
npm run check:inline-styles →  0 static attributes in 0 files; 14 converted pages at 0
npm run check:type          →  333/400 tokenised; 58 off-scale in 20 sizes
npm run check:spacing       →  radius 205/280 (73%), gap 171/320 (53%),
                               padding 224/498 (45%), margin 50/254 (20%); overall 650/1352 (48%)
                               off-scale: radius 39 in 12, gap 144 in 24,
                               padding 225 in 41, margin 25 in 13; 1 declaration skipped
npm run check:breakpoints   →  26/39 on the ladder, 13 budgeted in 8 widths
npm run check:z-index       →  27 declarations: 18 on the ladder in 11 rungs, 9 local
npm run check:colors        →  12 channel twins agree, 0 white literals, 20 budgeted + 1 pinned
npm run check:tokens        →  no var() fallbacks
npm run check:motion        →  111 durations in 106 files in band
ng build --configuration development  →  exit 0
ng test --watch=false                 →  TOTAL: 274 SUCCESS
```

---

## 7o. Change log — card titles 16, sections 20

The audit's 15–19px band held **58 declarations in 20 sizes**: **21 rendered heading-like
declarations** and one dead settings rule, plus 24 values, icons, body copy or controls, and 12
declarations outside the band. On 2026-10-25 the owner chose a mixed hierarchy: **card, panel,
list and state titles use `--kb-text-lg` (16px); section and modal titles use `--kb-text-xl`
(20px)**. This uses
both roles already documented in the nine-step scale and avoids inventing an 18px step.

### Mapping

| Role | Step | Declarations | Before | Where |
|---|---|---:|---|---|
| Card, panel, list, empty/error, subsection | `--kb-text-lg` 16px | 14 (12 snap + 2 remove) | 0.92, 15px, 0.95, 1.05, 1.1, 1.12, 1.15 | Dashboard intel cards; platform focus/table cards; settings logo meta; inventory material names; reports note card; live-order codes; menu category names; error/empty states; order-modal “Line Items” |
| Section and modal | `--kb-text-xl` 20px | 7 | 1.05, 1.1, 1.15 | Settings section headers; terminals panel headers; Feature Flags section label; classic, tactile, order-detail and confirm modal titles |
| Dead rule | — | 1 remove | 1.05 | `.settings-section > h3` |

**Result: 19 declarations snapped, 3 removed.** The 16px group moves by 0.8–2.4px in both
directions: the 0.92/0.95/15px titles grow by 0.8–1.3px, while the 1.05–1.15 titles tighten by
0.8–2.4px. The 20px group grows by 1.6–3.2px. No live declaration changes weight, colour, spacing,
line-height or markup.

### Removed rather than remapped

- `.settings-section > h3` never matched: every settings `<h3>` is nested in `.section-title-wrap`.
- `.intel-card__title` local `1.1rem` was a duplicate of the shared `1.12rem`; the shared rule now
  owns the size and the local override keeps the dashboard's 700 weight.
- `api-state h3` local `0.95rem` duplicated the shared `.state-panel h3`; removing the local size
  lets the shared 16px step apply while its margin and colour stay local.

No palette, dependency, logic or live spacing change.

### Counts and budget

`check:type` moved **333/400 → 352/397**; off-scale **58 in 20 → 36 in 19**. The `1.12rem` entry is
gone. Budgets were lowered to current use: `0.95rem` 5, `1.05rem` 3, `1.1rem` 3, `0.92rem` 3,
`1.15rem` 2, `15px` 3. The remaining 36 are the 24 non-title band values (numbers, icons, body
copy, controls) plus 12 outside the band; they need separate calls.

The spacing check moved **650/1352 → 649/1349** only because the deleted `.settings-section > h3`
rule also carried `margin: 0 0 var(--kb-space-2)`. The spacing check counted that dead margin; no
live spacing changed.

### Verification

```
npm run check:inline-styles →  0 static attributes in 0 files; 14 converted pages at 0
npm run check:type          →  352/397 tokenised; 36 off-scale in 19 sizes
npm run check:spacing       →  radius 205/280 (73%), gap 171/320 (53%),
                               padding 224/498 (45%), margin 49/251 (20%); overall 649/1349 (48%)
                               off-scale: radius 39 in 12, gap 144 in 24,
                               padding 225 in 41, margin 25 in 13; 1 declaration skipped
npm run check:breakpoints   →  26/39 on the ladder, 13 budgeted in 8 widths
npm run check:z-index       →  27 declarations: 18 on the ladder in 11 rungs, 9 local
npm run check:colors        →  12 channel twins agree, 0 white literals, 20 budgeted + 1 pinned
npm run check:tokens        →  no var() fallbacks
npm run check:motion        →  111 durations in 106 files in band
ng build --configuration development  →  exit 0
ng test --watch=false                 →  TOTAL: 274 SUCCESS
```

The 20px group is the largest growth. A rendered pass should confirm that no modal header wraps at
that size; the source, build and suite are otherwise clean.

---

## 8. Reproduce

```powershell
cd apps/web-admin

# suite
npx ng test --watch=false

# type scale: adoption, budget and any new literal
npm run check:type

# var() fallbacks (a second palette)
npm run check:tokens

# breakpoints (one ladder, ratcheted drift)
npm run check:breakpoints

# spacing + radius (step literals, budget, retired --r-* aliases)
npm run check:spacing

# one z ladder (rung names, dead rungs, local stacking exception)
npm run check:z-index

# colour tokens (white literals, -rgb/-soft twins, per-value literal budget)
npm run check:colors

# static inline styles (per-file ratchet; converted pages stay at 0)
npm run check:inline-styles

# motion band
npm run check:motion

# raw font-size inventory (what the check reads)
Get-ChildItem src -Recurse -Include *.ts,*.css |
  Select-String 'font-size:\s*([^;}\n]+)' -AllMatches |
  % { $_.Matches } | % { $_.Groups[1].Value.Trim() } | Sort-Object -Unique

# duplicate selectors across the two global sheets (batch 1 removed the four
# known copies; this is the regression detector)
$s = Get-Content src/styles.css -Raw; $b = Get-Content src/styles/bento.css -Raw
Compare-Object `
  ([regex]::Matches($s,'(?m)^\s*([.#][\w-]+)[^{]*\{') | % { $_.Groups[1].Value }) `
  ([regex]::Matches($b,'(?m)^\s*([.#][\w-]+)[^{]*\{') | % { $_.Groups[1].Value }) `
  -IncludeEqual -ExcludeDifferent
```
