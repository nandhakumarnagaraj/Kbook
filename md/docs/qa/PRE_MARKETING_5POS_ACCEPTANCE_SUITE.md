# KhanaBook — Pre-Marketing Acceptance Test Suite (5 POS + Web Admin)

Purpose: prove the six release-gate invariants on real hardware before customer onboarding.
This is a **human-run** suite. Nothing here is marked PASS from source inspection — the
whole point is the Room → sync queue → network → Spring → PostgreSQL → ack → local-state
loop, which only real devices exercise.

## The six release-gate invariants (all must be 🟢 before marketing)
1. Financial correctness — bills/payments never duplicated, lost, or retroactively changed.
2. Multi-terminal correctness — 5 devices operate simultaneously without identity/numbering corruption.
3. Offline correctness — offline transactions survive crash/restart and eventually sync.
4. Convergence — after sync, all POS + Web Admin + server reach the same deterministic state.
5. Security — offline authorization can't create a tenant/terminal/permission bypass.
6. Kitchen correctness — KOT never silently lost; retry deterministic and visible.

## GOLDEN RULE — capture state everywhere, before/during/after
For every test, fill this matrix. The failure you're hunting is **silent divergence**, not
just an error dialog. "The bill succeeded" is NOT a pass. "All seven columns converged to
the same correct value" is.

```
Entity: __________________________
              BEFORE      DURING       AFTER-SYNC
POS-1         ______      ______       ______
POS-2         ______      ______       ______
POS-3         ______      ______       ______
POS-4         ______      ______       ______
POS-5         ______      ______       ______
Web Admin     ______      ______       ______
Server (SQL)  ______      ______       ______
CONVERGED?  [ ] yes  [ ] no — divergence: __________
```

### Business State vs Technical State (avoid false convergence failures)
"Convergence" does NOT mean all seven copies are byte-identical. Server-generated technical
metadata legitimately differs per replica. Compare only the BUSINESS-RELEVANT authoritative
state; explicitly EXCLUDE technical metadata.
- Business state (MUST converge): bill total, invoice number, order status, payment amount +
  mode + operation_id, item price/tax on a bill, menu availability/price (final value),
  KOT printed-state (business outcome), terminal ownership.
- Technical metadata (may differ; do NOT flag): `updatedAt`/`serverUpdatedAt`, sync cursor,
  local Room row version/id, last-seen, local sync-status flags, per-device timestamps.
Convergence pass = all replicas agree on business state, with technical differences excluded.

## EVIDENCE BLOCK — capture exact IDs & timestamps per test (not just state values)
A state value ("Enabled/Disabled") tells you WHAT diverged; IDs + timestamps tell you WHERE
in the Room → sync queue → request → server txn → response → local-ack pipeline it broke.
Record for every test run:

```
Restaurant ID:            ______
Terminal ID (per device): POS-1 ___  POS-2 ___  POS-3 ___  POS-4 ___  POS-5 ___
User ID:                  ______
Bill/order LOCAL id:      ______      Server bill/order id: ______
Invoice number:           ______
Payment operation_id:     ______      gateway_txn_id: ______
KOT / event id:           ______
Device local timestamp:   ______      Server timestamp: ______
Network state:            ______
Sync status BEFORE:       ______      Sync status AFTER: ______
Quarantine id / reason:   ______
```

## FAILURE PROTOCOL — record first, reproduce, then STOP (do not fix during the run)
On any failure, capture before touching code:
```
EXPECTED:
ACTUAL:
DEVICE STATES (all 5):
SERVER STATE (SQL):
SYNC LOG:
TIMELINE (device-local, to the second):
REPRODUCIBLE? YES / NO   (reproduce at least once before reporting)
```
Example timeline to mirror:
```
14:02:10  POS-2 offline
14:02:32  Bill created (localId=…)
14:02:34  Payment saved locally (operation_id=…)
14:02:35  App killed
14:05:10  App reopened
14:06:00  Network enabled
14:06:03  Sync started
14:06:04  Server accepted bill (serverId=…)
14:06:04  Server accepted payment
14:06:05  Client marked synced
```
Then diff the timeline against the DB to locate the break (Room / queue / request / server
txn / response / local-ack). Classify each failure as: real bug | expected offline behavior
| architecture limitation | missing product rule.

