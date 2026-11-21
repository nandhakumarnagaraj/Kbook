---
stoplight-id: 7aochwnjw06o1
---


# Payout Webhooks
---

Whenever a payout is initiated, a record gets created in the Easebuzz system. At the occurance of that event, the Initiate Transfer Webhook will get triggered. Thereafter, for every subsequent status that the payout transfer transitions through, the Transfer (Payout) Status Update Webhook will get triggered. The statuses through which a payout can transition are described in the next section.


## Payout Status Flow
---

The following table provides the status through which a payout transfer can go through:

Status | Description | 
-----------|----------|
**accepted** | Transfer Request is sent to the bank.
**in_process** | Bank is processing your transfer request.
**success** | Funds are successfully credited to the beneficiary account.
**failure** | Transfer Request could not be processed due to any reason.
**pending** | Transfer Request is accepted by easebuzz but yet not sent to the bank.
**rejected** | Transfer Request is rejected by the beneficiary bank. Same as failure.
**reversed** | Transfer Request was successful but the beneficiary bank made a reverse entry to your account.


The below image illustrates the different statuses that a payout can transition to. The image is depicting the flow for normal payouts not requiring approval flow.
 

![TransferFlow.png](https://stoplight.io/api/v1/projects/cHJqOjE1NDA0Mw/branches/YnI6NjM5MTQwOQ/images/%2Fassets%2Fimages%2FTransferFlow.png)


### Initiate Transfer (payout) Webhook
---

This webhook is triggered whenever a payout is intiated on the platform. This webhook only gets triggered once for each payout. Every subsequent update on the same payout will trigger the Transaction status webhook listed below.

```json

{
  "event": "TRANSFER_INITIATED",
  "data": {
    "id": "tr6ee0678a9b2740",
    "unique_request_number": "27B270394A4B47C",
    "created_at": "2022-11-07T23:53:20.415494+05:30",
    "beneficiary_id": "bene89e604fa46ada71221b76be79a72",
    "beneficiary_bank_name": "HDFC Bank",
    "beneficiary_account_name": "John Doe",
    "beneficiary_account_number": "501001812XXXXX",
    "beneficiary_account_ifsc": "HDFC0000001",
    "beneficiary_upi_handle": null,
    "unique_transaction_reference": "231100027619",
    "payment_mode": "IMPS",
    "currency": "INR",
    "amount": 1,
    "service_charge": 0,
    "gst_amount": 0,
    "service_charge_with_gst": 0,
    "narration": "",
    "status": "accepted",
    "source_virtual_account": "000119100000060417",
    "source_virtual_account_ifsc": "ICIC0000104",
    "failure_reason": "",
    "queue_on_low_balance": false,
    "transfer_date": "2022-11-07T23:53:20.427195+05:30",
    "created_by": null,
    "beneficiary_email": "demo@test.in",
    "beneficary_phone_number": "0123456789",
    "udf1": null,
    "udf2": null,
    "udf3": null,
    "udf4": null,
    "udf5": null,
    "Authorization": "4b475fe6bc3b9b782edaa6bc1be519c3f92e13bc622219375b8edd7d69009dfc9904e3f83411b3274a36667cea10ab8966e7226848fa798678c9d1e36c022705"
  }
}
```

<b>Hash Sequence:</b> `key|beneficiary_account_number|ifsc|beneficiary_upi_handle|unique_request_number|amount|unique_transaction_reference|status|salt`

> **Note:** Please check the **[Authorization Parameter & Hash Generation](5.1-WebhooksOverview.md#authorization-parameter-&-hash-generation)** for more information on hash generation.




### Transfer (payout) Status Update Webhook
---
This webhook will get triggered whenever the status of a payout is updated. The different status that a payout can go through along with the description for each status is mentioned above.


```json

{
  "event": "TRANSFER_STATUS_UPDATE",
  "data": {
    "id": "tr6ee0678a9b2740",
    "unique_request_number": "27B270394A4B47C",
    "created_at": "2022-11-07T23:53:20.415494+05:30",
    "beneficiary_id": "bene89e604fa46ada7122xxxxxxxx",
    "beneficiary_bank_name": "HDFC Bank",
    "beneficiary_account_name": "John Doe",
    "beneficiary_account_number": "501001812XXXXX",
    "beneficiary_account_ifsc": "HDFC0000001",
    "beneficiary_upi_handle": null,
    "unique_transaction_reference": "231100027619",
    "payment_mode": "IMPS",
    "currency": "INR",
    "amount": 1,
    "service_charge": 0,
    "gst_amount": 0,
    "service_charge_with_gst": 0,
    "narration": "",
    "status": "failure",
    "source_virtual_account": "000119100000060417",
    "source_virtual_account_ifsc": "ICIC0000104",
    "failure_reason": "ACCOUNT INVALID",
    "queue_on_low_balance": false,
    "transfer_date": "2022-11-07T23:53:20.427195+05:30",
    "created_by": null,
    "beneficiary_email": "demo@test.in",
    "beneficary_phone_number": "0123456789",
    "udf1": null,
    "udf2": null,
    "udf3": null,
    "udf4": null,
    "udf5": null,
    "Authorization": "b9606769b5b41ec2acad05e21492dcec1db6e28428ed32124cba21716e19fa1d651e30d8cc97671f7342cbd818cd6c35a266abd593f7d131343ec17ce96ce92c"
  }
}

```

<b>Hash Sequence:</b> `key|beneficiary_account_number|ifsc|upi_handle|unique_request_number|amount|unique_transaction_reference|status|salt`

> **Note:** Please check the **[Authorization Parameter & Hash Generation](5.1-WebhooksOverview.md#authorization-parameter-&-hash-generation)** section for more information on hash generation.

---
### Related Links

* [**Payouts**](Payout-Overview.md)
* [**Webhooks Overview**](5.1-WebhooksOverview.md)

---