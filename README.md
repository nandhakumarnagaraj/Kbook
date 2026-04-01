# 🍱 KhanaBook: Built for the Heart of the Shop

Hey there! Welcome to the engine room of **KhanaBook**! 👋

Running a food stall, cafe, or a bustling restaurant is hard enough. KhanaBook is here to make the "business side" of things feel effortless. It is a companion for the people who keep our neighborhoods fed, whether it's the local biryani corner or a breakfast joint.

This repository contains both the **KhanaBook Lite (Android App)** and the **KhanaBook SaaS (Backend Server)**.

---

## 🔌 Why "Offline-First"? Because Life Happens.

Most billing apps break the moment the Wi-Fi drops. We built this differently. We know how it goes—the kitchen is heating up, orders are flying in, and suddenly... the internet goes out. In a busy shop, that shouldn't be a crisis. 

We built KhanaBook to be **offline-first**. It works whether you're in a basement cafe or a street-side stall.
- **⚡ Works Everywhere:** Create bills instantly without worrying about a signal.
- **🔄 Smart Syncing:** The moment you're back online, the app quietly syncs everything (bills, menu changes, users) to the server via our robust **Bidirectional Master Sync**. Your data is safely recorded without you ever having to hit "refresh."

---

## ✨ Features That Actually Matter

We focused on the things that actually matter when you're in the thick of it:

- **🥡 Your Menu, Your Way:** Organize your dishes into simple categories. We handle variants and pricing in a few taps.
- **🧾 Simple, Honest Billing:** GST, variants, and payments are handled in a flow that feels natural. Send professional-looking invoices directly to your customer's WhatsApp, or print them on the spot with a **Bluetooth thermal printer** ESC/POS integration.
- **📸 Smart Menu Import (OCR):** Too busy to type the menu? Just take a picture of your physical menu—KhanaBook uses Google ML Kit OCR to parse and import it instantly.
- **🔒 Your Data is Yours:** We use **Multi-Tenancy** on the server to strictly isolate restaurant data, and local **SQLCipher** (AES-256 encryption) on the app.

---

## 📱 Android App (KhanaBook Lite)

A mobile Point of Sale (POS) app that doesn't need the internet to work and keeps your data exactly where it belongs: with you.

### 🛠️ Tech Stack & Architecture
- **Language/UI:** Kotlin & Jetpack Compose (MVVM Architecture)
- **Dependency Injection:** Dagger Hilt
- **Local Database:** Room with SQLCipher for AES-256 encrypted local storage.
- **Networking/Sync:** Retrofit & OkHttp. Background sync handled aggressively and reliably via Android's **WorkManager** (`MasterSyncWorker`). 
- **AI/ML:** Google ML Kit (Text Recognition) & CameraX for OCR.
- **Hardware:** Bluetooth/Thermal Printer ESC/POS integration.
- **Authentication:** Google Sign-In, Firebase AppCheck, JWT Tokens, and offline fallback authentication.

### 🚀 Getting Started
1. **Requirements:** **Android Studio (Ladybug or newer)** and **JDK 17**.
2. **Setup Secrets:** Create `Android/local.properties` and keep credentials only there. You can copy the template from `Android/secrets.properties.example`. Required entries:
   ```properties
   BACKEND_URL=https://your-api-domain.com/
   GOOGLE_WEB_CLIENT_ID=your_google_web_client_id.apps.googleusercontent.com
   SIGNING_STORE_FILE=app/release-key.jks
   SIGNING_STORE_PASSWORD=your_keystore_password
   SIGNING_KEY_ALIAS=your_key_alias
   SIGNING_KEY_PASSWORD=your_key_password
   ```
   Do not commit `local.properties`, keystores, or signing files.
3. **Build & Run:** Open the `Android` project in Android Studio, sync Gradle, and run on a real device.

---

## 🖥️ Backend Server (KhanaBook SaaS)

The engine that receives synced offline data, handles conflict resolution, and ensures strict multi-tenant cloud storage.

### 🛠️ Tech Stack
- **Framework:** Java 17 & Spring Boot 3.5.x
- **Database:** PostgreSQL with Spring Data JPA & Hibernate
- **Migrations:** Flyway
- **Security:** Spring Security & stateless JWT Authentication
- **Rate Limiting:** Bucket4j (Protects Auth & Sync endpoints from brute force)
- **API Documentation:** SpringDoc OpenAPI (Swagger UI)

---

## 🔒 Production Hardening (Latest)

A full security, performance, and reliability overhaul was applied to the server and database. Here's what changed and why it matters.

