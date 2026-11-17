---
status: proposed
---

# One identity rule, and no silent account merging

A returning owner who signs in with Google is not recognised as the owner of their existing
restaurant. Phone signup deliberately writes `email = NULL`
(`AuthServiceImpl.signup`), and Google sign-in resolves identity by email only
(`AuthServiceImpl.googleLogin`), so the lookup chain misses and the fallback branch mints a new
`restaurant_id` next to the old one. The same human then holds two accounts in two tenants, and their
menu, bills, terminals and subscription stay behind in the first. Nothing in the schema can express
"these two rows are the same person", because `users` is simultaneously the person, the credential and
the single membership. We will define identity resolution in exactly one place, and we will not merge
accounts on the strength of a matching identifier.

## Decision

### 1. One resolver

Every entry point that answers "which account is this?" calls one function. Today there are four
hand-written lookup chains (`login`, `googleLogin`, `findUserByLoginId`, and
`UserProfileSyncService.findExistingUserByIdentity`) with different column sets, which is how the
Google path came to omit `phone_number` while the others included it. A new creation or
authentication path is not complete until it uses the resolver.

### 2. Verified identity is a fact, not an inference

An identity is only trusted once something proved control of it: an OTP for a phone number, a
Google-verified token for an email. `otp_requests` proves control for ten minutes and is then deleted
(`validateOtpOrThrow`), so the account never records that its phone was ever verified. Verification
must persist on the account, not only in a short-lived challenge row.

### 3. No silent linking

A Google sign-in never adopts an existing account because an email or a name matched. Adopting
requires proof of control of the identifier being added, requested with an explicit user action.
The brief for this work forbids guessing, and the risk is asymmetric: a wrong link hands one
business's data to another person, while a missing link merely asks the user to verify.

### 4. Business and billing stay with the business

`restaurant_id` remains the tenant key and subscription/payment ownership stays attached to it, not to
a person. This part of the current model is correct and is not being changed.

## Open items (owner decision, not resolved here)

- Which model: an additive `user_identities` table (smallest change, one unique rule per provider) or
  a full person / credential / membership split (solves multi-restaurant, touches the JWT contract).
- Whether one person may manage multiple restaurants in the near term.
- What the returning user is shown when Google cannot be linked automatically.
- Whether `email` keeps its current double duty as a phone-number column, which is why it has no
  case-insensitive uniqueness while every lookup is case-insensitive.

The investigation behind this record is in `ACCOUNT_SYSTEM_ARCHITECTURE_AUDIT.md`,
`ACCOUNT_CREATION_FLOW_ANALYSIS.md`, `ACCOUNT_DUPLICATION_ROOT_CAUSE.md` and
`ACCOUNT_IDENTITY_ARCHITECTURE.md`. No schema or code change is made by this record.