## Device roles (use these, not 5 identical happy-paths)
| Device | Role during tests |
|---|---|
| POS-1 | Online |
| POS-2 | Offline |
| POS-3 | Flaky network (drop/reconnect) |
| POS-4 | Online + actively editing orders |
| POS-5 | Offline → reconnecting |
| Web Admin | Changing configuration |

## Server state-capture queries (read-only; run per checkpoint)
```sql
-- Bills for restaurant R
SELECT id, invoice_number, order_status, payment_status, total_amount, terminal_id, updated_at
  FROM bills WHERE restaurant_id = :R ORDER BY id;
-- Payments
SELECT id, bill_id, amount, payment_mode, gateway_txn_id, operation_id FROM bill_payments WHERE restaurant_id = :R;
-- Quarantine backlog
SELECT * FROM sync_quarantine WHERE restaurant_id = :R;
-- Menu item state
SELECT id, name, base_price, is_available, updated_at FROM menu_items WHERE restaurant_id = :R;
```

---

# TESTS (run in this priority order)

## LAB-01 — Payment crash recovery 🔴 (Invariant 1,3)
Devices: POS-2 (offline)
Initial: menu synced on all devices.
Steps:
1. POS-2 → airplane mode.
2. POS-2 → create bill ₹500, add items, record CASH payment, finalize.
3. Capture BEFORE-sync: POS-2 local bill + payment rows.
4. Force-stop the app (kill from recents / adb force-stop) immediately after payment save.
5. Reopen app (still offline) — capture: is the bill+payment still present, status?
6. Re-enable network — let it sync. Capture AFTER on POS-2 + server SQL.
Invariant: exactly ONE bill and ONE payment on server; local status = synced; amount unchanged.
Record: server `bill_payments` count for that bill; local row; any duplicate.
PASS: no duplicate, no lost payment, deterministic status.
Also run the 9 crash points (before save / during Room txn / after bill / before payment /
after payment / before KOT-state / during sync / after server-accept / before local-ack).

## LAB-03 — Five-device concurrent billing 🔴 (Invariant 2,4)
Devices: all 5 online.
Steps:
1. On a count of 3, all 5 finalize a bill within ~2 seconds.
2. Capture AFTER on each device + server: invoice_number, order id, terminal_id, daily counter.
Invariant: 5 distinct invoice numbers, correct per-terminal series, no duplicate order/invoice id.
PASS: zero collisions; each terminal's series advanced by exactly 1.
Repeat variant: POS-1 offline, POS-2 online, POS-3 offline, POS-4 online, POS-5 reconnecting —
then reconnect offline ones; re-check numbering convergence.

## LAB-02 — Terminal revoked while offline 🔴 (Invariant 5) [product decision]
Devices: POS-3.
Steps:
1. POS-3 offline.
2. Web Admin → deactivate/remove POS-3.
3. POS-3 (offline) → create 2 bills.
4. POS-3 → reconnect. Capture AFTER on POS-3 + server + quarantine.
Invariant: outcome is DEFINED and documented — bills accepted OR rejected OR quarantined,
and the user is informed. No silent unauthorized bills.
PASS: deterministic, visible, documented behavior (not necessarily "accepted").

## LAB-04 — KOT retry/crash 🔴 (Invariant 6) — CORRECTED CRITERIA
Devices: POS-2 (offline).
Steps:
1. POS-2 offline → create order → print KOT (real printer).
2. Kill app immediately after the print fires (before local ack).
3. Reopen → observe KOT/sentToKot state → reconnect → observe sync.
Do NOT assert "prints exactly once" unless the architecture guarantees it. Instead classify
the observed behavior into exactly one of:
- duplicate print (same items printed twice)
- lost print (items never printed, no record)
- retryable print (system offers/does a visible retry)
- acknowledged print (printed once, recorded once)
Invariant: system must NEVER silently lose a KOT; retry must be deterministic and visible.
PASS: no silent loss; behavior is one of the above and is documented + visible to staff.
Note: cross-device KOT ownership is a known deferred gap (KotEvent is Android-only, no server
table) — do not expect cross-device delta-KOT convergence; record what actually happens.

