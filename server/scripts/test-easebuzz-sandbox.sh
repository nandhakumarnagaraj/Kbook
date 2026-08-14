#!/usr/bin/env bash
#
# Easebuzz Sandbox API Test Script
# Tests KhanaBook backend endpoints against Easebuzz sandbox
#
# Prerequisites:
#   - Backend running on localhost:8081 (dev profile)
#   - Easebuzz sandbox credentials in application-dev.properties
#   - Admin JWT token (login as KBOOK_ADMIN first)
#
# Usage:
#   export ADMIN_TOKEN="your_jwt_token_here"
#   ./test-easebuzz-sandbox.sh

set -euo pipefail

BASE_URL="http://localhost:8081/api/v2"
ADMIN_TOKEN="${ADMIN_TOKEN:-}"
RESTAURANT_ID="${RESTAURANT_ID:-999}"

echo "=========================================="
echo "KhanaBook Easebuzz Sandbox Test Script"
echo "=========================================="
echo ""

if [[ -z "$ADMIN_TOKEN" ]]; then
    echo "ERROR: Set ADMIN_TOKEN environment variable"
    echo "Example: export ADMIN_TOKEN='Bearer eyJhbG...'"
    exit 1
fi

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counters
PASSED=0
FAILED=0

# Helper functions
http_get() {
    local path="$1"
    local name="$2"
    echo -e "${YELLOW}→ GET $path${NC}"
    local response
    response=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: $ADMIN_TOKEN" \
        "$BASE_URL$path")
    local code
    code=$(echo "$response" | tail -n1)
    local body
    body=$(echo "$response" | sed '$d')
    if [[ "$code" == "200" ]]; then
        echo -e "${GREEN}✓ $name (HTTP $code)${NC}"
        ((PASSED++))
    else
        echo -e "${RED}✗ $name (HTTP $code)${NC}"
        echo "  Body: $body"
        ((FAILED++))
    fi
    echo "$body"
    echo ""
}

http_post() {
    local path="$1"
    local name="$2"
    local data="$3"
    echo -e "${YELLOW}→ POST $path${NC}"
    local response
    response=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: $ADMIN_TOKEN" \
        -H "Content-Type: application/json" \
        -d "$data" \
        "$BASE_URL$path")
    local code
    code=$(echo "$response" | tail -n1)
    local body
    body=$(echo "$response" | sed '$d')
    if [[ "$code" == "200" || "$code" == "201" ]]; then
        echo -e "${GREEN}✓ $name (HTTP $code)${NC}"
        ((PASSED++))
    else
        echo -e "${RED}✗ $name (HTTP $code)${NC}"
        echo "  Body: $body"
        ((FAILED++))
    fi
    echo "$body"
    echo ""
}

http_put() {
    local path="$1"
    local name="$2"
    local data="$3"
    echo -e "${YELLOW}→ PUT $path${NC}"
    local response
    response=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: $ADMIN_TOKEN" \
        -H "Content-Type: application/json" \
        -d "$data" \
        "$BASE_URL$path")
    local code
    code=$(echo "$response" | tail -n1)
    local body
    body=$(echo "$response" | sed '$d')
    if [[ "$code" == "200" ]]; then
        echo -e "${GREEN}✓ $name (HTTP $code)${NC}"
        ((PASSED++))
    else
        echo -e "${RED}✗ $name (HTTP $code)${NC}"
        echo "  Body: $body"
        ((FAILED++))
    fi
    echo "$body"
    echo ""
}

# ============================================
# TEST SUITE
# ============================================

echo "--- 1. List Sub-Merchants ---"
http_get "/admin/sub-merchants" "List all sub-merchants"

