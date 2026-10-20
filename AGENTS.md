# KhanaBook — AI Agent Coding Guidelines & Operating Rules (AGENTS.md)

**Document Version:** 2.0.0  
**Status:** Mandatory Rules for all AI Coding Agents (Antigravity, Claude, Cursor, Aider, Copilot)  

---

## 1. Ground Truth Specification Hierarchy

Before making ANY code or architectural modification, the agent MUST inspect and follow the single sources of truth in this order:

1. **[`PRD.md`](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/PRD.md):** Product scope, user journeys, MVP boundaries.
2. **[`DESIGN.md`](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/DESIGN.md):** Colors, contrast rules, layout density, component conventions.
3. **[`ARCHITECTURE.md`](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/ARCHITECTURE.md):** Tech stack, data flow, synchronization contract, security boundaries.
4. **[`DATABASE.md`](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/DATABASE.md):** PostgreSQL & SQLite relational models, keys, indexes, and constraints.
5. **[`API.md`](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/API.md):** Endpoints, request/response formats, error envelope, webhook verification.

---

## 2. Core Behavioral Rules

1. **Observe Before Changing:** Always inspect existing code, styles, and tests before editing. Never guess file paths, component names, or database structures.
2. **Preserve Domain Vocabulary:** Follow the strict domain model defined in [`CONTEXT.md`](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/CONTEXT.md). Avoid terms like "client/vendor/seller"; use "restaurant/sub-merchant".
3. **No Unnecessary Rewrites:** Implement the smallest correct change that solves the requirement. Do not rewrite functioning legacy code merely for cosmetic styling.
4. **Never Expose Secrets:** Never hardcode API keys, Easebuzz gateway salts, database credentials, or auth tokens. Always read from environment variables.
5. **Zero Data Destruction:** Never drop database tables, truncate records, or bypass database migrations.
6. **Enforce Authorization Server-Side:** Never trust client-side role checks alone; ensure backend endpoints validate `restaurant_id` and `role` claims.

---

## 3. Module Build & Test Commands

### 3.1 Web Admin (`apps/web-admin/`)
* **Install Dependencies:** `npm install`
* **Development Server:** `ng serve` or `npm start`
* **Production Build:** `npm run build` (or `ng build --configuration production`)
* **Unit/Component Tests:** `npm test -- --watch=false --browsers=ChromeHeadless`
* **Type-Check:** `npx tsc --noEmit`

### 3.2 Android POS (`apps/android/`)
* **Debug APK Build:** `./gradlew.bat assembleDebug`
* **Run Unit Tests:** `./gradlew.bat testDebugUnitTest`
* **Run All Tests:** `./gradlew.bat test`
* **Lint Check:** `./gradlew.bat lint`

### 3.3 Server Services (`services/server/`)
* **Build (Maven):** `mvn clean package`
* **Run Local Tests:** `mvn test`
* **Run Spring Boot Dev:** `mvn spring-boot:run -Dspring-boot.run.profiles=dev`

---

## 4. UI/UX & Styling Discipline (Web Admin)

1. **Brand Blue:** Always use `--kb-color-primary: #0066cc;` for primary actions, active navigation items, and focus rings. Never introduce rogue purple or unvetted accent colors.
2. **Contrast Enforcement:** Every button, toggle label, and badge must maintain at least 4.5:1 contrast against its background. **Never** style `.toggle-btn--on` with white text on a white background.
3. **Table Row Densities:** Always use `white-space: nowrap; flex-wrap: nowrap;` for `.action-stack` inside table cells to preserve uniform ~`52px` row heights.
4. **Modal Viewport Safety:** All modals must specify `max-height: min(88vh, 760px); overflow-y: auto;` to ensure footer actions remain visible on laptops and compact screens.
5. **Text Fields:** Use `<textarea rows="3">` for physical addresses to prevent horizontal text truncation.
6. **Sidebar Sign Out:** Must remain an understated secondary ghost button. Never style as an aggressive full-width solid red block.

---

## 5. Git & Change Discipline

* **Commit Message Format:** Follow Conventional Commits:
  `feat(module): description`, `fix(module): description`, `docs(module): description`, `test(module): description`.
* **Focused Changes:** Keep commits atomic. Do not mix unrelated formatting changes with bug fixes or new features.
* **Test Verification:** Before completing any coding turn, run the relevant test suite (`npm test`, `ng test`, or `mvn test`) and verify 100% passing tests.
