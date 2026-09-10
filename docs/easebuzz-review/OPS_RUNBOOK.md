# KhanaBook × Easebuzz — Operations Runbook

> **What this is:** every operational procedure in the sub-merchant lifecycle, from onboarding to closure.
> Money-flow model: **KhanaBook (parent) is the merchant of record; the restaurant is a sub-merchant twin.**
> Money always lands in the parent pool first and is split to each twin at settlement time.
> All request hashes follow `key | ... | salt`; responses are verified with the reverse hash.
>
> Supplemental: `ERA-QA-SESSION-2026-08-19.md` (platform contract), `diagrams/lifecycle-operations.mmd` (sequence).
> Math reference: ₹1000 bill, 5% commission, twin MDR 2% + 18% GST-on-MDR → **restaurant receives ₹926.40**, parent keeps ₹50 minus its own MDR on the commission.

---

## 0. Ground rules

| Rule | Why |
|---|---|
| **Parent is the financial anchor** — every refund, chargeback and clawback lands on the parent settlement, never the twin. | ERA Q1/Q2/Q3 |
| Sub-merchant **MDR slabs are set at creation and cannot be read or changed** after (`submerchant_deduction_percentage`). | ERA Q7 |
| **No negative-transfer / reversal API exists.** On-demand settlement is forward-only. Recovery = adjusting future payouts. | ERA Q3 |
| Restaurant payout is `payout_amount` on settlement rows — never `amount` (that is the gross/parent row). | ERA Q9 |
| Split labels are **unique per parent, multi-use, no update API** — bank change = rotate to `sm_<id>_v<N>` (new label). | ERA Q5 |
| `/post-split/v1/create` **idempotent by `merchant_request_id`**; a wrong/unregistered label fails the whole call (`SCEO2`). | ERA Q4b/Q9 |

---

## 1. Onboarding

### 1.1 Create the twin
1. Admin creates a sub-merchant draft for the restaurant (`POST /admin/sub-merchants`). One twin **per outlet**.
2. Ensure the KYC pack is collected before submit: **PAN (mandatory), bank proof, GST (if registered), FSSAI (restaurants)**, PDF/JPG/PNG ≤ 2 MB.
3. Submit → `/merchant/v1/submerchant/create` with `submerchant_deduction_percentage` slabs (UPI lt-limit, DC gt-₹2000, NB).
4. Expected: `submerchant_id` returned; local status `PENDING_KYC`.

### 1.2 OTP + KYC/CPV
5. Generate KYC access key → owner gets an **OTP auto-sent**; status must move `OTP_SENT` → `VERIFIED` before KYC.
6. Owner uploads documents to the hosted KYC portal (CPV is part of the same flow; no separate webhook).
7. Wait for `MERCHANT_KYC_APPROVAL` webhook. **This is the only signal** — there is no polling API.
   - `kyc_status: true` → status `ACTIVE`, **store the virtual account `account_number` + `ifsc`** (permanent).
   - `kyc_status: false` → `KYC_REJECTED` with reason; owner may resubmit.
8. KYC approved ⇒ ACTIVE automatically; splits and payouts are immediately enabled.

### 1.3 Split label
9. `POST /split/v1/create` with bank details → label `sm_<id>`.
10. Signatures: label unique per parent (duplicate = error, no silent no-op); multi-use; `payout_percentage` is **ignored** for per-transaction post-split.
11. Bank change later → create `sm_<id>_v1`, `_v2`, … and update the twin's split label (no update API).

**Checks:** twin ACTIVE, VA stored, label created, MDR slabs recorded in local snapshot (`V95` fields).

---

## 2. Payment

1. POS requests a payment link for a bill → `/payment/initiateLink` carries `sub_merchant_id`.
2. Customer pays → payment webhook arrives.
3. **Verify the reverse hash; non-200 = Easebuzz retries (5×, 30 min) then blocks.** A hash mismatch is a STOP condition.
4. Replay guard by `txnid` + `status` (webhook redelivery is expected); mark bill paid.
5. Fire `POST /post-split/v1/create` **within 24 h, target ≤ 5 min**:
   - idempotency: same `merchant_request_id` on retry, no duplicate split;
   - configuration = **exactly one entry, the restaurant's share** (`total − commission`). Commission stays at parent implicitly. Never a `kb_commission` label.
   - a label that isn't a registered split label fails the whole call — verify label exists before sending.
6. On success persist `commissionAmount` and `settledAt`. On failure: retry with backoff; if still failing, reconcile via §4.

---

## 3. Settlement (T+1)

