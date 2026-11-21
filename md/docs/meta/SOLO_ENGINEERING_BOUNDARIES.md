# Solo Engineering Boundaries — KhanaBook

For a **one-person** product team working with AI agents. There is no second reviewer and no
one to catch a plausible-sounding mistake, so the boundaries below are mostly about **evidence**
and **scope**, not ceremony.

Companion to [LOCAL_DEV_BOUNDARIES.md](LOCAL_DEV_BOUNDARIES.md) (environment/data tiers) and
[STAGING_SETUP_RUNBOOK.md](../qa/STAGING_SETUP_RUNBOOK.md) (isolated test stack).

---

## 1. Why this exists (three real failures from this codebase)

| Failure | What it cost |
|---|---|
| `main` was left red: `TerminalControllerTest` + `TerminalLifecycleTest` had 2 failing invariant tests, committed in the release commit. | Anyone cloning got a red build and no trusted baseline. |
| An audit report circulated with **stale `file:line` evidence** (`MenuDao.kt:59` cited as `updateName`; the file has `toggleItemAvailability`, and `updateName` no longer exists). | Effort spent fixing the wrong mechanism. |
| Two "fixes" to the menu edit path shipped before a **logcat** showed the real cause was a server rejection (`Menu item category could not be resolved`) followed by a destructive recovery pull. | Days lost; the code reasoning was plausible and wrong. |

The lesson, and the rule: **code reasoning is a hypothesis. A reproduction is evidence.**

---

## 2. Scope boundaries — what this product is NOT

`docs/meta/AGENTS.md` holds the authoritative scope. Do not resurrect removed features.

**In:** offline GST billing · menu config (categories/items/variants, OCR) · shop/payment/tax/printer
config · sync · 5-terminal multi-device · Easebuzz sub-merchant payments · reports · notifications.

**Out (do not build):** Zomato/Swiggy or marketplace integrations · storefront ordering ·
**inventory/stock ledger (removed 2026-09-29; `stock_logs` dropped in schema v78)** ·
staff-management UI · payment links · kitchen display.

Rule: if a request implies an out-of-scope feature, **stop and ask**. A half-resurrected
`stock_logs` or a payment-links screen is more expensive than a question.

---

## 3. Architecture invariants — never break these

Each one has a real enforcement point. If a change touches it, the change needs a test.

| # | Invariant | Enforced by | Why breaking it hurts |
|---|---|---|---|
| A1 | Android is **terminal-scoped**: a terminal sees only its own bills | `BillDao` filters `created_terminal_id`; `getOperationalBillById` scope guard | Cross-terminal leakage; the web-admin is the *intended* place for restaurant-wide views |
| A2 | Cross-terminal bill view lives **only** in web-admin | server role checks (`OWNER`/`KBOOK_ADMIN`) | Duplicating it in the app creates two authorities for money |
| A3 | **Master data is single-writer** (OWNER/`KBOOK_ADMIN`) | `SyncPushGuard.isMasterDataWriter` | A staff terminal becoming a menu authority |
| A4 | Field edits merge by **`changed_fields` mask**, not last-write-wins | client `MenuRepository`, server `GenericSyncService.applyChangedFieldsMerge` (**MenuItem only**) | Two devices editing different fields clobber each other |
| A5 | A row's sync flags are a **pair**: `is_synced` + `changed_fields` must move together | `markMenuItemsAsSynced`, quarantine path | `is_synced=0` with a NULL mask = server reads "overwrite every field" |
| A6 | A pulled row must not overwrite a row with **unpushed edits** | `MenuPullMergePolicy` | The edit is erased *and* marked synced — gone from both sides |
| A7 | **Offline billing never needs the network** | `BillingViewModel.completeOrder` path; no online gate | The core product promise |
| A8 | Invoice identity is per **terminal series** | `ux_restaurant_terminal_series`; local sequence allocation | Duplicate invoice numbers across counters |
| A9 | **5 active terminals** maximum, serialized | `MAX_ACTIVE_TERMINALS = 5` + pessimistic profile lock | Two approvals racing past the cap |
| A10 | A deviceId match is **identification, not authentication** | terminal token (`X-Terminal-Token`) + approval queue | A copied deviceId claiming a terminal |

