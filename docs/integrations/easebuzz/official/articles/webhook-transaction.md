---
stoplight-id: paw9n1qc3kuoz
---

# Transaction Webhook
---
A transaction webhook will be triggered for any change in the status of a transaction. For a complete list of all the statuses that a transaction may transition into, please **[**Click here**](https://docs.easebuzz.in/docs/payment-gateway/587zy3v064so6-what-are-webhooks#supported-events).**

> **Note:** Integrating webhooks can have a profound impact on your **transaction success rates.** Whenever a transaction moves from `pending` to `success` status, the Easebuzz system will trigger the **Transaction Webhook** to your system ensuring data consistency across both the systems.

> **Note :** The data received on the webhook URL will be in form-url-encoded format. It needs to be parsed into the JSON first and then use it accordingly.

## Responding to Webhook Notifications
---
To acknowledge receipt of a webhook, your endpoint should return a 200 HTTP status code. Easebuzz only accepts a 200 HTTP status code. If Easebuzz receives an HTTP status code other than 200 or no response within 10 seconds, we will try re-sending that notification.

---

### PG - Transaction Response
---


```X-Www-Form-Urlencoded

key=ZUUABCDIGL&
furl=https%3A%2F%2Fdashboard.easebuzz.in%2Fmerchant%2Fapi%2Fv1%2FpaymentResponse%2F&
hash=045c57bcd9ca5cb2d4215a5232be6bc1c2f9daac4a98987f654gt28u2ef640eebc49d7c37f9aba66f6b6b9b0700d2aca4eef9280a3725e76b417d5ee048f8aa631a&
mode=UPI&
surl=https%3A%2F%2Fdashboard.easebuzz.in%2Fmerchant%2Fapi%2Fv1%2FpaymentResponse%2F&
udf1=&
udf2=&
udf3=&
udf4=&
udf5=&
udf6=&
udf7=&
udf8=&
udf9=&
email=rajesh.kushwah%40easebuzz.in&
error=APPROVED+OR+COMPLETED+SUCCESSFULLY&
phone=9876543211&
txnid=KM0YB27HRK&
udf10=&
amount=1.0&
status=success&
upi_va=success%40axl&
PG_TYPE=NA&
addedon=2026-03-24+10%3A32%3A28.000000&
cardnum=&
bankcode=NA&
auth_code=&
bank_name=NA&
card_type=NA&
easepayid=E2603240VUHFQ3&
firstname=Shopify+Account&
error_code=NA&
productinfo=Transfer+amount&
service_tax=0.0&
auth_ref_num=NA&
bank_ref_num=053462897602&
cardCategory=NA&
issuing_bank=NA&
name_on_card=NA&
discount_code=NA&
error_Message=APPROVED+OR+COMPLETED+SUCCESSFULLY&
merchant_logo=NA&
response_code=00&
payment_source=Easebuzz&
service_charge=0.0&
unmappedstatus=NA&
discount_amount=0.0&
net_amount_debit=1.0&
payment_category=DEFAULT&
settlement_amount=1.0&
cancellation_reason=NA&
cash_back_percentage=50.0&
deduction_percentage=0.0

```

```JSON
{
    "key": "ZUUABCDIGL",
    "furl": "https://dashboard.easebuzz.in/merchant/api/v1/paymentResponse/",
    "hash": "045c57bcd9ca5cb2d4215a5232be6bc1c2f9daac4a98987f654gt28u2ef640eebc49d7c37f9aba66f6b6b9b0700d2aca4eef9280a3725e76b417d5ee048f8aa631a",
    "mode": "UPI",
    "surl": "https://dashboard.easebuzz.in/merchant/api/v1/paymentResponse/",
    "udf1": "",
    "udf2": "",
    "udf3": "",
    "udf4": "",
    "udf5": "",
    "udf6": "",
    "udf7": "",
    "udf8": "",
    "udf9": "",
    "email": "rajesh.kushwah@easebuzz.in",
    "error": "APPROVED OR COMPLETED SUCCESSFULLY",
    "phone": "9876543211",
    "txnid": "KM0YB27HRK",
    "udf10": "",
    "amount": "1.0",
    "status": "success",
    "upi_va": "success@axl",
    "PG_TYPE": "NA",
    "addedon": "2026-03-24 10:32:28.000000",
    "cardnum": "",
    "bankcode": "NA",
    "auth_code": "",
    "bank_name": "NA",
    "card_type": "NA",
    "easepayid": "E2603240VUHFQ3",
    "firstname": "Shopify Account",
    "error_code": "NA",
    "productinfo": "Transfer amount",
    "service_tax": "0.0",
    "auth_ref_num": "NA",
    "bank_ref_num": "053462897602",
    "cardCategory": "NA",
    "issuing_bank": "NA",
    "name_on_card": "NA",
    "discount_code": "NA",
    "error_Message": "APPROVED OR COMPLETED SUCCESSFULLY",
    "merchant_logo": "NA",
    "response_code": "00",
    "payment_source": "Easebuzz",
    "service_charge": "0.0",
    "unmappedstatus": "NA",
    "discount_amount": "0.0",
    "net_amount_debit": "1.0",
    "payment_category": "DEFAULT",
    "settlement_amount": "1.0",
    "cancellation_reason": "NA",
    "cash_back_percentage": "50.0",
    "deduction_percentage": "0.0"
}
```

If auto refund is enabled from the backend then status of the Transaction API response change according to transaction status.
The status on the bank API changes after autorefund time from 'pending' status, then we will automatically raise a refund in the system and send a response to you with 'autorefunded' status.


```java
{
  ....
  "status": "autorefunded",
  ....
}
```

<!-- ### Webhook Response For Mandate Registration ( Deprecated ):
---
```JSON
{
  "key": "ZUUGXXXXXXX",
  "furl": "http://localhost/php-kit-update/response.php",
  "hash": "f4fea6d9e0ca95a9b917289b5762b41977fb68fe6dd37831c92b5ce0476d567a4bf854dc4e4cce7080ff38a0f4bfc568aef9772a85d10ead051e30468b800000",
  "mode": "UPIAD",
  "surl": "http://localhost/php-kit-update/response.php",
  "udf1": "",
  "udf2": "",
  "udf3": "",
  "udf4": "",
  "udf5": "200.0",
  "udf6": "",
  "udf7": "",
  "udf8": "",
  "udf9": "",
  "email": "rajesh@easebuzz.in",
  "error": "APPROVED OR COMPLETED SUCCESSFULLY",
  "phone": "8000000006",
  "txnid": "202401-123456",
  "udf10": "",
  "amount": "1.0",
  "status": "success",
  "upi_va": "NA",
  "PG_TYPE": "NA",
  "addedon": "2024-08-12 07:51:26.000000",
  "cardnum": "NA",
  "bankcode": "NA",
  "bank_name": "NA",
  "card_type": "NA",
  "easepayid": "E24081XTIXX",
  "firstname": "Rajesh",
  "productinfo": "test",
  "ervice_tax": "0.0",
  "bank_ref_num": "AUTHE240812",
  "cardCategory": "NA",
  "issuing_bank": "NA",
  "name_on_card": "NA",
  "discount_code": "NA",
  "error_Message": "APPROVED OR COMPLETED SUCCESSFULLY",
  "merchant_logo": "NA",
  "mandate_status": "ACTIVE",
  "payment_source": "Easebuzz",
  "service_charge": "0.0",
  "unmappedstatus": "NA",
  "discount_amount": "0.0",
  "net_amount_debit": "0.0",
  "payment_category": "UPIDDA",
  "settlement_amount": "0.0",
  "auto_debit_auth_msg": "",
  "cancellation_reason": "NA",
  "authorization_status": "authorized",
  "cash_back_percentage": "50.0",
  "deduction_percentage": "0.0",
  "auto_debit_access_key": "j55sd77-5f88-4722-865f-08800000000008",
  "auto_debit_auth_error": "",
  "customer_authentication_id": "User001"
}

```
```Form_URL_Encoded
key=ZUUGXXXXXXX&furl=http%3A%2F%2Flocalhost%2Fphp-kit-update%2Fresponse.php&hash=f4fea6d9e0ca95a9b917289b5762b41977fb68fe6dd37831c92b5ce0476d567a4bf854dc4e4cce7080ff38a0f4bfc568aef9772a85d10ead051e30468b800000&mode=UPIAD&surl=http%3A%2F%2Flocalhost%2Fphp-kit-update%2Fresponse.php&udf1=&udf2=&udf3=&udf4=&udf5=2.0&udf6=&udf7=&udf8=&udf9=&email=rajesh.kushwaha%40easebuzz.in&error=APPROVED+OR+COMPLETED+SUCCESSFULLY&phone=8085298536&txnid=202401-123&udf10=&amount=1.0&status=success&upi_va=NA&PG_TYPE=NA&addedon=2024-08-12+07%3A51%3A26.000000&cardnum=NA&bankcode=NA&bank_name=NA&card_type=NA&easepayid=E24081209XTIA7&firstname=Rajesh&productinfo=test&service_tax=0.0&bank_ref_num=AUTHE24081209XTIA7&cardCategory=NA&issuing_bank=NA&name_on_card=NA&discount_code=NAerror_Message=APPROVED+OR+COMPLETED+SUCCESSFULLY&merchant_logo=NA&mandate_status=ACTIVE&payment_source=Easebuzz&service_charge=0.0&unmappedstatus=NA&discount_amount=0.0&net_amount_debit=0.0&payment_category=UPIDDA&settlement_amount=0.0&auto_debit_auth_msg=&cancellation_reason=NA&authorization_status=authorized&cash_back_percentage=50.0&deduction_percentage=0.0&auto_debit_access_key=5155a077-5f88-4722-865f-0882583c6cd8&auto_debit_auth_error=&customer_authentication_id=User21
```

### Webhook Response For Presentment/ Debit Request( Deprecated ):
---
``` JSON
{
  "key": "ZUUGWXXXXXX",
  "furl": "https://localhost/response.php",
  "hash": "8806626d53912bb80064874cb21f3571a104557ad0f75adc3619a6e8312dac923fbc7ca25e2b96c58ab753b88eb5ce8e6db82f18172828d3de6a2ae8667f107e",
  "mode": "UPIAD",
  "surl": "https://localhost/response.php",
  "udf1": "",
  "udf2": "",
  "udf3": "",
  "udf4": "",
  "udf5": "",
  "udf6": "",
  "udf7": "",
  "udf8": "",
  "udf9": "",
  "email": "rajes@easebuzz.in",
  "error": "APPROVED OR COMPLETED SUCCESSFULLY",
  "phone": "999999999",
  "txnid": "2136549845",
  "udf10": "",
  "amount": "20.0",
  "status": "success",
  "upi_va": "9575300000@ybl",
  "PG_TYPE": "NA",
  "addedon": "2024-08-12 07:55:05.000000",
  "cardnum": "NA",
  "bankcode": "NA",
  "bank_name": "NA",
  "card_type": "NA",
  "easepayid": "E24081209XXXX",
  "firstname": "Rajesh",
  "productinfo": "Test",
  "service_tax": "0.0",
  "bank_ref_num": "YES1F5FF8ACAF",
  "cardCategory": "NA",
  "issuing_bank": "NA",
  "name_on_card": "NA",
  "discount_code": "NA",
  "error_Message": "APPROVED OR COMPLETED SUCCESSFULLY",
  "merchant_logo": "NA",
  "payment_source": "Easebuzz",
  "service_charge": "0.0",
  "unmappedstatus": "NA",
  "discount_amount": "0.0",
  "net_amount_debit": "1.0",
  "payment_category": "UPIDDR",
  "merchant_debit_id": "User21",
  "settlement_amount": "1.0",
  "cancellation_reason": "NA",
  "cash_back_percentage": "50.0",
  "deduction_percentage": "0.0",
  "auto_debit_request_state": "success"
}
```

```Form_URL_Encoded
key=ZUUGWXXXXXX&furl=https%3A%2F%2Flocalhost%2Fresponse.php&hash=8806626d53912bb80064874cb21f3571a104557ad0f75adc3619a6e8312dac923fbc7ca25e2b96c58ab753b88eb5ce8e6db82f18172828d3de6a2ae8667f107e&mode=UPIAD&surl=https%3A%2F%2Flocalhost%2Fresponse.php&udf1=&udf2=&udf3=&udf4=&udf5=&udf6=&udf7=&udf8=&udf9=&email=rajesh%40easebuzz.in&error=APPROVED+OR+COMPLETED+SUCCESSFULLY&phone=999999999&txnid=123456&udf10=&amount=20.0&status=success&upi_va=900000000-40ybl&PG_TYPE=NA&addedon=2024-08-12+07%3A55%3A05.000000&cardnum=NA&bankcode=NA&bank_name=NA&card_type=NA&easepayid=E24081209XXXXX&firstname=Rajesh&productinfo=Test&service_tax=0.0&bank_ref_num=YES1F5B88BED67963ECE064AF&cardCategory=NA&issuing_bank=NA&name_on_card=NA&discount_code=NA&error_Message=APPROVED+OR+COMPLETED+SUCCESSFULLY&merchant_logo=NA&payment_source=Easebuzz&service_charge=0.0&unmappedstatus=NA&discount_amount=0.0&net_amount_debit=1.0&payment_category=UPIDDR&merchant_debit_id=User21&settlement_amount=1.0&cancellation_reason=NA&cash_back_percentage=50.0&deduction_percentage=0.0&auto_debit_request_state=success```
 -->


### Webhook Response For Auth Transactions:
---


```X-Www-Form-Urlencoded

key=E1FFACBFCYI&
furl=http%3A%2F%2Flocalhost%2Fpay_semless%2Fresponse.php&
hash=42361dbce48a8fb644ed2f60280f86f01f9adb354bbad82a6d36a01044ab818af05ec9925969d17f722f9a9187989523796d579ae1fb42413e4140149e751722&
mode=CC&
surl=http%3A%2F%2Flocalhost%2Fpay_semless%2Fresponse.php&
udf1=&
udf2=&
udf3=&
udf4=&
udf5=&
udf6=&
udf7=&
udf8=&
udf9=&
email=rajesh.k%40easebuzz.in&
error=Pending+for+capture&
phone=9879879879&
txnid=52e6818w9190f11f254&
udf10=&
amount=5.0&
status=pending&
upi_va=NA&
PG_TYPE=NA&
addedon=2026-03-20+10%3A34%3A30.000000&
cardnum=XXXXXXXXXXXX1000&
bankcode=NA&
auth_code=060962&
bank_name=NA&
card_type=NA&
easepayid=E2603200VN5KMY&
firstname=Rajesh&
productinfo=NCAP-Test-Txn&
service_tax=0.0&
auth_ref_num=NA&
bank_ref_num=607910076477&
cardCategory=NA&
issuing_bank=NA&
name_on_card=NA&
payment_date=2026-03-20+10%3A34%3A30&
discount_code=NA&
error_Message=Pending+for+capture&
merchant_logo=NA&
capture_status=authorized&
payment_source=Easebuzz&
service_charge=0.0&
unmappedstatus=NA&
discount_amount=0.0&
auth_expiry_date=2026-03-25+10%3A35%3A21&
net_amount_debit=5.0&
payment_category=NCAP&
settlement_amount=5.0&
cancellation_reason=NA&
cash_back_percentage=50.0&
deduction_percentage=0.0

```

```JSON
{
  "key": "E1FFACBFCYI",
  "furl": "http://localhost/pay_semless/response.php",
  "hash": "42361dbce48a8fb644ed2f60280f86f01f9adb354bbad82a6d36a01044ab818af05ec9925969d17f722f9a9187989523796d579ae1fb42413e4140149e751722",
  "mode": "CC",
  "surl": "http://localhost/pay_semless/response.php",
  "udf1": "",
  "udf2": "",
  "udf3": "",
  "udf4": "",
  "udf5": "",
  "udf6": "",
  "udf7": "",
  "udf8": "",
  "udf9": "",
  "email": "rajesh.k@easebuzz.in",
  "error": "Pending for capture",
  "phone": "9879879879",
  "txnid": "52e6818w9190f11f254",
  "udf10": "",
  "amount": "5.0",
  "status": "pending",
  "upi_va": "NA",
  "PG_TYPE": "NA",
  "addedon": "2026-03-20 10:34:30.000000",
  "cardnum": "XXXXXXXXXXXX1000",
  "bankcode": "NA",
  "auth_code": "060962",
  "bank_name": "NA",
  "card_type": "NA",
  "easepayid": "E2603200VN5KMY",
  "firstname": "Rajesh",
  "productinfo": "NCAP-Test-Txn",
  "service_tax": "0.0",
  "auth_ref_num": "NA",
  "bank_ref_num": "607910076477",
  "cardCategory": "NA",
  "issuing_bank": "NA",
  "name_on_card": "NA",
  "payment_date": "2026-03-20 10:34:30",
  "discount_code": "NA",
  "error_Message": "Pending for capture",
  "merchant_logo": "NA",
  "capture_status": "authorized",
  "payment_source": "Easebuzz",
  "service_charge": "0.0",
  "unmappedstatus": "NA",
  "discount_amount": "0.0",
  "auth_expiry_date": "2026-03-25 10:35:21",
  "net_amount_debit": "5.0",
  "payment_category": "NCAP",
  "settlement_amount": "5.0",
  "cancellation_reason": "NA",
  "cash_back_percentage": "50.0",
  "deduction_percentage": "0.0"
}
```

### Webhook Response For Capture Transactions:
---


```X-Www-Form-Urlencoded

key=E1FACBDFYI&
furl=http%3A%2F%2Flocalhost%2Fpay_semless%2Fresponse.php&
hash=dfa7ba5c09d5acc858fa9465ebbe79a0b5c613626930a5c2f63092630431cc01ee6b3951192ba50bb2274d85789049b3c0f18c8853669237520334e987f0e686&
mode=CC&
surl=http%3A%2F%2Flocalhost%2Fpay_semless%2Fresponse.php&
udf1=&
udf2=&
udf3=&
udf4=&
udf5=&
udf6=&
udf7=&
udf8=&
udf9=&
email=rajesh.k%40easebuzz.in&
error=Successful+Transaction&
phone=8764212251&
txnid=52e6818w9190f11f254&
udf10=&
amount=5.0&
status=success&
upi_va=NA&
PG_TYPE=NA&
addedon=2026-03-20+10%3A34%3A30.000000&
cardnum=XXXXXXXXXXXX1000&
bankcode=NA&
auth_code=060962&
bank_name=NA&
card_type=NA&
easepayid=E2603200VN5KMY&
firstname=Rajesh&
productinfo=NCAP-Test-txn&
service_tax=0.0&
auth_ref_num=NA&
bank_ref_num=607910076477&
cardCategory=NA&
issuing_bank=NA&
name_on_card=NA&
payment_date=2026-03-20+10%3A37%3A30&
discount_code=NA&
error_Message=Successful+Transaction&
merchant_logo=NA&
capture_status=success&
payment_source=Easebuzz&
service_charge=0.0&
unmappedstatus=NA&
discount_amount=0.0&
auth_expiry_date=2026-03-25+10%3A35%3A21&
net_amount_debit=5.0&
payment_category=NCAP&
settlement_amount=5.0&
cancellation_reason=NA&
cash_back_percentage=50.0&
deduction_percentage=0.0

```

```JSON
{
  "key": "E1FACBDFYI",
  "furl": "http://localhost/pay_semless/response.php",
  "hash": "dfa7ba5c09d5acc858fa9465ebbe79a0b5c613626930a5c2f63092630431cc01ee6b3951192ba50bb2274d85789049b3c0f18c8853669237520334e987f0e686",
  "mode": "CC",
  "surl": "http://localhost/pay_semless/response.php",
  "udf1": "",
  "udf2": "",
  "udf3": "",
  "udf4": "",
  "udf5": "",
  "udf6": "",
  "udf7": "",
  "udf8": "",
  "udf9": "",
  "email": "rajesh.k@easebuzz.in",
  "error": "Successful Transaction",
  "phone": "8764212251",
  "txnid": "52e6818w9190f11f254",
  "udf10": "",
  "amount": "5.0",
  "status": "success",
  "upi_va": "NA",
  "PG_TYPE": "NA",
  "addedon": "2026-03-20 10:34:30.000000",
  "cardnum": "XXXXXXXXXXXX1000",
  "bankcode": "NA",
  "auth_code": "060962",
  "bank_name": "NA",
  "card_type": "NA",
  "easepayid": "E2603200VN5KMY",
  "firstname": "Rajesh",
  "productinfo": "NCAP-Test-txn",
  "service_tax": "0.0",
  "auth_ref_num": "NA",
  "bank_ref_num": "607910076477",
  "cardCategory": "NA",
  "issuing_bank": "NA",
  "name_on_card": "NA",
  "payment_date": "2026-03-20 10:37:30",
  "discount_code": "NA",
  "error_Message": "Successful Transaction",
  "merchant_logo": "NA",
  "capture_status": "success",
  "payment_source": "Easebuzz",
  "service_charge": "0.0",
  "unmappedstatus": "NA",
  "discount_amount": "0.0",
  "auth_expiry_date": "2026-03-25 10:35:21",
  "net_amount_debit": "5.0",
  "payment_category": "NCAP",
  "settlement_amount": "5.0",
  "cancellation_reason": "NA",
  "cash_back_percentage": "50.0",
  "deduction_percentage": "0.0"
}
```

