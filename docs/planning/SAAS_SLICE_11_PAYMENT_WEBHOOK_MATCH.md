# KhanaBook SaaS — slice 11: payment webhook bill matching

A signed payment webhook now changes a bill only when its `txnid` matches the transaction ID that the bill stored when KhanaBook issued its link. If supplied, `udf1` must match that bill ID and `udf2` must match its restaurant ID. A success callback must also report the exact positive bill total. A valid payment for a direct link or another bill can no longer use `udf1` alone to mark a different bill paid.

Callbacks that do not match a bill remain visible in server logs for reconciliation. This fail-closed behavior may require manual review of legacy links whose transaction IDs were never stored on their bills.

Verification: `EasebuzzWebhookTest` covers valid payment, unknown transaction, mismatched bill ID, restaurant ID, and amount with valid webhook hashes.
