---
stoplight-id: 587zy3v064so6
---

# What are Webhooks ?
---

Webhooks are HTTP callbacks. Easebuzz uses Webhooks as an additional way of notifying you about payment-related events.A webhook endpoint is the URL to which you want to receive event notifications. Once you register a URL as a webhook endpoint, Easebuzz will send notifications to that URL whenever an event occurs related to your account.


> **Note:** It is critical to configure **Webhooks** as this will allow the Easebuzz system to 'push' data to your system whenever any event occurs, ensuring that both systems are always in sync.

There are several benefits to using Webhooks :

- **Data synchronization** : When you register a URL as a webhook endpoint, Easebuzz will send near real-time notifications to that URL as events take place. This allows you to synchronize the data with other services not directly responsible for making the API request that may need to know the result of the request.<br></br>

- **Device failur**e : When using Easebuzz Checkout, the result of the payment authorization is provided to your callback function, which when executed returns the payment ID to your front-end server. In the very rare event that a consumer’s internet connection drops before completing the checkout process, you will still receive a webhook notifying you of a successful payment.<br></br>

- **Monitoring** : Since Webhooks provides an additional way receiving events related to your account, you can monitor these notifications for specific events and you can then execute custom code after a specific event happens. For example, some merchants use these webhook notifications to trigger capture requests, or automatically sync their systems with the settlements done.

## How does it work ?
---

![new flow (1).png](https://stoplight.io/api/v1/projects/cHJqOjEyODU1Nw/images/t9PnWZaVOSI)


- When you send a request to Easebuzz, we processes the request and return a response containing an HTTP status code and either the new object (upon success) or an error object (upon failure).

- If Webhooks is set up, Easebuzz also creates an event object and sends it to your webhook endpoint via an HTTP POST request. The POST request will contain data (formatted as form-urlencoded) relevant to the event.

- You must acknowledge receipt of the webhook by returning a 200 HTTP status code. No response or a response other than 200 will indicate an error to Easebuzz.

- If acknowledge status doesn’t come 200 for 5 times upon retrying every 30 minutes, then Webhook call is blocked for that event. That can be reactivated by manually setting on dashboard.

> **Note :** The data received on the webhook URL will be in form-url-encoded format. It needs to be parsed into the JSON first and then use it accordingly.

## Supported Events
---

Easebuzz sends webhook notifications for the following requests:


**Webhook Type**| **Supported events** | 
---------|----------|
 **Transaction** | Success Payment, Failure Payment, User Cancelled|
  **Refund** | Refund Event |
  **Settlement** | Settlement/Payout Event | 

> **Note :** We may add webhooks for new event types at any time, so you may receive additional notifications to the ones listed here.

## Latency and Retries 
---
We create a webhook notification as soon as an event occurs and typically you will receive the notification in near real-time. However, you may experience latency receiving the notification.

There are no specific latency guarantees. Latency can occur for all types of event notifications, including notifications of payment success. It’s possible a webhook may fail (timeout) due to network latency, and in that case we retry every 30 minutes for 5 times before we block the webhook call.

While we are processing retry attempts, Easebuzz will continue to process and send webhook notifications for other payment events as they occur.  

## Integrating Webhooks
---

You can set up webhooks quite easily. Simply register the URL for the webhook endpoint in the Merchant Dashboard under ‘Account Settings’. You have the option of setting different endpoints for different webhooks(Transaction/Payout/Refund)

> **Note:** It is critical to configure **webhooks** as this will allow the Easebuzz system to 'push' data to your system whenever any event occurs, ensuring that both systems are always in sync.

![webhook.png](https://stoplight.io/api/v1/projects/cHJqOjEyODU1Nw/branches/YnI6OTg0MDU3OA/images/%2Fassets%2Fimages%2Fwebhook.png)



## Optional Configuration (Recommended)
--- 

Webhooks are asynchronous, their order is not guaranteed, and idempotency might lead to a duplicate notification of the same event type. For these reasons, you may also want to configure your system for the following:

- Webhooks are not guaranteed to be in any particular order. For example, a webhook call might be delivered after the browser response event for the same payment. You should configure your system to handle this, using, for example, a Finite State Machine or other logic.
- You may receive multiple copies of the same webhook notification. You should configure your system to ignore webhooks that you have already processed.

> While you can use Easebuzz without Webhooks, we highly recommend to use Webhooks. In the rare event that a callback does not get executed when a payment is authorized, Webhooks adds resilience. It is advisable to implement both the browser based response and Webhooks to add reliability to your system.

## Using Webhooks
---

**Receiving webhook notifications:**

Once you register a webhook endpoint, Easebuzz posts data to the URL every time an event occurs. The request will contain form-urlencoded data relevant to the event. More details about these parameters and their meaning can be found in our hyperlink this API Reference section

---











