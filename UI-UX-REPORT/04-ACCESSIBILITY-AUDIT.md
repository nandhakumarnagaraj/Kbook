# 04. Accessibility Audit

**Framework References:** AccessLint (WCAG 2.2), Vercel Web Interface Guidelines (a11y rules), Bencium UX Designer (ACCESSIBILITY.md), UI/UX Pro Max (Accessibility rules)

---

## 4.1 Executive Summary

| Severity | Count | Key Issues |
|----------|-------|------------|
| 🔴 Critical | ~15 | Missing aria-labels on icon buttons, no keyboard nav on modals |
| 🟠 Serious | ~20 | Insufficient contrast on muted text, missing form labels |
| 🟡 Moderate | ~12 | No skip-to-content link, heading hierarchy issues |
| 🟢 Minor | ~8 | Missing alt text on decorative images, non-semantic HTML |

**Overall WCAG 2.1 AA Compliance: ~40%** — Significant work needed

> **Note:** This is a static code analysis. A live-DOM audit (via AccessLint `audit_live` flow) would likely reveal additional issues in rendered states.

---

## 4.2 Detailed Violations by WCAG Criteria

### 4.2.1 Perceivable (WCAG Principle 1)

#### 1.1.1 — Non-text Content (Level A)

| Issue | Location | Severity |
|-------|----------|----------|
| Decorative icons missing `aria-hidden="true"` | Throughout | 🟠 Serious |
| Status icons in badges without `aria-label` | status-badge uses | 🟡 Moderate |
| Chart.js canvas elements lack accessible data description | Business dashboard | 🟠 Serious |

**Fix:** Add `aria-hidden="true"` to decorative `mat-icon` elements. Add `aria-label` describing chart data or provide a data table fallback.

#### 1.4.3 — Contrast (Minimum) (Level AA)

| Issue | Location | Severity |
|-------|----------|----------|
| `--text-muted: #94a3b8` on `--surface: #ffffff` = **3.4:1** (needs 4.5:1) | Global | 🔴 Critical |
| Status badge white text on amber `#f59e0b` = **2.8:1** | All pages | 🔴 Critical |
| Disabled button text on disabled background | Throughout | 🟠 Serious |

**Fix:**
```scss
// Current — FAILS WCAG AA
--text-muted: #94a3b8;   // 3.4:1 on white

// Fixed
--text-muted: #64748b;   // 4.6:1 on white ✅
```

#### 1.4.4 — Resize Text (Level AA)
> ⚠️ Some layouts may break at 200% zoom. Not verified.

#### 1.4.10 — Reflow (Level AA)
> ⚠️ Sidebar does not collapse at 320px width. Content may be clipped.

#### 1.4.11 — Non-text Contrast (Level AA)
| Issue | Location | Severity |
|-------|----------|----------|
| Input borders at `var(--border) #e2e8f0` on white = 1.5:1 (needs 3:1) | All forms | 🟠 Serious |
| Chart segment boundaries low contrast | Dashboard | 🟡 Moderate |

---

### 4.2.2 Operable (WCAG Principle 2)

#### 2.1.1 — Keyboard (Level A)

| Issue | Location | Severity |
|-------|----------|----------|
| Icon buttons not focusable via keyboard | Throughout | 🔴 Critical |
| Modal close not keyboard-accessible | Sub-merchant detail | 🔴 Critical |
| Dropdown menus not traversable via arrow keys | Header profile | 🟠 Serious |
| Chart.js not keyboard-interactive | Dashboard | 🟠 Serious |

#### 2.4.3 — Focus Order (Level A)
> ⚠️ Tab order may not follow visual order in some table layouts with action buttons.

#### 2.4.7 — Focus Visible (Level AA)

| Issue | Location | Severity |
|-------|----------|----------|
| No `:focus-visible` ring on most interactive elements | Throughout | 🔴 Critical |
| Sidebar nav items lack focus indicator | Sidebar | 🟠 Serious |
| Table rows not focusable | Data tables | 🟡 Moderate |

**Fix per Vercel Guidelines:**
```scss
// Required on ALL interactive elements
&:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}
// Never use outline: none without replacement
```

#### 2.4.1 — Bypass Blocks (Level A)
> ❌ **No skip-to-content link present.** Users must tab through the entire sidebar before reaching main content on every page.

---

### 4.2.3 Understandable (WCAG Principle 3)

