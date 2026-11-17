# ACCOUNT DUPLICATE DATA REPORT

**Scope:** Phase 6 — duplicate investigation on the live database.
**Mode:** READ-ONLY. Every figure below came from a `SELECT`. **No row was inserted, updated, deleted,
merged or moved.** No migration was applied to production.
**Date:** 2026-11-17
**Database:** `kbook_saas` (PostgreSQL 16.15), read via the running container.

---

## 1. Executive summary

Four distinct duplicate classes exist. Only the first can be found by an identifier query, which is why
the others have gone unnoticed.

| Class | Count | Severity | Reversible by SQL alone? |
| :-- | :-- | :-- | :-- |
| **A. One device, several live shops** | 20 devices → 45 shops (**25 extra**) | High | Partly — needs an owner decision per group |
| **B. Empty tenants (never transacted)** | 77 of 161 live (48%) | Medium | Identify only; deleting is a business decision |
| **C. Phone numbers written into the `email` column** | 24 users | Low | Yes, but it changes a login identifier |
| **D. Cross-identifier one human, two accounts** | not measurable | Critical | **No** — no person key exists |

Class D is the one users experience and complain about (the Google-vs-phone split from RC-1). It is
**not countable** with the current schema, and this report does not pretend otherwise.

---

## 2. Baseline

**Captured 2026-11-17 10:47 UTC.** Production is live, so these figures drift by a couple of rows per
day — an earlier draft of this report carried 166/162/160 because it was taken minutes before this one.
Treat any number here as a snapshot with a timestamp, never as an invariant.

| Metric | Value |
| :-- | :-- |
| `users` rows | 167 (0 soft-deleted) |
| distinct `restaurant_id` in `users` | 164 |
| `restaurantprofiles` rows | 163 (161 live: not deleted, not suspended) |
| `auth_provider` | 91 `PHONE`, 76 `GOOGLE` |
| users with `email IS NULL` | 34 |
| tenants with exactly one live user | 163 |
| tenants with more than one live user | 1 (a staff member) |
| tenants with more than one `OWNER` | 0 |

---

## 3. Class A — one device, several live shops (the `V122` blocker)

This is the class the pending `V122__prevent_duplicate_device_restaurants.sql` migration is meant to
stop. It has not prevented the existing ones, and **it cannot be applied to this data**.

```sql
-- live profiles sharing a device_id
WITH dup AS (
  SELECT device_id FROM restaurantprofiles
  WHERE is_deleted=false AND (is_suspended=false OR is_suspended IS NULL) AND device_id IS NOT NULL
  GROUP BY device_id HAVING count(*)>1
)
...
```

| Measure | Value |
| :-- | :-- |
| `device_id`s with more than one live shop | **20** |
| live shops in those groups | **45** |
| surplus shops beyond one-per-device | **25** |
| of those 45, shops with **zero bills** | **28** |
| of those 45, shops **with bills** | **17** |
| groups whose `device_id` is a **browser/client id** (`server`, `web-admin`, `dev_*`) | **14 groups / 32 shops** |
| groups whose `device_id` is **hardware** | **6 groups / 13 shops** |

The last two rows matter as much as the first: most of this class is not terminals at all. `server`,
`web-admin` and `dev_<hash>` are identifiers the web client sends, and the backend deliberately exempts
browser ids from device binding. A unique index on `device_id` would therefore also forbid two shops
created from the same web client — which is what a second web signup from one browser looks like.

**Consequence for deployment — blocking:** `V122` issues a bare `CREATE UNIQUE INDEX … ON
restaurantprofiles (device_id) WHERE is_deleted=false AND (is_suspended=false OR is_suspended IS NULL)`.
PostgreSQL will refuse to build it while duplicate keys exist:

```
ERROR: could not create unique index "uq_restaurantprofiles_live_device"
DETAIL: Key (device_id)=(…) is duplicated.
```

Because Flyway runs each migration in a transaction and the application migrates at boot, this is not a
failed migration — it is a **failed startup**. The same trap `V107` documented (and solved by creating
its index conditionally) is present here.

**Sub-classification (this is what makes remediation safe):**

* **A1 — the same shop signed up twice (28 zero-bill shops).** Two empty tenants on one device. No
  bills, no terminal history. Safe to retire once the owner confirms which one they use.
* **A2 — two real businesses on one device (17 shops with bills).** A genuine multi-outlet owner, a
  shared front-desk tablet, or a second web signup. These are **not duplicates** and force-merging them
  would destroy data.

A2 means the product rule itself is questionable: `device_id` identifies hardware, not a business.
Hardware can legitimately host two shops. **Recommendation: do not ship `V122` as written.** Either
relax the rule to "one live shop per device_owner" or make it advisory (warn at signup, as
`rejectSecondShopOnSameInstall` already does) rather than a database constraint.

---

## 4. Class B — empty tenants

