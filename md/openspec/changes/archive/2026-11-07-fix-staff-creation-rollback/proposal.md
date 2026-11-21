# Proposal: Staff Onboarding Delivery Resilience & Creation Fix

## Why
Creating staff members via the Web Admin dashboard failed with an unexpected HTTP 500 error (`UnexpectedRollbackException`) whenever the WhatsApp message gateway returned an error (e.g. template unapproved or missing configuration). The server caught the delivery exception intended to make delivery optional, but because the delivery service method was annotated with `@Transactional`, Spring's transaction interceptor marked the entire active transaction as `rollback-only`, silently rolling back the newly created user in PostgreSQL upon commit.

## What Changes
- **Decoupled Notification Delivery from DB Transaction**: Removed `@Transactional` from `PasswordResetOtpService.sendStaffCredentials()` to ensure third-party HTTP delivery failures never mark the parent database transaction as rollback-only.
- **Form Usability Default**: Defaulted the role select control in `StaffFormModalComponent` to `'SHOP_STAFF'` so the modal is immediately submittable once the staff member's name and 10-digit phone number are entered.
- **Build & Artifact Integrity**: Added missing `BackupsPageComponent` and refined `.gitignore` so Angular production builds compile without failing on uncommitted stub routes.

## Capabilities

### New Capabilities
- `staff-management`: Defines staff creation, role assignment (`SHOP_STAFF`), permission sets, and resilient delivery of credentials without blocking account persistence.

### Modified Capabilities
*(None - existing billing, terminal, sync, and payment specs remain unchanged)*

## Impact
- **Affected Platforms**: Server (`services/server`), Web Admin (`apps/web-admin`).
- **Affected Roles**: `OWNER` (creates staff), `SHOP_STAFF` (account persistence and credentials receipt).
- **Database/Sync Impact**: Zero schema changes; guarantees new staff user and permission rows commit successfully even when WhatsApp delivery is degraded or offline.
