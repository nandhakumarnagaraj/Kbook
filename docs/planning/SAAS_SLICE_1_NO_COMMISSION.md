# KhanaBook SaaS — slice 1: zero transaction commission

KhanaBook serves restaurant owners through an offline-first Android POS and a multi-tenant server. Customers can pay restaurant bills through Easebuzz. The owner has clarified the product policy: **KhanaBook takes no commission from these transactions**. Easebuzz's own processing charges and settlement deductions are separate; they remain subject to the provider's approved commercial terms and the restaurant agreement.

The old code stored an admin-configurable `commissionRate` and, after a successful customer payment, split the amount between the restaurant and KhanaBook. This was inconsistent with the clarified policy. It also made the restaurant share depend on the rate at split time, after a payment link had already been issued.

This slice makes the server's future transaction path zero-commission: sub-merchant creation and updates reject a nonzero rate; the admin commission endpoints cannot set one; a database migration normalizes existing sub-merchant rates to zero and constrains future writes; post-payment splitting assigns the full bill gross amount to the restaurant's split label and records zero Khanabook commission. Reconciliation expects the full gross amount for new splits while retaining historical recorded commission amounts for audit. The owner agreement explicitly states zero KhanaBook transaction commission and has a new version, so owners accept the corrected wording.

Path: Android bill → server payment link → Easebuzz customer payment → webhook → `PostSplitService` → restaurant split. Gateway fees may still be charged or deducted under Easebuzz's contract; “full gross split” describes KhanaBook's split instruction, not a promise that the bank deposit equals the gross bill.

## Verification

- Focused server tests cover a payment link with a legacy positive rate, rejection of a new positive rate, and the full-gross restaurant split.
- Historical bill amounts are not rewritten by the migration.
- No live Easebuzz transaction or production deployment has been performed for this slice.

## Remaining rollout checks

- Confirm the full-gross single-label split and settlement math with Easebuzz in sandbox and then with a controlled live transaction. A source-level test cannot prove Easebuzz's account-specific split behavior or fee deductions.
- Confirm the final Easebuzz rate schedule and legal form of the owner agreement before enabling this in production. The changed agreement version requires existing owners to accept it again.
- The older business-model document describes a proposed platform-commission model; its figures are superseded by this zero-commission policy.

Stop after this slice and review its behavior before selecting slice 2.