## LAB-05 — Offline draft + server-side menu deletion 🟠 (product decision)
Devices: POS-1 (offline).
Steps:
1. POS-1 offline → draft with Item A (2×).
2. Web Admin → delete Item A.
3. POS-1 → finalize draft → reconnect. Capture AFTER on POS-1 + server + quarantine.
Invariant: bill retains Item A snapshot (price/tax captured at add time) and either syncs or
quarantines deterministically; user informed if rejected.
PASS: historical bill financially intact; outcome documented.

## LAB-06 — Midnight / offline daily counter 🟠 (business rule)
Devices: POS-1, then repeat across all 5.
Steps:
1. Set scenario time near 23:59 (or test at real midnight) — POS-1 offline creates a bill.
2. Reconnect at 00:05. Capture: business date, daily counter, invoice number/series, which
   daily-closing/report the bill lands in — on device + server.
Invariant: business-day attribution + daily counter behavior is documented and consistent
across all 5 terminals.
PASS: deterministic, documented rule.

## LAB-07 — Rush-hour convergence (the big one) (Invariant 4)
Devices: all 5 in their assigned roles + Web Admin.
Steps (run ~15 min of mixed traffic):
- POS-1 dine-in bills; POS-2 offline takeaway; POS-3 flaky (toggle network); POS-4 online
  editing orders; POS-5 offline→reconnect; Web Admin changes price + disables one item +
  edits a permission mid-stream.
- Randomly: kill an app, drop network, reconnect a printer, reprint a KOT.
End: reconnect everything; wait for full sync.
Verify with the GOLDEN RULE matrix for: menu item, price, tax, category, bills, payments,
KOT, customer, terminal, staff permission.
Invariant: expected transactions == server DB == each POS local DB == Web Admin. Zero
unexplained financial/data discrepancies.
PASS: full convergence; every divergence has a documented, acceptable explanation.

## LAB-08 — Concurrent field edits / write skew 🟠 (Invariant 4) [proves LWW limitation]
Devices: POS-1 offline, POS-2 offline, POS-4/Admin.
Steps:
1. POS-1 offline → set Item A name = "Chicken Biriyani".
2. POS-2 offline → set Item A price = ₹250.
3. POS-4/Admin → a third edit to Item A (e.g. category).
4. Reconnect in different orders across repeats.
Verify (business state): which complete row wins? Was the name lost? Was the price lost?
Do all devices converge to one row? Is the result financially safe (no bad price on a bill)?
Invariant: converges deterministically; any lost field is a documented LWW effect, not corruption.
PASS: convergence + no financial impact; lost-field behavior documented.

## LAB-09 — Partial batch failure 🟡 (Invariant 3,4) [proves quarantine recovery]
Devices: POS-2 offline.
Steps:
1. Create offline transactions A, B, C, D, E.
2. During sync force: A ok, B ok, C ok, D forced failure, E pending.
3. Recover network/server; let sync resume.
Verify: A/B/C not duplicated; D retried/quarantined correctly; E eventually syncs; cursor not
corrupted; no permanent sync loop; all devices converge.
Invariant: per-record quarantine recovery works; zero duplicate/lost records.
PASS: exactly A–E on server once each (or D visibly quarantined with reason); no loop.

## LAB-10 — Causal ordering 🔴 (Invariant 4) [tests undefined area directly]
Devices: POS-2 offline.
Steps:
1. POS-2 offline → create Menu Item A → then create Bill containing Item A.
2. Reconnect under a controlled condition where the dependent bill can arrive before the item.
Verify: server response; quarantine?; retry behavior; final bill; final menu; local sync status;
convergence.
Invariant: dependent bill is never silently lost; resolves or quarantines deterministically.
PASS: deterministic + visible outcome; bill+item converge or quarantine with reason. (This is a
🔴 undefined area — record the actual behavior; it feeds Product Decision 1.)

---

# Menu LWW convergence sub-test (Scenario A, both directions)
Because menu conflict resolution is last-write-wins (no field merge), explicitly test the
lost-update surprise:
1. POS-1 offline disables Item; POS-2/Admin re-enables online; reconnect → record final state.
2. Reverse: Admin disables; POS-1 offline re-enables; reconnect → record final state.
Invariant: bills from either side stay valid; menu converges to a single deterministic state.
Flag if an item silently flips back to enabled (lost disable) — that's a documented LWW effect,
decide if acceptable.

