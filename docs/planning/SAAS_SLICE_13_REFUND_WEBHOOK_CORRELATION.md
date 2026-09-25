# KhanaBook SaaS — slice 13: refund callback payment matching

The refund callback hash is calculated from the Easebuzz payment ID. The callback also carries a merchant transaction ID used to locate a bill, but that transaction ID is not included in this hash. The server now compares the authenticated Easebuzz payment ID with the ID recorded for the bill's original payment before applying any refund state change. A valid hash copied to a different bill's transaction therefore cannot change that bill.

If an older payment has no recorded Easebuzz payment ID, its refund callback is left unchanged for manual reconciliation. This is intentionally fail-closed until the payment can be matched through a trusted gateway status lookup. The endpoint still acknowledges the callback; an operational reconciliation queue remains to be built.

Verification: `EasebuzzWebhookTest` covers matching IDs, a valid hash for a different payment, and a missing original payment ID.
