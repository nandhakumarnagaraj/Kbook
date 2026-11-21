# UI & runtime audit report

- **Target:** https://khanabook.in/
- **Viewports:** desktop, mobile
- **Design tokens:** not configured (token-drift rules skipped)
- **AI triage:** disabled (deterministic only)
- **Vision review:** disabled
- **Duration:** 27.9s
- **Generated:** 2026-10-20T16:38:27.839Z
- **Measured findings:** 13 listed, 4 rolled up, 17 total (gate-eligible)

## Summary

| Dimension | Breakdown |
| :--- | :--- |
| Severity | critical: 0 · high: 3 · medium: 0 · low: 14 |
| Category | a11y_contrast: 3 · layout_invariant: 14 |
| Viewport | desktop: 6 · mobile: 11 |
| Repetition | `undersized-touch-target` on mobile: 9 occurrences, 5 listed |

Counts above include rolled-up repetitions; the findings table lists each rule at most 5 times per viewport.

## Findings

Every value below was measured in the browser (computed style, bounding box, HTTP status).
Nothing here is model-generated. Lines marked `triage:` come from an optional classifier.

| ID | Sev | Viewport | Rule | Selector | Actual | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| desktop-1 | **high** | desktop | `color-contrast` | `.bg-\[\#F0142D\]` | Fix any of the following: Element has insufficient color contrast of 4.33 (foreground color: #ffffff, background color: #f0142d, font size: 12.0pt (16px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-7 | **high** | mobile | `color-contrast` | `.bg-\[\#F0142D\]` | Fix any of the following: Element has insufficient color contrast of 4.33 (foreground color: #ffffff, background color: #f0142d, font size: 12.0pt (16px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-8 | **high** | mobile | `scrollable-region-focusable` | `.overflow-x-auto.shadow-2xl.border-border\/70` | Fix any of the following: Element should have focusable content Element should be focusable | Ensure elements that have scrollable content are accessible by keyboard in Safari |
| desktop-2 | **low** | desktop | `undersized-touch-target` | `button[aria-label="Go to slide Live Counter & Tables"]` | 16x32px | >= 24x24px on desktop (WCAG 2.5.8) |
| desktop-3 | **low** | desktop | `undersized-touch-target` | `button[aria-label="Go to slide High-Speed Billing"]` | 16x16px | >= 24x24px on desktop (WCAG 2.5.8) |
| desktop-4 | **low** | desktop | `undersized-touch-target` | `button[aria-label="Go to slide Multi-Mode Settlement"]` | 16x16px | >= 24x24px on desktop (WCAG 2.5.8) |
| desktop-5 | **low** | desktop | `undersized-touch-target` | `button[aria-label="Go to slide Shift & Sales Reports"]` | 16x16px | >= 24x24px on desktop (WCAG 2.5.8) |
| desktop-6 | **low** | desktop | `undersized-touch-target` | `button[aria-label="Go to slide Live Menu Control"]` | 16x16px | >= 24x24px on desktop (WCAG 2.5.8) |
| mobile-9 | **low** | mobile | `undersized-touch-target` | `button[aria-label="Open navigation"]` | 40x40px (icon-only)  | >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG) |
| mobile-10 | **low** | mobile | `undersized-touch-target` | `button[aria-label="Previous customer story"]` | 40x40px (icon-only)  | >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG) |
| mobile-11 | **low** | mobile | `undersized-touch-target` | `button[aria-label="Pause customer stories carousel"]` | 40x40px (icon-only)  | >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG) |
| mobile-12 | **low** | mobile | `undersized-touch-target` | `button[aria-label="Show story 1"]` | 28x28px (icon-only)  | >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG) |
| mobile-13 | **low** | mobile | `undersized-touch-target` | `button[aria-label="Show story 2"]` | 28x28px (icon-only)  | >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG) |

## Repetition

One root cause hits many elements. The list above keeps the first 5 per rule per viewport; the rest are counted here rather than dropped.

| Viewport | Rule | Occurrences | Listed |
| :--- | :--- | :--- | :--- |
| mobile | `undersized-touch-target` | 9 | 5 |

### HIGH (3)

- **desktop-1 · `color-contrast` · desktop**
  - selector: `.bg-\[\#F0142D\]`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.33 (foreground color: #ffffff, background color: #f0142d, font size: 12.0pt (16px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-7 · `color-contrast` · mobile**
  - selector: `.bg-\[\#F0142D\]`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.33 (foreground color: #ffffff, background color: #f0142d, font size: 12.0pt (16px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-8 · `scrollable-region-focusable` · mobile**
  - selector: `.overflow-x-auto.shadow-2xl.border-border\/70`
  - actual: Fix any of the following: Element should have focusable content Element should be focusable
  - expected: Ensure elements that have scrollable content are accessible by keyboard in Safari

### LOW (10)

- **desktop-2 · `undersized-touch-target` · desktop**
  - selector: `button[aria-label="Go to slide Live Counter & Tables"]`
  - box: x=1304 y=373 w=16 h=32
  - actual: 16x32px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **desktop-3 · `undersized-touch-target` · desktop**
  - selector: `button[aria-label="Go to slide High-Speed Billing"]`
  - box: x=1304 y=417 w=16 h=16
  - actual: 16x16px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **desktop-4 · `undersized-touch-target` · desktop**
  - selector: `button[aria-label="Go to slide Multi-Mode Settlement"]`
  - box: x=1304 y=445 w=16 h=16
  - actual: 16x16px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **desktop-5 · `undersized-touch-target` · desktop**
  - selector: `button[aria-label="Go to slide Shift & Sales Reports"]`
  - box: x=1304 y=473 w=16 h=16
  - actual: 16x16px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **desktop-6 · `undersized-touch-target` · desktop**
  - selector: `button[aria-label="Go to slide Live Menu Control"]`
  - box: x=1304 y=501 w=16 h=16
  - actual: 16x16px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **mobile-9 · `undersized-touch-target` · mobile**
  - selector: `button[aria-label="Open navigation"]`
  - box: x=338 y=20 w=40 h=40
  - actual: 40x40px (icon-only) 
  - expected: >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG)
- **mobile-10 · `undersized-touch-target` · mobile**
  - selector: `button[aria-label="Previous customer story"]`
  - box: x=43 y=13837 w=40 h=40
  - actual: 40x40px (icon-only) 
  - expected: >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG)
- **mobile-11 · `undersized-touch-target` · mobile**
  - selector: `button[aria-label="Pause customer stories carousel"]`
  - box: x=99 y=13837 w=40 h=40
  - actual: 40x40px (icon-only) 
  - expected: >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG)
- **mobile-12 · `undersized-touch-target` · mobile**
  - selector: `button[aria-label="Show story 1"]`
  - box: x=155 y=13843 w=28 h=28
  - actual: 28x28px (icon-only) 
  - expected: >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG)
- **mobile-13 · `undersized-touch-target` · mobile**
  - selector: `button[aria-label="Show story 2"]`
  - box: x=191 y=13843 w=28 h=28
  - actual: 28x28px (icon-only) 
  - expected: >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG)

## Fix prompt

Paste the block below into a coding agent:

```text
Fix the issues in this audit report for https://khanabook.in/.
Rules:
1. Address findings in severity order (critical, high, medium, low).
2. Use the exact selector and measured value; do not guess markup.
3. Do not change unrelated styling or behaviour.
4. Re-run the audit afterwards and confirm the finding count drops.
```
