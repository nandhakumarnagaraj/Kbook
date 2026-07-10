# KhanaBook Engineering Plan

Status: Final and verified

## 1. Product Scope

KhanaBook is an offline-first POS for Indian QSRs and small dine-in
restaurants. The supported product scope is:

- GST billing with manual UPI, cash, and POS payments.
- Pay-before-food and pay-after-food workflows.
- Customer receipt and kitchen KOT printing.
- Menu, inventory, reports, synchronization, and public invoices.
- One to three Android terminals per restaurant after multi-device safety ships.

Deferred scope:

- Storefront ordering.
- Easebuzz gateway verification.
- Gateway refunds.
- Complex staff roles.
- Multi-branch management.

## 2. Immediate Operational Containment

Multi-device billing is currently unsafe. All devices share the restaurant's
`lifetime_order_counter`, while V22 attempts to enforce unique active lifetime
and daily order numbers. Two offline devices can print the same invoice number,
after which one bill is rejected or quarantined during synchronization.

Until Section 4 ships:

- Permit only one invoice-producing device per restaurant.
- Do not activate a second billing terminal.
- Audit PostgreSQL for duplicate active `(restaurant_id, lifetime_order_id)`
  groups.
- Audit PostgreSQL for duplicate active
  `(restaurant_id, last_reset_date, daily_order_id)` groups.
- Review `sync_quarantine_records` before changing constraints.
- Preserve every issued invoice and never silently renumber historical bills.
- Treat already-issued duplicate numbers as a business/accounting
  reconciliation problem, not something a schema migration can repair.

V22 created its unique indexes only when no duplicates existed. A production
database that already contained duplicates may not have either index.

## 3. P0 Contract Bugs

These are live correctness defects, not future features.

### 3.1 Source Channel

Android stores and pushes `sourceChannel`, but the server currently has no
corresponding column, entity field, or DTO field. Zomato, Swiggy, and other
source values are therefore lost during synchronization.

Required work:

- Add `source_channel` through a server migration.
- Add it to `Bill.java`, `BillDTO.java`, sync mapping, OpenAPI, and admin
  responses.
- Preserve it through both push and pull.
- Backfill from `payment_mode` only for unambiguous values such as Zomato,
  Swiggy, and POS. Leave ambiguous values null.

### 3.2 Refunds

Refunds are server-owned but do not currently round-trip to Android. Android
has no refund state in `BillEntity`, the pull DTO omits it, and Android pushes a
hardcoded refund value of `0.00`.

Required work:

- Add refund fields to Android Room and pull responses.
- Remove refund fields from Android-owned push state.
- Prevent Android sync from overwriting server refund state.
- Bump `updatedAt` and `serverUpdatedAt` whenever admin records a refund.
- Recalculate Android reports from synchronized refund state.
- Add an integration test covering admin refund, Android pull, and report
  output.

## 4. Coupled Multi-Device Identity Release

Invoice allocation, V22 constraints, and lifetime-ID reconciliation form one
atomic compatibility change. None may ship independently.

### 4.1 Canonical Bill Identity

Use `publicToken` as the immutable bill UUID. Android already creates it before
the first sync, so it is available while offline.

Required work:

- Backfill null local and server tokens before enforcing constraints.
- Upgrade the plain V19 `idx_bills_public_token` index to a unique constraint.
- Match bills by `publicToken`, then use `(deviceId, localId)` only as a legacy
  fallback.
- Never match or merge bills by `lifetimeOrderId`.
- Never log or expose the token outside its required public-invoice use.

### 4.2 New Numbering Fields

Add:

- `terminalSeries`
- `financialYear`
- `invoiceSeries`
- `invoiceSequence`
- `invoiceNumber`

Example display:

```text
Order    A1-0042
Invoice  26A1-000042
```

The invoice format must remain within the GST 16-character requirement and use
a consecutive sequence within each financial-year series.

### 4.3 Lifetime Order ID Decision

Selected decision: Option A.

`lifetimeOrderId` becomes nullable and legacy-only:

- Drop the PostgreSQL `NOT NULL` constraint.
- Change server entity and DTO `Long` fields to nullable semantics.
- Change Android `BillEntity` and DTO fields to `Long?`.
- Bump the Room schema and add a tested migration.
- New bills write null.
- Historical bills retain their old value for legacy display only.
- No production code uses it for allocation, uniqueness, reconciliation, or
  canonical lookup.

### 4.4 Expand Migration

The migration must be idempotent and tolerant of production databases on which
V22 skipped index creation.

1. Add all new columns as nullable.
2. Audit and resolve internal legacy duplicates before adding new constraints.
3. Backfill existing bills into a legacy series without changing printed
   invoice numbers.
4. Derive each historical bill's financial year from its own issue date.
5. Assign collision-safe internal legacy sequences without claiming to repair
   duplicate printed documents.
6. Create the terminal and invoice-series assignment table.
7. Backfill null `public_token` values and enforce uniqueness.
8. Run `DROP INDEX IF EXISTS ux_bills_restaurant_lifetime_order_active`.
9. Run `DROP INDEX IF EXISTS ux_bills_restaurant_daily_order_active`.
10. Add guarded uniqueness on
    `(restaurant, financialYear, invoiceSeries, invoiceSequence)`.
