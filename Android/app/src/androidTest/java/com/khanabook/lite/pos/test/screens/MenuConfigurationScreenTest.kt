package com.khanabook.lite.pos.test.screens

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khanabook.lite.pos.data.local.entity.CategoryEntity
import com.khanabook.lite.pos.data.local.relation.MenuWithVariants
import com.khanabook.lite.pos.ui.screens.ManualMenuView
import com.khanabook.lite.pos.ui.screens.MenuConfigurationTags
import com.khanabook.lite.pos.ui.screens.ModeSelectionView
import com.khanabook.lite.pos.ui.screens.ReviewDetectedItemsOverlay
import com.khanabook.lite.pos.ui.theme.KhanaBookLiteTheme
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MenuConfigurationScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun modeSelection_exposesStableTags_andExpandsSmartAiOptions() {
        composeTestRule.setContent {
            KhanaBookLiteTheme {
                ModeSelectionView(
                    selectedCategoryName = "Biryani",
                    onManualClick = {},
                    onSmartImportClick = {},
                    onGalleryClick = {},
                    onPdfClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(MenuConfigurationTags.modeSelectionRoot).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MenuConfigurationTags.manualEntryCard).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MenuConfigurationTags.smartAiCard).assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag(MenuConfigurationTags.smartAiCamera).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MenuConfigurationTags.smartAiGallery).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MenuConfigurationTags.smartAiPdf).assertIsDisplayed()
    }

    @Test
    fun manualMenuView_exposesPrimaryActionTags() {
        composeTestRule.setContent {
            KhanaBookLiteTheme {
                ManualMenuView(
                    categories = listOf(CategoryEntity(id = 1L, name = "Biryani", isVeg = false)),
                    selectedCategoryId = 1L,
                    menuItems = emptyList<MenuWithVariants>(),
                    onCategorySelect = {},
                    onAddCategory = {},
                    onUpdateCategory = {},
                    onDeleteCategory = {},
                    onAddItem = { _, _, _, _ -> },
                    onUpdateItem = {},
                    onDeleteItem = {},
                    onToggleAvailability = { _, _ -> },
                    onAddVariant = { _, _, _ -> },
                    onUpdateVariant = {},
                    onDeleteVariant = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(MenuConfigurationTags.manualMenuRoot).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MenuConfigurationTags.addCategoryButton).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MenuConfigurationTags.addItemButton).assertIsDisplayed()
    }

    @Test
    fun reviewOverlay_sheetControlsWork_andBackgroundDismisses() {
        val overlayVisible = mutableStateOf(true)
        val confirmClicks = mutableIntStateOf(0)
        val drafts = listOf(
            MenuViewModel.DraftMenuItem(
                name = "Bismi Spl Biryani",
                price = 270.0,
                categoryName = "Biryani"
            )
        )

        composeTestRule.setContent {
            KhanaBookLiteTheme {
                if (overlayVisible.value) {
                    ReviewDetectedItemsOverlay(
                        drafts = drafts,
                        onDismiss = { overlayVisible.value = false },
                        onConfirm = { confirmClicks.intValue += 1 },
                        onConfirmOverwrite = {},
                        showOverwritePrompt = false,
                        onDismissOverwritePrompt = {},
                        onToggleSelection = {},
                        onUpdateDraft = { _, _ -> },
                        onToggleFoodType = { _ -> }
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag(MenuConfigurationTags.reviewOverlayRoot).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MenuConfigurationTags.reviewOverlaySheet).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MenuConfigurationTags.reviewOverlayConfirm).assertIsDisplayed().performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, confirmClicks.intValue)
        }

        composeTestRule.onNodeWithTag(MenuConfigurationTags.reviewOverlayBackground).performClick()
        composeTestRule.onAllNodesWithTag(MenuConfigurationTags.reviewOverlayRoot).assertCountEquals(0)
    }
}
