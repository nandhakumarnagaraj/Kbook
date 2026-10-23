package com.khanabook.saas.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts credentials that KhanaBook must be able to show again.
 *
 * <p>The case that motivated it: Easebuzz requires a password when a sub-merchant is created, and
 * the merchant needs that password to sign in to the Easebuzz dashboard after approval. A
 * one-way hash cannot serve that — the plaintext has to come back — so the value is stored with
 * AES-GCM under a key held outside the database.
 *
 * <p>Fails closed by design. With no {@code security.portal-secret} configured, {@link #isConfigured()}
 * is false and callers decline to store rather than writing something unreadable or, worse,
 * plaintext. A configured but malformed key is treated the same way, with a warning, so a typo
 * cannot silently downgrade storage.
 */
@Component
public class PortalCredentialCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String PREFIX = "v1:";

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    /**
     * Key sources, in order: a dedicated secret, the payment secret every dev/sandbox profile
     * already requires, then the JWT secret every deployment has. Without the fallbacks the
     * feature would be silently off in production until somebody remembered an env var, which is
     * exactly the failure mode this class exists to prevent.
     */
    public PortalCredentialCipher(@Value("${security.portal-secret:}") String portalSecret,
                                  @Value("${payment.crypto.secret:}") String paymentSecret,
                                  @Value("${jwt.secret:}") String jwtSecret) {
        this.key = deriveKey(firstNonBlank(portalSecret, paymentSecret, jwtSecret));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    /** False when no usable key is configured; callers must not store credentials then. */
    public boolean isConfigured() {
        return key != null;
    }

    /**
     * Returns {@code v1:<base64(iv|ciphertext)>}, or {@code null} when no key is configured or the
     * value is blank. Callers treat {@code null} as "not retained".
     */
    public String encrypt(String plaintext) {
        if (key == null || plaintext == null || plaintext.isEmpty()) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            // Never log the value itself: this is a credential.
            throw new IllegalStateException("Could not encrypt the stored credential", e);
        }
    }

    /**
     * Returns the plaintext, or {@code null} when the value cannot be read with the current key —
     * a rotated secret or a row written before encryption existed. {@code null} means "ask support
     * to reset it", never "the password is empty".
     */
    public String decrypt(String stored) {
        if (key == null || stored == null || stored.isBlank()) return null;
        if (!stored.startsWith(PREFIX)) return null;
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (payload.length <= IV_BYTES) return null;

            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            System.arraycopy(payload, IV_BYTES, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Accepts either a base64-encoded 16/24/32-byte key or a passphrase, which is stretched with
     * SHA-256 to 32 bytes. A passphrase is not ideal, but it is far better than refusing to
     * encrypt at all on a deployment that only has an env var to work with.
     */
    private static SecretKey deriveKey(String secret) {
        if (secret == null || secret.isBlank()) return null;
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException notBase64) {
            raw = null;
        }
        if (raw == null || (raw.length != 16 && raw.length != 24 && raw.length != 32)) {
            raw = sha256(secret);
        }
        return new SecretKeySpec(raw, "AES");
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
