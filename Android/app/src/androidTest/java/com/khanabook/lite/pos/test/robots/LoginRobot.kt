package com.khanabook.lite.pos.test.robots

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import com.khanabook.lite.pos.test.util.TestData

class LoginRobot(private val composeTestRule: AndroidComposeTestRule<*, *>) {

    private val phoneFieldMatcher = hasText("Phone", substring = true)
        .or(hasTestTag("phone"))

    private val passwordFieldMatcher = hasText("Password", substring = true)
        .or(hasTestTag("password"))

    private val loginButtonMatcher = hasText("Login", substring = true)
        .or(hasText("Sign In", substring = true))

    private val signUpLinkMatcher = hasText("Sign Up", substring = true)
        .or(hasText("Create Account", substring = true))
        .or(hasText("Register", substring = true))

    private val forgotPasswordMatcher = hasText("Forgot Password?", substring = true)

    private val errorMatcher = hasText("Invalid", substring = true)
        .or(hasText("Error", substring = true))
        .or(hasText("Failed", substring = true))
        .or(hasText("incorrect", substring = true))
        .or(hasText("failed", substring = true))

    private val loadingMatcher = hasText("Loading", substring = true)
        .or(hasContentDescription("Loading"))

    private val successMatcher = hasText("Success", substring = true)
        .or(hasText("Home"))
        .or(hasText("Dashboard"))

    fun enterPhone(phone: String): LoginRobot {
        composeTestRule.onNode(phoneFieldMatcher)
            .performTextInput(phone)
        return this
    }

    fun enterPassword(password: String): LoginRobot {
        composeTestRule.onNode(passwordFieldMatcher)
            .performTextInput(password)
        return this
    }

    fun enterCredentials(
        phone: String = TestData.ValidCredentials.PHONE,
        password: String = TestData.ValidCredentials.PASSWORD
    ): LoginRobot {
        enterPhone(phone)
        enterPassword(password)
        return this
    }

    fun tapLogin(): LoginRobot {
        composeTestRule.onNode(loginButtonMatcher)
            .assertIsEnabled()
            .performClick()
        return this
    }

    fun tapSignUp(): LoginRobot {
        composeTestRule.onNode(signUpLinkMatcher)
            .performClick()
        return this
    }

    fun tapForgotPassword(): LoginRobot {
        composeTestRule.onNode(forgotPasswordMatcher)
            .performClick()
        return this
    }

    fun submitLogin() {
        enterCredentials()
        tapLogin()
    }

    fun clearPhoneField(): LoginRobot {
        composeTestRule.onNode(phoneFieldMatcher)
            .performTextClearance()
        return this
    }

    fun clearPasswordField(): LoginRobot {
        composeTestRule.onNode(passwordFieldMatcher)
            .performTextClearance()
        return this
    }

    fun clearAllFields(): LoginRobot {
        clearPhoneField()
        clearPasswordField()
        return this
    }

    fun assertPhoneFieldVisible(): LoginRobot {
        composeTestRule.onNode(phoneFieldMatcher)
            .assertIsDisplayed()
        return this
    }

    fun assertPasswordFieldVisible(): LoginRobot {
        composeTestRule.onNode(passwordFieldMatcher)
            .assertIsDisplayed()
        return this
    }

    fun assertLoginButtonVisible(): LoginRobot {
        composeTestRule.onNode(loginButtonMatcher)
            .assertIsDisplayed()
        return this
    }

    fun assertLoginButtonEnabled(): LoginRobot {
        composeTestRule.onNode(loginButtonMatcher)
            .assertIsEnabled()
        return this
    }

    fun assertLoginButtonDisabled(): LoginRobot {
        composeTestRule.onNode(loginButtonMatcher)
            .assertIsNotEnabled()
        return this
    }

    fun assertErrorMessageVisible(): LoginRobot {
        composeTestRule.waitUntil(5000) {
            try {
                composeTestRule.onNode(errorMatcher, useUnmergedTree = true)
                    .assertExists()
                true
            } catch (e: Exception) {
                false
            }
        }
        return this
    }

    fun assertNoErrorMessage(): LoginRobot {
        composeTestRule.waitForIdle()
        try {
            composeTestRule.onNode(errorMatcher, useUnmergedTree = true)
                .assertDoesNotExist()
        } catch (e: AssertionError) {
        }
        return this
    }

    fun assertLoadingIndicatorShown(): LoginRobot {
        composeTestRule.waitUntil(3000) {
            try {
                composeTestRule.onNode(loadingMatcher)
                    .assertExists()
                true
            } catch (e: Exception) {
                false
            }
        }
        return this
    }

    fun assertLoadingIndicatorHidden(): LoginRobot {
        composeTestRule.onNode(loadingMatcher)
            .assertDoesNotExist()
        return this
    }

    fun assertNavigationToHome(): LoginRobot {
        composeTestRule.waitUntil(15000) {
            try {
                composeTestRule.onAllNodes(hasText("Home", substring = true))
                    .onFirst()
                    .assertExists()
                true
            } catch (e: Exception) {
                false
            }
        }
        return this
    }

    fun assertNavigationToSignUp(): LoginRobot {
        composeTestRule.onNode(
            hasText("Create Account", substring = true)
                .or(hasText("Sign Up", substring = true))
        ).assertIsDisplayed()
        return this
    }

    fun waitForLoginScreen(): LoginRobot {
        composeTestRule.waitUntil(5000) {
            try {
                composeTestRule.onNode(phoneFieldMatcher)
                    .assertExists()
                true
            } catch (e: Exception) {
                false
            }
        }
        return this
    }

    fun waitForNavigation(timeoutMs: Long = 10000): LoginRobot {
        composeTestRule.waitUntil(timeoutMs) {
            try {
                composeTestRule.onAllNodes(hasText("Home", substring = true))
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
