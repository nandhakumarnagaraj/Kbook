# Specification: Terminal Management & Scoping

## Purpose
Governs till terminal registration, primary terminal assignment, number-matching challenge verification, strict 5-terminal limit enforcement, and terminal-level data scoping.

## Requirements

### Requirement: 5-Terminal Limit per Restaurant
The system SHALL strictly restrict the maximum number of active till terminals to five (5) per restaurant tenant.

#### Scenario: Registering terminal within limit
- **WHEN** a restaurant with fewer than 5 active terminals initiates a registration request
- **THEN** the server creates a `DeviceRegistrationRequest` with status `PENDING_APPROVAL` and returns HTTP 202.

#### Scenario: Exceeding 5-terminal limit
- **WHEN** an approval is attempted for a 6th active terminal
- **THEN** the server acquires a pessimistic row lock on `RestaurantProfile`, detects active terminal count >= 5, and rejects the approval with `TERMINAL_LIMIT_REACHED`.

---

### Requirement: Challenge Code Verification
Terminal activation and recovery SHALL require out-of-band verification using a 6-digit challenge code displayed on the requesting device and confirmed by the owner.

#### Scenario: Number matching approval
- **WHEN** the restaurant owner enters the matching 6-digit challenge code in Web Admin or Primary POS
- **THEN** the terminal is issued an `X-Terminal-Token` and status is transitioned to `ACTIVE`.

#### Scenario: Expired or mismatched challenge code
- **WHEN** the entered code does not match or has expired (>15 minutes)
- **THEN** the request transitions to `MISMATCH` or `EXPIRED` and token issuance is denied.

---

### Requirement: Terminal-Scoped Bill Isolation
Android POS terminals SHALL be strictly restricted to accessing, editing, and printing only bills originated by that specific terminal.

#### Scenario: Staff viewing active orders on terminal
- **WHEN** a cashier opens the active orders screen on Terminal T1
- **THEN** the query filters by `terminal_id = T1` and excludes bills belonging to Terminal T2..T5.

#### Scenario: Cross-terminal modification attempt
- **WHEN** an update request is sent to the server for a bill owned by a different terminal
- **THEN** the server rejects the request with `CROSS_TERMINAL_UPDATE`.
