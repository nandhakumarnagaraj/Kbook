package com.khanabook.lite.pos.test.screens

import com.khanabook.lite.pos.test.BaseTest
import com.khanabook.lite.pos.test.robots.HomeRobot
import com.khanabook.lite.pos.test.robots.LoginRobot
import com.khanabook.lite.pos.test.robots.SettingsRobot
import org.junit.Before
import org.junit.Test

class SettingsScreenTest : BaseTest() {

    private lateinit var homeRobot: HomeRobot
    private lateinit var settingsRobot: SettingsRobot
    private lateinit var loginRobot: LoginRobot

    @Before
    override fun setUp() {
        super.setUp()
        homeRobot = HomeRobot(composeTestRule)
        settingsRobot = SettingsRobot(composeTestRule)
        loginRobot = LoginRobot(composeTestRule)
        
        mockApiServer.enqueueLoginSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        
        loginRobot.submitLogin()
        homeRobot.waitForDataToLoad()
        homeRobot.tapSettingsTab()
    }

    @Test
    fun TC_LAYOUT_012_SettingsScreen_LayoutValid() {
        settingsRobot.assertSectionsVisible()
    }

    @Test
    fun TC_NAV_009_SettingsScreen_NavigationToMenuConfig() {
        settingsRobot
            .tapMenuSection()
            .tapMenuConfiguration()
            .assertModeSelectionVisible()
    }

    @Test
    fun TC_NAV_009_SettingsScreen_BackFromMenuConfig() {
        settingsRobot
            .tapMenuSection()
            .tapMenuConfiguration()
            .pressBack()
        
        settingsRobot.assertSectionsVisible()
    }

    @Test
    fun TC_SEC_001_SettingsScreen_LogoutFlow() {
        settingsRobot
            .scrollToLogout()
            .tapLogout()
            .assertLogoutConfirmationShown()
            .confirmLogout()
            .assertLoginScreenShown()
    }

    @Test
    fun TC_SEC_001_SettingsScreen_Logout_CancelFlow() {
        settingsRobot
            .scrollToLogout()
            .tapLogout()
            .assertLogoutConfirmationShown()
            .cancelLogout()
            .assertSectionsVisible()
    }

    @Test
    fun TC_OFFLINE_002_SettingsScreen_OfflineAccess() {
        disableNetwork()
        
        settingsRobot.assertSectionsVisible()
        
        enableNetwork()
    }
}
