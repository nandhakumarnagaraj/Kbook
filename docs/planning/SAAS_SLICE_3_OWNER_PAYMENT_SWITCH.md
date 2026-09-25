# KhanaBook SaaS — slice 3: owner-controlled Easebuzz switch

The POS payment-settings switch previously saved only to the local profile and later reached the server through general sync. The server could still create payment links when the switch was off, and an onboarding webhook could turn the switch back on. That made the displayed choice unreliable.

Now the payment-settings save sends a changed Easebuzz switch to the owner-only payment-config PUT and waits for the server's confirmation before saving the local profile. If that online request fails, the local switch remains unchanged and the screen reports a save error. The server rejects new payment links when its profile flag is off, before calling Easebuzz. Both direct links and bill links use that check. General profile sync preserves the server flag for field-level and legacy full-record pushes, and sub-merchant onboarding no longer auto-enables it.

The readiness GET now reports `paymentLinkReady` only when the current owner agreement, active sub-merchant and owner-enabled server flag all hold. Turning the switch off does not cancel a link already issued or reverse a completed payment; it blocks **new** links.

Verification: focused server tests cover owner-off → config confirmation → blocked bill link without a gateway call. Android tests check that a failed server update does not save an unconfirmed off state locally. Source-level verification does not prove gateway settlement or live deployment.
