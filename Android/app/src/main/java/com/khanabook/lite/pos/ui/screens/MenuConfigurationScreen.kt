@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.khanabook.lite.pos.data.local.entity.CategoryEntity
import com.khanabook.lite.pos.data.local.entity.MenuItemEntity
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel

object ReviewSheetLayout {
    val PRICE_WIDTH = 90.dp
    val CHECKBOX_WIDTH = 26.dp
    val CHECKBOX_GAP = 14.dp
    val FOOD_ICON_WIDTH = 40.dp
    val HORIZONTAL_PADDING = 16.dp
    val CARD_PADDING = 16.dp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuConfigurationScreen(
    navController: NavController,
    onBackClick: () -> Unit,
    viewModel: MenuViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
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
                }
            } catch (t: Throwable) {
                // Error handling could be added to viewModel or snackbar
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
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    var showOverwriteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Menu Configuration", color = PrimaryGold) },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PrimaryGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBrown1)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBrown1
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (ocrUiState.configMode == null) {
                ModeSelectionView(
                    selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name,
                    onManualClick = { viewModel.setConfigMode("manual") },
                    onSmartImportClick = {
                        val catName = categories.find { it.id == selectedCategoryId }?.name ?: ""
                        navController.navigate("ocr_scanner/$catName")
                    },
                    onGalleryClick = { galleryLauncher.launch("image/*") },
                    onPdfClick = { pdfLauncher.launch("application/pdf") }
                )
            } else {
                ManualMenuView(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    menuItems = menuItems,
                    onCategorySelect = { viewModel.selectCategory(it) },
                    onAddCategory = { viewModel.addCategory(it, true) },
                    onUpdateCategory = { viewModel.updateCategory(it) },
                    onDeleteCategory = { viewModel.deleteCategory(it) },
                    onAddItem = { name, price, type -> 
                        selectedCategoryId?.let { viewModel.addItem(it, name, price, type) }
                    },
                    onUpdateItem = { viewModel.updateItem(it) },
                    onDeleteItem = { viewModel.deleteItem(it) },
                    onAddVariant = { itemId, name, price -> viewModel.addVariant(itemId, name, price) },
                    onUpdateVariant = { viewModel.updateVariant(it) },
                    onDeleteVariant = { viewModel.deleteVariant(it) }
                )
            }

            if (ocrUiState.drafts.isNotEmpty()) {
                ReviewDetectedItemsSheet(
                    drafts = ocrUiState.drafts,
                    onDismiss = { viewModel.clearDrafts() },
                    onConfirm = { 
                        viewModel.checkForConflicts(selectedCategoryId) { hasConflict ->
                            if (hasConflict) {
                                showOverwriteDialog = true
                            } else {
                                viewModel.saveImportedMenu(selectedCategoryId, false)
                            }
                        }
                    },
                    onConfirmOverwrite = {
                        viewModel.saveImportedMenu(selectedCategoryId, true)
                        showOverwriteDialog = false
                    },
                    onToggleSelection = { viewModel.toggleDraftSelection(it) },
                    onUpdateDraft = { index, draft -> viewModel.updateDraft(index, draft) },
                    onToggleFoodType = { viewModel.toggleDraftFoodType(it) }
                )
            }

            if (showOverwriteDialog) {
                AlertDialog(
                    onDismissRequest = { showOverwriteDialog = false },
                    containerColor = DarkBrown2,
                    title = { Text("Items Already Exist", color = PrimaryGold) },
                    text = { Text("Some items you are adding already exist in your menu. Do you want to overwrite them or skip duplicates?", color = TextLight) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.saveImportedMenu(selectedCategoryId, true)
                            showOverwriteDialog = false
                        }) {
                            Text("Overwrite All", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                viewModel.saveImportedMenu(selectedCategoryId, false)
                                showOverwriteDialog = false
                            }) {
                                Text("Merge & Skip", color = SuccessGreen)
                            }
                            TextButton(onClick = { showOverwriteDialog = false }) {
                                Text("Cancel", color = TextGold)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ReviewDetectedItemsSheet(
    drafts: List<MenuViewModel.DraftMenuItem>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onConfirmOverwrite: () -> Unit,
    onToggleSelection: (Int) -> Unit,
    onUpdateDraft: (Int, MenuViewModel.DraftMenuItem) -> Unit,
    onToggleFoodType: (Int) -> Unit
) {
    val selectedCount = drafts.count { it.isSelected }
    
    val headerPadding = ReviewSheetLayout.HORIZONTAL_PADDING + ReviewSheetLayout.CARD_PADDING
    val checkboxPlaceholder = ReviewSheetLayout.CHECKBOX_WIDTH + ReviewSheetLayout.CHECKBOX_GAP

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(1.0f) // FULL SCREEN
                .statusBarsPadding()
                .navigationBarsPadding()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(DarkBrown1)
                .clickable(enabled = false) { }
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(PrimaryGold.copy(alpha = 0.4f), CircleShape)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Review Detected Items",
                        color = PrimaryGold,
                        fontSize = 24.sp, // Increased
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${drafts.size} items found · $selectedCount selected",
                        color = TextGold.copy(alpha = 0.7f),
                        fontSize = 15.sp // Increased
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextGold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = headerPadding, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(checkboxPlaceholder))
                Text("Item Name", color = TextGold.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Price", color = TextGold.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(ReviewSheetLayout.PRICE_WIDTH))
                Spacer(modifier = Modifier.width(ReviewSheetLayout.FOOD_ICON_WIDTH))
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = ReviewSheetLayout.HORIZONTAL_PADDING),
                verticalArrangement = Arrangement.spacedBy(12.dp), // Increased spacing
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                val groupedDrafts = drafts.withIndex().groupBy { it.value.categoryName ?: "Uncategorized" }
                
                groupedDrafts.forEach { (categoryName, indexedItems) ->
                    val allInCategorySelected = indexedItems.all { it.value.isSelected }
                    
                    item(key = "header_$categoryName") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (allInCategorySelected) PrimaryGold else Color.Transparent
                                    )
                                    .border(
                                        1.5.dp,
                                        if (allInCategorySelected) PrimaryGold else TextGold.copy(alpha = 0.5f),
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
                                        tint = DarkBrown1,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Text(
                                categoryName.uppercase(),
                                color = PrimaryGold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    items(indexedItems.size) { i ->
                        val index = indexedItems[i].index
                        val draft = indexedItems[i].value
                        val bgColor by animateColorAsState(
                            targetValue = if (draft.isSelected) DarkBrown2 else Color.Transparent,
                            animationSpec = tween(200),
                            label = "item_bg"
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgColor)
                                .border(
                                    width = 0.5.dp,
                                    color = if (draft.isSelected) BorderGold else BorderGold.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onToggleSelection(index) }
                                .padding(horizontal = ReviewSheetLayout.CARD_PADDING, vertical = 14.dp)
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
                                            if (draft.isSelected) PrimaryGold else Color.Transparent
                                        )
                                        .border(
                                            1.5.dp,
                                            if (draft.isSelected) PrimaryGold else TextGold.copy(alpha = 0.4f),
                                            RoundedCornerShape(6.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (draft.isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = DarkBrown1,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(ReviewSheetLayout.CHECKBOX_GAP))

                                Column(modifier = Modifier.weight(1f)) {
                                    BasicTextField(
                                        value = draft.name,
                                        onValueChange = { onUpdateDraft(index, draft.copy(name = it)) },
                                        textStyle = TextStyle(
                                            color = if (draft.isSelected) TextLight else TextLight.copy(alpha = 0.4f),
                                            fontSize = 18.sp, // INCREASED
                                            fontWeight = FontWeight.Bold,
                                            textDecoration = if (!draft.isSelected) TextDecoration.LineThrough else null
                                        ),
                                        cursorBrush = SolidColor(PrimaryGold)
                                    )
                                    
                                    BasicTextField(
                                        value = draft.categoryName ?: "",
                                        onValueChange = { onUpdateDraft(index, draft.copy(categoryName = it.ifBlank { null })) },
                                        textStyle = TextStyle(
                                            color = PrimaryGold.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        decorationBox = { innerTextField ->
                                            if (draft.categoryName.isNullOrBlank()) {
                                                Text("Tap to set category", color = PrimaryGold.copy(alpha = 0.3f), fontSize = 12.sp)
                                            }
                                            innerTextField()
                                        },
                                        cursorBrush = SolidColor(PrimaryGold)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                if (draft.variants.size <= 1) {
                                    Row(
                                        modifier = Modifier
                                            .width(ReviewSheetLayout.PRICE_WIDTH)
                                            .background(DarkBrown1.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("₹", color = PrimaryGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        BasicTextField(
                                            value = if (draft.price == 0.0) "" else draft.price.toInt().toString(),
                                            onValueChange = { raw ->
                                                val p = raw.toDoubleOrNull() ?: 0.0
                                                onUpdateDraft(index, draft.copy(price = p))
                                            },
                                            textStyle = TextStyle(
                                                color = if (draft.isSelected) TextLight else TextLight.copy(alpha = 0.4f),
                                                fontSize = 16.sp, // INCREASED
                                                textAlign = TextAlign.End
                                            ),
                                            cursorBrush = SolidColor(PrimaryGold),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                
                                IconButton(
                                    onClick = { onToggleFoodType(index) },
                                    modifier = Modifier.size(ReviewSheetLayout.FOOD_ICON_WIDTH)
                                ) {
                                    Icon(
                                        Icons.Default.Circle,
                                        contentDescription = null,
                                        tint = if (draft.foodType == "veg") VegGreen else NonVegRed,
                                        modifier = Modifier.size(16.dp).border(1.dp, Color.White, CircleShape)
                                    )
                                }
                            }

                            if (draft.variants.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 44.dp, top = 12.dp, end = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    draft.variants.forEachIndexed { vIndex, variant ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(
                                                        if (variant.isSelected) PrimaryGold.copy(alpha = 0.8f) else Color.Transparent
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (variant.isSelected) PrimaryGold else TextGold.copy(alpha = 0.4f),
                                                        RoundedCornerShape(5.dp)
                                                    )
                                                    .clickable { 
                                                        val newVariants = draft.variants.toMutableList()
                                                        newVariants[vIndex] = variant.copy(isSelected = !variant.isSelected)
                                                        onUpdateDraft(index, draft.copy(variants = newVariants))
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (variant.isSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = DarkBrown1,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(14.dp))

                                            BasicTextField(
                                                value = variant.name,
                                                onValueChange = { newName ->
                                                    val newVariants = draft.variants.toMutableList()
                                                    newVariants[vIndex] = variant.copy(name = newName)
                                                    onUpdateDraft(index, draft.copy(variants = newVariants))
                                                },
                                                textStyle = TextStyle(
                                                    color = if (variant.isSelected) TextGold.copy(alpha = 0.8f) else TextGold.copy(alpha = 0.3f),
                                                    fontSize = 15.sp,
                                                    textDecoration = if (!variant.isSelected) TextDecoration.LineThrough else null
                                                ),
                                                cursorBrush = SolidColor(PrimaryGold),
                                                modifier = Modifier.weight(1f)
                                            )
                                            
                                            Row(
                                                modifier = Modifier
                                                    .width(80.dp)
                                                    .background(DarkBrown1.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("₹", color = if (variant.isSelected) PrimaryGold else PrimaryGold.copy(alpha = 0.3f), fontSize = 14.sp)
                                                BasicTextField(
                                                    value = if (variant.price == 0.0) "" else variant.price.toInt().toString(),
                                                    onValueChange = { raw ->
                                                        val p = raw.toDoubleOrNull() ?: 0.0
                                                        val newVariants = draft.variants.toMutableList()
                                                        newVariants[vIndex] = variant.copy(price = p)
                                                        onUpdateDraft(index, draft.copy(variants = newVariants))
                                                    },
                                                    textStyle = TextStyle(
                                                        color = if (variant.isSelected) TextLight.copy(alpha = 0.9f) else TextLight.copy(alpha = 0.3f),
                                                        fontSize = 14.sp,
                                                        textAlign = TextAlign.End
                                                    ),
                                                    cursorBrush = SolidColor(PrimaryGold),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                color = DarkBrown2,
                border = BorderStroke(0.5.dp, BorderGold.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.5.dp, NonVegRed.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NonVegRed),
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Discard", maxLines = 1, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = selectedCount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryGold,
                            contentColor = DarkBrown1
                        ),
                        modifier = Modifier.weight(2f).height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, null, modifier = Modifier.size(24.dp))
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
fun ModeSelectionView(
    selectedCategoryName: String?,
    onManualClick: () -> Unit,
    onSmartImportClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onPdfClick: () -> Unit
) {
    var isSmartAIExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Add Menu Items", color = TextGold, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))

        // 1. Manual Entry (View & Edit)
        Card(
            onClick = onManualClick,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkBrown2),
            border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.2f))
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = PrimaryGold.copy(alpha = 0.1f), shape = CircleShape, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.Edit, null, tint = PrimaryGold, modifier = Modifier.padding(14.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Manual Entry", color = TextLight, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("View & Edit items one by one", color = TextGold.copy(alpha = 0.6f), fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Smart AI
        Card(
            onClick = { isSmartAIExpanded = !isSmartAIExpanded },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkBrown2),
            border = BorderStroke(1.dp, if (isSmartAIExpanded) PrimaryGold.copy(alpha = 0.5f) else BorderGold.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.animateContentSize()) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = PrimaryGold.copy(alpha = 0.2f), shape = CircleShape, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Default.AutoAwesome, null, tint = PrimaryGold, modifier = Modifier.padding(14.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Smart AI", color = TextLight, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(color = PrimaryGold, shape = RoundedCornerShape(4.dp)) {
                                Text("AI", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, color = DarkBrown1)
                            }
                        }
                        Text("Extract from Camera, Gallery or PDF", color = TextGold.copy(alpha = 0.6f), fontSize = 14.sp)
                    }
                    Icon(
                        if (isSmartAIExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null,
                        tint = TextGold.copy(alpha = 0.5f)
                    )
                }

                if (isSmartAIExpanded) {
                    HorizontalDivider(color = BorderGold.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 20.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SmartAIOption(
                            icon = Icons.Default.CameraAlt,
                            label = "Camera",
                            onClick = onSmartImportClick
                        )
                        SmartAIOption(
                            icon = Icons.Default.PhotoLibrary,
                            label = "Gallery",
                            onClick = onGalleryClick
                        )
                        SmartAIOption(
                            icon = Icons.Default.PictureAsPdf,
                            label = "PDF",
                            onClick = onPdfClick
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
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = DarkBrown1,
            shape = CircleShape,
            modifier = Modifier.size(44.dp),
            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.2f))
        ) {
            Icon(icon, null, tint = PrimaryGold, modifier = Modifier.padding(12.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = TextLight, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ManualMenuView(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    menuItems: List<com.khanabook.lite.pos.data.local.relation.MenuWithVariants>,
    onCategorySelect: (Long) -> Unit,
    onAddCategory: (String) -> Unit,
    onUpdateCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onAddItem: (String, Double, String) -> Unit,
    onUpdateItem: (MenuItemEntity) -> Unit,
    onDeleteItem: (MenuItemEntity) -> Unit,
    onAddVariant: (Long, String, Double) -> Unit,
    onUpdateVariant: (com.khanabook.lite.pos.data.local.entity.ItemVariantEntity) -> Unit,
    onDeleteVariant: (com.khanabook.lite.pos.data.local.entity.ItemVariantEntity) -> Unit
) {
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showEditCategoryDialog by remember { mutableStateOf<CategoryEntity?>(null) }
    var showDeleteCategoryDialog by remember { mutableStateOf<CategoryEntity?>(null) }
    
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showEditItemDialog by remember { mutableStateOf<com.khanabook.lite.pos.data.local.relation.MenuWithVariants?>(null) }
    var showDeleteItemDialog by remember { mutableStateOf<MenuItemEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(categories) { category ->
                val isSelected = category.id == selectedCategoryId
                Surface(
                    onClick = { onCategorySelect(category.id) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) PrimaryGold else DarkBrown2,
                    border = BorderStroke(1.dp, if (isSelected) PrimaryGold else BorderGold.copy(alpha = 0.3f)),
                    contentColor = if (isSelected) DarkBrown1 else TextLight
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .combinedClickable(
                                onClick = { onCategorySelect(category.id) },
                                onLongClick = { showDeleteCategoryDialog = category }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = category.name,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Category",
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { showEditCategoryDialog = category },
                                tint = DarkBrown1
                            )
                        }
                    }
                }
            }
            item {
                IconButton(onClick = { showAddCategoryDialog = true }) {
                    Icon(Icons.Default.Add, null, tint = PrimaryGold)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(menuItems) { itemWithVariants ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { showEditItemDialog = itemWithVariants },
                            onLongClick = { showDeleteItemDialog = itemWithVariants.menuItem }
                        ),
                    colors = CardDefaults.cardColors(containerColor = DarkBrown2),
                    border = BorderStroke(0.5.dp, BorderGold.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                itemWithVariants.menuItem.name,
                                color = TextLight,
                                fontWeight = FontWeight.Bold
                            )
                            val variantCount = itemWithVariants.variants.size
                            if (variantCount > 0) {
                                Text(
                                    "$variantCount variants",
                                    color = TextGold.copy(alpha = 0.6f),
                                    fontSize = 11.sp
                                )
                            } else {
                                Text(
                                    "₹${itemWithVariants.menuItem.basePrice}",
                                    color = TextGold.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Circle,
                                null,
                                tint = if (itemWithVariants.menuItem.foodType == "veg") VegGreen else NonVegRed,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Item",
                                tint = PrimaryGold.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = { showAddItemDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryGold,
                contentColor = DarkBrown1
            ),
            enabled = selectedCategoryId != null,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add New Item", fontWeight = FontWeight.Bold)
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

    if (showDeleteCategoryDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteCategoryDialog = null },
            containerColor = DarkBrown2,
            title = { Text("Delete Category?", color = PrimaryGold) },
            text = { Text("All items in \"${showDeleteCategoryDialog?.name}\" will also be deleted. This cannot be undone.", color = TextLight) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteCategoryDialog?.let { onDeleteCategory(it) }
                    showDeleteCategoryDialog = null
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCategoryDialog = null }) {
                    Text("Cancel", color = TextGold)
                }
            }
        )
    }

    // Item Dialogs
    if (showAddItemDialog) {
        ItemEditDialog(
            title = "Add New Item",
            onDismiss = { showAddItemDialog = false },
            onConfirm = { name, price, type ->
                onAddItem(name, price, type)
                showAddItemDialog = false
            }
        )
    }

    if (showEditItemDialog != null) {
        val itemWithVariants = showEditItemDialog!!
        ItemEditDialog(
            title = "Edit Item",
            initialName = itemWithVariants.menuItem.name,
            initialPrice = itemWithVariants.menuItem.basePrice.toDoubleOrNull() ?: 0.0,
            initialType = itemWithVariants.menuItem.foodType,
            variants = itemWithVariants.variants,
            onDismiss = { showEditItemDialog = null },
            onConfirm = { name, price, type ->
                onUpdateItem(itemWithVariants.menuItem.copy(
                    name = name,
                    basePrice = price.toString(),
                    foodType = type
                ))
                showEditItemDialog = null
            },
            onAddVariant = { name, price -> onAddVariant(itemWithVariants.menuItem.id, name, price) },
            onUpdateVariant = onUpdateVariant,
            onDeleteVariant = onDeleteVariant
        )
    }

    if (showDeleteItemDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteItemDialog = null },
            containerColor = DarkBrown2,
            title = { Text("Delete Item?", color = PrimaryGold) },
            text = { Text("Are you sure you want to delete \"${showDeleteItemDialog?.name}\"?", color = TextLight) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteItemDialog?.let { onDeleteItem(it) }
                    showDeleteItemDialog = null
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteItemDialog = null }) {
                    Text("Cancel", color = TextGold)
                }
            }
        )
    }
}

@Composable
fun CategoryEditDialog(
    title: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkBrown2,
        title = { Text(title, color = PrimaryGold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Category Name", color = TextGold) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGold,
                    unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Save", color = PrimaryGold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextGold)
            }
        }
    )
}

@Composable
fun ItemEditDialog(
    title: String,
    initialName: String = "",
    initialPrice: Double = 0.0,
    initialType: String = "veg",
    variants: List<com.khanabook.lite.pos.data.local.entity.ItemVariantEntity> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String) -> Unit,
    onAddVariant: (String, Double) -> Unit = { _, _ -> },
    onUpdateVariant: (com.khanabook.lite.pos.data.local.entity.ItemVariantEntity) -> Unit = {},
    onDeleteVariant: (com.khanabook.lite.pos.data.local.entity.ItemVariantEntity) -> Unit = {}
) {
    var name by remember { mutableStateOf(initialName) }
    var price by remember { mutableStateOf(if (initialPrice == 0.0) "" else initialPrice.toInt().toString()) }
    var foodType by remember { mutableStateOf(initialType) }
    
    var showAddVariantDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkBrown2,
        title = { Text(title, color = PrimaryGold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name", color = TextGold) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGold,
                        unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (variants.isEmpty()) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Base Price (₹)", color = TextGold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGold,
                            unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight
                        ),
                        modifier = Modifier.fillMaxWidth()
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
                            colors = RadioButtonDefaults.colors(selectedColor = VegGreen)
                        )
                        Text("Veg", color = TextLight)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = foodType == "non-veg",
                            onClick = { foodType = "non-veg" },
                            colors = RadioButtonDefaults.colors(selectedColor = NonVegRed)
                        )
                        Text("Non-Veg", color = TextLight)
                    }
                }

                if (variants.isNotEmpty() || initialName.isNotBlank()) {
                    HorizontalDivider(color = BorderGold.copy(alpha = 0.2f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Variants", color = PrimaryGold, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showAddVariantDialog = true }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Text("Add Variant", fontSize = 12.sp)
                        }
                    }

                    variants.forEach { variant ->
                        var vName by remember { mutableStateOf(variant.variantName) }
                        var vPrice by remember { mutableStateOf(variant.price.toDoubleOrNull()?.toInt()?.toString() ?: "") }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = vName,
                                onValueChange = { 
                                    vName = it
                                    onUpdateVariant(variant.copy(variantName = it))
                                },
                                label = { Text("Name", fontSize = 10.sp) },
                                modifier = Modifier.weight(1.5f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextLight, unfocusedTextColor = TextLight)
                            )
                            OutlinedTextField(
                                value = vPrice,
                                onValueChange = { 
                                    vPrice = it
                                    it.toDoubleOrNull()?.let { p ->
                                        onUpdateVariant(variant.copy(price = p.toString()))
                                    }
                                },
                                label = { Text("Price", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextLight, unfocusedTextColor = TextLight)
                            )
                            IconButton(onClick = { onDeleteVariant(variant) }) {
                                Icon(Icons.Default.Delete, null, tint = NonVegRed.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name, price.toDoubleOrNull() ?: 0.0, foodType) },
                enabled = name.isNotBlank()
            ) {
                Text("Save", color = PrimaryGold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextGold)
            }
        }
    )

    if (showAddVariantDialog) {
        var newVName by remember { mutableStateOf("") }
        var newVPrice by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showAddVariantDialog = false },
            containerColor = DarkBrown2,
            title = { Text("Add Variant", color = PrimaryGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newVName, onValueChange = { newVName = it }, label = { Text("Variant Name") })
                    OutlinedTextField(value = newVPrice, onValueChange = { newVPrice = it }, label = { Text("Price (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newVName.isNotBlank() && newVPrice.isNotBlank()) {
                        onAddVariant(newVName, newVPrice.toDoubleOrNull() ?: 0.0)
                        showAddVariantDialog = false
                    }
                }) {
                    Text("Add", color = PrimaryGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddVariantDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
