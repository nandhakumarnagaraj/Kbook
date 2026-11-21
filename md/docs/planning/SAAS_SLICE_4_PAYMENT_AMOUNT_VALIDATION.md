# KhanaBook SaaS — slice 4: payment amount validation

Payment-link requests now validate the amount before onboarding lookup or any Easebuzz call. The server accepts only positive values with at most two decimal places and returns `INVALID_PAYMENT_AMOUNT` for missing, malformed, zero, negative, or over-precise values. Bill payment links already format their bill total to two decimals, so this protects direct links and the shared bill-link path consistently.

Verification: the focused Easebuzz integration test proves an invalid amount is rejected and the gateway client is not called. This is source and test verification; live gateway validation still requires sandbox credentials.
