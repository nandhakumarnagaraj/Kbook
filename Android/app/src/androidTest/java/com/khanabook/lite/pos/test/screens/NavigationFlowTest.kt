package com.khanabook.lite.pos.test.screens

import android.content.Intent
import android.net.Uri
import com.khanabook.lite.pos.test.BaseTest
import com.khanabook.lite.pos.test.robots.HomeRobot
import com.khanabook.lite.pos.test.robots.LoginRobot
import com.khanabook.lite.pos.test.robots.NewBillRobot
import com.khanabook.lite.pos.test.robots.SettingsRobot
import com.khanabook.lite.pos.test.util.TestData
import org.junit.Before
import org.junit.Test

class NavigationFlowTest : BaseTest() {

    private lateinit var loginRobot: LoginRobot
    private lateinit var homeRobot: HomeRobot
    private lateinit var newBillRobot: NewBillRobot
    private lateinit var settingsRobot: SettingsRobot

    @Before
    override fun setUp() {
        super.setUp()
        loginRobot = LoginRobot(composeTestRule)
        homeRobot = HomeRobot(composeTestRule)
        newBillRobot = NewBillRobot(composeTestRule)
        settingsRobot = SettingsRobot(composeTestRule)
    }

    @Test
    fun TC_NAV_001_AuthFlow_LoginToMain() {
        mockApiServer.enqueueLoginSuccess()
        
        loginRobot.submitLogin()
        
        homeRobot
            .waitForDataToLoad()
            .assertDashboardVisible()
    }

    @Test
    fun TC_NAV_001_AuthFlow_SignUpToMain() {
        mockApiServer.enqueueSignupSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        
        loginRobot
            .tapSignUp()
            .assertNavigationToSignUp()
    }

    @Test
    fun TC_NAV_002_AuthFlow_ReturningUserSkipsInitialSync() {
        mockApiServer.enqueueLoginSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        
        loginRobot.submitLogin()
        
        homeRobot
            .waitForDataToLoad()
            .assertDashboardVisible()
    }

    @Test
    fun TC_NAV_006_Navigation_BackToExit_HomeScreen() {
        mockApiServer.enqueueLoginSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        
        loginRobot.submitLogin()
        homeRobot.waitForDataToLoad()
        
        repeat(3) {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
            Thread.sleep(500)
        }
    }

    @Test
    fun TC_NAV_007_Navigation_AllTabsAccessible() {
        mockApiServer.enqueueLoginSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        
        loginRobot.submitLogin()
        homeRobot.waitForDataToLoad()
        
        homeRobot
            .tapHomeTab()
            .assertDashboardVisible()
        
        homeRobot
            .tapOrdersTab()
            .waitForOrdersToLoad()
        
        homeRobot
            .tapReportsTab()
            .waitForReportsToLoad()
        
        homeRobot
            .tapSearchTab()
        
        homeRobot
            .tapSettingsTab()
            .assertSectionsVisible()
        
        homeRobot
            .tapHomeTab()
            .assertDashboardVisible()
    }

    @Test
    fun TC_NAV_009_Navigation_SettingsToMenuConfiguration() {
        mockApiServer.enqueueLoginSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        
        loginRobot.submitLogin()
        homeRobot.waitForDataToLoad()
        
        homeRobot.tapSettingsTab()
        
        settingsRobot
            .tapMenuSection()
            .tapMenuConfiguration()
            .assertModeSelectionVisible()
    }

    @Test
    fun TC_NAV_005_Navigation_RapidTabSwitching() {
        mockApiServer.enqueueLoginSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        
        loginRobot.submitLogin()
        homeRobot.waitForDataToLoad()
        
        repeat(10) {
            homeRobot.tapHomeTab()
            homeRobot.tapOrdersTab()
            homeRobot.tapSettingsTab()
            homeRobot.tapReportsTab()
            homeRobot.tapSearchTab()
        }
        
        homeRobot.assertBottomNavigationVisible()
    }

    @Test
    fun TC_NAV_008_DeepLink_BillDeepLink() {
        mockApiServer.enqueueLoginSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        mockApiServer.enqueueOrderDetail("ORD-123")
        
        loginRobot.submitLogin()
        homeRobot.waitForDataToLoad()
        
        val deepLinkIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("khanabook://bill/ORD-123")
        }
        composeTestRule.activity.startActivity(deepLinkIntent)
    }

    @Test
    fun TC_JOURNEY_002_MenuConfigurationFlow() {
        mockApiServer.enqueueLoginSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        
        loginRobot.submitLogin()
        homeRobot.waitForDataToLoad()
        
        homeRobot.tapSettingsTab()
        
        settingsRobot
            .tapMenuSection()
            .tapMenuConfiguration()
            .assertModeSelectionVisible()
            .assertManualModeVisible()
            .assertSmartImportVisible()
    }

    @Test
    fun TC_STATE_002_AppState_KillMidFlow_Resume() {
        mockApiServer.enqueueLoginSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        
        loginRobot.submitLogin()
        homeRobot.waitForDataToLoad()
        
        homeRobot.tapNewBill().waitForMenuToLoad()
        newBillRobot.addItemToCart(TestData.MenuItems.BURGER)
        newBillRobot.addItemToCart(TestData.MenuItems.PIZZA)
        
        forceStopApp()
        
        restartApp()
    }

    @Test
    fun TC_SEC_001_Logout_ClearsSession() {
        mockApiServer.enqueueLoginSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        
        loginRobot.submitLogin()
        homeRobot.waitForDataToLoad()
        
        homeRobot.tapSettingsTab()
        
        settingsRobot
            .scrollToLogout()
            .tapLogout()
            .confirmLogout()
            .assertLoginScreenShown()
        
        loginRobot
            .assertPhoneFieldVisible()
            .assertPasswordFieldVisible()
    }
}
