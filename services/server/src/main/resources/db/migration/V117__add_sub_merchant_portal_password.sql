-- The Easebuzz sub-merchant dashboard password.
--
-- Easebuzz requires a password at sub-merchant creation, and until now KhanaBook generated a
-- random one, sent it, and discarded it: an approved merchant had a dashboard login that nobody
-- on our side could recover, so the only recovery path was an Easebuzz support ticket.
--
-- Stored AES-GCM encrypted (see PortalCredentialCipher), never in plaintext. NULL means no copy
-- was retained — either the row predates this column, or the server had no encryption key
-- configured at creation time.
ALTER TABLE easebuzz_sub_merchant
    ADD COLUMN portal_password TEXT;

COMMENT ON COLUMN easebuzz_sub_merchant.portal_password IS
    'AES-GCM ciphertext of the Easebuzz dashboard password; NULL when not retained';
