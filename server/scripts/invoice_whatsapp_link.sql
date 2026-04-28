-- ============================================================
-- KhanaBook: Invoice Formatter + WhatsApp Link Generator
-- Tables: bills, bill_items, restaurant_profile
-- Usage:
--   SELECT khanabook_whatsapp_link(bill_id);
--   SELECT * FROM v_invoice_whatsapp_links;
-- ============================================================


-- ── 1. URL-encode helper ─────────────────────────────────────
-- Encodes text safe for use in a WhatsApp wa.me link.
CREATE OR REPLACE FUNCTION khanabook_url_encode(p_input TEXT)
RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE
AS $$
DECLARE
  v_result TEXT := p_input;
BEGIN
  -- % must be first to avoid double-encoding
  v_result := replace(v_result, '%',    '%25');
  v_result := replace(v_result, ' ',    '%20');
  v_result := replace(v_result, '&',    '%26');
  v_result := replace(v_result, '+',    '%2B');
  v_result := replace(v_result, '=',    '%3D');
  v_result := replace(v_result, '#',    '%23');
  v_result := replace(v_result, '?',    '%3F');
  v_result := replace(v_result, E'\n',  '%0A');
  v_result := replace(v_result, E'\r',  '%0D');
  v_result := replace(v_result, '*',    '%2A');  -- WhatsApp bold markers
  v_result := replace(v_result, '₹',   '%E2%82%B9');
  v_result := replace(v_result, 'Rs.',  'Rs.');  -- safe, no encoding needed
  RETURN v_result;
END;
$$;


-- ── 2. Invoice text builder ──────────────────────────────────
-- Builds the same invoice text as the Android app's generateBillText().
CREATE OR REPLACE FUNCTION khanabook_invoice_text(p_bill_id BIGINT)
RETURNS TEXT
LANGUAGE plpgsql STABLE
AS $$
DECLARE
  v_bill      RECORD;
  v_profile   RECORD;
  v_item      RECORD;
  v_text      TEXT := '';
  v_currency  TEXT;
  v_inv_label TEXT;
  v_order_num TEXT;
  v_item_name TEXT;
  v_tz        TEXT;
BEGIN
  SELECT * INTO v_bill
  FROM bills
  WHERE id = p_bill_id AND is_deleted = false;

  IF NOT FOUND THEN
    RETURN NULL;
  END IF;

  SELECT * INTO v_profile
  FROM restaurant_profile
  WHERE restaurant_id = v_bill.restaurant_id
  LIMIT 1;

  -- Currency symbol
  v_currency := CASE
    WHEN v_profile.currency IN ('INR', 'Rupee') THEN 'Rs.'
    ELSE coalesce(v_profile.currency, 'Rs.')
  END;

  v_tz := coalesce(v_profile.timezone, 'Asia/Kolkata');

  -- ── Header ──
  v_text := '*' || upper(coalesce(v_profile.shop_name, 'SHOP')) || '*' || E'\n';

  IF v_profile.shop_address IS NOT NULL AND trim(v_profile.shop_address) <> '' THEN
    v_text := v_text || v_profile.shop_address || E'\n';
  END IF;

  IF v_profile.gstin IS NOT NULL AND trim(v_profile.gstin) <> '' THEN
    v_text := v_text || 'GSTIN: ' || v_profile.gstin || E'\n';
  END IF;

  v_text := v_text || '--------------------------' || E'\n';

  -- ── Order meta ──
  -- daily_order_display format: "YYYY-MM-DD-N" — take the last segment
  v_order_num := split_part(
    v_bill.daily_order_display,
    '-',
    array_length(string_to_array(v_bill.daily_order_display, '-'), 1)
  );
  v_text := v_text || '*Order ID:* #' || v_order_num || E'\n';

  v_inv_label := CASE
    WHEN v_profile.gst_enabled THEN 'Tax Invoice No'
    ELSE 'Invoice No'
  END;
  v_text := v_text || '*' || v_inv_label || ':* INV' || v_bill.lifetime_order_id::TEXT || E'\n';

  v_text := v_text || '*Date:* ' || to_char(
    to_timestamp(v_bill.created_at / 1000.0) AT TIME ZONE v_tz,
    'DD Mon YYYY, HH12:MI AM'
  ) || E'\n';

  v_text := v_text || '--------------------------' || E'\n';

  -- ── Line items ──
  FOR v_item IN
    SELECT *
    FROM bill_items
    WHERE bill_id = p_bill_id AND is_deleted = false
    ORDER BY id
  LOOP
    v_item_name := upper(v_item.item_name);
    IF v_item.variant_name IS NOT NULL AND trim(v_item.variant_name) <> '' THEN
      v_item_name := v_item_name || ' (' || v_item.variant_name || ')';
    END IF;
    v_text := v_text
      || v_item_name
      || ' x ' || v_item.quantity
      || ' = ' || v_currency || v_item.item_total
      || E'\n';
  END LOOP;

  -- ── Footer ──
  v_text := v_text || '--------------------------' || E'\n';
  v_text := v_text || '*Total Amount: ' || v_currency || v_bill.total_amount || '*' || E'\n';
  v_text := v_text || '--------------------------' || E'\n';
  v_text := v_text || 'Thank you for your visit!' || E'\n';

  RETURN v_text;
