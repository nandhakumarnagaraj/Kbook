package com.khanabook.lite.pos.test.robots

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule

class HomeRobot(private val composeTestRule: AndroidComposeTestRule<*, *>) {

    private val newBillButtonMatcher = hasText("New Bill", substring = true)
        .or(hasContentDescription("New Bill"))

    private val homeTabMatcher = hasText("Home", substring = true)
        .or(hasContentDescription("Home"))

    private val ordersTabMatcher = hasText("Orders", substring = true)
        .or(hasContentDescription("Orders"))

    private val reportsTabMatcher = hasText("Reports", substring = true)
        .or(hasContentDescription("Reports"))

    private val searchTabMatcher = hasText("Search", substring = true)
        .or(hasContentDescription("Search"))

    private val settingsTabMatcher = hasText("Settings", substring = true)
        .or(hasContentDescription("Settings"))

    private val dashboardMatcher = hasText("Dashboard", substring = true)
        .or(hasText("Home"))

    fun tapNewBill(): NewBillRobot {
        composeTestRule.onNode(newBillButtonMatcher)
            .assertIsEnabled()
            .performClick()
        return NewBillRobot(composeTestRule)
    }

    fun tapHomeTab(): HomeRobot {
        composeTestRule.onNode(homeTabMatcher)
            .performClick()
        return this
    }

    fun tapOrdersTab(): OrdersRobot {
        composeTestRule.onNode(ordersTabMatcher)
            .performClick()
        return OrdersRobot(composeTestRule)
    }

    fun tapReportsTab(): ReportsRobot {
        composeTestRule.onNode(reportsTabMatcher)
            .performClick()
        return ReportsRobot(composeTestRule)
    }

    fun tapSearchTab(): SearchRobot {
        composeTestRule.onNode(searchTabMatcher)
            .performClick()
        return SearchRobot(composeTestRule)
    }

    fun tapSettingsTab(): SettingsRobot {
        composeTestRule.onNode(settingsTabMatcher)
            .performClick()
        return SettingsRobot(composeTestRule)
    }

    fun assertDashboardVisible(): HomeRobot {
        composeTestRule.onNode(dashboardMatcher)
            .assertIsDisplayed()
        return this
    }

    fun assertQuickActionsVisible(): HomeRobot {
        composeTestRule.onNode(newBillButtonMatcher)
            .assertIsDisplayed()
        return this
    }

    fun assertBottomNavigationVisible(): HomeRobot {
        listOf(
            homeTabMatcher,
            ordersTabMatcher,
            reportsTabMatcher,
            searchTabMatcher,
            settingsTabMatcher
        ).forEach { matcher ->
            composeTestRule.onNode(matcher)
                .assertIsDisplayed()
        }
        return this
    }

    fun assertMetricsCardsVisible(): HomeRobot {
        composeTestRule.onNode(
            hasText("Today's Orders", substring = true)
                .or(hasText("Revenue", substring = true))
                .or(hasText("Pending", substring = true))
                .or(hasText("Orders", substring = true))
        ).assertIsDisplayed()
        return this
    }

    fun assertNoCrash(): HomeRobot {
        composeTestRule.waitForIdle()
        return this
    }

    fun waitForDataToLoad(): HomeRobot {
        composeTestRule.waitUntil(15000) {
            try {
                composeTestRule.onNode(dashboardMatcher)
                    .assertExists()
                true
            } catch (e: Exception) {
                try {
                    composeTestRule.onNode(newBillButtonMatcher)
                        .assertExists()
                    true
                } catch (e2: Exception) {
                    false
                }
            }
        }
        return this
    }

    fun pullToRefresh(): HomeRobot {
        composeTestRule.onNode(dashboardMatcher)
            .performTouchInput {
                down(Offset(500f, 200f))
                moveTo(Offset(500f, 1000f))
                up()
            }
        return this
    }
}

class SearchRobot(private val composeTestRule: AndroidComposeTestRule<*, *>) {

    private val searchFieldMatcher = hasTestTag("search").or(hasText("Search", substring = true))

    private val searchResultsMatcher = hasText("Results", substring = true)

    fun enterSearchQuery(query: String): SearchRobot {
        composeTestRule.onNode(searchFieldMatcher)
            .performTextInput(query)
        return this
    }

    fun clearSearch(): SearchRobot {
        composeTestRule.onNode(searchFieldMatcher)
            .performTextClearance()
        return this
    }

    fun assertSearchFieldVisible(): SearchRobot {
        composeTestRule.onNode(searchFieldMatcher)
            .assertIsDisplayed()
        return this
    }

    fun waitForSearchResults(): SearchRobot {
        composeTestRule.waitUntil(5000) {
            try {
                composeTestRule.onNode(searchResultsMatcher)
                    .assertExists()
                true
            } catch (e: Exception) {
                false
            }
        }
        return this
    }

    fun pressBack(): HomeRobot {
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        return HomeRobot(composeTestRule)
    }
}
