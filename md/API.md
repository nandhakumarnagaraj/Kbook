# KhanaBook — API Specification & Integration Contract (API.md)

**Document Version:** 2.1.0  
**Status:** Approved Source of Truth for Internal & External APIs (Verified against Codebase Ground Truth)  
**Base URL:** `https://kbook.iadv.cloud/api/v1` (Web & Mobile Ingress)  
**Content-Type:** `application/json`  

---

## 1. Authentication & Global Conventions

### 1.1 Bearer Token Header
All authenticated endpoints require an `Authorization` header containing the JWT token issued upon login:
```http
Authorization: Bearer <jwt_token>
```

### 1.2 Multi-Tenant Isolation
The server extracts `restaurant_id` and `role` directly from the validated JWT claims. Clients are prohibited from overriding `restaurant_id` via request body or query parameters to access another tenant's data.

### 1.3 Standard Response & Error Envelopes
* **Success Envelope:**
  ```json
  {
    "success": true,
    "data": { ... },
    "meta": {
      "page": 0,
      "limit": 25,
      "total": 142
    }
  }
  ```
* **Standard Error Envelope:**
  API controller exceptions return structured error envelopes or standard Spring HTTP error responses (e.g., HTTP 403 Forbidden for surface violation):
  ```json
  {
    "status": 403,
    "error": "Forbidden",
    "message": "Staff accounts cannot sign in to web admin.",
    "path": "/api/v1/auth/login"
  }
  ```

---

## 2. Authentication Endpoints (`/auth`)

### 2.1 Password & Phone Login (`POST /auth/login`)
* **Payload:**
  ```json
  {
    "loginId": "9150677849",
    "password": "...",
    "surface": "web"
  }
  ```
  *(Note: `surface: 'web'` is mandatory for Web Admin to enforce the `SHOP_STAFF` boundary rejection).*
* **Response (Success - 200 OK):**
  ```json
  {
    "token": "eyJhbGciOi...",
    "refreshToken": "4a8e2b9c...",
    "role": "OWNER",
    "restaurantId": 1042,
    "userName": "Nandhakumar",
    "loginId": "9150677849"
  }
  ```
* **Staff Rejection Response (403 Forbidden):**
  If a user with role `SHOP_STAFF` attempts login with `surface: "web"` (or via Google SSO), the server throws `ResponseStatusException(HttpStatus.FORBIDDEN, "Staff accounts cannot sign in to web admin.")`:
  ```json
  {
    "status": 403,
    "error": "Forbidden",
    "message": "Staff accounts cannot sign in to web admin.",
    "path": "/api/v1/auth/login"
  }
  ```

### 2.2 Google SSO Login (`POST /auth/google`)
* **Payload:** `{ "idToken": "<google_id_token>" }`
* **Response:** Returns `AuthSession` with JWT and refresh token.

### 2.3 Logout & Session Invalidation (`POST /auth/logout`)
* **Payload:** `{ "refreshToken": "4a8e2b9c..." }`
* **Purpose:** Revokes the refresh token in the `refresh_tokens` database table immediately.

### 2.4 Staff Session Revocation (`POST /business/staff/:userId/revoke-sessions`)
* **Role:** `OWNER`
* **Response:** `{ "revoked": 2 }`
* **Purpose:** Terminate all active sessions of a compromised staff member.

---

## 3. Business & Menu Catalog API (`/business`)

### 3.1 Dashboard & Orders
* `GET /business/dashboard`
  * **Params:** `period` (`today`, `week`, `month`)
  * **Response:** Gross sales, net sales, order counts, average ticket size, and tax breakdowns.
* `GET /business/dashboard/trends`
  * **Response:** Hourly sales and order distribution curves.
* `GET /business/orders/page`
  * **Params:** `page`, `limit`, `status`, `paymentMode`, `search`
  * **Response:** Paginated list of completed, cancelled, or pending bills.
* `GET /business/orders/:billId`
  * **Response:** Detailed bill record including snapshot line items, tax components, and split payments.
* `POST /business/bills/:billId/void` *(Role: `OWNER`)*
  * **Payload:** `{ "reason": "Customer cancellation / wrong entry" }`
* `POST /business/bills/:billId/manual-refund` *(Role: `OWNER`)*
  * **Payload:** `{ "amount": 250.00, "refundMode": "CASH", "reason": "Dish returned" }`
