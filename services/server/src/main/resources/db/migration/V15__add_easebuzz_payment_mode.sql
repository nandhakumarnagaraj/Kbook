-- Easebuzz is now a distinct payment_mode value (separate from 'upi').
-- UPI = offline QR manual confirmation; easebuzz = Easebuzz gateway payment.

ALTER TABLE bill_payments
    DROP CONSTRAINT IF EXISTS chk_bill_payment_mode;

ALTER TABLE bill_payments
    ADD CONSTRAINT chk_bill_payment_mode CHECK (
        payment_mode IN ('cash', 'upi', 'easebuzz', 'pos', 'part_cash_upi', 'part_cash_pos', 'part_upi_pos', 'part_cash_easebuzz', 'part_easebuzz_pos', 'zomato', 'swiggy', 'own_website')
    );
