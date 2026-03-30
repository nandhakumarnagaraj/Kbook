ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_provider varchar(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS login_id varchar(255);

UPDATE users
SET auth_provider = CASE
    WHEN google_email IS NOT NULL AND google_email <> '' AND (login_id = google_email OR email = google_email) THEN 'GOOGLE'
    ELSE 'PHONE'
END
WHERE auth_provider IS NULL
   OR auth_provider NOT IN ('PHONE', 'GOOGLE');

UPDATE users
SET login_id = CASE
    WHEN auth_provider = 'GOOGLE' AND google_email IS NOT NULL AND google_email <> '' THEN google_email
    ELSE email
END
WHERE login_id IS NULL
   OR login_id = '';

DROP INDEX IF EXISTS ux_users_login_id;

CREATE INDEX IF NOT EXISTS idx_users_google_email ON users(google_email);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk6adilkxukeh17wlhyuujhsml6'
          AND connamespace = 'public'::regnamespace
    ) THEN
        ALTER TABLE restaurantprofiles DROP CONSTRAINT uk6adilkxukeh17wlhyuujhsml6;
    END IF;
END $$;