| Checklist | Notes |
|---|---|
| One payout webhook per settlement cycle | carries `payout_id`, UTR (`bank_transaction_id`), `txnid`, `submerchant_id`, `label`, `status`, `peb_transaction_id` |
| Restaurant share | `payout_amount` = `total − commission − MDR − GST` (twin's own slab) |
| Parent share | commission stays at parent; **parent pays MDR on its commission at parent's slab** |
| Verify | `/settlements/v1/retrieve` by date range, filterable by `submerchant_id`, max 500 rows/page, 1-yr retention |

---

## 4. Reconciliation (daily, 06:00 IST)

`runDailyReconciliation` runs **two passes** for yesterday:

1. **Payment pass** — every Easebuzz row must map to a local bill (`txnid` + amount). Flags orphans and missing webhooks.
2. **Split pass** (`reconcileSplits`) — for each sub-merchant row (`submerchant_id` set or `label` contains `sm_`):
   - expected = `bill.total − commission` (fallback: commission rate);
   - actual = `payout_amount`, fallback `amount − peb_service_charge − peb_service_tax`;
   - compare `|actual| == |expected|`.

Manual replay: `GET /admin/sub-merchants/reconciliation/splits?date=YYYY-MM-DD`

| Alert flag | Meaning | Action |
|---|---|---|
| `RECONCILIATION ALERT` anything > 0 | issues found for a date | open §7 checklist below |
| `splitMismatch` | restaurant share ≠ total − commission | check MDR/tax application & commission config; correct future splits / adjust payout |
| `splitMissingSettlement` | bill marked settled locally, **no** sub-merchant row at Easebuzz | post-split lost — re-run post-split or intervene |
| `splitOrphan` | Easebuzz sub-merchant row with no local bill | cross-check txnid; likely a foreign/duplicate txn |
| no alert | all transactions and splits matched | no action |

---

## 5. Refunds & chargebacks

| Scenario | What happens | Action |
|---|---|---|
| **Refund** | Initiate via parent (`/refund/v1/initiate`), idempotent by `merchant_refund_id`. Value deducted from **parent** settlement. | Track via `REFUND_INITIATED` / `REFUND_STATUS_UPDATE` webhooks; ARN arrives on `refunded`. **No auto split-reversal** — if the twin already received its share, claw it back manually (§6). |
| **Chargeback** | Debited from **parent** next settlement cycle. Notified by `CASE_STATUS_UPDATE` (parent only) + email + DRS dashboard. | Evidence deadline 7–10 working days (↑ 1–3 for fraud). Reply with `merchant_txn` + `udf1`. Win ⇒ auto-credit to parent's next settlement. |
| **Debited-but-failed** | Reversal is handled by Easebuzz/bank (UPI 24–48 h, cards 5–7 business days). Webhook sets `failure`/`auto refunded`. | **Do not manual-refund.** Show "Payment Pending" and advise reversal window. |

---

## 6. Clawback (manual)

There is **no reversal API** and on-demand settlement is forward-only. To recover a twin's already-paid share:

1. Identify the amount to recover (`Refund`/`Chargeback` that hit the parent but were already split to the twin).
2. Adjust the twin's **future payouts** by the owed amount (back-end change against the next settlement).
3. Record the entry in the **manual clawback ledger**.
4. Confirm the restaurant sees the adjustment in its settlement history/dashboard.

Repeat each settlement cycle until the ledger is zero. Alerting: same daily reconciliation flags catches if a twin is paid out in full before its ledger debt is cleared.

---

## 7. Suspension → closure

1. **Suspend**: Update Sub-Merchant API (status `suspended`). Blocks new transactions; **already-queued splits still complete** (no stranded money).
2. **Drain**: let pending settlements pay out; run reconciliation until the date range shows zero pending.
3. **Closure**: request support deactivation (no public delete API). VA freezes, label goes inactive.
4. **After-closure**: refunds/chargebacks still land on the **parent** — keep the ledger and keep routing them manually.
5. **Ownership change (not closure)**: prefer updating the existing twin (ledger, VA, history continuity) over creating a fresh one.

---

## 8. Alert-response quick checklist (§4)

- [ ] Re-run both passes for the failing date via the admin endpoint.
- [ ] Classify each flagged txnid (mismatch / missing / orphan) using the table in §4.
- [ ] If post-split missing: verify label `sm_<id>` exists; re-send post-split with the original `merchant_request_id`.
- [ ] If mismatch: compare `payout_amount` vs `total − commission`; check MDR/GST on the sub-merchant row and the twin's fixed slab; correct config going forward.
- [ ] Update the clawback ledger for any amount already paid to a twin that must be recovered.
- [ ] Escalate to Easebuzz dashboard/support only for rows you cannot explain (orphans, blocked webhooks).

---

## 9. Key identifiers & endpoints

| Thing | Value / pattern |
|---|---|
| Sub-merchant | 1 per outlet; `submerchant_id` from create response |
| Split label | `sm_<id>`, `sm_<id>_v1`, … (versioned locally, V95) |
| Virtual account | permanent, from KYC webhook (`account_number`, `ifsc`) |
| Reconciliation | payment pass + split pass; `GET /admin/sub-merchants/reconciliation/splits?date=` |
| Post-split | `/post-split/v1/create`, idempotent by `merchant_request_id` |
| Settlement report | `/settlements/v1/retrieve`, filter by `submerchant_id`, 500/page |
| Suspension | Update Sub-Merchant API |