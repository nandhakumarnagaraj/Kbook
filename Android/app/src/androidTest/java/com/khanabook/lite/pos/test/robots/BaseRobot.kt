package com.khanabook.lite.pos.test.robots

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule

abstract class BaseRobot(protected val composeTestRule: AndroidComposeTestRule<*, *>) {

    fun waitForScreen(screenMatcher: SemanticsMatcher, timeoutMillis: Long = 5000L) {
        composeTestRule.waitUntil(timeoutMillis) {
            try {
                composeTestRule.onNode(screenMatcher, useUnmergedTree = true)
                    .assertExists()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun waitForLoading(timeoutMillis: Long = 10000L) {
        composeTestRule.waitUntil(timeoutMillis) {
            try {
                composeTestRule.onNode(hasText("Loading", substring = true))
                    .assertDoesNotExist()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun pressBack() {
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    }

    fun click(matcher: SemanticsMatcher) {
        composeTestRule.onNode(matcher, useUnmergedTree = true)
            .performClick()
    }

    fun assertExists(matcher: SemanticsMatcher, timeoutMillis: Long = 5000L) {
        composeTestRule.waitUntil(timeoutMillis) {
            try {
                composeTestRule.onNode(matcher, useUnmergedTree = true)
                    .assertExists()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun assertDoesNotExist(matcher: SemanticsMatcher) {
        composeTestRule.onNode(matcher, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    fun scrollTo(matcher: SemanticsMatcher) {
        composeTestRule.onNode(matcher, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    fun scrollAndClick(matcher: SemanticsMatcher) {
        composeTestRule.onNode(matcher, useUnmergedTree = true)
            .performScrollTo()
            .performClick()
    }
}
