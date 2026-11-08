# staff-management Specification

## Purpose
Enables restaurant owners to provision and manage shop staff accounts with fine-grained POS permissions and resilient, non-blocking credential delivery.

## Requirements

### Requirement: Staff Account Creation and Role Assignment
The server SHALL allow restaurant owners to create staff accounts with role SHOP_STAFF bound strictly to their restaurant tenant, rejecting duplicate active phone numbers.

#### Scenario: Successful staff account creation
- **WHEN** an authenticated OWNER submits valid name and unique 10-digit phone number
- **THEN** a new User entity is created with role SHOP_STAFF and is_active set to true

#### Scenario: Duplicate phone number rejection
- **WHEN** an OWNER attempts to register a phone number already assigned to an active user
- **THEN** the request is rejected with HTTP 409 Conflict

### Requirement: Non-Blocking Credential Delivery
The system SHALL deliver staff credentials via WhatsApp without causing the staff creation transaction to fail or roll back if third-party delivery is degraded or unconfigured.

#### Scenario: Message gateway delivery failure
- **WHEN** the third-party WhatsApp service returns an HTTP error or is unreachable
- **THEN** the server commits the staff account in the database and returns credentialsSent as false

#### Scenario: Successful credential dispatch
- **WHEN** the third-party WhatsApp service accepts the credential template message
- **THEN** the server returns credentialsSent as true alongside the created staff details

### Requirement: Web Admin Staff Form Usability
The Web Admin staff modal SHALL default the role field to SHOP_STAFF in create mode, enabling submission once name and phone are filled.

#### Scenario: Opening create staff modal
- **WHEN** the restaurant owner opens the Add Staff Member modal
- **THEN** the role field defaults to SHOP_STAFF and the form enables submission upon entering valid name and phone
