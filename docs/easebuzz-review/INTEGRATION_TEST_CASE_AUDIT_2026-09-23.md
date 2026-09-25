# Easebuzz integration test case audit — 2026-09-23

## Source and scope

Source workbook: `C:\Users\nandh\Desktop\integration_test_cases_sample.xlsx` (SHA-256 `87EB6082EC2090249A2028203AA0C132B95CFAA116743F973121DE9B4585977D`). It contains six tabs and **39 non-empty test cases**: Debit Card (10), Credit Card (7), UPI (8), EMI (5), Netbanking (5), Wallet (4). Every PASS/FAIL cell is blank. The sheet tests checkout presentation, payment outcomes, surcharge display, and one transaction webhook. It contains **no refund, status-retrieval, onboarding, split, settlement, or payout cases**.

This audit records implementation/test evidence separately from gateway acceptance. The production server was verified on commit `a165d697` (2026-09-20); the targeted local suite below was checked out and run against that exact deployed commit. No case is marked passed from a unit test or code inspection alone. The case procedures/data in the workbook are sandbox-oriented; do not run its test card/VPA scenarios with production credentials. The table intentionally omits raw payment test data.

## Findings before execution

- The integration creates an Easebuzz payment link on the server; the card/UPI/netbanking/wallet checkout itself is hosted at Easebuzz. Local Java tests cannot prove checkout UI visibility or bank/card rails.
- `EasebuzzPaymentService` supplies `show_payment_mode=CC,DC,NB,UPI,WALLET`. EMI is absent from this allow-list. **The workbook's EMI-visible / EMI-payment cases therefore have an implementation gap** unless the flag is deliberately disabled in production or Easebuzz enables EMI independently. Confirm the exact supported mode token with Easebuzz before changing the list.
- The payment link path can request UPI QR via `upi_qr`, but the normal hosted-checkout allow-list above does not include an explicit QR mode. The two QR scenarios need a distinct end-to-end check and confirmation of how QR is exposed in the currently enabled Easebuzz account.
- The surcharge cases depend on Easebuzz merchant configuration and the actual checkout quote. The workbook does not define expected rates/amounts or show a tested configuration. KhanaBook's own commission is a separate settlement/split concern and must not be represented as a customer surcharge without explicit product/legal approval.
- Server tests cover generic payment link/webhook/refund behavior (`EasebuzzIntegrationTest`, `EasebuzzWebhookTest`, `RefundServiceTest`), but they are mocked/local integration tests; they do not exercise issuer OTP, provider checkout pages, production merchant settings, or actual Easebuzz webhook delivery.
- The supplied go-live instructions are [Easebuzz Go-live Steps](https://docs.easebuzz.in/docs/payment-gateway/hjcp8ib4tgx0s-go-live-steps). The docs page currently renders a JavaScript shell in this review environment, so its full checklist could not be independently extracted. Easebuzz's workbook link was also supplied; the local workbook above was read directly.

## Case-by-case disposition

`Pending external` means there is no real provider checkout evidence attached to the workbook. Local evidence listed here does not turn the case into PASS.

| Workbook case | Scenario covered | Current code/local evidence | Acceptance status |
|---|---|---|---|
| Debit Card TC_01 | Debit option visible | Hosted link generated; checkout is provider-controlled | Pending external |
| Debit Card TC_02 | Visa success/failure/cancel | Generic link/webhook tests only; no issuer flow | Pending external |
| Debit Card TC_03 | Mastercard success/failure/cancel | Generic link/webhook tests only; no issuer flow | Pending external |
| Debit Card TC_04 | RuPay success/failure/cancel | Generic link/webhook tests only; no issuer flow | Pending external |
| Debit Card TC_05 | Surcharge OFF quote | Depends on Easebuzz merchant configuration; no captured quote | Pending configuration + external |
| Debit Card TC_06 | Surcharge ON quote | Depends on Easebuzz merchant configuration; no captured quote | Pending configuration + external |
| Debit Card TC_07 | Success webhook delivered | `EasebuzzWebhookTest` covers signed handler behavior locally | Pending live delivery |
| Debit Card TC_08 | Debit slab at amount 100 | No observed provider fee quote for this amount | Pending external |
| Debit Card TC_09 | Debit slab at amount 1,500 | No observed provider fee quote for this amount | Pending external |
| Debit Card TC_10 | Debit slab above 2,000 | No observed provider fee quote for this amount | Pending external |
| Credit Card TC_01 | Credit option visible | Hosted link generated; checkout is provider-controlled | Pending external |
| Credit Card TC_02 | Visa success/failure/cancel | Generic link/webhook tests only; no issuer flow | Pending external |
| Credit Card TC_03 | Mastercard success/failure/cancel | Generic link/webhook tests only; no issuer flow | Pending external |
| Credit Card TC_04 | RuPay success/failure/cancel | Generic link/webhook tests only; no issuer flow | Pending external |
| Credit Card TC_05 | Surcharge OFF quote | Depends on Easebuzz merchant configuration; no captured quote | Pending configuration + external |
| Credit Card TC_06 | Surcharge ON quote | Depends on Easebuzz merchant configuration; no captured quote | Pending configuration + external |
| Credit Card TC_07 | Success webhook delivered | `EasebuzzWebhookTest` covers signed handler behavior locally | Pending live delivery |
| UPI TC_01 | UPI option visible | `show_payment_mode` includes UPI | Pending external |
| UPI TC_02 | Collect success | Generic link/webhook tests only | Pending sandbox/provider |
| UPI TC_03 | Collect failure | Generic handler tests do not simulate provider collect UI | Pending sandbox/provider |
| UPI TC_04 | QR success | Optional `upi_qr` API branch exists; normal allow-list does not explicitly include QR | Pending mode/account confirmation + external |
| UPI TC_05 | QR failure | Same as UPI TC_04 | Pending mode/account confirmation + external |
| UPI TC_06 | Surcharge OFF quote | Depends on Easebuzz merchant configuration; no captured quote | Pending configuration + external |
| UPI TC_07 | Surcharge ON quote | Depends on Easebuzz merchant configuration; no captured quote | Pending configuration + external |
| UPI TC_08 | Success webhook delivered | `EasebuzzWebhookTest` covers signed handler behavior locally | Pending live delivery |
| EMI TC_01 | EMI option visible | EMI missing from `show_payment_mode=CC,DC,NB,UPI,WALLET` | **Code/config gap; blocked** |
| EMI TC_02 | Surcharge OFF quote | No EMI display path currently enabled | Blocked by EMI mode gap |
| EMI TC_03 | Surcharge ON quote | No EMI display path currently enabled | Blocked by EMI mode gap |
| EMI TC_04 | Below minimum amount rejected | No EMI display path currently enabled | Blocked by EMI mode gap |
| EMI TC_05 | Amount at/above threshold succeeds | No EMI display path currently enabled | Blocked by EMI mode gap |
| Netbanking S_01 | Netbanking option visible | `show_payment_mode` includes NB | Pending external |
| Netbanking S_02 | Surcharge OFF quote | Depends on Easebuzz merchant configuration; no captured quote | Pending configuration + external |
| Netbanking S_03 | Surcharge ON quote | Depends on Easebuzz merchant configuration; no captured quote | Pending configuration + external |
| Netbanking S_04 | Retail bank success | Hosted bank flow; no live/sandbox evidence attached | Pending external |
| Netbanking S_05 | Corporate bank maker-checker flow | Hosted bank flow; no local test can prove it | Pending account enablement + external |
| Wallet TC_01 | Wallet option visible | `show_payment_mode` includes WALLET | Pending external |
| Wallet TC_02 | Surcharge OFF quote | Depends on Easebuzz merchant configuration; no captured quote | Pending configuration + external |
| Wallet TC_03 | Surcharge ON quote | Depends on Easebuzz merchant configuration; no captured quote | Pending configuration + external |
| Wallet TC_04 | Wallet payment success | Hosted wallet/OTP flow; no provider evidence attached | Pending external |

The workbook totals 10 + 7 + 8 + 5 + 5 + 4 = **39 cases**. Test IDs are scoped by worksheet because IDs repeat across tabs.

## Production acceptance execution record

| Gate | Evidence needed | State |
|---|---|---|
| Easebuzz go-live requirements | Confirm current dashboard/account checklist and callback setup against the linked official guide | Pending; docs page content unavailable in this environment |
| Mode access | Easebuzz confirms enabled production modes, including EMI and QR if required | Pending |
| Fee/surcharge configuration | Production checkout quote for each relevant mode and amount bracket, reconciled to approved merchant commercials | Pending |
| Payment outcomes | Sandbox success/failure/cancel per applicable mode; then controlled production ₹1 success | Pending; no transaction was initiated during this audit |
| Webhook | Provider-delivered production webhook, verified signature, persisted paid state, redelivery/idempotency evidence | Pending |
| Refund | Separate refund test plan required: full + partial initiation, status lookup, webhook, delayed/held/failed status handling, and settlement reconciliation | Missing from supplied workbook; pending |
| Operational deployment/security | Verify current production commit, secret rotation, health, database migration state, and callback routing on VPS | Not proven by this workbook; separate release gate |

## Local verification performed

Command: `mvn -o -q -Dtest=EasebuzzIntegrationTest,EasebuzzWebhookTest,RefundServiceTest test` from `server/` in a detached worktree at deployed revision `a165d697` (2026-09-23; Java 25; test profile; in-memory H2).

| Test class | Passed | Failed | What it establishes |
|---|---:|---:|---|
| `EasebuzzIntegrationTest` | 14 | 0 | Simulated link/webhook, refund, onboarding, and split logic |
| `EasebuzzWebhookTest` | 10 | 0 | Local payment/refund-adjacent handler and signature cases (plus other webhook types) |
| `RefundServiceTest` | 6 | 0 | Local refund eligibility and amount rules |
| **Total** | **30** | **0** | Does not prove provider-hosted method availability, issuer flows, live webhook delivery, or settlement |

Production read-only checks on 2026-09-23: app and PostgreSQL containers running; `https://kbook.iadv.cloud/actuator/health` returned HTTP 200; deployed checkout `.env` mode was `600 root:root`; effective SSH policy reported `PasswordAuthentication no`, `PermitRootLogin without-password`, `PubkeyAuthentication yes`. The server checkout was clean. These checks establish current availability and access controls, not payment/refund acceptance.

The production checkout already runs the exact tested revision `a165d697`; no deployment was made. The local `main` checkout is 30 commits behind that deployed revision and has one local-only commit adding `errors.txt`, so deploying from that checkout would roll back production code and reintroduce a log artifact.

## Credential exposure stop-ship

GitHub currently labels `nandhakumarnagaraj/Kbook` **Public** ([repository](https://github.com/nandhakumarnagaraj/Kbook)). In the locally cached `origin/main` tree (`72ac35b7`, 2026-09-19), a value-redacting scan found **32 key/salt-shaped assignments** in `docs/easebuzz/KhanaBook-Easebuzz-API.postman_collection.json` and `docs/easebuzz/sub-merchant-apis.md`; 13 values did not resemble obvious `YOUR_*` / `TEST_*` placeholders (matched lengths 12–24). The production checkout later has a credential-removal commit, but that commit is not an ancestor of the cached public `main`. This is enough to treat the Easebuzz merchant key/salt material in the public history as potentially compromised; the scan deliberately did not print any values, and it cannot prove whether each value is still active or was sandbox-only.

**Required before production acceptance:** ask Easebuzz to revoke/rotate any merchant key/salt that appeared in public repository history (both environments if their distinction is unclear), update the root-only production `.env` using the newly issued production pair, restart only after the new values are verified, and purge the exposed blobs from Git history after preserving a secure backup. Making the repository private alone does not invalidate leaked credentials or remove clones/caches. This audit did not rotate credentials or rewrite public Git history.

The supplied test sheet is an acceptance template, not a report of executed tests: all PASS/FAIL cells are blank. No production-ready claim can be based on this workbook alone.

## References

- Easebuzz test cases workbook URL: https://easebuzzpub.s3.ap-south-1.amazonaws.com/integration_test_cases_sample.xlsx
- Local workbook: `C:\Users\nandh\Desktop\integration_test_cases_sample.xlsx`
- Easebuzz Go-live Steps: https://docs.easebuzz.in/docs/payment-gateway/hjcp8ib4tgx0s-go-live-steps
- Easebuzz Initiate Transaction APIs: https://docs.easebuzz.in/docs/payment-gateway/viyex0lwoda66-initiate-transaction-ap-is
- Easebuzz EMI: https://docs.easebuzz.in/docs/payment-gateway/h4qilrzjfr8st-emi
- Code under review: `server/src/main/java/com/khanabook/saas/feature/payments/service/EasebuzzPaymentService.java`, `EasebuzzApiClient.java`; local tests `EasebuzzIntegrationTest`, `EasebuzzWebhookTest`, `RefundServiceTest`.
