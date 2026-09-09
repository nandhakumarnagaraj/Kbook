# Arow vs KhanaBook: deeper workflow analysis

Date: 2026-09-08. APK: `com.arowapp.arowapp`, `v1.1.14-c083314`.

## Outcome

Arow's strongest apparent advantage is restaurant-service workflow breadth: structured tables, configurable item choices, kitchen stations and owner controls. KhanaBook already has substantial billing, payment-recovery and inventory functionality. Its staff-capable offline design remains a promising differentiator, not a proven reliability win.

This follow-up adds disassembled code paths and data-model evidence to the [initial comparison](arow-competitor-analysis-2026-09-08.md). It is not a live-app comparison, full source recovery, security audit or exhaustive code review.

## 1. Offline restrictions are supported by executable code

The initial review found settings text. This pass found the corresponding branches:

- `CartActivity.onCreate` constructs `G3.z0`, a click listener whose `onClick` contains the offline checks.
- That listener reads the outlet's offline setting and a local offline-mode preference. When the connectivity helper reports disconnected and offline mode is disabled, it displays a warning and exits that billing branch.
- It then reads `lastOnlineVerificationTime`, compares elapsed time against `0x48190800` = 1,209,600,000 milliseconds = 14 days, and exits with the subscription-verification warning when disconnected and over that limit.
- The inspected connectivity helper, `G3.m6.j`, checks Android's active-network connection state. It does not itself establish that the backend can be reached. Captive portals and connected-but-unusable networks are therefore useful test cases, not demonstrated failures.
- `MainActivity.i` constructs a Firestore listener, `G3.F1`. Its callback checks offline-mode state and the roles `staff` and `co-admin`, then displays a non-cancelable restriction dialog explaining that the owner must disable offline mode to continue.

**Confidence:** high that these restrictions exist in connected application code. Actual account-specific behavior, every entry point, and recovery correctness remain untested. In particular, the staff dialog is triggered by outlet offline-mode state; reducing it to “staff only stop when their own internet disconnects” would be inaccurate.

Arow's older official offline article describes local SQLite writes, a sync queue and disconnected printing, but omits these role/verification qualifications. Treat it as publisher positioning, not proof of unrestricted cashier offline operation. [Official offline article](https://www.arowapp.com/blog/offline-first-tech-saves-restaurants)

## 2. Important differences hidden by the word “supported”

| Workflow | Stronger Arow evidence | KhanaBook's inspected implementation |
|---|---|---|
| Table service | `OrderModel` has floor, table, table number and order identity; `TableModel` tracks order identity/time and whether the bill was printed | Draft dine-in orders store the table label in `customerName`. No equivalent floor/table entity was found. Table-labelled billing is not a full table-management model. |
| Menu customization | `ItemModel` has variants, add-ons and modifier groups; `ModifierGroup` has minimum/maximum selections and options | Item variants and cooking instructions exist. No equivalent persisted modifier-group/add-on selection model was found in bill items. |
| Kitchen stations | Items carry a station; cart code reads and writes station fields; outlet settings enable station-wise KOT | Printer profiles are unique by restaurant and role, and roles are customer/kitchen. This permits only one profile per role per restaurant in that model—not an arbitrary set of kitchen stations. |
| Kitchen display | Paired browser-display resources plus KDS checks in cart code | “Reprint KDS” searches a bill and prints its kitchen ticket. It is not a dedicated live kitchen display. |
| Channel/location pricing | Item price-tier maps, order price-tier snapshots and outlet floor-to-price-tier maps | No equivalent tier workflow found. This is potentially broader than only a delivery-price toggle; application of floor tiers still needs testing. |
| Customer retention | Coupon fields include expiry, minimum order, free item and loyalty-combination flag; customer model has due amount, visits and spending | Customer data exists, but a comparable coupon/loyalty engine or customer-credit ledger was not established. Unpaid dine-in orders are not automatically a Khata product. |
| Expenses | Expense model includes category, amount, quantity/unit, payment mode and vendor details | Inventory purchasing is implemented, but that is not equivalent to a general operating-expense ledger. |

The Arow column establishes code/model presence, not successful calculation, subscription availability or runtime completeness.

KhanaBook references: [BillingViewModel](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/viewmodel/BillingViewModel.kt), [BillItemEntity](../../Android/app/src/main/java/com/khanabook/lite/pos/data/local/entity/BillItemEntity.kt), [PrinterProfileEntity](../../Android/app/src/main/java/com/khanabook/lite/pos/data/local/entity/PrinterProfileEntity.kt), [PrintRouter](../../Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/PrintRouter.kt), [ReprintKdsScreen](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/ReprintKdsScreen.kt).

