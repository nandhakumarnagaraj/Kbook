-- ============================================================================
-- public_token uniqueness reconciliation (PLAN §3.2 / §4.1)
-- ----------------------------------------------------------------------------
-- Context: V19 added public_token (NON NULL, plain index idx_bills_public_token).
-- V26 attempted to upgrade it to a UNIQUE index (ux_bills_public_token) but only
-- when NO duplicates existed. A production database that already held duplicate
-- public_token values (or one where V26's guard skipped the unique index) may
-- still be relying on the non-unique idx_bills_public_token.
--
-- A duplicate public_token is a correctness/security defect: two bills could
-- resolve to the same public invoice URL. This script detects, reconciles, and
-- enforces uniqueness. It is NOT a Flyway migration — run it manually after a
-- verified backup and only once the detection query (Step 0) returns zero rows
-- that you do not expect to reconcile.
--
-- Idempotent: safe to re-run. All writes are guarded.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- STEP 0 — DETECTION (run first, inspect output before proceeding)
-- ----------------------------------------------------------------------------
-- Every public_token that appears on more than one (non-deleted) bill.
SELECT public_token,
       COUNT(*) AS occurrences,
       COUNT(*) FILTER (WHERE is_deleted = false) AS active_occurrences
FROM bills
WHERE public_token IS NOT NULL
GROUP BY public_token
HAVING COUNT(*) > 1
ORDER BY occurrences DESC;

-- Summary count of duplicate groups (expect 0 after reconciliation).
SELECT COUNT(*) AS duplicate_token_groups
FROM (
    SELECT public_token
    FROM bills
    WHERE public_token IS NOT NULL
    GROUP BY public_token
    HAVING COUNT(*) > 1
) dup;

-- ----------------------------------------------------------------------------
-- STEP 1 — REASSIGN duplicate public_token values
-- ----------------------------------------------------------------------------
-- For every bill whose public_token collides with another, assign a fresh,
-- unique UUID. Deleted bills are included so the unique index (Step 2) is never
-- blocked by historical collisions. Pure UPDATEs; no rows deleted, no invoice
-- numbers touched.
UPDATE bills b
SET public_token = gen_random_uuid()
WHERE b.public_token IS NOT NULL
  AND b.id IN (
      SELECT id
      FROM bills b2
      WHERE b2.public_token = b.public_token
      ORDER BY b2.id
      OFFSET 1   -- keep the first row per group, reassign the rest
  );

-- ----------------------------------------------------------------------------
-- STEP 2 — ENFORCE uniqueness (guarded, mirrors V26)
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM bills
        WHERE public_token IS NOT NULL
        GROUP BY public_token
        HAVING COUNT(*) > 1
    ) THEN
        -- Drop the legacy non-unique index, create the unique one.
        DROP INDEX IF EXISTS idx_bills_public_token;
        CREATE UNIQUE INDEX IF NOT EXISTS ux_bills_public_token
            ON bills (public_token);
        RAISE NOTICE 'ux_bills_public_token created: public_token is now unique.';
    ELSE
        RAISE NOTICE 'Skipping ux_bills_public_token: duplicate public tokens still exist after Step 1. Re-run Step 0.';
    END IF;
END $$;

-- ----------------------------------------------------------------------------
-- STEP 3 — VERIFICATION (expect: 0 groups, unique index present)
-- ----------------------------------------------------------------------------
SELECT COUNT(*) AS remaining_duplicate_token_groups
FROM (
    SELECT public_token
    FROM bills
    WHERE public_token IS NOT NULL
    GROUP BY public_token
    HAVING COUNT(*) > 1
) dup;

SELECT indexname
FROM pg_indexes
WHERE tablename = 'bills'
  AND indexname = 'ux_bills_public_token';
