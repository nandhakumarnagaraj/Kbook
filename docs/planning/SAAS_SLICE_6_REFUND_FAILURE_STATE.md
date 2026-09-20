# KhanaBook SaaS — slice 6: refund failure state

Refund responses now use a mutable server-owned response envelope and always expose `refundStatus=failed`, `totalRefunded`, and `remainingRefundable` when Easebuzz rejects or does not return a response. A rejected refund leaves the bill's refund amount and payment status unchanged. Successful gateway-confirmed refunds retain the existing accounting and notification behavior.

Verification: focused `RefundServiceTest` coverage proves a rejected gateway response cannot change accounting state and remains safe when the gateway response is immutable.
