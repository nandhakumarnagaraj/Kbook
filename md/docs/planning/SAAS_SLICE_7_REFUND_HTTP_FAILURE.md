# KhanaBook SaaS — slice 7: refund HTTP failure contract

The business refund endpoint now returns HTTP 400 when Easebuzz rejects a refund, matching the primary payment refund endpoint. Successful refunds continue to return HTTP 200. The response body retains the stable refund failure fields from slice 6, so clients can show the reason while using the HTTP status for transport-level handling.

Verification: `RefundControllerTest` proves a rejected refund is returned as HTTP 400 with its failure state.
