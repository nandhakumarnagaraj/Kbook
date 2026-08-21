# Credential Rotation Plan & Git History Purge

**Created:** 2026-08-21  
**Status:** ACTION REQUIRED  
**Supersedes:** `docs/SECURITY_ROTATION_REQUIRED.md`

---

## 1. Inventory of Committed Secrets

All secrets below were at some point committed to the git repository history and must be considered compromised, even though the files are now `.gitignore`d.

### 1.1 `.env` / `.env.v2` (Root-level)

| Key Name | Type | Risk if Exposed |
|----------|------|-----------------|
| `POSTGRES_PASSWORD` | Database credential | Full DB read/write access |
| `DB_PASSWORD` | Database credential (alias) | Full DB read/write access |
| `JWT_SECRET` | HMAC signing key | Token forgery, session hijacking |
| `GOOGLE_CLIENT_ID` | OAuth client ID | Phishing via OAuth consent screen |
| `WHATSAPP_META_ACCESS_TOKEN` | Meta Cloud API token | Send messages as the business |
| `WHATSAPP_META_PHONE_NUMBER_ID` | WhatsApp phone ID | Message routing abuse |
| `PAYMENT_CRYPTO_SECRET` | Payment HMAC key | Payment tampering |
| `STITCH_API_KEY` | Stitch Design API key | Unauthorized API usage |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP credentials | Send email as the business |
| `EASEBUZZ_MERCHANT_KEY` | Payment gateway key | Financial fraud |
| `EASEBUZZ_SALT` | Payment HMAC salt | Payment hash forging |
| `EASEBUZZ_WIRE_API_KEY` | Sub-merchant mgmt key | KYC/merchant data access |

### 1.2 `Android/app/google-services.json`

| Item | Type | Risk if Exposed |
|------|------|-----------------|
| Firebase API key (`current_key`) | API key | Quota abuse, billing fraud |
| OAuth client IDs (multiple) | OAuth credentials | Impersonation via OAuth |
| Project number / Project ID (`new-khanabook-li`) | Project identifier | Targeted attacks on Firebase project |
| App IDs (mobilesdk_app_id) | App identifiers | Low risk alone, but aids targeting |

### 1.3 `Android/secrets.properties`

| Key Name | Type | Risk if Exposed |
|----------|------|-----------------|
| `SIGNING_STORE_PASSWORD` | Keystore password | Sign APKs as official app |
| `SIGNING_KEY_ALIAS` | Key alias | Used with keystore |
| `SIGNING_KEY_PASSWORD` | Key password | Sign APKs as official app |
| `GOOGLE_WEB_CLIENT_ID` | OAuth client ID | OAuth abuse |
| `BACKEND_URL` | Server URL | Low risk (public endpoint) |

### 1.4 `Android/release-key.jks` (Binary Keystore)

| Item | Type | Risk if Exposed |
|------|------|-----------------|
| Release signing keystore | Binary key | Sign malicious APKs as official app |

### 1.5 `firebase-service-account.json` (if ever committed)

| Item | Type | Risk if Exposed |
|------|------|-----------------|
| Firebase Admin SDK private key | Service account key | Full Firebase admin access |

### 1.6 `server/.env.production` (historical)

| Item | Type | Risk if Exposed |
|------|------|-----------------|
| All production credentials | Mixed | Full server/DB/payment access |

---

## 2. BFG Repo-Cleaner — History Purge

### 2.1 Prerequisites

```bash
# Install BFG (requires Java 8+)
# Download from: https://rtyley.github.io/bfg-repo-cleaner/
# Or via package manager:
#   brew install bfg        (macOS)
#   choco install bfg       (Windows)
#   scoop install bfg       (Windows/Scoop)
```

### 2.2 Create a Mirror Clone

> ⚠️ Work on a **mirror clone** — never run BFG on your working copy.

```bash
# Clone a bare mirror of the repo
git clone --mirror https://github.com/YOUR_ORG/KhanaBook.git KhanaBook-mirror.git
cd KhanaBook-mirror.git
```

### 2.3 Create Backup

```bash
# Backup before any destructive operation
cp -r ../KhanaBook-mirror.git ../KhanaBook-mirror-BACKUP.git
```

### 2.4 Remove Sensitive Files from History

```bash
# Remove specific files by name (from ALL commits in history)
bfg --delete-files .env
bfg --delete-files .env.v2
bfg --delete-files google-services.json
bfg --delete-files secrets.properties
bfg --delete-files release-key.jks
bfg --delete-files firebase-service-account.json
bfg --delete-files .env.production
```

### 2.5 Remove Sensitive Text Patterns (Belt-and-Suspenders)

Create a file `passwords.txt` containing patterns to scrub (one per line):