---

# Execution order (risk-first)
Run in this order — highest financial/security/distributed-state risk first, rush-hour last:

```
01 → 03 → 02 → 04 → 08 → 09 → 10 → 05 → 06 → 07
```
- Money: LAB-01 (+ 9 crash points) → LAB-03
- Security: LAB-02
- Kitchen: LAB-04
- Distributed-state gaps: LAB-08 (write skew) → LAB-09 (partial batch) → LAB-10 (causal ordering)
- Offline data lifecycle: LAB-05 → LAB-06
- Simulation: LAB-07 → Menu LWW both-direction

Bring back results after LAB-01, LAB-03, LAB-02 (even messy) before proceeding to fixes.

# Exit criteria → Go/No-Go
| Invariant | Gate |
|---|---|
| Financial | LAB-01 pass, no dup/lost payment across 9 crash points |
| Multi-terminal | LAB-03 pass, zero numbering collisions (online + mixed-network) |
| Offline | LAB-01/05/06 survive crash+restart, sync eventually |
| Convergence | LAB-07 zero unexplained discrepancies |
| Security | LAB-02 deterministic + documented; no silent unauthorized bills |
| Kitchen | LAB-04 no silent loss; retry visible |
| Product rules documented | LAB-02/05/06 + menu-LWW have written business rules |

## Strict Go/No-Go rule
- ONE unresolved P0 financial, security, or data-integrity failure = NO-GO.
- Product-decision cases (deleted item, revoked terminal, revoked permission, midnight,
  menu LWW) do NOT require the behavior to change before testing — but they MUST be
  explicitly decided, implemented consistently, and documented. Undecided = NO-GO.
- All six invariants 🟢 + product rules written → begin limited customer testing/marketing.

---

# Distributed-State Failure Taxonomy — KhanaBook

Why this matters: the LAB tests tell you *whether* something failed. This taxonomy tells you
*what kind* of failure you're hunting and *what invariant must survive*. Every entry is
grounded in code actually inspected (`GenericSyncService.saveAll`, bill/payment DTO
snapshotting, dual-key payment idempotency, per-record quarantine), not distributed-systems
theory.

Legend: 🟢 Protected · 🟡 Designed, needs device validation · 🟠 Known architectural limitation ·
🔴 Product decision required · 🐞 Potential bug.

| # | Failure class | KhanaBook restaurant example | Current architectural behavior | Status | Existing LAB | Additional test | Business impact |
|---|---|---|---|---|---|---|---|
| 1 | Lost update | POS-1 offline DISABLES Chicken Biryani 10:00; Admin ENABLES 10:05; POS-1 reconnects → enable wins | Row-level LWW on `updatedAt`; no field merge. Later write wins whole row | 🟠/🔴 | Menu LWW both-direction | — | Item silently re-enabled; staff sell a "disabled" dish |
| 2 | Stale read | POS-2 sells item POS-1 disabled offline (POS-2 never saw disable) | Allowed by design; bill snapshots price/tax so it stays valid | 🟢 (money) / 🟡 (ops) | LAB-07, menu LWW | — | Sold a "disabled" item; money still correct |
| 3 | Write skew | POS-1 edits item NAME offline; POS-2 edits its PRICE offline; both sync | Row-level LWW → later row-write clobbers the other's field | 🟠 | — | New: name+price concurrent-edit test | One edit silently lost |
| 4 | Duplicate delivery | Dropped response → client retries bill/payment | Bill dedup by (deviceId, localId); payment dual-key (gateway_txn_id + operation_id) semantic compare | 🟢 | LAB-01, LAB-03 | — | Double-charge / double-bill prevented |
| 5 | Partial failure | Batch A–E, D fails mid-sync | Per-record fallback; failed→quarantine; cursor advances on success only | 🟡 | LAB-01 (extend) | New: mid-batch failure test | Some bills stuck pending; must not duplicate/lose |
| 6 | Causal ordering | POS-1 offline creates Item A then Bill using A; sync delivers bill first | `resolveRelationalIds` + quarantine on unresolved refs; full ordering NOT guaranteed | 🔴 | — | New: out-of-order sync test | Bill quarantined/rejected; item mismatch |
| 7 | KOT external side-effect | Printer succeeds, app crashes before ack | `sentToKot` delta; no server KOT table (Android-only, deferred); no exactly-once across crash | 🟠 | LAB-04 | — | Duplicate or lost kitchen ticket |
| 8 | Split-brain identity | 5 terminals finalize invoices at once, some offline | Per-terminal series + invoice_series-scoped sequence (V26); collisions quarantined, not fatal | 🟡 | LAB-03 | Run Postgres MultiDeviceInvoiceSyncIntegrationTest | Duplicate/wrong invoice number = GST problem |
| 9 | Authorization staleness | POS-3 revoked while offline, keeps billing, reconnects | `credVer` checked per request; child-ownership at sync; offline-created-then-revoked acceptance NOT established | 🔴 | LAB-02 | — | Unauthorized bills accepted, or silent data loss |
| 10 | Clock / time attribution | 23:59 offline bill → 00:05 reconnect | `serverUpdatedAt` server-authoritative for sync order; business-day attribution NOT established | 🔴 | LAB-06 | — | Bill in wrong business day / daily report |
| 11 | Convergence failure | After all the above, do 5 POS + Admin + server agree? | Pull-by-`serverUpdatedAt` → eventual convergence; quarantined rows are the exception | 🟡 | LAB-07 | — | Devices show different truth; reconciliation pain |

