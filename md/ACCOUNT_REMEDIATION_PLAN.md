# ACCOUNT REMEDIATION PLAN

**Scope:** Phase 6/7 — how existing duplicate data and the identity defect are fixed **safely**.
**Status:** PLAN ONLY. **Nothing in this document has been executed.** Every step requires explicit
written approval before it runs.
**Date:** 2026-11-17

Companion documents: `ACCOUNT_DUPLICATE_DATA_REPORT.md` (what exists), `ACCOUNT_INTEGRITY_REQUIREMENTS.md`
(what must be true), `ACCOUNT_DEPLOYMENT_AND_ROLLBACK.md` (how it ships).

---

## 1. Non-negotiable rules

1. **No production write without a restored backup taken in the same change window.**
2. **No `DELETE` of a `users` or `restaurantprofiles` row.** Retirement means `is_deleted = true`.
3. **No automatic merge**, ever, on a matching phone, email or name.
4. **Dry run first**, on a restored copy, with the row counts diffed.
5. **One class at a time**, each with its own approval and its own verification query.
6. **Rollback script written before the forward script runs.**
7. **Never apply a unique index before its data is clean** — `V122` is currently blocked by 20 groups.

---

## 2. Phase R0 — Safety net (no product change)

| Step | Command (sketch) | Verify |
| :-- | :-- | :-- |
| Full backup | `pg_dump -Fc kbook_saas > pre-remediation.dump` | file size, `pg_restore -l` lists objects |
| Table-level export of affected tables | `pg_dump -Fc -t users -t restaurantprofiles -t subscriptions` | restore into scratch DB |
| **Restore drill** | restore the dump into a scratch database | `select count(*) from users` matches production |
| Baseline counts | record `count(*)` for users, profiles, bills, subscriptions | stored in the change ticket |
| Query archive | save every `SELECT` used in this plan | reproducible evidence |

Gate: the restore drill must succeed before anything else happens. A backup that has never been
restored is not a backup.

---

## 3. Phase R1 — Stop new duplicates (code, no data risk)

This is the only part that pays off immediately and touches no existing row.

| Step | Change | Rollback |
| :-- | :-- | :-- |
| R1.1 | Add the phone lookup to the Google resolution path **behind a feature flag**, returning `LINK_REQUIRED` rather than creating (FR-3/FR-4) | flip flag off |
| R1.2 | Normalise + case-insensitively check `email` on the two un-normalised writers (`AdminWriteService`, `createStaff`) | revert commit |
| R1.3 | Emit a metric on every duplicate rejection | revert commit |
| R1.4 | Correct the stale `/auth/check-user` client comment | revert commit |

R1 requires **D-3 answered** (what the returning user sees) and **D-4 answered** (email uniqueness).
R1.3 and R1.4 depend on neither and can ship first.

---

## 4. Phase R2 — Class A: device-sharing shops

**Data (captured 2026-11-17 10:47 UTC):** 20 `device_id`s → 45 live shops → 25 surplus; 28 have zero
bills, 17 have bills. 14 of the 20 groups are browser/client ids (`server`, `web-admin`, `dev_*`), only
6 are hardware. Check the current state with `ops/verify-schema-invariants.sh` — it lists the live
offenders rather than trusting this paragraph.

### R2.1 Re-specify the rule (product decision, FR-15)
`device_id` is hardware, not a business. Two real shops on one device is legitimate — 17 rows carry
bills, and 14 of the 20 groups are browser identifiers rather than hardware at all. Options:

* **(a)** Keep one-shop-per-device but scope by `device_id` **and** owner user (the same human may have
  two outlets).
* **(b)** Drop the database constraint; keep the existing signup warning
  (`rejectSecondShopOnSameInstall`) as advisory.
* **(c)** Keep as-is and clean the data.

**Recommendation: (b)**, because (a) needs a column that does not exist and (c) destroys real shops.
**Do not ship `V122` in its current form** — it cannot apply (see below).

### R2.2 If `V122` is to be kept
Convert to the V107 pattern: check for duplicates first, create the index only when clean, otherwise
`RAISE WARNING` and continue. Otherwise the migration aborts **at boot**, taking the API down:

```
ERROR: could not create unique index "uq_restaurantprofiles_live_device"
DETAIL: Key (device_id)=(…) is duplicated.
```

A migration must never be able to prevent startup.

### R2.3 Retiring the 27 empty surplus shops
Per group, with owner confirmation:
1. Identify the survivor (the one with terminals, or the newest with a real `shop_name`).
2. `UPDATE restaurantprofiles SET is_deleted = true, updated_at=… WHERE restaurant_id = <surplus>;`
3. Same for its owner row (`is_deleted = true`), leaving identifiers intact.
4. Verify: the survivor's bills/terminals unchanged; the retired tenant no longer appears in the admin
   list; no FK breakage.
Rollback: flip `is_deleted` back. Non-destructive by construction.

---

## 5. Phase R3 — Class C: phone numbers in the `email` column (24 rows)

Depends on **D-4**. Options:
* **(a)** Leave and document — the column is already polymorphic; nothing reads it as a mailbox for
  these rows.
* **(b)** Move the legacy value to `NULL` where `email !~ '@'`, after confirming no login depends on it.
  Risk: `email` is a login identifier for some rows — a user may be signing in with that number.
* **(c)** Add `legacy_email` and migrate, then free the column.

**Recommendation: (a) now, (c) if the identity model lands.** (b) can lock a user out of their own
account, which is worse than the mess.

---

## 6. Phase R4 — Class D: one human, two accounts

**No automated action. This is a product flow, not a cleanup.**

1. Ship the identity model (Option A) with `verified_at`.
2. Ship an in-app "link this Google account to my shop" flow requiring Google proof **and** OTP on the
   shop's registered phone.
3. Support-assisted linking only for cases the flow cannot resolve, with the audit trail from SEC-4.
4. Never retire the orphaned tenant automatically — its history belongs to the owner.

---

## 7. Verification queries (run after each phase)

```sql
-- no duplicate live shops per device (only if R2 constraint retained)
SELECT device_id, count(*) FROM restaurantprofiles
WHERE is_deleted=false AND (is_suspended=false OR is_suspended IS NULL)
GROUP BY device_id HAVING count(*)>1;

-- unique identities still unique
SELECT lower(email), count(*) FROM users WHERE email IS NOT NULL AND btrim(email)<>''
GROUP BY lower(email) HAVING count(*)>1;

-- nothing lost
SELECT count(*) FROM bills;                      -- must be >= pre-change
SELECT restaurant_id, count(*) FROM bills GROUP BY 1;  -- per-tenant unchanged
SELECT count(*) FROM users WHERE is_deleted=false;     -- expected delta only
```

---

## 8. Approvals required

| Phase | Needs | Blocked by |
| :-- | :-- | :-- |
| R0 | nothing (safe) | — |
| R1 | D-3, D-4 (R1.3/R1.4 unblocked) | decisions |
| R2 | FR-15 decision + per-group owner confirmation | product |
| R3 | D-4 | decision |
| R4 | D-1, D-3 | decision |

Nothing proceeds past R0 until the owner says so in writing, per the original brief.
