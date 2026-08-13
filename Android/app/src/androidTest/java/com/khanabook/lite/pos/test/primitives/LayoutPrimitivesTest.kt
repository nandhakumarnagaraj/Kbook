package com.khanabook.lite.pos.test.primitives

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.ui.designsystem.ListLayout
import com.khanabook.lite.pos.ui.designsystem.ScrollableCenteredLayout
import com.khanabook.lite.pos.ui.designsystem.StickyBottomScaffold
import com.khanabook.lite.pos.ui.theme.KhanaBookLiteTheme
import org.junit.Rule
import org.junit.Test

/**
 * Behavioral tests for the layout primitives introduced in the responsive
 * architecture migration. Run with: ./gradlew connectedAndroidTest
 *
 * These verify the core guarantees the audit demanded:
 * - StickyBottomScaffold: CTA never leaves the screen, regardless of scroll.
 * - ListLayout: filter bar pins, empty/content states switch correctly.
 * - ScrollableCenteredLayout: content always scrolls (never clips) and the
 *   optional bottom bar stays visible.
 */
class LayoutPrimitivesTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── StickyBottomScaffold ────────────────────────────────────────────────

    @Test
    fun stickyBottom_ctalwaysVisibleWithOverflowingContent() {
        composeRule.setContent {
            KhanaBookLiteTheme {
                StickyBottomScaffold(
                    bottomBar = { Text("Confirm Payment") }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        repeat(40) { Text("Content row $it") }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Confirm Payment").assertIsDisplayed()
        composeRule.onNodeWithText("Content row 39").performScrollTo()
        composeRule.onNodeWithText("Confirm Payment").assertIsDisplayed()
    }

    @Test
    fun stickyBottom_headerPinnedAboveScrollableContent() {
        composeRule.setContent {
            KhanaBookLiteTheme {
                StickyBottomScaffold(
                    header = { Text("Bill Header") },
                    bottomBar = { Text("Confirm Payment") }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        repeat(30) { Text("Content row $it") }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Bill Header").assertIsDisplayed()
        composeRule.onNodeWithText("Content row 29").performScrollTo()
        composeRule.onNodeWithText("Bill Header").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm Payment").assertIsDisplayed()
    }

    // ── ListLayout ──────────────────────────────────────────────────────────

    @Test
    fun listLayout_filterBarPinnedAndContentRendered() {
        composeRule.setContent {
            KhanaBookLiteTheme {
                ListLayout(
                    filterBar = { Text("Filter Bar") },
                    isEmpty = false
                ) {
                    LazyColumn {
                        items((0..24).toList()) { index ->
                            Text("List item $index")
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Filter Bar").assertIsDisplayed()
        composeRule.onNodeWithText("List item 0").assertIsDisplayed()
        composeRule.onNodeWithText("List item 24").performScrollTo()
        composeRule.onNodeWithText("Filter Bar").assertIsDisplayed()
    }

    @Test
    fun listLayout_emptyStateShownWhenEmpty() {
        composeRule.setContent {
            KhanaBookLiteTheme {
                ListLayout(
                    filterBar = { Text("Filter Bar") },
                    isEmpty = true,
                    emptyState = { Text("No items match the current filters.") }
                ) {
                    LazyColumn {
                        items((0..4).toList()) { index ->
                            Text("List item $index")
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("No items match the current filters.").assertIsDisplayed()
        composeRule.onAllNodesWithText("List item 0").fetchSemanticsNodes().isEmpty()
    }

    @Test
    fun listLayout_switchesBetweenContentAndEmptyState() {
        var isEmpty by mutableStateOf(false)
        composeRule.setContent {
            KhanaBookLiteTheme {
                ListLayout(
                    filterBar = { Text("Filter Bar") },
                    isEmpty = isEmpty,
                    emptyState = { Text("Empty State") },
                    content = {
                        LazyColumn {
                            items((0..9).toList()) { index ->
                                Text("List item $index")
                            }
                        }
                    }
                )
            }
        }

        composeRule.onNodeWithText("List item 0").assertIsDisplayed()

        isEmpty = true
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithText("Empty State").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Empty State").assertIsDisplayed()
        composeRule.onAllNodesWithText("List item 0").fetchSemanticsNodes().isEmpty()
    }

    // ── ScrollableCenteredLayout ────────────────────────────────────────────

    @Test
    fun centeredLayout_contentScrollsWhenItOverflowsViewport() {
        composeRule.setContent {
            KhanaBookLiteTheme {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .height(400.dp)
                ) {
                    ScrollableCenteredLayout {
                        repeat(30) { Text("Centered row $it") }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Centered row 0").assertIsDisplayed()
        composeRule.onNodeWithText("Centered row 29").performScrollTo()
        composeRule.onNodeWithText("Centered row 29").assertIsDisplayed()
    }

    @Test
    fun centeredLayout_bottomBarStaysVisibleWhenContentScrolls() {
        composeRule.setContent {
            KhanaBookLiteTheme {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .height(400.dp)
                ) {
                    ScrollableCenteredLayout(
                        bottomBar = { Text("Back to Home") }
                    ) {
                        repeat(30) { Text("Centered row $it") }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Back to Home").assertIsDisplayed()
        composeRule.onNodeWithText("Centered row 29").performScrollTo()
        composeRule.onNodeWithText("Back to Home").assertIsDisplayed()
    }

    // ── Cross-primitive: tags make components findable for future tests ─────

    @Test
    fun stickyBottom_scaffoldTagHierarchyIntact() {
        composeRule.setContent {
            KhanaBookLiteTheme {
                StickyBottomScaffold(
                    modifier = Modifier.testTag("sticky_scaffold"),
                    bottomBar = { Box(Modifier.testTag("sticky_bottom_bar")) { Text("CTA") } }
                ) {
                    Box(Modifier.fillMaxSize().testTag("sticky_content")) {}
                }
            }
        }

        composeRule.onNodeWithTag("sticky_scaffold").assertIsDisplayed()
        composeRule.onNodeWithTag("sticky_content").assertIsDisplayed()
        composeRule.onNodeWithTag("sticky_bottom_bar").assertIsDisplayed()
    }
}