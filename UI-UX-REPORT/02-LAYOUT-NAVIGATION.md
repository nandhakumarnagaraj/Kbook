# 02. Layout & Navigation Analysis

**Framework References:** Premium SaaS Design (Layout Principles), UI/UX Pro Max (App Interface patterns), Bencium UX Designer (Responsive + Spatial Design)

---

## 2.1 Layout Architecture

```
┌────────────────────────────────────────────┐
│            Header Toolbar                   │
│  [Menu] [Breadcrumb?]    [Theme] [Profile] │
├──────────┬─────────────────────────────────┤
│          │                                  │
│  Sidebar │        Main Content              │
│  (240px) │     (scrollable area)            │
│          │                                  │
│  · Home  │  ┌───────────────────────────┐  │
│  ·Orders │  │  Page Title               │  │
│  ·Menu   │  │  ┌───┐ ┌───┐ ┌───┐ ┌───┐│  │
│  ·Staff  │  │  │S1 │ │S2 │ │S3 │ │S4 ││  │
│  ·Market │  │  └───┘ └───┘ └───┘ └───┘│  │
│  ...     │  │  ┌──────────────────────┐│  │
│          │  │  │  Content Table/Grid  ││  │
│          │  │  └──────────────────────┘│  │
│          │  └───────────────────────────┘  │
└──────────┴─────────────────────────────────┘
```

### Strengths ✅

- **Consistent shell** — sidebar-layout component wraps all pages, providing brand identity
- **Theme toggle** — dark/light mode accessible from header
- **Brand logo** — PNG in sidebar + login page builds recognition
- **100dvh support** — full-height layout without scroll jank
- **Scrollable main area** — pages overflow independently of navigation

---

## 2.2 Sidebar Analysis

### Current Design

| Element | Implementation | Assessment |
|---------|---------------|------------|
| Width | Fixed 240px (desktop) | Good — standard for admin dashboards |
| Brand Logo | PNG in sidebar header | ✅ Good visibility |
| Navigation | Vertical list with icons + labels | ✅ Standard pattern |
| Active State | Brand orange indicator | ✅ Clear current location |
| Collapse | Not supported | ⚠️ No mobile collapse/hamburger |
| Icons | Material Icons (`mat-icon`) | ✅ Consistent icon set |

### Issues per Anthropic Frontend Design 🎨

**Issue 1: No sidebar collapse on mobile**
> At <768px, the sidebar occupies ~60% of viewport width, leaving almost no room for content. A hamburger toggle or auto-collapse to icon-only mode is needed.

**Issue 2: No sub-navigation indicators**
> Menu items with sub-pages (e.g., Settings → Tax Config) have no expand/collapse chevrons. Users discover sub-pages by landing on them.

**Issue 3: No icons for all items**
> Some nav items lack icons, creating visual inconsistency:
> - ✅ Dashboard (home icon), Orders (receipt), Menu (restaurant), Staff (people)
> - ⚠️ Settings, P&L Reports, Commission Config — no icons or generic icons

---

## 2.3 Header Analysis

### Current Design

| Element | Implementation | Assessment |
|---------|---------------|------------|
| Page Title | `<h1>` with subtitle | ✅ Clear hierarchy |
| Theme Toggle | Icon toggle button | ✅ Good discovery |
| User Profile | Avatar + dropdown | ✅ Standard pattern |
| Quick Shortcuts | **Removed** (recent session) | ✅ Decluttered |
| Breadcrumbs | Not present | ❌ Missing on deep pages |

### Issues

**Issue 1: No breadcrumb navigation**
> Users navigating to sub-pages (e.g., Settings → Tax Config → Lookup) have no breadcrumb trail to track location or navigate up.

**Issue 2: Header alignment** (recently fixed ✅)
> Theme toggle and profile avatar now align on the same vertical center line.

**Issue 3: No search bar**
> Admin dashboards with large datasets (orders, businesses, transactions) would benefit from a global search.

---

## 2.4 Page Layout Consistency Audit

| Page | Layout Pattern | Consistency |
|------|---------------|-------------|
| Business Dashboard | Metric cards (4-col grid) + tabbed panels | 🟢 Consistent |
| Payment Dashboard | Metric cards + chart + table | 🟢 Consistent |
| Platform Dashboard | Metric cards + data sections | 🟢 Consistent |
| Orders | Tab bar + Kanban/list view + filters | 🟢 Consistent |
| Marketplace Setup | Premium cards + timeline + toggles | 🟢 Premium |
| Sub-Merchants | Full-width table + detail modal | 🟢 Modern |
| Menu | Data table with actions | 🟡 Functional |
| Staff | Simple table | 🟡 Basic |
| Restaurant Settings | Form sections | 🟢 Clean |
| Settlement Reports | Filter bar + data table | 🟢 Standard |
| Commission Config | Basic form + table | 🟡 Basic |
| Businesses | Table + modals (recently refactored) | 🟢 Premium |
| Transaction Monitor | Table + detail drawer | 🟢 Standard |
| Login | Centered card + brand logo | 🟢 Clean |
| Forgot Password | Simple centered form | 🟡 Minimal |

### Key Inconsistencies
1. **Page padding** — Some pages use `24px`, others use `32px` or `16px`
2. **Card density** — Business dashboard has compact cards; staff page has sparse layout
3. **Table styling** — Tables have inconsistent header heights, cell padding, and row hover states

---

## 2.5 Navigation UX Assessment

| Criteria | Score | Notes |
|----------|-------|-------|
| Information architecture | 7/10 | Logical grouping, but some items could be reorganized |
| Findability | 6/10 | No search; sub-pages not obvious |
| Active state clarity | 8/10 | Brand color indicator is clear |
| Back/up navigation | 4/10 | No breadcrumbs; back button behavior inconsistent |
| Mobile navigation | 3/10 | No sidebar collapse, no hamburger |
| Keyboard navigation | 3/10 | Sidebar items not fully keyboard-accessible |
| Deep linking | 7/10 | URL-based routing works for most pages |

---

## 2.6 Premium SaaS Design Audit

**Rule: "Clean visual hierarchy with progressive disclosure"**
> ⚠️ Some pages (Menu, Commission Config) show all functionality at once without progressive disclosure. Complex pages would benefit from accordion sections or tabbed views.

**Rule: "Navigation is discoverable and predictable"**
> ⚠️ Without breadcrumbs, users must rely on the sidebar to understand their location. Adding breadcrumbs would significantly improve orientation on sub-pages.

**Overall: 6.5/10**
