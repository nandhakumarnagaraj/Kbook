---

description: "Task list template for feature implementation"
---

# Tasks: AI Insights Panel

**Input**: Design documents from `/specs/001-projectname-khanabook-featurename/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/
**Tests**: The examples below include test tasks. Tests are OPTIONAL - only include them if explicitly requested in the feature specification.
**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Web app**: `web-admin/src/`
- Paths shown below assume single project - adjust based on actual repository structure

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Create task list structure per implementation plan
- [ ] T002 Initialize Angular project section for AI Insights Panel
- [ ] T003 [P] Configure EsLint/Prettier for `--kb-*` token aware linting

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T004 Set up dashboard component structure in `web-admin/src/app/pages/dashboard/`
- [ ] T005 [P] Create AI Insights Panel component stub in `dashboard/ai-insights-panel.component.ts`
- [ ] T006 [P] Add `--kb-*` design token imports and styling to panel component
- [ ] T007 Create insight model interface in `web-admin/src/app/core/models/insight.interface.ts`
- [ ] T008 [P] Set up local storage service for insight persistence in `web-admin/src/app/core/services/local-storage.service.ts`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - AI Daily Insights Panel (Priority: P1) 🎯 MVP

**Goal**: Display a daily AI Insights Panel on the dashboard homepage showing plain-language summaries of key restaurant metrics

**Independent Test**: Panel renders on dashboard homepage, shows at least 3 insight types (Revenue, Margin, Popular Item), refresh button generates new insights

### Implementation for User Story 1

- [ ] T010 [P] [US1] Create `AIInsightsPanelComponent` in `web-admin/src/app/pages/dashboard/ai-insights-panel.component.ts`
- [ ] T011 [P] [US1] Add panel HTML template in `web-admin/src/app/pages/dashboard/ai-insights-panel.component.html`
- [ ] T012 [P] [US1] Implement `generateDailyInsights()` function using template-based plain-language generation
- [ ] T013 [US1] Integrate panel into dashboard homepage (`dashboard-home.component.html`)
- [ ] T014 [US1] Add "Refresh insights" button with click handler
- [ ] T015 [US1] Style panel using `--kb-color-primary: #3B82F6`, `--kb-color-espresso: #E87A1E`, `--kb-color-surface: #FFFFFF`
- [ ] T016 [US1] Verify panel appears above KPI grid without breaking existing dashboard layout

**Tests for User Story 1** (OPTIONAL)

- [ ] T017 [P] [US1] Visual regression test: panel renders with correct `--kb-*` token colors
- [ ] T018 [P] [US1] Unit test: `generateDailyInsights()` produces at least 3 insight types from 4 categories

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently - dashboard shows AI Insights Panel with 3+ plain-language insights

---

## Phase 4: User Story 2 - Insight Types Coverage (Priority: P2)

**Goal**: The panel covers at least 4 core insight types across different metric categories (Revenue, Margin, Popular Item, Operations)

### Implementation for User Story 2

- [ ] T019 [P] [US2] Add `revenueTrendInsight()` function generating plain-language revenue summary
- [ ] T020 [P] [US2] Add `marginAlertInsight()` function generating margin recommendation (e.g., "Consider 5% price adjustment")
- [ ] T021 [P] [US2] Add `popularItemInsight()` function showing top-selling item (e.g., "Top dish: Chicken biryani — 18 orders")
- [ ] T022 [P] [US2] Add `operationsAlertInsight()` function showing operational status (e.g., "Kitchen running normal")
- [ ] T023 [US2] Update `generateDailyInsights()` to select 3 insights from the 4 available categories
- [ ] T024 [US2] Verify all 4 insight types can appear individually across multiple panel loads

**Checkpoint**: Panel covers all 4 insight categories (Revenue, Margin, Popular Item, Operations) with plain-language descriptions

---

## Phase 5: User Story 3 - Manual Refresh & Persistence (Priority: P3)

**Goal**: Users can manually refresh insights, and insights persist across dashboard visits within the same day. Insights reset at midnight.

### Implementation for User Story 3

- [ ] T025 [P] [US3] Implement `refreshInsights()` function that regenerates insights and updates local storage
- [ ] T026 [P] [US3] Implement `loadPersistedInsights()` function that retrieves stored insights from `localStorage`
- [ ] T027 [P] [US3] Implement `resetInsightsAtMidnight()` function using `setInterval` checking `new Date().getUTCDate()`
- [ ] T028 [US3] Add `useEffect` in dashboard component to load persisted insights on component mount
- [ ] T029 [US3] Test that insights persist across dashboard navigation (home → settings → home)
- [ ] T030 [US3] Test that new day resets insights (simulate midnight date change)

**Checkpoint**: Manual refresh works; insights persist across navigations; insights reset at new day boundary

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T031 [P] Code cleanup and refactoring - ensure all `--kb-*` token references are consistent
- [ ] T032 Documentation updates - update `specs/001-projectname-khanabook-featurename/spec.md` with implementation notes
- [ ] T033 [P] Performance optimization - ensure panel renders within 100ms and doesn't cause dashboard layout shift
- [ ] T034 [P] Accessibility check - verify panel is screen-reader friendly with proper ARIA labels
- [ ] T035 [P] Cross-browser testing - verify panel renders correctly in Chrome, Firefox, Edge, Safari

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories (MVP can launch with 3 insights)
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1 to expand insight types
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - May integrate with US1/US2 for refresh/persistence features

### Within Each User Story

- Core implementation before integration
- Story complete before moving to next priority
- Verify tests fail before implementing (if tests included)

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows)
- Models within a story marked [P] can run in parallel
- Different user stories can be worked on in parallel by different team members

---

## Parallel Example: User Story 1

```bash
# Launch all [P] tasks for User Story 1 together:
Task: "Create AIInsightsPanelComponent in web-admin/src/app/pages/dashboard/ai-insights-panel.component.ts"
Task: "Add panel HTML template in web-admin/src/app/pages/dashboard/ai-insights-panel.component.html"
Task: "Implement generateDailyInsights() function"
Task: "Style panel using --kb-* design tokens"
Task: "Integrate panel into dashboard homepage"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1 (AI Insights Panel MVP with 3 insights)
4. **STOP and VALIDATE**: Test User Story 1 independently - dashboard shows panel with insights
5. Deploy/demo if ready - MVP is complete User Story 1

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo (adds 4th insight type)
4. Add User Story 3 → Test independently → Deploy/Demo (adds refresh + persistence)
5. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1 (AI Insights Panel MVP)
   - Developer B: User Story 2 (Insight Types Coverage - add 4th type)
   - Developer C: User Story 3 (Manual Refresh & Persistence)
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
---

## Execution Order Recommendation

**Sprint 1** (Weeks 1-2): Complete Phase 1 + Phase 2 (Setup + Foundational)
**Sprint 2** (Weeks 3-4): Complete User Story 1 (AI Insights Panel MVP) — this is the primary deliverable
**Sprint 3** (Weeks 5-6): Complete User Story 2 (add 4th insight type)
**Sprint 4** (Weeks 7-8): Complete User Story 3 (refresh + persistence) — finalize the feature

**MVP Launch Goal**: End of Sprint 2 — dashboard with AI Insights Panel showing 3 plain-language insights using `--kb-*` design tokens.