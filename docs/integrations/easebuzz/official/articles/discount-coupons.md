---
stoplight-id: v85hm6u1zem5j
---

# Discount Coupons
---

- Easebuzz has end-to-end discount coupon solution which you can provide provide discount coupons to your customers on the payment page. 
- With the discount feature you get the accessibility to create and customize your coupon codes, manage validity, payment mode-wise and bank wise applicability etc.
- The coupon feature is implemented on the payment page where the coupon validity can be checked against the applicable payment mode selected by the customer and payment details entered.
- Offers can be availed instantly by the customers or can be availed in the form of cashback later.

![FLow infographic2_Discount coupon flow.jpg](https://stoplight.io/api/v1/projects/cHJqOjEyODU1Nw/images/svVWNSglHOM)


## Discount Coupon creation
---

1.	Login to Dashboard https://dashboard.easebuzz.in/merchant/dashboard
2.	Select Discount Coupons from left hand side menu.
3.	Add New Discount Coupon by clicking the button on the top right side of the screen.
4.	Enter the details in the fields specified.

![DiscountCoupon.png](https://stoplight.io/api/v1/projects/cHJqOjEyODU1Nw/images/BRvpS2f10KM)

## User Flow
---
Below workflow is explained for Coupon code created for Demo “BBDMONSOON10” which is fixed instant discount of 10% on transaction amount greater than 500 and valid upto Jul 31,2019  05:55 AM. The discount coupon code can be set for particular bank, payment mode and payment network type. 


Customizable Fields | Description | Example
---------|----------|---------
Discount Coupon Code |	Alphanumeric, DISCOUNT CODE VALUE like HDFC10, SBI15.	| BBDMONSOON10
Expiry Date	| Coupon code expiry date. |	Jul 31,2019  05:55 AM
Is Active	| if 1 then Discount Coupon code active. |	Active
Min Txn Amount	| Min Amount for coupon Validity.	| 500
Max Txn Amount	| Max Amount for coupon Validity if required. |	9000
Max Discount Amount	| Discount Code apply Maximum upto this amount. |	150
Discount Type	| Whether discount type in percentage or fix Amount discount	Percentage. | 10%
Payment Mode |	Choose modes for discount on Credit, Debit, netbanking etc.	| Credit Card
Payment Network	| Choose modes for discount on VISA, Master, Rupay, Maestro etc.	| Master
Bank 	| Choose Banks for coupon validity. |	ICICI,YES bank


## Messages and Conditions
---


Sr. No. | Scenario | Response messages
---------|----------|---------
1	| Debit Card and enter discount code (In case Coupon valid only for credit card) |	Coupon is not applicable on the selected card.
2	| Credit card and enter invalid discount code.	| Invalid coupon code applied.
3	| Credit card and enter valid discount code.	| Congratulations! Coupon code applied successfully.
4	| Transaction amount is lesser than discount amount in credit card.	|Discount cannot be applied on this amount.
5	| If Credit card details entered on Debit Card and apply coupon	Credit card detected.| Coupon applied successfully.
6	| If debit entered in credit and code entered.	| Coupon cannot be applied on this card.
7	| If credit is disabled and credit card details entered in debit card	Invalid payment mode selected (Credit Card).| Please try again using a valid payment mode.
8	| Select credit card from save card and apply valid discount code.	|Congratulations! Coupon code applied successfully.
9	| Select credit card from save card and apply invalid discount code.	|Invalid coupon code applied.
10 |	Select debit card from save card and apply discount code. |	Coupon is not applicable on this card.
11 |	Customer click on apply coupon before entering card details. |	Enter card details first.

## Update
---

Whenever Merchant wants to update Discount Coupons then click on the update button and update all above details except Discount Coupon Code. Discount Coupon Code will not editable.

## Active or Inactive
---

If Merchant wants to Active or Inactive discount coupon then click on the particular Discount coupon active/inactive button. If Discount Coupon code inactive then it will not appear in discount coupon list on Easebuzz Card Page.

---