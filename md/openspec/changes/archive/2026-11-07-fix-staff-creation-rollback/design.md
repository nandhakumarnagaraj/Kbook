# Design: Resilient Staff Onboarding & Transaction Isolation

## Context
See `proposal.md` for problem motivation. In Spring Boot JPA transactions, any `@Transactional` boundary participating in an active transaction invokes `setRollbackOnly()` if an unchecked exception bubbles out of the proxy, regardless of whether a parent caller intercepts that exception.

## Goals / Non-Goals

**Goals:**
- Guarantee that staff record creation in PostgreSQL commits reliably even if third-party WhatsApp credential dispatch fails.
- Provide clear `credentialsSent: false` signaling in the API response so the UI can prompt the user to re-send credentials or instruct staff to use Forgot Password.
- Streamline the Web Admin staff form by setting default role selection.
- Ensure Angular production builds pass cleanly.

**Non-Goals:**
- Implementing SMS fallback or alternate delivery providers (Meta WhatsApp Graph API remains the configured transport).
- Altering the staff permission evaluation logic or terminal-scoping invariants.

## Decisions

### Decision 1: Remove `@Transactional` from `sendStaffCredentials()`
- **Rationale**: `sendStaffCredentials()` performs zero database queries or mutations; it purely coordinates external HTTP requests to Meta's WhatsApp API. Removing `@Transactional` ensures that any `IllegalStateException` thrown by HTTP failures reaches `BusinessWriteService.createStaff()` directly without Spring transaction interceptors marking the database transaction as rollback-only.
- **Alternatives Considered**:
  - `Propagation.NOT_SUPPORTED`: Works, but adds unnecessary transaction proxy overhead to a method that touches no persistent entities.
  - Suppressing exceptions entirely inside `sendTemplateMessage`: Would mask failures from other caller workflows that rely on exception signaling (such as explicit credential re-send).

### Decision 2: Default `role: 'SHOP_STAFF'` in `StaffFormModalComponent`
- **Rationale**: Sales Staff is the only assignable role in the modal; leaving it empty forced unnecessary clicks and left the form invalid.
- **Alternatives Considered**: Retaining empty selection — rejected due to poor UX and redundant validation trips.

## Risks / Trade-offs

- **[Risk] Staff created with undelivered credentials** → **Mitigation**: The API response explicitly returns `credentialsSent: false`, and the Web Admin UI renders a prominent warning panel giving the owner a single-click "Re-send password" button and a reminder that staff can also use "Forgot Password" in the POS app.
