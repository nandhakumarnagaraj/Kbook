# UI & runtime audit report

- **Target:** https://kbook.iadv.cloud/business/staff
- **Viewports:** desktop, mobile
- **Design tokens:** not configured (token-drift rules skipped)
- **AI triage:** disabled (deterministic only)
- **Vision review:** disabled
- **Duration:** 12.2s
- **Generated:** 2026-10-20T16:45:51.493Z
- **Measured findings:** 7 listed, 0 rolled up, 7 total (gate-eligible)

## Summary

| Dimension | Breakdown |
| :--- | :--- |
| Severity | critical: 0 · high: 6 · medium: 0 · low: 1 |
| Category | a11y_contrast: 6 · layout_invariant: 1 |
| Viewport | desktop: 3 · mobile: 4 |

## Findings

Every value below was measured in the browser (computed style, bounding box, HTTP status).
Nothing here is model-generated. Lines marked `triage:` come from an optional classifier.

| ID | Sev | Viewport | Rule | Selector | Actual | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| desktop-1 | **high** | desktop | `color-contrast` | `tr:nth-child(1) > td:nth-child(5) > .status-indicator-pill.status-indicator-pill--active` | Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| desktop-2 | **high** | desktop | `color-contrast` | `tr:nth-child(2) > td:nth-child(5) > .status-indicator-pill.status-indicator-pill--active` | Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| desktop-3 | **high** | desktop | `color-contrast` | `tr:nth-child(3) > td:nth-child(5) > .status-indicator-pill.status-indicator-pill--active` | Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-4 | **high** | mobile | `color-contrast` | `article:nth-child(1) > .mobile-data-card__head > .status-indicator-pill.status-indicator-pill--active` | Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-5 | **high** | mobile | `color-contrast` | `article:nth-child(2) > .mobile-data-card__head > .status-indicator-pill.status-indicator-pill--active` | Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-6 | **high** | mobile | `color-contrast` | `article:nth-child(3) > .mobile-data-card__head > .status-indicator-pill.status-indicator-pill--active` | Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-7 | **low** | mobile | `undersized-touch-target` | `button[aria-label="Toggle navigation menu"]` | 40x40px (icon-only)  | >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG) |

### HIGH (6)

- **desktop-1 · `color-contrast` · desktop**
  - selector: `tr:nth-child(1) > td:nth-child(5) > .status-indicator-pill.status-indicator-pill--active`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **desktop-2 · `color-contrast` · desktop**
  - selector: `tr:nth-child(2) > td:nth-child(5) > .status-indicator-pill.status-indicator-pill--active`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **desktop-3 · `color-contrast` · desktop**
  - selector: `tr:nth-child(3) > td:nth-child(5) > .status-indicator-pill.status-indicator-pill--active`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-4 · `color-contrast` · mobile**
  - selector: `article:nth-child(1) > .mobile-data-card__head > .status-indicator-pill.status-indicator-pill--active`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-5 · `color-contrast` · mobile**
  - selector: `article:nth-child(2) > .mobile-data-card__head > .status-indicator-pill.status-indicator-pill--active`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-6 · `color-contrast` · mobile**
  - selector: `article:nth-child(3) > .mobile-data-card__head > .status-indicator-pill.status-indicator-pill--active`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.1 (foreground color: #2f855a, background color: #eef5f2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds

### LOW (1)

- **mobile-7 · `undersized-touch-target` · mobile**
  - selector: `button[aria-label="Toggle navigation menu"]`
  - box: x=16 y=12 w=40 h=40
  - actual: 40x40px (icon-only) 
  - expected: >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG)

## Fix prompt

Paste the block below into a coding agent:

```text
Fix the issues in this audit report for https://kbook.iadv.cloud/business/staff.
Rules:
1. Address findings in severity order (critical, high, medium, low).
2. Use the exact selector and measured value; do not guess markup.
3. Do not change unrelated styling or behaviour.
4. Re-run the audit afterwards and confirm the finding count drops.
```
