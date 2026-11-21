# UI & runtime audit report

- **Target:** https://kbook.iadv.cloud/login
- **Viewports:** desktop, mobile
- **Design tokens:** not configured (token-drift rules skipped)
- **AI triage:** disabled (deterministic only)
- **Vision review:** disabled
- **Duration:** 19.8s
- **Generated:** 2026-10-20T16:39:29.254Z
- **Measured findings:** 9 listed, 0 rolled up, 9 total (gate-eligible)

## Summary

| Dimension | Breakdown |
| :--- | :--- |
| Severity | critical: 0 · high: 3 · medium: 2 · low: 4 |
| Category | runtime_bug: 2 · layout_invariant: 6 · a11y_contrast: 1 |
| Viewport | desktop: 4 · mobile: 5 |

## Findings

Every value below was measured in the browser (computed style, bounding box, HTTP status).
Nothing here is model-generated. Lines marked `triage:` come from an optional classifier.

| ID | Sev | Viewport | Rule | Selector | Actual | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| mobile-8 | **high** | mobile | `label-content-name-mismatch` | `.mobile-brand` | Fix any of the following: Text inside the element is not included in the accessible name | Ensure that elements labelled through their content must have their visible text as part of their accessible name |
| desktop-2 | **high** | desktop | `request-failed` | `https://accounts.google.com/gsi/style` | net::ERR_FAILED | Request completes successfully |
| mobile-7 | **high** | mobile | `request-failed` | `https://accounts.google.com/gsi/style` | net::ERR_FAILED | Request completes successfully |
| mobile-9 | **medium** | mobile | `undersized-touch-target` | `form > div:nth-of-type(2) > div:nth-of-type(1) > a` | 121x21px | >= 24x24px on mobile (WCAG 2.5.8) |
| mobile-11 | **medium** | mobile | `undersized-touch-target` | `div:nth-of-type(2) > div:nth-of-type(3) > div:nth-of-type(2) > a` | 98x20px | >= 24x24px on mobile (WCAG 2.5.8) |
| desktop-3 | **low** | desktop | `undersized-touch-target` | `aside > div:nth-of-type(5) > div > a:nth-of-type(1)` | 46x20px | >= 24x24px on desktop (WCAG 2.5.8) |
| desktop-4 | **low** | desktop | `undersized-touch-target` | `aside > div:nth-of-type(5) > div > a:nth-of-type(2)` | 97x20px | >= 24x24px on desktop (WCAG 2.5.8) |
| desktop-5 | **low** | desktop | `undersized-touch-target` | `form > div:nth-of-type(2) > div:nth-of-type(1) > a` | 121x21px | >= 24x24px on desktop (WCAG 2.5.8) |
| mobile-10 | **low** | mobile | `undersized-touch-target` | `button[aria-label="Show password"]` | 40x40px (icon-only)  | >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG) |

### HIGH (3)

- **mobile-8 · `label-content-name-mismatch` · mobile**
  - selector: `.mobile-brand`
  - actual: Fix any of the following: Text inside the element is not included in the accessible name
  - expected: Ensure that elements labelled through their content must have their visible text as part of their accessible name
- **desktop-2 · `request-failed` · desktop**
  - selector: `https://accounts.google.com/gsi/style`
  - actual: net::ERR_FAILED
  - expected: Request completes successfully
- **mobile-7 · `request-failed` · mobile**
  - selector: `https://accounts.google.com/gsi/style`
  - actual: net::ERR_FAILED
  - expected: Request completes successfully

### MEDIUM (2)

- **mobile-9 · `undersized-touch-target` · mobile**
  - selector: `form > div:nth-of-type(2) > div:nth-of-type(1) > a`
  - box: x=249 y=484 w=121 h=21
  - actual: 121x21px
  - expected: >= 24x24px on mobile (WCAG 2.5.8)
- **mobile-11 · `undersized-touch-target` · mobile**
  - selector: `div:nth-of-type(2) > div:nth-of-type(3) > div:nth-of-type(2) > a`
  - box: x=213 y=726 w=98 h=20
  - actual: 98x20px
  - expected: >= 24x24px on mobile (WCAG 2.5.8)

### LOW (4)

- **desktop-3 · `undersized-touch-target` · desktop**
  - selector: `aside > div:nth-of-type(5) > div > a:nth-of-type(1)`
  - box: x=678 y=833 w=46 h=20
  - actual: 46x20px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **desktop-4 · `undersized-touch-target` · desktop**
  - selector: `aside > div:nth-of-type(5) > div > a:nth-of-type(2)`
  - box: x=747 y=833 w=97 h=20
  - actual: 97x20px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **desktop-5 · `undersized-touch-target` · desktop**
  - selector: `form > div:nth-of-type(2) > div:nth-of-type(1) > a`
  - box: x=1249 y=518 w=121 h=21
  - actual: 121x21px
  - expected: >= 24x24px on desktop (WCAG 2.5.8)
- **mobile-10 · `undersized-touch-target` · mobile**
  - selector: `button[aria-label="Show password"]`
  - box: x=324 y=517 w=40 h=40
  - actual: 40x40px (icon-only) 
  - expected: >= 44x44px for icon-only controls on mobile (WCAG 2.5.5 / Apple HIG)

## Fix prompt

Paste the block below into a coding agent:

```text
Fix the issues in this audit report for https://kbook.iadv.cloud/login.
Rules:
1. Address findings in severity order (critical, high, medium, low).
2. Use the exact selector and measured value; do not guess markup.
3. Do not change unrelated styling or behaviour.
4. Re-run the audit afterwards and confirm the finding count drops.
```
