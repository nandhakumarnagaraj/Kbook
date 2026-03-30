package com.khanabook.lite.pos.test.screens

import android.content.pm.ActivityInfo
import com.khanabook.lite.pos.test.BaseTest
import com.khanabook.lite.pos.test.robots.CheckoutRobot
import com.khanabook.lite.pos.test.robots.HomeRobot
import com.khanabook.lite.pos.test.robots.LoginRobot
import com.khanabook.lite.pos.test.robots.NewBillRobot
import com.khanabook.lite.pos.test.util.TestData
import org.junit.After
import org.junit.Before
import org.junit.Test

class NewBillScreenTest : BaseTest() {

    private lateinit var homeRobot: HomeRobot
    private lateinit var newBillRobot: NewBillRobot
    private lateinit var loginRobot: LoginRobot

    @Before
    override fun setUp() {
        super.setUp()
        homeRobot = HomeRobot(composeTestRule)
        newBillRobot = NewBillRobot(composeTestRule)
        loginRobot = LoginRobot(composeTestRule)
        
        mockApiServer.enqueueLoginSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        
        loginRobot.submitLogin()
        homeRobot.waitForDataToLoad()
        homeRobot.tapNewBill().waitForMenuToLoad()
    }

    @After
    override fun tearDown() {
        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.tearDown()
    }

    @Test
    fun TC_LAYOUT_007_NewBillScreen_LayoutValid() {
        newBillRobot
            .assertCartEmpty()
            .assertCheckoutButtonDisabled()
    }

    @Test
    fun TC_LAYOUT_008_NewBillScreen_Performance_ManyItems() {
        TestData.MenuItems.POPULAR_ITEMS.forEach { item ->
            newBillRobot.addItemToCart(item)
        }
        newBillRobot.assertCartNotEmpty()
    }

    @Test
    fun TC_JOURNEY_001_NewBillScreen_CompleteSale_CashPayment() {
        mockApiServer.enqueueBillCreateSuccess()
        
        newBillRobot
            .addItemToCart(TestData.MenuItems.BURGER)
            .assertItemInCart(TestData.MenuItems.BURGER)
            .assertCheckoutButtonEnabled()
        
        val checkoutRobot = newBillRobot.tapCheckout()
        checkoutRobot.completePayment(CheckoutRobot.PaymentMethod.CASH)
        checkoutRobot.assertSuccessMessageShown()
    }

    @Test
    fun TC_JOURNEY_001_NewBillScreen_CompleteSale_CardPayment() {
        mockApiServer.enqueueBillCreateSuccess()
        
        newBillRobot
            .addItemToCart(TestData.MenuItems.PIZZA)
            .addItemToCart(TestData.MenuItems.COKE)
            .assertCheckoutButtonEnabled()
        
        val checkoutRobot = newBillRobot.tapCheckout()
        checkoutRobot.completePayment(CheckoutRobot.PaymentMethod.CARD)
        checkoutRobot.assertSuccessMessageShown()
    }

    @Test
    fun TC_JOURNEY_001_NewBillScreen_CompleteSale_UpiPayment() {
        mockApiServer.enqueueBillCreateSuccess()
        
        newBillRobot
            .addItemToCart(TestData.MenuItems.BURGER)
            .addItemToCart(TestData.MenuItems.PIZZA)
        
        val checkoutRobot = newBillRobot.tapCheckout()
        checkoutRobot.completePayment(CheckoutRobot.PaymentMethod.UPI)
        checkoutRobot.assertSuccessMessageShown()
    }

    @Test
    fun TC_API_009_NewBillScreen_SubmitEmptyCart() {
        newBillRobot
            .assertCheckoutButtonDisabled()
    }

    @Test
    fun TC_STATE_001_NewBillScreen_CartState_AfterRotation() {
        newBillRobot.addItemToCart(TestData.MenuItems.BURGER)
        newBillRobot.addItemToCart(TestData.MenuItems.PIZZA)
        
        rotateDevice(ScreenOrientation.LANDSCAPE)
        
        newBillRobot
            .assertItemInCart(TestData.MenuItems.BURGER)
            .assertItemInCart(TestData.MenuItems.PIZZA)
            .assertCartNotEmpty()
        
        rotateDevice(ScreenOrientation.PORTRAIT)
        
        newBillRobot
            .assertItemInCart(TestData.MenuItems.BURGER)
    }

    @Test
    fun TC_STATE_001_NewBillScreen_CartState_BackNavigation() {
        newBillRobot
            .addItemToCart(TestData.MenuItems.BURGER)
            .addItemToCart(TestData.MenuItems.PIZZA)
            .pressBack()
        
        homeRobot.tapNewBill().waitForMenuToLoad()
        
        newBillRobot
            .assertItemInCart(TestData.MenuItems.BURGER)
            .assertItemInCart(TestData.MenuItems.PIZZA)
    }

    @Test
    fun TC_NAV_004_NewBillScreen_BackStack_PreservesCart() {
        mockApiServer.enqueueBillCreateSuccess()
        
        newBillRobot.addItemToCart(TestData.MenuItems.BURGER)
        val checkoutRobot = newBillRobot.tapCheckout()
        checkoutRobot.selectCashPayment()
        
        pressBackKey()
        
        newBillRobot.assertItemInCart(TestData.MenuItems.BURGER)
    }

    @Test
    fun TC_VALIDATION_002_NewBillScreen_MultipleItemsValidation() {
        newBillRobot
            .addItemToCart(TestData.MenuItems.BURGER)
            .addItemToCart(TestData.MenuItems.PIZZA)
            .addItemToCart(TestData.MenuItems.COKE)
            .assertCheckoutButtonEnabled()
            .assertTotalVisible()
    }

    @Test
    fun TC_OFFLINE_001_NewBillScreen_CachedMenu_Offline() {
        disableNetwork()
        
        newBillRobot
            .addItemToCart(TestData.MenuItems.BURGER)
            .assertItemInCart(TestData.MenuItems.BURGER)
    }

    @Test
    fun TC_JOURNEY_002_Checkout_PaymentMethods() {
        newBillRobot.addItemToCart(TestData.MenuItems.BURGER)
        
        val checkoutRobot = newBillRobot.tapCheckout()
        checkoutRobot.assertPaymentMethodsVisible()
    }
}
