# KhanaBook — Distributed-State Product Decisions (REQUIRED before Go)

Scope: behaviors where the **code does not establish a clear product rule**, plus one
defined-but-surprising case (menu LWW) that needs a written rule. These must be decided,
implemented consistently, and documented before customer usage — an undecided P0 = NO-GO.

Count reconciliation (per review): there are **three currently identified 🔴 UNDEFINED
areas** — Decision 1 (causal ordering), Decision 2 (revoked terminal offline), Decision 3
(revoked permission offline) — plus **Decision 4 (midnight)** which is undefined for the
business-day question, and **Decision 5 (menu LWW)** which is *defined but needs a documented
product rule*, not undefined. So: 3 undefined (ordering, terminal-revoke, permission-revoke),
1 undefined-for-business-day (midnight), 1 defined-needs-rule (menu LWW). NOT four undefined.

No policy is chosen below. Each entry: current code behavior → what's NOT established →
options → security/operational implications.

---

## Decision 1 — Causal dependency / sync ordering  🔴 UNDEFINED
Scenario: POS-1 offline creates Item A, then a Bill using Item A. Sync delivers the bill
before the item creation.
- Current code: `resolveRelationalIds` maps local→server ids; unresolved references fall to
  per-record quarantine (`SyncQuarantineEntity`). There is NO explicit dependency-ordering
  guarantee that "create item" is applied before "bill references item."
- Not established: whether the server reorders by dependency, whether the client retries the
  bill after the item lands, or whether the bill simply quarantines until the next cycle.
- Options: (A) client guarantees dependency order in the push queue; (B) server buffers/retries
  unresolved-ref records; (C) quarantine + auto-retry next sync (closest to today); (D) reject.
- Implications: (A) most correct, client work; (C) eventual consistency with a lag window where
  a real bill sits quarantined — staff may think it "didn't save."

## Decision 2 — Revoked terminal operating offline  🔴 UNDEFINED (security)
Scenario: POS-3 offline → Admin deactivates POS-3 → POS-3 creates bills offline → reconnects.
- Current code: `TerminalRequestFilter` rejects any request whose token `credVer` ≠ DB
  `credentialVersion`; child-record ownership is enforced at sync. Whether bills CREATED offline
  BEFORE the reconnect (but AFTER server-side revocation) are accepted, rejected, or quarantined
  is NOT established.
- Options: (A) accept all offline txns created before reconnect (operational continuity);
  (B) reject/quarantine anything created after the server-side revocation timestamp (strict
  security); (C) accept bills but block new sessions.
- Implications: (A) a revoked/stolen device keeps producing accepted bills until it reconnects —
  security exposure; (B) safest, but a legitimately-still-working device loses a shift of bills.
  This is a genuine security-vs-continuity tradeoff for you to decide.

## Decision 3 — Permission revoked while offline  🔴 UNDEFINED (security)
Scenario: user has BILLING → POS offline → Admin removes BILLING → user bills offline → reconnect.
- Current code: permissions checked server-side per request; offline app uses the last-synced
  grant. Whether offline-created bills are re-validated against the NEW permission at sync is
  NOT established.
- Options: (A) offline txns honored (permission change is future-only); (B) sync re-checks and
  rejects/quarantines; (C) force re-login on reconnect.
- Implications: distinguish security requirement (revocation should take effect) from offline
  usability (staff mid-shift shouldn't lose bills). Likely (A) for bills already created +
  (C)/future-only for new actions — but this must be an explicit decision, not an accident.

## Decision 4 — Midnight / business-day attribution  🔴 UNDEFINED (business day)
Scenario: 23:59 offline bill → 00:05 reconnect.
- Current code: `serverUpdatedAt` is server-authoritative for SYNC ORDERING (technical
  timestamp). The BUSINESS-DAY attribution (which daily counter, which invoice series, which
  daily-closing report the bill belongs to) across an offline midnight is NOT established.
- Separate explicitly: technical timestamp (when synced) vs business date (when the sale
  happened). A bill created 23:59 should almost certainly belong to the previous business day.
- Options: (A) business date = device local creation time; (B) = server receipt time;
  (C) = explicit business-day cutoff configured per restaurant.
- Implications: (B) mis-attributes late-night offline sales to the next day → wrong daily
  closing and reports. (A)/(C) preserve the real business day but depend on device clock
  (see clock-skew, taxonomy #10). Decide per 5-terminal behavior too.

## Decision 5 — Menu LWW conflict behavior  🟠 DEFINED, needs written rule
Scenario: 10:00 POS-1 offline DISABLE item; 10:05 Admin ENABLE; 10:10 POS-1 reconnects.
- Current result: ENABLE wins (later `serverUpdatedAt`); POS-1's disable is a lost update.
  Row-level LWW, no field merge, no conflict record for menu edits.
- Document: why it happens (LWW), what users see (item they disabled is live again), whether
  acceptable, whether the product should surface a conflict warning, and whether future
  field-level/version-based conflict handling is desired.
- Do NOT change implementation yet — decide the rule and whether to warn.

---

## Required outcome
For each decision, produce a one-line written rule + where it's enforced. Until all five have a
written rule (and the 🔴 ones are implemented consistently), the product is NO-GO for the
distributed-state gate. Decisions 2 and 3 are security-sensitive — prioritize.
