#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 || $# -gt 4 ]]; then
  echo "Usage: $0 <owner_name> <phone_10_digits> <password> [device_id]" >&2
  exit 1
fi

OWNER_NAME="$1"
PHONE="$2"
PASSWORD="$3"
DEVICE_ID="${4:-seed-server}"

if ! [[ "$PHONE" =~ ^[0-9]{10}$ ]]; then
  echo "Phone number must be exactly 10 digits" >&2
  exit 1
fi

if [[ ${#PASSWORD} -lt 6 ]]; then
  echo "Password must be at least 6 characters" >&2
  exit 1
fi

if ! command -v htpasswd >/dev/null 2>&1; then
  echo "htpasswd is required to generate BCrypt hashes" >&2
  exit 1
fi

DB_URL="${DB_URL:-jdbc:postgresql://localhost:5432/kbook_saas}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-kbook_saas}"
DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-root}"

RESTAURANT_ID="$(date +%s%N | cut -b1-18)"
NOW_MS="$(($(date +%s) * 1000))"
TODAY="$(date +%F)"
SHOP_NAME="${OWNER_NAME}'s Restaurant"
PASSWORD_HASH="$(htpasswd -bnBC 12 '' "$PASSWORD" | tr -d ':\n')"

sql_escape() {
  printf "%s" "$1" | sed "s/'/''/g"
}

OWNER_NAME_SQL="$(sql_escape "$OWNER_NAME")"
PHONE_SQL="$(sql_escape "$PHONE")"
DEVICE_ID_SQL="$(sql_escape "$DEVICE_ID")"
SHOP_NAME_SQL="$(sql_escape "$SHOP_NAME")"
PASSWORD_HASH_SQL="$(sql_escape "$PASSWORD_HASH")"

export PGPASSWORD="$DB_PASSWORD"

psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" <<SQL
BEGIN;

INSERT INTO restaurantprofiles (
    local_id, device_id, restaurant_id, updated_at, is_deleted, server_updated_at, created_at,
    shop_name, shop_address, whatsapp_number, email, logo_path, fssai_number, email_invoice_consent,
    country, gst_enabled, gstin, is_tax_inclusive, gst_percentage,
    custom_tax_name, custom_tax_number, custom_tax_percentage,
    currency, upi_enabled, upi_qr_path, upi_handle, upi_mobile,
    cash_enabled, pos_enabled, zomato_enabled, swiggy_enabled, own_website_enabled,
    printer_enabled, printer_name, printer_mac, paper_size, auto_print_on_success,
    include_logo_in_print, print_customer_whatsapp,
    daily_order_counter, lifetime_order_counter, last_reset_date, session_timeout_minutes,
    timezone, review_url, show_branding, mask_customer_phone
) VALUES (
    1, '$DEVICE_ID_SQL', $RESTAURANT_ID, $NOW_MS, FALSE, $NOW_MS, $NOW_MS,
    '$SHOP_NAME_SQL', NULL, '$PHONE_SQL', '$PHONE_SQL', NULL, NULL, FALSE,
    'India', FALSE, NULL, FALSE, 0.00,
    NULL, NULL, 0.00,
    'INR', FALSE, NULL, NULL, NULL,
    TRUE, FALSE, FALSE, FALSE, FALSE,
    FALSE, NULL, NULL, '58mm', FALSE,
    TRUE, TRUE,
    0, 0, '$TODAY', 30,
    'Asia/Kolkata', NULL, TRUE, TRUE
);

INSERT INTO users (
    local_id, device_id, restaurant_id, updated_at, is_deleted, server_updated_at, created_at,
    name, email, login_id, google_email, auth_provider, password_hash, whatsapp_number, role, is_active
) VALUES (
    1, '$DEVICE_ID_SQL', $RESTAURANT_ID, $NOW_MS, FALSE, $NOW_MS, $NOW_MS,
    '$OWNER_NAME_SQL', '$PHONE_SQL', '$PHONE_SQL', NULL, 'PHONE', '$PASSWORD_HASH_SQL', '$PHONE_SQL', 'OWNER', TRUE
);

COMMIT;
SQL

echo "Seeded owner successfully."
echo "restaurant_id=$RESTAURANT_ID"
echo "login_id=$PHONE"
echo "device_id=$DEVICE_ID"
