---
status: proposed
---

# KhanaBook owns its view of Easebuzz state

Production onboarding stalled because "can this outlet collect?" was answered in three
places with three different status sets, sandbox passed only because the gate was skipped
by sniffing the payment URL for the string `test`, and every money-moving call chose its
idempotency key in memory. We will treat gateway state as **mirrored data with a named
source**, define the collect rule once, and persist an idempotency key before dispatch.

## Decision

### 1. One predicate

A payment may be collected only when all three hold:

- the outlet has a non-blank `sub_merchant_id`,
- its status is exactly `ACTIVE`,
- `easebuzz_enabled` is true for the outlet.

This is defined in one function and every consumer calls it — link creation, POS payment,
config read, UI badges. No second interpretation of the status column.

### 2. Status taxonomy

Only `ACTIVE` collects. Everything else is pre-live, failed, or expired, and exists to tell
us *where* onboarding stopped — not to grant permissions.

| Status | Meaning | Collects? |
|---|---|---|
| `DRAFT` | saved locally, never submitted | no |
| `PENDING_KYC` | created at Easebuzz, id issued, awaiting KYC | no |
| `CPV_PENDING` | KYC submitted, CPV under review | no |
| `KYC_SUBMITTED` | documents submitted, approval pending | no |
| `ACTIVE` | approved; virtual account issued | **yes** |
| `REJECTED` | CPV negative; owner may correct and resubmit | no |
| `FAILED` | submission failed | no |
| `KYC_EXPIRED` | approval lapsed; re-KYC required | no |

`docs/integrations/easebuzz/EASEBUZZ.md` calls the rejection state `KYC_REJECTED`; the code
writes `REJECTED`. One of them is wrong and must be fixed before any consumer filters on it.

### 3. State ownership

Easebuzz is authoritative for `submerchant_id`, KYC/CPV status, virtual account, payment and
refund state, and settlements. We mirror each with `status`, `source`
(`webhook` | `reconciliation` | `poll`) and `as_of`. A request outcome never sets a state:
a 200 on create proves we asked, not that anything was approved, and a refund create proves
it is *queued*, not refunded.

### 4. Idempotency contract

Every call that can create an object or move money persists its key **before** dispatch and
reuses it on every retry. A retry must never mint a new key.

| Operation | Key |
|---|---|
| EasyCollect link / payment | `merchant_txn` |
| Refund create | `merchant_refund_id` |
| On-demand settlement | `merchant_batch_id` |
| Post-split create | `merchant_request_id` |
| Sub-merchant create/update | no gateway key — email+phone act as identity, so duplicates are checked locally first |

Corollary: the write of the key and the business row commit together, and the HTTP call
happens outside that transaction. A crash after dispatch leaves an orphan key to sweep, not
a duplicate object at the gateway.

### 5. Environment is a capability, not a hostname

`test` vs `production` comes from explicit configuration and is carried in a typed value.
Nothing infers it by searching a URL for `test`. Sandbox may relax the `ACTIVE` requirement,
but only through that capability, and the relaxation must be visible in the response and the
log — the bypass is what hid the production gate.

## Open questions (Easebuzz, in writing)

1. Does create send the sub-merchant their login credentials, and with which password?
2. Can `sub_merchant_email` / phone be changed after creation, and does KYC/login identity follow?
3. Is `kyc_details.kyc_dashboard_url` in the create response equivalent to `generate_kyc_access_key`?
4. Is on-demand settlement enabled for our live merchants (or should that path be deleted)?
5. Will split ever be enabled (merchant has `split: false`)?

Answers 1–2 decide whether email verification is mandatory; 3 decides whether a round trip
disappears; 4–5 decide whether two code paths are work or deletion.

## Consequences

- One place to change when Easebuzz redefines a status; the UI and every guard follow.
- Persisted keys make retries safe, at the cost of a sweep for orphans.
- Three writers (mirror, webhook, reconciliation) require `source` and `as_of`, otherwise
  "who wrote this last?" becomes unanswerable.
- The sandbox/production divergence disappears, so a sandbox success no longer implies a
  production path works — which is the point.

## Next, in dependency order

| Step | Acceptance |
|---|---|
| Typed environment capability | sandbox cannot collect for a non-`ACTIVE` sub-merchant; the relaxation is visible |
| Idempotency store (refunds, settlements first) | kill after dispatch + retry ⇒ exactly one gateway object |
| Single collect predicate | every consumer asks the same function; no status literals outside it |
| Onboarding verification gates | a bad email/legal name cannot produce an Easebuzz profile |
| Batch-result recording | partial on-demand rejections are persisted per transaction |
| Reconciliation on by default | drift raises an alert instead of being noticed by a merchant |
