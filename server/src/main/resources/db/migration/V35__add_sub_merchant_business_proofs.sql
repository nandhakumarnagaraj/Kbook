-- EaseBuzz CPV: business proof documents.
-- Proprietorship entities must provide two valid business proof documents;
-- other entity types may require them based on EaseBuzz's risk assessment.
-- Each slot records the document type and the uploaded document URL.

ALTER TABLE easebuzz_sub_merchant
    ADD COLUMN IF NOT EXISTS business_proof_1_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS business_proof_1_url  TEXT,
    ADD COLUMN IF NOT EXISTS business_proof_2_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS business_proof_2_url  TEXT;