echo "--- 2. Create Sub-Merchant Draft ---"
CREATE_RESPONSE=$(http_post "/admin/sub-merchants" "Create draft" '{
    "restaurantId": '"$RESTAURANT_ID"',
    "businessName": "Sandbox Test Restaurant",
    "businessType": "Restaurant",
    "pan": "ABCDE1234F",
    "gst": "27AABCU9603R1ZM",
    "bankAccountNo": "123456789012",
    "ifsc": "HDFC0000123",
    "bankName": "HDFC",
    "branchName": "Mumbai",
    "beneficiaryName": "Test Owner",
    "businessAddress": "123 Sandbox Street",
    "contactEmail": "test@easebuzz.sandbox",
    "contactPhone": "9999999999",
    "commissionRate": "3.0"
}')

# Extract sub-merchant ID from response
SUB_MERCHANT_ID=$(echo "$CREATE_RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
if [[ -z "$SUB_MERCHANT_ID" ]]; then
    echo -e "${RED}Failed to create sub-merchant. Exiting.${NC}"
    exit 1
fi
echo "Created sub-merchant ID: $SUB_MERCHANT_ID"
echo ""

echo "--- 3. Get Sub-Merchant Detail ---"
http_get "/admin/sub-merchants/$SUB_MERCHANT_ID" "Get sub-merchant detail"

echo "--- 4. Submit to Easebuzz (Sandbox) ---"
SUBMIT_RESPONSE=$(http_post "/admin/sub-merchants/$SUB_MERCHANT_ID/submit-to-easebuzz" "Submit to Easebuzz" '{}')
echo "$SUBMIT_RESPONSE"

# Check if Easebuzz returned an error (e.g., module not enabled)
if echo "$SUBMIT_RESPONSE" | grep -q "error\|failure"; then
    echo -e "${YELLOW}⚠ Easebuzz sandbox returned error. This is expected if:${NC}"
    echo -e "${YELLOW}  - Sub-merchant module is not enabled on your Easebuzz account${NC}"
    echo -e "${YELLOW}  - Test credentials (ADNX3KYX5) are not valid for sub-merchant APIs${NC}"
    echo -e "${YELLOW}  - You need to request Easebuzz ops to enable marketplace features${NC}"
    echo ""
    echo "Skipping remaining Easebuzz-dependent tests..."
    SKIP_EASEBUZZ=1
else
    SKIP_EASEBUZZ=0
    EASEBUZZ_SUBMERCHANT_ID=$(echo "$SUBMIT_RESPONSE" | grep -o '"subMerchantId":"[^"]*"' | cut -d'"' -f4)
    echo "Easebuzz Sub-Merchant ID: $EASEBUZZ_SUBMERCHANT_ID"
fi

echo ""

if [[ "$SKIP_EASEBUZZ" == "0" ]]; then
    echo "--- 5. Generate KYC Access Key ---"
    http_post "/admin/sub-merchants/$SUB_MERCHANT_ID/kyc-access-key" "Generate KYC" '{}'

    echo "--- 6. Create Split Label ---"
    http_post "/admin/sub-merchants/$SUB_MERCHANT_ID/split-label" "Create split label" '{}'

    echo "--- 7. Update Status to Active ---"
    http_put "/admin/sub-merchants/$SUB_MERCHANT_ID/status" "Update status" '{"status": "ACTIVE"}'
fi

echo "--- 8. Payment Flow Test (Mock) ---"
# Create a test bill first via API or use a known bill ID
BILL_ID="${BILL_ID:-1}"

echo "Creating payment order for bill $BILL_ID..."
PAYMENT_RESPONSE=$(curl -s -w "\n%{http_code}" \
    -H "Authorization: $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"billId\": $BILL_ID, \"restaurantId\": $RESTAURANT_ID}" \
    "$BASE_URL/payments/easebuzz/create-order")
PAYMENT_CODE=$(echo "$PAYMENT_RESPONSE" | tail -n1)
PAYMENT_BODY=$(echo "$PAYMENT_RESPONSE" | sed '$d')

if [[ "$PAYMENT_CODE" == "200" ]]; then
    echo -e "${GREEN}✓ Payment order created (HTTP $PAYMENT_CODE)${NC}"
    ((PASSED++))
else
    echo -e "${RED}✗ Payment order failed (HTTP $PAYMENT_CODE)${NC}"
    echo "  Body: $PAYMENT_BODY"
    ((FAILED++))
fi
echo "$PAYMENT_BODY"
echo ""

echo "--- 9. Webhook Simulation ---"
TXNID=$(echo "$PAYMENT_BODY" | grep -o '"txnid":"[^"]*"' | cut -d'"' -f4)
if [[ -n "$TXNID" ]]; then
    echo "Simulating payment webhook for txnid: $TXNID"
    WEBHOOK_RESPONSE=$(curl -s -w "\n%{http_code}" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "txnid=$TXNID" \
        -d "status=success" \
        -d "amount=1000.00" \
        -d "easebuzz_id=E250TEST123" \
        -d "udf1=$BILL_ID" \
        -d "productinfo=Test" \
        -d "firstname=Test" \
        -d "email=test@test.com" \
        -d "hash=skip_for_test" \
        "$BASE_URL/payments/easebuzz/webhook")
    WH_CODE=$(echo "$WEBHOOK_RESPONSE" | tail -n1)
    WH_BODY=$(echo "$WEBHOOK_RESPONSE" | sed '$d')
    if [[ "$WH_CODE" == "200" ]]; then
        echo -e "${GREEN}✓ Webhook received (HTTP $WH_CODE)${NC}"
        ((PASSED++))
    else
        echo -e "${RED}✗ Webhook failed (HTTP $WH_CODE)${NC}"
        ((FAILED++))
    fi
    echo "$WH_BODY"
else
    echo -e "${YELLOW}⚠ No txnid found, skipping webhook test${NC}"
fi
echo ""

echo "--- 10. Refund Flow Test ---"
if [[ -n "$TXNID" ]]; then
    REFUND_RESPONSE=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: $ADMIN_TOKEN" \
        -H "Content-Type: application/json" \
        -d '{"amount": 100.00, "reason": "Customer request"}' \
        "$BASE_URL/payments/easebuzz/refund/$BILL_ID")
    REF_CODE=$(echo "$REFUND_RESPONSE" | tail -n1)
    REF_BODY=$(echo "$REFUND_RESPONSE" | sed '$d')
    if [[ "$REF_CODE" == "200" ]]; then
        echo -e "${GREEN}✓ Refund initiated (HTTP $REF_CODE)${NC}"
        ((PASSED++))
    else
        echo -e "${RED}✗ Refund failed (HTTP $REF_CODE)${NC}"
        ((FAILED++))
    fi
    echo "$REF_BODY"
    
    # Check refund status
    echo ""
    echo "Checking refund status..."
    REF_STATUS=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: $ADMIN_TOKEN" \
        "$BASE_URL/payments/easebuzz/refund-status/$BILL_ID")
    RS_CODE=$(echo "$REF_STATUS" | tail -n1)
    RS_BODY=$(echo "$REF_STATUS" | sed '$d')
    if [[ "$RS_CODE" == "200" ]]; then
        echo -e "${GREEN}✓ Refund status retrieved (HTTP $RS_CODE)${NC}"
        ((PASSED++))
    else
        echo -e "${RED}✗ Refund status failed (HTTP $RS_CODE)${NC}"
        ((FAILED++))
    fi
    echo "$RS_BODY"
else
    echo -e "${YELLOW}⚠ No txnid found, skipping refund test${NC}"
fi

echo ""
echo "=========================================="
echo "Test Results"
echo "=========================================="
echo -e "${GREEN}Passed: $PASSED${NC}"
echo -e "${RED}Failed: $FAILED${NC}"
echo ""

if [[ $FAILED -gt 0 ]]; then
    echo -e "${RED}Some tests failed. Review the output above.${NC}"
    exit 1
else
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
fi
