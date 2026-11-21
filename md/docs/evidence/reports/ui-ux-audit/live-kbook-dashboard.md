# UI & runtime audit report

- **Target:** https://kbook.iadv.cloud/business/dashboard
- **Viewports:** desktop, mobile
- **Design tokens:** not configured (token-drift rules skipped)
- **AI triage:** disabled (deterministic only)
- **Vision review:** disabled
- **Duration:** 12.5s
- **Generated:** 2026-10-20T16:45:02.878Z
- **Measured findings:** 18 listed, 0 rolled up, 18 total (gate-eligible)

## Summary

| Dimension | Breakdown |
| :--- | :--- |
| Severity | critical: 0 · high: 10 · medium: 3 · low: 5 |
| Category | a11y_contrast: 10 · layout_invariant: 8 |
| Viewport | desktop: 8 · mobile: 10 |

## Findings

Every value below was measured in the browser (computed style, bounding box, HTTP status).
Nothing here is model-generated. Lines marked `triage:` come from an optional classifier.

| ID | Sev | Viewport | Rule | Selector | Actual | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| desktop-1 | **high** | desktop | `aria-progressbar-name` | `.progress-meter-track` | Fix any of the following: aria-label attribute does not exist or is empty aria-labelledby attribute does not exist, references elements that do not exist or references elements that are empty Element has no title attribute | Ensure every ARIA progressbar node has an accessible name |
| mobile-9 | **high** | mobile | `aria-progressbar-name` | `.progress-meter-track` | Fix any of the following: aria-label attribute does not exist or is empty aria-labelledby attribute does not exist, references elements that do not exist or references elements that are empty Element has no title attribute | Ensure every ARIA progressbar node has an accessible name |
| desktop-2 | **high** | desktop | `color-contrast` | `.kpi-tag--amber` | Fix any of the following: Element has insufficient color contrast of 3.29 (foreground color: #b7791f, background color: #fbf3df, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| desktop-3 | **high** | desktop | `color-contrast` | `.kpi-tag--green` | Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| desktop-4 | **high** | desktop | `color-contrast` | `.bar-tick-label--current` | Fix any of the following: Element has insufficient color contrast of 3.73 (foreground color: #d2643a, background color: #ffffff, font size: 8.2pt (10.88px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-10 | **high** | mobile | `color-contrast` | `.kpi-tag--amber` | Fix any of the following: Element has insufficient color contrast of 3.29 (foreground color: #b7791f, background color: #fbf3df, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-11 | **high** | mobile | `color-contrast` | `.kpi-tag--green` | Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-12 | **high** | mobile | `color-contrast` | `.bar-tick-label--current` | Fix any of the following: Element has insufficient color contrast of 3.73 (foreground color: #d2643a, background color: #ffffff, font size: 8.2pt (10.88px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| desktop-5 | **high** | desktop | `label-content-name-mismatch` | `.header-pill-btn` | Fix any of the following: Text inside the element is not included in the accessible name | Ensure that elements labelled through their content must have their visible text as part of their accessible name |
| mobile-13 | **high** | mobile | `label-content-name-mismatch` | `.header-pill-btn` | Fix any of the following: Text inside the element is not included in the accessible name | Ensure that elements labelled through their content must have their visible text as part of their accessible name |
| mobile-16 | **medium** | mobile | `undersized-touch-target` | `section:nth-of-type(1) > article:nth-of-type(3) > div:nth-of-type(3) > button` | 88x18px | >= 24x24px on mobile (WCAG 2.5.8) |
| mobile-17 | **medium** | mobile | `undersized-touch-target` | `section:nth-of-type(1) > article:nth-of-type(4) > div:nth-of-type(3) > button` | 75x18px | >= 24x24px on mobile (WCAG 2.5.8) |
| mobile-18 | **medium** | mobile | `undersized-touch-target` | `section:nth-of-type(2) > article:nth-of-type(2) > div:nth-of-type(1) > button` | 70x19px | >= 24x24px on mobile (WCAG 2.5.8) |
| desktop-6 | **low** | desktop | `undersized-touch-target` | `section:nth-of-type(1) > article:nth-of-type(3) > div:nth-of-type(3) > button` | 88x18px | >= 24x24px on desktop (WCAG 2.5.8) |
| desktop-7 | **low** | desktop | `undersized-touch-target` | `section:nth-of-type(1) > article:nth-of-type(4) > div:nth-of-type(3) > button` | 75x18px | >= 24x24px on desktop (WCAG 2.5.8) |
| desktop-8 | **low** | desktop | `undersized-touch-target` | `section:nth-of-type(2) > article:nth-of-type(2) > div:nth-of-type(1) > button` | 70x19px | >= 24x24px on desktop (WCAG 2.5.8) |
| mobile-14 | **low** | mobile | `undersized-touch-target` | `button[aria-label="Toggle navigation menu"]` | 40x40px (icon-only)  | >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG) |
| mobile-15 | **low** | mobile | `undersized-touch-target` | `button[aria-label="Notifications"]` | 38x38px (icon-only)  | >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG) |

