---
stoplight-id: itvel6jh8cslv
---

# Transfers
---
Make transfers to your [**contacts**](2.2.2-Contact&BeneficiaryAddition.md), using Easebuzz transfer feature. 
A transfer is done into the [**beneficiary account**](2.2.2-Contact&BeneficiaryAddition.md) which is created for any contact.

When you make a transfer to the beneficiary account, amount is deducted from your Easebuzz [**virtual account**](Virtual-Account.md) or [**current account**](Current-Account.md).

## Transfer flow
---

Transfer funds to beneficiary account in few simple steps:

![PAYOUT.png](https://stoplight.io/api/v1/projects/cHJqOjE1NDA0Mw/branches/YnI6NjM5MTQwOQ/images/%2Fassets%2Fimages%2FPAYOUT.png)


## Transfer modes
---

**Mode** | **Amount Limit** | **Processing time** | 
---------|----------|---------
 **IMPS** | 1 Lakh | Instant (24 X 7 hrs)
 **NEFT** | Any amount | 2 hrs (Only working hrs)
 **RTGS** | Above 2 Lakhs | 30 mins (Only working hrs)
 **UPI**  | 1 Lakh | Instant (24 X 7 hrs)

## Transfer statuses & flow
--- 

![TransferFlow.png](https://stoplight.io/api/v1/projects/cHJqOjE1NDA0Mw/branches/YnI6NjM5MTQwOQ/images/%2Fassets%2Fimages%2FTransferFlow.png)

Status | Description | 
---------|----------|
 **Accepted** | Transfer request is sent to bank. |
 **Cancelled** | Transfer request was cancelled by user while it was in unapproved status. |
 **In Process** | Bank is processing your transfer request. | 
  **Success** | Money is successfully credited to beneficiary account. |
 **Failure**| 	Transfer request could not be processed due to some reason. |
 **Pending** | 	Transfer request is accepted by Easebuzz but yet not sent to bank. | 
  **Rejected** | Transfer request is rejected by beneficiary bank. Same as failure. Request can also be rejected by the checker. (Please check narration for more details). | 
  **Reversed** | Transfer request was successful but beneficiary bank made a reverse entry to your account. |
 **Unapproved**| Transfer request is in approval flow. |




---

### Related Information
* **[Account Types](Account-Types.md)**
* **[Beneficiary Transfers](2.2-Wire.md)**
* **[Instant Transfers](Instant-Transfers.md)**
* **[Scheduled Transfers](Scheduled-Transfers.md)**

---

