# UI & runtime audit report

- **Target:** https://kbook.iadv.cloud/business/terminals
- **Viewports:** desktop, mobile
- **Design tokens:** not configured (token-drift rules skipped)
- **AI triage:** disabled (deterministic only)
- **Vision review:** disabled
- **Duration:** 10.7s
- **Generated:** 2026-10-20T16:46:05.852Z
- **Measured findings:** 18 listed, 3 rolled up, 21 total (gate-eligible)

## Summary

| Dimension | Breakdown |
| :--- | :--- |
| Severity | critical: 0 · high: 8 · medium: 6 · low: 7 |
| Category | a11y_contrast: 8 · layout_invariant: 13 |
| Viewport | desktop: 10 · mobile: 11 |
| Repetition | `undersized-touch-target` on desktop: 6 occurrences, 5 listed · `undersized-touch-target` on mobile: 7 occurrences, 5 listed |

Counts above include rolled-up repetitions; the findings table lists each rule at most 5 times per viewport.

## Findings

Every value below was measured in the browser (computed style, bounding box, HTTP status).
Nothing here is model-generated. Lines marked `triage:` come from an optional classifier.

| ID | Sev | Viewport | Rule | Selector | Actual | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| desktop-1 | **high** | desktop | `color-contrast` | `.kpi-tag` | Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| desktop-2 | **high** | desktop | `color-contrast` | `.fleet-limit-banner > span` | Fix any of the following: Element has insufficient color contrast of 3.14 (foreground color: #b7791f, background color: #f1eeeb, font size: 9.8pt (13.12px), font weight: normal). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| desktop-3 | **high** | desktop | `color-contrast` | `tr:nth-child(3) > td:nth-child(5) > .status-indicator-pill--active.status-indicator-pill` | Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| desktop-4 | **high** | desktop | `color-contrast` | `tr:nth-child(5) > td:nth-child(5) > .status-indicator-pill--active.status-indicator-pill` | Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-10 | **high** | mobile | `color-contrast` | `.kpi-tag` | Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-11 | **high** | mobile | `color-contrast` | `.fleet-limit-banner > span` | Fix any of the following: Element has insufficient color contrast of 3.14 (foreground color: #b7791f, background color: #f1eeeb, font size: 9.8pt (13.12px), font weight: normal). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-12 | **high** | mobile | `color-contrast` | `.mobile-data-card:nth-child(3) > .mobile-data-card__head > .status-indicator-pill--active.status-indicator-pill` | Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-13 | **high** | mobile | `color-contrast` | `.mobile-data-card:nth-child(5) > .mobile-data-card__head > .status-indicator-pill--active.status-indicator-pill` | Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-15 | **medium** | mobile | `undersized-touch-target` | `article:nth-of-type(1) > p > span > button` | 14x14px | >= 24x24px on mobile (WCAG 2.5.8) |
| mobile-16 | **medium** | mobile | `undersized-touch-target` | `article:nth-of-type(2) > p > span > button` | 14x14px | >= 24x24px on mobile (WCAG 2.5.8) |
| mobile-17 | **medium** | mobile | `undersized-touch-target` | `article:nth-of-type(3) > p > span > button` | 14x14px | >= 24x24px on mobile (WCAG 2.5.8) |
| mobile-18 | **medium** | mobile | `undersized-touch-target` | `article:nth-of-type(4) > p > span > button` | 14x14px | >= 24x24px on mobile (WCAG 2.5.8) |
| desktop-5 | **low** | desktop | `undersized-touch-target` | `tr:nth-of-type(1) > td:nth-of-type(4) > span > button` | 12x12px | >= 24x24px on desktop (WCAG 2.5.8) |
| desktop-6 | **low** | desktop | `undersized-touch-target` | `tr:nth-of-type(2) > td:nth-of-type(4) > span > button` | 12x12px | >= 24x24px on desktop (WCAG 2.5.8) |
| desktop-7 | **low** | desktop | `undersized-touch-target` | `tr:nth-of-type(3) > td:nth-of-type(4) > span > button` | 12x12px | >= 24x24px on desktop (WCAG 2.5.8) |
| desktop-8 | **low** | desktop | `undersized-touch-target` | `tr:nth-of-type(4) > td:nth-of-type(4) > span > button` | 12x12px | >= 24x24px on desktop (WCAG 2.5.8) |
| desktop-9 | **low** | desktop | `undersized-touch-target` | `tr:nth-of-type(5) > td:nth-of-type(4) > span > button` | 12x12px | >= 24x24px on desktop (WCAG 2.5.8) |
| mobile-14 | **low** | mobile | `undersized-touch-target` | `button[aria-label="Toggle navigation menu"]` | 40x40px (icon-only)  | >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG) |

## Repetition

