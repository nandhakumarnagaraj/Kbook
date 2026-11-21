# 09 — Architecture Audit

## 1. Architecture as built

A **modular monolith with an offline-first mobile client**:

```
Android POS (Room, offline-first) ──┐
                                    ├── HTTPS/JSON ──► Spring Boot monolith ──► PostgreSQL
Web Admin (Angular, online) ────────┘        │
                                             └──► Easebuzz, SMTP/SMS/WhatsApp, FCM, object storage (CDN)
```

- **Server:** one deployable, 16 feature packages, layered controller → service → repository. No service-to-service network calls inside the product.
- **Android:** single `:app` module, Room as the source of truth on device, a sync engine (`MasterSyncProcessor` pull, `GenericSyncService` on the server) reconciling master data, and a push path for bills.
- **Web Admin:** Angular SPA against the same API, split by role (owner console vs `KBOOK_ADMIN` platform console).

## 2. Decision quality

**Good decisions, evidenced in code:**

1. **DTO boundary at the sync edge.** `KhanaBookApi.kt` states push "uses SyncDto, NOT Room entities (fix #1)". Without this, every local schema change becomes an API breaking change.
2. **Idempotent offline push** via `bills(restaurant_id, device_id, local_id)` (Flyway `V1:304`). This is the correct primitive for replay-after-reconnect.
3. **Offline auth via master pull.** The comment on `GET /sync/master/pull` notes it returns the user's own `grantedPermissions`/`permissionRevision` "which drives offline auth" — permissions are cached deliberately so a POS keeps working when the network drops.
4. **Two-gate authorisation** (URL matcher + `@RequireRole`), which survives someone editing one of them.
5. **Forward-only migrations** with an intent-named file per change.
6. **Design tokens enforced by scripts** in the web admin rather than by convention.
7. **Failure behaviour chosen explicitly for hardware absence** — KOTs queue in Room when no printer is paired (`KNOWN_LIMITATIONS.md` §3) instead of erroring the sale.

**Trade-offs that are now costs:**

| Decision | Cost today |
| --- | --- |
| Single Gradle/Maven module per surface | build times; 2,387-line `AppDatabase.kt`; no enforced boundary between features |
| `TenantContext` as a static/thread-local convention | no compiler or ORM enforcement of tenant scoping (AUD-003) |
| "Generic" sync service handling all entities | `GenericSyncService` 1,704 lines, `MasterSyncProcessor` 2,054; every new field touches the largest files in both codebases |
| Aggregation modules (`business`, `platform`) re-exposing leaf data | changes ripple through modules that do not own the concept |
| Thin test gate for Android | red `main` was possible (AUD-001) |

## 3. SOLID / coupling assessment [C]

- **SRP:** mostly respected at class level (controllers thin, services hold rules). Violated at *file* level in the five god-files listed in `01` and `13`.
- **OCP:** the sync layer is the weak point — adding an entity means editing a central `when`/switch rather than registering a handler.
- **LSP/ISP:** no notable violations found.
- **DIP:** constructors receive dependencies (Hilt on Android, Spring injection on the server); no service instantiates its own repository.
- **DRY:** one clear violation — the duplicated phone/email OTP state machines in `ShopConfigSection.kt` (AUD-012). A second, smaller one: two Android CI workflows covering the same job (AUD-019).
- **Boundary leakage:** `TenantContext` is read from 55 files, which is the practical definition of a global. It is convenient and it is the reason tenant isolation cannot be reasoned about locally.

## 4. Findings

| ID | Priority | Finding | Evidence |
| --- | --- | --- | --- |
| AUD-003 | P1 | No ORM-level tenant enforcement; tenancy is a convention across 55 files | [V] |
| AUD-009 | P3 | Five god-files hold disproportionate risk (3,901 / 2,398 / 2,387 / 2,054 / 1,695 lines) | [V] |
| AUD-012 | P2 | Duplicated OTP state machine inside one composable | [V] |
| AUD-013 | P3 | Business rules embedded in composables, untestable without UI | [V] |
| AUD-014 | P2 | Broad `catch (Exception)` without logging the cause | [C] |
| AUD-019 | P3 | Duplicate Android CI workflows (`ci.yml` + `android-ci.yml`) both triggering on `apps/android/**` | [V] |
| AUD-020 | P2 | Sync layer is open-for-modification only: a new entity requires editing the two largest files in both codebases | [V] |

## 5. Recommended structural changes (no new capability)

Ordered by value per unit of risk; each keeps existing tests as the safety net.

1. **Tenant filter at the persistence boundary** (AUD-003). Highest value, lowest surface area: one filter + admin bypass + per-module cross-tenant tests.
2. **Make the Android compile/test gate blocking** so structural refactors are safe to attempt (pairs with `12`).
3. **De-duplicate the OTP flows** (AUD-012) — a pure extraction, with the email flow's extra behaviour preserved as a parameter.
4. **Split `AppDatabase.kt` along entity boundaries** (AUD-015) after the migration gate is live.
5. **Register sync handlers instead of extending a switch** (AUD-020) — only when a new syncable entity is next added; do not do this speculatively.
6. **Move dashboard/menu logic into services** (AUD-009) one tab at a time.
7. **Delete one of the two Android workflows** (AUD-019).

Explicitly **not** recommended at this stage: microservices, a Gradle feature-module split, or an event bus. Each would add operational surface without addressing a demonstrated failure. The architecture's problems are enforcement and file size, not topology.
