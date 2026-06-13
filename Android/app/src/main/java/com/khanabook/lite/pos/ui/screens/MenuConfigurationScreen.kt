@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.khanabook.lite.pos.data.local.entity.CategoryEntity
import com.khanabook.lite.pos.data.local.entity.MenuItemEntity
import com.khanabook.lite.pos.data.local.relation.MenuWithVariants
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import java.util.Locale

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
    val categories by viewModel.categories.collectAsState()
    val totalCategoriesCount by viewModel.totalCategoriesCount.collectAsState()
    val totalItemsCount by viewModel.totalItemsCount.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()
    val ocrUiState by viewModel.ocrImportUiState.collectAsState()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }

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

    // Edge-to-edge is handled by enableEdgeToEdge() + manual inset modifiers.
    // Do NOT toggle setDecorFitsSystemWindows — it conflicts with edge-to-edge
    // and causes double padding on navigation bars.

    LaunchedEffect(ocrUiState.successMessage) {
        ocrUiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    LaunchedEffect(ocrUiState.error) {
        ocrUiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.setError(null)
        }
    }

    LaunchedEffect(ocrUiState.configMode, categories, selectedCategoryId) {
        if (ocrUiState.configMode == "manual" && selectedCategoryId == null && categories.isNotEmpty()) {
            viewModel.selectCategory(categories.first().id)
        }
    }

    var showOverwritePrompt by remember { mutableStateOf(false) }
    // Tracks which Manual Entry action ("add"/"view"/"edit") the user picked, so the
    // manual view can pre-open the add-item flow for "add" instead of all three behaving alike.
    var pendingManualAction by remember { mutableStateOf<String?>(null) }

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
            if (ocrUiState.configMode != "manual") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.kbHeaderGradient)
                        .statusBarsPadding()
                        .padding(top = 8.dp, bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KhanaBookBackButton(onClick = { onBack() })
                        Text(
                            text = "Menu Configuration",
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                }
            }
        },
        snackbarHost = { KhanaBookSnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.kbBgPrimary,
        contentWindowInsets = WindowInsets.systemBars
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (ocrUiState.configMode == null) {
                AnimatedVisibility(visible = screenVisible, enter = enterSpec, exit = exitSpec) {
                    ModeSelectionView(
                        selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name,
                        totalCategoriesCount = totalCategoriesCount,
                        totalItemsCount = totalItemsCount,
                        onManualClick = { action ->
                            pendingManualAction = action
                            viewModel.setConfigMode("manual")
                        },
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
                    val categoryItemCounts by viewModel.categoryItemCounts.collectAsState()
                    ManualMenuView(
                        categories = categories,
                        categoryItemCounts = categoryItemCounts,
                        totalItemsCount = totalItemsCount,
                        selectedCategoryId = selectedCategoryId,
                        menuItems = menuItems,
                        initialAction = pendingManualAction,
                        onInitialActionConsumed = { pendingManualAction = null },
                        onCategorySelect = { viewModel.selectCategory(it) },
                        onAddCategory = { viewModel.addCategory(it, true) },
                        onUpdateCategory = { viewModel.updateCategory(it) },
                        onDeleteCategory = { viewModel.deleteCategory(it) },
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
                        onDeleteItem = { viewModel.deleteItem(it) },
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

@Composable
fun ReviewDetectedItemsScreen(
    drafts: List<MenuViewModel.DraftMenuItem>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onConfirmOverwrite: () -> Unit,
    showOverwritePrompt: Boolean,
    onDismissOverwritePrompt: () -> Unit,
    onToggleSelection: (Int) -> Unit,
    onUpdateDraft: (Int, MenuViewModel.DraftMenuItem) -> Unit,
    onToggleFoodType: (Int) -> Unit
) {
    val selectedCount = drafts.count { it.isSelected }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    if (drafts.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.kbBgPrimary)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(MaterialTheme.kbSecondary.copy(alpha = 0.4f), CircleShape)
                    )
                }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Review Detected Items",
                                    color = MaterialTheme.kbSecondary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${drafts.size} items found · $selectedCount selected",
                                    color = MaterialTheme.kbPrimary.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.kbPrimary)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ReviewSheetLayout.HORIZONTAL_PADDING + ReviewSheetLayout.CARD_PADDING, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.width(ReviewSheetLayout.CHECKBOX_WIDTH + ReviewSheetLayout.CHECKBOX_GAP))
                            Text("Item Name", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Price", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.width(ReviewSheetLayout.PRICE_WIDTH))
                            Spacer(modifier = Modifier.width(ReviewSheetLayout.FOOD_ICON_WIDTH))
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = ReviewSheetLayout.HORIZONTAL_PADDING, vertical = 12.dp)
                    ) {
                        val groupedDrafts = drafts.withIndex().groupBy { it.value.categoryName ?: "Uncategorized" }

                        groupedDrafts.forEach { (categoryName, indexedItems) ->
                            val allInCategorySelected = indexedItems.all { it.value.isSelected }

                            item(key = "header_$categoryName") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = ReviewSheetLayout.CARD_PADDING, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(ReviewSheetLayout.CHECKBOX_WIDTH)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(                                if (allInCategorySelected) MaterialTheme.kbPrimary else Color.Transparent
                            )
                            .border(
                                1.5.dp,
                                if (allInCategorySelected) MaterialTheme.kbPrimary else MaterialTheme.kbPrimary.copy(alpha = 0.5f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable {
                                                val targetSelection = !allInCategorySelected
                                                indexedItems.forEach { indexed ->
                                                    if (indexed.value.isSelected != targetSelection) {
                                                        onToggleSelection(indexed.index)
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (allInCategorySelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.kbTextPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(ReviewSheetLayout.CHECKBOX_GAP))

                                    Text(
                                        categoryName.uppercase(),
                                        color = MaterialTheme.kbSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            items(indexedItems.size) { i ->
                                val index = indexedItems[i].index
                                val draft = indexedItems[i].value

                                DraftItemRow(
                                    index = index,
                                    draft = draft,
                                    onToggleSelection = { onToggleSelection(index) },
                                    onUpdateDraft = { onUpdateDraft(index, it) },
                                    onToggleFoodType = { onToggleFoodType(index) }
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.kbBgSecondary,
                        border = BorderStroke(0.5.dp, MaterialTheme.kbOutlineSubtle.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showDiscardConfirm = true },
                                border = BorderStroke(1.5.dp, KbError.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = KbError),
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Discard", maxLines = 1, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onConfirm,
                                enabled = selectedCount > 0,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = KbBrandSaffron,
                                    contentColor = MaterialTheme.kbTextOnBrand
                                ),
                                modifier = Modifier.weight(2f).height(56.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, null, modifier = Modifier.size(KhanaBookTheme.iconSize.medium))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Add $selectedCount Items",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
@Composable
internal fun ReviewDetectedItemsOverlay(
    drafts: List<MenuViewModel.DraftMenuItem>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onConfirmOverwrite: () -> Unit,
    showOverwritePrompt: Boolean,
    onDismissOverwritePrompt: () -> Unit,
    onToggleSelection: (Int) -> Unit,
    onUpdateDraft: (Int, MenuViewModel.DraftMenuItem) -> Unit,
    onToggleFoodType: (Int) -> Unit
) {
    if (drafts.isEmpty()) return

    val selectedCount = drafts.count { it.isSelected }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val dismissInteractionSource = remember { MutableInteractionSource() }

    Box(modifier = Modifier.fillMaxSize().testTag(MenuConfigurationTags.reviewOverlayRoot)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .testTag(MenuConfigurationTags.reviewOverlayBackground)
                .background(MaterialTheme.kbBgPrimary.copy(alpha = 0.82f))
                .clickable(
                    interactionSource = dismissInteractionSource,
                    indication = null,
                    onClick = {
                        onDismissOverwritePrompt()
                        showDiscardConfirm = false
                        onDismiss()
                    }
                )
        )

        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.96f)
                .padding(bottom = 10.dp, top = 10.dp)
                .imePadding()
                .testTag(MenuConfigurationTags.reviewOverlaySheet), color = MaterialTheme.kbTextPrimary,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(MaterialTheme.kbSecondary.copy(alpha = 0.4f), CircleShape)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Review Detected Items",
                            color = MaterialTheme.kbSecondary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${drafts.size} items found · $selectedCount selected",
                            color = MaterialTheme.kbPrimary.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = {
                        onDismissOverwritePrompt()
                        showDiscardConfirm = false
                        onDismiss()
                    }, modifier = Modifier.testTag(MenuConfigurationTags.reviewOverlayClose)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.kbPrimary)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = ReviewSheetLayout.HORIZONTAL_PADDING + ReviewSheetLayout.CARD_PADDING,
                            vertical = 4.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(ReviewSheetLayout.CHECKBOX_WIDTH + ReviewSheetLayout.CHECKBOX_GAP))
                    Text("Type", color = MaterialTheme.kbPrimary.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.width(ReviewSheetLayout.FOOD_ICON_WIDTH))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Item Name", color = MaterialTheme.kbPrimary.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Price",
                        color = MaterialTheme.kbPrimary.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(ReviewSheetLayout.PRICE_WIDTH)
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = ReviewSheetLayout.HORIZONTAL_PADDING, vertical = 8.dp)
                ) {
                    val groupedDrafts = drafts.withIndex().groupBy { it.value.categoryName ?: "Uncategorized" }

                    groupedDrafts.forEach { (categoryName, indexedItems) ->
                        val allInCategorySelected = indexedItems.all { it.value.isSelected }

                        item(key = "header_$categoryName") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = ReviewSheetLayout.CARD_PADDING, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(ReviewSheetLayout.CHECKBOX_WIDTH)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(                                        if (allInCategorySelected) MaterialTheme.kbPrimary else Color.Transparent
                                    )
                                    .border(
                                        1.5.dp,
                                        if (allInCategorySelected) MaterialTheme.kbPrimary else MaterialTheme.kbPrimary.copy(alpha = 0.5f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            val targetSelection = !allInCategorySelected
                                            indexedItems.forEach { indexed ->
                                                if (indexed.value.isSelected != targetSelection) {
                                                    onToggleSelection(indexed.index)
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (allInCategorySelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.kbTextPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(ReviewSheetLayout.CHECKBOX_GAP))

                                Text(
                                    categoryName.uppercase(),
                                    color = MaterialTheme.kbSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        items(indexedItems.size) { i ->
                            val index = indexedItems[i].index
                            val draft = indexedItems[i].value

                            DraftItemRow(
                                index = index,
                                draft = draft,
                                onToggleSelection = { onToggleSelection(index) },
                                onUpdateDraft = { onUpdateDraft(index, it) },
                                onToggleFoodType = { onToggleFoodType(index) }
                            )
                        }
                    }
                }

                if (showDiscardConfirm) {
                    InlineDecisionBar(
                        title = "Discard Items?",
                        message = "All ${drafts.size} detected items will be discarded.",
                        primaryLabel = "Discard",
                        primaryColor = KbError,
                        secondaryLabel = "Keep Editing",
                        secondaryColor = MaterialTheme.kbTertiary,
                        onPrimaryClick = {
                            showDiscardConfirm = false
                            onDismiss()
                        },
                        onSecondaryClick = { showDiscardConfirm = false }
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.kbBgSecondary,
                    border = BorderStroke(0.5.dp, MaterialTheme.kbOutlineSubtle.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDismissOverwritePrompt()
                                showDiscardConfirm = true
                            },
                            border = BorderStroke(1.5.dp, KbError.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KbError),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag(MenuConfigurationTags.reviewOverlayDiscard),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (showDiscardConfirm) "Confirming..." else "Discard",
                                maxLines = 1,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = {
                                showDiscardConfirm = false
                                onConfirm()
                            },
                            enabled = selectedCount > 0,                                colors = ButtonDefaults.buttonColors(
                                    containerColor = KbBrandSaffron,
                                    contentColor = MaterialTheme.kbTextOnBrand
                                ),
                                modifier = Modifier
                                    .weight(2f)
                                    .height(56.dp)
                                .testTag(MenuConfigurationTags.reviewOverlayConfirm),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, null, modifier = Modifier.size(KhanaBookTheme.iconSize.medium))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Add $selectedCount Items",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }

        if (showOverwritePrompt) {
            ConflictResolutionDialog(
                onOverwriteAll = onConfirmOverwrite,
                onMergeAndSkip = {
                    onDismissOverwritePrompt()
                    onConfirm()
                },
                onCancel = onDismissOverwritePrompt
            )
        }
    }
}

@Composable
private fun InlineDecisionBar(
    title: String,
    message: String,
    primaryLabel: String,
    primaryColor: Color,
    secondaryLabel: String,
    secondaryColor: Color,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    tertiaryLabel: String? = null,
    onTertiaryClick: (() -> Unit)? = null,
    primaryTag: String? = null,
    secondaryTag: String? = null,
    tertiaryTag: String? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.kbBgSecondary.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, color = MaterialTheme.kbSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(message, color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPrimaryClick,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = MaterialTheme.kbTextOnBrand),
                    modifier = Modifier
                        .weight(1f)
                        .then(if (primaryTag != null) Modifier.testTag(primaryTag) else Modifier)
                ) {
                    Text(primaryLabel, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onSecondaryClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = secondaryColor),
                    border = BorderStroke(1.dp, secondaryColor.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .weight(1f)
                        .then(if (secondaryTag != null) Modifier.testTag(secondaryTag) else Modifier)
                ) {
                    Text(secondaryLabel, fontWeight = FontWeight.Bold)
                }
                if (tertiaryLabel != null && onTertiaryClick != null) {
                    TextButton(
                        onClick = onTertiaryClick,
                        modifier = if (tertiaryTag != null) Modifier.testTag(tertiaryTag) else Modifier
                    ) {
                        Text(tertiaryLabel, color = MaterialTheme.kbPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictResolutionDialog(
    onOverwriteAll: () -> Unit,
    onMergeAndSkip: () -> Unit,
    onCancel: () -> Unit
) {
    KhanaBookDialog(
        onDismissRequest = onCancel,
        title = "Conflicts Found",
        message = "Some selected items already exist in this category. Choose how to continue."
    ) {
        Button(
            onClick = onOverwriteAll,
            colors = ButtonDefaults.buttonColors(
                containerColor = KbError,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag(MenuConfigurationTags.reviewOverlayConflictOverwrite),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Overwrite All", fontWeight = FontWeight.Bold, maxLines = 1)
        }
        OutlinedButton(
            onClick = onMergeAndSkip,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = KbSuccess),
            border = BorderStroke(1.dp, KbSuccess.copy(alpha = 0.7f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag(MenuConfigurationTags.reviewOverlayConflictMerge),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Merge & Skip", fontWeight = FontWeight.Bold, maxLines = 1)
        }
        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.kbPrimary),
            border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag(MenuConfigurationTags.reviewOverlayConflictCancel),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Cancel", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun DraftItemRow(
    index: Int,
    draft: MenuViewModel.DraftMenuItem,
    onToggleSelection: () -> Unit,
    onUpdateDraft: (MenuViewModel.DraftMenuItem) -> Unit,
    onToggleFoodType: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (draft.isSelected) MaterialTheme.kbBgCard else Color.Transparent,
        animationSpec = tween(200),
        label = "item_bg"
    )

    val rowRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(rowRequester)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(
                width = 0.5.dp,
                color = if (draft.isSelected) MaterialTheme.kbOutlineSubtle else MaterialTheme.kbOutlineSubtle.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onToggleSelection() }
            .padding(horizontal = ReviewSheetLayout.CARD_PADDING, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(ReviewSheetLayout.CHECKBOX_WIDTH)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (draft.isSelected) MaterialTheme.kbPrimary else Color.Transparent
                    )
                    .border(
                        1.2.dp,
                        if (draft.isSelected) MaterialTheme.kbPrimary else MaterialTheme.kbPrimary.copy(alpha = 0.4f),
                        RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (draft.isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.kbTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(ReviewSheetLayout.CHECKBOX_GAP))

            // Food Type Indicator - aligned with Type header
            Box(
                modifier = Modifier
                    .width(ReviewSheetLayout.FOOD_ICON_WIDTH)
                    .clickable { onToggleFoodType() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .border(1.dp, if (draft.foodType == "veg") KbSuccess else KbError, RoundedCornerShape(2.dp))
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (draft.foodType == "veg") KbSuccess else KbError, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = draft.name,
                    onValueChange = { onUpdateDraft(draft.copy(name = it)) },
                    textStyle = TextStyle(
                        color = if (draft.isSelected) MaterialTheme.kbTextPrimary else MaterialTheme.kbTextPrimary.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (!draft.isSelected) TextDecoration.LineThrough else null
                    ),
                    cursorBrush = SolidColor(MaterialTheme.kbPrimary),
                    modifier = Modifier.onFocusChanged {
                        if (it.isFocused) scope.launch { rowRequester.bringIntoView() }
                    }
                )

                BasicTextField(
                    value = draft.categoryName ?: "",
                    onValueChange = { onUpdateDraft(draft.copy(categoryName = it.ifBlank { null })) },
                    textStyle = TextStyle(color = MaterialTheme.kbSecondary.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        decorationBox = { innerTextField ->
                            if (draft.categoryName.isNullOrBlank()) {
                                Text("No Category", color = MaterialTheme.kbSecondary.copy(alpha = 0.2f), fontSize = 11.sp)
                        }
                        innerTextField()
                    },
                    cursorBrush = SolidColor(MaterialTheme.kbPrimary),
                    modifier = Modifier.onFocusChanged {
                        if (it.isFocused) scope.launch { rowRequester.bringIntoView() }
                    }
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            if (draft.variants.size <= 1) {
                Row(
                    modifier = Modifier
                        .width(ReviewSheetLayout.PRICE_WIDTH)
                        .background(MaterialTheme.kbBgCard.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("₹", color = MaterialTheme.kbSecondary.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    BasicTextField(
                        value = if (draft.price == 0.0) "" else {
                            val i = draft.price.toLong()
                            if (draft.price == i.toDouble()) i.toString() else draft.price.toString()
                        },
                        onValueChange = { raw ->
                            val p = raw.toDoubleOrNull() ?: 0.0
                            onUpdateDraft(draft.copy(price = p))
                        },
                        textStyle = TextStyle(
                            color = if (draft.isSelected) MaterialTheme.kbTextPrimary else MaterialTheme.kbTextPrimary.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.End,
                            fontWeight = FontWeight.Bold
                        ),
                        cursorBrush = SolidColor(MaterialTheme.kbPrimary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).onFocusChanged {
                            if (it.isFocused) scope.launch { rowRequester.bringIntoView() }
                        }
                    )
                }
            }
        }

        if (draft.variants.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ReviewSheetLayout.CHECKBOX_WIDTH + ReviewSheetLayout.CHECKBOX_GAP - 4.dp, top = 8.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                draft.variants.forEachIndexed { vIndex, variant ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    if (variant.isSelected) MaterialTheme.kbPrimary.copy(alpha = 0.8f) else Color.Transparent
                                )
                                .border(
                                    1.dp,
                                    if (variant.isSelected) MaterialTheme.kbPrimary else MaterialTheme.kbPrimary.copy(alpha = 0.4f),
                                    RoundedCornerShape(5.dp)
                                )
                                .clickable {
                                    val newVariants = draft.variants.toMutableList()
                                    newVariants[vIndex] = variant.copy(isSelected = !variant.isSelected)
                                    onUpdateDraft(draft.copy(variants = newVariants))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (variant.isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.kbTextPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        BasicTextField(
                            value = variant.name,
                            onValueChange = { newName ->
                                val newVariants = draft.variants.toMutableList()
                                newVariants[vIndex] = variant.copy(name = newName)
                                onUpdateDraft(draft.copy(variants = newVariants))
                            },
                            textStyle = TextStyle(
                                color = if (variant.isSelected) MaterialTheme.kbPrimary.copy(alpha = 0.8f) else MaterialTheme.kbPrimary.copy(alpha = 0.3f),
                                fontSize = 12.sp,
                                textDecoration = if (!variant.isSelected) TextDecoration.LineThrough else null
                            ),
                            cursorBrush = SolidColor(MaterialTheme.kbPrimary),
                            modifier = Modifier.weight(1f).onFocusChanged {
                                if (it.isFocused) scope.launch { rowRequester.bringIntoView() }
                            }
                        )

                        Row(
                            modifier = Modifier
                                .width(72.dp)
                                .background(MaterialTheme.kbBgCard.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("₹", color = MaterialTheme.kbSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            BasicTextField(
                                value = if (variant.price == 0.0) "" else {
                                    val i = variant.price.toLong()
                                    if (variant.price == i.toDouble()) i.toString() else variant.price.toString()
                                },
                                onValueChange = { p ->
                                    val newVariants = draft.variants.toMutableList()
                                    newVariants[vIndex] = variant.copy(price = p.toDoubleOrNull() ?: 0.0)
                                    onUpdateDraft(draft.copy(variants = newVariants))
                                },
                                textStyle = TextStyle(
                                    color = if (variant.isSelected) MaterialTheme.kbTextPrimary else MaterialTheme.kbTextPrimary.copy(alpha = 0.4f),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.End
                                ),
                                cursorBrush = SolidColor(MaterialTheme.kbPrimary),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).onFocusChanged {
                                    if (it.isFocused) scope.launch { rowRequester.bringIntoView() }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModeSelectionView(
    selectedCategoryName: String?,
    totalCategoriesCount: Int,
    totalItemsCount: Int,
    onManualClick: (String) -> Unit,
    onSmartImportClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onPdfClick: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .testTag(MenuConfigurationTags.modeSelectionRoot)
            .padding(horizontal = spacing.medium, vertical = spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        // Dashboard Stats with Glassmorphism
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            KhanaBookGlassCard(
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Categories", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$totalCategoriesCount", color = KbBrandSaffron, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }
            }
            KhanaBookGlassCard(
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total Items", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$totalItemsCount", color = KbBrandSaffron, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(spacing.small))

        Text(
            "Configure Menu",
            color = MaterialTheme.kbTextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // 1. Manual Entry (View & Edit) - Redesigned with Glassmorphism
        KhanaBookGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MenuConfigurationTags.manualEntryCard)
        ) {
            Column {
                Row(modifier = Modifier.padding(spacing.medium), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(KbBrandSaffron.copy(alpha = 0.1f), CircleShape)
                            .border(1.dp, KbBrandSaffron.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, null, tint = KbBrandSaffron, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(spacing.medium))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Manual Entry", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Add, view & edit items one by one", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                    }
                }

                HorizontalDivider(color = KbBrandSaffron.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SmartAIOption(
                        icon = Icons.Default.Add,
                        label = "Add",
                        onClick = { onManualClick("add") },
                        modifier = Modifier.weight(1f)
                    )
                    SmartAIOption(
                        icon = Icons.Default.Visibility,
                        label = "View",
                        onClick = { onManualClick("view") },
                        modifier = Modifier.weight(1f)
                    )
                    SmartAIOption(
                        icon = Icons.Default.Edit,
                        label = "Edit",
                        onClick = { onManualClick("edit") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Smart AI - Redesigned with Saffron Glow
        KhanaBookGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MenuConfigurationTags.smartAiCard)
        ) {
            Column {
                Row(modifier = Modifier.padding(spacing.medium), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(KbBrandSaffron.copy(alpha = 0.2f), Color.Transparent)
                                ), CircleShape
                            )
                            .border(1.5.dp, KbBrandSaffron, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = KbBrandSaffron, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(spacing.medium))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Smart AI Import", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(color = KbBrandSaffron, shape = RoundedCornerShape(4.dp)) {
                                Text("MAGICAL", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                            }
                        }
                        Text("Extract from camera, gallery, or PDF", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                    }
                }

                Column {
                    HorizontalDivider(color = KbBrandSaffron.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 20.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SmartAIOption(
                            icon = Icons.Default.CameraAlt,
                            label = "Camera",
                            onClick = onSmartImportClick,
                            testTag = MenuConfigurationTags.smartAiCamera,
                            modifier = Modifier.weight(1f)
                        )
                        SmartAIOption(
                            icon = Icons.Default.PhotoLibrary,
                            label = "Gallery",
                            onClick = onGalleryClick,
                            testTag = MenuConfigurationTags.smartAiGallery,
                            modifier = Modifier.weight(1f)
                        )
                        SmartAIOption(
                            icon = Icons.Default.PictureAsPdf,
                            label = "PDF",
                            onClick = onPdfClick,
                            testTag = MenuConfigurationTags.smartAiPdf,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .background(KbBrandSaffron.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "AI might make mistakes. Please review before saving.",
                            color = KbBrandSaffron.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun SmartAIOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.kbTextPrimary,
            shape = CircleShape,
            modifier = Modifier.size(44.dp),
            border = BorderStroke(1.dp, MaterialTheme.kbSecondary.copy(alpha = 0.2f))
        ) {
            Icon(icon, null, tint = MaterialTheme.kbSecondary, modifier = Modifier.padding(12.dp))
        }
        Spacer(modifier = Modifier.height(KhanaBookTheme.spacing.small))
        Text(
            label,
            color = MaterialTheme.kbTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

private data class PendingManualItemOverwrite(
    val existing: MenuWithVariants,
    val name: String,
    val price: Double,
    val foodType: String,
    val variants: List<Pair<String, Double>>
)

private data class EditableVariantDraft(
    val name: String,
    val price: Double
)
private fun normalizeMenuItemName(name: String): String =
    name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.getDefault())


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ManualMenuView(
    categories: List<CategoryEntity>,
    categoryItemCounts: Map<Long, Int>,
    totalItemsCount: Int,
    selectedCategoryId: Long?,
    menuItems: List<MenuWithVariants>,
    initialAction: String? = null,
    onInitialActionConsumed: () -> Unit = {},
    onCategorySelect: (Long) -> Unit,
    onAddCategory: (String) -> Unit,
    onUpdateCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onAddItem: (String, Double, String, List<Pair<String, Double>>) -> Unit,
    onUpdateItem: (MenuItemEntity) -> Unit,
    onDeleteItem: (MenuItemEntity) -> Unit,
    onToggleAvailability: (Long, Boolean) -> Unit,
    onAddVariant: (Long, String, Double) -> Unit,
    onUpdateVariant: (com.khanabook.lite.pos.data.local.entity.ItemVariantEntity) -> Unit,
    onDeleteVariant: (com.khanabook.lite.pos.data.local.entity.ItemVariantEntity) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showEditCategoryDialog by remember { mutableStateOf<CategoryEntity?>(null) }

    var showAddItemDialog by remember { mutableStateOf(false) }
    var showEditItemDialog by remember { mutableStateOf<MenuWithVariants?>(null) }
    var pendingOverwrite by remember { mutableStateOf<PendingManualItemOverwrite?>(null) }

    // local drill-down state
    var activeCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Intercept back key to reset category drill-down
    BackHandler(enabled = activeCategory != null) {
        activeCategory = null
        searchQuery = ""
    }

    // Make the Manual Entry "Add" / "View" / "Edit" buttons behave distinctly.
    // "add": drill into the selected (or first) category and open the new-item dialog
    // immediately, saving the user two taps. "view"/"edit": land on the categories list.
    LaunchedEffect(initialAction, categories) {
        when (initialAction) {
            "add" -> {
                if (categories.isNotEmpty()) {
                    val target = categories.firstOrNull { it.id == selectedCategoryId }
                        ?: categories.first()
                    onCategorySelect(target.id)
                    activeCategory = target
                    showAddItemDialog = true
                    onInitialActionConsumed()
                }
                // If no categories exist yet, wait — this effect re-runs once they load.
            }
            "view", "edit" -> onInitialActionConsumed()
        }
    }

    val applyItemDraftToExisting: (MenuWithVariants, String, Double, String, List<Pair<String, Double>>) -> Unit =
        { existingItem, updatedName, updatedPrice, updatedFoodType, updatedVariants ->
            onUpdateItem(
                existingItem.menuItem.copy(
                    name = updatedName.trim(),
                    basePrice = updatedPrice.toString(),
                    foodType = updatedFoodType,
                    updatedAt = System.currentTimeMillis()
                )
            )
            existingItem.variants.forEach { onDeleteVariant(it) }
            updatedVariants.forEach { (variantName, variantPrice) ->
                onAddVariant(existingItem.menuItem.id, variantName, variantPrice)
            }
        }

    Box(modifier = Modifier.fillMaxSize().testTag(MenuConfigurationTags.manualMenuRoot)) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Premium Header with Search pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.kbHeaderGradient)
                    .statusBarsPadding()
                    .padding(bottom = 12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (activeCategory != null) {
                                    activeCategory = null
                                    searchQuery = ""
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        
                        Text(
                            text = activeCategory?.name ?: "Menu Configuration",
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        
                        // Add Button (+ icon)
                        IconButton(
                            onClick = {
                                if (activeCategory == null) {
                                    showAddCategoryDialog = true
                                } else {
                                    showAddItemDialog = true
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = if (activeCategory == null) "Search categories & items..." else "Search items...",
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.6f)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, null, tint = Color.White)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.12f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                }
            }

            // Drill Down Screens
            if (activeCategory == null) {
                // LEVEL 1: Categories view
                val filteredCategories = categories.filter {
                    it.name.contains(searchQuery, ignoreCase = true)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.kbBgPrimary)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CATEGORIES",
                            color = Color(0xFF7C3AED), // Muted Purple
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                fontSize = 12.sp
                            )
                        )
                        Text(
                            text = "$totalItemsCount items total",
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        if (filteredCategories.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (searchQuery.isEmpty()) "No categories yet. Tap + to add." else "No categories found",
                                        color = MaterialTheme.kbTextTertiary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = filteredCategories,
                                key = { _, category -> category.id }
                            ) { index, category ->
                                val itemCount = categoryItemCounts[category.id] ?: 0

                                // Colored badge configuration
                                val badgeColors = when (index % 5) {
                                    0 -> Pair(Color(0xFFFFF7ED), Color(0xFFF97316)) // Orange
                                    1 -> Pair(Color(0xFFF5F3FF), Color(0xFF8B5CF6)) // Purple
                                    2 -> Pair(Color(0xFFECFDF5), Color(0xFF10B981)) // Green
                                    3 -> Pair(Color(0xFFEFF6FF), Color(0xFF3B82F6)) // Blue
                                    else -> Pair(Color(0xFFFEF2F2), Color(0xFFEF4444)) // Red
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .shadow(1.dp, RoundedCornerShape(16.dp))
                                        .combinedClickable(
                                            onClick = {
                                                onCategorySelect(category.id)
                                                activeCategory = category
                                            },
                                            onLongClick = { showEditCategoryDialog = category }
                                        ),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
                                    border = BorderStroke(0.5.dp, MaterialTheme.kbOutlineSubtle)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Gold food plate circle icon container
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(Color(0xFFFFF7ED), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Restaurant,
                                                contentDescription = null,
                                                tint = Color(0xFFD97706),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = category.name,
                                                color = MaterialTheme.kbTextPrimary,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "$itemCount items",
                                                color = MaterialTheme.kbTextSecondary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }

                                        // Badge count pill
                                        Box(
                                            modifier = Modifier
                                                .background(badgeColors.first, RoundedCornerShape(10.dp))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = itemCount.toString(),
                                                color = badgeColors.second,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.kbTextTertiary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            // Outlined dashed-like button
                            OutlinedButton(
                                onClick = { showAddCategoryDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .padding(horizontal = 16.dp),
                                border = BorderStroke(1.5.dp, Color(0xFFF97316)),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF97316))
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add New Category", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            } else {
                // LEVEL 2: Items view (inside activeCategory)
                val filteredItems = menuItems.filter {
                    it.menuItem.name.contains(searchQuery, ignoreCase = true)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.kbBgPrimary)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ITEMS IN ${activeCategory?.name?.uppercase()}",
                            color = Color(0xFF7C3AED),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                fontSize = 12.sp
                            )
                        )
                        Text(
                            text = "${filteredItems.size} items found",
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        if (filteredItems.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (searchQuery.isEmpty()) "No items yet. Tap FAB (+) to add." else "No items found",
                                        color = MaterialTheme.kbTextTertiary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        } else {
                            items(
                                items = filteredItems,
                                key = { it.menuItem.id }
                            ) { itemWithVariants ->
                                MenuItemRow(
                                    itemWithVariants = itemWithVariants,
                                    onToggleAvailability = onToggleAvailability,
                                    onEditClick = { showEditItemDialog = it }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Float FAB to add item when viewing category details
        if (activeCategory != null && selectedCategoryId != null) {
            FloatingActionButton(
                onClick = { showAddItemDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = Color(0xFFF97316),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, "Add Item")
            }
        }
    }

    pendingOverwrite?.let { pending ->
        KhanaBookDialog(
            onDismissRequest = { pendingOverwrite = null },
            title = "Item already exists",
            message = "An item with this name already exists in this category. Do you want to overwrite/update the existing item?"
        ) {
            TextButton(onClick = { pendingOverwrite = null }) {
                Text("Cancel", color = MaterialTheme.kbPrimary)
            }
            TextButton(
                onClick = {
                    applyItemDraftToExisting(
                        pending.existing,
                        pending.name,
                        pending.price,
                        pending.foodType,
                        pending.variants
                    )
                    pendingOverwrite = null
                }
            ) {                    Text("Overwrite", color = MaterialTheme.kbPrimary)
            }
        }
    }

    // Category Dialogs
    if (showAddCategoryDialog) {
        CategoryEditDialog(
            title = "Add Category",
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name ->
                onAddCategory(name)
                showAddCategoryDialog = false
            }
        )
    }

    if (showEditCategoryDialog != null) {
        CategoryEditDialog(
            title = "Edit Category",
            initialName = showEditCategoryDialog?.name ?: "",
            onDismiss = { showEditCategoryDialog = null },
            onConfirm = { name ->
                showEditCategoryDialog?.let {
                    onUpdateCategory(it.copy(name = name))
                }
                showEditCategoryDialog = null
            }
        )
    }

    // Item Dialogs
    if (showAddItemDialog) {
        ItemEditDialog(
            title = "Add New Item",
            onDismiss = { showAddItemDialog = false },
            onConfirm = { name, price, type, draftVariants ->
                val normalizedName = normalizeMenuItemName(name)
                val existing = menuItems.firstOrNull {
                    normalizeMenuItemName(it.menuItem.name) == normalizedName
                }
                if (existing != null) {
                    pendingOverwrite = PendingManualItemOverwrite(
                        existing = existing,
                        name = name.trim(),
                        price = price,
                        foodType = type,
                        variants = draftVariants
                    )
                } else {
                    onAddItem(name.trim(), price, type, draftVariants)
                }
                showAddItemDialog = false
            }
        )
    }

    showEditItemDialog?.let { itemWithVariants ->
        ItemEditDialog(
            title = "Edit Item",
            initialName = itemWithVariants.menuItem.name,
            initialPrice = itemWithVariants.menuItem.basePrice.toDoubleOrNull() ?: 0.0,
            initialType = itemWithVariants.menuItem.foodType,
            variants = itemWithVariants.variants,
            onDismiss = { showEditItemDialog = null },
            onConfirm = { name, price, type, updatedVariants ->
                applyItemDraftToExisting(itemWithVariants, name, price, type, updatedVariants)
                showEditItemDialog = null
            }
        )
    }

}

@Composable
fun MenuItemRow(
    itemWithVariants: MenuWithVariants,
    onToggleAvailability: (Long, Boolean) -> Unit,
    onEditClick: (MenuWithVariants) -> Unit
) {
    val item = itemWithVariants.menuItem
    val variants = itemWithVariants.variants
    val priceText = if (variants.isNotEmpty()) {
        "₹${variants.minOf { it.price.toDoubleOrNull() ?: 0.0 }.toInt()}+"
    } else {
        "₹${item.basePrice.toDoubleOrNull()?.toInt() ?: item.basePrice}"
    }
    val descriptionText = if (variants.isNotEmpty()) {
        "${variants.size} variants available"
    } else {
        item.description?.takeIf { it.isNotBlank() } ?: item.name
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onEditClick(itemWithVariants) },
                onLongClick = { onEditClick(itemWithVariants) }
            ),
        shape = KbShape.Medium,
        colors = CardDefaults.cardColors(
            containerColor = if (item.isAvailable) MaterialTheme.kbBgCard else MaterialTheme.kbBgCard.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            0.5.dp,
            if (item.isAvailable) MaterialTheme.kbOutlineSubtle else MaterialTheme.kbOutlineSubtle.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(KhanaBookTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val indicatorColor = if (item.foodType == "veg") Color(0xFF10B981) else Color(0xFFEF4444)
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .border(1.dp, indicatorColor, RoundedCornerShape(2.dp))
                            .padding(2.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(indicatorColor, CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(KhanaBookTheme.spacing.small))
                    Text(
                        text = item.name,
                        color = if (item.isAvailable) MaterialTheme.kbTextPrimary else MaterialTheme.kbTextPrimary.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = priceText,
                    color = KbBrandSaffron,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = descriptionText,
                    color = MaterialTheme.kbTextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(KhanaBookTheme.spacing.small))
            KhanaBookSwitch(
                checked = item.isAvailable,
                onCheckedChange = { onToggleAvailability(item.id, it) },
                checkedTrackColor = KbSuccess,
                checkedThumbColor = Color.White
            )
            IconButton(
                onClick = { onEditClick(itemWithVariants) },
                modifier = Modifier.size(KbButtonSize.HeightIcon)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit ${item.name}",
                    tint = MaterialTheme.kbTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun CategoryEditDialog(
    title: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }

    KhanaBookDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Category Name", color = MaterialTheme.kbPrimary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.kbPrimary,
                    unfocusedBorderColor = MaterialTheme.kbOutlineSubtle.copy(alpha = 0.5f),
                    focusedTextColor = MaterialTheme.kbTextPrimary,
                    unfocusedTextColor = MaterialTheme.kbTextPrimary
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        TextButton(
            onClick = onDismiss
        ) {
            Text("Cancel", color = MaterialTheme.kbPrimary)
        }
        TextButton(
            onClick = { if (name.isNotBlank()) onConfirm(name) },
            enabled = name.isNotBlank()
        ) {
            Text("Save", color = MaterialTheme.kbPrimary)
        }
    }
}

@Composable
fun ItemEditDialog(
    title: String,
    initialName: String = "",
    initialPrice: Double = 0.0,
    initialType: String = "veg",
    variants: List<com.khanabook.lite.pos.data.local.entity.ItemVariantEntity> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, List<Pair<String, Double>>) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var price by remember(initialPrice) { mutableStateOf(if (initialPrice == 0.0) "" else initialPrice.toInt().toString()) }
    var foodType by remember(initialType) { mutableStateOf(initialType) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var priceError by remember { mutableStateOf<String?>(null) }
    var variantError by remember { mutableStateOf<String?>(null) }

    var showAddVariantDialog by remember { mutableStateOf(false) }
    var editableVariants by remember(variants) {
        mutableStateOf(
            variants.map {
                EditableVariantDraft(
                    name = it.variantName,
                    price = it.price.toDoubleOrNull() ?: 0.0
                )
            }
        )
    }

    KhanaBookDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (nameError != null) nameError = null
                    },
                    label = { Text("Item Name", color = MaterialTheme.kbPrimary) },
                    isError = nameError != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.kbPrimary,
                        unfocusedBorderColor = MaterialTheme.kbOutlineSubtle.copy(alpha = 0.5f),
                        focusedTextColor = MaterialTheme.kbTextPrimary,
                        unfocusedTextColor = MaterialTheme.kbTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        nameError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                )

                if (editableVariants.isEmpty()) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = {
                            price = it
                            if (priceError != null) priceError = null
                        },
                        label = { Text("Base Price (₹)", color = MaterialTheme.kbPrimary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = priceError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.kbPrimary,
                            unfocusedBorderColor = MaterialTheme.kbOutlineSubtle.copy(alpha = 0.5f),
                            focusedTextColor = MaterialTheme.kbTextPrimary,
                            unfocusedTextColor = MaterialTheme.kbTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            priceError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = foodType == "veg",
                            onClick = { foodType = "veg" },
                            colors = RadioButtonDefaults.colors(selectedColor = KbSuccess)
                        )
                        Text("Veg", color = MaterialTheme.kbTextPrimary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = foodType == "non-veg",
                            onClick = { foodType = "non-veg" },
                            colors = RadioButtonDefaults.colors(selectedColor = KbError)
                        )
                        Text("Non-Veg", color = MaterialTheme.kbTextPrimary)
                    }
                }

                HorizontalDivider(color = MaterialTheme.kbOutlineSubtle.copy(alpha = 0.2f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Variants", color = MaterialTheme.kbSecondary, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showAddVariantDialog = true }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Text("Add Variant", fontSize = 12.sp)
                    }
                }

                editableVariants.forEachIndexed { index, variantDraft ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = variantDraft.name,
                            onValueChange = {
                                editableVariants = editableVariants.toMutableList().also { updated ->
                                    updated[index] = variantDraft.copy(name = it)
                                }
                                if (variantError != null) variantError = null
                            },
                            label = { Text("Name", fontSize = 10.sp) },
                            modifier = Modifier.weight(0.7f),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.kbTextPrimary, unfocusedTextColor = MaterialTheme.kbTextPrimary)
                        )
                        OutlinedTextField(
                            value = if (variantDraft.price == 0.0) "" else variantDraft.price.toInt().toString(),
                            onValueChange = {
                                val parsed = it.toDoubleOrNull()
                                if (it.isBlank() || parsed == null || parsed >= 0.0) {
                                    editableVariants = editableVariants.toMutableList().also { updated ->
                                        updated[index] = variantDraft.copy(price = parsed ?: 0.0)
                                    }
                                    if (variantError != null) variantError = null
                                }
                            },
                            label = { Text("Price", fontSize = 10.sp) },
                            modifier = Modifier.weight(0.3f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.kbTextPrimary, unfocusedTextColor = MaterialTheme.kbTextPrimary)
                        )
                        IconButton(onClick = {
                            editableVariants = editableVariants.toMutableList().also { updated ->
                                updated.removeAt(index)
                            }
                        }) {
                            Icon(Icons.Default.Delete, null, tint = KbError.copy(alpha = 0.7f), modifier = Modifier.size(KhanaBookTheme.iconSize.small))
                        }
                    }
                }

                variantError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    ) {
        TextButton(onClick = onDismiss) {
            Text("Cancel", color = MaterialTheme.kbPrimary)
        }
        TextButton(
            onClick = {
                val normalizedName = name.trim()
                val parsedPrice = price.toDoubleOrNull()
                nameError = null
                priceError = null
                variantError = null

                val hasInlineVariants = editableVariants.isNotEmpty()
                val invalidDraftVariant = editableVariants.firstOrNull { it.name.isBlank() || it.price < 0.0 }

                when {
                    normalizedName.isBlank() -> nameError = "Item name is required"
                    !hasInlineVariants && price.isBlank() -> priceError = "Enter a valid item price"
                    !hasInlineVariants && parsedPrice == null -> priceError = "Enter a valid item price"
                    !hasInlineVariants && (parsedPrice ?: 0.0) < 0.0 -> priceError = "Price cannot be negative"
                    invalidDraftVariant != null -> variantError = "Enter a valid item price"
                    else -> onConfirm(
                        normalizedName,
                        parsedPrice ?: 0.0,
                        foodType,
                        editableVariants.map { it.name.trim() to it.price }
                    )
                }
            }
        ) {
            Text("Save", color = MaterialTheme.kbPrimary)
        }
    }

    if (showAddVariantDialog) {
        var newVName by remember { mutableStateOf("") }
        var newVPrice by remember { mutableStateOf("") }
        var newVariantError by remember { mutableStateOf<String?>(null) }

        KhanaBookDialog(
            onDismissRequest = { showAddVariantDialog = false },
            title = "Add Variant",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newVName, onValueChange = { newVName = it }, label = { Text("Variant Name") })
                    OutlinedTextField(value = newVPrice, onValueChange = { newVPrice = it; newVariantError = null }, label = { Text("Price (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = newVariantError != null)
                    newVariantError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
        ) {
            TextButton(onClick = { showAddVariantDialog = false }) {
                Text("Cancel", color = MaterialTheme.kbPrimary)
            }
            TextButton(onClick = {
                val parsedVariantPrice = newVPrice.toDoubleOrNull()
                when {
                    newVName.isBlank() -> newVariantError = "Item name is required"
                    newVPrice.isBlank() || parsedVariantPrice == null -> newVariantError = "Enter a valid item price"
                    (parsedVariantPrice ?: 0.0) < 0.0 -> newVariantError = "Price cannot be negative"
                    else -> {
                        val variantPrice = parsedVariantPrice ?: 0.0
                        editableVariants = editableVariants + EditableVariantDraft(
                            name = newVName.trim(),
                            price = variantPrice
                        )
                        price = ""
                        showAddVariantDialog = false
                    }
                }
            }) {
                Text("Add", color = MaterialTheme.kbPrimary)
            }
        }
    }
}