A11 (process): **any change to sync, pull, or quarantine logic is release-blocking until it has
been run on staging with a real device.** There is no emulator on the dev machine, so it cannot
be verified locally — that is a reason to gate it, not to skip it.

---

## 4. R&D boundaries

- **Time-box, then decide.** Give an investigation a fixed box (e.g. one session). At the end,
  write the decision or write "insufficient evidence" — never leave it drifting.
- **One question per investigation.** "Why don't menu edits persist" was answerable. "Is my sync
  good?" is not.
- **Record where it will be found again:** `docs/planning/` for investigations, `docs/meta/` for
  durable rules, `docs/qa/` for test procedure. An answer that lives only in a chat is lost.
- **Label every statement** as *verified* (with the command or file:line that proves it),
  *suspected*, or *unknown*. A report with no such labels is not evidence.
- **Stop when the marginal answer changes nothing.** More research that doesn't alter a decision
  is procrastination with a good excuse.

## 5. Competitor analysis boundaries

- **Purpose:** decide one of — build it, don't build it, or build it differently. Output is a
  decision, not a feature list.
- **Compare against the same constraints** this product has: offline-first, 5 terminals, Indian
  GST, phone + tablet. A competitor's cloud-only feature is not evidence that it's achievable here.
- **Separate observed from claimed.** Vendor marketing is a claim; a screenshot of the flow is an
  observation. Never let the two share a bullet.
- **Never copy a feature that breaks an invariant in §3** (e.g. an online-only pricing engine).
- **Format:** `Capability → what they do → what we do → gap → decision (now/later/never) + why`.
  Keep it to one screen; it goes in `docs/planning/`.

## 6. Feature design boundaries

A design is complete when it answers these, in this order — skip one and it will come back as a bug:

1. **Problem** — the user-visible symptom, with a reproduction (not a solution in disguise).
2. **Constraint check** — does it conflict with §2 (scope) or §3 (invariants)? If yes, stop.
3. **Data & migration** — Room schema version bump, migration test, server Flyway step; both sides
   of the sync contract (field names, defaults, nullability) named explicitly.
4. **Offline behaviour** — what happens with no network, mid-sync, and after a reinstall.
5. **Failure behaviour** — what the user sees when it fails, and whether it is retried,
   quarantined, or permanently rejected. *(This is the gap that produced the menu bug.)*
6. **Authorization** — which role/permission, enforced where (client + server).
7. **Test plan** — the executed check that will prove it, and the one that will catch a regression.
8. **Rollback** — can it ship and be undone without a data migration?

## 7. Definition of done (the evidence standard)

A change is done when **all** of these are true:

- [ ] A check that covers it **ran**, and its exit status is reported honestly (including failure).
- [ ] Anything that could not run is stated as **not executed**, with the reason.
- [ ] Device behaviour was confirmed with a **logcat or a reproduction**, not by reading code.
- [ ] No test, assertion, or lint rule was weakened or suppressed to make it pass.
- [ ] `main` is **green**: no merged commit leaves a failing test behind (§8).
- [ ] The claim "fixed" is backed by the failing case becoming a passing one.

## 8. Solo process boundaries

- **One risky change in flight.** Parallel risky work with no reviewer is how two half-fixes
  both look finished.
- **Never leave the tree red.** If a test must change, change the test *and* justify it in the
  same commit. A red `main` is worse than a slower merge.
- **Don't mix a behaviour fix with a refactor.** You cannot tell which one moved the test.
- **Uncommitted work is not a boundary violation, but it is a risk:** verify that any build you
  test actually contains the change (today's debug APK may have predated the edits). Check the
  build's timestamp against the edit time before drawing conclusions from a log.
- **When a fix doesn't work, stop and get a reproduction.** Do not ship fix #3 on the same theory.

## 9. Agent delegation boundaries

**Delegate:** mechanical, wide, low-judgement work — a rename across many files, the same edit
repeated in dozens of modules, bulk test scaffolding.

**Keep:** anything touching §3 invariants, money, sync semantics, permissions, or a migration.

**Always:** read what a worker actually changed before believing its report; it runs without
seeing the other workers, so the seams are where failures live.
