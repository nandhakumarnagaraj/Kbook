# UI & runtime audit report

- **Target:** https://kbook.iadv.cloud/business/settings
- **Viewports:** desktop, mobile
- **Design tokens:** not configured (token-drift rules skipped)
- **AI triage:** disabled (deterministic only)
- **Vision review:** disabled
- **Duration:** 11.6s
- **Generated:** 2026-10-20T16:46:55.038Z
- **Measured findings:** 22 listed, 1 rolled up, 23 total (gate-eligible)

## Summary

| Dimension | Breakdown |
| :--- | :--- |
| Severity | critical: 2 · high: 10 · medium: 5 · low: 6 |
| Category | a11y_contrast: 12 · layout_invariant: 11 |
| Viewport | desktop: 11 · mobile: 12 |
| Repetition | `undersized-touch-target` on mobile: 6 occurrences, 5 listed |

Counts above include rolled-up repetitions; the findings table lists each rule at most 5 times per viewport.

## Findings

Every value below was measured in the browser (computed style, bounding box, HTTP status).
Nothing here is model-generated. Lines marked `triage:` come from an optional classifier.

| ID | Sev | Viewport | Rule | Selector | Actual | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| desktop-6 | **critical** | desktop | `label` | `input[type="number"]` | Fix any of the following: Element does not have an implicit (wrapped) <label> Element does not have an explicit <label> aria-label attribute does not exist or is empty aria-labelledby attribute does not exist, references elements that do not exist or references elements that are empty Element has no… | Ensure every form element has a label |
| mobile-17 | **critical** | mobile | `label` | `input[type="number"]` | Fix any of the following: Element does not have an implicit (wrapped) <label> Element does not have an explicit <label> aria-label attribute does not exist or is empty aria-labelledby attribute does not exist, references elements that do not exist or references elements that are empty Element has no… | Ensure every form element has a label |
| desktop-1 | **high** | desktop | `color-contrast` | `.kpi-tag` | Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| desktop-2 | **high** | desktop | `color-contrast` | `.test-qr-badge` | Fix any of the following: Element has insufficient color contrast of 3.75 (foreground color: #2f855a, background color: #dceee2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-12 | **high** | mobile | `color-contrast` | `.kpi-tag` | Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| mobile-13 | **high** | mobile | `color-contrast` | `.test-qr-badge` | Fix any of the following: Element has insufficient color contrast of 3.75 (foreground color: #2f855a, background color: #dceee2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1 | Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds |
| desktop-3 | **high** | desktop | `label-content-name-mismatch` | `.header-action-btn--primary` | Fix any of the following: Text inside the element is not included in the accessible name | Ensure that elements labelled through their content must have their visible text as part of their accessible name |
| desktop-4 | **high** | desktop | `label-content-name-mismatch` | `button[aria-label="Reload settings from server"]` | Fix any of the following: Text inside the element is not included in the accessible name | Ensure that elements labelled through their content must have their visible text as part of their accessible name |
| desktop-5 | **high** | desktop | `label-content-name-mismatch` | `.primary-btn[aria-label="Save all business settings"][type="button"]` | Fix any of the following: Text inside the element is not included in the accessible name | Ensure that elements labelled through their content must have their visible text as part of their accessible name |
| mobile-14 | **high** | mobile | `label-content-name-mismatch` | `.header-action-btn--primary` | Fix any of the following: Text inside the element is not included in the accessible name | Ensure that elements labelled through their content must have their visible text as part of their accessible name |
| mobile-15 | **high** | mobile | `label-content-name-mismatch` | `button[aria-label="Reload settings from server"]` | Fix any of the following: Text inside the element is not included in the accessible name | Ensure that elements labelled through their content must have their visible text as part of their accessible name |
| mobile-16 | **high** | mobile | `label-content-name-mismatch` | `.primary-btn[aria-label="Save all business settings"][type="button"]` | Fix any of the following: Text inside the element is not included in the accessible name | Ensure that elements labelled through their content must have their visible text as part of their accessible name |
| mobile-19 | **medium** | mobile | `undersized-touch-target` | `div:nth-of-type(2) > div:nth-of-type(1) > label > input` | 13x13px | >= 24x24px on mobile (WCAG 2.5.8) |
| mobile-20 | **medium** | mobile | `undersized-touch-target` | `div:nth-of-type(1) > div:nth-of-type(1) > label > input` | 18x18px | >= 24x24px on mobile (WCAG 2.5.8) |
| mobile-21 | **medium** | mobile | `undersized-touch-target` | `div:nth-of-type(1) > div:nth-of-type(2) > label > input` | 18x18px | >= 24x24px on mobile (WCAG 2.5.8) |
| mobile-22 | **medium** | mobile | `undersized-touch-target` | `div:nth-of-type(1) > div:nth-of-type(3) > label > input` | 18x18px | >= 24x24px on mobile (WCAG 2.5.8) |
| desktop-7 | **low** | desktop | `undersized-touch-target` | `div:nth-of-type(2) > div:nth-of-type(1) > label > input` | 13x13px | >= 24x24px on desktop (WCAG 2.5.8) |
| desktop-8 | **low** | desktop | `undersized-touch-target` | `div:nth-of-type(1) > div:nth-of-type(1) > label > input` | 18x18px | >= 24x24px on desktop (WCAG 2.5.8) |
| desktop-9 | **low** | desktop | `undersized-touch-target` | `div:nth-of-type(1) > div:nth-of-type(2) > label > input` | 18x18px | >= 24x24px on desktop (WCAG 2.5.8) |
| desktop-10 | **low** | desktop | `undersized-touch-target` | `div:nth-of-type(1) > div:nth-of-type(3) > label > input` | 18x18px | >= 24x24px on desktop (WCAG 2.5.8) |
| desktop-11 | **low** | desktop | `undersized-touch-target` | `div > div > label > input` | 18x18px | >= 24x24px on desktop (WCAG 2.5.8) |
| mobile-18 | **low** | mobile | `undersized-touch-target` | `button[aria-label="Toggle navigation menu"]` | 40x40px (icon-only)  | >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG) |

