# KhanaBook

KhanaBook is an offline-first restaurant billing and operations system for small food businesses. The repository contains the Android POS app, the Spring Boot backend, and an Angular web admin dashboard.

## Repository Layout

```text
Android/      Android POS app built with Kotlin and Jetpack Compose
server/       Spring Boot SaaS backend and REST APIs
web-admin/    Angular admin dashboard
ops/          Production backup, restore, and deployment notes
```

## Core Features

- Offline-first billing with background bidirectional sync.
- Multi-device restaurant ordering with per-terminal invoice series.
- Kitchen Order Ticket (KOT) event tracking for new, added, and voided items.
- Menu, category, variant, inventory, and staff management.
- GST-aware billing, part payments, refunds, and order reports.
- Bluetooth thermal printer support with ESC/POS output.
- OCR menu import using CameraX and Google ML Kit.
- Multi-tenant backend with JWT authentication and tenant isolation.
- Public storefront and customer order APIs.
- Web admin dashboard for platform and business administration.

## Multi-Device POS Model

KhanaBook supports one restaurant account running on multiple Android devices.
Each device is activated as a terminal and receives its own terminal series, such
as `A1`, `A2`, or `A3`. New invoice numbers are allocated with the financial
year plus terminal series, for example:

```text
26A1-000001
26A2-000001
```

Bills are reconciled by immutable `publicToken` identity during sync. The legacy
`lifetimeOrderId` field is retained only for old bill display/search fallback and
is not used for new invoice allocation or sync reconciliation.

KOT printing is event-based:

- `NEW` event for a new kitchen-facing order.
- `ADD` event when new items are added to an active order.
- `VOID` event when items are reduced or removed.
- Event identity is `publicToken + kotRevision`.
- Auto KOT printing is guarded so a synced bill from another terminal does not
  duplicate-print on the current device.

Before production rollout, validate this flow on two real Android devices and a
real kitchen printer. Printer behavior, offline queueing, and sync timing are
hardware-sensitive.

## Tech Stack

### Android App

- Kotlin
- Jetpack Compose
- Room with SQLCipher
- Dagger Hilt
- Retrofit and OkHttp
- WorkManager
- CameraX and Google ML Kit
- Firebase App Check and Google Sign-In

### Backend

- Java 17
- Spring Boot 3.5.x
- Spring Security
- PostgreSQL
- Flyway
- Spring Data JPA and Hibernate
- SpringDoc OpenAPI
- Actuator and Prometheus metrics

### Web Admin

- Angular
- TypeScript
- Standalone Angular components

## Getting Started After Changing Laptops

This project is already connected to GitHub through the `origin` remote:

```bash
git remote -v
```

Expected remote:

```text
origin  https://github.com/nandhakumarnagaraj/Kbook.git (fetch)
origin  https://github.com/nandhakumarnagaraj/Kbook.git (push)
```

To refresh the project on a new laptop:

```bash
git fetch origin
git status
git pull origin main
```

If GitHub asks for authentication, sign in with Git Credential Manager or use a GitHub personal access token for HTTPS authentication.

## Android Setup

Requirements:

- Android Studio Ladybug or newer
- JDK 17
- A real Android device for printer, camera, and full POS testing

Create `Android/local.properties` from `Android/secrets.properties.example` and keep secrets out of Git:

```properties
BACKEND_URL=https://your-api-domain.com/
GOOGLE_WEB_CLIENT_ID=your_google_web_client_id.apps.googleusercontent.com
SIGNING_STORE_FILE=app/release-key.jks
SIGNING_STORE_PASSWORD=your_keystore_password
SIGNING_KEY_ALIAS=your_key_alias
SIGNING_KEY_PASSWORD=your_key_password
```

Run the app:

1. Open the `Android` folder in Android Studio.
2. Sync Gradle.
3. Select a device.
4. Run the app.

Do not commit `local.properties`, keystores, signing files, or production credentials.

## Backend Setup

Requirements:

- JDK 17
- Maven
- PostgreSQL

Create a local backend config from the example:

```bash
cp server/src/main/resources/application-dev.properties.example server/src/main/resources/application-dev.properties
```

Run the backend:

```bash
cd server
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
cd server
.\mvnw spring-boot:run
```

## Web Admin Setup

Requirements:

- Node.js
- npm

Run the Angular dashboard:

```bash
cd web-admin
npm install
npm start
```

Update `web-admin/src/environments/environment.ts` if the backend API URL is different in your local setup.

## Docker

Local stack:

```bash
docker compose up --build
```

Production stack:

```bash
docker compose -f docker-compose.production.yml up --build -d
```

Review `ops/PRODUCTION_STACK.md` before deploying production services.

## Testing

Backend tests:

```bash
cd server
./mvnw test
```

Android unit tests:

```bash
cd Android
./gradlew test
```

Focused KOT repository tests:

```bash
cd Android
./gradlew testDebugUnitTest --tests com.khanabook.lite.pos.BillRepositoryTest
```

Android instrumented tests:

```bash
cd Android
./gradlew connectedAndroidTest
```

Web admin tests:

```bash
cd web-admin
npm test
```

## Multi-Device Acceptance Test

Use this checklist before releasing multi-device/KOT changes:

1. Install the same APK on two Android devices.
2. Log in to the same restaurant on both devices.
3. Confirm each device has a different terminal series, for example `A1` and `A2`.
4. Create an order on Device A and confirm invoice numbering uses Device A's series.
5. Create an order on Device B and confirm invoice numbering uses Device B's series.
6. Print KOT from Device A and confirm it prints once.
7. Sync Device B and confirm Device B does not duplicate-print Device A's KOT.
8. Add an item on Device A and confirm only the new item prints.
9. Reduce or remove an item and confirm the `VOID` event is recorded.
10. Settle orders from both devices and confirm the backend/admin shows both bills.

## Git Workflow

Check your branch and pending files:

```bash
git status
```

Commit changes:

```bash
git add README.md
git commit -m "Update README"
```

Push to GitHub:

```bash
git push origin main
```

## License

KhanaBook Server is licensed under the MIT License. See `server/LICENSE`.

KhanaBook Lite Android app is an internal/private project. All rights reserved.
