# KhanaBook — AI Agent Coding Guidelines & Operating Rules (AGENTS.md)

**Document Version:** 2.1.0  
**Status:** Mandatory Rules for all AI Coding Agents (Antigravity, Claude, Cursor, Aider, Copilot)  
**Target Monorepo:** `apps/web-admin` (Angular 18.2.0), `apps/android` (Kotlin Compose, Room v82), `services/server` (Spring Boot 3.5.12, Java 17)  

---

## 1. Ground Truth Specification Hierarchy

Before making ANY code or architectural modification, the agent MUST inspect and follow the single sources of truth in this order:

1. **[`PRD.md`](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/PRD.md):** Product scope, user journeys, 3-role security model, MVP boundaries.
2. **[`DESIGN.md`](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/DESIGN.md):** Color tokens, contrast rules, table layout density, modal sizing, component conventions.
3. **[`ARCHITECTURE.md`](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/ARCHITECTURE.md):** Tech stack (Angular 18.2.0, Spring Boot 3.5.12 on Java 17, Room v82), data sync flow, security boundaries.
4. **[`DATABASE.md`](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/DATABASE.md):** Relational schema (`restaurantprofiles`, `bills`, `bill_items`, `menuitems`, `categories`), composite keys, Room v82 entities.
5. **[`API.md`](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/API.md):** REST controller endpoints, authentication payload with `surface: 'web'`, sync contracts (`/sync/push`, `/sync/pull`), webhook HMAC verification.

---

## 2. Technical Ground Truth Invariants

1. **Backend Tech Stack:**
   * **Language & Runtime:** Java **17** (Do NOT downgrade to Java 11 or upgrade to Java 21 without explicit user approval).
   * **Framework:** Spring Boot **3.5.12** monolithic application located in `services/server/`.
   * **Database & Migrations:** PostgreSQL 16+ managed via **117+ Flyway migrations** (`src/main/resources/db/migration/`). Forward-only discipline: **never** edit or delete an existing migration (`V1` through `V117`).
2. **Frontend Tech Stack:**
   * **Framework:** Angular **18.2.0** with Standalone Components (zero `NgModules`).
   * **State & Reactivity:** Angular Signals and RxJS observables.
   * **Testing:** Karma + Jasmine (`npm test -- --watch=false --browsers=ChromeHeadless`).
3. **Android POS Client Tech Stack:**
   * **Language & UI:** Kotlin, Jetpack Compose.
   * **Database:** Android Room **Schema v82** encrypted with SQLCipher (`khanabook_lite_db_<restaurantId>`).
   * **Testing:** JUnit + Robolectric (`./gradlew.bat testDebugUnitTest`).

---

## 3. Security & Role Discipline

1. **Strict 3-Role Model:**
   * Valid roles are strictly **`OWNER`**, **`SHOP_STAFF`**, and **`KBOOK_ADMIN`** (per migration `V94__collapse_staff_roles`).
   * **Never** introduce legacy roles (`WAITER`, `CASHIER`, `MANAGER`, `KITCHEN`).
2. **Surface Boundary Enforcement:**
   * `SHOP_STAFF` is restricted to Android POS counter terminals.
   * Web Admin login sends `{ surface: 'web' }` (and unconditionally rejects `SHOP_STAFF` on Google SSO). The backend rejects `SHOP_STAFF` with `403 Forbidden` (`"Staff accounts cannot sign in to web admin."`).
3. **Multi-Tenant Isolation:**
   * Every SQL query against synced entities must enforce `restaurant_id = :restaurantId`.
   * Never trust client-provided tenant IDs; extract identity strictly from verified JWT claims.
4. **Staff Management Architecture (Zero Staff UI on Android POS):**
   * **Web Admin Exclusive:** Staff account creation, deactivation, 4-digit PIN setup, and permission delegation exist **100% in Web Admin (`apps/web-admin/src/app/pages/staff`)** and are restricted exclusively to the `OWNER` role (`@RequireRole({UserRole.OWNER})`).
   * **Zero Staff Management UI on Android:** The Android POS tablet contains **no screens, dialogs, or forms for managing staff** for ANY role (neither Staff nor Owner). Counter POS tablets are shared physical devices; stripping staff administration prevents physical credential tampering. Android POS only consumes staff data as a read-only entity (`UserEntity`) via MasterSync strictly for cashier counter sign-in and stamping `created_by_user_id` on bills/KOTs.
   * **Do Not Hallucinate Android Staff Screens:** If working on Android, never attempt to implement, search for, or bind staff creation/deletion UI on mobile. The permission keys `STAFF_ADD`, `STAFF_REMOVE`, and `STAFF_PERMISSIONS` exist in Android only for enum/sync contract parity with the server.