11. Add guarded order uniqueness on
    `(restaurant, orderDate, terminalSeries, dailyOrderId)`.
12. Make `lifetime_order_id` nullable and legacy-only.

### 4.5 Remove Lifetime-ID Dependencies

All of the following must change in the same release:

- Replace post-insert lifetime lookups in `BillingViewModel` with the returned
  local bill ID or `publicToken`.
- Replace `markBillsSyncedByLifetimeIds` with reconciliation by `publicToken`
  and legacy `(deviceId, localId)`.
- Remove post-pull lifetime-counter correction from `MasterSyncProcessor`.
- Remove counter-raise repair flows in `SettingsViewModel`,
  `RestaurantRepository`, and related DAO methods.
- Remove server lookup and conflict detection by `lifetimeOrderId` from
  `BillRepository` and `GenericSyncService`.
- Update local duplicate checks, Sync Center diagnostics, reports, invoice
  lookup, repair tools, and tests.
- Continue linking child records through `serverBillId`, never through invoice
  numbers.

### 4.6 Deployment Compatibility

Use an expand, migrate, contract rollout:

1. Deploy an expand-compatible server with nullable new columns and support for
   both contracts.
2. Upgrade every active Android billing device.
3. Keep restaurants in single-device mode until every terminal uses the new
   contract.
4. Accept old unsynced bills through the `(deviceId, localId)` fallback.
5. Enforce a minimum app version before enabling multi-device billing.

## 5. Android Allocation Logic

- First online activation assigns a permanent terminal series from the server.
- Reserve the next order or invoice sequence and save the bill, items, and
  payments in one Room transaction.
- Drafts receive an order number but no invoice number.
- Allocate the invoice number only during successful settlement.
- Never renumber a settled or printed invoice.
- Give a reinstalled device a new series unless its encrypted database and
  device identity are restored.
- Pre-provision the next financial-year series before rollover.

## 6. Multi-Device KOT Safety

Delta printing already works through `sentToKot` and must remain during the
migration. KOT events add the missing guarantees:

- Cross-device print ownership.
- Audit history.
- Immutable item snapshots.
- Crash ambiguity through an `UNKNOWN` state.
- New, add-item, and void revisions.

Example:

```text
A1-0042/K1  NEW
A1-0042/K2  ADD
A1-0042/K3  VOID
```

Use `publicToken + kotRevision` as event identity. The originating device
automatically prints its events. Pulled events never automatically print on a
different terminal. The existing local-bill-ID kitchen queue remains local in
V1. A shared kitchen print hub is deferred.

## 7. Logout Verification

Verify the existing implementation before changing it:

- An unsynced completed bill blocks normal logout.
- Failed immediate sync preserves the database.
- Force logout cannot bypass unsynced terminal bills.
- Login restores the completed state.
- Hard data deletion requires explicit confirmation.

Change the exception fallback only if on-device testing demonstrates a bypass.

## 8. Sync and Scale

After identity correctness:

- Add cursor-based pagination to master pull.
- Use the existing `mergeMasterSyncPages` client seam.
- Guarantee a follow-up run when data changes during an active `KEEP` sync.
- Add Android/server contract tests for every replicated field.
- Monitor quarantine count, conflict reasons, payload size, and sync duration.
- Keep server-owned refund state protected from Android overwrites.

## 9. Deployment Automation

This is a new engineering project; the complete pipeline does not exist today.

Target flow:

```text
mvn test
-> package JAR
-> PostgreSQL backup
-> record exact Git commit
-> restart service
-> health and OpenAPI smoke tests
-> retain a rollback command and previous JAR
```

Existing pieces are the backup script and health endpoint. Missing pieces are
test gating, Git commit exposure, previous-JAR retention, rollback, and an
automated Git pull/rebuild flow.

- Expose the deployed commit through build metadata or injected `GIT_COMMIT`.
- Remove `-DskipTests` from production deployment after the suite is stable.
- Never run Flyway against production without a verified backup and rollback
  plan.

## 10. Execution Sequence and Release Gates

Execution order:

```text
Containment
-> P0 sourceChannel and refund fixes
-> coupled identity, V22, and reconciliation migration
-> KOT ownership and physical printing verification
-> enable multi-device
-> pagination, deployment automation, and integrations
```

Multi-device billing stays disabled until:

- Two offline devices produce unique orders and invoices.
- V22 legacy indexes are dropped with `IF EXISTS`.
- The expand migration is idempotent on a duplicate-containing database copy.
- `lifetimeOrderId` is nullable and no production code uses it for matching,
  reconciliation, uniqueness, allocation, or canonical lookup.
- Server and local child rows remain attached through `serverBillId` and
  `publicToken`.
- `sourceChannel` round-trips without loss.
- Admin refunds appear correctly on Android.
- KOT additions print once automatically on the originating device only.
- Logout and login preserve every terminal bill.
- Migration, rollback, and physical printer tests pass.
- Already-issued duplicate invoice numbers have been manually audited and
  reconciled outside the schema migration.
