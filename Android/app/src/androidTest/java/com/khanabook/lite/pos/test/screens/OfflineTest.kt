package com.khanabook.lite.pos.test.screens

import com.khanabook.lite.pos.test.BaseTest
import com.khanabook.lite.pos.test.robots.HomeRobot
import com.khanabook.lite.pos.test.robots.LoginRobot
import com.khanabook.lite.pos.test.robots.NewBillRobot
import com.khanabook.lite.pos.test.robots.OrdersRobot
import com.khanabook.lite.pos.test.util.TestData
import org.junit.Before
import org.junit.Test

class OfflineTest : BaseTest() {

    private lateinit var loginRobot: LoginRobot
    private lateinit var homeRobot: HomeRobot
    private lateinit var ordersRobot: OrdersRobot
    private lateinit var newBillRobot: NewBillRobot

    @Before
    override fun setUp() {
        super.setUp()
        loginRobot = LoginRobot(composeTestRule)
        homeRobot = HomeRobot(composeTestRule)
        ordersRobot = OrdersRobot(composeTestRule)
        newBillRobot = NewBillRobot(composeTestRule)
    }

    @Test
    fun TC_OFFLINE_001_Login_FailsGracefully_Offline() {
        disableNetwork()
        
        loginRobot
            .enterCredentials()
            .tapLogin()
            .assertErrorMessageVisible()
    }

    @Test
    fun TC_OFFLINE_001_HomeScreen_CachedData_Offline() {
        mockApiServer.enqueueLoginSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        
        loginRobot.submitLogin()
        homeRobot.waitForDataToLoad()
        
        disableNetwork()
        
        homeRobot
            .assertDashboardVisible()
            .assertMetricsCardsVisible()
    }

    @Test
    fun TC_OFFLINE_001_NewBillScreen_CachedMenu_Offline() {
        mockApiServer.enqueueLoginSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        
        loginRobot.submitLogin()
        homeRobot.waitForDataToLoad()
        
        disableNetwork()
        
        homeRobot.tapNewBill().waitForMenuToLoad()
        
        newBillRobot
            .addItemToCart(TestData.MenuItems.BURGER)
            .assertItemInCart(TestData.MenuItems.BURGER)
    }

    @Test
    fun TC_OFFLINE_001_OrdersScreen_CachedData_Offline() {
        mockApiServer.enqueueLoginSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        
        loginRobot.submitLogin()
        homeRobot.waitForDataToLoad()
        
        homeRobot.tapOrdersTab().waitForOrdersToLoad()
        
        disableNetwork()
        
        ordersRobot.assertOrdersListNotEmpty()
    }

    @Test
    fun TC_OFFLINE_002_NetworkDropsMidRequest_HandledGracefully() {
        mockApiServer.enqueueLoginSuccess()
        
        loginRobot.enterCredentials()
        loginRobot.tapLogin()
        
        disableNetwork()
        
        loginRobot.assertErrorMessageVisible()
        
        enableNetwork()
    }

    @Test
    fun TC_OFFLINE_001_Reconnect_RefreshesData() {
        mockApiServer.enqueueLoginSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        
        loginRobot.submitLogin()
        homeRobot.waitForDataToLoad()
        
        disableNetwork()
        
        homeRobot.assertDashboardVisible()
        
        enableNetwork()
        
        homeRobot
            .pullToRefresh()
            .assertMetricsCardsVisible()
    }
}
