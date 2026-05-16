# Easebuzz Sub-Merchant API Reference

## 1. Create Sub-Merchant

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

- **Endpoint**: `POST https://dashboard.easebuzz.in/merchant/v1/submerchant/create` (same endpoint)
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

## 4. Create Split Label

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

## 5. Post-Transaction Split — Create/Update

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

## 6. Post-Transaction Split — Retrieve

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

## Sub-Merchant Onboarding Flow

```
1. Create sub-merchant draft in local DB
       │
2. POST /merchant/v1/submerchant/create  (create)
       │  Returns: submerchant_id
       ▼
3. POST /submerchant/v1/generate_kyc_access_key
       │  Returns: KYC portal URL
       ▼
4. Sub-merchant uploads KYC docs via portal
       │
5. Webhook received: {"status":"1","data":{"submerchant_id":"...","status":"True/False/Pending"}}
       │
6. POST /split/v1/create  (create split label for settlement)
       │
7. For each transaction → POST /post-split/v1/create/  (split funds)
       │
8. POST /post-split/v1/retrieve/  (verify split configuration)
```

## Our Backend Endpoints

| Method | Path | Action |
|--------|------|--------|
| `GET` | `/admin/sub-merchants` | List all |
| `GET` | `/admin/sub-merchants/{id}` | Get by ID |
| `POST` | `/admin/sub-merchants` | Create draft |
| `PUT` | `/admin/sub-merchants/{id}` | Update draft |
| `POST` | `/admin/sub-merchants/{id}/assign-id` | Manually assign sub_merchant_id |
| `PUT` | `/admin/sub-merchants/{id}/status` | Update status |
| `POST` | `/admin/sub-merchants/{id}/submit-to-easebuzz` | Create in Easebuzz |
| `POST` | `/admin/sub-merchants/{id}/update-on-easebuzz` | Update in Easebuzz |
| `POST` | `/admin/sub-merchants/{id}/kyc-access-key` | Generate KYC portal URL |
| `POST` | `/admin/sub-merchants/{id}/split-label` | Create split label |
