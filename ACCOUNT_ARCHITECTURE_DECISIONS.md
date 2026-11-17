# ACCOUNT ARCHITECTURE DECISIONS

**Scope:** Phase 10 — the decision log for account identity work.
**Date:** 2026-11-17

Two kinds of entry:
* **Recorded** — a decision already made (by the owner, or by evidence that left only one option).
* **Pending** — a decision the owner must make. These block implementation and are not decided here.

Long-form rationale for D-1 lives in `docs/adr/0002-account-identity-resolution.md`.

---

## Recorded decisions

| ID | Decision | Basis | Consequence |
| :-- | :-- | :-- | :-- |
| R-1 | **Implementation of the identity fix is not started** until findings and plan are approved. | Original brief, restated by the owner. | Only documentation, process gates, and a migration renumber were delivered. |
| R-2 | **Two migrations must never share a version.** The new one is `V122`. | `V110` collision made the build unbootable; reproduced. | Renamed; `ops/check-migrations.sh` now enforces uniqueness in CI. |
| R-3 | **Migrations must validate in CI, not at boot.** | The collision only surfaced at startup, i.e. in production. | New `guard-migrations` job in `guards.yml`. |
| R-4 | **The database is the integrity authority**, not application pre-checks. | `check-then-insert` is racy; the unique indexes are what actually held under 8-way concurrency. | Pinned by `PhoneIdentityIntegrityTest`. |
| R-5 | **Identity guarantees are tested against real PostgreSQL.** | Mock-based tests cannot fail on uniqueness; that is how RC-1 hid. | Testcontainers test added; runs in `ci.yml`. |
| R-6 | **Production PII must not live in the working tree.** | `.backup-srcafe-*/users.csv` was untracked in the repo root. | `.gitignore` updated; file left in place for the owner to relocate. |
| R-7 | **`pgAdmin4` is publicly reachable; no IP allowlists on public sites.** | Explicit owner instruction: "sites public, login is the gate". | Allowlist removed from both nginx blocks and the repo copy; backup retained; residual risk recorded as SEC-8. |
| R-8 | **Breaking changes to client-visible contracts ship with the client change in the same release.** | Android `BackendErrorParser` branches on 400/409. | 400 → 409 for duplicates cannot ship alone. |
| R-9 | **Soft delete retires an account; deletion removes nothing.** | Existing behaviour is correct and already relied upon. | Reaffirmed as an invariant for all future remediation. |

---

## Pending decisions (owner)

These are unchanged from `ACCOUNT_IDENTITY_ARCHITECTURE.md` §6. **None has been assumed.**

| ID | Question | Options | Blocks |
| :-- | :-- | :-- | :-- |
| **D-1** | Which identity model? | **A** additive `user_identities` table (recommended) · **B** person/credential/membership split · **A now, B later** | every migration and repository signature |
| **D-2** | Is multi-restaurant-per-user a requirement in the next two releases? | yes / no / not yet | whether B is speculative |
| **D-3** | What does a returning Google user see when no link exists? | **a** `LINK_REQUIRED` + OTP proof · **b** create a second account, link via support · **c** block Google login for existing phones | the Google fix itself |
| **D-4** | How is `email` made case-insensitively unique given it also stores phone numbers? | **a** partial `lower(email)` unique index ignoring digit-only values · **b** stop writing phones into `email`, migrate the 24 rows · **c** normalise on write only | RC-2 fix, and enabling the `@Disabled` test |
| **D-5** | May an account exist without a verified phone? | yes (Google-only) / no | whether `verified_at` is mandatory per provider |
| **D-6** | Existing duplicate tenants: reconcile or prevent-only? | reconcile with owner proof / prevent-only | Phase R2/R4 scope |

---

## Decisions exposed by the data (new, needs an answer)

| ID | Question | Why it appeared | Options |
| :-- | :-- | :-- | :-- |
| **D-7** | Is "one live shop per `device_id`" a real business rule? | Production has **17 shops with bills** sharing a device with another live shop, and **14 of 20 groups are browser ids** (`server`, `web-admin`, `dev_*`), so the rule mostly constrains web signups, not terminals. | **(a)** scope the rule to device+owner · **(b)** advisory only (recommended) · **(c)** keep and clean data |
| **D-8** | Ship `V122` as a unique index at all? | It cannot apply to current data and would fail startup. | **(a)** drop the index · **(b)** conditional creation like V107 (recommended) · **(c)** clean first |
| **D-9** | Is `/auth/check-user`'s existence oracle acceptable? | It is a real oracle; the client comment claiming otherwise is false. | **(a)** keep + document · **(b)** remove · **(c)** keep but stop calling it from clients |

### Resolution note — D-8 (provisional, engineering)

**Taken:** option **(b)**, conditional creation, implemented 2026-11-17 in
`V122__prevent_duplicate_device_restaurants.sql` — index created only when no duplicate group exists,
otherwise `RAISE WARNING` and continue.

**Basis:** a migration that runs during application bootstrap must never be able to prevent the API
from starting. This is not a product decision, it is a safety property; it changes no behaviour on data
that is already consistent, and it does not touch a single row where it is not.

**Still the owner's call:** whether the device rule should exist at all (**D-7**), and whether to clean
the 25 surplus shops (**c**). Options (a) and (c) remain available without rewriting this again.

**Verified:** three branches against real PostgreSQL — clean data (index created), duplicate data
(`WARNING`, exit 0, index skipped, rows untouched), after cleanup (index created, idempotent re-run).

---

## Rejected alternatives (and why)

| Alternative | Rejected because |
| :-- | :-- |
| Add `findByPhoneNumber` to the Google chain and match on it | A Google email cannot prove ownership of a phone number. It would link accounts on a coincidence — forbidden by the brief and unsafe. |
| Merge rows where phone or email matches | Matching identifiers do not prove common ownership; the brief forbids it explicitly. |
| Auto-delete empty duplicate tenants | 27 empty shops may be future customers; deletion is irreversible; owners must confirm. |
| Enforce uniqueness only in application code | Proven racy: 8 concurrent inserts are resolved by the index, not the pre-check. |
| Require every client to update before the API changes | Forces re-authentication on every Android install; unacceptable. |
| Put pgAdmin behind an IP allowlist again | Contradicts the owner's stated policy (R-7). Recorded as risk instead. |

---

## How a decision gets recorded

1. Owner answers in the change thread.
2. It is added here as `Recorded`, with date and basis.
3. If it is irreversible or costly to reverse, it also gets a file in `docs/adr/`.
4. Only then does code follow.