## 3. What KhanaBook already has—and what must be qualified

- **Multi-terminal ownership:** local bill identity, originating-terminal fields and KOT ownership checks are concrete mechanisms. They do not prove that staff can jointly edit the same disconnected order or freely reprint another terminal's KOT. [BillEntity](../../Android/app/src/main/java/com/khanabook/lite/pos/data/local/entity/BillEntity.kt), [PrintRouter](../../Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/PrintRouter.kt).
- **Payment recovery:** validation distinguishes complete, partial and conflicting recorded payments, checks exact totals and rejects duplicate payment identities/modes. This is substantive implementation evidence, not a benchmark against Arow's payment correctness. [PaymentSetValidator](../../Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/PaymentSetValidator.kt).
- **Inventory depth:** Android deducts menu-item/variant stock; the server separately implements recipe-based raw-material consumption, purchase, wastage and physical-count operations. Do not describe all ingredient workflows as offline Android functionality. [Android consumption](../../Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/InventoryConsumptionManager.kt), [server inventory](../../server/src/main/java/com/khanabook/saas/service/InventoryService.java).
- **Billing refactor is not yet the active entry point:** source search found no production caller of `BillCreationUseCase`. `BillingViewModel` still calls `billRepository.insertFullBill` directly in three creation paths. The existence of a class described as a single source of truth is not proof that live billing uses it.
- **Test names overstate some coverage:** `BillCreationUseCaseTest` checks intent/parameter objects but does not invoke the use case. The inspected `OfflineTest` covers cached screens and reconnection UI, not a five-terminal offline sale/return/reconciliation scenario. Other sync tests exist; no suite or device tests were executed during this analysis. [Use-case test](../../Android/app/src/test/java/com/khanabook/lite/pos/domain/manager/BillCreationUseCaseTest.kt), [offline UI tests](../../Android/app/src/androidTest/java/com/khanabook/lite/pos/test/screens/OfflineTest.kt).

## 4. Commercial claims still need verification

Arow's terms explicitly exclude aggregator integration automation and state a 2.5% commission on online-paid QR orders. Custom zero-commission plans have a 3,000-orders/month allowance before the standard commission applies. Current renewal pricing, actual device/outlet limits, trial duration and enabled integrations were not established. [Official terms](https://www.arowapp.com/terms)

Merchant wallet credits, customer credit, payment QR, customer-ordering QR, split tender and splitting an order into separate bills must each be evaluated separately. Similar names do not establish equivalent products.

## 5. Practical priorities and acceptance tests

These are recommendations from the inspected evidence, not implementation instructions or delivery estimates.

1. **Prove our offline proposition:** multiple terminals create different bills while disconnected; kill/restart one app; reconnect; replay synchronization. Check invoice uniqueness, exact payments, stock and KOT duplication. Test shared-order handoff separately.
2. **For dine-in customers, validate tables and kitchen stations first:** floor/table identity, add-to-order, table move, station routing and printer failure should form one complete restaurant scenario. Add a live KDS only with clear update/acknowledgement behavior.
3. **For configurable menus, validate modifier rules:** require one choice, cap optional choices, price extras, preserve historical selections and print them correctly. Existing size variants do not cover this.
4. **For owner operations, validate late-night closing and price tiers:** a 01:00 sale under a 03:00 rollover must agree across numbering, reports and closing. Test dine-in/delivery/floor prices with discounts and refunds.
5. **Compare onboarding and total cost:** time menu import through the first correct printed bill, obtain a written plan quote, and exercise an authorized Arow demo with synthetic data.

## Evidence and limits

The Android SDK disassembled five extracted DEX files successfully. The local parser traversed 29,424 classes and 211,334 code-bearing methods, indexing 1,230 selected application/relevant methods. These are inventory counts, not a claim that every method was manually reviewed.

- [Selected method, call and model-field evidence](arow-deep-static-evidence-2026-09-08.json)
- [Reproducible indexer](tools/index-arow-dex.cjs)
- [Original APK metadata and resource evidence](arow-apk-evidence-2026-09-08.json)

The same artifact hash was retained. Raw disassembly and extracted DEX files remain in a task-specific local temporary directory, not the repository. The JSON omits credential-like literals and URL paths. Some names are obfuscated; dynamic dispatch and all event branches were not exhaustively traced. Full matching APK splits and an authorized test account are still needed for runtime verification. No app code, deployment, account or competitor backend was changed.
