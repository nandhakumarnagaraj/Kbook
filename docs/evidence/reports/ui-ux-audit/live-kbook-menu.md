# UI & runtime audit report

- **Target:** https://kbook.iadv.cloud/business/menu
- **Viewports:** desktop, mobile
- **Design tokens:** not configured (token-drift rules skipped)
- **AI triage:** disabled (deterministic only)
- **Vision review:** disabled
- **Duration:** 15.1s
- **Generated:** 2026-10-20T16:45:35.673Z
- **Measured findings:** 16 listed, 15 rolled up, 31 total (gate-eligible)

## Summary

| Dimension | Breakdown |
| :--- | :--- |
| Severity | critical: 0 · high: 30 · medium: 0 · low: 1 |
| Category | a11y_contrast: 30 · layout_invariant: 1 |
| Viewport | desktop: 10 · mobile: 21 |
| Repetition | `color-contrast` on desktop: 10 occurrences, 5 listed · `color-contrast` on mobile: 10 occurrences, 5 listed · `label-content-name-mismatch` on mobile: 10 occurrences, 5 listed |

Counts above include rolled-up repetitions; the findings table lists each rule at most 5 times per viewport.

## Findings

Every value below was measured in the browser (computed style, bounding box, HTTP status).
Nothing here is model-generated. Lines marked `triage:` come from an optional classifier.

| ID | Sev | Viewport | Rule | Selector | Actual | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| desktop-1 | **high** | desktop | `color-contrast` | `tr:nth-child(1) > td:nth-child(6) > .chip.success` | Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| desktop-2 | **high** | desktop | `color-contrast` | `tr:nth-child(2) > td:nth-child(6) > .chip.success` | Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| desktop-3 | **high** | desktop | `color-contrast` | `tr:nth-child(3) > td:nth-child(6) > .chip.success` | Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| desktop-4 | **high** | desktop | `color-contrast` | `tr:nth-child(4) > td:nth-child(6) > .chip.success` | Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| desktop-5 | **high** | desktop | `color-contrast` | `tr:nth-child(5) > td:nth-child(6) > .chip.success` | Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-6 | **high** | mobile | `color-contrast` | `article:nth-child(1) > .mobile-data-card__head > .chip.success` | Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-7 | **high** | mobile | `color-contrast` | `article:nth-child(2) > .mobile-data-card__head > .chip.success` | Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-8 | **high** | mobile | `color-contrast` | `article:nth-child(3) > .mobile-data-card__head > .chip.success` | Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-9 | **high** | mobile | `color-contrast` | `article:nth-child(4) > .mobile-data-card__head > .chip.success` | Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-10 | **high** | mobile | `color-contrast` | `article:nth-child(5) > .mobile-data-card__head > .chip.success` | Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-11 | **high** | mobile | `label-content-name-mismatch` | `.mobile-data-card__actions > .fav-btn--on.fav-btn[aria-label="Remove Tester from favourites"]` | Fix any of the following: Text inside the element is not included in the accessible name | Ensure that elements labelled through their content must have their visible text as part of their accessible name |
| mobile-12 | **high** | mobile | `label-content-name-mismatch` | `.mobile-data-card__actions > .fav-btn[aria-label="Mark TestLiveSite as favourite"][type="button"]` | Fix any of the following: Text inside the element is not included in the accessible name | Ensure that elements labelled through their content must have their visible text as part of their accessible name |
| mobile-13 | **high** | mobile | `label-content-name-mismatch` | `article:nth-child(3) > .mobile-data-card__actions > .fav-btn.ghost-btn[type="button"]` | Fix any of the following: Text inside the element is not included in the accessible name | Ensure that elements labelled through their content must have their visible text as part of their accessible name |
| mobile-14 | **high** | mobile | `label-content-name-mismatch` | `article:nth-child(4) > .mobile-data-card__actions > .fav-btn.ghost-btn[type="button"]` | Fix any of the following: Text inside the element is not included in the accessible name | Ensure that elements labelled through their content must have their visible text as part of their accessible name |
| mobile-15 | **high** | mobile | `label-content-name-mismatch` | `.mobile-data-card__actions > .fav-btn[aria-label="Mark 409loop as favourite"][type="button"]` | Fix any of the following: Text inside the element is not included in the accessible name | Ensure that elements labelled through their content must have their visible text as part of their accessible name |
| mobile-16 | **low** | mobile | `undersized-touch-target` | `button[aria-label="Toggle navigation menu"]` | 40x40px (icon-only)  | >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG) |

