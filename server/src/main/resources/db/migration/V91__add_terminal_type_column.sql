ALTER TABLE restaurant_terminal
    ADD COLUMN terminal_type VARCHAR(20) NOT NULL DEFAULT 'BILLING';

COMMENT ON COLUMN restaurant_terminal.terminal_type IS
    'Functional role: BILLING (POS), KOT (kitchen display), ADMIN (back-office)';
