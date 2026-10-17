package com.khanabook.lite.pos.test.robots

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule

class ReportsRobot(private val composeTestRule: AndroidComposeTestRule<*, *>) {

    private val reportsTitleMatcher = hasText("Reports", substring = true).or(hasText("Analytics"))
    private val dateRangePickerMatcher = hasText("Date Range", substring = true)
    private val todayFilterMatcher = hasText("Today", substring = true)
    private val weekFilterMatcher = hasText("This Week", substring = true)
    private val monthFilterMatcher = hasText("This Month", substring = true)
    private val customRangeMatcher = hasText("Custom Range", substring = true)
    private val revenueChartMatcher = hasText("Revenue", substring = true)
    private val ordersCountMatcher = hasText("Orders", substring = true)
    private val exportButtonMatcher = hasContentDescription("Export").or(hasText("Export"))

    fun selectToday(): ReportsRobot {
        composeTestRule.onNode(todayFilterMatcher).performClick()
        return this
    }

    fun selectThisWeek(): ReportsRobot {
        composeTestRule.onNode(weekFilterMatcher).performClick()
        return this
    }

    fun selectThisMonth(): ReportsRobot {
        composeTestRule.onNode(monthFilterMatcher).performClick()
        return this
    }

    fun selectCustomRange(): ReportsRobot {
        composeTestRule.onNode(customRangeMatcher).performClick()
        return this
    }

    fun tapDateRangePicker(): ReportsRobot {
        composeTestRule.onNode(dateRangePickerMatcher).performClick()
        return this
    }

    fun tapExport(): ReportsRobot {
        composeTestRule.onNode(exportButtonMatcher).performClick()
        return this
    }

    fun scrollToCharts(): ReportsRobot {
        composeTestRule.onNode(revenueChartMatcher).performScrollTo()
        return this
    }

    fun assertReportsTitleVisible(): ReportsRobot {
        composeTestRule.onNode(reportsTitleMatcher).assertIsDisplayed()
        return this
    }

    fun assertRevenueChartVisible(): ReportsRobot {
        scrollToCharts()
        composeTestRule.onNode(revenueChartMatcher).assertIsDisplayed()
        return this
    }

    fun assertFilterOptionsVisible(): ReportsRobot {
        listOf(todayFilterMatcher, weekFilterMatcher, monthFilterMatcher).forEach { matcher ->
            composeTestRule.onNode(matcher).assertIsDisplayed()
        }
        return this
    }

    fun assertNoDataMessage(): ReportsRobot {
        composeTestRule.onNode(hasText("No data", substring = true).or(hasText("No reports"))).assertIsDisplayed()
        return this
    }

    fun waitForReportsToLoad(): ReportsRobot {
        composeTestRule.waitUntil(10000) {
            try {
                composeTestRule.onNode(revenueChartMatcher, useUnmergedTree = true)
                    .assertExists()
                true
            } catch (e: Exception) {
                false
            }
        }
        return this
    }
}
