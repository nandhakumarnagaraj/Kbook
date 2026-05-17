# Easebuzz Sub-Merchant & Payment API Reference

> **Official Easebuzz Documentation**: https://docs.easebuzz.in/
> All endpoint URLs, hash sequences, and request/response schemas below are aligned with the official Easebuzz Stoplight documentation.

---

## Table of Contents

1. [Create Sub-Merchant](#1-create-sub-merchant)
2. [Update Sub-Merchant](#2-update-sub-merchant)
3. [Generate KYC Access Key](#3-generate-kyc-access-key)
4. [Confirm OTP / Verify OTP](#4-confirm-otp--verify-otp)
5. [Resend OTP](#5-resend-otp)
6. [Create Split Label](#6-create-split-label)
7. [Post-Transaction Split — Create](#7-post-transaction-split--create)
8. [Post-Transaction Split — Retrieve](#8-post-transaction-split--retrieve)
9. [Payment — Initiate API](#9-payment--initiate-api)
10. [Payment — Status API](#10-payment--status-api)
11. [Cancel Transaction](#11-cancel-transaction)
12. [Refund API v2](#12-refund-api-v2)
13. [Refund Status API](#13-refund-status-api)
14. [Payout API v2](#14-payout-api-v2)
15. [On-Demand Settlement APIs](#15-on-demand-settlement-apis)

---

## 1. Create Sub-Merchant

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/af94640eeae86-create-sub-merchant-api
- **Endpoint**: `POST https://dashboard.easebuzz.in/merchant/v1/submerchant/create`
- **Content-Type**: `application/json`
- **Hash**: `key|submerchant_email|submerchant_phone|salt`

### Request
```json
{
  "merchant_details": {
    "merchant_key": "YOUR_MERCHANT_KEY",
    "hash": "sha512(key|email|phone|salt)"
  },
  "submerchant_details": {
    "sub_merchant_name": "Business Name",
    "sub_merchant_email": "email@example.com",
    "sub_merchant_phone": "9999999999",
    "sub_merchant_name_in_bank": "Name as per bank",
    "sub_merchant_account_number": "1234567890",
    "sub_merchant_bank_name": "HDFC",
    "sub_merchant_branch_name": "Branch Name",
    "sub_merchant_ifsc_code": "HDFC0000123",
    "sub_merchant_password": "AutoGenPass@123",
    "sub_merchant_confirm_password": "AutoGenPass@123"
  }
}
```

### Response (Success)
```json
{
  "status": true,
  "submerchant": {
    "email": "email@example.com",
    "account_number": "xxxxxxxxxxxx",
    "submerchant_id": "S360DILA",
    "name": "Business Name",
    "mobile_number": "xxxxxxxxxx"
  },
  "submerchant_id": "S360DILA"
}
```

### Response (Failure)
```json
{
  "status": false,
  "error": "error_code",
  "error_desc": "Human readable error description"
}
```

---

## 2. Update Sub-Merchant

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/94318e0f55958-update-sub-merchant-api
- **Endpoint**: `POST https://dashboard.easebuzz.in/merchant/v1/submerchant/create` (same endpoint as create)
- **Content-Type**: `application/json`
- **Hash**: `key|submerchant_email|submerchant_phone|salt`

### Request
```json
{
  "merchant_details": {
    "merchant_key": "YOUR_MERCHANT_KEY",
    "hash": "sha512(key|email|phone|salt)"
  },
  "submerchant_details": {
    "sub_merchant_name": "Updated Business Name",
    "sub_merchant_email": "email@example.com",
    "sub_merchant_phone": "9999999999",
    "sub_merchant_name_in_bank": "Name as per bank",
    "sub_merchant_account_number": "1234567890",
    "sub_merchant_bank_name": "HDFC",
    "sub_merchant_branch_name": "Branch Name",
    "sub_merchant_ifsc_code": "HDFC0000123",
    "sub_merchant_id": "S360DILA"
  }
}
```

### Response (Success)
```json
{
  "status": true,
  "submerchant_id": "S360DILA"
}
```

---

## 3. Generate KYC Access Key

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/2b48a38b084b9-generate-sub-merchant-kyc-access-key-api
- **Endpoint**: `POST https://dashboard.easebuzz.in/submerchant/v1/generate_kyc_access_key`
- **Content-Type**: `application/json`
- **Hash**: `merchant_key|sub_merchant_id|name|email|phone|salt`

### Request
```json
{
  "merchant_key": "YOUR_MERCHANT_KEY",
  "sub_merchant_id": "S360DILA",
  "name": "Business Name",
  "email": "email@example.com",
  "phone": "9999999999",
  "hash": "sha512(merchant_key|sub_merchant_id|name|email|phone|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": "https://kyc.easebuzz.in/..."
}
```

### Response (Failure)
```json
{
  "status": false,
  "msg": "Error message"
}
```

---

## 4. Confirm OTP / Verify OTP

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/ifudkz7jpfadk-confirm-otp-verify-otp-api
- **Endpoint**: `POST https://dashboard.easebuzz.in/submerchant/v1/verify_otp`
- **Content-Type**: `application/json`
- **Purpose**: Verify the OTP sent to the sub-merchant's registered mobile/email during onboarding or password reset.
- **Note**: KhanaBook uses this API via `POST /admin/sub-merchants/{id}/verify-otp` from the web admin UI.

### Request
```json
{
  "merchant_key": "YOUR_MERCHANT_KEY",
  "sub_merchant_id": "S360DILA",
  "otp": "123456",
  "hash": "sha512(merchant_key|sub_merchant_id|otp|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": "OTP verified successfully"
}
```

---

## 5. Resend OTP

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/3b9hki8thr6pn-resend-otp-api
- **Endpoint**: `POST https://dashboard.easebuzz.in/submerchant/v1/resend_otp`
- **Content-Type**: `application/json`
- **Purpose**: Resend OTP to the sub-merchant's registered contact.
- **Note**: KhanaBook uses this API via `POST /admin/sub-merchants/{id}/resend-otp` from the web admin UI.

### Request
```json
{
  "merchant_key": "YOUR_MERCHANT_KEY",
  "sub_merchant_id": "S360DILA",
  "hash": "sha512(merchant_key|sub_merchant_id|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": "OTP resent successfully"
}
```

---

## 6. Create Split Label

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/e383e3fb60ce3-create-api
- **Endpoint**: `POST https://dashboard.easebuzz.in/split/v1/create`
- **Content-Type**: `application/json`
- **Hash**: `key|name_on_bank|bank_name|branch_name|ifsc_code|account_number|label|salt`

### Request
```json
{
  "key": "YOUR_MERCHANT_KEY",
  "hash": "sha512(key|name_on_bank|bank_name|branch_name|ifsc_code|account_number|label|salt)",
  "name_on_bank": "Name as per bank",
  "bank_name": "HDFC",
  "branch_name": "Branch Name",
  "ifsc_code": "HDFC0000123",
  "account_number": "1234567890",
  "label": "sm_S360DILA"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": "Label created successfully"
}
```

---

## 7. Post-Transaction Split — Create

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/xsrzv37a3ksv0-payout-api-v2 (split creation is part of the payout/split suite)
- **Endpoint**: `POST https://dashboard.easebuzz.in/post-split/v1/create/`
- **Content-Type**: `application/json`
- **Hash**: `key|merchant_request_id|easebuzz_id|salt`
- **Note**: Max 3 update attempts per transaction

### Request
```json
{
  "key": "YOUR_MERCHANT_KEY",
  "merchant_request_id": "UMRID_1712345678",
  "easebuzz_id": "E250XXXXXXXXXX",
  "amount": "100.00",
  "description": "Split for order #123",
  "configuration": [
    { "label": "sm_S360DILA", "amount": "95.00" },
    { "label": "master_commission", "amount": "5.00" }
  ],
  "hash": "sha512(key|merchant_request_id|easebuzz_id|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "request_status": "success",
  "merchant_request_id": "UMRID_1712345678",
  "configuration": [
    { "label": "sm_S360DILA", "status": "success" },
    { "label": "master_commission", "status": "success" }
  ],
  "meta": {
    "description": "Split Configuration for a transaction can be changed upto 3 times",
    "total_attempts": "3",
    "remaining_attempts": "2"
  }
}
```

### Error Codes
| Code | Description |
|------|-------------|
| EBPTSURVE01 | Duplicate merchant_request_id |
| EBPTSURVE02 | Post-split feature not activated |
| EBPTSURVE04 | Split payouts disabled |
| EBPTSURVE06 | Max update attempts reached |
| EBPTSURVE07 | Empty configuration array |
| EBPTSURVE08 | Split total ≠ transaction amount |
| EBPTSUTVE01 | Transaction not found |
| EBPTSUTVE02 | Transaction not successful |
| EBPTSUTVE03 | Amount mismatch |
| EBPTSUTVE04 | Transaction already settled |
| EBPTSUTVE05 | Refunds exist against transaction |

---

## 8. Post-Transaction Split — Retrieve

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/d333db79c5c82-retrieve-api
- **Endpoint**: `POST https://dashboard.easebuzz.in/post-split/v1/retrieve/`
- **Content-Type**: `application/json`
- **Hash**: `key|merchant_request_id|salt`

### Request
```json
{
  "key": "YOUR_MERCHANT_KEY",
  "merchant_request_id": "UMRID_1712345678",
  "hash": "sha512(key|merchant_request_id|salt)"
}
```

### Response (Success)
```json
{
  "status": "success",
  "merchant_request_id": "UMRID_1712345678",
  "description": "Split for order #123",
  "error": null,
  "split_configuration": [
    { "label": "sm_S360DILA", "amount": 95.00 },
    { "label": "master_commission", "amount": 5.00 }
  ]
}
```

---

## 9. Payment — Initiate API

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/q3zhxyn3v4xbv-initiate-api
- **Endpoint**: `POST https://pay.easebuzz.in/payment/initiateLink` (or SDK equivalent)
- **Content-Type**: `application/json`
- **Hash**: `key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5|||||||salt`
- **Note**: This is the standard Easebuzz payment initiation. KhanaBook backend calls this and returns params to the Android SDK V2.

### Request
```json
{
  "key": "YOUR_MERCHANT_KEY",
  "txnid": "KB1712345678",
  "amount": "100.00",
  "productinfo": "Order #123",
  "firstname": "Rahul",
  "email": "rahul@example.com",
  "phone": "9999999999",
  "surl": "https://kbook.iadv.cloud/api/v2/payments/easebuzz/return",
  "furl": "https://kbook.iadv.cloud/api/v2/payments/easebuzz/return",
  "hash": "sha512(key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5|||||||salt)"
}
```

### Response
```json
{
  "status": 1,
  "data": "PAYMENT_LINK_OR_SDK_PARAMS"
}
```

---

## 10. Payment — Status API

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/j6v1nmy74m325-status-api
- **Endpoint**: `POST https://pay.easebuzz.in/transaction/v2.1/retrieve`
- **Content-Type**: `application/json`
- **Hash**: `key|txnid|salt`
- **Note**: KhanaBook uses this via `GET /api/v2/payments/easebuzz/status/{billId}` which proxies to Easebuzz.

### Request
```json
{
  "txnid": "KB1712345678",
  "key": "YOUR_MERCHANT_KEY",
  "hash": "sha512(key|txnid|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": {
    "txnid": "KB1712345678",
    "easebuzz_id": "E250XXXXXXXXXX",
    "status": "success",
    "amount": "100.00",
    "mode": "UPI"
  }
}
```

---

## 11. Cancel Transaction

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/vjykxhu9v65zf-cancel-transaction-api
- **Endpoint**: `POST https://pay.easebuzz.in/transaction/v1/cancel`
- **Content-Type**: `application/json`
- **Hash**: `key|txnid|amount|salt`
- **Purpose**: Cancel a pending transaction before it is captured/settled.
- **Note**: KhanaBook currently does not expose this to POS. Documented for future use (e.g., cancelling unconfirmed payments after timeout).

### Request
```json
{
  "key": "YOUR_MERCHANT_KEY",
  "txnid": "KB1712345678",
  "amount": "100.00",
  "hash": "sha512(key|txnid|amount|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": "Transaction cancelled successfully"
}
```

---

## 12. Refund API v2

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/c2ac48618b3bd-refund-api-v2
- **Endpoint**: `POST https://pay.easebuzz.in/transaction/v2/refund`
- **Content-Type**: `application/json`
- **Hash**: `key|txnid|amount|salt`
- **Note**: KhanaBook backend calls this when `POST /api/v2/payments/easebuzz/refund/{billId}` is invoked from Android POS.

### Request
```json
{
  "key": "YOUR_MERCHANT_KEY",
  "txnid": "KB1712345678",
  "amount": "50.00",
  "reason": "Customer request",
  "hash": "sha512(key|txnid|amount|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": {
    "refund_id": "REF250XXXXXXXXXX",
    "refund_amount": "50.00",
    "status": "initiated"
  }
}
```

---

## 13. Refund Status API

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/de78eba8de53c-refund-status-api
- **Endpoint**: `POST https://pay.easebuzz.in/transaction/v2/refund_status`
- **Content-Type**: `application/json`
- **Hash**: `key|txnid|refund_id|salt`
- **Purpose**: Check the current status of a refund initiated via Refund API v2.
- **Note**: KhanaBook can use this in the refund polling loop inside `OrdersScreen`.

### Request
```json
{
  "key": "YOUR_MERCHANT_KEY",
  "txnid": "KB1712345678",
  "refund_id": "REF250XXXXXXXXXX",
  "hash": "sha512(key|txnid|refund_id|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": {
    "refund_id": "REF250XXXXXXXXXX",
    "refund_amount": "50.00",
    "status": "completed",
    "refunded_at": "1712345678"
  }
}
```

---

## 14. Payout API v2

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/xsrzv37a3ksv0-payout-api-v2
- **Endpoint**: `POST https://dashboard.easebuzz.in/payout/v2/transfer`
- **Content-Type**: `application/json`
- **Hash**: `key|merchant_request_id|amount|salt`
- **Purpose**: Direct payout to a bank account or UPI ID. Can be used for instant commission payouts or emergency settlements.
- **Note**: KhanaBook does not currently use this. Future enhancement for on-demand restaurant payouts.

### Request
```json
{
  "key": "YOUR_MERCHANT_KEY",
  "merchant_request_id": "KB_PAYOUT_1712345678",
  "amount": "500.00",
  "purpose": "Restaurant settlement",
  "beneficiary_name": "Business Name",
  "beneficiary_account_number": "1234567890",
  "beneficiary_ifsc": "HDFC0000123",
  "hash": "sha512(key|merchant_request_id|amount|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": {
    "merchant_request_id": "KB_PAYOUT_1712345678",
    "status": "initiated",
    "amount": "500.00"
  }
}
```

---

## 15. On-Demand Settlement APIs

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/643f40bc5eae3-on-demand-settlement-ap-is
- **Endpoint**: `POST https://dashboard.easebuzz.in/settlement/v1/on_demand`
- **Content-Type**: `application/json`
- **Hash**: `key|merchant_request_id|amount|salt`
- **Purpose**: Request an immediate settlement of available balance instead of waiting for T+1 auto-settlement.
- **Note**: KhanaBook does not currently expose this. Can be added as a premium feature for high-volume partners.

### Request
```json
{
  "key": "YOUR_MERCHANT_KEY",
  "merchant_request_id": "KB_SETTLE_1712345678",
  "amount": "5000.00",
  "hash": "sha512(key|merchant_request_id|amount|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": {
    "merchant_request_id": "KB_SETTLE_1712345678",
    "status": "initiated",
    "estimated_credit_time": "2-4 hours"
  }
}
```

---

## Sub-Merchant Onboarding Flow

```
1. Create sub-merchant draft in local DB
       │
2. POST /merchant/v1/submerchant/create  (Create Sub-Merchant API)
       │  Official: https://docs.easebuzz.in/docs/payment-gateway/af94640eeae86-create-sub-merchant-api
       │  Returns: submerchant_id
       ▼
3. POST /submerchant/v1/generate_kyc_access_key  (Generate KYC Access Key API)
       │  Official: https://docs.easebuzz.in/docs/payment-gateway/2b48a38b084b9-generate-sub-merchant-kyc-access-key-api
       │  Returns: KYC portal URL
       ▼
4. Sub-merchant uploads KYC docs via portal
       │
5. Webhook received: {"status":"1","data":{"submerchant_id":"...","status":"True/False/Pending"}}
       │
6. POST /split/v1/create  (Create Split Label API)
       │  Official: https://docs.easebuzz.in/docs/payment-gateway/e383e3fb60ce3-create-api
       │
7. For each transaction → POST /post-split/v1/create/  (Split Create API)
       │  Official: https://docs.easebuzz.in/docs/payment-gateway/xsrzv37a3ksv0-payout-api-v2
       │
8. POST /post-split/v1/retrieve/  (Split Retrieve API)
       │  Official: https://docs.easebuzz.in/docs/payment-gateway/d333db79c5c82-retrieve-api
```

## Payment & Refund Flow

```
Customer pays via Android POS
       │
1. POST /payment/initiateLink  (Initiate API)
       │  Official: https://docs.easebuzz.in/docs/payment-gateway/q3zhxyn3v4xbv-initiate-api
       ▼
2. Customer completes payment on Easebuzz checkout
       │
3. Webhook / Polling: POST /transaction/v2.1/retrieve  (Status API)
       │  Official: https://docs.easebuzz.in/docs/payment-gateway/j6v1nmy74m325-status-api
       ▼
4. If success → trigger Post-Split Create
       │
5. If refund needed → POST /transaction/v2/refund  (Refund API v2)
       │  Official: https://docs.easebuzz.in/docs/payment-gateway/c2ac48618b3bd-refund-api-v2
       ▼
6. Poll refund status → POST /transaction/v2/refund_status  (Refund Status API)
       │  Official: https://docs.easebuzz.in/docs/payment-gateway/de78eba8de53c-refund-status-api
```

## Our Backend Endpoints

| Method | Path | Action | Underlying Easebuzz API |
|--------|------|--------|------------------------|
| `GET` | `/admin/sub-merchants` | List all | — |
| `GET` | `/admin/sub-merchants/{id}` | Get by ID | — |
| `POST` | `/admin/sub-merchants` | Create draft | — |
| `PUT` | `/admin/sub-merchants/{id}` | Update draft | — |
| `POST` | `/admin/sub-merchants/{id}/assign-id` | Manually assign sub_merchant_id | — |
| `PUT` | `/admin/sub-merchants/{id}/status` | Update status | — |
| `POST` | `/admin/sub-merchants/{id}/submit-to-easebuzz` | Create in Easebuzz | Create Sub-Merchant API |
| `POST` | `/admin/sub-merchants/{id}/update-on-easebuzz` | Update in Easebuzz | Update Sub-Merchant API |
| `POST` | `/admin/sub-merchants/{id}/kyc-access-key` | Generate KYC portal URL | Generate KYC Access Key API |
| `POST` | `/admin/sub-merchants/{id}/verify-otp` | Verify OTP | Confirm OTP / Verify OTP API |
| `POST` | `/admin/sub-merchants/{id}/resend-otp` | Resend OTP | Resend OTP API |
| `POST` | `/admin/sub-merchants/{id}/split-label` | Create split label | Create Split Label API |
| `POST` | `/admin/sub-merchants/{id}/cancel-transaction` | Cancel a pending transaction | Cancel Transaction API |
| `POST` | `/admin/sub-merchants/{id}/payout` | Initiate payout to sub-merchant | Payout API v2 |
| `POST` | `/admin/sub-merchants/{id}/split-retrieve` | Retrieve split configuration | Post-Transaction Split — Retrieve |
| `POST` | `/admin/settlements/on-demand` | Request on-demand settlement | On-Demand Settlement API |
| `POST` | `/api/v2/payments/easebuzz/create-order` | Initiate payment | Initiate API |
| `GET` | `/api/v2/payments/easebuzz/status/{billId}` | Check payment status | Status API (v2.1) |
| `POST` | `/api/v2/payments/easebuzz/refund/{billId}` | Raise refund | Refund API v2 |
| `GET` | `/api/v2/payments/easebuzz/refund-status/{billId}` | Check refund status | Refund Status API |
| `POST` | `/api/v2/payments/easebuzz/webhook` | Receive Easebuzz webhooks | All webhook events |
| `POST` | `/api/v2/payments/easebuzz/submerchant-webhook` | Receive KYC webhooks | Sub-merchant webhook |