---

## 4. Entity & Domain Vocabulary Invariants

1. **Entity Table Names (Cloud PostgreSQL):**
   * Business profile table is **`restaurantprofiles`** (singleton per tenant).
   * Billing tables are **`bills`**, **`bill_items`**, and **`bill_payments`** (NEVER `orders`/`order_items`).
   * Menu tables are **`menuitems`**, **`categories`**, and **`itemvariants`**.
   * Terminal table is **`restaurant_terminal`**.
   * Raw materials and recipes are **`raw_materials`** and **`item_recipes`**.
2. **Composite Tuple Invariant:**
   * Synced entities inherit from `BaseSyncEntity` and share the composite unique constraint:
     `UNIQUE (restaurant_id, device_id, local_id)`
3. **Monotonic Status Versioning:**
   * Deliberate status changes on `bills` must increment `status_version` to prevent accidental overwrite by stale sync packets.
4. **Domain Terminology:**
   * Follow [`CONTEXT.md`](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/CONTEXT.md). Use "restaurant" / "sub-merchant", never "client" / "seller".

---

## 5. Module Build & Test Commands

### 5.1 Web Admin (`apps/web-admin/`)
* **Install Dependencies:** `npm install`
* **Development Server:** `ng serve` or `npm start`
* **Production Build:** `npm run build`
* **Run Unit Tests:** `npm test -- --watch=false --browsers=ChromeHeadless`
* **Type-Check:** `npx tsc --noEmit`

### 5.2 Android POS (`apps/android/`)
* **Debug APK Build:** `./gradlew.bat assembleDebug`
* **Run Unit Tests:** `./gradlew.bat testDebugUnitTest`
* **Run All Tests:** `./gradlew.bat test`
* **Lint Check:** `./gradlew.bat lint`

### 5.3 Server Services (`services/server/`)
* **Build (Maven):** `mvn clean package`
* **Run Local Tests:** `mvn test`
* **Run Spring Boot Dev:** `mvn spring-boot:run -Dspring-boot.run.profiles=dev`

---

## 6. UI/UX & Styling Discipline (Web Admin)

1. **Brand Blue:** Always use `--kb-color-primary: #0066cc;` for primary actions, active navigation items, and focus rings. Never introduce rogue purple (`#7c3aed`) or unvetted accent colors.
2. **Contrast Enforcement (WCAG 2.1 AA):** Every button, toggle label, and badge must maintain at least 4.5:1 contrast against its background. **Never** style `.toggle-btn--on` with white text on a white background.
3. **Table Row Densities:** Always use `white-space: nowrap; flex-wrap: nowrap;` for `.action-stack` inside table cells to preserve uniform ~`52px` row heights and prevent row ballooning.
4. **Modal Viewport Safety:** All modals must specify `max-height: min(88vh, 760px); overflow-y: auto;` to ensure footer actions remain visible on laptops and compact screens.
5. **Text Fields:** Use `<textarea rows="3">` for physical addresses with `min-height: 72px; resize: vertical;` to prevent horizontal text truncation.
6. **Sidebar Sign Out:** Must remain an understated secondary ghost button. On hover, apply subtle danger tint (`rgba(192, 57, 43, 0.08)`). Never style as an aggressive full-width solid red block.

---

## 7. Git & Change Discipline

* **Commit Message Format:** Follow Conventional Commits:
  `feat(module): description`, `fix(module): description`, `docs(module): description`, `test(module): description`.
* **Focused Changes:** Keep commits atomic. Do not mix unrelated formatting changes with bug fixes or new features.
* **Test Verification:** Before completing any coding turn, run the relevant test suite (`npm test`, `ng test`, or `mvn test`) and verify 100% passing tests.
