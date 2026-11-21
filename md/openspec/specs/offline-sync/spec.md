# Specification: Offline-First Synchronization

## Purpose
Provides bi-directional, deterministic data synchronization between offline Android POS terminals (SQLite/SQLCipher) and the centralized PostgreSQL server database.

## Requirements

### Requirement: Local Database Authority
The Android POS client SHALL write all billing and operational transactions to the local encrypted SQLite database first, ensuring zero latency and continuous cashier operation regardless of internet connectivity.

#### Scenario: Cashier creates order while offline
- **WHEN** internet connectivity is disconnected and an order is created or settled
- **THEN** the order is stored in Room SQLite with `is_synced = false`, printed immediately to local thermal hardware, and queued for synchronization.

---

### Requirement: Changed-Fields Delta Push
Sync push payloads SHALL include a `changed_fields` mask indicating specifically which column values were modified on the device.

#### Scenario: Server merges terminal push payload
- **WHEN** the server receives a batch push from a terminal with `changed_fields` specified
- **THEN** the server applies updates only to the fields declared in `changed_fields`, preserving other fields and server-owned states.

---

### Requirement: Server-Owned State Immutability
Server-owned attributes SHALL NOT be overwritten by client-originated push synchronization data.

#### Scenario: Client pushes stale payment or verification status
- **WHEN** a terminal pushes a bill record whose online payment status was verified directly by gateway webhook on the server
- **THEN** `preserveServerOwnedState` retains the server's gateway status, transaction reference, and reconciliation timestamps.

---

### Requirement: Sync Quarantine Isolation
Records that fail synchronization due to data corruption or unrecoverable constraint violations SHALL be quarantined to prevent infinite retry loops and UI disruption.

#### Scenario: Sync push encounters fatal constraint failure
- **WHEN** a record push fails deterministically (e.g. invalid catalog reference or foreign key violation)
- **THEN** `quarantineFailedSyncRecords` moves the record to `sync_quarantine_records`, increments the quarantine count badge, and allows the remaining sync queue to proceed.