* `POST /business/bills/:billId/status`
  * **Payload:** `{ "orderStatus": "COMPLETED", "statusVersion": 2 }`
* `POST /business/bills/:billId/payment-mode`
  * **Payload:** `{ "paymentMode": "SPLIT", "statusVersion": 2 }`

### 3.2 Menu Items & Categories
* `GET /business/menu`
  * **Response:** Full catalog of menu items with variants, categories, and availability.
* `POST /business/menu` *(Role: `OWNER`)*
  * **Payload:**
    ```json
    {
      "name": "Paneer Butter Masala",
      "categoryId": 8,
      "foodType": "VEG",
      "basePrice": 240.00,
      "description": "Cottage cheese cubes in rich tomato gravy",
      "isAvailable": true
    }
    ```
* `PUT /business/menu/:menuItemId` *(Role: `OWNER`)*
* `DELETE /business/menu/:menuItemId` *(Role: `OWNER`)*
* `POST /business/menu/:menuItemId/toggle-availability`
  * **Purpose:** Instant toggle that invalidates edge terminal caches.
* `POST /business/menu/:menuItemId/image` *(Multipart Form-Data)*
* `DELETE /business/menu/:menuItemId/image`
* `GET /business/menu/categories`
* `POST /business/menu/categories` `{ "name": "Desserts" }`
* `PUT /business/menu/categories/:categoryId` `{ "name": "Signature Desserts" }`
* `DELETE /business/menu/categories/:categoryId`
* `GET /business/menu/:menuItemId/variants`
* `POST /business/menu/:menuItemId/variants` `{ "variantName": "Full", "price": 320.00 }`
* `DELETE /business/menu/:menuItemId/variants/:variantId`

---

## 4. Staff & Hardware Terminal API (`/business`)

### 4.1 Staff Management (Web Admin Exclusive)
> **Surface Scope:** These endpoints are consumed exclusively by the Web Admin console (`apps/web-admin/src/app/pages/staff`). Android POS contains zero staff administration screens and only pulls active staff profiles via read-only `/sync/master/pull` for counter sign-in.

* `GET /business/staff` *(Role: `OWNER`)*
  * **Response:** Array of staff accounts (`name`, `loginId`, `phoneNumber`, `role`, `isActive`, `lastLoginAt`).
* `POST /business/staff` *(Role: `OWNER`)*
  * **Payload:**
    ```json
    {
      "name": "Karthik",
      "loginId": "karthik_pos",
      "password": "...",
      "role": "SHOP_STAFF",
      "phoneNumber": "9876543210"
    }
    ```
* `PUT /business/staff/:userId` *(Role: `OWNER`)*
* `POST /business/staff/:userId/deactivate` *(Role: `OWNER`)*
* `POST /business/staff/:userId/activate` *(Role: `OWNER`)*
* `POST /permissions/grant` *(Role: `OWNER`)*
* `POST /permissions/revoke` *(Role: `OWNER`)*

### 4.2 Terminal Provisioning & Pairing
* `GET /business/terminals`
  * **Response:** List of registered POS devices, terminal series (`A`, `B`), hardware UUIDs, and heartbeats.
* `GET /business/terminal-requests`
  * **Response:** Pending activation requests from newly installed Android POS devices.
* `POST /business/terminal-requests/:requestId/approve`
  * **Payload:** `{ "terminalSeries": "A", "terminalName": "Billing Counter 1" }`
* `POST /business/terminal-requests/:requestId/reject`
* `POST /business/terminals/:terminalId/rename`
* `POST /business/terminals/:terminalId/deactivate`
* `POST /business/terminals/:terminalId/reactivate`
* `POST /business/terminals/:terminalId/recover`

---

## 5. Inventory & Recipe Tracking API (`/inventory`)

* `GET /inventory/materials`
  * **Response:** Raw materials list with current stock levels, units (KG, LTR), and reorder thresholds.
* `POST /inventory/materials` / `PUT /inventory/materials/:id` / `DELETE /inventory/materials/:id`
* `GET /inventory/recipes/:menuItemId`
  * **Response:** Bill-of-materials recipe breakdown for a specific dish.
