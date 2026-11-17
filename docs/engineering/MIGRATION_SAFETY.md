# Migration Safety — destructive and non-idempotent DDL

**Created:** 2026-11-17, after a question about tables/columns being removed and `already exists` errors.
**Status:** Inventory verified against the live schema. One guard added; two items in the backlog.

---

## 1. First, what is *not* happening

An earlier audit pass did not cover this; it covered duplicate versions (V110) and the V122 guard. So the
question was legitimate. Before listing risks, here is what was checked and found clean on 2026-11-17:

| Check | Result |
| :-- | :-- |
| `spring.jpa.hibernate.ddl-auto` | **`validate`** in `application.properties` and `application-dev.properties`. Hibernate never creates, alters or drops a table. No `create-drop`, no `update`. |
| Container env overriding it | none (`SPRING_JPA_HIBERNATE_DDL_AUTO` absent) |
| Tables the migrations create vs tables the database has | **55 vs 55** — no drift |
| Tables in the DB that no migration creates | only `flyway_schema_history` (Flyway's own) |
| Tables a migration creates that are missing | only `merchant_agreement` — dropped deliberately by **V116** |
| Code still referencing dropped objects | none. Nothing in `services/server/src/main` mentions `merchant_agreement`, or the V9/V99 columns (`easebuzz_merchant_key`, `easebuzz_salt`, `easebuzz_env`, `printer_enabled`, `printer_mac`, `kitchen_printer_*`) |
| Subscription tables | present and populated: `subscription_plans` = 4 rows, `restaurant_subscriptions` = **163** rows |
| V120 (creates the subscription system) | `CREATE TABLE IF NOT EXISTS` only — no `DROP`, `DELETE`, `TRUNCATE` or `ALTER` anywhere in the file |

So the subscription schema has not been removed. If a client is showing no subscription data while the
table holds 163 rows, the cause is application-side (query, tenant filter, plan mapping), not a dropped
table — and that is a different investigation.

---

## 2. Every destructive statement in the migration set

Only three migrations remove anything, and all of them are guarded with `IF EXISTS`:

| Migration | Removes | Why it was safe |
| :-- | :-- | :-- |
| `V9__remove_easebuzz_credentials_from_restaurantprofiles.sql` | 4 credential columns from `restaurantprofiles` | secrets moved out of the row; nothing reads them |
| `V99__drop_printer_config_from_restaurant_profiles.sql` | 8 printer/paper columns | printing config moved to the POS client |
| `V116__drop_merchant_agreement.sql` | `merchant_agreement` table + 2 indexes | feature retired; no code reference remains |

Everything else that says `DROP` is `DROP CONSTRAINT` / `DROP INDEX` — a re-shape, not data loss.

**No migration drops a table and recreates it**, which is the pattern that would silently destroy rows.

---

## 3. Where `already exists` / `does not exist` actually comes from

Flyway applies each version exactly once, so idempotency is not needed *when Flyway is the only writer*.
The migration set does **not** have that property, and the inventory is:

| Statement kind | Without `IF NOT EXISTS` | Consequence if applied twice |
| :-- | :-- | :-- |
| `CREATE TABLE` | **22** across 7 files (`V1` alone has 12, then `V48`, `V61`, `V62`, `V63`, `V64`, `V103`) | `ERROR: relation "…" already exists` |
| `CREATE INDEX` | **83** | `ERROR: relation "…" already exists` |
| `ADD COLUMN` | **8** | `ERROR: column "…" of relation "…" already exists` |

So those messages appear when a migration statement is executed **outside Flyway**, or when Flyway
believes it has not run a version it has. Two realistic triggers on this host:

1. **Manual execution.** Running a `.sql` from `db/migration/` by hand (`psql -f V120__…`) — safe for
   V120 because it uses `IF NOT EXISTS`, unsafe for the seven files above.
2. **`spring.flyway.baseline-on-migrate=true`** (`application.properties:57`). If `flyway_schema_history`
   is ever lost or reset against a **populated** database — a restore that drops the table, a repaired
   baseline, a copied schema — Flyway baselines at version 1 and then tries to apply V2 onward against
   tables that already exist. That produces exactly this error storm, on a schema that looks intact.

`baseline-on-migrate=true` is the single setting most likely to turn a history mishap into "already
exists" everywhere.

---

## 4. Rules

1. **Only Flyway applies DDL.** Never `psql -f` a migration file against production. (V120 is safe even
   then, because it uses `IF NOT EXISTS`; the seven files in §3 are not.)
2. **A migration file is immutable once applied.** Editing one changes its checksum; the repo already
   carries `ops/flyway_repair_*` scripts because this happened before.
3. **Never `DROP TABLE`/`DROP COLUMN` without a fresh, restored backup** — `docs/runbooks/BACKUP_AND_RESTORE.md`.
4. **Never re-run a version.** If a migration must be corrected, add the next version (`V123`, `V124`…).
5. **A new migration that drops anything must say why** in a header comment, and needs review.
6. **`flyway_schema_history` is production data.** It is included in the dump; losing it is what makes
   a healthy schema unreadable to Flyway.
7. **New migrations must be re-runnable.** Enforced from `V123` by `ops/check-migrations.sh`.

---

## 5. What changed as a result

| Fix | Where | Verified by |
| :-- | :-- | :-- |
| Destructive DDL is reported on every run (the §2 list) | `ops/check-migrations.sh` | Found the three historic files; a scratch migration with `DROP TABLE` and `ALTER TABLE … DROP COLUMN` is flagged |
| Unguarded DDL in a **new** migration fails the check | `ops/check-migrations.sh` (`IDEMPOTENT_FROM`, default `123`) | `V123` with plain `CREATE TABLE`/`CREATE INDEX` → exit 1; same DDL with `IF NOT EXISTS` → exit 0; current repo → exit 0 |
| `baseline-on-migrate` turned **off** | `application.properties` | Full migration set re-applied from an empty database (identity test, 104 migrations) |

The legacy files are **not** edited: V1–V122 are applied in production, so a whitespace change to any of
them alters its checksum and Flyway refuses to start. Only new versions are held to the idempotency rule.

Backlog: `KB-047` and `KB-048` — closed by the above.
