---
stoplight-id: aejiuiyqxuei9
---

# Webhooks
---

Webhooks are event-based triggers for HTTP-based callback functions that allow communication between systems to maintain data consistency. Whenever there is an event (usually a status update) a webhook call is automatically triggered to a configured URL such that the source system sends data (in the form of a payload) to the destination system so as to keep both the systems in sync.


There are various benefits to using webhooks:

- **Data synchronization**: When you register a URL as a webhook endpoint, Easebuzz will send near real-time notifications to that URL as events take place. This allows you to synchronize the data with other services not directly responsible for making the API request that may need to know the result of the request.


- **Monitoring**: Since Webhooks provides an additional way receiving events related to your account, you can monitor these notifications for specific events and you can then execute custom code after a specific event happens. For example, some merchants use these webhook notifications to trigger capture requests, or automatically sync their systems with the settlements done.



## Supported Events
---

Easebuzz triggers webhooks for different modules on the occurance of the following events:

Module | Supported Events | Event Name
---------|----------|----------|
Payouts (Transfer) | Funding Account Verification Status Update | FUNDING_ACCOUNT_STATUS_UPDATE
Payouts (Transfer) | Initiate Transfer | TRANSFER_INITIATED
Payouts (Transfer) | Transfer Status Update | TRANSFER_STATUS_UPDATE
Payouts (Virtual Account Balance) | Low Virtual Account Balance | LOW_BALANCE_ALERT 
InstaCollect | Transaction Credit | TRANSACTION_CREDIT
InstaCollect | Initiate Refund | REFUND_INITIATED
InstaCollect | Refund Status Update | REFUND_STATUS_UPDATE
InstaCollect | Initiate Settlement | SETTLEMENT_INITIATED
InstaCollect | Settlement Status Update | SETTLEMENT_STATUS_UPDATE
InstaCollect Orders (Dynamic QR) | Order Created | ORDER_CREATED
InstaCollect Orders (Dynamic QR) | Order Status Update | ORDER_STATUS_UPDATE
InstaCollect | Transaction Capture | TRANSACTION_CAPTURED
Sub-merchants | Sub-merchant's KYC Status Update | MERCHANT_KYC_APPROVAL

## Authorization Parameter & Hash Generation
---
All webhooks triggered from the Easebuzz system include a paramerter called `Authorization`. This field contains the hashed value of a combination of parameters from the payload which is to be generated in a specified format. The hash sequence format for each webhook may be different, and is mentioned along with the webhook payload.

For example, the <b>Hash sequence</b> for the **[InstaCollect Transaction Credit Webhook](5.3-WebhooksInstaCollect.md#transaction-credit-webhook)** is `key|unique_transaction_reference|amount|status|salt`.

> **Note:** Please refer to the table below for generating the <b>SHA-512 hash</b> for sequences inclusive of the `amount` parameter. 

Sample Transaction Amount Value | Value for **`amount`** parameter in Hash generation |
---------|----------|
2 | 2.0
2.3 | 2.3
2.34 | 2.34

---
## Related Links

* [**Funding Account Webhooks**](5.2.1-WebhooksFundingAccount.md)
* [**Payout Webhooks**](5.2-Webhooks-Payouts.md)
* [**InstaCollect Transaction Credit Webhook**](5.3-WebhooksInstaCollect.md)
* [**InstaCollect Orders Webhooks**](5.5-Webhooks-Orders.md)

---
