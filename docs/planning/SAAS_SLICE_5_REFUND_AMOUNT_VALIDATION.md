# KhanaBook SaaS — slice 5: refund amount validation

Refund requests now reject amounts with more than two decimal places before calling Easebuzz. This keeps the refund amount accepted by the business API aligned with currency precision and ensures an invalid request cannot change the bill or create a gateway refund attempt.

Verification: the focused `RefundServiceTest` proves an over-precise amount raises a business error, does not call the gateway, and does not save the bill.
