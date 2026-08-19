# SECURITY: Credential Rotation Required

**Date:** 2026-08-19
**Severity:** HIGH
**Status:** Files untracked from git. Rotation pending.

## What Happened

The following files with secrets were committed to git history:

| File | Contains | Risk |
|---|---|---|
| `.env.v2` | PostgreSQL password, DB URL | DB access if repo exposed |
| `Android/app/google-services.json` | Firebase API key, project IDs | Firebase abuse (quota, billing) |
| `server/.env.production` (historical) | Production credentials | Full server access |

## Actions Taken (2026-08-19)

- [x] `.env.v2` removed from git tracking (`git rm --cached`)
- [x] `google-services.json` removed from git tracking (`git rm --cached`)
- [x] Added `Android/app/google-services.json` to `.gitignore`
- [ ] **Rotate PostgreSQL password** (VPS + docker-compose .env)
- [ ] **Rotate Firebase API key** (Firebase Console → Project Settings → regenerate)
- [ ] **Rotate Easebuzz salt** if it was ever in `.env.v2`
- [ ] Consider `git filter-branch` / BFG to purge from history (optional if repo is private)

## Rotation Steps

### PostgreSQL Password
```bash
# On VPS:
cd /var/www/kbook.iadv.cloud
# Edit .env → change DB_PASSWORD
# Then:
docker compose exec postgres psql -U kbookuser -c "ALTER USER kbookuser WITH PASSWORD 'NEW_SECURE_PASSWORD';"
docker compose --env-file .env -f docker-compose.production.yml restart server
```

### Firebase API Key
1. Go to Firebase Console → Project Settings
2. Under "Your apps" → Android app → delete and re-add the app
3. Download new `google-services.json`
4. Place in `Android/app/google-services.json` (gitignored, stays local)

### Easebuzz Credentials
- Confirm with Easebuzz if salt rotation is possible
- If yes, update `.env` on VPS and `application.properties` env vars

## Prevention

- `.gitignore` now covers all sensitive files
- AGENTS.md convention: "Do not commit secrets"
- Consider pre-commit hook: `git secrets --install`
