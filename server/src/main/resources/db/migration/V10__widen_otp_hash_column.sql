-- Widen otp column to store BCrypt hash (60 chars) instead of plaintext 6-digit OTP
ALTER TABLE otp_requests ALTER COLUMN otp TYPE VARCHAR(60);
