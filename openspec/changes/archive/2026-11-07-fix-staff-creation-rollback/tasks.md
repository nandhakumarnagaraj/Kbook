# Tasks

## 1. Server API & Transaction Decoupling

- [x] 1.1 Remove `@Transactional` from `PasswordResetOtpService.sendStaffCredentials()` to prevent WhatsApp HTTP failures from marking active DB transaction rollback-only.
- [x] 1.2 Verify `BusinessWriteService.createStaff()` catches `RuntimeException` cleanly, logs a warning, persists the staff user, and returns `credentialsSent: false`.

## 2. Web Admin Usability & Build Integrity

- [x] 2.1 Default `role: 'SHOP_STAFF'` in `StaffFormModalComponent` so the form enables submission once name and phone are filled.
- [x] 2.2 Fix root `.gitignore` pattern from `backups/` to `/backups/` and provide `BackupsPageComponent` so Angular production build succeeds.

## 3. Deployment & Integration Verification

- [x] 3.1 Rebuild backend server container image with stamped git properties and start container.
- [x] 3.2 Execute end-to-end API test calling `POST /api/v1/business/staff` with owner JWT token and verify HTTP 200 response with `credentialsSent: false`.
- [x] 3.3 Confirm staff user and permissions persist in PostgreSQL and clean up test record.
- [x] 3.4 Update codebase knowledge graph with `graphify update .`.

## Workflow follow-up

- Sync delta specs to main specs (`openspec sync-specs`) or archive upon final sign-off.
