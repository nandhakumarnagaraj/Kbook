# KhanaBook — Competitive Landscape

> Canonical entry point for competitor research. One place to see who we are measured
> against, what the evidence actually supports, and which "gaps" still exist today.
>
> **Snapshot dates:** binary teardowns 2026-09-08 / 2026-09-10; UX benchmark undated in
> content (files all touched 2026-10-05). The *KhanaBook side* of every gap claim below
> was re-verified against `main` / HEAD `afa5cc86` on 2026-10-17 and is labelled
> accordingly.
>
> Related: `FEATURE.md` (what we ship), `PROBLEM.md` (open issues),
> `../design/MENU_ITEM_MODEL_INDIA_FIT_GAP.md` (menu/pricing model detail).

## 1. How to read competitor claims

The two bodies of work use different evidence, and mixing them is how bad roadmap
decisions get made.

| Label | Meaning |
|---|---|
| **Published** | the vendor or a store listing says it — marketing, not verification |
| **Packaged** | found in a decompiled APK / bundled resources; proves the code ships, not that it works correctly |
| **Historical** | an old indexed page or snapshot, possibly withdrawn |
| **Inferred** | our analytical interpretation of the above |
| **Verified (KhanaBook)** | confirmed by reading our own source on 2026-10-17 |

Two hard limits apply to everything below:

1. **Asymmetric evidence.** A competitor APK is a partially-obfuscated binary; our side
   is an editable source tree. A "gap" means *we found no evidence of the capability*,
   not that it is absent.
2. **No runtime testing.** No competitor APK was executed, no account created, no
   transaction submitted, no competitor API probed. Nothing here is a reliability or
   security verdict.

## 2. Who we are compared against

### 2.1 Direct competitors (code-level teardown)

| Competitor | Package / version | Positioning | Evidence |
|---|---|---|---|
| **Arow** ("Restaurant Billing App") | `com.arowapp.arowapp` v1.1.14-c083314, ~5K+ installs claimed, published by Arow Labs | Indian restaurant billing + operations, broad workflow pitch | Packaged + Published |
| **Khide Owner** | `com.khide.restaurant` v116.1, published by Ultimate Digital Solutions Pvt. Ltd | Firebase-native POS, part of a 4-app ecosystem (owner / waiter / kitchen / chain) | Packaged + publisher-adjacent listings |
| **BillKaro** | (menu-model analysis only) | minimal POS: 4 tables, no variants | Packaged |

**Analysed but not readable** — do not treat this research as covering the Indian market
leader:

- **Petpooja** — Flutter (Dart AOT); the domain logic sits inside `libapp.so`.
- **EZO** — Kotlin, but `in.co.ezo.data.repo.ItemRepo` is imported by every worker and
  absent from the decompile.
- **OfflineShop** — Flutter AOT.
- **Loyverse** — folder not decompiled.

### 2.2 UX benchmark set (public product/UI review)

**POSist, Petpooja, DotPe, TMBill, eZee/BurrP, Restrofi** — reviewed for navigation, KPI
presentation, order management, settings patterns and visual language. No code involved.

## 3. Document index

| Doc | Covers | Evidence |
|---|---|---|
| `../product/competitor-uiux-analysis.md` (541 lines) | 6-product UI/UX benchmark, comparison table, design recommendations | Published / UI review |
| `../evidence/rnd/arow-competitor-analysis-2026-09-08.md` (191) | Arow: positioning, advertised capabilities, pricing qualifications, APK metadata, inferred architecture, offline/printing/KDS behaviour | Packaged + Published |
| `../evidence/rnd/arow-deep-analysis-2026-09-08.md` | Arow: disassembled offline-gating code paths, workflow breadth, tables/modifiers/printing | Packaged |
| `../evidence/rnd/arow-deep-public-sources-2026-09-08.md` | Arow commercial terms: pricing, QR commission, trial, device caps | Published / Historical |
| `../evidence/rnd/khide-competitor-analysis-2026-09-10.md` (144) | Khide: offline outbox, 5-transport printing, tables, modifiers, Khata ledger, payroll, GSTR-1, AI voice | Packaged |
| `../design/MENU_ITEM_MODEL_INDIA_FIT_GAP.md` | Menu/variant/modifier/tax model across Arow, Khide, BillKaro — and what we implemented | Packaged |
| `../evidence/archive/AROW_VS_KHANABOOK_ANALYSIS_AND_PLAN.txt` (25 KB) | 2026-09-09 Arow gap matrix + architectural plan — **archived, historical** | Inferred |
| Raw evidence (tracked) | `../evidence/rnd/arow-apk-evidence-2026-09-08.json` (72 KB), `../evidence/rnd/arow-deep-static-evidence-2026-09-08.json` (**1.5 MB**), `../evidence/rnd/com.arowapp.arowapp-2026-09-10-evidence.json` (16 KB), `../evidence/rnd/khide-apk-evidence-2026-09-10.json` (12 KB) | Packaged |

