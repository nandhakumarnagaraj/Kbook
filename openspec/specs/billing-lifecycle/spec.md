# Specification: Billing Lifecycle & Money Path

## Purpose
Specifies the end-to-end order and billing lifecycle, supporting both Pay-Before-Food and Pay-After-Food modes, GST calculation rules, and event-sourced KOT ticket dispatching.

## Requirements

### Requirement: Dining Flow Support
The system SHALL support both `PAY_BEFORE_FOOD` (QSR/Fast Casual) and `PAY_AFTER_FOOD` (Dine-in) order workflows configurable per restaurant profile.

#### Scenario: Pay After Food (Dine-in) flow
- **WHEN** items are added to a cart in `PAY_AFTER_FOOD` mode
- **THEN** the order is saved as `DRAFT`, assigns an order token, generates KOT events for the kitchen, and remains open until payment collection.

#### Scenario: Pay Before Food (QSR) flow
- **WHEN** an order is placed in `PAY_BEFORE_FOOD` mode
- **THEN** payment collection is required immediately, transitioning the bill directly to `COMPLETED` and generating both customer receipt and kitchen token simultaneously.

---

### Requirement: GST Computation Engine
The billing engine SHALL compute SGST/CGST or IGST based on item tax configuration, restaurant registration state, and reverse-charge rules.

#### Scenario: GST-enabled restaurant billing
- **WHEN** a bill is calculated with GST enabled
- **THEN** SGST and CGST amounts are split equally, SAC/HSN codes are printed, and the restaurant GSTIN is included in invoice headers.

#### Scenario: Non-GST or Composition taxpayer billing
- **WHEN** GST is disabled or the restaurant operates under composition scheme
- **THEN** tax line items are omitted or summarized, and the bill header omits the tax invoice designation per statutory requirements.

---

### Requirement: Event-Sourced KOT Pipeline
Kitchen Order Tickets (KOT) SHALL be generated as immutable delta events tagged with a unique `batchToken`, ensuring kitchen staff only receive newly added or voided items.

#### Scenario: Appending items to active draft order
- **WHEN** a cashier adds additional items to an existing draft table order
- **THEN** the system generates `KotEvent` records with `ADD` status for the newly added items only, assigns a fresh `batchToken`, and prints only the increment to the kitchen printer.

#### Scenario: Print transport confirmation
- **WHEN** a KOT ticket is successfully sent over Bluetooth, USB, or Network socket
- **THEN** `sentToKot` is marked `true` as a physical hardware receipt confirmation without altering the order's business completion state.
