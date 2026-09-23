# Refund status transport correction

The client now posts JSON to the configured dashboard base URL plus `/refund/v1/retrieve`, using `key`, `easebuzz_id`, `hash`, and the existing optional `refund_id` filter. Hash input is `key|easebuzz_id|salt`; the obsolete `easepayid` alias is removed. Blank payment IDs are rejected before network access.

Evidence: user-supplied ERA clarification and JSON cURL example on 2026-09-23. The official PHP SDK agrees on endpoint and hash but uses form encoding. JSON acceptance is therefore an ERA-supplied contract, not independently verified production behavior.

References:
- https://docs.easebuzz.in/docs/payment-gateway/oudxtp83258l9
- https://github.com/easebuzz/paywitheasebuzz-php-lib/blob/master/easebuzz-lib/refund_status.php

The local HTTP contract test verifies method, URL, JSON content type, exact fields and hash, and preservation of the refunds array. It does not contact production. Automatic reconciliation remains incomplete: responses must correlate payment, refund identifiers and per-attempt amount before accounting changes. Hold Refund must remain reserved for review and must not permit a replacement refund.

User reports successful sandbox testing and access to both environments. Production acceptance and settlement evidence remain pending.
