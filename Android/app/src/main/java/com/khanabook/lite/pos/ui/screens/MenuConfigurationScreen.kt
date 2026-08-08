@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.screens.menuconfig.ManualMenuView
import com.khanabook.lite.pos.ui.screens.menuconfig.ModeSelectionView
import com.khanabook.lite.pos.ui.screens.menuconfig.ReviewDetectedItemsOverlay
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel

object ReviewSheetLayout {
    val HORIZONTAL_PADDING = 12.dp
    val CARD_PADDING = 10.dp
    val CHECKBOX_WIDTH = 22.dp
    val CHECKBOX_GAP = 10.dp
    val PRICE_WIDTH = 76.dp
    val FOOD_ICON_WIDTH = 32.dp
}

object MenuConfigurationTags {
    const val modeSelectionRoot = "menu_config_mode_selection_root"
    const val manualEntryCard = "menu_config_manual_entry"
    const val smartAiCard = "menu_config_smart_ai"
    const val smartAiCamera = "menu_config_smart_ai_camera"
    const val smartAiGallery = "menu_config_smart_ai_gallery"
    const val smartAiPdf = "menu_config_smart_ai_pdf"
    const val manualMenuRoot = "menu_config_manual_menu_root"
    const val addCategoryButton = "menu_config_add_category"
    const val addItemButton = "menu_config_add_item"
    const val reviewOverlayRoot = "menu_config_review_overlay_root"
    const val reviewOverlayBackground = "menu_config_review_overlay_background"
    const val reviewOverlaySheet = "menu_config_review_overlay_sheet"
    const val reviewOverlayClose = "menu_config_review_overlay_close"
    const val reviewOverlayDiscard = "menu_config_review_overlay_discard"
    const val reviewOverlayConfirm = "menu_config_review_overlay_confirm"
    const val reviewOverlayConflictOverwrite = "menu_config_review_overlay_conflict_overwrite"
    const val reviewOverlayConflictMerge = "menu_config_review_overlay_conflict_merge"
    const val reviewOverlayConflictCancel = "menu_config_review_overlay_conflict_cancel"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuConfigurationScreen(
    navController: NavController,
    onBackClick: () -> Unit,
    viewModel: MenuViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val totalCategoriesCount by viewModel.totalCategoriesCount.collectAsStateWithLifecycle()
    val totalItemsCount by viewModel.totalItemsCount.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val menuItems by viewModel.menuItems.collectAsStateWithLifecycle()
    val ocrUiState by viewModel.ocrImportUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.extractTextFromPdf(context, it) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, it))
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
                val bitmapCopy = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                if (bitmapCopy != null) {
                    viewModel.processMenuImage(context, bitmapCopy)
                } else {
                    viewModel.setError("Couldn't read image. Try a clearer photo.")
                }
            } catch (t: Throwable) {
                viewModel.setError("Couldn't read image. Try a clearer photo.")
            }
        }
    }

    val onBack: () -> Unit = {
        if (ocrUiState.drafts.isNotEmpty()) {
            viewModel.clearDrafts()
        } else if (ocrUiState.configMode != null) {
            viewModel.setConfigMode(null)
        } else {
            onBackClick()
        }
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(ocrUiState.successMessage) {
        ocrUiState.successMessage?.let {
            KhanaToast.show(it, ToastKind.Success)
            viewModel.clearSuccessMessage()
        }
    }

    LaunchedEffect(ocrUiState.error) {
        ocrUiState.error?.let {
            KhanaToast.show(it, ToastKind.Error)
            viewModel.setError(null)
        }
    }

    LaunchedEffect(ocrUiState.configMode, categories, selectedCategoryId) {
        if (ocrUiState.configMode == "manual" && selectedCategoryId == null && categories.isNotEmpty()) {
            viewModel.selectCategory(categories.first().id)
        }
    }

    var showOverwritePrompt by remember { mutableStateOf(false) }

    // Standard staggered entry animation
    var screenVisible by remember { mutableStateOf(false) }
    val enterSpec = fadeIn(tween(350)) + slideInVertically(
        initialOffsetY = { it / 6 },
        animationSpec = tween(350, easing = FastOutSlowInEasing)
    )
    val exitSpec = fadeOut(tween(200))
    LaunchedEffect(Unit) {
        screenVisible = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when (ocrUiState.configMode) {
                                "manual" -> "Manual Entry"
                                else -> "Menu Configuration"
                            },
                            color = PrimaryGold,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        if (ocrUiState.configMode == "manual") {
                            Text(
                                text = "${categories.size} categories",
                                color = TextGold.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PrimaryGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBrown1)
            )
        },
        containerColor = DarkBrown1,
        contentWindowInsets = WindowInsets.systemBars
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (ocrUiState.configMode == null) {
                AnimatedVisibility(visible = screenVisible, enter = enterSpec, exit = exitSpec) {
                    ModeSelectionView(
                        selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name,
                        totalCategoriesCount = totalCategoriesCount,
                        totalItemsCount = totalItemsCount,
                        onManualClick = { viewModel.setConfigMode("manual") },
                        onSmartImportClick = {
                            val catName = categories.find { it.id == selectedCategoryId }?.name ?: ""
                            navController.navigate("ocr_scanner/$catName")
                        },
                        onGalleryClick = { galleryLauncher.launch("image/*") },
                        onPdfClick = { pdfLauncher.launch("application/pdf") }
                    )
                }
            } else {
                AnimatedVisibility(visible = screenVisible, enter = enterSpec, exit = exitSpec) {
                    ManualMenuView(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    menuItems = menuItems,
                    onCategorySelect = { viewModel.selectCategory(it) },
                    onAddCategory = { viewModel.addCategory(it, true) },
                    onUpdateCategory = { viewModel.updateCategory(it) },
                    onAddItem = { name, price, type, variants ->
                        selectedCategoryId?.let {
                            if (variants.isEmpty()) {
                                viewModel.addItem(it, name, price, type)
                            } else {
                                viewModel.addItemWithVariants(it, name, price, type, variants)
                            }
                        }
                    },
                    onUpdateItem = { viewModel.updateItem(it) },
                    onToggleAvailability = { id, available -> viewModel.toggleItem(id, available) },
                    onAddVariant = { itemId, name, price -> viewModel.addVariant(itemId, name, price) },
                    onUpdateVariant = { viewModel.updateVariant(it) },
                    onDeleteVariant = { viewModel.deleteVariant(it) }
                )
                }
            }

            KhanaBookLoadingOverlay(
                visible = ocrUiState.isProcessing,
                type = LoadingType.PROCESSING,
                message = ocrUiState.processingLabel,
                subtitle = "Please wait..."
            )

            if (ocrUiState.drafts.isNotEmpty()) {
                ReviewDetectedItemsOverlay(
                    drafts = ocrUiState.drafts,
                    onDismiss = { viewModel.clearDrafts() },
                    onConfirm = {
                        viewModel.checkForConflicts(selectedCategoryId) { hasConflict ->
                            if (hasConflict) {
                                showOverwritePrompt = true
                            } else {
                                viewModel.saveImportedMenu(selectedCategoryId, false)
                            }
                        }
                    },
                    onConfirmOverwrite = {
                        viewModel.saveImportedMenu(selectedCategoryId, true)
                        showOverwritePrompt = false
                    },
                    showOverwritePrompt = showOverwritePrompt,
                    onDismissOverwritePrompt = { showOverwritePrompt = false },
                    onToggleSelection = { viewModel.toggleDraftSelection(it) },
                    onUpdateDraft = { index, draft -> viewModel.updateDraft(index, draft) },
                    onToggleFoodType = { viewModel.toggleDraftFoodType(it) }
                )
            }
        }
    }
}
