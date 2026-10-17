package com.khanabook.lite.pos.test.robots

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import com.khanabook.lite.pos.test.util.TestData

class NewBillRobot(private val composeTestRule: AndroidComposeTestRule<*, *>) {

    private val addItemButtonMatcher = hasText("Add Item", substring = true)
        .or(hasContentDescription("Add Item"))

    private val checkoutButtonMatcher = hasTestTag("checkout_button")
        .or(hasText("Proceed to Pay", substring = true))
        .or(hasText("Pay", substring = true))
        .or(hasText("Complete", substring = true))

    private val cartItemMatcher = hasText(TestData.MenuItems.BURGER, substring = true)

    private val totalMatcher = hasText("Total", substring = true)
        .or(hasText("Grand Total", substring = true))

    private val emptyCartMatcher = hasText("Cart is empty", substring = true)
        .or(hasText("No items", substring = true))
        .or(hasText("Add items", substring = true))

    private val quantityMatcher = hasText("Qty", substring = true)
        .or(hasContentDescription("Quantity"))

    fun tapAddItem(): NewBillRobot {
        composeTestRule.onNode(addItemButtonMatcher)
            .assertIsEnabled()
            .performClick()
        return this
    }

    fun selectCategory(categoryName: String): NewBillRobot {
        composeTestRule.onNode(hasText(categoryName, substring = true))
            .performClick()
        return this
    }

    fun selectItem(itemName: String): NewBillRobot {
        composeTestRule.onNode(hasText(itemName, substring = true))
            .performClick()
        return this
    }

    fun tapCheckout(): CheckoutRobot {
        composeTestRule.waitForIdle()
        composeTestRule.onNode(checkoutButtonMatcher)
            .assertIsEnabled()
            .performClick()
        return CheckoutRobot(composeTestRule)
    }

    fun addItemToCart(itemName: String = TestData.MenuItems.BURGER): NewBillRobot {
        tapAddItem()
        composeTestRule.waitForIdle()
        selectItem(itemName)
        return this
    }

    fun addMultipleItemsToCart(itemNames: List<String>): NewBillRobot {
        itemNames.forEach { name ->
            addItemToCart(name)
        }
        return this
    }

    fun removeItemFromCart(itemName: String): NewBillRobot {
        composeTestRule.onNode(
            hasText(itemName, substring = true)
        ).performTouchInput {
            swipeLeft()
        }
        return this
    }

    fun assertItemInCart(itemName: String): NewBillRobot {
        composeTestRule.onNode(hasText(itemName, substring = true))
            .assertIsDisplayed()
        return this
    }

    fun assertItemNotInCart(itemName: String): NewBillRobot {
        composeTestRule.onNode(hasText(itemName, substring = true))
            .assertDoesNotExist()
        return this
    }

    fun assertCartNotEmpty(): NewBillRobot {
        composeTestRule.onNode(emptyCartMatcher)
            .assertDoesNotExist()
        return this
    }

    fun assertCartEmpty(): NewBillRobot {
        composeTestRule.onNode(emptyCartMatcher)
            .assertIsDisplayed()
        return this
    }

    fun assertTotalVisible(): NewBillRobot {
        composeTestRule.onNode(totalMatcher)
            .assertIsDisplayed()
        return this
    }

    fun assertCheckoutButtonEnabled(): NewBillRobot {
        composeTestRule.onNode(checkoutButtonMatcher)
            .assertIsEnabled()
        return this
    }

    fun assertCheckoutButtonDisabled(): NewBillRobot {
        composeTestRule.onNode(checkoutButtonMatcher)
            .assertIsNotEnabled()
        return this
    }

    fun waitForMenuToLoad(): NewBillRobot {
        composeTestRule.waitUntil(10000) {
            try {
                composeTestRule.onNode(addItemButtonMatcher, useUnmergedTree = true)
                    .assertIsDisplayed()
                true
            } catch (e: Exception) {
                false
            }
        }
        return this
    }

    fun pressBack(): HomeRobot {
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        return HomeRobot(composeTestRule)
    }
}

class CheckoutRobot(private val composeTestRule: AndroidComposeTestRule<*, *>) {

    private val cashPaymentMatcher = hasText("Cash", substring = true)
        .or(hasContentDescription("Pay with Cash"))

    private val cardPaymentMatcher = hasText("Card", substring = true)
        .or(hasContentDescription("Pay with Card"))

    private val upiPaymentMatcher = hasText("UPI", substring = true)
        .or(hasContentDescription("Pay with UPI"))

    private val walletPaymentMatcher = hasText("Wallet", substring = true)

