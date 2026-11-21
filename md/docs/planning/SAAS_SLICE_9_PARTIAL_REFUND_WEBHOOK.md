# KhanaBook SaaS — slice 9: partial refund webhook state

Easebuzz's `refunded` webhook reports completion of one refund attempt. A partial attempt must not mark the whole bill fully refunded. The webhook now compares the bill's cumulative refund amount with the bill total: a smaller amount keeps `paymentStatus=partially_refunded`; equality marks it `refunded`. Missing, invalid, or impossible amounts put the gateway state in `refund_review_required` and avoid a completion notification.

The webhook amount remains an amount for one attempt. When an initiated refund has already stored a cumulative amount, the webhook does not replace that total.

Verification: `EasebuzzWebhookTest` covers partial, full, and malformed amount callbacks with a valid refund hash. Gateway delivery and settlement still require sandbox verification.
