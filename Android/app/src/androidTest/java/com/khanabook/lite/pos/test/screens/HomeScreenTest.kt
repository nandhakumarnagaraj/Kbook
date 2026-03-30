package com.khanabook.lite.pos.test.screens

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import com.khanabook.lite.pos.test.BaseTest
import com.khanabook.lite.pos.test.robots.HomeRobot
import com.khanabook.lite.pos.test.robots.LoginRobot
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test

@HiltAndroidTest
class HomeScreenTest : BaseTest() {

    private lateinit var homeRobot: HomeRobot
    private lateinit var loginRobot: LoginRobot

    @Before
    override fun setUp() {
        super.setUp()
        homeRobot = HomeRobot(composeTestRule)
        loginRobot = LoginRobot(composeTestRule)
        
        mockApiServer.enqueueLoginSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        
        loginRobot.submitLogin()
        homeRobot.waitForDataToLoad()
    }

    @Test
    fun TC_LAYOUT_005_MainScreen_BottomNavigationVisible() {
        homeRobot
            .assertBottomNavigationVisible()
            .assertQuickActionsVisible()
    }

    @Test
    fun TC_LAYOUT_006_HomeScreen_DashboardCardsVisible() {
        homeRobot
            .assertDashboardVisible()
            .assertMetricsCardsVisible()
    }

    @Test
    fun TC_NAV_007_HomeScreen_TabNavigation_OrdersTab() {
        homeRobot.tapOrdersTab().waitForOrdersToLoad()
        
        composeTestRule.onNode(hasText("Orders")).assertIsDisplayed()
    }

    @Test
    fun TC_NAV_007_HomeScreen_TabNavigation_ReportsTab() {
        homeRobot.tapReportsTab().waitForReportsToLoad()
        
        composeTestRule.onNode(hasText("Reports")).assertIsDisplayed()
    }

    @Test
    fun TC_NAV_007_HomeScreen_TabNavigation_SettingsTab() {
        homeRobot.tapSettingsTab()
        
        composeTestRule.onNode(hasText("Settings")).assertIsDisplayed()
    }

    @Test
    fun TC_NAV_005_HomeScreen_RapidTabSwitching() {
        repeat(5) {
            homeRobot.tapOrdersTab()
            homeRobot.tapReportsTab()
            homeRobot.tapHomeTab()
            homeRobot.tapSettingsTab()
        }
        
        homeRobot.assertBottomNavigationVisible()
    }

    @Test
    fun TC_STATE_001_HomeScreen_StatePreserved_AfterRotation() {
        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        homeRobot.assertDashboardVisible()
        
        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        
        homeRobot.assertDashboardVisible()
    }
}
