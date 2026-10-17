package com.khanabook.lite.pos.test.robots

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule

class SettingsRobot(private val composeTestRule: AndroidComposeTestRule<*, *>) {

    private val profileSectionMatcher = hasText("Profile", substring = true)
    private val menuSectionMatcher = hasText("Menu", substring = true)
    private val paymentSectionMatcher = hasText("Payment", substring = true)
    private val printerSectionMatcher = hasText("Printer", substring = true)
    private val shopSectionMatcher = hasText("Shop", substring = true)
    private val aboutSectionMatcher = hasText("About", substring = true)
    private val logoutButtonMatcher = hasText("Logout", substring = true).or(hasText("Sign Out"))
    private val menuConfigMatcher = hasText("Menu Configuration", substring = true)

    fun tapProfileSection(): SettingsRobot {
        composeTestRule.onNode(profileSectionMatcher).performClick()
        return this
    }

    fun tapMenuSection(): SettingsRobot {
        composeTestRule.onNode(menuSectionMatcher).performClick()
        return this
    }

    fun tapMenuConfiguration(): MenuConfigurationRobot {
        composeTestRule.onNode(menuConfigMatcher).performClick()
        return MenuConfigurationRobot(composeTestRule)
    }

    fun tapPaymentSection(): SettingsRobot {
        composeTestRule.onNode(paymentSectionMatcher).performClick()
        return this
    }

    fun tapPrinterSection(): SettingsRobot {
        composeTestRule.onNode(printerSectionMatcher).performClick()
        return this
    }

    fun tapShopSection(): SettingsRobot {
        composeTestRule.onNode(shopSectionMatcher).performClick()
        return this
    }

    fun tapAboutSection(): SettingsRobot {
        composeTestRule.onNode(aboutSectionMatcher).performClick()
        return this
    }

    fun tapLogout(): LogoutRobot {
        composeTestRule.onNode(logoutButtonMatcher).performClick()
        return LogoutRobot(composeTestRule)
    }

    fun scrollToLogout(): SettingsRobot {
        composeTestRule.onNode(logoutButtonMatcher).performScrollTo()
        return this
    }

    fun assertSectionsVisible(): SettingsRobot {
        listOf(
            profileSectionMatcher,
            menuSectionMatcher,
            paymentSectionMatcher,
            printerSectionMatcher
        ).forEach { matcher ->
            composeTestRule.onNode(matcher).assertIsDisplayed()
        }
        return this
    }

    fun assertLogoutButtonVisible(): SettingsRobot {
        scrollToLogout()
        composeTestRule.onNode(logoutButtonMatcher).assertIsDisplayed()
        return this
    }

    fun pressBack(): SettingsRobot {
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        return this
    }
}

class MenuConfigurationRobot(private val composeTestRule: AndroidComposeTestRule<*, *>) {

    private val addMenuItemsMatcher = hasText("Add Menu Items", substring = true)
    private val manualEntryMatcher = hasText("Manual Entry", substring = true)
    private val smartAIMatcher = hasText("Smart AI", substring = true)
    
    private val cameraOptionMatcher = hasText("Camera", substring = true)
    private val galleryOptionMatcher = hasText("Gallery", substring = true)
    private val pdfOptionMatcher = hasText("PDF", substring = true)

    fun assertAddMenuItemsHeaderVisible(): MenuConfigurationRobot {
        composeTestRule.onNode(addMenuItemsMatcher).assertIsDisplayed()
        return this
    }

    // Backwards compatibility methods
    fun assertModeSelectionVisible(): MenuConfigurationRobot = assertAddMenuItemsHeaderVisible()
    fun assertManualModeVisible(): MenuConfigurationRobot = assertManualEntryVisible()
    fun assertSmartImportVisible(): MenuConfigurationRobot = assertSmartAIVisible()
    fun selectManualMode(): MenuConfigurationRobot = tapManualEntry()
    fun selectSmartImport(): MenuConfigurationRobot = tapSmartAI()

    fun assertManualEntryVisible(): MenuConfigurationRobot {
        composeTestRule.onNode(manualEntryMatcher).assertIsDisplayed()
        return this
    }

    fun tapManualEntry(): MenuConfigurationRobot {
        composeTestRule.onNode(manualEntryMatcher).performClick()
        return this
    }

    fun assertSmartAIVisible(): MenuConfigurationRobot {
        composeTestRule.onNode(smartAIMatcher).assertIsDisplayed()
        return this
    }

    fun tapSmartAI(): MenuConfigurationRobot {
        composeTestRule.onNode(smartAIMatcher).performClick()
        return this
    }

    fun assertSmartAIOptionsVisible(): MenuConfigurationRobot {
        composeTestRule.onNode(cameraOptionMatcher).assertIsDisplayed()
        composeTestRule.onNode(galleryOptionMatcher).assertIsDisplayed()
        composeTestRule.onNode(pdfOptionMatcher).assertIsDisplayed()
        return this
    }

    fun tapCamera(): MenuConfigurationRobot {
        composeTestRule.onNode(cameraOptionMatcher).performClick()
        return this
    }

    fun tapGallery(): MenuConfigurationRobot {
        composeTestRule.onNode(galleryOptionMatcher).performClick()
        return this
    }

    fun tapPDF(): MenuConfigurationRobot {
        composeTestRule.onNode(pdfOptionMatcher).performClick()
        return this
    }

    fun pressBack(): MenuConfigurationRobot {
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        return this
    }
}

class LogoutRobot(private val composeTestRule: AndroidComposeTestRule<*, *>) {

    private val confirmButtonMatcher = hasText("Confirm", substring = true).or(hasText("Logout"))
    private val cancelButtonMatcher = hasText("Cancel", substring = true)

    fun confirmLogout(): LogoutRobot {
        composeTestRule.onNode(confirmButtonMatcher).performClick()
        return this
    }

    fun cancelLogout(): SettingsRobot {
        composeTestRule.onNode(cancelButtonMatcher).performClick()
        return SettingsRobot(composeTestRule)
    }

    fun assertLogoutConfirmationShown(): LogoutRobot {
        composeTestRule.onNode(confirmButtonMatcher).assertIsDisplayed()
        composeTestRule.onNode(cancelButtonMatcher).assertIsDisplayed()
        return this
    }

    fun assertLoginScreenShown(): LogoutRobot {
        composeTestRule.waitUntil(5000) {
            try {
                composeTestRule.onAllNodes(hasText("Login", substring = true))
                    .onFirst()
                    .assertExists()
                true
            } catch (e: Exception) {
                false
            }
        }
        return this
    }
}
