# KhanaBook SaaS — slice 14: verified payment ID for refunds

Refund initiation now requires the original Easebuzz payment ID. The server no longer substitutes the merchant `txnid` when that ID is missing. If it must recover the ID from the transaction-status API, the response must report a successful transaction with the bill's exact transaction ID and positive total amount. An uncorrelated or incomplete response returns `PAYMENT_ID_UNAVAILABLE` without calling the refund API or changing the bill.

Verification: `EasebuzzIntegrationTest` covers a matched transaction-status response and a mismatched transaction that cannot initiate a refund. The actual gateway response format still needs sandbox confirmation. Legacy payments without a recorded payment ID require trusted reconciliation before a refund can be sent.
