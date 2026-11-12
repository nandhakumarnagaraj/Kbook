-- Widen phone_number column in otp_requests to accommodate email addresses for email verification
ALTER TABLE otp_requests ALTER COLUMN phone_number TYPE VARCHAR(255);