> The `../evidence/rnd/` teardowns contain **stale code links** — they point at pre-vertical-slice
> paths (`.../pos/domain/manager/SyncManager.kt`, `.../ui/screens/...`,
> `.../saas/service/...`). Current locations are `feature/<slice>/...` on Android and
> `feature/<slice>/service/...` on the server. Use `FEATURE.md` for current paths.

## 4. The finding that matters most

**Offline-first is no longer a differentiator — it is table stakes.**

- **Arow** ships *"Offline Billing Mode (Owner Only)"*: `CartActivity` contains branches
  that check outlet offline setting + local preference, plus a `lastOnlineVerificationTime`
  comparison against `0x48190800` ms (14 days) that exits billing with a
  subscription-verification warning. A Firestore listener shows a non-cancelable
  "owner must disable offline mode" dialog to `staff` / `co-admin` roles.
- **Khide** ships a full outbox: `OfflineOrderHelper`, `OrderSyncWorker`
  (`order_sync_oneshot` / `order_sync_periodic`), `ForegroundOutboxWatcher`,
  `FirestoreHealthMonitor`, a user-facing sync-status screen with pending/failed rows, and
  `SafeSignOut` that blocks sign-out while an outbox exists.

Our own design (owner **and** staff bill offline, serialized sync, terminal-scoped bills)
is still plausibly better — but *plausibly* is the operative word. Both teardowns reach the
same conclusion: **claim the win with recovery tests, not with architecture.** The test to
run is specific — disconnect an owner device and a staff device, bill concurrently,
reconnect, and reconcile invoices, payments and inventory with no duplicates or losses.
See `../qa/TESTING.md` for the gated multi-device suite
(`MultiDeviceInvoiceSyncIntegrationTest`) and `../engineering/SYNC_CONTRACT.md` for the invariants it
should assert.

## 5. Candidate gaps — re-verified 2026-10-17

Sep-2026 findings checked against the current tree. Two changed; the rest still stand.

### 5.1 Closed or narrowed since the teardown

| Gap (Sep 2026) | Current state |
|---|---|
| **No USB printing** | **Closed** — `PrinterConnectionType` = `BLUETOOTH`, `WIFI`, **`USB`**, with `UsbPrinterTransport.kt` |
| **No print queue / status monitoring** | **Partially matched** — `KitchenPrintQueueManager` (retry queue) and `EscPosPrinterStatus` exist; compare depth against Khide's priority queue + paper/cutter alerts |
| **No GSTR-1 / GST return export** | **Capability exists, no UI** — server has `/business/tax/gst-report`, `/gst-report/csv`, `/gst-return` (`TaxComplianceController`), but the whole `/business/tax/**` family is **orphan** (`PROBLEM.md` item 3). This is a wiring gap, not a build gap |

### 5.2 Still open (evidence of absence, not proof)

| Area | Competitor evidence | Our state (verified 2026-10-17) |
|---|---|---|
| **Table / session model** | Khide: `Table` with `Available`/`Occupied`/`Bill Requested`/`Maintenance`, areas, transfer, open/closed sessions, per-table staff role | No table or session entity — dine-in table is a label in `customerName` |
| **Modifiers / add-ons** | Khide: `portions` (name+price+`recipeFactor`), `extras`, `extrasSingleSelect`, `portionWiseExtras`; Arow: `modifierGroups` with min/max | Variants only; no add-on/modifier model in items or bill lines |
| **"Choose exactly N" modifiers** | **Nobody in the set enforces it** — Arow stores `minSelection` but never passes it to the option adapter | Genuine open differentiator if we implement it |
| **Khata / credit ledger** | Khide: `CREDIT`/`REPAYMENT` transactions, bulk upload, per-day views | Absent |
| **Expense ledger** | Khide: categories + calendar + cash-drawer lines; Arow: price tiers, expenses | Absent as a general ledger |
| **Staff attendance / payroll** | Khide: attendance, comp-off, shifts, document types (Aadhaar/Passport/Voter ID), staff payments | Permissions only; no attendance/payroll |
| **Voice ordering** | Khide: `parseVoiceOrder` cloud function + match UI | Absent |
| **WhatsApp / email bill delivery** | Khide: `WhatsAppBillSender`, email receipts | Absent |
| **Multi-outlet / chain** | Khide: all-outlets dashboard, account switching, separate chain app | Single-restaurant model |
| **Regional languages** | Arow declares 17 locales; Khide English-only | English-only (`values-v31`/`v33` are config variants, not locales) |
| **Business-day rollover** | Arow: configurable day-start (e.g. 03:00); Khide: `BusinessDayConfig` | No cutoff field on the restaurant profile |
| **Forced / optional update management** | Khide: in-app update dialogs with progress | No equivalent found |
| **Coupons / loyalty / price tiers** | Arow: coupons, tiers (incl. a `Delivery` tier), pay-later credit | Absent |
| **Ethernet printing** | Khide: Bluetooth, BLE, USB, Wi-Fi, **Ethernet** | Bluetooth, Wi-Fi, USB — no Ethernet |
| **AI daily insights (UX)** | Restrofi: plain-language "daily insight" panels | Not present (see §6) |