### HIGH (10)

- **desktop-1 · `aria-progressbar-name` · desktop**
  - selector: `.progress-meter-track`
  - actual: Fix any of the following: aria-label attribute does not exist or is empty aria-labelledby attribute does not exist, references elements that do not exist or references elements that are empty Element has no title attribute
  - expected: Ensure every ARIA progressbar node has an accessible name
- **mobile-9 · `aria-progressbar-name` · mobile**
  - selector: `.progress-meter-track`
  - actual: Fix any of the following: aria-label attribute does not exist or is empty aria-labelledby attribute does not exist, references elements that do not exist or references elements that are empty Element has no title attribute
  - expected: Ensure every ARIA progressbar node has an accessible name
- **desktop-2 · `color-contrast` · desktop**
  - selector: `.kpi-tag--amber`
  - actual: Fix any of the following: Element has insufficient color contrast of 3.29 (foreground color: #b7791f, background color: #fbf3df, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **desktop-3 · `color-contrast` · desktop**
  - selector: `.kpi-tag--green`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **desktop-4 · `color-contrast` · desktop**
  - selector: `.bar-tick-label--current`
  - actual: Fix any of the following: Element has insufficient color contrast of 3.73 (foreground color: #d2643a, background color: #ffffff, font size: 8.2pt (10.88px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-10 · `color-contrast` · mobile**
  - selector: `.kpi-tag--amber`
  - actual: Fix any of the following: Element has insufficient color contrast of 3.29 (foreground color: #b7791f, background color: #fbf3df, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-11 · `color-contrast` · mobile**
  - selector: `.kpi-tag--green`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-12 · `color-contrast` · mobile**
  - selector: `.bar-tick-label--current`
  - actual: Fix any of the following: Element has insufficient color contrast of 3.73 (foreground color: #d2643a, background color: #ffffff, font size: 8.2pt (10.88px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **desktop-5 · `label-content-name-mismatch` · desktop**
  - selector: `.header-pill-btn`
  - actual: Fix any of the following: Text inside the element is not included in the accessible name
  - expected: Ensure that elements labelled through their content must have their visible text as part of their accessible name
- **mobile-13 · `label-content-name-mismatch` · mobile**
  - selector: `.header-pill-btn`
  - actual: Fix any of the following: Text inside the element is not included in the accessible name
  - expected: Ensure that elements labelled through their content must have their visible text as part of their accessible name

### MEDIUM (3)

- **mobile-16 · `undersized-touch-target` · mobile**
  - selector: `section:nth-of-type(1) > article:nth-of-type(3) > div:nth-of-type(3) > button`
  - box: x=264 y=704 w=88 h=18
  - actual: 88x18px
  - expected: >= 24x24px on mobile (WCAG 2.5.8)
- **mobile-17 · `undersized-touch-target` · mobile**
  - selector: `section:nth-of-type(1) > article:nth-of-type(4) > div:nth-of-type(3) > button`
  - box: x=276 y=869 w=75 h=18
  - actual: 75x18px
  - expected: >= 24x24px on mobile (WCAG 2.5.8)
- **mobile-18 · `undersized-touch-target` · mobile**
  - selector: `section:nth-of-type(2) > article:nth-of-type(2) > div:nth-of-type(1) > button`
  - box: x=279 y=1252 w=70 h=19
  - actual: 70x19px
  - expected: >= 24x24px on mobile (WCAG 2.5.8)

### LOW (5)

- **desktop-6 · `undersized-touch-target` · desktop**
  - selector: `section:nth-of-type(1) > article:nth-of-type(3) > div:nth-of-type(3) > button`
  - box: x=1015 y=234 w=88 h=18
  - actual: 88x18px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **desktop-7 · `undersized-touch-target` · desktop**
  - selector: `section:nth-of-type(1) > article:nth-of-type(4) > div:nth-of-type(3) > button`
  - box: x=1310 y=234 w=75 h=18
  - actual: 75x18px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **desktop-8 · `undersized-touch-target` · desktop**
  - selector: `section:nth-of-type(2) > article:nth-of-type(2) > div:nth-of-type(1) > button`
  - box: x=1313 y=319 w=70 h=19
  - actual: 70x19px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **mobile-14 · `undersized-touch-target` · mobile**
  - selector: `button[aria-label="Toggle navigation menu"]`
  - box: x=16 y=12 w=40 h=40
  - actual: 40x40px (icon-only) 
  - expected: >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG)
- **mobile-15 · `undersized-touch-target` · mobile**
  - selector: `button[aria-label="Notifications"]`
  - box: x=158 y=191 w=38 h=38
  - actual: 38x38px (icon-only) 
  - expected: >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG)

## Fix prompt

Paste the block below into a coding agent:

```text
Fix the issues in this audit report for https://kbook.iadv.cloud/business/dashboard.
Rules:
1. Address findings in severity order (critical, high, medium, low).
2. Use the exact selector and measured value; do not guess markup.
3. Do not change unrelated styling or behaviour.
4. Re-run the audit afterwards and confirm the finding count drops.
```