* `POST /inventory/recipes`
  * **Payload:** `{ "menuItemId": 42, "rawMaterialId": 12, "quantityRequired": 0.25 }`
* `DELETE /inventory/recipes/:id`
* `GET /inventory/purchase-orders`
* `POST /inventory/purchase-orders`
* `POST /inventory/purchase-orders/:id/receive` `{ "unitCost": 45.00 }`
* `POST /inventory/purchase-orders/:id/cancel`
* `POST /inventory/wastage` `{ "rawMaterialId": 12, "quantity": 1.5, "reason": "Spoiled" }`
* `POST /inventory/physical-count` `{ "rawMaterialId": 12, "countedStock": 14.2 }`
* `GET /inventory/movements/:materialId`
* `GET /inventory/variance?from=...&to=...`
* `GET /inventory/vendors` / `POST /inventory/vendors`

---

## 6. Analytics & Compliance API

### 6.1 Shift & Tax Analytics (`/analytics`)
* `GET /analytics/daily-closing?date=YYYY-MM-DD`
  * **Response:** Closed shift summary: cash collected, Easebuzz digital total, tax totals, variance.
* `GET /analytics/hourly-sales?date=YYYY-MM-DD`
  * **Response:** Array of hourly sales volumes and ticket counts.
* `GET /analytics/item-sales?from=YYYY-MM-DD&to=YYYY-MM-DD`
  * **Response:** Product mix report ranking dishes by revenue and quantity sold.
* `GET /analytics/gst-ledger?from=YYYY-MM-DD&to=YYYY-MM-DD`
  * **Response:** Tax report detailing CGST (2.5%), SGST (2.5%), taxable turnover, and exempted sales.

### 6.2 Government Compliance & Merchant Agreement
* `GET /business/lookup/fssai?fssaiNo=14-digit-number`
  * **Response:** Real-time FoSCoS portal lookup returning business name, address, category, and expiry date.
* `GET /business/lookup/gst?gstin=15-digit-gstin`
  * **Response:** Official GST portal lookup returning registered legal name and filing status.
* `GET /business/merchant-agreement`
* `POST /business/merchant-agreement`
* `GET /business/merchant-agreement/download` *(Returns signed PDF contract blob)*

---

## 7. Edge Sync Engine Contract (`/sync`)

### 7.1 Sync Push (`POST /sync/push`)
The Android POS client batches locally committed entities (`BaseSyncEntity`) and pushes them to the server:
* **Payload:**
  ```json
  {
    "deviceId": "d41d8cd98f00b204e9800998ecf8427e",
    "terminalSeries": "A",
    "bills": [ ... ],
    "billItems": [ ... ],
    "billPayments": [ ... ],
    "stockMovements": [ ... ]
  }
  ```
* **Server Logic:**
  * Validates tenant isolation (`restaurant_id`).
  * Employs monotonic counter checks (`status_version`) via `BillSyncService.protectBillState` to ensure intentional offline edits take precedence over stale state.
  * Stamps records with `server_updated_at` (epoch ms).

### 7.2 Sync Pull (`POST /sync/pull`)
* **Payload:**
  ```json
  {
    "since": 1729410000000,
    "limit": 100
  }
  ```
* **Response:** Returns delta changes for `categories`, `menuitems`, `itemvariants`, and `restaurantprofiles` modified after the specified timestamp.

---

## 8. Easebuzz Payment Webhook Integration

### 8.1 Inbound Webhook (`POST /api/v1/webhooks/easebuzz`)
Dispatched by Easebuzz upon dynamic UPI QR code completion:
```json
{
  "txnid": "KB-1042-A-2425-0042",
  "easepayid": "EP20261020104928",
  "amount": "480.00",
  "status": "success",
  "mode": "UPI",
  "hash": "d5a8b7c4f1e0..."
}
```

### 8.2 HMAC-SHA512 Signature Verification
Every inbound webhook must verify the SHA-512 cryptographic hash before crediting a bill:
```
CalculatedHash = SHA512(Key + "|" + txnid + "|" + amount + "|" + status + "|" + Salt)
```
* If `CalculatedHash !== payload.hash`, the server immediately responds with `401 Unauthorized` and records a `SecurityAuditEvent`.
* Upon successful verification, the bill status transitions to `PAID` and an FCM push notification is dispatched to counter terminals.
