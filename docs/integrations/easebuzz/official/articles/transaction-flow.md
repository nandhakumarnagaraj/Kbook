---
stoplight-id: y3ujtl2emd5bg
---

# Transaction Flow

---

![FLow infographic2_Transaction flow.jpg](https://stoplight.io/api/v1/projects/cHJqOjEyODU1Nw/images/zSmoDjyl3VE)

- Customers visit your website or application, place an order, and proceed to checkout.
- When the customer clicks “Checkout,” your website or e-commerce platform redirects them to the Easebuzz payment gateway.
- On the payment page, the customer selects their preferred payment method and enters the required payment details.
- The Easebuzz payment gateway securely collects the submitted payment information and forwards the transaction request to the customer's issuing bank through the acquiring bank and the relevant card network.
- The issuing bank validates the transaction by verifying the payment details, available balance, card validity, and expiry date, as applicable. It then returns an approval or decline response.
- The Easebuzz payment gateway forwards the transaction response to your website or application.
- If an "approved" response is received from issuing bank then the customer order will be placed successfully.
- The transaction result is displayed to the customer on your website or application.
- Funds will be settled to merchant settlement account for all successful transactions according on the settlement cycle.


> **Note:** Merchant Discount Rate (MDR) will be deducted from the payout, which can be flat or percentage amount of the transaction. The applicable MDR rates will be configure during merchant onboarding.

### Transaction Statuses

---

| Status            | Description                                                                                    |
| ----------------- | ---------------------------------------------------------------------------------------------- |
| **PreInitiated**     | The customer clicks the “Pay Now” button on the merchant's website, and an access key is generated for the transaction.                                                 |
| **Initiated**     | Customers who are currently on the card/payment/checkout page.                                                  |
| **Pending**       | The customer has been redirected to the bank's page, and the transaction is awaiting a response.                                                  |
| **Success**       | The transaction has been successfully completed and approved.                                                       |
| **Failure**       | The transaction has failed or been declined.                                                                |
| **Usercancelled** | Transactions cancelled by the user on easebuzz payment page.                                                            |
| **Dropped**       | The customer leaves the transaction while on the bank's page without completing the payment. |
| **Bounced**       | If the customer leaves the transaction on the card page, "access key" will be expired and the transaction is marked as "bounced" |

---
