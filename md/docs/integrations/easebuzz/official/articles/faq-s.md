---
stoplight-id: 5f7sz5pcehz6i
---

# FAQ's
---


Question 1
---------|
**How can I get the test `Key` and `Salt` details?** | 
 `Key` and `Salt` details are sent over your email ID registered with Easebuzz. You can find it with **"Subject line: Easebuzz Payment Gateway Credentials"** .  In case you do not receive the `Key` & `Salt` details over email, you can connect with the respective RM/ Sales team.  | 


Question 2
---------|
 **What causes a `Hash` mismatch?** | 
 You will get the errro `Hash` mismatch when the values sent in parameters and the values used in generating the hash are different.  | 


Question 3
---------|
 **How do we resolve an "Invalid merchant `Key`" error?** | 
 You can check if the correct `Key` or `Salt` is used for the corresponding API URL.  There could be a possibility that you are using the `Key` or `Salt` of Sandbox on Production API URL and vice versa.  | 


 Question 4
---------|
 **Are special characters not allowed in certain fields?** | 
 Yes, for certan fields, the special characters are not allowed. You can refer to the **[Easebuzz Docs](https://docs.easebuzz.in/docs/payment-gateway/8ec545c331e6f-initiate-payment-api)**.  | 


 Question 5
---------|
 **Why are we getting the error "Split payment not set properly" ?** | 
**Majorly 2 issues**: <br> </br> 1. The format in which the split details are passed could be incorrect. Please ensure you are passing the correct details.
2. The Split payment is accepted only on the basis of amount and not in percentage. Please verify if the amount to be split is passed and not the percentage value.  | 


 Question 6
---------|
 **How do we fix cross-origin issues in an iframe?** | 
 To handle cross origin errors,  you need to include specific headers in your server-side response. The key headers to add are Access-Control-Allow-Origin, which specifies which origins are permitted to access the resource, and Access-Control-Allow-Methods, which defines the `HTTP` methods allowed for cross-origin requests. Additionally, you may need to include Access-Control-Allow-Headers to specify which headers can be used in the request. Properly configuring these headers ensures that your server can handle cross-origin requests securely and effectively.  | 


 Question 7
---------|
 **Why am I getting the error "You are not authorised to access this resource"?** | 
 You need to check with the Easebuzz support team (**pgsupport@easebuzz.in**) to verify if the Seamless and Enach/Autopay is enabled on Easebuzz side.  | 



 Question 8
---------|
 **What is the correct format for `expiry_date` in EasyCollect?** | 
 Please refer to the sample expiry date format below in EasyCollect APIs: **`20-01-2024 09:10:55 PM` `DD-MM-YYYY HH:MM:SS`**  | 


 Question 9
---------|
 **Why am I getting "Duplicate merchant transaction ID" error?** | 
 For every transaction request, Easebuzz expects a unique merchant transaction ID from your end. Hence , in case the transaction ID is repeated, Easebuzz will send this error in response.  | 


 Question 10
---------|
 **Where will I find the details regarding webhook format and sample response?** | 
 You can refer to the **[Easebuzz Docs](9.5-Webhooks.md)** for the required webhook format.   | 



 Question 11
---------|
 **For Easebuzz Hosted Checkout Integration, what are the URLs for sandbox and production environments?** | 
**Sandbox:** **https://testpay.easebuzz.in/pay/**
**Production:** **https://pay.easebuzz.in/pay/** |



 Question 12
---------|
 **Will credentials differ between live and test environments?** | 
 Yes, There will be different URLs & Credentials (`Key` & `Salt`) for sandbox and production environments.  | 



 Question 13
---------|
 **Can the sequence of Payment mode be customized?** | 
 No, the sequence will remain consistent across for merchants on Easebuzz Hosted Checkout.  | 



 Question 14
---------|
 **What should the `card_details` response look like?** | 
 In the response for `Card_Details` , the below additional details will be passed: `card_brand` , `card_type`, `card_level`, `is_corporate`, `is_international`. These are not enabled by default, kindly connect with respective RM in case these details are required in the response.  | 


 ```java

"card_details": {
        "bank_name": "AXIS BANK LIMITED",
        "card_type": "credit",
        "card_level": "signature",
        "card_brand": "VISA",
        "is_corporate": "false",
        "is_international": "false"
      }
```      


 Question 15
---------|
 **How is on-demand settlement handled?** | 
 For merchants enabled with On demand settlement, merchant needs to implement and call our on demand Settlement API with transaction details basis which settlement is triggered from Easebuzz side for the transaction amount.  | 



 Question 16
---------|
 **How do we reverse a `Hash` value?** | 
 For the `hash` value received in the response from Easebuzz, the validation needs to be done by creating a `hash` with the values received in Easebuzz response and matched with the hash received in Response.  | 




 Question 17
---------|
 **Why are we getting "Request Invalid for the merchant"?** | 
 Easebuzz returns this response when the requests come from non-whitelisted IPs. Verify that you're sending the request from the IP address that was provided to Easebuzz for whitelisting. If you've recently changed your server or IP address, you'll need to update this information with Easebuzz. Contact the Easebuzz support team or your assigned Relationship Manager to verify and update the whitelisted IP addresses for your account. | 



 Question 18
---------|
 **Why I am getting `WC0E01` error code after sending the Initiate Payment API request?** | 
 Our Initiaite Payment API supports only `form-url-encoded-format` in the request, hence in case you are passing the request in `JSON` format, you will receive this error. Request you to correct the format and retry.  | 



  Question 19
---------|
 **Do you support multi-lingual parameters?** | 
 Yes, we support multi-lingual parameters.  Please reach out to our support team or your respective RM for more details. | 



 Question 20
---------|
 **Does Easebuzz support acceptance of International cards?** | 
 Yes, we support and process international card transaction. This is enabled basis the request from merchant. You can connect with the assigned RM for more details.  | 



  Question 21
---------|
 **What should be done when active "auto debit authorization is not found" ?** | 
 If the mandate is not active, you will receive this error. Kindly try once the mandate is in active state.  | 


  Question 22
---------|
 **Why I am getting `RERR02-V2` error code after sending Refund request?** | 
 To initiate multiple refund requests for the same transaction, there should be interval of 60 seconds between the refund requests for the same transaction.  | 


  Question 23
---------|
 **Can I pass the currency and amount other than INR in the Initiate payment API request while initiating a transaction?** | 
  The merchant needs to pass the amount in INR only. There is no separate parameter to specify the currency. Based on the card bin details, the user will be redirected to the international payment page. There, they can choose the currency and complete the payment. Also, the amount will be received in the INR only in response.  | 

Question 24
---------|
 **What is Third Party Validation (TPV)?** | 
 Third Party Validation (TPV) is essentially a process of validating customer’s Bank account information by an external organization. TPV plays an important role in securing transactions made in the BFSI Sector (Banking, Financial Services and Insurance). Merchants in securities, broking and mutual funds operating in the BFSI sector are prone to cases of cybercrimes, asset appropriation, identity thefts, money laundering, accounting frauds etc. When the customer transacts with the merchant (with TPV feature enabled), his bank account number is mapped in such a way so as to lock the transaction to ensure that the payment is processed only from his registered bank account.   | 
 

 Question 25
---------|
 **In how many ways can I consume the transaction response for any transaction?** | 
  **Real-time response**: The transaction response will be received on surl/furl in case of web integratio. In the mobile SDK integration, the response will be received in the application and not on the provided surl/furl. Additionally, for iFrame checkout, the response will be provided in the on-response function of iframe code.
  **Transaction status API**: You can check the transaction status using "**[Transaction API](https://docs.easebuzz.in/docs/payment-gateway/ws7va0ivtpb08-transaction-status-ap-is)**"
  **Transaction Webhook**: You can check the transaction status using "**[Transaction Webhook](https://docs.easebuzz.in/docs/payment-gateway/587zy3v064so6-what-are-webhooks)**" |
  
 

  Question 26
---------|
 **What value should I pass for surl/furl if I am using the Easebuzz mobile SDK?**
 surl/furl are mandatory parameters for the Initiate payment API call. You can pass your domain URL in the request for the surl/furl parameter. You will receive the response on in-app only. |

Question 27
---------|
**What is the expiry time for access key?**
The access key is valid for 15 mins. We cannot extend the time limit but can reduce it up to 3 mins.|


Question 28
---------|
**What is the minimum autorefund time that we can set?**
The minimum autorefund time that can be configured is 10 mins. |

Question 29
---------|
**Do I need PCI-DSS certificate for Seamless PG Integration?**
Yes. As per RBI guidelines, and PCI-DSS, merchant who are PCI-DSS compliant can collect/process the card details on their website/server. |


 ---

