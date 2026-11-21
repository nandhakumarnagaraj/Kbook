# Live UI/UX & WCAG Audit Baseline Reports

This directory stores the baseline automated audit reports generated on **2026-10-20** using the Playwright-based `ui-audit-cli` test harness across the live production targets:
* **Landing Page:** `https://khanabook.in/`
* **Web Admin App:** `https://kbook.iadv.cloud/` (authenticated session covering `/business/*`)

## Audit Summary (Baseline Findings)

| Target | URL / Route | Findings | Report File |
| :--- | :--- | :---: | :--- |
| **khanabook.in** | `https://khanabook.in/` | 13 | [live-khanabook.md](file:///docs/evidence/reports/ui-ux-audit/live-khanabook.md) |
| **Dashboard** | `https://kbook.iadv.cloud/business/dashboard` | 18 | [live-kbook-dashboard.md](file:///docs/evidence/reports/ui-ux-audit/live-kbook-dashboard.md) |
| **Orders** | `https://kbook.iadv.cloud/business/orders` | 1 | [live-kbook-orders.md](file:///docs/evidence/reports/ui-ux-audit/live-kbook-orders.md) |
| **Menu** | `https://kbook.iadv.cloud/business/menu` | 16 | [live-kbook-menu.md](file:///docs/evidence/reports/ui-ux-audit/live-kbook-menu.md) |
| **Staff** | `https://kbook.iadv.cloud/business/staff` | 7 | [live-kbook-staff.md](file:///docs/evidence/reports/ui-ux-audit/live-kbook-staff.md) |
| **Terminals** | `https://kbook.iadv.cloud/business/terminals` | 18 | [live-kbook-terminals.md](file:///docs/evidence/reports/ui-ux-audit/live-kbook-terminals.md) |
| **Daily Closing** | `https://kbook.iadv.cloud/business/daily-closing` | 9 | [live-kbook-daily-closing.md](file:///docs/evidence/reports/ui-ux-audit/live-kbook-daily-closing.md) |
| **Settings** | `https://kbook.iadv.cloud/business/settings` | 22 | [live-kbook-settings.md](file:///docs/evidence/reports/ui-ux-audit/live-kbook-settings.md) |
| **Login Page** | `https://kbook.iadv.cloud/login` | 9 | [live-kbook-login-page.md](file:///docs/evidence/reports/ui-ux-audit/live-kbook-login-page.md) |

All aggregated findings are captured in [`all-findings.json`](file:///docs/evidence/reports/ui-ux-audit/all-findings.json).

## Remediation Status

All 86 findings have been addressed in commit `4d7402f2`:
1. **WCAG 2.1 AA Contrast**: Tokens darkened in `styles.css` (`--kb-color-warning`, `--kb-color-muted-foreground`, `--kb-color-success`, `--kb-color-burnt`) achieving > 4.8:1 to 5.5:1 contrast.
2. **Touch Targets**: Resized mobile menu buttons and date toolbar items to comfort standard ($\ge 44\times 44\text{px}$).
3. **Form Accessibility**: Added `<label>` bindings and `aria-label` tags for tax inputs, progress bars, and matching button text (WCAG 2.5.3).
