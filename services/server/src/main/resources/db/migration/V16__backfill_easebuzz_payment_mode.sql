-- Backfill existing 'upi' payment_mode records that were actually Easebuzz gateway
-- payments. verified_by = 'easebuzz' is the authoritative signal set by
-- EasebuzzPaymentService when it records a gateway-confirmed payment.

-- 1. bill_payments: upi → easebuzz where gateway confirmed it
UPDATE bill_payments
SET payment_mode = 'easebuzz'
WHERE payment_mode = 'upi'
  AND verified_by = 'easebuzz';

-- 2. bills: upi → easebuzz where the payment leg came through the gateway
UPDATE bills b
SET payment_mode = 'easebuzz'
WHERE payment_mode = 'upi'
  AND EXISTS (
      SELECT 1 FROM bill_payments bp
      WHERE bp.bill_id = b.id
        AND bp.verified_by = 'easebuzz'
  );

-- 3. bills: part_cash_upi → part_cash_easebuzz where the UPI leg was gateway-paid
UPDATE bills b
SET payment_mode = 'part_cash_easebuzz'
WHERE payment_mode = 'part_cash_upi'
  AND EXISTS (
      SELECT 1 FROM bill_payments bp
      WHERE bp.bill_id = b.id
        AND bp.verified_by = 'easebuzz'
  );

-- 4. bills: part_upi_pos → part_easebuzz_pos where the UPI leg was gateway-paid
UPDATE bills b
SET payment_mode = 'part_easebuzz_pos'
WHERE payment_mode = 'part_upi_pos'
  AND EXISTS (
      SELECT 1 FROM bill_payments bp
      WHERE bp.bill_id = b.id
        AND bp.verified_by = 'easebuzz'
  );
