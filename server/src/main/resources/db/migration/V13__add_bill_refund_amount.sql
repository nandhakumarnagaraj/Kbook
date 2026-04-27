ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS refund_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

UPDATE bills
SET refund_amount = total_amount
WHERE is_deleted = FALSE
  AND order_status = 'cancelled'
  AND payment_status = 'success'
  AND COALESCE(refund_amount, 0) = 0;
