# KYC Document Exposure Remediation — Production Runbook

Moves the four EaseBuzz KYC document types (`id_proof`, `bank_proof`,
`business_proof_1`, `business_proof_2`) off the Apache-public `/cdn/` path onto
private, authenticated storage. **Logos are intentionally untouched** (they are
consumed by public invoices and must stay public).

## Why a code deploy alone is NOT enough
Apache serves `/cdn/` directly from disk:
```
Alias /cdn/ /var/www/cdn.kbook.iadv.cloud/restaurants/
<Directory ...> Require all granted </Directory>
```
Existing KYC files physically live under that directory, so they stay publicly
readable until they are **moved on disk** and the DB is **backfilled** to point
at the private path. New uploads become private immediately on code deploy; the
existing exposure is closed only after the steps below.

## Representation change
| | Old (public) | New (private) |
|---|---|---|
| DB | `<type>_url` = `https://host/cdn/{rid}/kyc_{type}_v{n}.pdf` | `<type>_key` = `kyc/{rid}/kyc_{type}_v{n}.pdf` |
| Disk | `/var/www/cdn.kbook.iadv.cloud/restaurants/{rid}/kyc_*` | `/var/www/kbook-private/documents/kyc/{rid}/kyc_*` |
| Access | direct Apache URL (no auth) | `GET /api/v1/business/kyc-document/{type}/download` (auth, tenant-scoped) |

## Preconditions
- `PRIVATE_DOCS_BASE_PATH=/var/www/kbook-private/documents/` set for the server
  container/env, and the directory is **outside** any Apache `Alias`.
- Confirm Apache has **no** alias mapping the private path (see step 8).

## Steps (run on VPS)

### 1. Backups
```bash
cd /var/www/kbook.iadv.cloud
./ops/backup_postgres.sh                                  # DB backup (mandatory)
tar -czf /var/backups/cdn_kyc_$(date +%F).tar.gz \
    -C /var/www/cdn.kbook.iadv.cloud/restaurants .        # filesystem backup
```

### 2. Deploy new application code
```bash
git pull
./deploy-production.sh                                    # builds image, runs V89 migration, restarts
curl -fsS http://127.0.0.1:8081/api/v1/actuator/health    # expect {"status":"UP"}
```
V89 adds the four `*_key` columns (additive, safe). New KYC uploads now go
private automatically.

### 3. Dry-run the filesystem move (non-destructive)
```bash
./ops/migrate_kyc_documents.sh                            # dry-run: lists what WOULD move
# review /tmp/kyc_migration_*.log — confirm only kyc_* files, expected count
```

### 4. Verify counts
```bash
find /var/www/cdn.kbook.iadv.cloud/restaurants -type f -name 'kyc_*' | wc -l
```
Cross-check against the dry-run "would move" count and the DB detection query
(step 5).

### 5. DB backfill (keys only; URLs kept for now)
```bash
psql "$DB_URL" -f ops/sql/kyc_document_reconciliation.sql   # runs detection + BEGIN...backfill...verify
# review the VERIFY output, then COMMIT (uncomment COMMIT or run it interactively)
```

### 6. Filesystem move (destructive — requires explicit flags + backup dir)
```bash
./ops/migrate_kyc_documents.sh --apply --i-understand-prod --backup-dir /var/backups/kyc
# copies -> verifies size -> deletes public original, per file; writes a log
```

### 7. Post-migration verification
- App serves KYC via auth endpoint (as OWNER token):
  ```bash
  curl -H "Authorization: Bearer <owner-jwt>" \
    http://127.0.0.1:8081/api/v1/business/kyc-document/business_proof_1/download -o /tmp/x.pdf
  ```
- **KYC no longer public** (expect 404/no file):
  ```bash
  curl -I https://kbook.iadv.cloud/cdn/<rid>/kyc_business_proof_1_v1.pdf   # expect 404
  ```
- **Logos still work** (expect 200):
  ```bash
  curl -I https://kbook.iadv.cloud/cdn/<rid>/logo_v1.webp                  # expect 200
  ```
- Sub-merchant status API returns `*Present`/`*DownloadPath`, no `*Url`.

### 8. Apache verification
Confirm the private path is not exposed:
```bash
grep -n "kbook-private" /etc/apache2/sites-enabled/*.conf    # expect: no Alias for it
curl -I https://kbook.iadv.cloud/kbook-private/... 2>/dev/null # expect 404
```
Do NOT add a Spring Security rule for `/cdn/**` as a fix — Apache bypasses Spring.

### 9. Finalize (optional, after a soak period)
Run the FINALIZE block in `ops/sql/kyc_document_reconciliation.sql` to null the
legacy `*_url` columns once you are confident the private path is serving.

## Rollback
- **App/schema:** restore the previous JAR/image; V89 is additive so no schema
  rollback is needed. If required, the `*_key` columns can be ignored/dropped.
- **DB backfill:** `*_url` columns are preserved until step 9, so re-pointing to
  public serving is possible by clearing `*_key` (keys) and relying on `*_url`.
- **Filesystem:** restore from `/var/backups/cdn_kyc_*.tar.gz` (step 1) or the
  targeted backup written by `migrate_kyc_documents.sh --backup-dir`.
- **DB:** `./ops/restore_postgres.sh <backup.sql.gz>`.

## Manual verification still required (cannot be validated from dev)
- The on-disk move actually ran and public KYC URLs now 404.
- Apache does not alias the private directory.
- Private path is included in backup/retention like other production data.
- (Follow-up) encryption-at-rest and a KYC retention/deletion policy.
