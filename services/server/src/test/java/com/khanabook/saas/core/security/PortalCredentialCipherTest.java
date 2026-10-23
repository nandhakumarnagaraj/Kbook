package com.khanabook.saas.core.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stored credential is the merchant's Easebuzz dashboard password: it has to survive a
 * round trip, must never be written in the clear, and must fail closed rather than pretend.
 */
class PortalCredentialCipherTest {

    private PortalCredentialCipher cipher(String secret) {
        return new PortalCredentialCipher(secret, "", "");
    }

    @Test
    void aStoredCredentialComesBackUnchanged() {
        PortalCredentialCipher cipher = cipher("portal-secret-under-test");

        String stored = cipher.encrypt("Pa55word@kb");

        assertTrue(stored.startsWith("v1:"), "the version prefix is what makes rotation possible");
        assertEquals("Pa55word@kb", cipher.decrypt(stored));
    }

    @Test
    void theCiphertextCarriesNoTraceOfThePlaintext() {
        String stored = cipher("portal-secret-under-test").encrypt("Pa55word@kb");

        assertFalse(stored.contains("Pa55word"), "the password must not be readable in the column");
        assertNotEquals("Pa55word@kb", stored);
    }

    @Test
    void twoEncryptionsOfTheSameValueDiffer() {
        PortalCredentialCipher cipher = cipher("portal-secret-under-test");

        assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"),
                "a fixed IV would leak that two merchants share a password");
    }

    @Test
    void withNoKeyConfiguredItRefusesToStoreAnything() {
        PortalCredentialCipher cipher = new PortalCredentialCipher("", "", "");

        assertFalse(cipher.isConfigured());
        assertNull(cipher.encrypt("Pa55word@kb"),
                "storing plaintext because encryption is unavailable would be worse than storing nothing");
        assertNull(cipher.decrypt("v1:whatever"));
    }

    @Test
    void aDifferentKeyCannotReadTheStoredValue() {
        String stored = cipher("old-secret").encrypt("Pa55word@kb");

        assertNull(cipher("rotated-secret").decrypt(stored),
                "an unreadable row must read as 'not retained', never as an empty password");
    }

    @Test
    void aPlaintextValueFromBeforeEncryptionIsNotMistakenForCiphertext() {
        assertNull(cipher("portal-secret-under-test").decrypt("Pa55word@kb"));
    }

    @Test
    void thePaymentSecretIsUsedWhenNoDedicatedSecretIsSet() {
        PortalCredentialCipher fromPaymentSecret =
                new PortalCredentialCipher("", "payment-secret-under-test", "");
        assertTrue(fromPaymentSecret.isConfigured());
        assertEquals("Pa55word@kb", fromPaymentSecret.decrypt(fromPaymentSecret.encrypt("Pa55word@kb")));
    }

    @Test
    void theJwtSecretIsTheLastResort() {
        PortalCredentialCipher fromJwt = new PortalCredentialCipher("", "", "jwt-secret-under-test");
        assertTrue(fromJwt.isConfigured());
        assertEquals("Pa55word@kb", fromJwt.decrypt(fromJwt.encrypt("Pa55word@kb")));
    }
}