One root cause hits many elements. The list above keeps the first 5 per rule per viewport; the rest are counted here rather than dropped.

| Viewport | Rule | Occurrences | Listed |
| :--- | :--- | :--- | :--- |
| desktop | `undersized-touch-target` | 6 | 5 |
| mobile | `undersized-touch-target` | 7 | 5 |

### HIGH (8)

- **desktop-1 · `color-contrast` · desktop**
  - selector: `.kpi-tag`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **desktop-2 · `color-contrast` · desktop**
  - selector: `.fleet-limit-banner > span`
  - actual: Fix any of the following: Element has insufficient color contrast of 3.14 (foreground color: #b7791f, background color: #f1eeeb, font size: 9.8pt (13.12px), font weight: normal). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **desktop-3 · `color-contrast` · desktop**
  - selector: `tr:nth-child(3) > td:nth-child(5) > .status-indicator-pill--active.status-indicator-pill`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **desktop-4 · `color-contrast` · desktop**
  - selector: `tr:nth-child(5) > td:nth-child(5) > .status-indicator-pill--active.status-indicator-pill`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-10 · `color-contrast` · mobile**
  - selector: `.kpi-tag`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-11 · `color-contrast` · mobile**
  - selector: `.fleet-limit-banner > span`
  - actual: Fix any of the following: Element has insufficient color contrast of 3.14 (foreground color: #b7791f, background color: #f1eeeb, font size: 9.8pt (13.12px), font weight: normal). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-12 · `color-contrast` · mobile**
  - selector: `.mobile-data-card:nth-child(3) > .mobile-data-card__head > .status-indicator-pill--active.status-indicator-pill`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-13 · `color-contrast` · mobile**
  - selector: `.mobile-data-card:nth-child(5) > .mobile-data-card__head > .status-indicator-pill--active.status-indicator-pill`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds

### MEDIUM (4)

- **mobile-15 · `undersized-touch-target` · mobile**
  - selector: `article:nth-of-type(1) > p > span > button`
  - box: x=95 y=857 w=14 h=14
  - actual: 14x14px
  - expected: >= 24x24px on mobile (WCAG 2.5.8)
- **mobile-16 · `undersized-touch-target` · mobile**
  - selector: `article:nth-of-type(2) > p > span > button`
  - box: x=186 y=1140 w=14 h=14
  - actual: 14x14px
  - expected: >= 24x24px on mobile (WCAG 2.5.8)
- **mobile-17 · `undersized-touch-target` · mobile**
  - selector: `article:nth-of-type(3) > p > span > button`
  - box: x=187 y=1423 w=14 h=14
  - actual: 14x14px
  - expected: >= 24x24px on mobile (WCAG 2.5.8)
- **mobile-18 · `undersized-touch-target` · mobile**
  - selector: `article:nth-of-type(4) > p > span > button`
  - box: x=186 y=1685 w=14 h=14
  - actual: 14x14px
  - expected: >= 24x24px on mobile (WCAG 2.5.8)

### LOW (6)

- **desktop-5 · `undersized-touch-target` · desktop**
  - selector: `tr:nth-of-type(1) > td:nth-of-type(4) > span > button`
  - box: x=697 y=471 w=12 h=12
  - actual: 12x12px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **desktop-6 · `undersized-touch-target` · desktop**
  - selector: `tr:nth-of-type(2) > td:nth-of-type(4) > span > button`
  - box: x=789 y=528 w=12 h=12
  - actual: 12x12px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **desktop-7 · `undersized-touch-target` · desktop**
  - selector: `tr:nth-of-type(3) > td:nth-of-type(4) > span > button`
  - box: x=789 y=586 w=12 h=12
  - actual: 12x12px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **desktop-8 · `undersized-touch-target` · desktop**
  - selector: `tr:nth-of-type(4) > td:nth-of-type(4) > span > button`
  - box: x=789 y=643 w=12 h=12
  - actual: 12x12px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **desktop-9 · `undersized-touch-target` · desktop**
  - selector: `tr:nth-of-type(5) > td:nth-of-type(4) > span > button`
  - box: x=789 y=700 w=12 h=12
  - actual: 12x12px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **mobile-14 · `undersized-touch-target` · mobile**
  - selector: `button[aria-label="Toggle navigation menu"]`
  - box: x=16 y=12 w=40 h=40
  - actual: 40x40px (icon-only) 
  - expected: >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG)

## Fix prompt

Paste the block below into a coding agent:

```text
Fix the issues in this audit report for https://kbook.iadv.cloud/business/terminals.
Rules:
1. Address findings in severity order (critical, high, medium, low).
2. Use the exact selector and measured value; do not guess markup.
3. Do not change unrelated styling or behaviour.
4. Re-run the audit afterwards and confirm the finding count drops.
```
