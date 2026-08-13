# KhanaBook UX Simplification Plan

## Goal
Reduce time-to-first-bill from ~10 minutes to under 2 minutes for a new user.

## Current Flow (8 mandatory screens)
```
Signup (phone + OTP + name + password)
  → Initial Sync (wait for server data)
    → Background Reliability (battery optimization prompt)
      → Home (dashboard)
        → Settings > Menu > Add items (one by one)
          → Back to Home > New Bill
            → Step 1: Menu selection
              → Step 2: Cart
                → Step 3: Payment
```

## Proposed Flow (4 screens to first bill)
```
Signup (phone + OTP + name + password) [unchanged — server requires it]
  → Quick Start Wizard (shop name + 3-5 menu items inline)
    → Home (dashboard with "Create your first bill!" CTA)
      → New Bill (menu ready, immediate billing)
```

## Changes

### 1. Quick Start Wizard (NEW SCREEN)
**Where:** After successful signup/login when `isInitialSyncCompleted = false` AND this is a new restaurant (no existing menu items).

**Steps:**
1. **Shop name** (single text field, required)
2. **Quick menu** (add 3-5 items: name + price, no category required — auto-assigned to "General")
3. **Done** → triggers sync + navigates to Home

**Design:**
- Single scrollable screen with card-based sections
- "Skip" button always visible for users who want full manual setup later
- Items can be added with just name + price (₹) — category defaults to "General"
- No printer/tax/payment config here — those come later

**Why this works:** A street food vendor needs item names and prices. Nothing else. Categories, variants, GST, printer — all optional for first bill.

### 2. Defer Background Reliability
**Change:** Remove from mandatory first-run flow. Move to:
- A banner on HomeScreen after 5 bills (when sync matters)
- Settings > Support section (already accessible)

**Implementation:** Skip the `background_reliability` route in `AppNavGraph`. Add a one-time banner trigger in `HomeViewModel` after `totalBillCount >= 5`.

### 3. Progressive Settings (Phase 2 — separate PR)
Current settings sections: Menu, Shop Config, Printer, Tax, Payment, App Lock, Interaction, Sync Center, Support, About.

**Essential (show always):** Menu, Shop Config
**Show after first bill:** Printer, Payment, Tax
**Show after 5 bills or multi-device:** App Lock, Sync Center
**Always hidden in sidebar:** Interaction Feedback (move to About)

This is a lower-risk change but requires more UI refactoring. Do it after the wizard ships.

## Technical Implementation

### QuickStartScreen.kt
- New composable in `ui/screens/`
- Receives `SettingsViewModel` to save profile and `MenuViewModel` to save items
- Creates a default "General" category if none exists
- Saves menu items locally → triggers sync
- Sets `isInitialSyncCompleted = true` and `isQuickStartCompleted = true`

### Navigation Changes (AppNavGraph.kt)
- After login/signup, check: if new restaurant (no menu items pulled from sync), route to `quick_start`
- If existing restaurant (login on second device with existing data), keep current initial_sync flow
- Quick start completion routes to `main/0` (Home tab)

### SessionManager Changes
- Add `isQuickStartCompleted(): Boolean` / `setQuickStartCompleted(Boolean)`
- Used to determine if wizard should show

### Server Side
- No server changes needed — quick start uses existing sync push APIs
- Category and menu item push already works

## What We're NOT Changing
- Signup flow (server requires phone + OTP + password)
- Billing flow itself (menu → cart → payment is already good)
- Login flow for existing users
- Multi-device terminal activation (necessary for security)

## Architecture Fix: BillCreationUseCase

Alongside the UX changes, a critical architecture fix was implemented to eliminate the
#1 root cause of billing bugs:

### Problem
`BillingViewModel.kt` (70KB) contained **three separate bill-creation paths** with ~80%
duplicated code: `completeOrder()`, `createDraftOnlineBill()`, `saveDraftOrder()`. Bugs
fixed in one path (invoice allocation, counter increment) had to be manually replicated
to all three — and frequently weren't.

### Solution
New file: `domain/manager/BillCreationUseCase.kt` (320 lines)

A single `createBill(params: BillCreationParams): BillCreationResult` method that:
- Accepts a sealed `BillIntent` (Settle | DraftForPayment | DraftForDineIn)
- Handles all counter allocation, invoice identity, publicToken generation
- Builds bill + items + payments consistently
- Returns a clean result type

The ViewModel still owns: loading state, cart state, sync trigger, print dispatch.

### Migration Path
1. ✅ Created `BillCreationUseCase` with `BillIntent` sealed class
2. ✅ Created `CartItemSnapshot` to decouple from UI types
3. ✅ Added unit tests (`BillCreationUseCaseTest`)
4. Next: Wire `BillingViewModel.completeOrder()` to use the use case (replaces ~130 lines)
5. Next: Wire `createDraftOnlineBill()` and `saveDraftOrder()` similarly
6. Final: Delete the old duplicated code from BillingViewModel


## Risk Assessment
- **Low risk:** This is an additive screen with a skip button. Existing users never see it.
- **Rollback:** If `isQuickStartCompleted` flag is false and user has menu items (from skip or manual), they go to normal Home.
- **Edge case:** User signs up, adds items in wizard, then gets sync conflict. Handled by existing sync infrastructure — wizard items are local Room records that push normally.
