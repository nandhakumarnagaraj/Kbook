KhanaBook Root Cause Log

Purpose
- Track repeat issues, their root cause, the fix applied, and the prevention rule.
- Keep this file updated whenever a billing, sync, or logout/login issue is discovered.
- Multi-device invoice safety and the coupled migration decision are recorded in `PLAN.md`.

1) Completed bill comes back as draft after logout/login
- Symptom:
  - Session 1 creates bills and settles them.
  - After logout and login again, some completed bills appear as draft again or payment is requested again.
- Root cause:
  - Logout was soft by default, so the local Room database stayed on the device.
  - Bill settlement sync was started with `triggerImmediateSync()`, but that call is asynchronous.
  - A logout could happen before the completed-state push finished.
  - On re-login, the app reused local state and draft queries, so the unfinished sync could look like a draft again.
- Fix applied:
  - Logout now blocks when unsynced terminal bills exist (`completed`, `paid`, `cancelled`).
  - The app no longer allows those records to be discarded through a soft logout path.
- Prevention rule:
  - Do not rely on fire-and-forget sync for terminal billing states.
  - Do not log out while a settled bill is still unsynced.

2) Pay Before Food / Pay After Food workflow stability
- Symptom:
  - Order flow can become inconsistent if state changes are not synced immediately after draft creation or settlement.
- Root cause:
  - The home toggle changes billing mode, but the bill state still depends on local draft/completion updates reaching the server.
- Fix applied:
  - Trigger sync immediately after draft save.
  - Trigger sync immediately after bill completion / settlement.
- Prevention rule:
  - Keep `triggerImmediateSync()` after every draft insert and after every payment completion path.
  - Treat order-status changes as terminal events that must not be left pending across logout/login.

Implementation notes
- `BillingViewModel`
  - Calls `syncManager.triggerImmediateSync()` after draft save and after settlement.
- `LogoutViewModel`
  - Checks unsynced bills before logout.
  - Blocks logout if terminal bills are still unsynced.
- `BillDao`
  - Draft queries only include `order_status = 'draft'` and `payment_status = 'pending'`.
  - Completed bills are excluded from draft selection.

Working rule
- If a bug changes order state, payment state, or sync state, write:
  - symptom
  - root cause
  - fix
  - prevention rule
  - code location

3) Multi-device invoice collision and sync quarantine
- Symptom:
  - Two offline devices can print different bills with the same daily and
    lifetime order numbers; the second synchronized bill can be rejected or
    quarantined.
- Root cause:
  - Devices allocate from shared restaurant counters without coordination.
  - V22 enforces restaurant-wide lifetime/daily uniqueness, but conditionally
    skipped its indexes when duplicates already existed.
  - `lifetimeOrderId` is also used as a reconciliation fallback, coupling a
    display counter to bill identity.
- Immediate containment:
  - Permit one invoice-producing device per restaurant until the complete
    multi-device release passes all gates in `PLAN.md`.
  - Audit PostgreSQL, local databases, and Sync Center before migration.
- Planned fix:
  - Atomically ship terminal/invoice series allocation, removal of both V22
    indexes, series-aware uniqueness, and removal of lifetime-ID identity logic.
  - Use `publicToken` as canonical identity with `(deviceId, localId)` only for
    legacy fallback.
  - Make `lifetimeOrderId` nullable and legacy-only; new bills write null.
  - Never renumber issued invoices; historical duplicates require manual
    business/accounting reconciliation.
- Prevention rule:
  - Never use a human-readable counter as a canonical sync key.
  - Never enable a second terminal before offline collision, migration,
    reconciliation, and physical printing tests pass.
- Code locations:
  - `Android/app/src/main/java/com/khanabook/lite/pos/ui/viewmodel/BillingViewModel.kt`
  - `Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/MasterSyncProcessor.kt`
  - `Android/app/src/main/java/com/khanabook/lite/pos/data/local/dao/BillDao.kt`
  - `server/src/main/java/com/khanabook/saas/sync/service/GenericSyncService.java`
  - `server/src/main/java/com/khanabook/saas/repository/BillRepository.java`
  - `server/src/main/resources/db/migration/V22__bill_number_uniqueness_hardening.sql`
