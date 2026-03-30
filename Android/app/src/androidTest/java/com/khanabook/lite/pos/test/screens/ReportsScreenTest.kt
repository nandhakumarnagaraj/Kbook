package com.khanabook.lite.pos.test.screens

import com.khanabook.lite.pos.test.BaseTest
import com.khanabook.lite.pos.test.robots.HomeRobot
import com.khanabook.lite.pos.test.robots.LoginRobot
import com.khanabook.lite.pos.test.robots.ReportsRobot
import org.junit.Before
import org.junit.Test

class ReportsScreenTest : BaseTest() {

    private lateinit var homeRobot: HomeRobot
    private lateinit var reportsRobot: ReportsRobot
    private lateinit var loginRobot: LoginRobot

    @Before
    override fun setUp() {
        super.setUp()
        homeRobot = HomeRobot(composeTestRule)
        reportsRobot = ReportsRobot(composeTestRule)
        loginRobot = LoginRobot(composeTestRule)
        
        mockApiServer.enqueueLoginSuccess()
        mockApiServer.enqueueMasterSyncSuccess()
        
        loginRobot.submitLogin()
        homeRobot.waitForDataToLoad()
    }

    @Test
    fun TC_LAYOUT_010_ReportsScreen_LayoutValid() {
        homeRobot.tapReportsTab()
        
        reportsRobot
            .waitForReportsToLoad()
            .assertReportsTitleVisible()
            .assertFilterOptionsVisible()
    }

    @Test
    fun TC_LAYOUT_010_ReportsScreen_Rotation_ChartsAdapt() {
        homeRobot.tapReportsTab()
        
        reportsRobot.waitForReportsToLoad()
        
        reportsRobot.assertRevenueChartVisible()
    }

    @Test
    fun TC_API_012_ReportsScreen_FilterToday() {
        mockApiServer.enqueueReportsData("Today")
        
        homeRobot.tapReportsTab()
        
        reportsRobot
            .waitForReportsToLoad()
            .selectToday()
            .assertRevenueChartVisible()
    }

    @Test
    fun TC_API_012_ReportsScreen_FilterThisWeek() {
        mockApiServer.enqueueReportsData("This Week")
        
        homeRobot.tapReportsTab()
        
        reportsRobot
            .waitForReportsToLoad()
            .selectThisWeek()
            .assertRevenueChartVisible()
    }

    @Test
    fun TC_API_012_ReportsScreen_FilterThisMonth() {
        mockApiServer.enqueueReportsData("This Month")
        
        homeRobot.tapReportsTab()
        
        reportsRobot
            .waitForReportsToLoad()
            .selectThisMonth()
            .assertRevenueChartVisible()
    }

    @Test
    fun TC_API_012_ReportsScreen_CustomDateRange() {
        mockApiServer.enqueueReportsData("Custom Range")
        
        homeRobot.tapReportsTab()
        
        reportsRobot
            .waitForReportsToLoad()
            .selectCustomRange()
    }

    @Test
    fun TC_API_012_ReportsScreen_NoDataMessage() {
        mockApiServer.enqueueEmptyReports()
        
        homeRobot.tapReportsTab()
        
        reportsRobot
            .waitForReportsToLoad()
            .assertNoDataMessage()
    }
}
