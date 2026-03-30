package com.khanabook.lite.pos.domain.util

import android.util.Log
import com.google.mlkit.vision.text.Text
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel.DraftMenuItem
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel.DraftVariant
import java.util.regex.Pattern

data class OcrConfig(
    val priceRegex: Pattern = Pattern.compile("""(?:[\u20B9\u20A8]|rs\.?|inr)?\s*((?:\d{1,3}(?:,\d{3})*|\d+)(?:\.\d{1,2})?)(?:\s*/-)?""", Pattern.CASE_INSENSITIVE),
    val weightRegex: Regex = Regex("""(?i)\d+\s*(g|kg|ml|ltr|pcs|lb|oz)\b"""),
    val noiseKeywords: List<String> = listOf("authentic", "experience", "since", "halal", "fine dining", "multi cuisine", "restaurant", "order online", "phone", "website", "www.", "special item", "menu", "thiruv", "address", "contact", "zomato", "swiggy", "foodpanda", "find us on", "fssai", "gst", "taxes", "scan qr"),
    val variantHeaderKeywords: List<String> = listOf("full", "half", "qty", "price", "size", "large", "medium", "small", "regular", "portion", "plate"),
    val itemKeywords: List<String> = listOf("biriyani", "chicken", "murg", "mutton", "egg", "anda", "fish", "prawn", "meat", "seekh", "kebab", "tikka", "paneer", "mashroom", "veg", "aloo", "gobi", "dal", "roti", "naan", "paratha"),
    val yThresholdRatio: Double = 0.35,
    val typoCorrections: Map<String, String> = mapOf(
        "Tildka" to "Tikka",
        "Tikla" to "Tikka",
        "Mashroom" to "Mushroom",
        "Paneer" to "Paneer",
        "Biryani" to "Biryani",
        "Biriyani" to "Biriyani"
    )
)

object OcrSpatialParser {
    private const val TAG = "OCR_PARSER_DEBUG"

    data class VariantHeader(val name: String, val xCenter: Int)
    data class PriceInfo(val value: Double, val xCenter: Int)

