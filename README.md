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
- Menu, category, variant, inventory, and staff management.
- GST-aware billing, part payments, refunds, and order reports.
- Bluetooth thermal printer support with ESC/POS output.
- OCR menu import using CameraX and Google ML Kit.
- Multi-tenant backend with JWT authentication and tenant isolation.
- Public storefront and customer order APIs.
- Web admin dashboard for platform and business administration.

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
