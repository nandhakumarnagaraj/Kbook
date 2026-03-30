package com.khanabook.lite.pos.test.flows

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.khanabook.lite.pos.test.BaseTest
import com.khanabook.lite.pos.test.robots.HomeRobot
import com.khanabook.lite.pos.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

@HiltAndroidTest
class MenuConfigUITest : BaseTest() {

    @Test
    fun testMenuConfigurationOptionsVisible() {
        val homeRobot = HomeRobot(composeTestRule)
        
        homeRobot
            .tapSettingsTab()
            .tapMenuConfiguration()
            .assertAddMenuItemsHeaderVisible()
            .assertManualEntryVisible()
            .assertSmartAIVisible()
            .tapSmartAI()
            .assertSmartAIOptionsVisible()
    }
}