## Distributed-State Attack/Failure Matrix (for prioritization)
| Failure | Offline? | 2 POS? | 5 POS? | Web Admin? | Financial | Data-loss | Operational | Security | Current protection |
|---|---|---|---|---|---|---|---|---|---|
| Duplicate payment | Y | Y | Y | N | HIGH | — | — | — | dual-key idempotency 🟢 |
| Lost payment | Y | N | N | N | HIGH | HIGH | — | — | local-write + retry 🟡 (crash window) |
| Duplicate bill | Y | Y | Y | N | MED | — | — | — | (deviceId,localId) dedup 🟢 |
| Wrong invoice number | Y | Y | Y | N | HIGH | — | MED | — | per-terminal series 🟡 |
| Retroactive price/tax | Y | Y | Y | Y | HIGH | — | — | — | snapshotting 🟢 |
| Stale menu / lost update | Y | Y | Y | Y | — | LOW | HIGH | — | LWW 🟠 |
| KOT duplicate/loss | Y | Y | Y | N | — | MED | HIGH | — | sentToKot 🟠 |
| Deleted item + offline draft | Y | Y | Y | Y | LOW | MED | MED | — | undefined 🔴 |
| Midnight attribution | Y | Y | Y | N | LOW | — | MED | — | undefined 🔴 |
| Revoked terminal offline | Y | Y | Y | Y | MED | — | HIGH | HIGH | undefined 🔴 |
| Revoked permission offline | Y | Y | Y | Y | LOW | — | MED | HIGH | undefined 🔴 |
| Expired credentials offline | Y | N | N | N | — | — | MED | MED | refresh-on-reconnect 🟡 |
| Cross-tenant sync/read | N | Y | Y | Y | HIGH | HIGH | — | HIGH | tenant scoping + tests 🟡 |

## Failure class → physical LAB test map
```
1 Lost update            → Menu LWW both-direction test
2 Stale read             → Offline-disable vs online-sale (LAB-07 sub)
3 Write skew             → NEW: POS-1 name + POS-2 price, both offline
4 Duplicate delivery     → LAB-01 (kill net after server accepts bill/payment)
5 Partial failure        → NEW: A/B/C success → D fail → E pending
6 Causal ordering        → NEW: create item+bill offline, manipulate sync order
7 KOT side-effect        → LAB-04 (printer ok, crash before ack)
8 Identity allocation    → LAB-03 (5-device simultaneous finalize)
9 Authorization staleness→ LAB-02 (revoke terminal while offline)
10 Clock/time            → LAB-06 (23:59 offline → 00:05 reconnect)
11 Convergence           → LAB-07 (rush-hour)
```
Gaps to add before Go: write-skew test (#3), mid-batch partial-failure test (#5), out-of-order
sync test (#6). These are the failure classes with no dedicated LAB today.
Any 🔴 open → NO-GO.
