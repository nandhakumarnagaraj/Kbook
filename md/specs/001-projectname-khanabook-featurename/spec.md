# Feature Specification: AI Insights Panel

**Feature Branch**: `feature/ai-insights-panel`

**Created**: 2026-08-29

**Status**: Draft

**Input**: User description: "Define AI-powered plain-language daily summary insights for Khanabook restaurant dashboard, inspired by Restrofi competitor pattern. Users should receive actionable daily insights via WhatsApp-style format and dashboard panel, replacing manual analytics interpretation."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - AI Daily Insights Panel (Priority: P1)

**Description**: Users see a daily insights panel on the dashboard displaying plain-language summaries of key restaurant metrics, automatically generated each morning. The panel includes: top-performing dish, lowest-margin item, revenue trend, and one actionable recommendation.

**Why this priority**: P1 — Directly addresses the #1 competitor insight from analysis: "Restrofi's plain-language daily summaries are a key differentiator." Adds immediate value by reducing manual analytics time for restaurant owners. Independent MVP viable — panel can launch with 3-4 core insights without full AI integration.

**Independent Test**: Can be fully tested by verifying the panel displays correct metrics for sample data sets, shows at least 3 insight types (top dish, margin alert, revenue trend), and the "refresh" action regenerates insights. No backend AI required for MVP — can use predefined insight templates.

**Acceptance Scenarios**:

1. **Given** a restaurant has daily data, **when** the dashboard loads, **then** the AI Insights Panel displays "Good morning [Restaurant Name]!" with timestamp
2. **Given** insights are displayed, **when** the user clicks "Refresh", **then** new insights are generated with updated metrics
3. **Given** the panel shows "Top dish: Chicken biryani — 18 orders", **when** the user views the menu page, **then** the top dish matches the menu items in the system

### User Story 2 - Insight Types Coverage (Priority: P2)

**Description**: The panel covers at least 4 core insight types across different metric categories, ensuring comprehensive restaurant health visibility.

**Why this priority**: P2 — Ensures the feature delivers comprehensive value rather than a narrow view. Covers the key metric categories identified in competitor analysis: revenue, margins, popular items, and operational alerts.

**Independent Test**: Verify the panel includes insights from each of the 4 categories (revenue, margins, popularity, operations) with correct data mapping.

**Acceptance Scenarios**:

1. **Given** the panel is active, **when** metrics are available, **then** at least one insight from each category appears: Revenue, Margin, Popular Item, Operations
2. **Given** a low-margin dish is detected, **when** the panel displays it, **then** the insight includes a plain-language recommendation (e.g., "Consider a 5% price adjustment")
3. **Given** no data is available for a category, **when** the panel renders, **then** a placeholder insight appears: "Data pending for [category]"

### User Story 3 - Manual Refresh & Persistence (Priority: P3)

**Description**: Users can manually refresh insights, and insights persist across dashboard visits within the same session. Insights are tied to the current day and reset at midnight.

**Why this priority**: P3 — Ensures the feature is usable and reliable. Users should control when insights update, and not lose their insights if they navigate away and return.

**Independent Test**: Refresh button generates new insights; navigating to another page and back shows the same insights within the same day.

**Acceptance Scenarios**:

1. **Given** the user clicks "Refresh insights", **when** the spinner completes, **then** new insight content appears
2. **Given** the user navigates away and returns within the same day, **when** the dashboard loads, **then** the same insights are displayed (from local storage or session)
3. **Given** it's a new day (after midnight), **when** the dashboard loads, **then** insights are reset with new daily data

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST display a daily AI Insights Panel on the dashboard homepage
- **FR-002**: Panel MUST show at least 3 distinct insight types from: Revenue, Margin, Popular Item, Operations
- **FR-003**: Insights MUST be in plain language (no dashboards, charts, or technical jargon)
- **FR-004**: System MUST auto-generate insights each day at 9:00 AM (configurable time)
- **FR-004**: System MUST provide a "Refresh insights" button for manual updates
- **FR-005**: Insights MUST persist across dashboard visits within the same day
- **FR-006**: System MUST reset insights at midnight and generate new daily insights
- **FR-006**: Panel design MUST use --kb-* design tokens (primary: #3B82F6, accent: #E87A1E, surface: #FFFFFF)

### Key Entities

- **Insight Entity**: Represents a single AI-generated insight with fields: id, type, title, body, priority, createdAt, actionUrl (optional)
- **Insight Type**: Enum: `revenue_trend`, `margin_alert`, `popular_item`, `operations_alert`
- **Daily Insights Cache**: Session/local storage entity holding today's generated insights

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of dashboard homepage visits show the AI Insights Panel (no broken UI)
- **SC-002**: Panel displays at least 3 distinct insight types from the 4 categories on each load
- **SC-003**: At least 80% of insights are in plain language (no chart/jargon-only descriptions), verified by human review
- **SC-004**: Refresh button successfully generates new insights in 100% of manual test cases
- **SC-005**: Insights persist across page navigations within the same day in 100% of test cases
- **SC-006**: Insights reset at midnight and new insights appear in 100% of daily reset test cases

## Assumptions

- Existing dashboard homepage has a designated panel area that can be populated with HTML/JS
- Backend or mock data provides daily restaurant metrics (revenue, top items, margin data)
- Khanabook already collects the necessary data points (daily revenue, top-selling items, margin calculations)
- Design tokens `--kb-color-primary: #3B82F6`, `--kb-color-espresso: #E87A1E`, `--kb-color-surface: #FFFFFF` are available in the dashboard context
- No AI/ML backend required for MVP — insights can use template-based generation with restaurant data substitution

## Dependencies

### Web Dependencies

- `web-admin/src/app/pages/dashboard/` — Dashboard homepage component where panel will be inserted
- `web-admin/src/styles.css` — `--kb-*` design tokens for styling
- `web-admin/src/app/core/firebase/kb-notification.service.ts` — FCM integration for daily insight push notifications (optional phase 2)
- Existing dashboard KPI grid component structure

### Data Dependencies

- Daily revenue totals
- Top 3 best-selling menu items
- Current period margin calculations (ideally overall and per-top-item)
- Operational flags (e.g., "kitchen running slow", "low stock on popular item")

## Roadmap Connection

This feature implements **Medium Priority #1** from the competitor analysis roadmap: "AI/plain-language insights panel — Inspired by Restrofi's 'Daily insight · 9:02 AM' WhatsApp-style summary with plain-language recommendations."

It also supports the **Design System Specification** success criteria:
- SC-002: Panel displays 3+ insight types from 4 categories
- SC-003: 80%+ insights in plain language
- SC-005: Insights persist across navigations

## Edge Cases

- What happens when no data is available for any category? → Display "Data pending for [category]" placeholder insights
- What happens if the backend/mock data fails to load? → Show gracefully degraded panel with "Insights unavailable" message and "Try again" button
- What if a restaurant has been open less than 1 day? → Show "Getting your first insights..." message until end of first business day
- What if the user's locale/settings affect date/midnight reset? → Use UTC date boundary or user's local timezone consistently

## Spec File Location

`specs/001-projectname-khanabook-featurename/spec.md`

**To execute related tasks**: Run `/speckit-plan` to break this spec into implementation tasks, then `/speckit-tasks` to generate the actionable task list.

**To return to the main project**: The SPECIFY_FEATURE environment variable is set to `001-projectname-khanabook-featurename` — you can persist this in your shell with: `$env:SPECIFY_FEATURE = '001-projectname-khanabook-featurename'`