| Measure | Value |
| :-- | :-- |
| live tenants with no bills at all | **77** of 161 (48%) |
| … of which have exactly one live user | **77** |

Interpretation, stated carefully: a tenant with no bills is *consistent with* an abandoned or duplicate
signup, but it is **not proof**. A restaurant may have signed up today and not yet billed. This report
does not label them duplicates; it flags them as the population to examine first, using account age and
last sign-in, before any remediation.

---

## 5. Class C — phone numbers in the `email` column

| Measure | Value |
| :-- | :-- |
| users whose `email` is all digits (≥10) | **24** |
| users where `email = phone_number` | **0** |

The writes come from `UserServiceImpl.updatePrimaryMobileNumber`
(`currentUser.setEmail(newMobileNumber)`). The 24 rows hold a number that is *not* the row's current
`phone_number`, which means the account's number was changed again afterwards and `email` kept the
previous value. This is why `email` has no case-insensitive uniqueness (see RC-2) — the column is not a
mailbox for these rows.

Risk: a future lookup that treats `email` as an address would resolve a phone to a mailbox. Low today,
structural tomorrow.

---

## 6. Class D — the class that cannot be measured

A human who signed up by phone (`email=NULL`) and later signed in with Google holds two rows in two
tenants. The schema has no person key, so the only signals are soft: identical `name`, identical
`shop_name` shape (`"<name>'s Restaurant"`), or adjacent `created_at`.

A read-only probe found **1** name-group spanning different tenants *and* different auth providers, and
**7** name-groups spanning multiple tenants. Names collide between different people constantly in this
market, so these are **leads, not findings**.

**This report will not name them as duplicates.** Per the brief: matching identifiers do not prove
common ownership, and merging on that basis is exactly the failure mode to avoid.

To make this class countable, the system needs the `user_identities` table proposed in
`ACCOUNT_IDENTITY_ARCHITECTURE.md` (Option A) and a `verified_at` fact. That is a Phase 5 decision, not
a query.

---

## 7. Verified non-issues

Recording these so nobody "fixes" them:

| Check | Result |
| :-- | :-- |
| Duplicate phone digits across rows | **0** — `ux_users_phone_digits` holds |
| Duplicate `login_id` (case-insensitive) | **0** |
| Case-variant duplicate emails | **0** — the RC-2 defect is latent, not active |
| Cross-column identity collisions (one row's `login_id` = another's `email`) | **0** |
| Duplicate `restaurant_id` in `restaurantprofiles` | **0** — V107's cleanup held |
| Blank / NULL `device_id` on live profiles | **0** |
| Tenants with more than one `OWNER` | **0** |

---

## 8. Remediation strategy (plan only — nothing executed)

Ordered by risk, least destructive first.

### 8.1 Preconditions for any change
1. **Backup first.** `pg_dump` of `users`, `restaurantprofiles`, `subscriptions`, `refresh_tokens`,
   plus a full `pg_dump` before any write. Verified restorable by restoring into a scratch database —
   a backup that has never been restored is not a backup.
2. **Dry run on a restored copy**, never on production.
3. **Owner verification** for every A1 group and every D candidate. No exceptions.
4. **Explicit written approval** before the first `UPDATE`/`DELETE`. Not implied by any earlier
   conversation.

### 8.2 Per class

| Class | Action | Gate |
| :-- | :-- | :-- |
| **A1** (27 empty surplus shops) | Mark `is_deleted=true` on the surplus profile and its owner row, preserving `restaurant_id` for audit. Do **not** delete rows. | Owner confirms which shop they use |
| **A2** (17 shops with bills) | **Do nothing.** They are real. Fix the product rule instead of the data. | — |
| **B** (78 empty tenants) | No action. Report only. | Age + last-seen analysis |
| **C** (24 phone-in-email) | Optional migration to move the value to a legacy column or `NULL`. | Changes a login identifier → needs D-4 |
| **D** (cross-identifier) | **Nothing automatic, ever.** Offer an in-app "link this Google account" flow with OTP/Google proof, after the identity model lands. | D-1 and D-3 |

### 8.3 What must never happen
* No `DELETE` of a `users` or `restaurantprofiles` row.
* No merge of two tenants with bills.
* No matching on name, or on an unverified phone/email, to declare two accounts the same person.
* No unique index applied before its data is clean.

### 8.4 Post-migration verification
1. Row counts per table before/after, asserted.
2. `SELECT … HAVING count(*)>1` re-run for every class → all zero.
3. A test signup, a test login, and a master-sync pull for one affected tenant.
4. Bills-per-tenant unchanged for every tenant that had bills.

---

## 9. Limitations

* Read-only aggregate queries only; no per-row listing of personal data was produced.
* "Zero bills" is a proxy for abandonment, not proof.
* Class D is unmeasured by construction; any count would be a guess dressed as data.
* `subscriptions` table name differs from expectation (V120 does not create a table literally named
  `subscriptions`), so subscription duplication was **not** verified. `[UNVERIFIED]`