```bash
# Create patterns file (use actual values from your records)
cat > ../passwords.txt << 'EOF'
POSTGRES_PASSWORD_VALUE_HERE
JWT_SECRET_VALUE_HERE
WHATSAPP_META_ACCESS_TOKEN_VALUE_HERE
PAYMENT_CRYPTO_SECRET_VALUE_HERE
STITCH_API_KEY_VALUE_HERE
FIREBASE_API_KEY_VALUE_HERE
EASEBUZZ_SALT_VALUE_HERE
EOF
```

> ⚠️ Replace placeholders above with the **actual secret values** that were committed. Do NOT commit `passwords.txt` anywhere.

```bash
# Scrub text patterns from all files in history
bfg --replace-text ../passwords.txt
```

### 2.6 Clean Up and Force Push

```bash
# Expire reflogs and garbage collect
git reflog expire --expire=now --all
git gc --prune=now --aggressive

# Force push ALL branches and tags (destructive — coordinate with team)
git push --force
```

### 2.7 Post-Push Cleanup for All Collaborators

Every collaborator must **re-clone** the repository:

```bash
# Each team member:
rm -rf KhanaBook
git clone https://github.com/YOUR_ORG/KhanaBook.git
```

> ⚠️ Existing local clones still contain the old history. `git pull` is NOT sufficient.

---

## 3. Post-Purge Credential Rotation

After BFG purge, rotate **every** credential that was committed. The old values are compromised regardless of history rewriting (forks, caches, CI logs may retain them).

### 3.1 PostgreSQL Password

```bash
# On VPS (kbook.iadv.cloud):
cd /var/www/kbook.iadv.cloud

# 1. Generate new password
NEW_PW=$(openssl rand -base64 24)
echo "New password: $NEW_PW"   # Note it securely

# 2. Update .env on VPS
sed -i "s/POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$NEW_PW/" .env
sed -i "s/DB_PASSWORD=.*/DB_PASSWORD=$NEW_PW/" .env

# 3. Change password in PostgreSQL
docker compose exec postgres psql -U kbookuser -c \
  "ALTER USER kbookuser WITH PASSWORD '$NEW_PW';"

# 4. Restart server to pick up new password
docker compose --env-file .env -f docker-compose.production.yml restart server
```

### 3.2 JWT Secret

```bash
# Generate new JWT secret (64 bytes hex)
NEW_JWT=$(openssl rand -hex 64)

# Update .env on VPS
sed -i "s/JWT_SECRET=.*/JWT_SECRET=$NEW_JWT/" .env

# Restart server (all existing user sessions will be invalidated)
docker compose --env-file .env -f docker-compose.production.yml restart server
```

> ⚠️ Rotating JWT_SECRET logs out ALL users. Plan for off-peak deployment.

### 3.3 Firebase API Key & OAuth Clients

