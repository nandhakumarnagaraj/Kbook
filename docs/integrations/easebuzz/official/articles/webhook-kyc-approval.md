---
stoplight-id: ydk17g4xurhhp
---

# Submerchant KYC Approval Webhook
---

Integrating the **"Submerchant KYC Approval Webhook"** will ensure that  your system receives updates whenever the KYC of your sub-merchant is approved. 

**Hash Sequence**:`key|submerchant_id|salt`

> **Note :** The data received on the webhook URL will be in form-url-encoded format. It needs to be parsed into the JSON first and then use it accordingly.



```java
{
  "status":"1",
  "data" : {
    "hash": "eea8661654028110489069627b16d135853c8bb7c7bc7933cb00602451a40b29c78711b37d9489f61fd7c1c289329d18fd8a2e83ddff6dfc2993cb4541cd38ee",
    "name": "Dummy Customer",
    "email": "dummycustomer@test.com",
    "mobile": "1234567890",
    "status": "True",
    "submerchant_id": "0001"
  }
}

```

> **Note:** It is critical to configure **webhooks** as this will allow the Easebuzz system to 'push' data to your system whenever any event occurs, ensuring that both systems are always in sync.

## Responding to Webhook Notifications
---

To acknowledge receipt of a webhook, your endpoint should return a 200 HTTP status code. Easebuzz only accepts a 200 HTTP status code. If Easebuzz receives an HTTP status code other than 200 or no response within 10 seconds, we will try re-sending that notification.


---

