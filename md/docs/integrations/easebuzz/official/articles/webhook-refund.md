---
stoplight-id: y50x2qvuqc785
---

# Refund Webhook
---

Integrating the **Refund Webhook** will ensure that  your system receives updates for all **Refund** status. Whenever the status of a refund changes, your system will get a notification about that particular refund status getting updated. 

**Hash sequence:** `"key|easepayid|salt`


> **Note :** The data received on the webhook URL will be in form-url-encoded format. It needs to be parsed into the JSON first and then use it accordingly.

## Refund Events
---

Event | Description
------- | -------
**queued** | Refund raised on Easebuzz.
**accepted** | Holding done at Easebuzz.
**refunded** | Refund successfully proceed and ARN generated.

```JSON
{
"status": 1,
"data": {
  "hash": "80a2f3bd31e3bf8d499b69b031481f1744eb25dbb61d6c54dc35e609cc4ce42b61e7574767899b1b02442cf747f0620ad094c392a9f80ebc0784b167250000000000",
  "udf1": null,
  "udf2": null,
  "udf3": null,
  "udf4": null,
  "udf5": null,
  "udf6": null,
  "udf7": null,
  "txnid": "249PR101010101",
  "easepayid": "E2412270C6U",
  "refund_id": "RWK8DL",
  "arn_number": null,
  "refund_amount": 2,
  "refund_status": "queued", // Expected values:  “queued”, “accepted”, and “refunded”.
  "transaction_date": "2024-12-27 11:08:38.000000",
  "transaction_type": "UPI",
  "merchant_refund_id": "E2412270C6U-82144",
  "transaction_amount": 20,
  "refund_request_date": "2025-01-30 10:50:50.536893",
  "chargeback_description": ""
}
}
```
```Form_URL_Encoded
status=1&data=%7B%22hash%22%3A+%2280a2f3bd31e3bf8d499b69b031481f1744eb25dbb61d6c54dc35e609cc4ce42b61e7574767899b1b02442cf747f0620ad094c392a9f80ebc0784b1672510000000000000%22%2C+%22udf1%22%3A+null%2C+%22udf2%22%3A+null%2C+%22udf3%22%3A+null%2C+%22udf4%22%3A+null%2C+%22udf5%22%3A+null%2C+%22udf6%22%3A+null%2C+%22udf7%22%3A+null%2C+%22txnid%22%3A+%22249PR101010101%22%2C+%22easepayid%22%3A+%22E2412270C6U%22%2C+%22refund_id%22%3A+%22RWK8DL%22%2C+%22arn_number%22%3A+null%2C+%22refund_amount%22%3A+2.0%2C+%22refund_status%22%3A+%22queued%22%2C+%22transaction_date%22%3A+%222024-12-27+11%3A08%3A38.000000%22%2C+%22transaction_type%22%3A+%22UPI%22%2C+%22merchant_refund_id%22%3A+%22E2412270C6-17382144%22%2C+%22transaction_amount%22%3A+20.0%2C+%22refund_request_date%22%3A+%222025-01-30+10%3A50%3A50.536893%22%2C+%22chargeback_description%22%3A+%22%22%7D

```



> **Note:** It is critical to configure **webhooks** as this will allow the Easebuzz system to 'push' data to your system whenever any event occurs, ensuring that both systems are always in sync.


## Responding to Webhook Notifications
---

To acknowledge receipt of a webhook, your endpoint should return a 200 HTTP status code. Easebuzz only accepts a 200 HTTP status code. If Easebuzz receives an HTTP status code other than 200 or no response within 10 seconds, we will try re-sending that notification.



---