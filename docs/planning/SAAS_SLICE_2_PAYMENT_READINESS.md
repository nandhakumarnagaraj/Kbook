# KhanaBook SaaS — slice 2: truthful payment readiness

The Android payment-settings screen previously showed a locally saved Easebuzz switch. It did not read the existing server payment configuration, so an owner could see the switch on while the current agreement or sub-merchant activation was missing. The server's configuration GET also wrote to the profile by re-enabling Easebuzz, and the master-sync GET repeated that write.

This slice adds a read-only `paymentLinkReady` result to the restaurant payment-config GET, based on the current signed agreement and active Easebuzz sub-merchant ID. Android loads that result when the payment-settings screen opens and shows the missing prerequisite or an unavailable-network message. An unexpected server lookup failure is no longer reported as a missing sub-merchant. The onboarding and agreement actions remain visible even when the local Easebuzz switch is off, so an owner can complete setup. The GET and master-sync pull no longer auto-enable the payment method as a side effect of reading.

`paymentLinkReady` describes the prerequisites checked before payment-link creation; it does not assert that an Easebuzz sandbox or live transaction has settled. The locally saved switch still controls whether the POS offers Easebuzz as a payment option. When the switch has been changed but not synced, the screen says the server has not confirmed it yet.

Verification: server readiness test checks that reading an active account reports the prerequisites without changing a disabled profile; Android debug compilation checks the settings integration. This slice does not deploy or perform a live gateway transaction.
