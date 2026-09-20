package com.khanabook.saas.feature.onboarding.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** The exact payment terms accepted by the restaurant owner. */
public final class MerchantAgreementTerms {
    public static final String VERSION = "2.0";
    public static final String TEXT = """
            KHANABOOK RESTAURANT PAYMENT AND SETTLEMENT ADDENDUM

            Parties: Khanabook provides restaurant software and payment integration. The Restaurant Owner sells and fulfils food and services to its customers. Easebuzz processes eligible electronic payments under its applicable terms.

            Proposed Easebuzz processing rates (exclusive of GST):
            UPI 0.25%; Visa/Mastercard/RuPay credit card 1.90%; Amex 2.90%; Diners and corporate card 2.50%; Visa/Mastercard/RuPay debit card 1.00%; UPI on RuPay credit card 2.00%; netbanking 1.75%; mobile wallets 1.90%; BNPL 2.00%; credit-card EMI 2.00%; cardless EMI 2.00%.

            These are proposed Easebuzz commercials for an initial 3 to 6 months, subject to Easebuzz's formal approval and the merchant's final approved pricing. Khanabook will show the final approved rate schedule before live payment activation. Later rate changes require prior notice and the Restaurant Owner's acceptance.

            Easebuzz stated T+1 single settlement, subject to its approved terms, banking days, holds, refunds, chargebacks and risk checks. The Restaurant Owner authorizes the applicable approved processing charges and GST to be deducted from gross collections. Any separate Khanabook platform fee must be disclosed and accepted separately; none is created by this addendum.

            The Restaurant Owner is responsible for order fulfilment, food quality, customer invoices, cancellations and refund decisions. Khanabook provides transaction records and may initiate approved refunds through Easebuzz. Refunds and chargebacks may affect future settlements. Processing fees are not automatically refunded.

            This acceptance records the Restaurant Owner's authorization for Khanabook to integrate Easebuzz for its customer payments. Easebuzz merchant/submerchant onboarding, KYC and provider approval remain separate requirements. The signer confirms authority to act for the Restaurant Owner.
            """.strip();

    public static final String SHA256 = sha256(TEXT);

    private MerchantAgreementTerms() { }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
