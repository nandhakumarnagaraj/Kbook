UPDATE users
SET auth_provider = 'GOOGLE'
WHERE google_email IS NOT NULL
  AND google_email <> ''
  AND login_id = google_email;

UPDATE users
SET auth_provider = 'PHONE'
WHERE auth_provider IS NULL
   OR auth_provider NOT IN ('PHONE', 'GOOGLE');
