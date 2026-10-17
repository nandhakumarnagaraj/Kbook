-- Split label rotation support (2026-09-10): Easebuzz has no label-update API.
-- A restaurant bank change requires registering a NEW split label (sm_<id>_vN);
-- these columns track the current label version and the bank it was registered for.
ALTER TABLE easebuzz_sub_merchant
    ADD COLUMN IF NOT EXISTS split_label_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE easebuzz_sub_merchant
    ADD COLUMN IF NOT EXISTS split_label_bank_snapshot TEXT;