### 5.3 Where we are ahead

| Advantage | Evidence |
|---|---|
| **Self-hosted Spring Boot backend** | vs Khide's Firebase-only stack — control, portability, no vendor lock-in |
| **Easebuzz sub-merchant digital payments** | vs Khide's UPI-link / UPI-intent approach with no aggregator SDK — real settlement, refunds, reconciliation (`../integrations/easebuzz/EASEBUZZ.md`) |
| **Server-trusted recipe consumption** | recipe-based raw-material deduction runs server-side on bill finalisation |
| **Owner + staff offline segmentation with a 5-terminal model** | vs Arow's owner-only offline mode; needs the recovery tests in §4 to be claimed |
| **Lower tracking surface** | Khide bundles Microsoft Clarity session recording, Facebook SDK, Google Analytics — not a stack we should match |

## 6. UX benchmark takeaways

From `competitor-uiux-analysis.md` (6-product review):

**Where we already align:** sidebar navigation with mobile collapse; live order grid with
colour-coded statuses; KPI grids *plus* charts (competitors mostly stop at metric cards);
card-based layout with a token-based radius/shadow scale; feature flags for configuration;
responsive breakpoints.

**Opportunities it identified:** plain-language AI insights panel (Restrofi), trust/stat
badges and client-logo grids (TMBill, Petpooja), integration-status toggles, mobile bottom
quick-action bar, counter cards on the platform dashboard, and a metric-badge pattern.

> Careful: several of those recommendations assume we *want* marketing surfaces
> (logos, social proof) — our product is a working POS, not a landing page. Treat them as
> options, not a backlog.

## 7. What we deliberately do not copy

- **Marketplace integrations.** Arow's Play listing advertises Zomato/Swiggy, but Arow's
  own terms say it does *not* provide integration automation and only forwards requests.
  Ours is an explicit scope decision — see `MARKETPLACE_INTEGRATIONS.md`.
- **Firebase-only business logic.** Business rules must stay server-side and portable.
- **Heavyweight session-recording/attribution SDKs.**
- **Vendor comparison pages as evidence** — Arow's own "we beat X" pages were not used for
  any claim about third parties.

## 8. Recommended follow-ups

Priority order from the two teardowns, deduplicated:

1. **Prove offline recovery** — concurrent owner/staff billing, process death,
   reconnection, retries, refunds, printer failure, then reconcile. Highest-value item;
   it converts our main narrative from architecture into evidence.
2. **Close the two most demonstrable dine-in gaps** — table/session model and
   portion+extra modifiers; both feed printing and inventory.
3. **Wire the GST return UI** we already have server-side (`PROBLEM.md` item 3).
4. **Benchmark onboarding and hardware** — time menu-photo import → first printed bill;
   test the printers merchants actually use; assess Ethernet demand.
5. **Small operational needs** — business-day rollover, delivery pricing, regional
   languages; then coupons/expense ledger against merchant interviews.
6. **Verify total cost of ownership** against Arow's real terms (₹299 headline vs
   ₹2,999+ store snapshot, 2.5% QR commission, 3,000-order fair-use cap on zero-commission
   plans) — never quote their headline price as annual cost.

## 9. Refreshing this research

1. Keep dated snapshot filenames (`<competitor>-<topic>-YYYY-MM-DD.md`) in `../evidence/rnd/`.
2. Never overwrite a snapshot; add a new one and update the table in §3.
3. Re-run §5 against the current tree before using any gap in a plan — Sep-2026 claims
   already decayed in three places (USB, print queue, GST).
4. Keep the evidence label on every claim, and keep runtime questions runtime-only.
