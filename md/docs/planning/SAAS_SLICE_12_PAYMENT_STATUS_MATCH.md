# KhanaBook SaaS — slice 12: transaction-status bill matching

The explicit payment-status refresh path now marks a bill paid only when Easebuzz reports `success` for the bill's stored transaction ID and the exact positive bill total. Missing, malformed, or mismatched transaction details return `PAYMENT_VERIFICATION_MISMATCH` and leave the bill pending. This closes a separate path from the payment webhook addressed in slice 11.

Verification: `EasebuzzIntegrationTest` covers matching status, a different transaction ID, and a different amount. The actual gateway status-response schema still needs a sandbox check; if its fields differ, the bill stays pending for reconciliation rather than being credited from an uncorrelated response.
