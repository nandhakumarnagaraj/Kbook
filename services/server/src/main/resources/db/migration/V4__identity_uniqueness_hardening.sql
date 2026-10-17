-- Normalize identity fields so case-insensitive uniqueness matches application logic.
UPDATE users
SET login_id = lower(btrim(login_id))
WHERE login_id IS NOT NULL
  AND btrim(login_id) <> ''
  AND login_id LIKE '%@%';

UPDATE users
SET email = lower(btrim(email))
WHERE email IS NOT NULL
  AND btrim(email) <> '';

UPDATE users
SET google_email = lower(btrim(google_email))
WHERE google_email IS NOT NULL
  AND btrim(google_email) <> '';

UPDATE users
SET whatsapp_number = btrim(whatsapp_number)
WHERE whatsapp_number IS NOT NULL
  AND whatsapp_number <> btrim(whatsapp_number);

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_login_id_ci
    ON users (lower(login_id))
    WHERE login_id IS NOT NULL AND btrim(login_id) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_google_email_ci
    ON users (lower(google_email))
    WHERE google_email IS NOT NULL AND btrim(google_email) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_whatsapp_number
    ON users (whatsapp_number)
    WHERE whatsapp_number IS NOT NULL AND btrim(whatsapp_number) <> '';
