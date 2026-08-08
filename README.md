# KhanaBook

KhanaBook is an offline-first restaurant billing and operations system for small food businesses. The repository contains the Android POS app, the Spring Boot backend, and an Angular web admin dashboard.

## Core Features

- Offline-first billing with background bidirectional sync.
- Multi-device restaurant ordering with per-terminal invoice series.
- Kitchen Order Ticket (KOT) event tracking for new, added, and voided items.
- Menu, category, variant, inventory, and staff management with waiter, cashier,
  manager, and shop-admin roles.
- GST-aware billing, part payments, refunds, and order reports.
- Bluetooth thermal printer support with ESC/POS output.
- OCR menu import using CameraX and Google ML Kit.
- Multi-tenant backend with JWT authentication, tenant isolation, request-size
  guards, rate limiting, bounded sync pagination, and token revocation.
- Public storefront and customer order APIs.
- Web admin dashboard with active orders, daily closing and cash
  reconciliation, business settings, order cancellation, CSV report export,
  and inline category creation.

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

## License

KhanaBook Server is licensed under the MIT License. See `server/LICENSE`.

KhanaBook Lite Android app is an internal/private project. All rights reserved.