END;
$$;


-- ── 3. WhatsApp link generator ───────────────────────────────
-- Returns a wa.me link pre-filled with the invoice text.
-- If customer_whatsapp exists → link opens chat directly.
-- If not → link opens WhatsApp with text only (user picks chat).
CREATE OR REPLACE FUNCTION khanabook_whatsapp_link(p_bill_id BIGINT)
RETURNS TEXT
LANGUAGE plpgsql STABLE
AS $$
DECLARE
  v_phone           TEXT;
  v_digits          TEXT;
  v_phone_formatted TEXT;
  v_text            TEXT;
BEGIN
  SELECT customer_whatsapp INTO v_phone
  FROM bills
  WHERE id = p_bill_id AND is_deleted = false;

  v_text := khanabook_invoice_text(p_bill_id);
  IF v_text IS NULL THEN
    RETURN NULL;
  END IF;

  -- Strip non-digits, add country code if 10-digit Indian number
  v_digits := regexp_replace(coalesce(v_phone, ''), '[^0-9]', '', 'g');
  v_phone_formatted := CASE
    WHEN length(v_digits) = 10 THEN '91' || v_digits
    WHEN length(v_digits) > 10 THEN v_digits
    ELSE NULL
  END;

  IF v_phone_formatted IS NOT NULL THEN
    RETURN 'https://wa.me/' || v_phone_formatted
      || '?text=' || khanabook_url_encode(v_text);
  ELSE
    RETURN 'https://wa.me/?text=' || khanabook_url_encode(v_text);
  END IF;
END;
$$;


-- ── 4. Convenience view ──────────────────────────────────────
-- Lists all paid/completed bills with their WhatsApp links.
CREATE OR REPLACE VIEW v_invoice_whatsapp_links AS
SELECT
  b.id                     AS bill_id,
  b.lifetime_order_id,
  b.daily_order_display    AS order_display,
  b.customer_name,
  b.customer_whatsapp,
  b.total_amount,
  to_timestamp(b.created_at / 1000.0)
    AT TIME ZONE coalesce(rp.timezone, 'Asia/Kolkata') AS billed_at,
  khanabook_whatsapp_link(b.id)                         AS whatsapp_link
FROM bills b
LEFT JOIN restaurant_profile rp ON rp.restaurant_id = b.restaurant_id
WHERE b.is_deleted = false
  AND b.customer_whatsapp IS NOT NULL
ORDER BY b.created_at DESC;


-- ============================================================
-- USAGE EXAMPLES
-- ============================================================

-- Get WhatsApp link for a single bill:
--   SELECT khanabook_whatsapp_link(123);

-- Get invoice text (plain, for debugging):
--   SELECT khanabook_invoice_text(123);

-- Get links for all bills with a customer phone:
--   SELECT bill_id, customer_name, customer_whatsapp, whatsapp_link
--   FROM v_invoice_whatsapp_links
--   LIMIT 20;

-- Get links for today's bills (IST):
--   SELECT bill_id, order_display, customer_name, whatsapp_link
--   FROM v_invoice_whatsapp_links
--   WHERE billed_at::date = (NOW() AT TIME ZONE 'Asia/Kolkata')::date;

-- Run against a specific restaurant:
--   SELECT bill_id, customer_name, whatsapp_link
--   FROM v_invoice_whatsapp_links
--   WHERE bill_id IN (
--     SELECT id FROM bills WHERE restaurant_id = 1
--   );
