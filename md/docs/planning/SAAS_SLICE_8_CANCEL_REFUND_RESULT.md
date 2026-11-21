# KhanaBook SaaS — slice 8: truthful cancel and refund result

An immediate cancel-and-refund request previously returned `refundApplied=true` even when Easebuzz rejected the refund. It now reports `refundApplied=false` and `refundStatus=failed` for rejection, while the nested refund response retains the gateway error. A successful initiation reports `refundStatus=initiated`; this is not confirmation that money has reached the customer.

The order cancellation remains a separate action. If a refund fails, the cancelled order still needs an operator to retry or reconcile the refund. Delayed refunds use an in-memory scheduler and are reviewed separately.

Verification: `RefundServiceTest` covers gateway rejection and confirms the bill's refund amount and payment status remain unchanged.
