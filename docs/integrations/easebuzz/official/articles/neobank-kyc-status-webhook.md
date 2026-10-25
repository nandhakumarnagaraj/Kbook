---
stoplight-id: q7ba7nsflsf78
---

# Sub-merchant KYC Status Webhooks
---

Easebuzz performs KYC verification and background check for each sub-merchant onboarded on to our platform, as per RBI guidelines. The complete list of documents required for each business entity getting onboarded can be found [**here**](https://docs.easebuzz.in/docs/get-started/6v6i0r0x14zly-kyc-documents).

Once the business entity uploads their KYC information, the Easebuzz team will either **approve** or **reject** the KYC of the business based on the information which has been provided. 

The following webhooks get triggered from the Easebuzz system to the pre-configured URL of the client's system as a callback in the event of an update to the KYC status of a sub-merchat entity.



### KYC Status Webhook (Completed)
---

This webhook is triggered in the event that the provided KYC information for a sub-merchant entity has been verified and **Approved** by the Easebuzz team. This webhook gets triggered only once, everytime there is a change in the status of the KYC of a sub-merchant. If the below callback is received by the client's system, it can be used as a cue that the sub-merchant's account has been activated and they may start using the services offered by the Easebuzz platform.

```json

{
  "event": "MERCHANT_KYC_APPROVAL",
  "data": {
    "id": 4900,
    "virtual_account": {
      "id": "vabb6eb90ec640689d4377d56b395d6e",
      "account_number": "1010000000xxxxxxxx",
      "balance": 0,
      "is_active": true,
      "bank_name": "ICICI Bank",
      "status": "active",
      "ifsc": "ICIC0000104"
    },
    "kyc": {},
    "commission_charges": {
      "UPI": [
        {
          "charge": 0,
          "charge_in_percent": false
        }
      ],
      "IMPS": [
        {
          "charge": 0,
          "max_txn_amount": 30,
          "min_txn_amount": 0,
          "charge_in_percent": false
        },
        {
          "charge": 0,
          "min_txn_amount": 31,
          "charge_in_percent": true
        }
      ],
      "NEFT": [
        {
          "charge": 0,
          "charge_in_percent": true
        }
      ],
      "RTGS": [
        {
          "charge": 0,
          "max_txn_amount": 500,
          "min_txn_amount": 0,
          "charge_in_percent": false
        },
        {
          "charge": 0,
          "max_txn_amount": 2000,
          "min_txn_amount": 501
        },
        {
          "charge": 0,
          "min_txn_amount": 2001
        }
      ]
    },
    "insta_primary_vpa": "20230228583241@yesbank",
    "kyc_status": true,
    "bank_status": true,
    "kyc_url": "https://kyc.easebuzz.in/83e19952c070a8a2a448b985ac642186b21d5099?access_key=6299291652203833382774936045455183579769040460381714087837006303643121802608983891946916734591612488",
    "kyc_profile_status": "Completed",
    "kyc_expiry_date": "2023-03-02 12:27:22",
    "email": "easebuzztest@gmail.com",
    "phone": "9999999999",
    "name": "KYC check"
  }
}

```


### KYC Status Webhook (Rejected)
---
This webhook is triggered whenever the KYC inforamtion provided for a sub-merchant does not get verified after necessary checks by the Easebuzz team. The callback should act as a trigger that the verification has failed. The client is expected to update the provided KYC information in this case by logging into the Easebuzz platform or through the APIs provided. They may alternatively reach out to the Relationship Manager or the Easebuzz Support team for a resolution.


```json

{
  "event": "MERCHANT_KYC_APPROVAL",
  "data": {
    "id": 4900,
    "virtual_account": {
      "id": "vabb6eb90ec640689d4377d56b395d6e",
      "account_number": "1010000000xxxxxxx",
      "balance": 0,
      "is_active": true,
      "bank_name": "ICICI Bank",
      "status": "active",
      "ifsc": "ICIC0000104"
    },
    "kyc": {},
    "commission_charges": {
      "UPI": [
        {
          "charge": 0,
          "charge_in_percent": false
        }
      ],
      "IMPS": [
        {
          "charge": 0,
          "max_txn_amount": 30,
          "min_txn_amount": 0,
          "charge_in_percent": false
        },
        {
          "charge": 0,
          "min_txn_amount": 31,
          "charge_in_percent": true
        }
      ],
      "NEFT": [
        {
          "charge": 0,
          "charge_in_percent": true
        }
      ],
      "RTGS": [
        {
          "charge": 0,
          "max_txn_amount": 500,
          "min_txn_amount": 0,
          "charge_in_percent": false
        },
        {
          "charge": 0,
          "max_txn_amount": 2000,
          "min_txn_amount": 501
        },
        {
          "charge": 0,
          "min_txn_amount": 2001
        }
      ]
    },
    "insta_primary_vpa": "20230228583241@yesbank",
    "kyc_status": false,
    "bank_status": false,
    "kyc_url": "https://kyc.easebuzz.in/83e19952c070a8a2a448b985ac642186b21d5099?access_key=1715815062622710281713768577595528709490335080598030190996513440837755111722601542365608319525300120",
    "kyc_profile_status": "Rejected",
    "kyc_expiry_date": "2023-03-02 12:25:44",
    "email": "easebuzztest@gmail.com",
    "phone": "9999999999",
    "name": "KYC check"
  }
}
```