-- =============================================================================
-- KYC document exposure reconciliation / backfill
-- =============================================================================
-- Purpose: migrate the four EaseBuzz KYC document types off the Apache-public
-- /cdn/ path onto private storage keys, WITHOUT touching logos.
--
-- Legacy representation (public, exposed):
--   easebuzz_sub_merchant.<type>_url =
--     https://<host>/cdn/{restaurantId}/kyc_{docType}_v{n}.pdf   (or .webp/.img)
--   Apache: Alias /cdn/ -> /var/www/cdn.kbook.iadv.cloud/restaurants/
--   => on disk at: /var/www/cdn.kbook.iadv.cloud/restaurants/{restaurantId}/kyc_{docType}_v{n}.<ext>
--
-- New representation (private, authenticated):
--   easebuzz_sub_merchant.<type>_key = kyc/{restaurantId}/{original filename}
--   resolved under kbook.private-docs.base-path (/var/www/kbook-private/documents/)
--   => on disk at: /var/www/kbook-private/documents/kyc/{restaurantId}/{original filename}
--
-- The private KEY preserves the ORIGINAL filename (kyc_{docType}_v{n}.<ext>) so
-- this SQL and the shell move script agree on the exact file, with no guessing.
--
-- RUN ORDER (see runbook): take backups -> run this in a transaction to preview
-- (SELECTs) -> run the shell move (dry-run then real) -> COMMIT the UPDATEs.
-- The UPDATEs below are idempotent: they only backfill rows whose *_key is null
-- and whose *_url points at the /cdn/ path.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) DETECTION: rows still holding a public /cdn/ KYC URL (pre-reconciliation).
-- ---------------------------------------------------------------------------
SELECT id, restaurant_id,
       id_proof_url, bank_proof_url, business_proof_1_url, business_proof_2_url
FROM easebuzz_sub_merchant
WHERE (id_proof_url IS NOT NULL         AND id_proof_key IS NULL         AND id_proof_url LIKE '%/cdn/%')
   OR (bank_proof_url IS NOT NULL       AND bank_proof_key IS NULL       AND bank_proof_url LIKE '%/cdn/%')
   OR (business_proof_1_url IS NOT NULL AND business_proof_1_key IS NULL AND business_proof_1_url LIKE '%/cdn/%')
   OR (business_proof_2_url IS NOT NULL AND business_proof_2_key IS NULL AND business_proof_2_url LIKE '%/cdn/%');

-- Helper: the private key is "kyc/{restaurant_id}/{filename}", where {filename}
-- is the last path segment of the existing URL (everything after the final '/').

-- ---------------------------------------------------------------------------
-- 2) BACKFILL: set <type>_key from the existing URL's filename. Idempotent.
--    Run inside a transaction; verify with the SELECTs in (3) before COMMIT.
-- ---------------------------------------------------------------------------
BEGIN;

UPDATE easebuzz_sub_merchant
SET id_proof_key = 'kyc/' || restaurant_id || '/' || regexp_replace(id_proof_url, '^.*/', '')
WHERE id_proof_url IS NOT NULL AND id_proof_key IS NULL AND id_proof_url LIKE '%/cdn/%';

UPDATE easebuzz_sub_merchant
SET bank_proof_key = 'kyc/' || restaurant_id || '/' || regexp_replace(bank_proof_url, '^.*/', '')
WHERE bank_proof_url IS NOT NULL AND bank_proof_key IS NULL AND bank_proof_url LIKE '%/cdn/%';

UPDATE easebuzz_sub_merchant
SET business_proof_1_key = 'kyc/' || restaurant_id || '/' || regexp_replace(business_proof_1_url, '^.*/', '')
WHERE business_proof_1_url IS NOT NULL AND business_proof_1_key IS NULL AND business_proof_1_url LIKE '%/cdn/%';

UPDATE easebuzz_sub_merchant
SET business_proof_2_key = 'kyc/' || restaurant_id || '/' || regexp_replace(business_proof_2_url, '^.*/', '')
WHERE business_proof_2_url IS NOT NULL AND business_proof_2_key IS NULL AND business_proof_2_url LIKE '%/cdn/%';

-- ---------------------------------------------------------------------------
-- 3) VERIFY: every backfilled key now maps to its source filename. Confirm the
--    counts/pairs look right BEFORE COMMIT.
-- ---------------------------------------------------------------------------
SELECT id, restaurant_id,
       id_proof_url, id_proof_key,
       bank_proof_url, bank_proof_key,
       business_proof_1_url, business_proof_1_key,
       business_proof_2_url, business_proof_2_key
FROM easebuzz_sub_merchant
WHERE id_proof_key IS NOT NULL OR bank_proof_key IS NOT NULL
   OR business_proof_1_key IS NOT NULL OR business_proof_2_key IS NOT NULL;

-- COMMIT;   -- uncomment to apply once verified and after the shell move succeeds
-- ROLLBACK; -- to abort

-- ---------------------------------------------------------------------------
-- 4) FINALIZE (OPTIONAL, only after the shell move is verified and the app is
--    confirmed serving from the private path): clear the legacy public URLs so
--    nothing can reconstruct the old /cdn/ link. Run as a separate step.
-- ---------------------------------------------------------------------------
-- BEGIN;
-- UPDATE easebuzz_sub_merchant SET id_proof_url = NULL         WHERE id_proof_key IS NOT NULL;
-- UPDATE easebuzz_sub_merchant SET bank_proof_url = NULL       WHERE bank_proof_key IS NOT NULL;
-- UPDATE easebuzz_sub_merchant SET business_proof_1_url = NULL WHERE business_proof_1_key IS NOT NULL;
-- UPDATE easebuzz_sub_merchant SET business_proof_2_url = NULL WHERE business_proof_2_key IS NOT NULL;
-- COMMIT;