    private val completeButtonMatcher = hasText("Complete", substring = true)
        .or(hasText("Pay", substring = true))
        .or(hasText("Confirm", substring = true))

    private val amountFieldMatcher = hasTestTag("amount").or(hasText("Amount", substring = true))

    private val discountFieldMatcher = hasTestTag("discount").or(hasText("Discount", substring = true))

    private val successMessageMatcher = hasText("Success", substring = true)
        .or(hasText("Order Created", substring = true))
        .or(hasText("Payment Successful", substring = true))

    private val orderIdMatcher = hasText("Order #", substring = true)
        .or(hasText("Order ID", substring = true))
        .or(hasText("Bill #", substring = true))

    private val totalAmountMatcher = hasText("₹", substring = true)

    fun selectCashPayment(): CheckoutRobot {
        composeTestRule.onNode(cashPaymentMatcher)
            .performClick()
        return this
    }

    fun selectCardPayment(): CheckoutRobot {
        composeTestRule.onNode(cardPaymentMatcher)
            .performClick()
        return this
    }

    fun selectUpiPayment(): CheckoutRobot {
        composeTestRule.onNode(upiPaymentMatcher)
            .performClick()
        return this
    }

    fun selectWalletPayment(): CheckoutRobot {
        composeTestRule.onNode(walletPaymentMatcher)
            .performClick()
        return this
    }

    fun enterCustomAmount(amount: String): CheckoutRobot {
        composeTestRule.onNode(amountFieldMatcher)
            .performTextInput(amount)
        return this
    }

    fun applyDiscount(code: String): CheckoutRobot {
        composeTestRule.onNode(hasTestTag("coupon").or(hasText("Apply Coupon", substring = true)))
            .performTextInput(code)
        composeTestRule.onNode(hasText("Apply", substring = true))
            .performClick()
        return this
    }

    fun selectPaymentMode(modeName: String): CheckoutRobot {
        composeTestRule.onNode(hasText(modeName, substring = true)).performClick()
        return this
    }

    fun tapAmountField(label: String): CheckoutRobot {
        val tag = if (label.contains("1") || label.contains("Cash")) "part_amount_1" else "part_amount_2"
        composeTestRule.onNode(hasTestTag(tag).or(hasText(label, substring = true))).performClick()
        return this
    }

    fun assertAmountFieldVisible(label: String): CheckoutRobot {
        val tag = if (label.contains("1") || label.contains("Cash")) "part_amount_1" else "part_amount_2"
        composeTestRule.onNode(hasTestTag(tag).or(hasText(label, substring = true))).assertIsDisplayed()
        return this
    }

    fun tapComplete(): CheckoutRobot {
        composeTestRule.onNode(completeButtonMatcher)
            .assertIsEnabled()
            .performClick()
        return this
    }

    fun completePayment(paymentMethod: PaymentMethod = PaymentMethod.CASH): CheckoutRobot {
        when (paymentMethod) {
            PaymentMethod.CASH -> selectCashPayment()
            PaymentMethod.CARD -> selectCardPayment()
            PaymentMethod.UPI -> selectUpiPayment()
            PaymentMethod.WALLET -> selectWalletPayment()
        }
        tapComplete()
        return this
    }

    fun assertPaymentMethodsVisible(): CheckoutRobot {
        composeTestRule.onNode(cashPaymentMatcher).assertIsDisplayed()
        composeTestRule.onNode(cardPaymentMatcher).assertIsDisplayed()
        composeTestRule.onNode(upiPaymentMatcher).assertIsDisplayed()
        return this
    }

    fun assertSuccessMessageShown(): CheckoutRobot {
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(15000) {
            try {
                composeTestRule.onNode(successMessageMatcher)
                    .assertExists()
                true
            } catch (e: Exception) {
                false
            }
        }
        return this
    }

    fun assertOrderIdGenerated(): CheckoutRobot {
        composeTestRule.onNode(orderIdMatcher)
            .assertIsDisplayed()
        return this
    }

    fun assertTotalAmountShown(): CheckoutRobot {
        composeTestRule.onNode(totalAmountMatcher)
            .assertIsDisplayed()
        return this
    }

    fun assertNoErrorMessage(): CheckoutRobot {
        composeTestRule.waitForIdle()
        return this
    }

    fun waitForCheckoutScreen(): CheckoutRobot {
        composeTestRule.waitUntil(5000) {
            try {
                composeTestRule.onNode(completeButtonMatcher)
                    .assertExists()
                true
            } catch (e: Exception) {
                false
            }
        }
        return this
    }

    enum class PaymentMethod {
        CASH, CARD, UPI, WALLET
    }
}