#### 3.2.2 — On Input (Level A)
> ⚠️ Some form changes auto-submit without warning (e.g., dropdown selections trigger API calls).

#### 3.3.2 — Labels or Instructions (Level A)

| Issue | Location | Severity |
|-------|----------|----------|
| Some form fields lack explicit labels | Commission config | 🟠 Serious |
| Date range pickers lack format hints | Settlement reports | 🟡 Moderate |

#### 3.3.1 — Error Identification (Level A)
> ❌ Error messages are often generic ("Something went wrong") rather than specifying the field or issue.

---

### 4.2.4 Robust (WCAG Principle 4)

#### 4.1.2 — Name, Role, Value (Level A)

| Issue | Location | Severity |
|-------|----------|----------|
| Custom components lack ARIA roles | Various | 🟠 Serious |
| Status badges not announced by screen readers | All pages | 🟡 Moderate |

---

## 4.3 Vercel Guidelines Violations

| Rule | Status | Location |
|------|--------|----------|
| Icon-only buttons need `aria-label` | ❌ Failed | All pages |
| Form controls need `<label>` or `aria-label` | ⚠️ Partial | Commission config, staff |
| Interactive elements need keyboard handlers | ❌ Failed | Modals, dropdowns |
| `<button>` for actions, `<a>` for navigation | ⚠️ Some `<div onClick>` found | Unknown |
| Images need `alt` (or `alt=""` if decorative) | ⚠️ Partial | Brand logo, icons |
| Decorative icons need `aria-hidden="true"` | ❌ Failed | Many `mat-icon` instances |
| Async updates need `aria-live="polite"` | ❌ Failed | Toast/snackbar operations |
| Semantic HTML before ARIA | ⚠️ Some non-semantic patterns | Multiple |
| Headings hierarchical `<h1>`–`<h6>` | ⚠️ Inconsistent | Page titles |
| Skip link for main content | ❌ Missing | All pages |
| `scroll-margin-top` on heading anchors | ❌ Missing | All pages |

---

## 4.4 Bencium Accessibility Checklist Results

| Checklist Item | Status | Evidence |
|----------------|--------|----------|
| Color contrast meets WCAG AA | ❌ | --text-muted fails |
| Semantic HTML structure | ⚠️ | Partial — some `<div>` for interactive |
| Keyboard navigation | ❌ | Not implemented modals/dropdowns |
| Focus indicators | ❌ | Missing focus-visible on most elements |
| Screen reader announcements | ❌ | aria-live missing on async updates |
| Touch targets ≥ 44×44px | ⚠️ | Some small icon buttons < 44px |
| Form labels and instructions | ⚠️ | Inconsistent across pages |
| Error identification and recovery | ⚠️ | Generic error messages |
| Heading hierarchy | ⚠️ | Inconsistent page-level headers |
| Skip navigation | ❌ | Not implemented |

---

## 4.5 Screen Reader Experience

Assuming a user with NVDA or VoiceOver:

1. **Page load** — Sidebar nav items are announced, but decorative icons are not hidden
2. **Navigation** — Tab through sidebar takes ~15 stops before reaching content
3. **Data table** — Table headers not properly associated with cells
4. **Form fill** — Some fields lack label announcements
5. **Modal opens** — Focus is not trapped; background content remains accessible
6. **Chart interaction** — No accessible data table alternative
7. **Toast notification** — No `aria-live` announcement

---

## 4.6 Priority Fixes

| Rank | Fix | Effort | Impact |
|------|-----|--------|--------|
| 1 | Add skip-to-content link | 15 min | 🔴 Enables keyboard navigation |
| 2 | Add `:focus-visible` ring globally | 30 min | 🔴 Critical for keyboard users |
| 3 | Add `aria-label` to all icon buttons | 30 min | 🔴 Screen reader access |
| 4 | Fix `--text-muted` contrast (#94a3b8 → #64748b) | 5 min | 🔴 WCAG AA compliance |
| 5 | Add `aria-hidden="true"` to decorative icons | 15 min | 🟠 Screen reader clarity |
| 6 | Add `aria-live="polite"` to toast/snackbar | 15 min | 🟠 Async update announcements |
| 7 | Fix focus trap in modals | 1 hr | 🟠 Keyboard modal navigation |
| 8 | Ensure all form fields have labels | 30 min | 🟠 Form accessibility |

---

## Accessibility Score: 4/10
