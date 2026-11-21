# Refund status transport correction

The client posts JSON to the configured dashboard base URL plus `/refund/v1/retrieve`, using `key`, `easebuzz_id`, `hash`, and the optional `merchant_refund_id` filter. Hash input is `key|easebuzz_id|salt`; the obsolete `easepayid` alias is removed. Blank payment IDs are rejected before network access.

Evidence: user-supplied ERA clarification and JSON cURL example on 2026-09-23, plus ERA + official Java kit (`paywitheasebuzz-java-lib`, `web/refund_status_response.jsp` + `web/config.jsp`) follow-up confirmation on 2026-09-24: the optional lookup parameter is `merchant_refund_id` (the gateway may also accept `refund_id`, but `merchant_refund_id` is the documented field); both JSON and form-urlencoded bodies are accepted (form-urlencoded is the documented primary; success flag is `true`/`false`; refunds items expose `refund_id` + `refund_status`). The official PHP/Java SDKs agree on endpoint and hash but use form encoding, so JSON acceptance is an ERA-confirmed contract — final production acceptance still pending.

References:
- https://docs.easebuzz.in/docs/payment-gateway/oudxtp83258l9
- https://github.com/easebuzz/paywitheasebuzz-php-lib/blob/master/easebuzz-lib/refund_status.php

The local HTTP contract test verifies method, URL, JSON content type, exact fields and hash, and preservation of the refunds array. It does not contact production. Automatic reconciliation remains incomplete: responses must correlate payment, refund identifiers and per-attempt amount before accounting changes. Hold Refund must remain reserved for review and must not permit a replacement refund.

User reports successful sandbox testing and access to both environments. Production acceptance and settlement evidence remain pending.