## Repetition

One root cause hits many elements. The list above keeps the first 5 per rule per viewport; the rest are counted here rather than dropped.

| Viewport | Rule | Occurrences | Listed |
| :--- | :--- | :--- | :--- |
| mobile | `undersized-touch-target` | 6 | 5 |

### CRITICAL (2)

- **desktop-6 · `label` · desktop**
  - selector: `input[type="number"]`
  - actual: Fix any of the following: Element does not have an implicit (wrapped) <label> Element does not have an explicit <label> aria-label attribute does not exist or is empty aria-labelledby attribute does not exist, references elements that do not exist or references elements that are empty Element has no…
  - expected: Ensure every form element has a label
- **mobile-17 · `label` · mobile**
  - selector: `input[type="number"]`
  - actual: Fix any of the following: Element does not have an implicit (wrapped) <label> Element does not have an explicit <label> aria-label attribute does not exist or is empty aria-labelledby attribute does not exist, references elements that do not exist or references elements that are empty Element has no…
  - expected: Ensure every form element has a label

### HIGH (10)

- **desktop-1 · `color-contrast` · desktop**
  - selector: `.kpi-tag`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **desktop-2 · `color-contrast` · desktop**
  - selector: `.test-qr-badge`
  - actual: Fix any of the following: Element has insufficient color contrast of 3.75 (foreground color: #2f855a, background color: #dceee2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-12 · `color-contrast` · mobile**
  - selector: `.kpi-tag`
  - actual: Fix any of the following: Element has insufficient color contrast of 4.01 (foreground color: #2f855a, background color: #e7f4ec, font size: 8.6pt (11.52px), font weight: bold). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **mobile-13 · `color-contrast` · mobile**
  - selector: `.test-qr-badge`
  - actual: Fix any of the following: Element has insufficient color contrast of 3.75 (foreground color: #2f855a, background color: #dceee2, font size: 8.6pt (11.52px), font weight: normal). Expected contrast ratio of 4.5:1
  - expected: Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds
- **desktop-3 · `label-content-name-mismatch` · desktop**
  - selector: `.header-action-btn--primary`
  - actual: Fix any of the following: Text inside the element is not included in the accessible name
  - expected: Ensure that elements labelled through their content must have their visible text as part of their accessible name
- **desktop-4 · `label-content-name-mismatch` · desktop**
  - selector: `button[aria-label="Reload settings from server"]`
  - actual: Fix any of the following: Text inside the element is not included in the accessible name
  - expected: Ensure that elements labelled through their content must have their visible text as part of their accessible name
- **desktop-5 · `label-content-name-mismatch` · desktop**
  - selector: `.primary-btn[aria-label="Save all business settings"][type="button"]`
  - actual: Fix any of the following: Text inside the element is not included in the accessible name
  - expected: Ensure that elements labelled through their content must have their visible text as part of their accessible name
- **mobile-14 · `label-content-name-mismatch` · mobile**
  - selector: `.header-action-btn--primary`
  - actual: Fix any of the following: Text inside the element is not included in the accessible name
  - expected: Ensure that elements labelled through their content must have their visible text as part of their accessible name
- **mobile-15 · `label-content-name-mismatch` · mobile**
  - selector: `button[aria-label="Reload settings from server"]`
  - actual: Fix any of the following: Text inside the element is not included in the accessible name
  - expected: Ensure that elements labelled through their content must have their visible text as part of their accessible name
- **mobile-16 · `label-content-name-mismatch` · mobile**
  - selector: `.primary-btn[aria-label="Save all business settings"][type="button"]`
  - actual: Fix any of the following: Text inside the element is not included in the accessible name
  - expected: Ensure that elements labelled through their content must have their visible text as part of their accessible name

### MEDIUM (4)

- **mobile-19 · `undersized-touch-target` · mobile**
  - selector: `div:nth-of-type(2) > div:nth-of-type(1) > label > input`
  - box: x=21 y=1372 w=13 h=13
  - actual: 13x13px
  - expected: >= 24x24px on mobile (WCAG 2.5.8)
- **mobile-20 · `undersized-touch-target` · mobile**
  - selector: `div:nth-of-type(1) > div:nth-of-type(1) > label > input`
  - box: x=21 y=1737 w=18 h=18
  - actual: 18x18px
  - expected: >= 24x24px on mobile (WCAG 2.5.8)
- **mobile-21 · `undersized-touch-target` · mobile**
  - selector: `div:nth-of-type(1) > div:nth-of-type(2) > label > input`
  - box: x=21 y=1779 w=18 h=18
  - actual: 18x18px
  - expected: >= 24x24px on mobile (WCAG 2.5.8)
- **mobile-22 · `undersized-touch-target` · mobile**
  - selector: `div:nth-of-type(1) > div:nth-of-type(3) > label > input`
  - box: x=21 y=1822 w=18 h=18
  - actual: 18x18px
  - expected: >= 24x24px on mobile (WCAG 2.5.8)

### LOW (6)

- **desktop-7 · `undersized-touch-target` · desktop**
  - selector: `div:nth-of-type(2) > div:nth-of-type(1) > label > input`
  - box: x=277 y=678 w=13 h=13
  - actual: 13x13px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **desktop-8 · `undersized-touch-target` · desktop**
  - selector: `div:nth-of-type(1) > div:nth-of-type(1) > label > input`
  - box: x=277 y=1024 w=18 h=18
  - actual: 18x18px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **desktop-9 · `undersized-touch-target` · desktop**
  - selector: `div:nth-of-type(1) > div:nth-of-type(2) > label > input`
  - box: x=659 y=1024 w=18 h=18
  - actual: 18x18px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **desktop-10 · `undersized-touch-target` · desktop**
  - selector: `div:nth-of-type(1) > div:nth-of-type(3) > label > input`
  - box: x=1041 y=1024 w=18 h=18
  - actual: 18x18px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **desktop-11 · `undersized-touch-target` · desktop**
  - selector: `div > div > label > input`
  - box: x=277 y=1523 w=18 h=18
  - actual: 18x18px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **mobile-18 · `undersized-touch-target` · mobile**
  - selector: `button[aria-label="Toggle navigation menu"]`
  - box: x=16 y=12 w=40 h=40
  - actual: 40x40px (icon-only) 
  - expected: >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG)

## Fix prompt

Paste the block below into a coding agent:

```text
Fix the issues in this audit report for https://kbook.iadv.cloud/business/settings.
Rules:
1. Address findings in severity order (critical, high, medium, low).
2. Use the exact selector and measured value; do not guess markup.
3. Do not change unrelated styling or behaviour.
4. Re-run the audit afterwards and confirm the finding count drops.
```
