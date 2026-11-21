{
    "openapi": "3.0.0",
    "x-stoplight": {
        "id": "ws7va0ivtpb08"
    },
    "info": {
        "version": "1.0.0",
        "title": "Transaction Status APIs",
        "description": "### Transaction Statuses\n---\n\nTransaction statuses and their respective description received from the Easebuzz side in response:\n\nStatus | Description\n------- | -------\n**preInitiated** |\tWhen customers click on **\"Pay Now\"** button on merchant page (Access Key generated).\n**initiated** | Customers who are currently on the card page.\n**pending** | Customers who are currently on the bank page.\n**success** | Transactions which have been successful.\n**failure** | Transactions which have failed.\n**userCancelled** | Transactions cancelled by the user.\n**dropped** | If the customer leaves the transaction on the bank page, the transaction is marked as dropped.\n**bounced** | If the customer leaves the transaction on the card page, the transaction is marked as bounced.\n"
    },
    "servers": [
        {
            "url": "https://testdashboard.easebuzz.in",
            "description": "Sandbox URL"
        },
        {
            "url": "https://dashboard.easebuzz.in",
            "description": "Production URL"
        }
    ],
    "paths": {
        "/transaction/v1/retrieve": {
            "post": {
                "summary": "Transaction API V1",
                "description": "Easebuzz provides an API to fetch all the information related to a particular transaction by providing the transaction id. \n\nIf the auto refund is enabled, then the status of the transaction API response changes according to transaction status. Once the status is updated from the bank side, we will automatically raise a refund in the system and send the response with the status marked as 'auto refunded'.\n\n> **Note:** It is critical to configure [**webhooks**](https://docs.easebuzz.in/docs/payment-gateway/587zy3v064so6-what-are-webhooks) as this will allow the Easebuzz system to 'push' data to your system whenever any event occurs, ensuring that both systems are always in sync.\n<br /><br />\n**Hash sequence:** \n```\nkey|txnid|amount|email|phone|salt\n```",
                "requestBody": {
                    "description": "",
                    "content": {
                        "application/x-www-form-urlencoded": {
                            "schema": {
                                "type": "object",
                                "properties": {
                                    "txnid": {
                                        "type": "string",
                                        "description": "Unique Transaction Id set on Merchant’s End for every transaction."
                                    },
                                    "key": {
                                        "type": "string",
                                        "description": "Unique Merchant Key provided by Easebuzz for your merchant account."
                                    },
                                    "amount": {
                                        "type": "number",
                                        "description": "The payment amount of the particular transaction. Note: Please type-cast the amount to float type.",
                                        "format": "float"
                                    },
                                    "email": {
                                        "type": "string",
                                        "description": "Email of the customer."
                                    },
                                    "phone": {
                                        "type": "string",
                                        "description": "Phone number of the customer."
                                    },
                                    "hash": {
                                        "type": "string",
                                        "description": "This is sha512 encrypted string. And hash sequence is mentioned above.\nHash sequence: `key|txnid|amount|email|phone|salt`"
                                    }
                                },
                                "required": [
                                    "txnid",
                                    "key",
                                    "amount",
                                    "email",
                                    "phone",
                                    "hash"
                                ],
                                "x-examples": {
                                    "Example 1": {
                                        "txnid": "LXXXXXXXX",
                                        "key": "4XXXXXXX",
                                        "amount": 100,
                                        "email": "surXXXXXXXXX@gmail.com",
                                        "phone": "9XXXXXXXXX",
                                        "hash": "93502e75846d38831fad9d79cd8caedd1454482376d9b042d82a2157d805c0c0ac5972f367f81e43c170ef73fff9a90b1fec81ab4909457857713ffe63ce6cc6"
                                    }
                                }
                            },
                            "examples": {
                                "Example 1": {
                                    "value": {
                                        "txnid": "LXXXXXXXX",
                                        "key": "4XXXXXXX",
                                        "amount": 100,
                                        "email": "surXXXXXXXXX@gmail.com",
                                        "phone": "9XXXXXXXXX",
                                        "hash": "93502e75846d38831fad9d79cd8caedd1454482376d9b042d82a2157d805c0c0ac5972f367f81e43c170ef73fff9a90b1fec81ab4909457857713ffe63ce6cc6"
                                    }
                                }
                            }
                        }
                    }
                },
                "responses": {
                    "200": {
                        "description": "",
                        "content": {
                            "application/json": {
                                "schema": {
                                    "type": "object",
                                    "x-examples": {
                                        "example-1": {
                                            "status": true,
                                            "msg": {
                                                "txnid": "EBZTestTxn0001",
                                                "firstname": "Sagar",
                                                "email": "sagar.sawant@easebuzz.in",
                                                "phone": "8805596828",
                                                "key": "D1K8SLB2XW",
                                                "mode": "UPI",
                                                "unmappedstatus": "NA",
                                                "cardCategory": "NA",
                                                "addedon": "2022-08-23 08:06:41",
                                                "payment_source": "Easebuzz",
                                                "PG_TYPE": "NA",
                                                "bank_ref_num": "223542981037",
                                                "bankcode": "NA",
                                                "error": "APPROVED OR COMPLETED SUCCESSFULLY",
                                                "error_Message": "APPROVED OR COMPLETED SUCCESSFULLY",
                                                "name_on_card": "NA",
                                                "upi_va": "8805596828@ybl",
                                                "cardnum": "NA",
                                                "issuing_bank": "NA",
                                                "easepayid": "E2208231A0ZMRS",
                                                "amount": "2.0",
                                                "net_amount_debit": "2.0",
                                                "cash_back_percentage": "50.0",
                                                "deduction_percentage": "0.0",
                                                "merchant_logo": "NA",
                                                "surl": "http://localhost:3000/response",
                                                "furl": "http://localhost:3000/response",
                                                "productinfo": "test product info",
                                                "udf10": "",
                                                "udf9": "",
                                                "udf8": "",
                                                "udf7": "",
                                                "udf6": "",
                                                "udf5": "",
                                                "udf4": "",
                                                "udf3": "",
                                                "udf2": "",
                                                "udf1": "",
                                                "card_type": "UPI",
                                                "hash": "7c36ca4836a6b4af19e4966f2af90bb6c05a235ec35840662deb9f6302b38eab6d1d75e8401cacef7647ed4e9e9807b58ffca00ee9c61e4fdbbcb5e49a8563d4",
                                                "status": "success",
                                                "bank_name": "NA"
                                            }
                                        }
                                    },
                                    "properties": {
                                        "status": {
                                            "type": "boolean"
                                        },
                                        "msg": {
                                            "type": "object",
                                            "properties": {
                                                "txnid": {
                                                    "type": "string",
                                                    "description": "The value of the transaction ID that you passed during the initiation of the payment."
                                                },
                                                "firstname": {
                                                    "type": "string",
                                                    "description": "The value of the customer’s name."
                                                },
                                                "email": {
                                                    "type": "string",
                                                    "description": "The email address of the customer"
                                                },
                                                "phone": {
                                                    "type": "string",
                                                    "description": "The phone number of the customer."
                                                },
                                                "key": {
                                                    "type": "string",
                                                    "description": "The merchant’s key used to intiate the transaction"
                                                },
                                                "mode": {
                                                    "type": "string",
                                                    "description": "The payment mode used for the transaction (e.g., UPI, CC, DC, NB)."
                                                },
                                                "unmappedstatus": {
                                                    "type": "string",
                                                    "description": "NA"
                                                },
                                                "cardCategory": {
                                                    "type": "string",
                                                    "description": "The UPI credit card used for the transaction (e.g., UPI CC transaction, or NA if not applicable)."
                                                },
                                                "addedon": {
                                                    "type": "string",
                                                    "description": "The date and time in UTC format when the transaction was initiated."
                                                },
                                                "payment_source": {
                                                    "type": "string",
                                                    "description": "The source of the payment, it will be the default value as 'Easebuzz'."
                                                },
                                                "PG_TYPE": {
                                                    "type": "string",
                                                    "description": "NA"
                                                },
                                                "bank_ref_num": {
                                                    "type": "string",
                                                    "description": "The reference number provided by the bank for the transaction."
                                                },
                                                "bankcode": {
                                                    "type": "string",
                                                    "description": "The Net bank code associated with the transaction if the user complete the transaction using NB applicable only for NB."
                                                },
                                                "error": {
                                                    "type": "string",
                                                    "description": "The error description of the transaction, typically indicating success or failure reason."
                                                },
                                                "error_Message": {
                                                    "type": "string",
                                                    "description": "The error description of the transaction, typically indicating success or failure reason."
                                                },
                                                "name_on_card": {
                                                    "type": "string",
                                                    "description": "The name on the card provided by the user on the payment page. (you will receive the NA, if the payment mode is non-card)"
                                                },
                                                "upi_va": {
                                                    "type": "string",
                                                    "description": "The UPI Virtual Address (VPA) used for the transaction for UPI mode."
                                                },
                                                "cardnum": {
                                                    "type": "string",
                                                    "description": "The last 4 digits of the card number (you will receive the NA, if the payment mode is non-card)"
                                                },
                                                "issuing_bank": {
                                                    "type": "string",
                                                    "description": "NA"
                                                },
                                                "easepayid": {
                                                    "type": "string",
                                                    "description": "The unique easebuzz ID assigned to the transaction by Easebuzz."
                                                },
                                                "amount": {
                                                    "type": "string",
                                                    "description": "The amount that we received in the transaction request"
                                                },
                                                "net_amount_debit": {
                                                    "type": "string",
                                                    "description": "The total amount debited from the customer’s account after TDR charges."
                                                },
                                                "cash_back_percentage": {
                                                    "type": "string",
                                                    "description": "The percentage of cashback the customer is eligible to receive for the transaction."
                                                },
                                                "deduction_percentage": {
                                                    "type": "string",
                                                    "description": "The percentage of deduction applied to the transaction, based on the payment method used."
                                                },
                                                "merchant_logo": {
                                                    "type": "string",
                                                    "description": "The logo of the merchant (NA if not provided).\r\n"
                                                },
                                                "surl": {
                                                    "type": "string",
                                                    "description": "After the success payment, the user will redirected on the provided surl."
                                                },
                                                "furl": {
                                                    "type": "string",
                                                    "description": "After the success payment, the user will redirected on the provided furl."
                                                },
                                                "productinfo": {
                                                    "type": "string",
                                                    "description": "The value of the Product info that you passed during the initiation of the payment."
                                                },
                                                "udf10": {
                                                    "type": "string",
                                                    "description": "NA"
                                                },
                                                "udf9": {
                                                    "type": "string",
                                                    "description": "NA"
                                                },
                                                "udf8": {
                                                    "type": "string",
                                                    "description": "NA"
                                                },
                                                "udf7": {
                                                    "type": "string",
                                                    "description": "The value of the udf7 that you passed during the initiation of the payment."
                                                },
                                                "udf6": {
                                                    "type": "string",
                                                    "description": "The value of the udf6 that you passed during the initiation of the payment."
                                                },
                                                "udf5": {
                                                    "type": "string",
                                                    "description": "The value of the udf5 that you passed during the initiation of the payment."
                                                },
                                                "udf4": {
                                                    "type": "string",
                                                    "description": "The value of the udf4 that you passed during the initiation of the payment."
                                                },
                                                "udf3": {
                                                    "type": "string",
                                                    "description": "The value of the udf3 that you passed during the initiation of the payment."
                                                },
                                                "udf2": {
                                                    "type": "string",
                                                    "description": "The value of the udf2 that you passed during the initiation of the payment."
                                                },
                                                "udf1": {
                                                    "type": "string",
                                                    "description": "The value of the udf1 that you passed during the initiation of the payment."
                                                },
                                                "card_type": {
                                                    "type": "string",
                                                    "description": "The mode of the transaction (e.g., UPI, Credit Card, Debit Card, NetBanking, UPI, Wallet)."
                                                },
                                                "hash": {
                                                    "type": "string",
                                                    "description": "A reverse hash of the transaction data for security and validation purposes."
                                                },
                                                "status": {
                                                    "type": "string",
                                                    "description": "The overall status of the transaction (e.g., 'success', 'failure','dropped', 'pending', 'userCancelled', 'initiated', 'bounced')."
                                                },
                                                "bank_name": {
                                                    "type": "string",
                                                    "description": "The name of the bank that provided only NB trasnactions (you will receive the NA, if the payment mode is non-netbanking)."
                                                },
                                                "auth_code": {
                                                    "type": "string",
                                                    "description": "The authorization code provided by the bank for card transactions (you will receive the NA, if the payment mode is non-card)."
                                                },
                                                "auth_ref_num": {
                                                    "type": "string",
                                                    "description": "The authorization code provided by the payment processor or issuing bank during the transaction (you will receive the NA, if the payment mode is non-card)"
                                                }
                                            }
                                        }
                                    }
                                },
                                "examples": {
                                    "success": {
                                        "value": {
                                            "status": true,
                                            "msg": {
                                                "txnid": "EBZTestTxn0001",
                                                "firstname": "Sagar",
                                                "email": "sagar.sawant@easebuzz.in",
                                                "phone": "8805596828",
                                                "key": "XXXXXXXXXX",
                                                "mode": "UPI",
                                                "unmappedstatus": "NA",
                                                "cardCategory": "NA",
                                                "addedon": "2022-08-23 08:06:41",
                                                "payment_source": "Easebuzz",
                                                "PG_TYPE": "NA",
                                                "bank_ref_num": "223542981037",
                                                "bankcode": "NA",
                                                "error": "APPROVED OR COMPLETED SUCCESSFULLY",
                                                "error_Message": "APPROVED OR COMPLETED SUCCESSFULLY",
                                                "name_on_card": "NA",
                                                "upi_va": "8805596828@ybl",
                                                "cardnum": "NA",
                                                "issuing_bank": "NA",
                                                "easepayid": "E2208231A0ZMRS",
                                                "amount": "2.0",
                                                "net_amount_debit": "2.0",
                                                "cash_back_percentage": "50.0",
                                                "deduction_percentage": "0.0",
                                                "merchant_logo": "NA",
                                                "surl": "http://localhost:3000/response",
                                                "furl": "http://localhost:3000/response",
                                                "productinfo": "test product info",
                                                "auth_code": "NA",
                                                "auth_ref_num": "NA",
                                                "udf10": "",
                                                "udf9": "",
                                                "udf8": "",
                                                "udf7": "",
                                                "udf6": "",
                                                "udf5": "",
                                                "udf4": "",
                                                "udf3": "",
                                                "udf2": "",
                                                "udf1": "",
                                                "card_type": "UPI",
                                                "hash": "7c36ca4836a6b4af19e4966f2af90bb6c05a235ec35840662deb9f6302b38eab6d1d75e8401cacef7647ed4e9e9807b58ffca00ee9c61e4fdbbcb5e49a8563d4",
                                                "status": "success",
                                                "bank_name": "NA"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                "deprecated": true,
                "security": [],
                "servers": [
                    {
                        "url": "https://testdashboard.easebuzz.in",
                        "description": "Sandbox URL"
                    },
                    {
                        "url": "https://dashboard.easebuzz.in",
                        "description": "Production URL"
                    }
                ],
                "x-stoplight": {
                    "id": "nujl9cnjsx095"
                }
            }
        },
        "/transaction/v2.1/retrieve": {
            "post": {
                "summary": "Transaction API V2.1",
                "description": "Easebuzz provides an API to fetch all the information related to a particular transaction by providing the transaction id. \n\nIf the auto refund is enabled, then the status of the transaction API response changes according to transaction status. Once the status is updated from the bank side, we will automatically raise a refund in the system and send the response with the status marked as 'auto refunded'.\n\n> **Note:** It is critical to configure [**webhooks**](https://docs.easebuzz.in/docs/payment-gateway/587zy3v064so6-what-are-webhooks) as this will allow the Easebuzz system to 'push' data to your system whenever any event occurs, ensuring that both systems are always in sync.\n<br /><br />\n**Hash sequence:** \n```\nkey|txnid|salt\n```\n",
                "requestBody": {
                    "description": "",
                    "content": {
                        "application/x-www-form-urlencoded": {
                            "schema": {
                                "type": "object",
                                "x-examples": {
                                    "Example 1": {
                                        "txnid": "LXXXXXXXX",
                                        "key": "4XXXXXXX",
                                        "amount": 100,
                                        "email": "surXXXXXXXXX@gmail.com",
                                        "phone": "9XXXXXXXXX",
                                        "hash": "93502e75846d38831fad9d79cd8caedd1454482376d9b042d82a2157d805c0c0ac5972f367f81e43c170ef73fff9a90b1fec81ab4909457857713ffe63ce6cc6"
                                    }
                                },
                                "required": [
                                    "key",
                                    "txnid",
                                    "hash"
                                ],
                                "properties": {
                                    "key": {
                                        "type": "string",
                                        "description": "Unique Merchant Key provided by Easebuzz for your merchant account."
                                    },
                                    "txnid": {
                                        "type": "string",
                                        "description": "Unique Transaction Id set on Merchant’s End for every transaction."
                                    },
                                    "hash": {
                                        "type": "string",
                                        "description": "This is sha512 encrypted string. And hash sequence is mentioned above.\nHash sequence: `key|txnid|salt`"
                                    }
                                }
                            },
                            "examples": {
                                "Example 1": {
                                    "value": {
                                        "txnid": "LXXXXXXXX",
                                        "key": "4XXXXXXX",
                                        "hash": "93502e75846d38831fad9d79cd8caedd1454482376d9b042d82a2157d805c0c0ac5972f367f81e43c170ef73fff9a90b1fec81ab4909457857713ffe63ce6cc6"
                                    }
                                }
                            }
                        }
                    }
                },
                "responses": {
                    "200": {
                        "description": "",
                        "content": {
                            "application/json": {
                                "schema": {
                                    "type": "object",
                                    "x-examples": {
                                        "Example 1": {
                                            "status": true,
                                            "msg": [
                                                {
                                                    "txnid": "202409-01",
                                                    "firstname": "Rajesh",
                                                    "email": "rajesh@easebuzz.in",
                                                    "phone": "9085298536",
                                                    "key": "ZUUGWR4IGL",
                                                    "mode": "DC",
                                                    "unmappedstatus": "NA",
                                                    "cardCategory": "NA",
                                                    "addedon": "2024-09-04 13:23:58",
                                                    "payment_source": "Easebuzz",
                                                    "PG_TYPE": "NA",
                                                    "bank_ref_num": "NA",
                                                    "bankcode": "NA",
                                                    "error": "Failed to generate alt id in the Token Management System.",
                                                    "error_Message": "Failed to generate alt id in the Token Management System.",
                                                    "name_on_card": "Rajesh",
                                                    "upi_va": "NA",
                                                    "cardnum": "XXXXXXXXXXXX9655",
                                                    "issuing_bank": "NA",
                                                    "easepayid": "E2409040A97T7L",
                                                    "amount": "2.0",
                                                    "net_amount_debit": "2.0",
                                                    "cash_back_percentage": "50.0",
                                                    "deduction_percentage": "0.0",
                                                    "merchant_logo": "NA",
                                                    "surl": "http://localhost/php-kit-update/response.php",
                                                    "furl": "http://localhost/php-kit-update/response.php",
                                                    "productinfo": "test transaction",
                                                    "udf10": "",
                                                    "udf9": "",
                                                    "udf8": "",
                                                    "udf7": "",
                                                    "udf6": "",
                                                    "udf5": "",
                                                    "udf4": "",
                                                    "udf3": "",
                                                    "udf2": "",
                                                    "udf1": "",
                                                    "card_type": "Debit Card",
                                                    "hash": "2974be5409be37a882197b52d0acffd3ff2fc8f0207049d87bd091195236e9a8ab25f50c040aa752f1e0640ebbadf201cd1b09427d77b83dd7e18842d250c1af",
                                                    "status": "failure",
                                                    "bank_name": "NA",
                                                    "auth_code": "",
                                                    "auth_ref_num": "NA",
                                                    "response_code": "NA",
                                                    "error_code": "NA"
                                                },
                                                {
                                                    "txnid": "202409-01",
                                                    "firstname": "Rajesh",
                                                    "email": "rajesh@easebuzz.in",
                                                    "phone": "9085298536",
                                                    "key": "ZUUGWR4IGL",
                                                    "mode": "NA",
                                                    "unmappedstatus": "NA",
                                                    "cardCategory": "NA",
                                                    "addedon": "2024-09-04 13:23:40",
                                                    "payment_source": "Easebuzz",
                                                    "PG_TYPE": "NA",
                                                    "bank_ref_num": "NA",
                                                    "bankcode": "NA",
                                                    "error": "NA",
                                                    "error_Message": "NA",
                                                    "name_on_card": "NA",
                                                    "upi_va": "NA",
                                                    "cardnum": "NA",
                                                    "issuing_bank": "NA",
                                                    "easepayid": "E2409040A97SZ4",
                                                    "amount": "2.0",
                                                    "net_amount_debit": "2.0",
                                                    "cash_back_percentage": "50.0",
                                                    "deduction_percentage": "0",
                                                    "merchant_logo": "NA",
                                                    "surl": "http://localhost/php-kit-update/response.php",
                                                    "furl": "http://localhost/php-kit-update/response.php",
                                                    "productinfo": "test transaction",
                                                    "udf10": "",
                                                    "udf9": "",
                                                    "udf8": "",
                                                    "udf7": "",
                                                    "udf6": "",
                                                    "udf5": "",
                                                    "udf4": "",
                                                    "udf3": "",
                                                    "udf2": "",
                                                    "udf1": "",
                                                    "card_type": "NA",
                                                    "hash": "d7078a75b279599bfc1b88673389005c076300f5497a3aa1cd5372b4c0c621a8ad0868812e64ad269826f435a4084149cab807d64b6619c4a732d969f5e143cc",
                                                    "status": "userCancelled",
                                                    "bank_name": "NA",
                                                    "auth_code": "",
                                                    "auth_ref_num": "NA",
                                                    "response_code": "NA",
                                                    "error_code": "NA"
                                                }
                                            ]
                                        }
                                    },
                                    "properties": {
                                        "status": {
                                            "type": "boolean"
                                        },
                                        "msg": {
                                            "type": "array",
                                            "items": {
                                                "type": "object",
                                                "properties": {
                                                    "txnid": {
                                                        "type": "string",
                                                        "description": "The value of the transaction ID that you passed during the initiation of the payment."
                                                    },
                                                    "firstname": {
                                                        "type": "string",
                                                        "description": "The value of the customer’s name."
                                                    },
                                                    "email": {
                                                        "type": "string",
                                                        "description": "The email address of the customer."
                                                    },
                                                    "phone": {
                                                        "type": "string",
                                                        "description": "The phone number of the customer."
                                                    },
                                                    "key": {
                                                        "type": "string",
                                                        "description": "The merchant’s key used to intiate the transaction."
                                                    },
                                                    "mode": {
                                                        "type": "string",
                                                        "description": "The payment mode used for the transaction (e.g., UPI, CC, DC, NB)."
                                                    },
                                                    "unmappedstatus": {
                                                        "type": "string",
                                                        "description": "NA\r\n"
                                                    },
                                                    "cardCategory": {
                                                        "type": "string",
                                                        "description": "The UPI credit card used for the transaction (e.g., UPI CC transaction, or NA if not applicable)."
                                                    },
                                                    "addedon": {
                                                        "type": "string",
                                                        "description": "The date and time in UTC format when the transaction was initiated."
                                                    },
                                                    "payment_source": {
                                                        "type": "string",
                                                        "description": "The source of the payment, it will be the default value as 'Easebuzz'."
                                                    },
                                                    "PG_TYPE": {
                                                        "type": "string",
                                                        "description": "NA"
                                                    },
                                                    "bank_ref_num": {
                                                        "type": "string",
                                                        "description": "The reference number provided by the bank for the transaction."
                                                    },
                                                    "bankcode": {
                                                        "type": "string",
                                                        "description": "The Net bank code associated with the transaction if the user complete the transaction using NB applicable only for NB."
                                                    },
                                                    "error": {
                                                        "type": "string",
                                                        "description": "The error description of the transaction, typically indicating success or failure reason."
                                                    },
                                                    "error_Message": {
                                                        "type": "string",
                                                        "description": "The error description of the transaction, typically indicating success or failure reason."
                                                    },
                                                    "name_on_card": {
                                                        "type": "string",
                                                        "description": "The name on the card provided by the user on the payment page. (you will receive the NA, if the payment mode is non-card)\r\n"
                                                    },
                                                    "upi_va": {
                                                        "type": "string",
                                                        "description": "The UPI Virtual Address (VPA) used for the transaction for UPI mode."
                                                    },
                                                    "cardnum": {
                                                        "type": "string",
                                                        "description": "The last 4 digits of the card number (you will receive the NA, if the payment mode is non-card)."
                                                    },
                                                    "issuing_bank": {
                                                        "type": "string",
                                                        "description": "NA\r\n"
                                                    },
                                                    "easepayid": {
                                                        "type": "string",
                                                        "description": "The unique easebuzz ID assigned to the transaction by Easebuzz."
                                                    },
                                                    "amount": {
                                                        "type": "string",
                                                        "description": "The amount that we received in the transaction request"
                                                    },
                                                    "net_amount_debit": {
                                                        "type": "string",
                                                        "description": "The total amount debited from the customer’s account after TDR charges."
                                                    },
                                                    "cash_back_percentage": {
                                                        "type": "string",
                                                        "description": "The percentage of cashback the customer is eligible to receive for the transaction."
                                                    },
                                                    "deduction_percentage": {
                                                        "type": "string",
                                                        "description": "The percentage of deduction applied to the transaction, based on the payment method used."
                                                    },
                                                    "merchant_logo": {
                                                        "type": "string",
                                                        "description": "The logo of the merchant (NA if not provided)."
                                                    },
                                                    "surl": {
                                                        "type": "string",
                                                        "description": "After the success payment, the user will redirected on the provided surl"
                                                    },
                                                    "furl": {
                                                        "type": "string",
                                                        "description": "After the success payment, the user will redirected on the provided furl."
                                                    },
                                                    "productinfo": {
                                                        "type": "string",
                                                        "description": "The value of the Product info that you passed during the initiation of the payment."
                                                    },
                                                    "udf10": {
                                                        "type": "string",
                                                        "description": "NA"
                                                    },
                                                    "udf9": {
                                                        "type": "string",
                                                        "description": "NA"
                                                    },
                                                    "udf8": {
                                                        "type": "string",
                                                        "description": "NA"
                                                    },
                                                    "udf7": {
                                                        "type": "string",
                                                        "description": "The value of the udf7 that you passed during the initiation of the payment."
                                                    },
                                                    "udf6": {
                                                        "type": "string",
                                                        "description": "The value of the udf6 that you passed during the initiation of the payment."
                                                    },
                                                    "udf5": {
                                                        "type": "string",
                                                        "description": "The value of the udf5 that you passed during the initiation of the payment."
                                                    },
                                                    "udf4": {
                                                        "type": "string",
                                                        "description": "The value of the udf4 that you passed during the initiation of the payment."
                                                    },
                                                    "udf3": {
                                                        "type": "string",
                                                        "description": "The value of the udf3 that you passed during the initiation of the payment."
                                                    },
                                                    "udf2": {
                                                        "type": "string",
                                                        "description": "The value of the udf2 that you passed during the initiation of the payment."
                                                    },
                                                    "udf1": {
                                                        "type": "string",
                                                        "description": "The value of the udf1 that you passed during the initiation of the payment."
                                                    },
                                                    "card_type": {
                                                        "type": "string",
                                                        "description": "The mode of the transaction (e.g., UPI, Credit Card, Debit Card, NetBanking, UPI, Wallet)."
                                                    },
                                                    "hash": {
                                                        "type": "string",
                                                        "description": "A reverse hash of the transaction data for security and validation purposes."
                                                    },
                                                    "status": {
                                                        "type": "string",
                                                        "description": "The overall status of the transaction (e.g., 'success', 'failure','dropped', 'pending', 'userCancelled', 'initiated', 'bounced')."
                                                    },
                                                    "bank_name": {
                                                        "type": "string",
                                                        "description": "The name of the bank that provided only NB trasnactions (you will receive the NA, if the payment mode is non-netbanking)."
                                                    },
                                                    "auth_code": {
                                                        "type": "string",
                                                        "description": "The authorization code provided by the bank for card transactions (you will receive the NA, if the payment mode is non-card)."
                                                    },
                                                    "auth_ref_num": {
                                                        "type": "string",
                                                        "description": "The authorization code provided by the payment processor or issuing bank during the transaction (you will receive the NA, if the payment mode is non-card)."
                                                    },
                                                    "response_code": {
                                                        "type": "string"
                                                    },
                                                    "error_code": {
                                                        "type": "string"
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                "examples": {
                                    "PG Txn Response": {
                                        "value": {
                                            "status": true,
                                            "msg": [
                                                {
                                                    "txnid": "202409-01",
                                                    "firstname": "Rajesh K",
                                                    "email": "rajesh.k@gmail.in",
                                                    "phone": "9044448536",
                                                    "key": "ZBSD3DGDL",
                                                    "mode": "DC",
                                                    "unmappedstatus": "NA",
                                                    "cardCategory": "NA",
                                                    "addedon": "2024-09-04 13:23:58",
                                                    "payment_source": "Easebuzz",
                                                    "PG_TYPE": "NA",
                                                    "bank_ref_num": "NA",
                                                    "bankcode": "NA",
                                                    "error": "Transaction successful.",
                                                    "error_Message": "Transaction successful.",
                                                    "name_on_card": "Rajesh K",
                                                    "upi_va": "NA",
                                                    "cardnum": "XXXXXXXXXXXX9655",
                                                    "issuing_bank": "NA",
                                                    "easepayid": "E2409040A97T7L",
                                                    "amount": "2.0",
                                                    "net_amount_debit": "2.0",
                                                    "cash_back_percentage": "50.0",
                                                    "deduction_percentage": "0.0",
                                                    "merchant_logo": "NA",
                                                    "surl": "http://localhost/php-kit-update/response.php",
                                                    "furl": "http://localhost/php-kit-update/response.php",
                                                    "productinfo": "PG transaction",
                                                    "udf10": "",
                                                    "udf9": "",
                                                    "udf8": "",
                                                    "udf7": "",
                                                    "udf6": "",
                                                    "udf5": "",
                                                    "udf4": "",
                                                    "udf3": "",
                                                    "udf2": "",
                                                    "udf1": "",
                                                    "card_type": "Debit Card",
                                                    "hash": "2974be5409be37a882197b52d0acffd3ff2fc8f0207049d87bd091195236e9a8ab25f50c040aa752f1e0640ebbadf201cd1b09427d77b83dd7e18842d250c1af",
                                                    "status": "success",
                                                    "bank_name": "NA",
                                                    "auth_code": "",
                                                    "auth_ref_num": "NA",
                                                    "response_code": "NA",
                                                    "error_code": "NA"
                                                }
                                            ]
                                        }
                                    },
                                    "EasyCollect txn Response": {
                                        "value": {
                                            "status": true,
                                            "msg": [
                                                {
                                                    "txnid": "EC01KH0M4MVDNF318Z4Q1WWCCX8D",
                                                    "firstname": "Rajesh K",
                                                    "email": "rajesh.k@easebuzz.in",
                                                    "phone": "+91-9879879879",
                                                    "key": "ZUUABCDEGL",
                                                    "mode": "UPI",
                                                    "unmappedstatus": "NA",
                                                    "cardCategory": "NA",
                                                    "addedon": "2026-02-11 06:10:08",
                                                    "payment_source": "Easebuzz",
                                                    "PG_TYPE": "NA",
                                                    "bank_ref_num": "397078139202",
                                                    "bankcode": "NA",
                                                    "error": "APPROVED OR COMPLETED SUCCESSFULLY",
                                                    "error_Message": "APPROVED OR COMPLETED SUCCESSFULLY",
                                                    "name_on_card": "NA",
                                                    "upi_va": "success@ptyes",
                                                    "cardnum": "NA",
                                                    "issuing_bank": "NA",
                                                    "easepayid": "E2602110TNGCSD",
                                                    "amount": "1.0",
                                                    "net_amount_debit": "1.0",
                                                    "cash_back_percentage": "50.0",
                                                    "deduction_percentage": "0.0",
                                                    "merchant_logo": "NA",
                                                    "surl": "https://pay.easebuzz.in/easy_collect/surl/15010706251d4c3585db2abbd5bbf012",
                                                    "furl": "https://pay.easebuzz.in/easy_collect/furl/15010706251d4c3585db2abbd5bbf012",
                                                    "productinfo": "EasyCollect Payment",
                                                    "udf10": "",
                                                    "udf9": "",
                                                    "udf8": "",
                                                    "udf7": "",
                                                    "udf6": "",
                                                    "udf5": "",
                                                    "udf4": "",
                                                    "udf3": "",
                                                    "udf2": "",
                                                    "udf1": "",
                                                    "card_type": "UPI",
                                                    "hash": "1516fc522fa790ec34dd0d1040704b91dbe40bdcff43402516e6a6e960c2f7aedc2f54766144cbd0b59b6022cdb23c64df04047628f0f6fed1cfb462ad160794",
                                                    "status": "success",
                                                    "bank_name": "NA",
                                                    "auth_code": "",
                                                    "auth_ref_num": "NA",
                                                    "response_code": "00",
                                                    "error_code": "NA"
                                                },
                                                {
                                                    "txnid": "EC01KH0M4MVDNF318Z4Q1WWCCX8D",
                                                    "firstname": "Rajesh K",
                                                    "email": "rajesh.k@easebuzz.in",
                                                    "phone": "+91-9879879879",
                                                    "key": "ZUUABCDGL",
                                                    "mode": "UPI",
                                                    "unmappedstatus": "NA",
                                                    "cardCategory": "NA",
                                                    "addedon": "2026-02-11 05:54:15",
                                                    "payment_source": "Easebuzz",
                                                    "PG_TYPE": "NA",
                                                    "bank_ref_num": "NA",
                                                    "bankcode": "NA",
                                                    "error": "Transaction timed out due to customer inactivity or no bank response",
                                                    "error_Message": "Transaction timed out due to customer inactivity or no bank response",
                                                    "name_on_card": "NA",
                                                    "upi_va": "NA",
                                                    "cardnum": "NA",
                                                    "issuing_bank": "NA",
                                                    "easepayid": "E2602110TNF0QO",
                                                    "amount": "1.0",
                                                    "net_amount_debit": "1.0",
                                                    "cash_back_percentage": "50.0",
                                                    "deduction_percentage": "0.0",
                                                    "merchant_logo": "NA",
                                                    "surl": "https://pay.easebuzz.in/easy_collect/surl/15010706251d4c3585db2abbd5bbf012",
                                                    "furl": "https://pay.easebuzz.in/easy_collect/furl/15010706251d4c3585db2abbd5bbf012",
                                                    "productinfo": "EasyCollect Payment",
                                                    "udf10": "",
                                                    "udf9": "",
                                                    "udf8": "",
                                                    "udf7": "",
                                                    "udf6": "",
                                                    "udf5": "",
                                                    "udf4": "",
                                                    "udf3": "",
                                                    "udf2": "",
                                                    "udf1": "",
                                                    "card_type": "UPI",
                                                    "hash": "cd32986fe2ccd7c653acb433e87c640a48fa572abfc4cc2bacf416a21f4f36ef0696de37818481774286cb4873dcc98cc86133a94df300dbe4645a61ed56873a",
                                                    "status": "dropped",
                                                    "bank_name": "NA",
                                                    "auth_code": "",
                                                    "auth_ref_num": "NA",
                                                    "response_code": null,
                                                    "error_code": null
                                                }
                                            ]
                                        }
                                    },
                                    "": {
                                        "value": {
                                            "status": true,
                                            "msg": [
                                                {
                                                    "txnid": "HGJ86788",
                                                    "firstname": "Rajesh K",
                                                    "email": "rajesh.k@easebuzz.in",
                                                    "phone": "9879879879",
                                                    "key": "DEVABCD95K",
                                                    "mode": "DC",
                                                    "unmappedstatus": "NA",
                                                    "cardCategory": "NA",
                                                    "addedon": "2026-01-12 13:10:38",
                                                    "payment_source": "Easebuzz",
                                                    "PG_TYPE": "NA",
                                                    "bank_ref_num": "601213473521",
                                                    "bankcode": "NA",
                                                    "error": "Successful Transaction",
                                                    "error_Message": "Successful Transaction",
                                                    "name_on_card": "NA",
                                                    "upi_va": "NA",
                                                    "cardnum": "XXXXXXXXXXXX1188",
                                                    "issuing_bank": "NA",
                                                    "easepayid": "E26011206R7958",
                                                    "amount": "1.5",
                                                    "net_amount_debit": "1.5",
                                                    "cash_back_percentage": "50.0",
                                                    "deduction_percentage": "0.0",
                                                    "merchant_logo": "NA",
                                                    "surl": "http://localhost/pay_semless/response.php",
                                                    "furl": "http://localhost/pay_semless/response.php",
                                                    "productinfo": "Auth-capture txn",
                                                    "udf10": "",
                                                    "udf9": "",
                                                    "udf8": "",
                                                    "udf7": "",
                                                    "udf6": "",
                                                    "udf5": "10.0",
                                                    "udf4": "",
                                                    "udf3": "",
                                                    "udf2": "",
                                                    "udf1": "",
                                                    "card_type": "Debit Card",
                                                    "hash": "17ea02e52f09976c7292eb049ccba9790b38f4ec255e476a4903ba3cb5df7e1160e9f71dbc3cd2f9ad7d9cd1b999c199476f971765d538391cc051a4bc6e80c5",
                                                    "status": "success",
                                                    "bank_name": "NA",
                                                    "auth_code": "868078",
                                                    "auth_ref_num": "NA",
                                                    "settlement_amount": 1.5,
                                                    "service_tax": 0,
                                                    "service_charge": 0,
                                                    "payment_date": "2026-01-12 19:54:45",
                                                    "auth_expiry_date": "2026-01-16 13:11:20",
                                                    "capture_status": "success",
                                                    "payment_category": "NCAP"
                                                }
                                            ]
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                "security": [],
                "servers": [
                    {
                        "url": "https://testdashboard.easebuzz.in",
                        "description": "Sandbox URL"
                    },
                    {
                        "url": "https://dashboard.easebuzz.in",
                        "description": "Production URL"
                    }
                ],
                "x-internal": false,
                "x-stoplight": {
                    "id": "f607brr7f8ek8"
                }
            }
        },
        "/transaction/v1/retrieve/date": {
            "post": {
                "summary": "Transaction API (by date)",
                "description": "Easebuzz provides an API to fetch all the information of multiple transactions by providing the transaction date. \n\nIf the auto refund is enabled, then the status of the transaction API response changes according to transaction status. Once the status is updated from the bank side, we will automatically raise a refund in the system and send the response with the status marked as 'auto refunded'.\n\n> **Note:** It is critical to configure [**webhooks**](https://docs.easebuzz.in/docs/payment-gateway/587zy3v064so6-what-are-webhooks) as this will allow the Easebuzz system to 'push' data to your system whenever any event occurs, ensuring that both systems are always in sync.\n\n**Hash sequence:** \n```\nkey|merchant_email|transaction_date|salt\n```\n",
                "requestBody": {
                    "description": "",
                    "content": {
                        "application/x-www-form-urlencoded": {
                            "schema": {
                                "type": "object",
                                "properties": {
                                    "merchant_key": {
                                        "type": "string",
                                        "description": "Unique Merchant Key provided by Easebuzz for your merchant account."
                                    },
                                    "transaction_date": {
                                        "type": "string",
                                        "description": "The transaction date of the transactions you wish to query in dd-mm-yyyy."
                                    },
                                    "merchant_email": {
                                        "type": "string",
                                        "description": "Registered email of the merchant."
                                    },
                                    "hash": {
                                        "type": "string",
                                        "description": "This is sha512 encrypted string. And hash sequence is mentioned above. Hash sequence: key|merchant_email|transaction_date|salt"
                                    },
                                    "submerchant_id": {
                                        "type": "string",
                                        "description": "Filter transactions associated by submerchant."
                                    }
                                },
                                "required": [
                                    "merchant_key",
                                    "transaction_date",
                                    "merchant_email",
                                    "hash"
                                ],
                                "x-examples": {
                                    "Example 1": {
                                        "merchant_key": "XXXXXXXXXXX",
                                        "transaction_date": "26-09-2022",
                                        "merchant_email": "XXXXXXXXXXX@gmail.com",
                                        "hash": "b2a83f0fa7116fa85a055a109a529a5cca61d07e14c63a7c35fd2bd5c8592c652033b7eb8a18bdf4df7a7aafa08ddc061fe5f213aa3fb5c9d62f746f8cbd1f72"
                                    }
                                }
                            },
                            "examples": {
                                "Example 1": {
                                    "value": {
                                        "merchant_key": "XXXXXXXXXXX",
                                        "transaction_date": "26-09-2022",
                                        "merchant_email": "XXXXXXXXXXX@gmail.com",
                                        "hash": "b2a83f0fa7116fa85a055a109a529a5cca61d07e14c63a7c35fd2bd5c8592c652033b7eb8a18bdf4df7a7aafa08ddc061fe5f213aa3fb5c9d62f746f8cbd1f72"
                                    }
                                }
                            }
                        }
                    }
                },
                "responses": {
                    "200": {
                        "description": "",
                        "content": {
                            "application/json": {
                                "schema": {
                                    "type": "object",
                                    "properties": {
                                        "status": {
                                            "type": "boolean"
                                        },
                                        "transactions": {
                                            "type": "array",
                                            "items": {
                                                "type": "object",
                                                "properties": {
                                                    "status": {
                                                        "type": "string"
                                                    },
                                                    "net_amount_debit": {
                                                        "type": "integer"
                                                    },
                                                    "txnid": {
                                                        "type": "string"
                                                    },
                                                    "easepayid": {
                                                        "type": "string"
                                                    },
                                                    "amount": {
                                                        "type": "integer"
                                                    },
                                                    "firstname": {
                                                        "type": "string"
                                                    },
                                                    "phone": {
                                                        "type": "string"
                                                    },
                                                    "email": {
                                                        "type": "string"
                                                    },
                                                    "udf1": {
                                                        "type": "string"
                                                    },
                                                    "udf2": {
                                                        "type": "string"
                                                    },
                                                    "udf3": {
                                                        "type": "string"
                                                    },
                                                    "udf4": {
                                                        "type": "string"
                                                    },
                                                    "udf5": {
                                                        "type": "string"
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    "x-examples": {
                                        "example-1": {
                                            "status": true,
                                            "transactions": [
                                                {
                                                    "status": "success",
                                                    "net_amount_debit": 100,
                                                    "txnid": "MID0001",
                                                    "easepayid": "TRXNID0001",
                                                    "amount": 100,
                                                    "firstname": "Customer name",
                                                    "phone": "1234567890",
                                                    "email": "john@doe.com",
                                                    "udf1": "Additional 1",
                                                    "udf2": "Additional 2",
                                                    "udf3": "Additional 3",
                                                    "udf4": "Additional 4",
                                                    "udf5": "Additional 5"
                                                },
                                                {
                                                    "status": "bounced",
                                                    "net_amount_debit": 50,
                                                    "txnid": "MID0002",
                                                    "easepayid": "TRXNID0002",
                                                    "amount": 50,
                                                    "firstname": "Customer name",
                                                    "phone": "1234567890",
                                                    "email": "jane@doe.com",
                                                    "udf1": "Additional 1",
                                                    "udf2": "Additional 2",
                                                    "udf3": "Additional 3",
                                                    "udf4": "Additional 4",
                                                    "udf5": "Additional 5"
                                                },
                                                {
                                                    "status": "dropped",
                                                    "net_amount_debit": 80,
                                                    "txnid": "MID0003",
                                                    "easepayid": "TRXNID0003",
                                                    "amount": 80,
                                                    "firstname": "Customer name",
                                                    "phone": "1234567890",
                                                    "email": "john@doe.com",
                                                    "udf1": "Additional 1",
                                                    "udf2": "Additional 2",
                                                    "udf3": "Additional 3",
                                                    "udf4": "Additional 4",
                                                    "udf5": "Additional 5"
                                                },
                                                {
                                                    "status": "success",
                                                    "net_amount_debit": 20,
                                                    "txnid": "MID0004",
                                                    "easepayid": "TRXNID0004",
                                                    "amount": 20,
                                                    "firstname": "Customer name",
                                                    "phone": "1234567890",
                                                    "email": "jane@doe.com",
                                                    "udf1": "Additional 1",
                                                    "udf2": "Additional 2",
                                                    "udf3": "Additional 3",
                                                    "udf4": "Additional 4",
                                                    "udf5": "Additional 5"
                                                },
                                                {
                                                    "status": "failure",
                                                    "net_amount_debit": 100,
                                                    "txnid": "MID0005",
                                                    "easepayid": "TRXNID0005",
                                                    "amount": 100,
                                                    "firstname": "Customer name",
                                                    "phone": "1234567890",
                                                    "email": "john@doe.com",
                                                    "udf1": "Additional 1",
                                                    "udf2": "Additional 2",
                                                    "udf3": "Additional 3",
                                                    "udf4": "Additional 4",
                                                    "udf5": "Additional 5"
                                                }
                                            ]
                                        }
                                    }
                                },
                                "examples": {
                                    "success": {
                                        "value": {
                                            "status": true,
                                            "transactions": [
                                                {
                                                    "status": "success",
                                                    "net_amount_debit": 100,
                                                    "txnid": "MID0001",
                                                    "easepayid": "TRXNID0001",
                                                    "amount": 100,
                                                    "firstname": "Customer name",
                                                    "phone": "1234567890",
                                                    "email": "john@doe.com",
                                                    "udf1": "Additional 1",
                                                    "udf2": "Additional 2",
                                                    "udf3": "Additional 3",
                                                    "udf4": "Additional 4",
                                                    "udf5": "Additional 5"
                                                },
                                                {
                                                    "status": "bounced",
                                                    "net_amount_debit": 50,
                                                    "txnid": "MID0002",
                                                    "easepayid": "TRXNID0002",
                                                    "amount": 50,
                                                    "firstname": "Customer name",
                                                    "phone": "1234567890",
                                                    "email": "jane@doe.com",
                                                    "udf1": "Additional 1",
                                                    "udf2": "Additional 2",
                                                    "udf3": "Additional 3",
                                                    "udf4": "Additional 4",
                                                    "udf5": "Additional 5"
                                                },
                                                {
                                                    "status": "dropped",
                                                    "net_amount_debit": 80,
                                                    "txnid": "MID0003",
                                                    "easepayid": "TRXNID0003",
                                                    "amount": 80,
                                                    "firstname": "Customer name",
                                                    "phone": "1234567890",
                                                    "email": "john@doe.com",
                                                    "udf1": "Additional 1",
                                                    "udf2": "Additional 2",
                                                    "udf3": "Additional 3",
                                                    "udf4": "Additional 4",
                                                    "udf5": "Additional 5"
                                                },
                                                {
                                                    "status": "success",
                                                    "net_amount_debit": 20,
                                                    "txnid": "MID0004",
                                                    "easepayid": "TRXNID0004",
                                                    "amount": 20,
                                                    "firstname": "Customer name",
                                                    "phone": "1234567890",
                                                    "email": "jane@doe.com",
                                                    "udf1": "Additional 1",
                                                    "udf2": "Additional 2",
                                                    "udf3": "Additional 3",
                                                    "udf4": "Additional 4",
                                                    "udf5": "Additional 5"
                                                },
                                                {
                                                    "status": "failure",
                                                    "net_amount_debit": 100,
                                                    "txnid": "MID0005",
                                                    "easepayid": "TRXNID0005",
                                                    "amount": 100,
                                                    "firstname": "Customer name",
                                                    "phone": "1234567890",
                                                    "email": "john@doe.com",
                                                    "udf1": "Additional 1",
                                                    "udf2": "Additional 2",
                                                    "udf3": "Additional 3",
                                                    "udf4": "Additional 4",
                                                    "udf5": "Additional 5"
                                                }
                                            ]
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                "deprecated": true,
                "servers": [
                    {
                        "url": "https://testdashboard.easebuzz.in",
                        "description": "Sandbox URL"
                    },
                    {
                        "url": "https://dashboard.easebuzz.in",
                        "description": "Production URL"
                    }
                ],
                "x-stoplight": {
                    "id": "rknak5q5oz4e5"
                }
            }
        },
        "/transaction/v2/retrieve/date": {
            "post": {
                "summary": "Transaction API (by date range)",
                "description": "Easebuzz provides an API to fetch all the information of multiple transactions by providing the transaction date range. \n\nIf the auto refund is enabled, then the status of the transaction API response changes according to transaction status. Once the status is updated from the bank side, we will automatically raise a refund in the system and send the response with the status marked as 'auto refunded'.\n\n> **Note:** It is critical to configure [**webhooks**](https://docs.easebuzz.in/docs/payment-gateway/587zy3v064so6-what-are-webhooks) as this will allow the Easebuzz system to 'push' data to your system whenever any event occurs, ensuring that both systems are always in sync.\n\n**Hash sequence:** \n```\nkey||start_date|end_date|salt\n```\n",
                "requestBody": {
                    "description": "",
                    "content": {
                        "application/json": {
                            "schema": {
                                "type": "object",
                                "required": [
                                    "key",
                                    "date_range",
                                    "hash"
                                ],
                                "properties": {
                                    "key": {
                                        "type": "string",
                                        "description": "Unique Merchant Key provided by Easebuzz for your merchant account."
                                    },
                                    "date_range": {
                                        "type": "object",
                                        "properties": {
                                            "start_date": {
                                                "type": "string",
                                                "format": "date",
                                                "description": "The start date of the transactions you wish to query in dd-mm-yyyy."
                                            },
                                            "end_date": {
                                                "type": "string",
                                                "format": "date",
                                                "description": "The end date of the transactions you wish to query in dd-mm-yyyy."
                                            }
                                        }
                                    },
                                    "hash": {
                                        "type": "string",
                                        "description": "This is sha512 encrypted string. And hash sequence is mentioned above. "
                                    },
                                    "merchant_email": {
                                        "type": "string",
                                        "description": "Pass registered email of the merchant. Use this hash sequence, if you are passing the email id in the request payload.\n`key|merchant_email|start_date|end_date|salt`"
                                    },
                                    "submerchant_id": {
                                        "type": "string",
                                        "description": "Filter transactions associated by submerchant."
                                    },
                                    "page": {
                                        "type": "string",
                                        "description": "The alphanumeric value of the next parameter."
                                    },
                                    "additional_data": {
                                        "type": "string",
                                        "x-stoplight": {
                                            "id": "haqcokazbrm32"
                                        },
                                        "description": "Pass the key mentioned below to receive the additional corresponding values in the API response.\n`transaction_charges`, ` payout_details`, ` settlement_amount`, ` txn_date`, ` submerchant_id`, ` payout_id`, ` submerchant_payout_id`, ` transaction_ref_num`, ` bank_ref_num`"
                                    }
                                }
                            },
                            "examples": {
                                "Example 1": {
                                    "value": {
                                        "key": "XXXXXXXXXXX",
                                        "hash": "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
                                        "merchant_email": "XXXXXXXXXXX",
                                        "date_range": {
                                            "start_date": "10-05-2024",
                                            "end_date": "23-08-2024"
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                "responses": {
                    "200": {
                        "description": "",
                        "content": {
                            "application/json": {
                                "schema": {
                                    "type": "object",
                                    "properties": {
                                        "next": {
                                            "type": "string"
                                        },
                                        "previous": {
                                            "nullable": true
                                        },
                                        "count": {
                                            "type": "integer"
                                        },
                                        "data": {
                                            "type": "array",
                                            "items": {
                                                "type": "object",
                                                "properties": {
                                                    "status": {
                                                        "type": "string"
                                                    },
                                                    "total_debit_amount": {
                                                        "type": "integer"
                                                    },
                                                    "net_debit_amount": {
                                                        "type": "integer"
                                                    },
                                                    "easepayid": {
                                                        "type": "string"
                                                    },
                                                    "firstname": {
                                                        "type": "string"
                                                    },
                                                    "phone": {
                                                        "type": "string"
                                                    },
                                                    "udf1": {
                                                        "type": "string"
                                                    },
                                                    "udf2": {
                                                        "type": "string"
                                                    },
                                                    "udf3": {
                                                        "type": "string"
                                                    },
                                                    "udf4": {
                                                        "type": "string"
                                                    },
                                                    "udf5": {
                                                        "type": "string"
                                                    },
                                                    "txnid": {
                                                        "type": "string"
                                                    },
                                                    "email": {
                                                        "type": "string"
                                                    }
                                                }
                                            }
                                        },
                                        "status": {
                                            "type": "boolean"
                                        }
                                    },
                                    "x-examples": {
                                        "Example 1": {
                                            "next": "eyJwIjogIjU5ODU4NjAwMSIsICJ0IjogeyJlbmQiOiAzLjB9fQ==",
                                            "previous": null,
                                            "count": 20,
                                            "data": [
                                                {
                                                    "status": "success",
                                                    "total_debit_amount": 0,
                                                    "net_debit_amount": 1,
                                                    "easepayid": "E2408230A2BXYR",
                                                    "firstname": "test",
                                                    "phone": "+91-8000000000",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "1.00",
                                                    "txnid": "EC6GF4GQ2B",
                                                    "email": "rajesh.kushwaha@easebuzz.in"
                                                },
                                                {
                                                    "status": "dropped",
                                                    "total_debit_amount": 1,
                                                    "net_debit_amount": 1,
                                                    "easepayid": "E2408230A2BXG4",
                                                    "firstname": "Test Account shopify rajesh.kushwaha",
                                                    "phone": "8008008000",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "FUGE8XZ56H",
                                                    "email": "rajesh.kushwaha@easebuzz.in"
                                                },
                                                {
                                                    "status": "failure",
                                                    "total_debit_amount": 0,
                                                    "net_debit_amount": 1,
                                                    "easepayid": "E2408230A2BWD2",
                                                    "firstname": "test",
                                                    "phone": "+91-8000000000",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "1.00",
                                                    "txnid": "EC6GF4GQ2B",
                                                    "email": "rajesh.kushwaha@easebuzz.in"
                                                },
                                                {
                                                    "status": "failure",
                                                    "total_debit_amount": 0,
                                                    "net_debit_amount": 1,
                                                    "easepayid": "E2408220A1ZI2D",
                                                    "firstname": "test",
                                                    "phone": "+91-8000000000",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "1.00",
                                                    "txnid": "EC6GF4GQ2B",
                                                    "email": "rajesh.kushwaha@easebuzz.in"
                                                },
                                                {
                                                    "status": "bounced",
                                                    "total_debit_amount": 1,
                                                    "net_debit_amount": 1,
                                                    "easepayid": "E2408220A1ZHUI",
                                                    "firstname": "Test Account shopify rajesh.kushwaha",
                                                    "phone": "8008008000",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "8C0ISDKKGB",
                                                    "email": "rajesh.kushwaha@easebuzz.in"
                                                },
                                                {
                                                    "status": "bounced",
                                                    "total_debit_amount": 1001,
                                                    "net_debit_amount": 1001,
                                                    "easepayid": "E2408220A1U6QL",
                                                    "firstname": "Rajesh& Kushwah",
                                                    "phone": "999999999",
                                                    "udf1": "RAJENDRA NAGAR C. BLOCK",
                                                    "udf2": "RN C - B-1358, Rajendra Nagar C. Block Awas Vikas Bareilly 243006",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "542e1412165ew8464612114",
                                                    "email": "ramzade@sociology.mu.ac.in"
                                                },
                                                {
                                                    "status": "userCancelled",
                                                    "total_debit_amount": 1,
                                                    "net_debit_amount": 1,
                                                    "easepayid": "E2408220A1SBNG",
                                                    "firstname": "Test Account shopify rajesh.kushwaha",
                                                    "phone": "8008008000",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "AZ8FPYSDAT",
                                                    "email": "rajesh.kushwaha@easebuzz.in"
                                                },
                                                {
                                                    "status": "failure",
                                                    "total_debit_amount": 2,
                                                    "net_debit_amount": 2,
                                                    "easepayid": "E2408210A1EWGZ",
                                                    "firstname": "sanket",
                                                    "phone": "7559177365",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "RHIADGE0",
                                                    "email": "sanket.pawar@easebuzz.in"
                                                },
                                                {
                                                    "status": "failure",
                                                    "total_debit_amount": 2,
                                                    "net_debit_amount": 2,
                                                    "easepayid": "E2408210A1EVQY",
                                                    "firstname": "sanket",
                                                    "phone": "7559177365",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "46D7ZFMY",
                                                    "email": "sanket.pawar@easebuzz.in"
                                                },
                                                {
                                                    "status": "failure",
                                                    "total_debit_amount": 100,
                                                    "net_debit_amount": 100,
                                                    "easepayid": "E2408210A1E1Y7",
                                                    "firstname": "Rajesh Kushwah",
                                                    "phone": "+91-8085298536",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "542e1416qw5123q123w42e1132w82134646114",
                                                    "email": "rajesh.k@easebuzz.in"
                                                },
                                                {
                                                    "status": "userCancelled",
                                                    "total_debit_amount": 100,
                                                    "net_debit_amount": 100,
                                                    "easepayid": "E2408210A1E1S4",
                                                    "firstname": "Rajesh Kushwah",
                                                    "phone": "+91-8085298536",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "542e1416qw5123q123w42e1132w82134646114",
                                                    "email": "rajesh.kushwahah@easebuzz.in"
                                                },
                                                {
                                                    "status": "bounced",
                                                    "total_debit_amount": 1001,
                                                    "net_debit_amount": 1001,
                                                    "easepayid": "E2408210A1E0OD",
                                                    "firstname": "Rajesh Kushwah",
                                                    "phone": "999999999",
                                                    "udf1": "RAJENDRA NAGAR C. BLOCK",
                                                    "udf2": "RN C - B-1358, Rajendra Nagar C. Block Awas Vikas Bareilly 243006",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "542e1412165ew8464612114",
                                                    "email": "ramzade@sociology.mu.ac.in"
                                                },
                                                {
                                                    "status": "userCancelled",
                                                    "total_debit_amount": 1001,
                                                    "net_debit_amount": 1001,
                                                    "easepayid": "E2408210A1E0AC",
                                                    "firstname": "Rajesh Kushwah",
                                                    "phone": "999999999",
                                                    "udf1": "RAJENDRA NAGAR C. BLOCK",
                                                    "udf2": "RN C - B-1358, Rajendra Nagar C. Block Awas Vikas Bareilly 243006",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "542e1412165ew8464612114",
                                                    "email": "ramzade@sociology.mu.ac.in"
                                                },
                                                {
                                                    "status": "bounced",
                                                    "total_debit_amount": 1,
                                                    "net_debit_amount": 1,
                                                    "easepayid": "E24081609ZICWX",
                                                    "firstname": "Test",
                                                    "phone": "+91-8085298536",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "20.00",
                                                    "txnid": "ECVLCMQJ3V",
                                                    "email": "rajesh.kushwaha@easebuzz.in"
                                                },
                                                {
                                                    "status": "bounced",
                                                    "total_debit_amount": 2,
                                                    "net_debit_amount": 2,
                                                    "easepayid": "E24081409YNVBV",
                                                    "firstname": "sanket",
                                                    "phone": "7559177365",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "HPKQ8LBC",
                                                    "email": "sanket.pawar@easebuzz.in"
                                                },
                                                {
                                                    "status": "bounced",
                                                    "total_debit_amount": 20,
                                                    "net_debit_amount": 20,
                                                    "easepayid": "E24081209Y21W5",
                                                    "firstname": "sanket",
                                                    "phone": "7559177365",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "61W12BZZ",
                                                    "email": "sanket.pawar@easebuzz.in"
                                                },
                                                {
                                                    "status": "success",
                                                    "total_debit_amount": 1,
                                                    "net_debit_amount": 1,
                                                    "easepayid": "E24081209XTKEK",
                                                    "firstname": "Rajesh Kushwah",
                                                    "phone": "999999999",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "JRTJHGDUJNFJN",
                                                    "email": "rajesh.kushwahah@easebuzz.in"
                                                },
                                                {
                                                    "status": "success",
                                                    "total_debit_amount": 0,
                                                    "net_debit_amount": 1,
                                                    "easepayid": "E24081209XTIA7",
                                                    "firstname": "Rajesh",
                                                    "phone": "8085298536",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "2.0",
                                                    "txnid": "202401-123",
                                                    "email": "rajesh.kushwaha@easebuzz.in"
                                                },
                                                {
                                                    "status": "bounced",
                                                    "total_debit_amount": 5000,
                                                    "net_debit_amount": 5000,
                                                    "easepayid": "E24080809WBQ9F",
                                                    "firstname": "Rajesh",
                                                    "phone": "95755380504",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "TXN11234T1XNvc1112s12",
                                                    "email": "Rajesh.Kushwah@easebuzz.in"
                                                },
                                                {
                                                    "status": "bounced",
                                                    "total_debit_amount": 5000,
                                                    "net_debit_amount": 5000,
                                                    "easepayid": "E24080809WBPVD",
                                                    "firstname": "Rajesh",
                                                    "phone": "95755380504",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "TXN11234T1XNvc1112s12",
                                                    "email": "Rajesh.Kushwah@easebuzz.in"
                                                }
                                            ],
                                            "status": true
                                        }
                                    }
                                },
                                "examples": {
                                    "Example 1": {
                                        "value": {
                                            "next": "eyJwIjogIjU5ODU4NjAwMSIsICJ0IjogeyJlbmQiOiAzLjB9fQ==",
                                            "previous": null,
                                            "count": 20,
                                            "data": [
                                                {
                                                    "status": "success",
                                                    "total_debit_amount": 0,
                                                    "net_debit_amount": 1,
                                                    "easepayid": "E2408230A2BXYR",
                                                    "firstname": "test",
                                                    "phone": "+91-8000000000",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "1.00",
                                                    "txnid": "EC6GF4GQ2B",
                                                    "email": "john@gmail.com"
                                                },
                                                {
                                                    "status": "dropped",
                                                    "total_debit_amount": 1,
                                                    "net_debit_amount": 1,
                                                    "easepayid": "E2408230A2BXG4",
                                                    "firstname": "Test Account shopify rajesh.kushwaha",
                                                    "phone": "8008008000",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "FUGE8XZ56H",
                                                    "email": "john@gmail.com"
                                                },
                                                {
                                                    "status": "failure",
                                                    "total_debit_amount": 0,
                                                    "net_debit_amount": 1,
                                                    "easepayid": "E2408230A2BWD2",
                                                    "firstname": "test",
                                                    "phone": "+91-8000000000",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "1.00",
                                                    "txnid": "EC6GF4GQ2B",
                                                    "email": "john@gmail.com"
                                                },
                                                {
                                                    "status": "failure",
                                                    "total_debit_amount": 0,
                                                    "net_debit_amount": 1,
                                                    "easepayid": "E2408220A1ZI2D",
                                                    "firstname": "test",
                                                    "phone": "+91-8000000000",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "1.00",
                                                    "txnid": "EC6GF4GQ2B",
                                                    "email": "john@gmail.com"
                                                },
                                                {
                                                    "status": "bounced",
                                                    "total_debit_amount": 1,
                                                    "net_debit_amount": 1,
                                                    "easepayid": "E2408220A1ZHUI",
                                                    "firstname": "Test Account shopify rajesh.kushwaha",
                                                    "phone": "8008008000",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "8C0ISDKKGB",
                                                    "email": "john@gmail.com"
                                                },
                                                {
                                                    "status": "bounced",
                                                    "total_debit_amount": 1001,
                                                    "net_debit_amount": 1001,
                                                    "easepayid": "E2408220A1U6QL",
                                                    "firstname": "Rajesh& Kushwah",
                                                    "phone": "999999999",
                                                    "udf1": "RAJENDRA NAGAR C. BLOCK",
                                                    "udf2": "RN C - B-1358, Rajendra Nagar C. Block Awas Vikas Bareilly 243006",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "542e1412165ew8464612114",
                                                    "email": "ramzade@sociology.mu.ac.in"
                                                },
                                                {
                                                    "status": "userCancelled",
                                                    "total_debit_amount": 1,
                                                    "net_debit_amount": 1,
                                                    "easepayid": "E2408220A1SBNG",
                                                    "firstname": "Test Account shopify rajesh.kushwaha",
                                                    "phone": "8008008000",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "AZ8FPYSDAT",
                                                    "email": "john@gmail.com"
                                                },
                                                {
                                                    "status": "failure",
                                                    "total_debit_amount": 2,
                                                    "net_debit_amount": 2,
                                                    "easepayid": "E2408210A1EWGZ",
                                                    "firstname": "sanket",
                                                    "phone": "7559177365",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "RHIADGE0",
                                                    "email": "john@gmail.com"
                                                },
                                                {
                                                    "status": "failure",
                                                    "total_debit_amount": 2,
                                                    "net_debit_amount": 2,
                                                    "easepayid": "E2408210A1EVQY",
                                                    "firstname": "sanket",
                                                    "phone": "7559177365",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "46D7ZFMY",
                                                    "email": "john@gmail.com"
                                                },
                                                {
                                                    "status": "failure",
                                                    "total_debit_amount": 100,
                                                    "net_debit_amount": 100,
                                                    "easepayid": "E2408210A1E1Y7",
                                                    "firstname": "Rajesh Kushwah",
                                                    "phone": "+91-8085298536",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "542e1416qw5123q123w42e1132w82134646114",
                                                    "email": "john@gmail.com"
                                                },
                                                {
                                                    "status": "userCancelled",
                                                    "total_debit_amount": 100,
                                                    "net_debit_amount": 100,
                                                    "easepayid": "E2408210A1E1S4",
                                                    "firstname": "Rajesh Kushwah",
                                                    "phone": "+91-8085298536",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "542e1416qw5123q123w42e1132w82134646114",
                                                    "email": "john@gmail.com"
                                                },
                                                {
                                                    "status": "bounced",
                                                    "total_debit_amount": 1001,
                                                    "net_debit_amount": 1001,
                                                    "easepayid": "E2408210A1E0OD",
                                                    "firstname": "Rajesh Kushwah",
                                                    "phone": "999999999",
                                                    "udf1": "RAJENDRA NAGAR C. BLOCK",
                                                    "udf2": "RN C - B-1358, Rajendra Nagar C. Block Awas Vikas Bareilly 243006",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "542e1412165ew8464612114",
                                                    "email": "john@gmail.com"
                                                },
                                                {
                                                    "status": "userCancelled",
                                                    "total_debit_amount": 1001,
                                                    "net_debit_amount": 1001,
                                                    "easepayid": "E2408210A1E0AC",
                                                    "firstname": "Rajesh Kushwah",
                                                    "phone": "999999999",
                                                    "udf1": "RAJENDRA NAGAR C. BLOCK",
                                                    "udf2": "RN C - B-1358, Rajendra Nagar C. Block Awas Vikas Bareilly 243006",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "542e1412165ew8464612114",
                                                    "email": "ramzade@sociology.mu.ac.in"
                                                },
                                                {
                                                    "status": "bounced",
                                                    "total_debit_amount": 1,
                                                    "net_debit_amount": 1,
                                                    "easepayid": "E24081609ZICWX",
                                                    "firstname": "Test",
                                                    "phone": "+91-8085298536",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "20.00",
                                                    "txnid": "ECVLCMQJ3V",
                                                    "email": "john@gmail.com"
                                                },
                                                {
                                                    "status": "bounced",
                                                    "total_debit_amount": 2,
                                                    "net_debit_amount": 2,
                                                    "easepayid": "E24081409YNVBV",
                                                    "firstname": "sanket",
                                                    "phone": "7559177365",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "HPKQ8LBC",
                                                    "email": "john@gmail.com"
                                                },
                                                {
                                                    "status": "bounced",
                                                    "total_debit_amount": 20,
                                                    "net_debit_amount": 20,
                                                    "easepayid": "E24081209Y21W5",
                                                    "firstname": "sanket",
                                                    "phone": "7559177365",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "61W12BZZ",
                                                    "email": "john@gmail.com"
                                                },
                                                {
                                                    "status": "success",
                                                    "total_debit_amount": 1,
                                                    "net_debit_amount": 1,
                                                    "easepayid": "E24081209XTKEK",
                                                    "firstname": "Rajesh Kushwah",
                                                    "phone": "999999999",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "JRTJHGDUJNFJN",
                                                    "email": "john@gmail.com"
                                                },
                                                {
                                                    "status": "success",
                                                    "total_debit_amount": 0,
                                                    "net_debit_amount": 1,
                                                    "easepayid": "E24081209XTIA7",
                                                    "firstname": "Rajesh",
                                                    "phone": "8085298536",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "2.0",
                                                    "txnid": "202401-123",
                                                    "email": "john@gmail.com"
                                                },
                                                {
                                                    "status": "bounced",
                                                    "total_debit_amount": 5000,
                                                    "net_debit_amount": 5000,
                                                    "easepayid": "E24080809WBQ9F",
                                                    "firstname": "Rajesh",
                                                    "phone": "95755380504",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "TXN11234T1XNvc1112s12",
                                                    "email": "john@gmail.com"
                                                },
                                                {
                                                    "status": "bounced",
                                                    "total_debit_amount": 5000,
                                                    "net_debit_amount": 5000,
                                                    "easepayid": "E24080809WBPVD",
                                                    "firstname": "Rajesh K",
                                                    "phone": "95755380504",
                                                    "udf1": "",
                                                    "udf2": "",
                                                    "udf3": "",
                                                    "udf4": "",
                                                    "udf5": "",
                                                    "txnid": "TXN11234T1XNvc1112s12",
                                                    "email": "john@gmail.com"
                                                }
                                            ],
                                            "status": true
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                "servers": [
                    {
                        "url": "https://testdashboard.easebuzz.in",
                        "description": "Sandbox URL"
                    },
                    {
                        "url": "https://dashboard.easebuzz.in",
                        "description": "Production URL"
                    }
                ],
                "x-stoplight": {
                    "id": "xl5wyz91ugbpg"
                },
                "operationId": ""
            }
        }
    },
    "x-internal": false
}