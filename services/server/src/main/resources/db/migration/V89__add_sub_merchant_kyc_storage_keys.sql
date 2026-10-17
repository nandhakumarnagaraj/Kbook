-- Private storage keys for EaseBuzz KYC documents.
--
-- Context: KYC PII documents (id proof, bank proof, two business proofs) were
-- stored under the Apache-public /cdn/ path (Alias /cdn/ ->
-- /var/www/cdn.kbook.iadv.cloud/restaurants/), making them publicly enumerable.
-- Remediation moves these four document types to a PRIVATE filesystem path
-- served only through authenticated Spring endpoints.
--
-- Representation change (old -> new):
--   OLD: <type>_url  holds a public URL, e.g.
--        https://kbook.iadv.cloud/cdn/{restaurantId}/kyc_business_proof_1_v1.pdf
--   NEW: <type>_key  holds a relative private storage key, e.g.
--        kyc/{restaurantId}/business_proof_1_<uuid>.pdf
--        (resolved under kbook.private-docs.base-path, NOT under /cdn/)
--
-- The legacy *_url columns are retained (not dropped) so that:
--   1. Reconciliation can map existing public files to new private keys.
--   2. Rollback can restore the previous public-serving behaviour if needed.
-- Logos are unaffected (logo_url on restaurantprofiles stays public by design).

ALTER TABLE easebuzz_sub_merchant ADD COLUMN IF NOT EXISTS id_proof_key TEXT;
ALTER TABLE easebuzz_sub_merchant ADD COLUMN IF NOT EXISTS bank_proof_key TEXT;
ALTER TABLE easebuzz_sub_merchant ADD COLUMN IF NOT EXISTS business_proof_1_key TEXT;
ALTER TABLE easebuzz_sub_merchant ADD COLUMN IF NOT EXISTS business_proof_2_key TEXT;
