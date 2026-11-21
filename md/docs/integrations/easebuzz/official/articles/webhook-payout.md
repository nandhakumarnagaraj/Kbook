---
stoplight-id: bwc1ej4g1d490
---

# Payout Webhook
---

Integrating the **Payout Webhook** will ensure that  your system receives updates for all **settlement** transactions. Whenever the status of a payout transaction changes, your system will get a notification about that particular settlement transaction getting updated. 

> **Note :** The data received on the webhook URL will be in form-url-encoded format. It needs to be parsed into the JSON first and then use it accordingly.

**Hash sequence:** `key|payout_id|salt`

```JSON
{
"status": 1,
"data": {
  "hash": "7a0d185f04e46fedb6ce37cea16a8d99d84b63c02ac02a10078cce19b1eb1bae74a61cae297bb08f97767f6b3d801c44a38b38cd58a8884b462d778e2f460000",
  "paid_out": 1,
  "bank_name": "Kotak Mahindra Bank",
  "ifsc_code": "K00000ABC",
  "payout_id": "PT1LCHKBAB",
  "payout_date": "2025-01-30 11:50:21.712996",//The date and time (in IST) when the bank transaction ID (UTR No.) was updated in the Easebuzz system.
  "name_on_bank": "RAJESH K",
  "total_amount": 5,
  "payout_amount": 2,
  "refund_amount": 3,
  "split_payouts": [],
  "account_number": "000000000000",
  "service_tax_amount": 0,
  "bank_transaction_id": "YESBN5202501300640000000",
  "refund_transactions": [
    {
      "udf1": null,
      "udf2": null,
      "udf3": null,
      "txnid": "UEK8UDMK",
      "easepayid": "E2501300C",
      "refund_id": "R3WIORP000",
      "refund_amount": 1,
      "transaction_type": "UPI",
      "merchant_refund_id": "E2501300C-17382143",
      "transaction_amount": 5
    },
    {
      "udf1": null,
      "udf2": null,
      "udf3": null,
      "txnid": "249PRKT",
      "easepayid": "E2412270C6U",
      "refund_id": "RWK8DL3",
      "refund_amount": 2,
      "transaction_type": "UPI",
      "merchant_refund_id": "E2412270C6U-17382144",
      "transaction_amount": 20
    }
  ],
  "submerchant_payouts": [],
  "settled_transactions": [
    {
      "txnid": "UEK8UDMK",
      "easepayid": "E2501300C",
      "service_tax": 0,
      "service_charge": 0,
      "submerchant_id": null,
      "transaction_type": "UPI",
      "settlement_amount": 5,
      "split_transactions": null,
      "transaction_amount": 5
    },
    {
      "txnid": "RGhO12MK",
      "easepayid": "E250578EGDCG",
      "service_tax": 0,
      "service_charge": 0,
      "submerchant_id": null,
      "transaction_type": "UPI",
      "settlement_amount": 2,
      "split_transactions": null,
      "transaction_amount": 2
    }
  ],
  "service_charge_amount": 0
}
}
```
```Form_URL_Encoded
status=1&data=%7B%22hash%22%3A+%227a0d185f04e46fedb6ce37cea16a8d99d84b63c02ac02a10078cce19b1eb1bae74a61cae297bb08f97767f6b3d801c44a38b38cd58a8884b462d778e2f460000%22%2C+%22paid_out%22%3A+1%2C+%22bank_name%22%3A+%22Kotak+Mahindra+Bank%22%2C+%22ifsc_code%22%3A+%22KKBK0000000%22%2C+%22payout_id%22%3A+%22PT1LCHKBAB%22%2C+%22payout_date%22%3A+%222025-01-30+11%3A50%3A21.712996%22%2C+%22name_on_bank%22%3A+%22RAJESH+K%22%2C+%22total_amount%22%3A+5.0%2C+%22payout_amount%22%3A+2.0%2C+%22refund_amount%22%3A+3.0%2C+%22split_payouts%22%3A+%5B%5D%2C+%22account_number%22%3A+%220000000000%22%2C+%22service_tax_amount%22%3A+0.0%2C+%22bank_transaction_id%22%3A+%22YESBN5202501300640000000%22%2C+%22refund_transactions%22%3A+%5B%7B%22udf1%22%3A+null%2C+%22udf2%22%3A+null%2C+%22udf3%22%3A+null%2C+%22txnid%22%3A+%22UEK8UDMK%22%2C+%22easepayid%22%3A+%22E2501300C%22%2C+%22refund_id%22%3A+%22R3WIORP000%22%2C+%22refund_amount%22%3A+1.0%2C+%22transaction_type%22%3A+%22UPI%22%2C+%22merchant_refund_id%22%3A+%22E2501300C-17382143%22%2C+%22transaction_amount%22%3A+5.0%7D%2C+%7B%22udf1%22%3A+null%2C+%22udf2%22%3A+null%2C+%22udf3%22%3A+null%2C+%22txnid%22%3A+%22249PRKT%22%2C+%22easepayid%22%3A+%22E2412270C6U%22%2C+%22refund_id%22%3A+%22RWK8DL3%22%2C+%22refund_amount%22%3A+2.0%2C+%22transaction_type%22%3A+%22UPI%22%2C+%22merchant_refund_id%22%3A+%22E2412270C6U-17382144%22%2C+%22transaction_amount%22%3A+20.0%7D%5D%2C+%22submerchant_payouts%22%3A+%5B%5D%2C+%22settled_transactions%22%3A+%5B%7B%22txnid%22%3A+%22UEK8UDMK%22%2C+%22easepayid%22%3A+%22E2501300C%22%2C+%22service_tax%22%3A+0.0%2C+%22service_charge%22%3A+0.0%2C+%22submerchant_id%22%3A+null%2C+%22transaction_type%22%3A+%22UPI%22%2C+%22settlement_amount%22%3A+5.0%2C+%22split_transactions%22%3A+null%2C+%22transaction_amount%22%3A+5.0%7D%5D%2C+%22service_charge_amount%22%3A+0.0%7D
```


> **Note:** It is critical to configure **webhooks** as this will allow the Easebuzz system to 'push' data to your system whenever any event occurs, ensuring that both systems are always in sync.


## Responding to Webhook Notifications
---

To acknowledge receipt of a webhook, your endpoint should return a 200 HTTP status code. Easebuzz only accepts a 200 HTTP status code. If Easebuzz receives an HTTP status code other than 200 or no response within 10 seconds, we will try re-sending that notification.



---