ALTER TABLE easebuzz_sub_merchant
    ALTER COLUMN easebuzz_response TYPE TEXT USING easebuzz_response::text;
