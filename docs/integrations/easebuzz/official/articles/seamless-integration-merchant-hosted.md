---
stoplight-id: k3ho860cy66zh
---

# Seamless Integration (Merchant Hosted)
---

Integrate your website, application or e-commerce store with the Easebuzz Payment Gateway. Choose the integration method that best suits your business requirements and start accepting payments from your customers.

With seamless integration, customers can complete payments without being redirected to a separate payment page for supported payment flows. Merchants can collect payment details directly on their websites, subject to applicable regulatory requirements and PCI DSS compliance obligations.
Merchants that meet the applicable PCI DSS requirements may collect and process card details on their websites or servers, subject to the relevant security standards and regulatory guidelines.

The payment interface can be customized to match the merchant's preferences, allowing the website's existing look and feel to be retained.

Payment data must be protected using the required encryption standards. For card payments, follow the AES-256 encryption guidelines described below.

### **Credit /Debit card Encryption Guidelines:**
---

1.	For debit card and credit card transactions, the following parameters must be encrypted: card_number, card_holder_name, card_cvv, card_expiry_date. 
2.	Use AES-256 encryption in CBC mode.
3.	To generate the AES-256 key, create an SHA-256 hash of your Easebuzz merchant key and use the first 32 bytes of the resulting hash.
4.	To generate the AES-256 initialization vector (IV), create an SHA-256 hash of your Easebuzz salt and use the first 16 bytes of the resulting hash.
5.	Ensure that the encrypted data is padded according to the required padding scheme so that its length is a multiple of 16 bytes.



> **Note:** Please get in touch with **pgsupport@easebuzz.in** for any queries in encryption guidelines.


## Seamless Integration Flow
---

Read the **[Go-live steps](Integration-flow.md)** before you start the integration.

### Generate Access Key
---

- Generate an access_key using the Easebuzz [**Initiate Payment**](/docs/payment-gateway/ec545c331e6f-initiate-payment-api) API. 
- To generate an access key for a seamless integration flow, pass the `request_flow` parameter with the value `SEAMLESS`.



![merch seamless.png](https://stoplight.io/api/v1/projects/cHJqOjEyODU1Nw/branches/YnI6OTg0MDU3OA/images/%2Fassets%2Fimages%2Fmerch%20seamless.png)


## Initiate Seamless Payment API
---

- Once the "access key" is generated, invoke "the Seamless Payment API" using the parameters listed below.
- Create an auto-submit HTML form containing the required parameters to submit the payment request.
- Capture the transaction response through the SURL (Success URL) or FURL (Failure URL) send in the Initiate Payment API request.
- Configure `webhooks` and integrate the `Transaction Status API` to verify the transaction status.


 
> **Note:** It is mandatory to integrate "Webhook" and "Transaction Status API" to verify the transaction status for Native OTP and UPI QR flow as the customer will not be redirected to the SURL/FURL.


Value | Description | 
---------|----------|
 **Production Endpoint** | `https://pay.easebuzz.in/initiate_seamless_payment/` | 
 **UAT Endpoint** | `https://testpay.easebuzz.in/initiate_seamless_payment/`
**Content-Type** | **application/x-www-form-urlencoded**
**Method** | **POST**

**Description**: Create an auto-submit form for API response (HTML response). 



### **Parameters:**


**Key** | **Datatype** | **Note**
---------|----------|---------
access_key | String | Generate the "access_key" with [**Initiate Payment API**](https://docs.easebuzz.in/docs/payment-gateway/8ec545c331e6f-initiate-payment-api).
payment_mode | String |  NB (Net Banking), DC (Debit Card), CC (Credit Card), MW (Mobile Wallet), UPI (UPI), OM (Ola Money), PL (Pay Later), EMI  |  
bank_code	| String | If payment_mode is one out of: [ Net Banking, Wallet] | 
card_number	| String | Required for Debit Card, Credit Card, and EMI transactions. Must be encrypted.| 
card_holder_name	| String | Required for Debit Card, Credit Card, and EMI transactions. Must be encrypted.| 
card_cvv	| String | Required for Debit Card, Credit Card, and EMI transactions. Must be encrypted.| 
card_expiry_date	| String[MM/YYYY] | Required for Debit Card, Credit Card, and EMI transactions. Must be encrypted. | 
upi_va	| String | 	If payment_mode is UPI. It is the user's VPA (UPI ID).| 
upi_qr  | String | 	true (Set to true when UPI deep linking or QR-based payment functionality is required.) | 
pay_later_app	| String | 		Value : Simpl |
request_mode | String | If payment_mode is UPI, If you want a seamless API response in JSON Format, pass the value as “SUVA”. Pass the browser details as per the request payload below and **"request_mode"** = **“S2S”** for Easebuzz to check if the card is enabled for **Native OTP flow**.
emi_object | JSON | Mandatory for EMI transaction. 


Sample value for `emi_object`

```java
{
   "principal_amount": "3102.95",
   "is_flat_rate": "0",
   "emi_amount": "1058.54",
   "total_interest": "72.68",
   "discount": "0",
   "unique_identifier": "D221229AKOH1LI_AXIS_3",
   "emi_tenure": "3",
   "description": "3 Months",
   "rate_of_interest": "14.0",
   "emi_id": "02",
   "bank_code": "AXIS"
}
```


> **Note:** After receiving a response through the SURL/FURL or webhook or status API, you must verify the response hash using the reverse hash sequence.


## UPI Deep Linking
---

You can generate a UPI deeplink via our seamless API which can be used to generate a UPI QR on your end or directly open UPI PSP apps from your payment page. Use the deeplink received in the response as-is in your integration.


**Example :**


```
'access_key': 'be4052c703866b581231232685daba5123212435fe1231239d8dae415156e',
'payment_mode': 'UPI',
'upi_qr': 'true',
'request_mode': 'SUVA'

```

**Response (For UPI Transaction with request mode parameter) :**

```json
{
  "status": true,
  "msg_desc": "Scan the QR Code on any UPI Application",
  "qr_link": "upi://pay?pa=merchant_upi@bank&pn=EASEBUZZs&mc=0000&tid=&tr=<transaction_id>&tn=Pay&am=2.0&mam=2.0&cu=INR&refUrl=https://pay.easebuzz.in ",
  "msg_title": ""
}
```

### Bank Codes  List
---

Download the list of Bank codes for various payment mode from the below give link:

- **[List of Bank Codes](https://ebz-open-docs.s3.ap-south-1.amazonaws.com/Easebuzz-PG-Bank-Codes.xlsx)**

---



                  