# Arow: deeper first-party product and commercial evidence

Research date: 2026-09-08. Identity: `com.arowapp.arowapp`, published as Arow Labs. This note supplements the [initial APK/source comparison](arow-competitor-analysis-2026-09-08.md). It covers publisher-owned public pages and the publisher's exact Google Play listing. No account creation, competitor API probing, installation, payment or live transaction was performed.

Evidence labels: **Published** means the publisher states it; **Historical** means an older indexed page, not a current quote; **Unknown** means the reviewed public sources did not settle it. None of these labels means successful runtime verification.

## Commercial terms: what a merchant can actually compare

| Question | Evidence and limit |
|---|---|
| Introductory price | **Published:** homepage advertises a ₹299 limited-time offer. It does not expose the offer period, renewal rate, included devices or feature eligibility in retrieved content. [Homepage](https://www.arowapp.com/) |
| Free trial | **Published:** terms mention a free trial but specify no duration. A free download, a free trial and the ₹299 offer must not be treated as the same entitlement. [Terms §2](https://www.arowapp.com/terms) |
| Subscription interval | **Published:** yearly billing; monthly purchase is mentioned only conditionally. Plans are described as non-refundable. [Terms §§3, 9](https://www.arowapp.com/terms) |
| Historical annual offering | **Historical:** store snapshot advertised software from ₹2,999 and a one-year plan including features, updates and support, requiring renewal for premium access. Snapshot was approximately five months old; direct retrieval returned 404. [Indexed store](https://store.arowapp.com/) |
| Online-paid QR orders | **Published:** standard commission is 2.5%. Custom-priced zero-commission plans have a 3,000-orders/month allowance, then the standard rate applies beyond that count. [Terms §9](https://www.arowapp.com/terms) |
| Device, outlet and staff caps | **Unknown:** the reviewed pages provide no numeric caps or per-additional-device/outlet tariff. Multi-device advertising does not establish unlimited concurrency. |
| Setup charge | **Published:** Arow advertises zero setup fees on its comparison landing page. Its statements about other vendors were not used as evidence. [Arow positioning page](https://www.arowapp.com/alternatives/posist) |

Cost illustration, not a quote: applying the published standard QR rate to ₹1,00,000 of qualifying online-paid orders yields ₹2,500 commission. Subscription, tax, gateway charges, refunds and other charges are not resolved by that calculation. It is not valid to apply the rate to all POS sales or assume a static payment QR has the same charging rules. [Rate source](https://www.arowapp.com/terms)

The current total annual cost cannot be established from the offer headline. A useful quote must explicitly state renewal price, outlets, concurrent devices, included roles, kitchen displays, support and online-order charges.

## Offline capability: stronger publisher detail, still no staff guarantee

An Arow article dated May 10, 2024 describes local SQLite order writes, a background upload queue, Bluetooth/USB KOT printing without internet, and upload after connectivity returns. It also claims the cashier experiences unchanged operation. These are architecture and behavior claims, not an inspected implementation or endurance test. [Official offline article](https://www.arowapp.com/blog/offline-first-tech-saves-restaurants)

The article provides no owner-versus-staff eligibility rule, concurrent-device conflict policy, maximum disconnected period, or procedure for a lost device before synchronization. Its discussion of conflict handling does not document an algorithm. Therefore it does not establish that several disconnected staff terminals can operate independently and later reconcile correctly. [Official offline article](https://www.arowapp.com/blog/offline-first-tech-saves-restaurants)

The earlier local APK analysis found an owner-only offline setting and a subscription-verification message. Those narrower packaged statements should qualify broad marketing language; neither source alone proves which branch the current runtime executes. See the [local evidence and caveats](arow-competitor-analysis-2026-09-08.md#offline-operation-the-most-important-distinction).

## The connected restaurant workflow being advertised

The exact Play listing describes the following connected capabilities. They are feature claims, not demonstrations of edge-case correctness. [Publisher's listing](https://play.google.com/store/apps/details?id=com.arowapp.arowapp)

| Workflow stage | Published capability | Unresolved detail |
|---|---|---|
| Menu setup | Categories, catalog, kitchen station, item availability, photos, tax profiles, variants, modifiers and add-ons | Required/optional groups, minimum/maximum choices, modifier stock consumption and price precedence |
| Dining room | Named areas; free/occupied/billed table status; seating, table orders and billing | Table transfer/merge, seats/covers, course sequencing and concurrent edits |
| Kitchen | Automatic station-routed KOT and KDS; food notes; 58/80 mm Bluetooth printing | Display count, ticket acknowledgements, amendments, cancellations, retries and offline-display behavior |
| Staff | Captain ordering plus permissions for billing, discounts, cancellation, reports and menu work | Custom roles, approval escalation and server-side enforcement |
| Customer | History, pay-later credit, coupons and loyalty | Points accrual/redemption, expiry, stacking and cross-outlet balances |
| Ownership | Multiple outlets and devices | Shared menus, consolidated accounting and transfer workflows |

The historical store FAQ separately names cashier/captain/admin roles and describes captains printing directly to a kitchen printer while bills are produced at the counter. It promises setup, printer assistance and basic training, but not a measurable response-time commitment. [Historical store FAQ](https://store.arowapp.com/)

This positions kitchen and floor coordination as a central part of the product, not just receipt creation. It does not justify counting every unresolved detail above as implemented.

## Important feature distinctions

### Customer credit, promotions and merchant charges are different systems

The public feature description separates pay-later customer credit from coupons and loyalty, but does not specify whether loyalty is a points engine, a discount mechanism or a marketing label. A dedicated points-ledger implementation remains unverified. [Customer section of listing](https://play.google.com/store/apps/details?id=com.arowapp.arowapp)

Public sources reviewed do not document how an Arow merchant wallet is funded, debited, refunded or reconciled. Consequently, do not label that wallet a customer stored-value wallet, a loyalty balance or a settlement account. The initial APK report's wallet strings are a separate evidence source, not a published contractual explanation.

### Expenses are broader than ingredient purchases

The homepage advertises expense entry for operating costs such as salary advances and packaging, categorized expense tracking, and sales/expense/profit views. That is a broader owner workflow than recording inventory purchases alone. Double-entry accounting, bank reconciliation and statutory-accounting completeness are not established. [Homepage](https://www.arowapp.com/)

### QR ordering is different from a bill's payment QR

The homepage describes customer-led menu browsing and order placement from a table QR. It also advertises a separate anonymous-feedback QR workflow. Ordering, payment collection and feedback should be compared separately. [Homepage](https://www.arowapp.com/)

### Hardware support needs exact combinations

The official printing article claims an ESC/POS bridge for commodity Bluetooth printers. It discusses 58 mm and 80 mm paper widths. This is useful compatibility positioning, not proof every printer, Android version, USB adapter or regional script works. [Official printer article](https://www.arowapp.com/blog/top-5-thermal-printers)

## Contradictions and qualifications that affect the comparison

| Subject | What needs qualification |
|---|---|
| Aggregator automation | Arow's landing page pitches aggregator integration, while its terms say integration requests are forwarded for platform approval and that Arow does not provide automation. Do not score automated ingestion as demonstrated. [Marketing](https://www.arowapp.com/alternatives/urbanpiper), [terms §8](https://www.arowapp.com/terms) |
| Regional languages | Play lists eleven: English, Hindi, Telugu, Tamil, Kannada, Malayalam, Marathi, Gujarati, Odia, Bengali and Punjabi. It does not specify translated screen coverage, receipt output or offline text-entry support. Compare against the APK's separately declared locale set, not an assumed complete translation count. [Listing](https://play.google.com/store/apps/details?id=com.arowapp.arowapp) |
| Adoption | The homepage contains both a 1,000-plus restaurant claim and a separate 100-restaurant counter. Treat them as unverified marketing figures, not an installed-paying-customer estimate. [Homepage](https://www.arowapp.com/) |
| Documentation availability | Homepage links labelled pricing, documentation and changelog resolved to the homepage in retrieved navigation; no complete plan matrix or public release history was verified. [Homepage navigation](https://www.arowapp.com/) |
| Hardware article freshness | Its page title refers to 2025, while the article heading and displayed date refer to 2024. Do not use it as a current tested-hardware certification list. [Printer article](https://www.arowapp.com/blog/top-5-thermal-printers) |

## Questions that require a demonstration or current written quote

1. Can an owner and two staff phones create independent orders while all three are disconnected? What happens to invoice numbering, table state, payments and stock after reconnection?
2. What stops working after the trial, subscription expiration, extended offline period or insufficient merchant-wallet balance? Can a merchant still retrieve and export historical records?
3. Does a kitchen cancellation or quantity amendment update every affected printer/display without duplicate preparation? What happens when the printer fails after an order is saved?
4. Can one modifier be mandatory, another allow multiple choices, and each have separate prices and ingredient consumption? Which price wins across variant, tier, coupon and tax rules?
5. Are customer credit repayments distinct from sales revenue? Can partial repayments, refunds and loyalty reversals be reconciled in reports?
6. What is the actual per-outlet/device/display limit, and can staff move between outlets without seeing unauthorized data?
7. Can a merchant export items, customers, unsettled credit, bills, payments and stock history in usable formats before leaving?
8. Which screens and receipt fields are translated, and do Indian-script receipts remain legible on the target 58/80 mm printers?

These are comparison tests, not accusations of defects or missing features.

## Retrieval limits

The direct store URL returned 404; its historical search snapshot was kept clearly separate. `/kds` and `/privacy` could not be retrieved through the browser tool, and `/blog` and `/sitemap.xml` were unavailable through that tool. These failures do not prove the corresponding services or policies are absent. Public KDS pairing was not exercised, no help account was accessed and no integration was activated.

The highest-value new public evidence is the offline article's explicit local-write/upload-queue claim and the historical captain-to-kitchen workflow description. The largest remaining uncertainty is operational behavior and entitlement boundaries, not a shortage of advertised feature names.