## Repetition

One root cause hits many elements. The list above keeps the first 5 per rule per viewport; the rest are counted here rather than dropped.

| Viewport | Rule | Occurrences | Listed |
| :--- | :--- | :--- | :--- |
| desktop | `color-contrast` | 10 | 5 |
| mobile | `color-contrast` | 10 | 5 |
| mobile | `label-content-name-mismatch` | 10 | 5 |

### HIGH (15)

- **desktop-1 · `color-contrast` · desktop**
  - selector: `tr:nth-child(1) > td:nth-child(6) > .chip.success`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **desktop-2 · `color-contrast` · desktop**
  - selector: `tr:nth-child(2) > td:nth-child(6) > .chip.success`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **desktop-3 · `color-contrast` · desktop**
  - selector: `tr:nth-child(3) > td:nth-child(6) > .chip.success`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **desktop-4 · `color-contrast` · desktop**
  - selector: `tr:nth-child(4) > td:nth-child(6) > .chip.success`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **desktop-5 · `color-contrast` · desktop**
  - selector: `tr:nth-child(5) > td:nth-child(6) > .chip.success`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-6 · `color-contrast` · mobile**
  - selector: `article:nth-child(1) > .mobile-data-card__head > .chip.success`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-7 · `color-contrast` · mobile**
  - selector: `article:nth-child(2) > .mobile-data-card__head > .chip.success`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-8 · `color-contrast` · mobile**
  - selector: `article:nth-child(3) > .mobile-data-card__head > .chip.success`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-9 · `color-contrast` · mobile**
  - selector: `article:nth-child(4) > .mobile-data-card__head > .chip.success`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-10 · `color-contrast` · mobile**
  - selector: `article:nth-child(5) > .mobile-data-card__head > .chip.success`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-11 · `label-content-name-mismatch` · mobile**
  - selector: `.mobile-data-card__actions > .fav-btn--on.fav-btn[aria-label="Remove Tester from favourites"]`
  - actual: Fix any of the following: Text inside the element is not included in the accessible name
  - expected: Ensure that elements labelled through their content must have their visible text as part of their accessible name
- **mobile-12 · `label-content-name-mismatch` · mobile**
  - selector: `.mobile-data-card__actions > .fav-btn[aria-label="Mark TestLiveSite as favourite"][type="button"]`
  - actual: Fix any of the following: Text inside the element is not included in the accessible name
  - expected: Ensure that elements labelled through their content must have their visible text as part of their accessible name
- **mobile-13 · `label-content-name-mismatch` · mobile**
  - selector: `article:nth-child(3) > .mobile-data-card__actions > .fav-btn.ghost-btn[type="button"]`
  - actual: Fix any of the following: Text inside the element is not included in the accessible name
  - expected: Ensure that elements labelled through their content must have their visible text as part of their accessible name
- **mobile-14 · `label-content-name-mismatch` · mobile**
  - selector: `article:nth-child(4) > .mobile-data-card__actions > .fav-btn.ghost-btn[type="button"]`
  - actual: Fix any of the following: Text inside the element is not included in the accessible name
  - expected: Ensure that elements labelled through their content must have their visible text as part of their accessible name
- **mobile-15 · `label-content-name-mismatch` · mobile**
  - selector: `.mobile-data-card__actions > .fav-btn[aria-label="Mark 409loop as favourite"][type="button"]`
  - actual: Fix any of the following: Text inside the element is not included in the accessible name
  - expected: Ensure that elements labelled through their content must have their visible text as part of their accessible name

### LOW (1)

- **mobile-16 · `undersized-touch-target` · mobile**
  - selector: `button[aria-label="Toggle navigation menu"]`
  - box: x=16 y=12 w=40 h=40
  - actual: 40x40px (icon-only) 
  - expected: >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG)

## Fix prompt

Paste the block below into a coding agent:

```text
Fix the issues in this audit report for https://kbook.iadv.cloud/business/menu.
Rules:
1. Address findings in severity order (critical, high, medium, low).
2. Use the exact selector and measured value; do not guess markup.
3. Do not change unrelated styling or behaviour.
4. Re-run the audit afterwards and confirm the finding count drops.
```