### Database Integrity
- **Foreign Key Constraints:** All `server_*_id` relationship columns now have proper FK constraints (`DEFERRABLE INITIALLY DEFERRED` so batch sync inserts don't trip constraints mid-transaction). Orphaned records are no longer possible at the DB level.
- **CHECK Constraints:** Non-negative prices, positive quantity, and valid `auth_provider` enum values are enforced by the database — invalid data is rejected before it reaches the app layer.
- **24 New Indexes:** Added composite indexes for soft-delete filters (`restaurant_id, is_deleted`), FK join columns (partial, where set), `server_updated_at` for pull queries, and missing indexes on `menu_extraction_jobs` and OTP lookup.
- **Optimistic Locking:** A `version` column on every sync entity table. Hibernate now throws a `409 Conflict` if two devices try to overwrite the same record simultaneously — no more silent data loss.
- **Atomic Order Counter:** Daily order counter reset is now a single atomic SQL `UPDATE` — eliminates the race condition where two concurrent bill creates could both try to reset the counter on a new day.

### Security
- **JWT Revocation (Logout):** `POST /auth/logout` now revokes the token server-side via a `token_blocklist` table. Stolen tokens stop working immediately. Expired blocklist entries are cleaned up hourly.
- **JTI Claim in JWT:** Every token now carries a unique `jti` (JWT ID). The auth filter checks this against the blocklist on every authenticated request.
- **Rate Limiting on Sync:** `/sync/**` endpoints are now rate-limited (30 req/min per IP), separate from `/auth/**` (5 req/min). The limiter respects `X-Forwarded-For` for reverse proxy deployments.
- **Secured API Docs:** Swagger UI / OpenAPI docs now require authentication — not publicly browsable in production.
- **Actuator Access Control:** `/actuator/health` is public (for load balancer probes); all other actuator endpoints require the `KBOOK_ADMIN` role.
- **OTP Hardening:** Fixed OTP values via config (`fixed-otp`) removed entirely. OTPs are always random. OTP values are never written to logs.

### Performance
- **N+1 Query Fix:** The sync engine used to fire 2–3 DB queries per record to resolve foreign key IDs (e.g., 1,500 queries for a 500-item BillItem sync). It now bulk pre-loads all referenced IDs once per device batch — ~8 queries total regardless of payload size.
- **Open-In-View Disabled:** `spring.jpa.open-in-view=false` — lazy-loaded relationships no longer trigger hidden DB queries during JSON serialization.
- **Flyway Out-of-Order Disabled:** `spring.flyway.out-of-order=false` — prevents schema divergence during rolling deployments.
- **HikariCP Leak Detection:** Connection leak threshold set to 60 seconds — leaks are logged before they exhaust the pool.

### Reliability
- **TenantContext in Async Threads:** `AsyncConfig` now propagates `TenantContext` (tenant ID + role) into async worker threads via a `TaskDecorator`. Previously, `@Async` methods had no tenant context and would NPE under load.
- **Paginated Sync Pull:** `SyncRepository` now has `Page<T>` variants for all pull queries — ready for chunked sync responses to prevent OOM on large datasets.
- **Structured Error Responses:** All error responses now include `path` and a short `errorId` (logged server-side) to make support and debugging tractable.

### Observability
- **Actuator + Prometheus:** `spring-boot-starter-actuator` and `micrometer-registry-prometheus` added. `/actuator/health`, `/actuator/metrics`, and `/actuator/prometheus` are live.
- **Liveness/Readiness Probes:** Enabled for Kubernetes and Docker health check integration.
- **Logging Configuration:** Log levels set for `com.khanabook` (INFO), Spring Security (WARN), Hibernate SQL (WARN). Console pattern includes timestamp and thread name.

### Android
- **Migration Failure Visibility:** Silent `catch (_: Exception) {}` in `MIGRATION_30_31` replaced with `android.util.Log.w(...)` so schema migration issues appear in Logcat instead of being swallowed silently.

---

## 🍱 Previous Improvements

### Security & Architecture
- **Centralized OTP Handling:** All WhatsApp OTP generation and delivery logic lives on the server. Android clients no longer carry Meta/WhatsApp API tokens.
- **Persistent OTP Storage:** Dedicated `otp_requests` table with hourly cleanup. OTPs survive server restarts and work across multi-instance deployments.
- **Android Secret Hardening:** Backend URLs and Google Client IDs injected via `local.properties` and `BuildConfig` — not hardcoded in APK resources.

### Billing & Reports
- **Clean Order Numbering:** Daily Order IDs show as 3-digit counters (`001`, `002`) in tables and reports.
- **Integrated Sharing:** WhatsApp share button on every Order Details row for instant invoice delivery.

### Hardware & Connectivity
- **Robust Printer Discovery:** Bluetooth scanning reliably detects new/unpaired thermal printers. Automatic Location/GPS status checks for Android hardware discovery.
- **Unified Printer State:** Singleton Bluetooth manager keeps printer connected across all screens.

### WhatsApp Smart Sharing
- **Smart Two-Step Flow:** Supports unsaved numbers — opens correct customer chat and prompts to attach the PDF invoice.
- **Android 14 Compatibility:** Fixed PDF attachment issues via explicit URI permission granting (`ClipData`).

---

## 📜 License & Privacy

**KhanaBook Server:** MIT License. See the `server/LICENSE` file for more details.  
**KhanaBook Lite (Android):** Internal/private project. All rights reserved.

Happy Billing! ☕🥡
