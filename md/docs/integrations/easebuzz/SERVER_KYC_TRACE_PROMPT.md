# Production KYC trace — brief for a server-side CLI agent

Paste everything below the line into the agent that has shell access to the production
host (`kbook.iadv.cloud`). It is written to be answered from evidence on that host, not from
assumption.

---

## Role

You are a production support engineer tracing **one** failed API call on this host. You have a
shell on the server, read access to its logs, and read-only access to its PostgreSQL database.
You do not change code, configuration, services, or data.

## Objective

Explain why this call returned no KYC portal link, and prove it with captured evidence:

```
POST https://kbook.iadv.cloud/api/v1/restaurants/payment-config/easebuzz/kyc-access-key
(bearer token, zero-length body)
```

Response the Android app received at **2026-10-23 16:47:02 IST (11:17:02 GMT)**, HTTP 200,
`x-request-id: b6b57131`:

```json
{"kyc_url":"","error":"","status":false,"sub_merchant_id":"S280498XHD5"}
```

Three questions, in this order: **what the app sent**, **what our server sent to Easebuzz**, and
**what Easebuzz answered**. Do not skip ahead to fixes.

## Constraints — non-negotiable

1. **Read-only.** No restarts, no deploys, no `UPDATE`/`INSERT`/`DELETE`, no calls to Easebuzz
   write endpoints (`submerchant/create`, `submerchant/update`, `generate_kyc_access_key`, refunds,
   payouts, split). If a step seems to need a write, stop and report instead.
2. **Never print secret values.** Acceptable: variable *names*; a masked key (`first 4 … last 2`);
   the Easebuzz base URLs (not secret). Forbidden: `EASEBUZZ_SALT`, `EASEBUZZ_MERCHANT_KEY`,
   `JWT_SECRET`, `DB_PASSWORD`, portal `access_key` values. When in doubt, print
   `awk -F= '{print $1"="(length($2)>0?"<set>":"<unset>")}'`.
3. **Mask the portal URL** in anything you quote: keep scheme + host, drop the query string.
4. Quote commands and raw output; do not paraphrase log lines.

## Facts already established (do not re-derive)

- The sub-merchant exists in the **production** Easebuzz dashboard: `S280498XHD5`,
  id `103156`, "BIRYANIWALE ANNA", Bihar, KYC `Not Uploaded`, active `false`.
- A direct production probe of `POST https://dashboard.easebuzz.in/submerchant/v1/generate_kyc_access_key`
  with merchant `280498` credentials returned:
  - `name = "BIRYANIWALE ANNA"` → `{"status": true, "kyc_dashboard_url": "https://kyc.easebuzz.in/…?access_key=…"}`
  - `name = "Biryaniwale Anna"` → `{"status": false, "error_desc": "", "error": "Request cannot be verified. Hash mismatch."}`
  - Same `sub_merchant_id`, email `nandhakumar2536@gmail.com`, phone `9150677849`, same key.
- So the gateway is reachable and the product is enabled; the outcome depends on the **`name`
  value hashed with the request**. Easebuzz verifies against the name it stores (upper-cased).
- Our API returned `"error": ""` even though Easebuzz sent a populated `error`, because
  `error_desc` was present-but-empty and was preferred over `error`.

Your job is to confirm what *this host* actually sent in the failing call, not to re-test the
gateway.

## Steps

### 1. Identify the build that served the request

```
curl -s http://127.0.0.1:8081/actuator/info
```

Record `git.commit.id` and time. If the service is a container, also `docker compose ps` (or
`systemctl status <service>`) so we know which process answered.

### 2. Confirm the environment names (values only where safe)

```
sudo systemctl show <service> -p Environment | tr ' ' '\n' | grep -E '^EASEBUZZ' | cut -d= -f1
```

For a container: `docker compose exec <service> env | cut -d= -f1 | sort | grep EASEBUZZ`.

Then print **only** these values, which are not secret:

```
echo "$EASEBUZZ_DASHBOARD_BASE_URL"
echo "$EASEBUZZ_PAYMENT_BASE_URL"
echo "$EASEBUZZ_MERCHANT_KEY" | sed -E 's/^(....).*(..)$/\1…\2/'
```

Expected: `https://dashboard.easebuzz.in` and a key whose last two characters match `LB`. If the
dashboard URL is a `testdashboard` host, stop — that alone explains the failure, and say so.

### 3. Pull the server's own record of the call

The client logs every access-key attempt at INFO as
`Easebuzz generate_kyc_access_key request=… response=…`, with the portal URL masked.

```
journalctl -u <service> --since "2026-10-23 16:40" --until "2026-10-23 16:50" \
  | grep -F "generate_kyc_access_key"
```

Container variant: `docker compose logs --since 2026-10-23T16:40:00 --until 2026-10-23T16:50:00 <service> | grep generate_kyc_access_key`.

If nothing matched in that window, widen it by 30 minutes each way and repeat; report the window
you actually searched. Then also capture any line carrying `b6b57131`.

From the request line, quote **verbatim**: `merchant_key` (masked), `sub_merchant_id`, `name`,
`email`, `phone`, and the response body. The `hash` field must not be quoted.

### 4. Compare with what the database holds

```
psql "$DB_URL" -c "select id, restaurant_id, sub_merchant_id, business_name, legal_entity_name,
       contact_email, contact_phone, kyc_portal_url is not null as has_portal_url, status, kyc_status
  from easebuzz_sub_merchant
 where sub_merchant_id = 'S280498XHD5';"
```

Quote `business_name`, `contact_email`, `contact_phone`. Truncate `kyc_portal_url` to scheme+host.

### 5. Answer the three questions

Write a single table, no prose inside it:

| Hop | Field | Value observed |
| --- | --- | --- |
| App → our API | method / path / body | |
| App → our API | timestamp / request id | |
| Our server → Easebuzz | endpoint | |
| Our server → Easebuzz | name (exact characters) | |
| Our server → Easebuzz | email / phone | |
| Our server → Easebuzz | HTTP status | |
| Easebuzz → our server | raw body | |
| Our server → app | raw body | |

Then answer exactly:

1. **Did our server send the name `BIRYANIWALE ANNA`, exactly, including casing and spacing?**
   Compare character by character with the dashboard value. Report `match` or `differs: "<sent>"`
   with an explicit note of what differs (case, trailing space, punctuation, legal suffix).
2. **Did the email and phone match what the sub-merchant was registered with?**
3. **What did Easebuzz answer, and did our API preserve that reason?** If our API returned
   `"error": ""` while Easebuzz returned a message, say which key held the message.

### 6. Verdict and one next action

Conclude with one of:

- `HASH_INPUT_MISMATCH` — the gateway rejected the hash because a hashed field differs from what
  it stores. Name the field and the exact difference.
- `CREDENTIALS_OR_HOST_MISMATCH` — key/salt/base URL do not belong to the production account.
- `GATEWAY_DISABLED` — Easebuzz refused for a product/entitlement reason; quote its words.
- `NOT_REPRODUCED` — the log shows a successful call in that window; then say which request the
  app's failure actually hit.

Then give **one** next action, stated as an instruction someone can execute without reading the
logs themselves, and state what evidence would confirm it worked.

## Do not

- Do not re-run `generate_kyc_access_key` to "see what happens" — that is a write, and it was
  already probed from outside the server.
- Do not edit `application*.properties`, `.env`, or the database.
- Do not summarise away the exact `name` string; whitespace and case are the whole question.
- If a command is unavailable on this host, say so and continue with the rest rather than
  inventing its output.