1. Go to [Google Cloud Console](https://console.cloud.google.com/) → Project `new-khanabook-li`
2. Navigate to **APIs & Services → Credentials**
3. **Restrict** or **regenerate** the API key (`current_key` in google-services.json)
4. Re-download `google-services.json` → place in `Android/app/` (gitignored)
5. Restrict the API key to:
   - Android apps only (by package name + SHA-1 fingerprint)
   - Specific APIs (Firebase Cloud Messaging, Firebase Installations, etc.)

### 3.4 Google OAuth Client ID

1. Google Cloud Console → **APIs & Services → Credentials → OAuth 2.0 Client IDs**
2. Rotate the web client: `GOOGLE_CLIENT_ID` (type 3 — web application)
3. Update `.env` on VPS with new client ID
4. Update `Android/secrets.properties` locally with new `GOOGLE_WEB_CLIENT_ID`
5. Rebuild and deploy Android app

### 3.5 WhatsApp Meta Cloud API Token

1. Go to [Meta for Developers](https://developers.facebook.com/) → Your App
2. Navigate to **WhatsApp → API Setup**
3. Generate a **new permanent access token** (or use System User token)
4. Update `.env` on VPS: `WHATSAPP_META_ACCESS_TOKEN`
5. Restart server

### 3.6 Payment Crypto Secret

```bash
# Generate new 32-byte hex key
NEW_PAY_SECRET=$(openssl rand -hex 32)

# Update .env on VPS
sed -i "s/PAYMENT_CRYPTO_SECRET=.*/PAYMENT_CRYPTO_SECRET=$NEW_PAY_SECRET/" .env

# Restart server
docker compose --env-file .env -f docker-compose.production.yml restart server
```

### 3.7 Stitch API Key

1. Go to Google Stitch dashboard
2. Revoke the old key (`STITCH_API_KEY`)
3. Generate a new API key
4. Update `.env` on VPS

### 3.8 Easebuzz Credentials

1. Log in to [Easebuzz Dashboard](https://dashboard.easebuzz.in/)
2. Navigate to **Settings → API Credentials**
3. Request salt regeneration (contact Easebuzz support if self-service unavailable)
4. Update `.env` on VPS:
   - `EASEBUZZ_MERCHANT_KEY`
   - `EASEBUZZ_SALT`
   - `EASEBUZZ_WIRE_API_KEY`
5. Restart server

### 3.9 Android Release Keystore

> ⚠️ If `release-key.jks` was committed, the keystore is compromised.

**If using Google Play App Signing (recommended):**
- The upload key can be reset via Play Console → Setup → App signing → Request upload key reset
- Generate a new upload keystore and update `secrets.properties`

**If NOT using Play App Signing:**
- You cannot rotate the signing key without publishing a new app listing
- Immediately enroll in [Google Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
- Then reset the upload key as above

### 3.10 Firebase Service Account Key (if committed)

1. Go to Firebase Console → Project Settings → Service Accounts
2. **Revoke** the old key
3. Generate a new private key JSON
4. Deploy to server securely (never commit to git)

### 3.11 SMTP / Email Credentials

1. Go to Google Account → Security → App Passwords (if using Gmail)
2. Revoke the old app password
3. Generate a new app password
4. Update `.env` on VPS: `MAIL_USERNAME`, `MAIL_PASSWORD`

---

## 4. Verification Checklist

### 4.1 History Purge Verification

- [ ] Fresh clone of repo contains NO sensitive files in any commit
  ```bash
  git clone https://github.com/YOUR_ORG/KhanaBook.git /tmp/verify-clone
  cd /tmp/verify-clone
  git log --all --diff-filter=A --name-only --pretty=format: | sort -u | grep -E '\.(env|jks|json)$'
  # Should NOT show: .env, .env.v2, google-services.json, release-key.jks, etc.
  ```
- [ ] Search full history for known secret patterns:
  ```bash
  git log --all -p | grep -c "POSTGRES_PASSWORD="   # Should be 0
  git log --all -p | grep -c "JWT_SECRET="          # Should be 0
  git log --all -p | grep -c "AIzaSy"              # Should be 0 (Firebase key prefix)
  ```
- [ ] GitHub/GitLab cache invalidated (contact support or wait 24h after force push)
- [ ] All forks notified to re-clone (if any)
- [ ] CI/CD pipeline caches cleared

### 4.2 Credential Rotation Verification

- [ ] **PostgreSQL:** Connect with new password succeeds; old password rejected
  ```bash
  docker compose exec postgres psql -U kbookuser -c "SELECT 1;"
  ```
- [ ] **JWT:** Old tokens return 401; new login produces valid token
- [ ] **Firebase:** App can authenticate and use Firebase services
- [ ] **Google OAuth:** Login flow works on Android and web-admin
- [ ] **WhatsApp:** OTP messages send successfully
- [ ] **Payments:** Test transaction completes (use sandbox first)
- [ ] **Stitch:** API calls succeed with new key
- [ ] **Easebuzz:** Test payment flow in sandbox mode
- [ ] **Android APK:** Signed build installs and updates correctly
- [ ] **Email:** Test email sends from server

### 4.3 Preventive Measures

- [ ] `.gitignore` covers all secret files (already done ✓)
- [ ] Install `git-secrets` pre-commit hook:
  ```bash
  git secrets --install
  git secrets --add 'POSTGRES_PASSWORD=(?!CHANGE_ME)'
  git secrets --add 'JWT_SECRET=(?!CHANGE_ME)'
  git secrets --add 'AIzaSy[0-9A-Za-z_-]{33}'
  git secrets --add 'EAA[0-9A-Za-z]+'
  ```
- [ ] Add CI check that blocks PRs containing known secret patterns
- [ ] Document credential storage location (e.g., 1Password, Bitwarden vault)
- [ ] Set calendar reminder for periodic credential rotation (90 days)

---

## 5. Priority Order

Execute in this order to minimize downtime:

1. **BFG purge** (Section 2) — can be done independently
2. **PostgreSQL + JWT** (3.1, 3.2) — brief downtime, do together
3. **Firebase + OAuth** (3.3, 3.4) — requires Android app rebuild
4. **WhatsApp token** (3.5) — affects OTP delivery
5. **Payment keys** (3.6, 3.8) — test in sandbox first
6. **Android keystore** (3.9) — coordinate with Play Store release
7. **Remaining** (3.7, 3.10, 3.11) — lower urgency

---

## 6. Emergency Contacts

| Service | Rotation Docs |
|---------|---------------|
| Firebase | https://console.firebase.google.com/ → Project Settings |
| Google Cloud | https://console.cloud.google.com/apis/credentials |
| Meta/WhatsApp | https://developers.facebook.com/ |
| Easebuzz | https://dashboard.easebuzz.in/ or support@easebuzz.in |
| Google Play Signing | https://play.google.com/console → App signing |

---

*This document contains NO secret values. All referenced credentials must be obtained from the production environment or password manager.*
