# UI & runtime audit report

- **Target:** https://kbook.iadv.cloud/business/daily-closing
- **Viewports:** desktop, mobile
- **Design tokens:** not configured (token-drift rules skipped)
- **AI triage:** disabled (deterministic only)
- **Vision review:** disabled
- **Duration:** 10.7s
- **Generated:** 2026-10-20T16:46:20.350Z
- **Measured findings:** 9 listed, 0 rolled up, 9 total (gate-eligible)

## Summary

| Dimension | Breakdown |
| :--- | :--- |
| Severity | critical: 0 · high: 6 · medium: 0 · low: 3 |
| Category | a11y_contrast: 6 · layout_invariant: 3 |
| Viewport | desktop: 3 · mobile: 6 |

## Findings

Every value below was measured in the browser (computed style, bounding box, HTTP status).
Nothing here is model-generated. Lines marked `triage:` come from an optional classifier.

| ID | Sev | Viewport | Rule | Selector | Actual | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| desktop-1 | **high** | desktop | `color-contrast` | `.kpi-tag` | Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| desktop-2 | **high** | desktop | `color-contrast` | `.bento-kpi--hero > .bento-kpi__label` | Fix any of the following: Element has insufficient color contrast of 4.46 (foreground color: #d9e8f7, background color: #0066cc, font size: 9.4pt (12.48px), font weight: normal). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| desktop-3 | **high** | desktop | `color-contrast` | `.bento-kpi--hero > .bento-kpi__sub` | Fix any of the following: Element has insufficient color contrast of 4.46 (foreground color: #d9e8f7, background color: #0066cc, font size: 9.1pt (12.16px), font weight: normal). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-4 | **high** | mobile | `color-contrast` | `.kpi-tag` | Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-5 | **high** | mobile | `color-contrast` | `.bento-kpi--hero > .bento-kpi__label` | Fix any of the following: Element has insufficient color contrast of 4.46 (foreground color: #d9e8f7, background color: #0066cc, font size: 9.4pt (12.48px), font weight: normal). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-6 | **high** | mobile | `color-contrast` | `.bento-kpi--hero > .bento-kpi__sub` | Fix any of the following: Element has insufficient color contrast of 4.46 (foreground color: #d9e8f7, background color: #0066cc, font size: 9.1pt (12.16px), font weight: normal). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-7 | **low** | mobile | `undersized-touch-target` | `button[aria-label="Toggle navigation menu"]` | 40x40px (icon-only)  | >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG) |
| mobile-8 | **low** | mobile | `undersized-touch-target` | `input[aria-label="Closing date"]` | 129x36px (icon-only)  | >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG) |
| mobile-9 | **low** | mobile | `undersized-touch-target` | `button[aria-label="Download daily closing CSV"]` | 38x38px (icon-only)  | >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG) |

### HIGH (6)

- **desktop-1 · `color-contrast` · desktop**
  - selector: `.kpi-tag`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **desktop-2 · `color-contrast` · desktop**
  - selector: `.bento-kpi--hero > .bento-kpi__label`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.46 (foreground color: #d9e8f7, background color: #0066cc, font size: 9.4pt (12.48px), font weight: normal). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **desktop-3 · `color-contrast` · desktop**
  - selector: `.bento-kpi--hero > .bento-kpi__sub`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.46 (foreground color: #d9e8f7, background color: #0066cc, font size: 9.1pt (12.16px), font weight: normal). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-4 · `color-contrast` · mobile**
  - selector: `.kpi-tag`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-5 · `color-contrast` · mobile**
  - selector: `.bento-kpi--hero > .bento-kpi__label`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.46 (foreground color: #d9e8f7, background color: #0066cc, font size: 9.4pt (12.48px), font weight: normal). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-6 · `color-contrast` · mobile**
  - selector: `.bento-kpi--hero > .bento-kpi__sub`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.46 (foreground color: #d9e8f7, background color: #0066cc, font size: 9.1pt (12.16px), font weight: normal). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds

### LOW (3)

- **mobile-7 · `undersized-touch-target` · mobile**
  - selector: `button[aria-label="Toggle navigation menu"]`
  - box: x=16 y=12 w=40 h=40
  - actual: 40x40px (icon-only) 
  - expected: >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG)
- **mobile-8 · `undersized-touch-target` · mobile**
  - selector: `input[aria-label="Closing date"]`
  - box: x=16 y=223 w=129 h=36
  - actual: 129x36px (icon-only) 
  - expected: >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG)
- **mobile-9 · `undersized-touch-target` · mobile**
  - selector: `button[aria-label="Download daily closing CSV"]`
  - box: x=153 y=222 w=38 h=38
  - actual: 38x38px (icon-only) 
  - expected: >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG)

## Fix prompt

Paste the block below into a coding agent:

```text
Fix the issues in this audit report for https://kbook.iadv.cloud/business/daily-closing.
Rules:
1. Address findings in severity order (critical, high, medium, low).
2. Use the exact selector and measured value; do not guess markup.
3. Do not change unrelated styling or behaviour.
4. Re-run the audit afterwards and confirm the finding count drops.
```
