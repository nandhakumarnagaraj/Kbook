# KhanaBook — API Specification & Integration Contract (API.md)

**Document Version:** 2.0.0  
**Status:** Approved Source of Truth for Internal & External APIs  
**Base URL:** `https://kbook.iadv.cloud/api/v1`  
**Content-Type:** `application/json`  

---

## 1. Authentication & Global Conventions

### 1.1 Bearer Token Header
All authenticated endpoints require an `Authorization` header containing the JWT token issued upon login:
```http
Authorization: Bearer <jwt_token>
```

### 1.2 Standard Success Envelope
```json
{
  "success": true,
  "data": { ... },
  "meta": {
    "page": 1,
    "limit": 25,
    "total": 128
  }
}
```

### 1.3 Standard Error Envelope
```json
{
  "success": false,
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "Phone number or password incorrect.",
    "details": {}
  }
}
```

---

## 2. Core API Endpoints

### 2.1 Authentication (`/auth`)
* `POST /auth/login`
  * **Payload:** `{ "loginId": "9150677849", "password": "..." }`
  * **Response:** `{ "token": "...", "refreshToken": "...", "role": "OWNER", "restaurantId": "...", "userName": "..." }`
* `POST /auth/refresh`
  * **Payload:** `{ "refreshToken": "..." }`
  * **Response:** Returns refreshed token.
* `POST /auth/revoke-session` *(Role: `OWNER`)*
  * **Payload:** `{ "userId": 42 }`
  * **Purpose:** Remotely invalidates a staff member's active session without disabling their account.

### 2.2 Menu Catalog (`/business/menu`)
* `GET /business/menu/items`
  * **Query Params:** `categoryId`, `search`, `page`, `limit`
  * **Response:** Array of menu items with variants, price, food type, category, and availability flags.
* `POST /business/menu/items` *(Role: `OWNER`)*
  * **Payload:**
    ```json
    {
      "name": "Chicken Biryani",
      "categoryId": 12,
      "foodType": "NON_VEG",
      "basePrice": 220.00,
      "isAvailable": true,
      "variants": [
        { "name": "Half", "price": 140.00 },
        { "name": "Full", "price": 240.00 }
      ]
    }
    ```
* `PATCH /business/menu/items/:id/availability` *(Role: `OWNER`, `MANAGER`)*
  * **Payload:** `{ "isAvailable": true }`
  * **Purpose:** Instant toggle syncing to all POS terminals.
* `PATCH /business/menu/items/:id/favourite` *(Role: `OWNER`)*
  * **Payload:** `{ "isFavourite": true }`
  * **Purpose:** Pins dish to lead the category on POS terminals.

### 2.3 Orders & Billing (`/business/orders`)
* `GET /business/orders/live`
  * **Purpose:** Real-time stream/poll of floor orders currently in preparation or open tables.
* `GET /business/orders/history`
  * **Query Params:** `startDate`, `endDate`, `terminalId`, `paymentMode`, `page`
  * **Response:** Paginated completed, cancelled, and refunded orders.
* `POST /business/orders/sync`
  * **Purpose:** Sync endpoint where Android POS batch-pushes offline order events.

### 2.4 Staff Management (`/business/staff`)
* `GET /business/staff` *(Role: `OWNER`)*
  * **Response:** List of staff members, assigned roles (`MANAGER`, `CASHIER`, `KITCHEN`), phone, and active status.
* `POST /business/staff` *(Role: `OWNER`)*
  * **Payload:** `{ "name": "Rahul", "loginId": "rahul_cashier", "password": "...", "role": "CASHIER", "phone": "9876543210" }`
* `PATCH /business/staff/:id/status` *(Role: `OWNER`)*
  * **Payload:** `{ "isActive": false }`
  * **Purpose:** Deactivates or activates a staff profile.

### 2.5 Terminals & Hardware (`/business/terminals`)
* `GET /business/terminals`
  * **Response:** List of registered hardware terminals, role, hardware UUID, and heartbeat timestamp.
* `POST /business/terminals/heartbeat`
  * **Payload:** `{ "terminalId": "term_01", "hardwareId": "...", "appVersion": "2.4.1" }`

### 2.6 Daily Closing & Shift Settlements (`/business/daily-closing`)
* `GET /business/daily-closing/current`
  * **Response:** Running shift totals: expected cash, Easebuzz digital total, order count, void count.
* `POST /business/daily-closing/close` *(Role: `OWNER`, `MANAGER`)*
  * **Payload:** `{ "actualCashCounted": 4500.00, "notes": "Shift 1 balanced" }`
  * **Response:** Sealed daily closing ledger record with discrepancy calculation.

---

## 3. External Webhook Contracts (Easebuzz Payment Aggregator)

### 3.1 Payment Webhook (`POST /api/v1/webhooks/easebuzz`)
When a customer scans a dynamic UPI QR code and completes payment, Easebuzz dispatches an instant webhook to KhanaBook:

```json
{
  "txnid": "KB-4912-TERM01-ORD-10492",
  "easepayid": "EP1928491823",
  "amount": "380.00",
  "status": "success",
  "mode": "UPI",
  "hash": "8f3a9e2c4b..."
}
```

### 3.2 HMAC Signature Verification
Every inbound webhook must verify the SHA-512 / HMAC signature before updating the order status:
```
CalculatedHash = SHA512(Key + "|" + txnid + "|" + amount + "|" + status + "|" + Salt)
```
If `CalculatedHash !== payload.hash`, the request is rejected with `401 Unauthorized` to prevent spoofed payments.

---

## 4. Rate Limiting & API Security Rules

* **Rate Limiting:** Public auth endpoints (`/auth/login`) are rate-limited to 5 requests per minute per IP to prevent brute-force attacks.
* **No Client Secrets:** Payment gateway salts and database secrets are never returned in any API response.
* **CORS Policy:** Restricted to `https://kbook.iadv.cloud` and authorized local development origins (`http://localhost:4200`).
