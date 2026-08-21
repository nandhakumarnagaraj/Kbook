# Payment Gateway Security (Easebuzz Integration)

## Trigger Conditions
- Implementing or modifying payment flows
- Adding new payment methods (UPI, cards, wallets)
- Handling webhooks/callbacks from payment gateway
- Debugging payment failures or double charges
- Setting up sub-merchant split payments
- User asks about hash verification, refunds, or idempotency

---

## Hash Verification

### Request Hash (Outgoing to Easebuzz)
```java
// NEVER trust client-sent amounts — always compute server-side
public String generatePaymentHash(PaymentRequest request) {
    // Easebuzz hash format: key|txnid|amount|productinfo|firstname|email|udf1-10|salt
    String hashString = String.join("|",
        MERCHANT_KEY,
        request.getTxnId(),
        request.getAmount().toPlainString(),
        request.getProductInfo(),
        request.getFirstName(),
        request.getEmail(),
        "", "", "", "", "",  // udf1-10
        MERCHANT_SALT
    );
    return computeSha512(hashString);
}
```

### Response Hash (Incoming from Easebuzz)
```java
// ALWAYS verify hash before processing payment result
public boolean verifyResponseHash(PaymentCallback callback) {
    // Reverse hash: salt|status|udf10-1|email|firstname|productinfo|amount|txnid|key
    String hashString = String.join("|",
        MERCHANT_SALT,
        callback.getStatus(),
        "", "", "", "", "", "", "", "",  // udf10-1
        callback.getEmail(),
        callback.getFirstName(),
        callback.getProductInfo(),
        callback.getAmount(),
        callback.getTxnId(),
        MERCHANT_KEY
    );
    String computed = computeSha512(hashString);
    // Constant-time comparison to prevent timing attacks
    return MessageDigest.isEqual(
        computed.getBytes(StandardCharsets.UTF_8),
        callback.getHash().getBytes(StandardCharsets.UTF_8)
    );
}
```

---

## Webhook Idempotency

```java
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private final PaymentRepository paymentRepository;
    private final RedisTemplate<String, String> redis;

    @Transactional
    public void processWebhook(PaymentCallback callback) {
        // 1. Verify hash FIRST
        if (!verifyResponseHash(callback)) {
            log.warn("Invalid hash for txn: {}", callback.getTxnId());
            throw new SecurityException("Hash verification failed");
        }

        // 2. Idempotency check — process each webhook exactly once
        String idempotencyKey = "webhook:" + callback.getTxnId() + ":" + callback.getStatus();
        Boolean isNew = redis.opsForValue().setIfAbsent(idempotencyKey, "1", Duration.ofHours(24));
        if (Boolean.FALSE.equals(isNew)) {
            log.info("Duplicate webhook ignored: {}", callback.getTxnId());
            return;
        }

        // 3. Update payment status
        Payment payment = paymentRepository.findByTxnId(callback.getTxnId())
            .orElseThrow(() -> new PaymentNotFoundException(callback.getTxnId()));

        // 4. State machine — only valid transitions
        if (!payment.canTransitionTo(callback.getStatus())) {
            log.warn("Invalid transition: {} -> {}", payment.getStatus(), callback.getStatus());
            return;
        }

        payment.setStatus(PaymentStatus.fromGateway(callback.getStatus()));
        payment.setGatewayResponse(callback.getRawJson());
        paymentRepository.save(payment);

        // 5. Trigger downstream actions
        if (payment.isSuccessful()) {
            eventPublisher.publish(new PaymentSuccessEvent(payment));
        }
    }
}
```

---

## Double-Charge Prevention

