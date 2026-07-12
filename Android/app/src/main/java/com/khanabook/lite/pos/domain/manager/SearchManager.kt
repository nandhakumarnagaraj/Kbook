package com.khanabook.lite.pos.domain.manager


import android.content.Intent
import android.net.Uri
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.repository.BillRepository

class SearchManager(private val billRepository: BillRepository) {

    suspend fun searchByDailyId(displayId: String, date: String): BillWithItems? {
        val billEntity = displayId.toLongOrNull()?.let { id ->
            billRepository.getBillByDailyIntIdAndDate(id, date)
        } ?: billRepository.getBillByDailyIdAndDate(displayId, date)
        
        return billEntity?.let { billRepository.getBillWithItemsById(it.id) }
    }

    /**
     * Looks up a bill by its printed invoice number.
     *
     * New bills carry a GST invoice string such as "26A1-000042" (matched exactly).
     * Legacy bills issued before the new numbering only have a numeric
     * lifetime_order_id (e.g. printed as "INV42"), so we fall back to that when the
     * input is purely numeric.
     */
    suspend fun searchByInvoiceNumber(rawInput: String): BillWithItems? {
        val normalized = rawInput.trim().uppercase()
        if (normalized.isEmpty()) return null

        billRepository.getBillWithItemsByInvoiceNumber(normalized)?.let { return it }

        val legacyId = normalized.removePrefix("INV").filter { it.isDigit() }.toLongOrNull()
        return legacyId?.let { billRepository.getBillWithItemsByLegacyInvoiceNo(it) }
    }

    suspend fun getBillsWithPendingKds(): List<BillWithItems> {
        return billRepository.getBillsWithPendingKds()
    }

    fun buildCallIntent(whatsappNumber: String): Intent {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$whatsappNumber")
        return intent
    }
}


