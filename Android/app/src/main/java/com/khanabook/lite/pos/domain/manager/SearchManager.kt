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

    suspend fun searchByLifetimeId(id: Long): BillWithItems? {
        return billRepository.getBillWithItemsByLifetimeId(id)
    }

    fun buildCallIntent(whatsappNumber: String): Intent {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$whatsappNumber")
        return intent
    }
}