```java
// 1. Unique transaction ID per payment attempt
public String generateTxnId(UUID billId, int attemptNumber) {
    // Format: KB_{billId_short}_{attempt}_{timestamp}
    return String.format("KB_%s_%d_%d",
        billId.toString().substring(0, 8),
        attemptNumber,
        Instant.now().getEpochSecond()
    );
}

// 2. Lock before initiating payment
@Transactional
public PaymentInitResponse initiatePayment(UUID billId, UUID merchantId) {
    // Pessimistic lock on bill
    Bill bill = billRepository.findByIdWithLock(billId)
        .orElseThrow(() -> new ResourceNotFoundException("Bill", billId));

    // Check no pending payment exists
    Optional<Payment> existingPayment = paymentRepository
        .findByBillIdAndStatusIn(billId, List.of(INITIATED, PENDING));
    if (existingPayment.isPresent()) {
        // Check with gateway if pending payment resolved
        PaymentStatus gatewayStatus = checkPaymentStatus(existingPayment.get().getTxnId());
        if (gatewayStatus == SUCCESS) {
            // Process the existing successful payment
            markBillPaid(bill, existingPayment.get());
            throw new PaymentAlreadySuccessfulException(billId);
        }
        // Cancel stale pending payment
        existingPayment.get().setStatus(CANCELLED);
    }

    // Create new payment record
    Payment payment = Payment.builder()
        .billId(billId)
        .merchantId(merchantId)
        .amount(bill.getTotalAmount())
        .txnId(generateTxnId(billId, bill.getPaymentAttempts() + 1))
        .status(INITIATED)
        .build();
    paymentRepository.save(payment);

    return callGatewayInitiate(payment);
}
```

---

## Refund Handling

```java
@Transactional
public RefundResponse processRefund(UUID paymentId, BigDecimal amount, String reason) {
    Payment payment = paymentRepository.findById(paymentId)
        .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

    // Validate refund amount
    BigDecimal totalRefunded = refundRepository.sumRefundedAmount(paymentId);
    BigDecimal maxRefundable = payment.getAmount().subtract(totalRefunded);
    if (amount.compareTo(maxRefundable) > 0) {
        throw new InvalidRefundException("Refund exceeds available amount: " + maxRefundable);
    }

    // Create refund record BEFORE calling gateway
    Refund refund = Refund.builder()
        .paymentId(paymentId)
        .amount(amount)
        .reason(reason)
        .status(RefundStatus.INITIATED)
        .refundTxnId(generateRefundTxnId(paymentId))
        .build();
    refundRepository.save(refund);

    // Call gateway
    try {
        GatewayRefundResponse response = easebuzzClient.initiateRefund(
            payment.getTxnId(), amount, refund.getRefundTxnId());
        refund.setStatus(RefundStatus.fromGateway(response.getStatus()));
        refund.setGatewayResponse(response.getRawJson());
    } catch (Exception e) {
        refund.setStatus(RefundStatus.FAILED);
        refund.setErrorMessage(e.getMessage());
    }
    refundRepository.save(refund);
    return RefundMapper.toResponse(refund);
}
```

---

## Sub-Merchant Isolation

```java
// Split payment configuration for multi-outlet
public record SplitConfig(
    String subMerchantId,
    BigDecimal platformFeePercent,    // KhanaBook's commission
    BigDecimal subMerchantPercent     // Restaurant's share
) {}

// Validate sub-merchant belongs to the merchant
public void validateSubMerchant(UUID merchantId, String subMerchantId) {
    boolean valid = splitConfigRepository
        .existsByMerchantIdAndSubMerchantIdAndIsActiveTrue(merchantId, subMerchantId);
    if (!valid) {
        throw new SecurityException("Sub-merchant not authorized for this merchant");
    }
}
```

---

## Anti-patterns
- ❌ Trusting client-sent payment amount (always server-computed)
- ❌ Processing webhook without hash verification
- ❌ No idempotency check on webhooks (leads to double-processing)
- ❌ Storing payment secrets in application.yml (use vault/env vars)
- ❌ String comparison for hash verification (timing attack vulnerable)
- ❌ Initiating payment without checking for existing pending payments
- ❌ Refunding without tracking cumulative refund amount
- ❌ Logging full card numbers or sensitive payment data

## Verification Checklist
- [ ] Hash generated server-side with constant-time comparison
- [ ] Transaction IDs are unique and traceable to bills
- [ ] Webhook handler is idempotent (duplicate-safe)
- [ ] Double-charge prevention: lock + check before initiate
- [ ] Refund amount validated against remaining balance
- [ ] Payment secrets stored in environment variables only
- [ ] All payment events logged for audit trail
- [ ] Sub-merchant operations validate ownership
- [ ] Gateway errors handled gracefully (no money lost in limbo)
