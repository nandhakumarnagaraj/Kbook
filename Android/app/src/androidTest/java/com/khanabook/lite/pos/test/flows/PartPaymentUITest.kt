package com.khanabook.lite.pos.test.flows

import com.khanabook.lite.pos.test.BaseTest
import com.khanabook.lite.pos.test.robots.HomeRobot
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

@HiltAndroidTest
class PartPaymentUITest : BaseTest() {

    @Test
    fun testPartPaymentAmountFieldsVisibilityOnFocus() {
        val homeRobot = HomeRobot(composeTestRule)
        
        homeRobot
            .tapNewBill()
            .waitForMenuToLoad()
            .addItemToCart() // Adds default Burger
            .tapCheckout()
            .waitForCheckoutScreen()
            .selectPaymentMode("Cash + UPI")
            .tapAmountField("Cash Amount")
            .assertAmountFieldVisible("Cash Amount")
            .tapAmountField("UPI Amount")
            .assertAmountFieldVisible("UPI Amount")
    }
}
