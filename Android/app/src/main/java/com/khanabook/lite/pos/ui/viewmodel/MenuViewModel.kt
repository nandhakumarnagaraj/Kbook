package com.khanabook.lite.pos.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.data.local.entity.CategoryEntity
import com.khanabook.lite.pos.data.local.entity.ItemVariantEntity
import com.khanabook.lite.pos.data.local.entity.MenuItemEntity
import com.khanabook.lite.pos.data.local.relation.MenuWithVariants
import com.khanabook.lite.pos.data.repository.CategoryRepository
import com.khanabook.lite.pos.data.repository.MenuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class MenuViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val menuRepository: MenuRepository
) : ViewModel() {
    private val ocrDebugTag = "OCR_DEBUG"

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.getAllCategoriesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val selectedCategoryId = MutableStateFlow<Long?>(null)

    val menuItems: StateFlow<List<MenuWithVariants>> = selectedCategoryId
        .flatMapLatest { id ->
            if (id != null) menuRepository.getMenuWithVariantsByCategoryFlow(id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val searchQuery = MutableStateFlow("")
    val disabledItemsCount = MutableStateFlow(0)
    val menuAddOnsCount = MutableStateFlow(0)

    fun selectCategory(id: Long?) {
        selectedCategoryId.value = id
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun addCategory(name: String, isVeg: Boolean) {
        viewModelScope.launch {
            try {
                categoryRepository.insertCategory(CategoryEntity(name = name, isVeg = isVeg))
            } catch (e: Exception) {
                android.util.Log.e("MenuViewModel", "Error adding category", e)
            }
        }
    }

    fun updateCategory(category: CategoryEntity) {
        viewModelScope.launch {
            categoryRepository.updateCategory(category)
        }
    }

    fun toggleCategory(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            categoryRepository.toggleActive(id, enabled)
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            categoryRepository.deleteCategory(category)
        }
    }

    fun addItem(categoryId: Long, name: String, price: Double, foodType: String, description: String? = null) {
        viewModelScope.launch {
            menuRepository.insertItem(
                MenuItemEntity(
                    categoryId = categoryId,
                    name = name,
                    basePrice = price.toString(),
                    foodType = foodType,
                    description = description,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun updateItem(item: MenuItemEntity) {
        viewModelScope.launch {
            menuRepository.updateItem(item)
        }
    }

    fun toggleItem(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            menuRepository.toggleItemAvailability(id, enabled)
        }
    }

    fun deleteItem(item: MenuItemEntity) {
        viewModelScope.launch {
            menuRepository.deleteItem(item)
        }
    }

    fun clearCategoryItems(categoryId: Long) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val items = menuRepository.getItemsByCategoryOnce(categoryId)
            items.forEach { menuRepository.deleteItem(it) }
        }
    }

    fun addVariant(menuItemId: Long, name: String, price: Double) {
        viewModelScope.launch {
            menuRepository.insertVariant(
                ItemVariantEntity(
                    menuItemId = menuItemId,
                    variantName = name,
                    price = price.toString()
                )
            )
        }
    }

    fun updateVariant(variant: ItemVariantEntity) {
        viewModelScope.launch {
            menuRepository.updateVariant(variant)
        }
    }

    fun deleteVariant(variant: ItemVariantEntity) {
        viewModelScope.launch {
            menuRepository.deleteVariant(variant)
        }
    }

    data class DraftVariant(
        val name: String,
        val price: Double,
        val isSelected: Boolean = true
    )

    data class DraftMenuItem(
        val name: String,
        val price: Double,
        val variants: List<DraftVariant> = emptyList(),
        val isSelected: Boolean = true,
        val foodType: String = "veg",
        val categoryName: String? = null,
        val description: String? = null
    )

    data class OcrImportUiState(
        val configMode: String? = null, 
        val isProcessing: Boolean = false,
        val processingLabel: String = "Processing...",  
        val rawText: String = "",
        val drafts: List<DraftMenuItem> = emptyList(),
        val error: String? = null,
        val successMessage: String? = null  
    )

    private val _ocrImportUiState = MutableStateFlow(OcrImportUiState())
    val ocrImportUiState: StateFlow<OcrImportUiState> = _ocrImportUiState.asStateFlow()

    fun clearDrafts() {
        _ocrImportUiState.update { 
            it.copy(rawText = "", drafts = emptyList(), isProcessing = false, error = null) 
        }
    }

    fun setConfigMode(mode: String?) {
        _ocrImportUiState.update { it.copy(configMode = mode) }
    }

    fun setProcessing(isProcessing: Boolean) {
        _ocrImportUiState.update { it.copy(isProcessing = isProcessing, error = null) }
    }

    fun setError(error: String?) {
        _ocrImportUiState.update { it.copy(error = error, isProcessing = false) }
    }

    fun updateDraft(index: Int, updated: DraftMenuItem) {
        val current = _ocrImportUiState.value.drafts.toMutableList()
        if (index in current.indices) {
            current[index] = updated
            _ocrImportUiState.update { it.copy(drafts = current) }
        }
    }

    fun toggleDraftSelection(index: Int) {
        val current = _ocrImportUiState.value.drafts.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(isSelected = !current[index].isSelected)
            _ocrImportUiState.update { it.copy(drafts = current) }
        }
    }

    fun moveDraftToCategory(index: Int, newCategory: String?) {
        val current = _ocrImportUiState.value.drafts.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(categoryName = newCategory)
            _ocrImportUiState.update { it.copy(drafts = current) }
        }
    }

    fun reorderDrafts(fromIndex: Int, toIndex: Int) {
        val current = _ocrImportUiState.value.drafts.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _ocrImportUiState.update { it.copy(drafts = current) }
        }
    }

    fun toggleDraftFoodType(index: Int) {
        val current = _ocrImportUiState.value.drafts.toMutableList()
        if (index in current.indices) {
            val item = current[index]
            current[index] = item.copy(foodType = if (item.foodType == "veg") "non-veg" else "veg")
            _ocrImportUiState.update { it.copy(drafts = current) }
        }
    }

    fun selectAllDrafts(select: Boolean) {
        _ocrImportUiState.update { state ->
            state.copy(drafts = state.drafts.map { it.copy(isSelected = select) })
        }
    }

    fun processImportFile(context: Context, uri: Uri) {
        val contentResolver = context.contentResolver
        val type = contentResolver.getType(uri)
        
        val fileName = getFileName(context, uri).lowercase()
        val isPdf = type == "application/pdf" || fileName.endsWith(".pdf")
        val isImage = type?.startsWith("image/") == true || 
                     listOf(".jpg", ".jpeg", ".png", ".webp").any { fileName.endsWith(it) }

        if (isPdf) {
            extractTextFromPdf(context, uri)
        } else if (isImage) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val bitmap = uriToBitmap(context, uri)
                    if (bitmap != null) {
                        processMenuImage(context, bitmap)
                    } else {
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            _ocrImportUiState.update { it.copy(isProcessing = false, error = "Could not load image.") }
                        }
                    }
                } catch (e: Exception) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _ocrImportUiState.update { it.copy(isProcessing = false, error = "Error loading image: ${e.message}") }
                    }
                }
            }
        } else {
            _ocrImportUiState.update { it.copy(isProcessing = false, error = "Unsupported file format. Please use PDF or Image.") }
        }
    }

    private fun uriToBitmap(context: Context, uri: Uri): android.graphics.Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            android.graphics.BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = ""
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = it.getString(index)
            }
        }
        return name
    }

    fun extractTextFromPdf(context: Context, uri: Uri) {
        _ocrImportUiState.update { it.copy(isProcessing = true, processingLabel = "Analysing PDF...", error = null) }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val fileName = getFileName(context, uri)
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw Exception("Cannot open PDF file.")
                
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                val pageCount = renderer.pageCount
                val allDrafts = mutableListOf<DraftMenuItem>()
                val seenNames = mutableSetOf<String>()

                Log.d(ocrDebugTag, "extractTextFromPdf start fileName='${fileName}' pageCount=$pageCount")
                
                val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                    com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
                )

                for (i in 0 until pageCount) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _ocrImportUiState.update { it.copy(processingLabel = "Reading page ${i + 1} of $pageCount...") }
                    }

                    val page = renderer.openPage(i)
                    // Render at high resolution for better OCR (e.g., 4.0x scale)
                    val width = (page.width * 4.0).toInt()
                    val height = (page.height * 4.0).toInt()
                    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                    
                    // Fill background with white
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    
                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                    // Process synchronously in IO thread
                    val visionText = com.google.android.gms.tasks.Tasks.await(recognizer.process(image))

                    val rawLen = visionText.text?.length ?: 0
                    val pageDrafts = com.khanabook.lite.pos.domain.util.OcrSpatialParser.parse(visionText)
                    Log.d(
                        ocrDebugTag,
                        "PDF page ${i + 1}/$pageCount recognizedRawLen=$rawLen parsedDrafts=${pageDrafts.size} bitmap=${width}x${height}"
                    )
                    
                    pageDrafts.forEach { draft ->
                        if (seenNames.add(draft.name.lowercase())) {
                            allDrafts.add(draft)
                        }
                    }
                    bitmap.recycle()
                }

                recognizer.close()
                renderer.close()
                pfd.close()

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _ocrImportUiState.update { 
                        it.copy(
                            drafts = allDrafts,
                            isProcessing = false,
                            error = if (allDrafts.isEmpty()) "No items extracted. Please try again." else null
                        )
                    }
                }

                Log.d(ocrDebugTag, "extractTextFromPdf done totalUniqueDrafts=${allDrafts.size}")
            } catch (e: Exception) {
                Log.e("PDF_EXTRACT", "Error processing PDF", e)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _ocrImportUiState.update { it.copy(
                        isProcessing = false,
                        error = "Failed to process PDF: ${e.message?.take(80)}"
                    )}
                }
            }
        }
    }

    fun processMenuImage(context: Context, bitmap: android.graphics.Bitmap) {
        _ocrImportUiState.update { it.copy(isProcessing = true, processingLabel = "Analysing image...", error = null) }
        
        val scaledBitmap = scaleBitmapIfNeeded(bitmap)
        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(scaledBitmap, 0)
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
        )

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                if (visionText.text.isBlank()) {
                    _ocrImportUiState.update { it.copy(
                        isProcessing = false,
                        error = "No text found. Ensure the menu is well-lit and in focus."
                    )}
                } else {
                    processSpatialVisionText(visionText)
                }
            }
            .addOnFailureListener {
                _ocrImportUiState.update { it.copy(
                    isProcessing = false,
                    error = "Recognition failed. Try with better lighting or a clearer photo."
                )}
            }
            .addOnCompleteListener {
                recognizer.close()
                if (scaledBitmap != bitmap) scaledBitmap.recycle()
            }
    }

    private fun processSpatialVisionText(visionText: com.google.mlkit.vision.text.Text) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val drafts = com.khanabook.lite.pos.domain.util.OcrSpatialParser.parse(visionText)
            
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                _ocrImportUiState.update { 
                    it.copy(
                        drafts = drafts,
                        isProcessing = false,
                        rawText = visionText.text,
                        error = if (drafts.isEmpty()) "No items extracted. Please try again or use a clearer photo." else null
                    )
                }
            }
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        val maxDim = 2048
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val scale = maxDim.toFloat() / maxOf(w, h)
        return android.graphics.Bitmap.createScaledBitmap(
            bitmap, (w * scale).toInt(), (h * scale).toInt(), true
        )
    }

    fun checkForConflicts(defaultCategoryId: Long?, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val selectedDrafts = _ocrImportUiState.value.drafts.filter { it.isSelected }
            if (selectedDrafts.isEmpty()) {
                withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(false) }
                return@launch
            }

            val groupedByDetected = selectedDrafts.groupBy { it.categoryName }
            var hasConflict = false

            for ((detectedName, items) in groupedByDetected) {
                val targetCategoryId: Long? = if (detectedName != null) {
                    categoryRepository.getAllCategoriesFlow().first()
                        .find { it.name.equals(detectedName, ignoreCase = true) }?.id
                } else {
                    defaultCategoryId
                }

                if (targetCategoryId != null) {
                    val existingNames = menuRepository.getItemsByCategoryOnce(targetCategoryId)
                        .map { it.name.lowercase() }.toHashSet()
                    
                    if (items.any { it.name.lowercase() in existingNames }) {
                        hasConflict = true
                        break
                    }
                }
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(hasConflict) }
        }
    }

    fun saveImportedMenu(defaultCategoryId: Long?, overwrite: Boolean = false) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _ocrImportUiState.update { it.copy(isProcessing = true, processingLabel = "Saving menu...") }
            
            try {
                val selectedDrafts = _ocrImportUiState.value.drafts.filter { it.isSelected }
                if (selectedDrafts.isEmpty()) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _ocrImportUiState.update { it.copy(isProcessing = false, error = "No items selected to add.") }
                    }
                    return@launch
                }

                // 1. Group items by their detected category names
                val groupedByDetected = selectedDrafts.groupBy { it.categoryName }
                
                var totalAdded = 0
                
                // 2. Process each group
                groupedByDetected.forEach { (detectedName, items) ->
                    // Determine target category ID
                    val targetCategoryId: Long = if (detectedName != null) {
                        // Find or create category by name
                        val existingCat = categoryRepository.getAllCategoriesFlow().first()
                            .find { it.name.equals(detectedName, ignoreCase = true) }
                        
                        existingCat?.id ?: categoryRepository.insertCategory(
                            CategoryEntity(name = detectedName, isVeg = items.all { it.foodType == "veg" })
                        )
                    } else if (defaultCategoryId != null) {
                        defaultCategoryId
                    } else {
                        // No category detected and no default selected? Create a "General" category
                        val existingGeneral = categoryRepository.getAllCategoriesFlow().first()
                            .find { it.name.equals("General", ignoreCase = true) }
                        existingGeneral?.id ?: categoryRepository.insertCategory(CategoryEntity(name = "General", isVeg = true))
                    }

                    if (overwrite) {
                        val existingItems = menuRepository.getItemsByCategoryOnce(targetCategoryId)
                        existingItems.forEach { menuRepository.deleteItem(it) }
                    }

                    val existingAfterClear = if (overwrite) emptyList() else menuRepository.getItemsByCategoryOnce(targetCategoryId)
                    val existingNames = existingAfterClear.map { it.name.lowercase() }.toHashSet()

                    for (draft in items) {
                        if (draft.name.lowercase() !in existingNames) {
                            val itemId = menuRepository.insertItem(
                                MenuItemEntity(
                                    categoryId = targetCategoryId,
                                    name = draft.name,
                                    basePrice = draft.price.toString(),
                                    foodType = draft.foodType,
                                    description = draft.description,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            
                            if (draft.variants.isNotEmpty()) {
                                draft.variants.filter { it.isSelected }.forEach { variant ->
                                    menuRepository.insertVariant(
                                        ItemVariantEntity(
                                            menuItemId = itemId,
                                            variantName = variant.name,
                                            price = variant.price.toString()
                                        )
                                    )
                                }
                            }
                            totalAdded++
                        }
                    }
                }

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _ocrImportUiState.update { it.copy(
                        drafts = emptyList(),
                        rawText = "",
                        isProcessing = false,
                        successMessage = "Successfully added $totalAdded items to your menu!"
                    )}
                }
            } catch (e: Exception) {
                android.util.Log.e("MenuViewModel", "Error saving imported menu", e)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _ocrImportUiState.update { it.copy(
                        isProcessing = false, 
                        error = "Failed to save: ${e.localizedMessage ?: "Unknown error"}"
                    )}
                }
            }
        }
    }

    fun saveDraftsToCategory(categoryId: Long, overwrite: Boolean = false) {
        // Keeping this for backward compatibility if needed elsewhere
        saveImportedMenu(categoryId, overwrite)
    }

    fun clearSuccessMessage() {
        _ocrImportUiState.update { it.copy(successMessage = null) }
    }

    companion object {
        private val genericPriceRegex =
            Regex("""(?:[\u20B9\u20A8]|rs\.?|inr)?\s*((?:\d{1,3}(?:,\d{3})*|\d+)(?:\.\d{1,2})?)\b""", RegexOption.IGNORE_CASE)
            
        private val headerLineRegex = Regex("""(?i)^(full|half|qty|price|s\.\s*no|veg|non.?veg).*$""")
        private val allCapsHeaderRegex = Regex("""^[A-Z\s\-&]{3,}$""")
        
        private val leadingBulletRegex = Regex("""^\s*(?:[-*•]+|\d+[.):])\s*""")
        private val trailingSeparatorRegex = Regex("""[\s\-:|.…]+$""")
        private val trailingCurrencyRegex = Regex("""(?i)(?:[\u20B9\u20A8]|rs\.?|inr)\s*$""")
        
        private val skipLineRegex = Regex("""(?i)^(menu|category|item|price|qty|total|subtotal|s\.no|veg|non.?veg|page\s+\d+)\.?\s*$""")
        
        private const val MAX_PRICE = 99999.0

        internal fun parseDraftsFromText(text: String): List<DraftMenuItem> {
            val seen = mutableSetOf<String>()
            val drafts = mutableListOf<DraftMenuItem>()
            var currentCategory: String? = null
            var currentVariantHeaders = listOf<String>()

            text.lineSequence()
                .map { normalizeImportLine(it) }
                .filter { it.isNotBlank() }
                .forEach { line ->
                    val lower = line.lowercase()
                    if (lower.contains("full") || lower.contains("half") || lower.contains("qty") || lower.contains("price")) {
                        val headers = line.split(Regex("""[\s\-:|.]+""")).filter { it.length > 2 }
                        val hasKeywords = headers.any { h -> h.lowercase() in listOf("full", "half", "qty", "price") }
                        if (hasKeywords) {
                            currentVariantHeaders = headers.map { h -> toTitleCase(h) }
                            return@forEach
                        }
                    }

                    if (allCapsHeaderRegex.matches(line) && !line.any { it.isDigit() }) {
                        if (!skipLineRegex.matches(line)) {
                            currentCategory = toTitleCase(line)
                        }
                        return@forEach
                    }

                    val draft = parseDraftLine(line, currentVariantHeaders)
                    if (draft != null) {
                        val finalDraft = draft.copy(categoryName = currentCategory)
                        if (seen.add(finalDraft.name.lowercase())) {
                            drafts.add(finalDraft)
                        }
                    }
                }
            return drafts
        }

        private fun normalizeImportLine(raw: String): String {
            return raw
                .replace('\u20B9', ' ')  
                .replace('\u20A8', ' ')  
                .replace("Rs.", " Rs ")
                .replace("rs.", " Rs ")
                .replace('\u00A0', ' ')  
                .replace('\u2019', '\'')  
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
        }

        private fun parseDraftLine(line: String, variantHeaders: List<String>): DraftMenuItem? {
            val noBullet = line.replace(leadingBulletRegex, "").trim()
            if (noBullet.isBlank()) return null
            if (skipLineRegex.matches(noBullet)) return null

            val priceMatches = genericPriceRegex.findAll(noBullet).toList()
            
            if (priceMatches.isEmpty()) {
                // If no price found, treat entire line as name with price 0
                val name = toTitleCase(noBullet.replace(trailingSeparatorRegex, ""))
                if (name.length < 2) return null
                return DraftMenuItem(name = name, price = 0.0)
            }

            val firstNamePos = priceMatches.first().range.first
            val lastNamePos = priceMatches.last().range.last
            
            val priceValues = priceMatches.mapNotNull { 
                it.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull() 
            }.filter { it <= MAX_PRICE }
            
            if (priceValues.isEmpty()) return null

            val rawName = if (firstNamePos > noBullet.length / 3) {
                noBullet.substring(0, firstNamePos)
            } else {
                noBullet.substring(lastNamePos + 1)
            }
            .replace(trailingCurrencyRegex, "")
            .replace(trailingSeparatorRegex, "")
            .replace(Regex("""^\s*[-:|.…]+"""), "") 
            .trim()

            val name = toTitleCase(rawName)
            if (name.isBlank() || name.length < 2) return null

            val variants = priceValues.mapIndexed { index, price ->
                val vName = variantHeaders.getOrNull(index) ?: if (priceValues.size > 1) "Variant ${index + 1}" else "Base"
                DraftVariant(vName, price)
            }

            // Automatic Non-Veg detection
            val lowerName = name.lowercase()
            val nonVegKeywords = listOf("chicken", "murg", "mutton", "egg", "anda", "fish", "prawn", "meat", "non-veg", "non veg", "seekh", "kebab", "tikka")
            val isNonVeg = nonVegKeywords.any { lowerName.contains(it) }

            return DraftMenuItem(
                name = name,
                price = priceValues.first(),
                variants = variants,
                foodType = if (isNonVeg) "non-veg" else "veg"
            )
        }

        private fun toTitleCase(s: String): String {
            return s.lowercase()
                .split(Regex("""\s+"""))
                .joinToString(" ") { word ->
                    word.replaceFirstChar { it.uppercase() }
                }
        }
    }
}