    fun parse(visionText: Text, config: OcrConfig = OcrConfig()): List<DraftMenuItem> {
        val lines = visionText.textBlocks.flatMap { it.lines }
        if (lines.isEmpty()) return emptyList()

        Log.d(TAG, "parse start: lines=${lines.size} blocks=${visionText.textBlocks.size}")

        val rows = groupLinesIntoRows(lines, config.yThresholdRatio)
        
        val drafts = mutableListOf<DraftMenuItem>()
        var currentCategory: String? = null
        var currentHeaders = mutableListOf<VariantHeader>()

        val maxRowsToLog = 25
        var rowIdx = 0

        for (row in rows) {
            try {
                val sortedRow = row.sortedBy { it.boundingBox?.left ?: 0 }
                val rowText = sortedRow.joinToString(" ") { it.text }.trim()
                val lower = rowText.lowercase()

                val priceInfos = findPricesWithCoordinates(sortedRow, config)

                if (rowIdx < maxRowsToLog) {
                    Log.d(TAG, "row[$rowIdx] text='${rowText.take(180)}' currentCategory=$currentCategory priceInfos=${priceInfos.size} headers=${currentHeaders.size}")
                }
                
                if (priceInfos.isEmpty()) {
                    // Try to detect category or variant headers
                    val headersDetected = detectVariantHeaders(sortedRow, config)
                    if (headersDetected.isNotEmpty()) {
                        currentHeaders.clear()
                        currentHeaders.addAll(headersDetected)
                        
                        val categoryFromHeaderLine = extractCategoryFromHeaderLine(sortedRow, config)
                        if (categoryFromHeaderLine != null) {
                            currentCategory = categoryFromHeaderLine
                        }
                        if (rowIdx < maxRowsToLog) {
                            Log.d(TAG, "row[$rowIdx] header detected: headers=${currentHeaders.map { it.name }} categoryNow=$currentCategory")
                        }
                        continue
                    }

                    val detectedCategory = detectCategory(rowText, lower, config)
                    if (detectedCategory != null) {
                        val isItemLike = config.itemKeywords.any { lower.contains(it) }
                        if (!isItemLike || currentCategory == null || rowText == rowText.uppercase()) {
                            currentCategory = detectedCategory
                            val clearing = config.variantHeaderKeywords.none { lower.contains(it) }
                            if (clearing) currentHeaders.clear()
                            if (rowIdx < maxRowsToLog) {
                                Log.d(TAG, "row[$rowIdx] category detected: categoryNow=$currentCategory clearedHeaders=$clearing")
                            }
                        }
                        continue
                    }
                    continue
                }

                val draftItem = extractMenuItem(sortedRow, rowText, priceInfos, currentHeaders, currentCategory, config)
                if (draftItem != null) {
                    if (rowIdx < maxRowsToLog) {
                        Log.d(TAG, "row[$rowIdx] item: name='${draftItem.name.take(120)}' categoryNow=$currentCategory priceInfos=${priceInfos.size} headers=${currentHeaders.size} variantLabels=${draftItem.variants.map { it.name }}")
                    }
                    drafts.add(draftItem)
                } else {
                    // If no prices and no headers, this might be a description for the LAST added item
                    if (rowText.length > 5 && drafts.isNotEmpty()) {
                        val lastItem = drafts.last()
                        if (lastItem.description == null) {
                            drafts[drafts.size - 1] = lastItem.copy(description = rowText)
                            Log.d(TAG, "row[$rowIdx] detected as description for: ${lastItem.name}")
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
            rowIdx++
        }

        Log.d(TAG, "parse end: drafts=${drafts.size} categories=${drafts.mapNotNull { it.categoryName }.distinct()}")
        return drafts
    }

    private fun groupLinesIntoRows(lines: List<Text.Line>, yThresholdRatio: Double): List<List<Text.Line>> {
        val sortedLines = lines.sortedBy { it.boundingBox?.top ?: 0 }
        val rows = mutableListOf<MutableList<Text.Line>>()
        
        for (line in sortedLines) {
            val bounds = line.boundingBox ?: continue
            val yCenter = bounds.centerY()
            val lineHeight = bounds.height()

            val existingRow = rows.find { row ->
                val rowBounds = row.first().boundingBox ?: return@find false
                val overlapTop = maxOf(bounds.top, rowBounds.top)
                val overlapBottom = minOf(bounds.bottom, rowBounds.bottom)
                val overlapHeight = overlapBottom - overlapTop
                
                if (overlapHeight > 0) {
                    val minHeight = minOf(bounds.height(), rowBounds.height())
                    overlapHeight > minHeight * 0.5
                } else {
                    val rowCenter = rowBounds.centerY()
                    Math.abs(yCenter - rowCenter) < (Math.max(lineHeight, rowBounds.height()) * yThresholdRatio)
                }
            }

            if (existingRow != null) existingRow.add(line) else rows.add(mutableListOf(line))
        }
        return rows
    }

    private fun detectVariantHeaders(row: List<Text.Line>, config: OcrConfig): List<VariantHeader> {
        val newHeaders = mutableListOf<VariantHeader>()
        val rowLower = row.joinToString(" ") { it.text }.lowercase()
        
        if (config.variantHeaderKeywords.any { rowLower.contains(it) }) {
            row.forEach { line ->
                val txt = line.text.trim().lowercase()
                if (config.variantHeaderKeywords.any { txt.contains(it) } && txt.length >= 2) {
                    // Strip weight/qty info like "(500g)" from header label for cleaner variant names
                    val finalLabel = line.text.replace(Regex("""\([^)]*\)"""), "").trim()
                    if (finalLabel.length >= 2 && finalLabel.lowercase() !in listOf("item", "price", "description")) {
                        newHeaders.add(VariantHeader(toTitleCase(finalLabel), line.boundingBox?.centerX() ?: 0))
                    }
                }
            }
        }
        return newHeaders
    }

    private fun extractCategoryFromHeaderLine(row: List<Text.Line>, config: OcrConfig): String? {
        val catPart = row.filter { line ->
            val txt = line.text.lowercase()
            config.variantHeaderKeywords.none { txt.contains(it) } && config.noiseKeywords.none { txt.contains(it) } && txt.length > 2
        }.joinToString(" ") { it.text }.trim()
        
        if (catPart.isNotEmpty() && (catPart.uppercase() == catPart || !catPart.any { it.isDigit() })) {
            return toTitleCase(catPart)
        }
        return null
    }

    private fun detectCategory(rowText: String, lower: String, config: OcrConfig): String? {
        val words = rowText.split(Regex("\\s+"))
        
        // Universally safe heuristic: Categories rarely contain these descriptive words
        val descriptionStopWords = listOf("with", "served", "cooked", "in", "and", "fried", "topped", "pieces", "flavor", "spices")
        if (descriptionStopWords.any { lower.contains(it) }) {
            return null // It's a description, skip it
        }

        if (words.size in 1..5 && config.noiseKeywords.none { lower.contains(it) }) {
            if (rowText.length > 2 && (rowText == rowText.uppercase() || !rowText.any { it.isDigit() })) {
                return toTitleCase(rowText)
            }
        }
        return null
    }

    private fun extractMenuItem(
        row: List<Text.Line>, 
        rowText: String, 
        priceInfos: List<PriceInfo>, 
        currentHeaders: List<VariantHeader>,
        currentCategory: String?,
        config: OcrConfig
    ): DraftMenuItem? {
        val firstPriceX = priceInfos.minOf { it.xCenter }
        val nameElements = row.filter { (it.boundingBox?.centerX() ?: 0) < firstPriceX }
        var rawName = nameElements.joinToString(" ") { it.text }.trim()
        
        if (rawName.isEmpty()) {
            val firstPriceIdx = config.priceRegex.matcher(rowText).let { if (it.find()) it.start() else rowText.length }
            rawName = rowText.substring(0, firstPriceIdx).trim()
        }

        val cleanName = rawName
            .replace(Regex("""^\s*(?:[-*•]+|\d+[.):])\s*"""), "")
            .replace(Regex("""[\s\-:|.…]+$"""), "")
            .trim()
        
        // Universally safe: Ignore standard table headers
        if (cleanName.length < 2 || cleanName.lowercase() in listOf("item", "description", "price", "total", "particulars", "amount", "menu", "half plate", "full plate")) {
            return null
        }

        val correctedName = fixTypos(toTitleCase(cleanName), config)

        val variants = priceInfos.mapIndexed { index, price ->
            val closestHeader = currentHeaders.minByOrNull { Math.abs(it.xCenter - price.xCenter) }
            val vLabel = closestHeader?.name ?: when (priceInfos.size) {
                1 -> "Regular"
                2 -> if (index == 0) "Half" else "Full"
                3 -> if (index == 0) "Small" else if (index == 1) "Medium" else "Large"
                else -> "Variant ${index + 1}"
            }
            DraftVariant(vLabel, price.value)
        }

        val lower = rowText.lowercase()
        val foodType = if (config.itemKeywords.filter { it != "veg" }.any { lower.contains(it) }) "non-veg" else "veg"

        return DraftMenuItem(
            name = correctedName,
            price = priceInfos.first().value,
            variants = if (variants.size > 1) variants else emptyList(),
            categoryName = currentCategory,
            foodType = foodType
        )
    }

    private fun findPricesWithCoordinates(row: List<Text.Line>, config: OcrConfig): List<PriceInfo> {
        val prices = mutableListOf<PriceInfo>()
        for (line in row) {
            val txt = line.text.trim()
            if (config.weightRegex.containsMatchIn(txt.lowercase())) continue

            val matcher = config.priceRegex.matcher(txt)
            var matchCount = 0
            while (matcher.find()) matchCount++

            if (matchCount > 1 && line.elements.isNotEmpty()) {
                // SCALABLE FIX: If multiple prices are grouped in one line (common in PDFs), 
                // iterate through the individual words (elements) to get their true X-coordinates.
                for (element in line.elements) {
                    val elTxt = element.text.trim()
                    val elMatcher = config.priceRegex.matcher(elTxt)
                    while (elMatcher.find()) {
                        val valStr = elMatcher.group(1)?.replace(",", "")
                        valStr?.toDoubleOrNull()?.let {
                            if (it in 1.0..100000.0) {
                                prices.add(PriceInfo(it, element.boundingBox?.centerX() ?: line.boundingBox?.centerX() ?: 0))
                            }
                        }
                    }
                }
            } else {
                val matcher2 = config.priceRegex.matcher(txt)
                while (matcher2.find()) {
                    val valStr = matcher2.group(1)?.replace(",", "")
                    valStr?.toDoubleOrNull()?.let { 
                        if (it in 1.0..100000.0) {
                            prices.add(PriceInfo(it, line.boundingBox?.centerX() ?: 0))
                        }
                    }
                }
            }
        }
        // distinctBy prevents duplicate additions if regex overlaps elements
        return prices.distinctBy { "${it.value}_${it.xCenter}" }.sortedBy { it.xCenter }
    }

    private fun fixTypos(name: String, config: OcrConfig): String {
        var result = name
        config.typoCorrections.forEach { (bad, good) ->
            result = result.replace(bad, good, ignoreCase = true)
        }
        return result
    }

    private fun toTitleCase(s: String): String {
        return s.lowercase().split(Regex("""\s+""")).joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
    }
}
