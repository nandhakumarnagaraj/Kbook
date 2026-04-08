package com.khanabook.lite.pos.test.screens

import androidx.compose.ui.test.*
import com.khanabook.lite.pos.test.BaseTest
import com.khanabook.lite.pos.test.robots.HomeRobot
import com.khanabook.lite.pos.test.robots.LoginRobot
import com.khanabook.lite.pos.test.robots.SettingsRobot
import org.junit.Before
import org.junit.Test

class MenuConfigurationTest : BaseTest() {

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
        settingsRobot.tapMenuSection().tapMenuConfiguration()
    }

    @Test
    fun TC_MENU_001_ModeSelection_Layout() {
        val menuRobot = settingsRobot.tapMenuConfiguration()
        menuRobot.assertSmartAIVisible()
        menuRobot.assertManualEntryVisible()
    }

    @Test
    fun TC_MENU_002_SmartAI_Expansion() {
        val menuRobot = settingsRobot.tapMenuConfiguration()
        menuRobot.tapSmartAI()
        menuRobot.assertSmartAIOptionsVisible()
    }
}
