# Requirement Implementation Gaps

Open items recorded while making the owner's login identity (`loginId` / mobile / email)
the single source of truth for restaurant config. Each entry states what is not done,
why, and where the evidence lives.

---

## 1. Config identity could diverge from login identity — FIXED

`PUT /business/profile` used to write `restaurant_profiles.whatsappNumber/email` only.
The owner `User` row (what login actually resolves) was untouched, so the shop settings
page could show a number/email that could not sign in.

Fixed by `BusinessWriteService.assertIdentityUnchanged` (`BusinessWriteService.java`,
start of `updateProfile`): any change to those two fields must go through OTP, otherwise
`409 CONFLICT` with an actionable message. Web-admin now sends the owner-identity values
in the profile payload, which heals existing drift without an OTP prompt.

Test: `services/server/src/test/java/com/khanabook/saas/feature/business/service/BusinessWriteServiceProfileIdentityTest.java`.

## 2. Staff token could retarget the owner's login identity — FIXED

The four `/sync/config/users/update-mobile*` / `update-email*` endpoints sat under
`/sync/**`, which is `hasAnyRole("OWNER","SHOP_STAFF")`. They resolve the target via
`getPrimaryUser(tenantId)` (local id 1 / first OWNER), so any staff token could change
the owner's sign-in identity.

Fixed twice over: explicit `OWNER` matchers in `SecurityConfig` for those four paths,
and `@RequireRole(UserRole.OWNER)` on the controller methods
(`UserController.java`). `RequireRoleAspect` fails closed on a null role (terminal staff
tokens included).

## 3. No WhatsApp notice when the login id changes — OPEN

`confirmMobileNumberUpdate` / `confirmEmailUpdate` send an email notice to the old
address via `EmailNotificationService.sendLoginIdChangedNotice`. There is no WhatsApp
counterpart because it needs an approved Meta template for `login_id_changed`; adding
one requires template approval outside this change.

Impact: an owner who changes identity without reading email gets no channel-level
confirmation on the old number. Mitigation today: OTP is delivered to the new
identifier, and the old identifier stops matching at the next login.

## 4. No OTP rate limit on `/business/profile/identity/*` — OPEN

`BusinessAdminController` issues identity OTPs without `OtpRateLimiter`. The legacy
`/sync/config/users/update-*` endpoints do rate-limit. Not added here because the
controller uses Lombok `@RequiredArgsConstructor`, which does not copy `@Qualifier`
unless `lombok.copyableAnnotations` is configured, so wiring the limiter cleanly needs
a config change or an explicit constructor.

Exposure is limited: the endpoints are `OWNER`-only and each request targets the
owner's own identifier. Still a hole worth closing before public launch.

## 5. Two web-admin specs fail on this machine for environmental reasons — OPEN

Neither spec file is touched by the identity change, and both failures reproduce on
clean `HEAD` (`2b999f7e`) in a detached worktree, so this is pre-existing:

- `src/app/pages/micro-interactions.spec.ts:333` — asserts every enabled control has a
  non-zero `transition-duration`. Dashboard buttons (`dash-pill-btn`,
  `dash-primary-cta-btn`, `dash-user-profile`, …) compute `0s` because their transition
  is declared with `var(--kb-motion-fast)`, which is defined nowhere in
  `src/styles.css` or `src/styles/bento.css`. The declaration is invalid at
  computed-value time, so the initial `0s` wins. Real (small) CSS defect: those buttons
  currently snap instead of easing.
- `src/app/shared/refresh-button.component.spec.ts:67` — requires
  `matchMedia('(prefers-reduced-motion: reduce)').matches === true`. Headless Chrome
  defaults to `false`; the Karma launcher in `karma.conf.js` does not pass
  `--force-prefers-reduced-motion`.

Current suite: 350/352 pass, with exactly these two failures. Everything else,
including the new identity specs, is green.
