UPDATE users
SET auth_provider = CASE
    WHEN google_email IS NOT NULL AND google_email <> '' AND login_id = google_email THEN 'GOOGLE'
    ELSE 'PHONE'
END
WHERE auth_provider IS NULL
   OR auth_provider NOT IN ('PHONE', 'GOOGLE')
   OR (google_email IS NOT NULL AND google_email <> '' AND login_id = google_email AND auth_provider <> 'GOOGLE');

UPDATE users
SET login_id = CASE
    WHEN auth_provider = 'GOOGLE' AND google_email IS NOT NULL AND google_email <> '' THEN google_email
    ELSE email
END
WHERE login_id IS NULL
   OR login_id = '';

ALTER TABLE users
    ALTER COLUMN auth_provider SET NOT NULL;

ALTER TABLE users
    ALTER COLUMN login_id SET NOT NULL;
