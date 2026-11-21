---
stoplight-id: zdzd4lnxr28sb
---


<div style="text-align:left;">
    <h1 style="display: inline-block;">Overview</h1>
    <span style="float:right;">
        <span style="display: inline-block; background:linear-gradient(135deg,#484eff 0%,#1eefb0 94.79%);border-radius: 16px; padding: 10px 20px;background-clip: text;-webkit-background-clip: text; color: rgba(0, 0, 0, 0.2);font-size:25px;text-decoration: underline;">
          <a href="https://docs.easebuzz.in/docs/payment-gateway/xsrzv37a3ksv0-payout-api-v2" >Settlement APIs</a>
        </span>
    </span>
</span>

---

The settlement process entails the deposit of funds received from your customers into your bank account. Captured payments are automatically settled to the bank account submitted to us as part of your KYC verification as per your settlement cycle. 

The time of settlement may vary. The settlement can be done, once a day at a fixed time, multiple times a day with a fixed number of settlements or on-demand settlement.

## Settlement Process
---

- Your customer initiates the payment on the checkout page with desired payment mode to buy goods and services.
- If customer details and payment details are authenticated by the bank, then said amount is debited from the customer’s bank account.
- If the customer does not have enough funds in his account, or payment information was wrongly provided, then the online payment gets automatically rejected.
- Once payment is completed, Easebuzz will receive the funds for all successful payments.
- Now, Easebuzz will settle the funds for all successful transaction to the merchant account.
- Generally, settlement takes T+1, where T is the day of transaction success. But this period can vary based on the payment settlement mechanism and settlement cycle.

> **Note:** Merchant Discount Rate (MDR) will be deducted from the merchant payout/settlement which can be flat or percentage amount of the transaction. MDR rates will be configured during the merchant onboarding.

## Settlement Types
---

We have different types of settlements available for you:

- Normal settlement
- T+0 or Same day settlement
- Instant settlement
- T+1 Settlement (Single file settlement)
- On-Demand Settlement

### Normal settlement
---

Normal settlement process takes a time of **T+1** or **T+2** business days for domestic transactions, where T being the date of capture of payment. All the successful transactions and unsettled transactions will be settled to merchant. Normal settlement is done only after Easebuzz receives the settlement funds from the Bank. 

Normal settlement process is executed only on bank working days and not on bank holiday. 

### T+0 or Same day settlement
---

T+0 settlement takes place multiple times a day in different parts which includes all unsettled and successful transactions. T+0 settlement is done in series of batches.

Transactions which are not settled in T+0 settlement cycle will follow the normal settlement cycle which means funds will be settled to the merchant once received from bank based on the payment mode. 

T+0 settlement is executed on bank holidays as well, but with a lesser frequency in comparison with bank working days. 

To enable T+0 settlement, please call your respective sales manager.

### Instant settlement
---

Instant Settlement feature allows you to settle your current payout to your settlement account instantly 24x7 for a small fee with a single click. All the success and unsettled transactions will be settled to merchant account regardless of the payment method used. You can either settle your payout in full or choose to settle a portion of it as per your needs.


![image (7).png](https://stoplight.io/api/v1/projects/cHJqOjEyODU1Nw/images/jHizXz2DkbQ)

- To raise a request for instant fund settlement to your settlement account, login on Easebuzz merchant dashboard.
- Click on **“Instant Request”** button under **"Expected Settlement"** tab.
- Claim on **"Claim Amount"** for request confirmation.
- Based on the request - All unsettled and successful transactions get settled.
- Instant settlement includes only current day’s unsettled and successful transactions. 
- Instant settlement request is executed within 15-20 mins.
- Once instant settlement is processed, funds will be settled to your settlement account registered with Easebuzz.
- If any instant settlement request is in queue, you cannot initiate another settlement request.

Check the status of all the instant settlement request.  


To enable Instant settlement, please call your respective sales manager.

### T+1 Settlement (Single file settlement)
---

- T+1 settlement is also called as single file settlement. 
- All previous day unsettled and success transactions are considered in T+1 settlement cycle. 
- T+1 settlement is made at once, and only one settlement file is sent to you. 
- If you have enabled T+1 settlement then all the transactions performed on bank holidays will be settled on the next bank working day in your settlement account. 
- T+1 settlement is only done only on **BANK WORKING DAYS**!

### On-demand Settlement 
---
On-demand Settlement feature enables the merchant to request the settlement of the transaction through APIs. Transactions will not be settled by Easebuzz untill Settlement request is received from the merchant. If Settlement request is not received within 45 days after the transaction, funds will be automatically settled to merchant settlement account. 

Refer **[On-demand Settlement APIs](https://docs.easebuzz.in/docs/payment-gateway/643f40bc5eae3-on-demand-settlement-ap-is)**.


---

