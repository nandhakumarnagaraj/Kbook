package com.khanabook.lite.pos.test.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import com.khanabook.lite.pos.test.BaseTest
import com.khanabook.lite.pos.test.robots.CheckoutRobot
import com.khanabook.lite.pos.test.robots.HomeRobot
import com.khanabook.lite.pos.test.robots.LoginRobot
import com.khanabook.lite.pos.test.robots.NewBillRobot
import com.khanabook.lite.pos.test.robots.OrdersRobot
import com.khanabook.lite.pos.test.util.TestData
import org.junit.Before
import org.junit.Test

class OrdersScreenTest : BaseTest() {

    private lateinit var homeRobot: HomeRobot
    private lateinit var ordersRobot: OrdersRobot
    private lateinit var newBillRobot: NewBillRobot
    private lateinit var loginRobot: LoginRobot

    @Before
    override fun setUp() {
        super.setUp()
        homeRobot = HomeRobot(composeTestRule)
        ordersRobot = OrdersRobot(composeTestRule)
        newBillRobot = NewBillRobot(composeTestRule)
        loginRobot = LoginRobot(composeTestRule)
        
        mockApiServer.enqueueLoginSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        
        loginRobot.submitLogin()
        homeRobot.waitForDataToLoad()
    }

    private fun createTestOrder() {
        homeRobot.tapNewBill().waitForMenuToLoad()
        newBillRobot.addItemToCart(TestData.MenuItems.BURGER)
        mockApiServer.enqueueBillCreateSuccess()
        newBillRobot.tapCheckout().completePayment(CheckoutRobot.PaymentMethod.CASH)
        
        composeTestRule.waitUntil(5000) {
            try {
                composeTestRule.onNode(hasText("Order Created", substring = true))
                    .assertExists()
                true
            } catch (e: Exception) {
                false
            }
        }
        
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    }

    @Test
    fun TC_LAYOUT_009_OrdersScreen_LayoutValid() {
        homeRobot.tapOrdersTab()
        
        ordersRobot
            .waitForOrdersToLoad()
            .assertOrdersListNotEmpty()
    }

    @Test
    fun TC_LAYOUT_009_OrdersScreen_EmptyState() {
        mockApiServer.enqueueEmptyOrders()
        
        homeRobot.tapOrdersTab()
        
        ordersRobot
            .waitForOrdersToLoad()
            .assertEmptyStateShown()
    }

    @Test
    fun TC_NAV_004_OrdersScreen_NavigateToDetail() {
        createTestOrder()
        
        homeRobot.tapOrdersTab().waitForOrdersToLoad()
        
        ordersRobot
            .tapOrder(0)
            .assertOrderDetailsVisible()
            .assertTotalAmountVisible()
            .assertPrintButtonVisible()
    }

    @Test
    fun TC_NAV_004_OrdersScreen_BackFromDetail() {
        createTestOrder()
        
        homeRobot.tapOrdersTab().waitForOrdersToLoad()
        
        ordersRobot
            .tapOrder(0)
            .pressBack()
    }

    @Test
    fun TC_JOURNEY_003_OrdersScreen_ViewAndUpdateStatus() {
        createTestOrder()
        
        homeRobot.tapOrdersTab().waitForOrdersToLoad()
        
        ordersRobot
            .tapOrder(0)
            .tapUpdateStatus()
            .selectNewStatus(TestData.OrderStatuses.COMPLETED)
            .confirmStatusUpdate()
        
        composeTestRule.onNode(
            hasText(TestData.OrderStatuses.COMPLETED, substring = true)
        ).assertIsDisplayed()
    }

    @Test
    fun TC_API_012_OrdersScreen_FilterByStatus() {
        mockApiServer.enqueueFilteredOrders(TestData.OrderStatuses.PENDING)
        
        homeRobot.tapOrdersTab()
        
        ordersRobot
            .waitForOrdersToLoad()
            .filterByStatus(OrdersRobot.OrderStatus.PENDING)
            .assertPendingOrdersVisible()
    }

    @Test
    fun TC_API_012_OrdersScreen_SearchOrder() {
        createTestOrder()
        
        homeRobot.tapOrdersTab().waitForOrdersToLoad()
        
        ordersRobot.searchOrder("ORD-001")
    }

    @Test
    fun TC_OFFLINE_001_OrdersScreen_OfflineMode() {
        disableNetwork()
        
        homeRobot.tapOrdersTab()
        
        ordersRobot
            .waitForOrdersToLoad()
            .assertOrdersListNotEmpty()
        
        enableNetwork()
    }
}
