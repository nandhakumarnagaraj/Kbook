# Flyway Database Migrations

## Trigger Conditions
- Adding new tables or columns
- Modifying existing schema (indexes, constraints)
- Backfilling data or fixing data issues
- User asks about migration naming, rollback, or zero-downtime changes
- Planning migrations for production deployment

---

## Naming Convention

```
V{number}__{description}.sql

Format: V80__add_split_payment_config.sql
         │    └── snake_case description
         └── Sequential number (next after V79)

Types:
  V = Versioned (forward-only, run once)
  R = Repeatable (re-run on checksum change, for views/functions)
```

**KhanaBook Current State:** V1 through V79
**Next migration:** V80__description.sql

**Location:** `server/src/main/resources/db/migration/`

---

## Migration Templates

### Add Table
```sql
-- V80__create_split_payment_config.sql
CREATE TABLE split_payment_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    sub_merchant_id VARCHAR(50) NOT NULL,
    split_percentage DECIMAL(5,2) NOT NULL CHECK (split_percentage BETWEEN 0 AND 100),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_split_payment_merchant ON split_payment_config(merchant_id);
CREATE INDEX idx_split_payment_sub_merchant ON split_payment_config(sub_merchant_id);
```

### Add Column (Safe)
```sql
-- V81__add_table_number_to_bills.sql
-- Safe: ADD COLUMN with DEFAULT doesn't lock table in PG 11+
ALTER TABLE bills ADD COLUMN table_number VARCHAR(10) DEFAULT NULL;

-- Add index CONCURRENTLY (non-blocking)
CREATE INDEX CONCURRENTLY idx_bills_table_number ON bills(table_number);
```

### Rename Column (Zero-downtime)
```sql
-- V82__rename_bill_amount_to_subtotal.sql
-- Step 1: Add new column
ALTER TABLE bills ADD COLUMN subtotal DECIMAL(10,2);

-- Step 2: Backfill (in batches for large tables)
UPDATE bills SET subtotal = amount WHERE subtotal IS NULL;

-- Step 3: Set NOT NULL after backfill
ALTER TABLE bills ALTER COLUMN subtotal SET NOT NULL;

-- V83__drop_old_amount_column.sql (deploy AFTER app code updated)
ALTER TABLE bills DROP COLUMN amount;
```

---

## Zero-Downtime Migration Strategy

### Safe Operations (No Lock / Short Lock)
```
✅ ADD COLUMN (nullable or with default in PG 11+)
✅ CREATE INDEX CONCURRENTLY
✅ ADD CONSTRAINT ... NOT VALID + VALIDATE later
✅ DROP INDEX
✅ DROP COLUMN (mark unused first)
```

### Dangerous Operations (Require AccessExclusiveLock)
```
⚠️ ALTER COLUMN SET NOT NULL (use CHECK constraint instead)
⚠️ ALTER COLUMN TYPE (full table rewrite)
⚠️ ADD COLUMN with volatile default
⚠️ CREATE INDEX (without CONCURRENTLY)
⚠️ RENAME TABLE
```

### Pattern: Safe NOT NULL Addition
```sql
-- Instead of: ALTER TABLE bills ALTER COLUMN notes SET NOT NULL;
-- Do this (no lock):
ALTER TABLE bills ADD CONSTRAINT bills_notes_not_null CHECK (notes IS NOT NULL) NOT VALID;
-- Then validate (only ShareUpdateExclusiveLock):
ALTER TABLE bills VALIDATE CONSTRAINT bills_notes_not_null;
```

---

## Index Creation

```sql
-- ALWAYS use CONCURRENTLY in production migrations
CREATE INDEX CONCURRENTLY idx_bills_merchant_date
    ON bills(merchant_id, created_at DESC);

-- Partial indexes for common filters
CREATE INDEX CONCURRENTLY idx_bills_pending_sync
    ON bills(merchant_id, created_at)
    WHERE sync_status = 'PENDING';

-- Note: CONCURRENTLY cannot run inside a transaction
-- Flyway config needed:
-- spring.flyway.out-of-order=false
-- Or use a separate migration file for each concurrent index
```

**Flyway + CONCURRENTLY:**
Flyway wraps each migration in a transaction by default. For `CREATE INDEX CONCURRENTLY`, the migration file must contain only that statement (Flyway detects and skips transaction wrapping).

---

## Data Backfills

```sql
-- V84__backfill_bill_status.sql
-- Batch update to avoid long locks and WAL bloat
DO $$
DECLARE
    batch_size INT := 1000;
    rows_updated INT;
BEGIN
    LOOP
        UPDATE bills
        SET status = 'COMPLETED'
        WHERE id IN (
            SELECT id FROM bills
            WHERE status IS NULL
            LIMIT batch_size
            FOR UPDATE SKIP LOCKED
        );
        GET DIAGNOSTICS rows_updated = ROW_COUNT;
        EXIT WHEN rows_updated = 0;
        COMMIT;
    END LOOP;
END $$;
```

### Backfill Rules
- Never UPDATE entire table in one statement (locks, WAL)
- Use batch sizes (500-5000 rows)
- Add `SKIP LOCKED` for concurrent safety
- Run backfills in separate migration from schema changes
- Monitor with `pg_stat_activity` during execution

---

## Rollback Strategy

Flyway doesn't support automatic rollback. Strategy:

### 1. Forward-fix (Preferred)
```sql
-- If V80 broke something, create V81 to fix it
-- V81__fix_split_payment_constraint.sql
ALTER TABLE split_payment_config DROP CONSTRAINT IF EXISTS split_payment_config_check;
ALTER TABLE split_payment_config ADD CONSTRAINT split_payment_config_check
    CHECK (split_percentage BETWEEN 0.01 AND 100);
```

### 2. Manual Rollback Script (Emergency)
```sql
-- rollback/V80__rollback.sql (NOT auto-run, manual emergency use)
DROP TABLE IF EXISTS split_payment_config;
-- Then: DELETE FROM flyway_schema_history WHERE version = '80';
```

### 3. Blue-Green Deploy
- Fork database before migration
- Apply migration to fork
- Test against fork
- Swap connection if successful

---

## KhanaBook Migration Guidelines

```yaml
Rules:
  1. One logical change per migration file
  2. Always test migration on fork before production
  3. Include CONCURRENTLY for all production indexes
  4. Separate data backfills from schema changes
  5. Never modify a migration that has been applied
  6. Add comments explaining WHY for complex migrations
  7. Keep migrations idempotent where possible (IF NOT EXISTS)
```

---

## Anti-patterns
- ❌ Multiple unrelated changes in one migration file
- ❌ CREATE INDEX without CONCURRENTLY on production tables
- ❌ Editing already-applied migrations (checksum mismatch)
- ❌ Full table UPDATE without batching
- ❌ DROP COLUMN in same deploy as code removal (deploy code first)
- ❌ ALTER COLUMN TYPE on large tables without planning

## Verification Checklist
- [ ] Migration number is next in sequence (V80, V81, etc.)
- [ ] File name uses double underscore: V80__description.sql
- [ ] Tested locally with `mvn flyway:migrate`
- [ ] Tested on database fork with production-like data
- [ ] No AccessExclusiveLock on large tables
- [ ] Indexes created with CONCURRENTLY
- [ ] Rollback plan documented
- [ ] Migration is idempotent or has IF NOT EXISTS guards
