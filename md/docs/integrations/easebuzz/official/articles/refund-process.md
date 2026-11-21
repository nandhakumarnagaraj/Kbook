---
stoplight-id: ju9n2x8fiwwbe
---

<div style="text-align:left;">
    <h1 style="display: inline-block;">Overview</h1>
    <span style="float:right;">
        <span style="display: inline-block; background:linear-gradient(135deg,#484eff 0%,#1eefb0 94.79%);border-radius: 16px; padding: 10px 20px;background-clip: text;-webkit-background-clip: text; color: rgba(0, 0, 0, 0.2);font-size:25px;text-decoration: underline;">
          <a href="https://docs.easebuzz.in/docs/payment-gateway/c2ac48618b3bd-refund-api-v2" >Refund APIs</a>
        </span>
    </span>
</span>

---

Refunds can be initiated for successful transactions where the amount has been deducted from the customer’s account and needs to be refunded. Merchants can initiate full or partial refunds through the dashboard, process bulk refunds, or initiate refunds using APIs.

## Refund Process
---

- Refund requests can be initiated through the Easebuzz dashboard, API or via bulk upload.
- To initiate a refund through the dashboard, navigate to the `Transactions` tab and select the transaction for which you want to initiate the refund.
- Click on the **Refund** button to proceed.
- Review the transaction details and enter the refund amount. You can initiate either a full or a partial refund.
- Once the refund request is successfully initiated, its status will be marked as `Queued`.
- The Easebuzz system will review the refund details and place a hold on the merchant's payout transaction. Once the hold is placed, the refund status will change to `Approved`.
- After the refund is approved by the Easebuzz team, the refund request will be forwarded to the bank for processing.
- Once the bank successfully processes the refund and refund ARN number is received, the refund status will be marked as `Refunded`.


> **Note:** 
> * Refund will be credited to customer account maximum in 7 working days.
> * Only those refunds will be raised to the bank, if a merchant has some future transactions that Easebuzz can hold in the payout to proccess the refund.
> * VISA and Mastercard says that any refund raised on the bank can only be raised within the 180 days of transaction.



### Refund Statuses
---

Status | Description
------- | -------
**Queued** | Refund raised on Easebuzz.
**Approved** | Holding done at Easebuzz.
**Refunded** | Refund successfully proceed and ARN updated.


---