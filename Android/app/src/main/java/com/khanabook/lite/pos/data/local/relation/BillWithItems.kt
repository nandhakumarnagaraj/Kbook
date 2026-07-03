package com.khanabook.lite.pos.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.BillItemEntity
import com.khanabook.lite.pos.data.local.entity.BillPaymentEntity

data class BillWithItems(
    @Embedded val bill: BillEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "bill_id"
    )
    val items: List<BillItemEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "bill_id"
    )
    val payments: List<BillPaymentEntity>
) {
    fun getConsolidatedItems(): List<BillItemEntity> {
        return items.groupBy { (it.menuItemId ?: 0L) to it.variantId }
            .map { (_, groupItems) ->
                if (groupItems.size == 1) {
                    groupItems.first()
                } else {
                    val first = groupItems.first()
                    val totalQty = groupItems.sumOf { it.quantity }
                    val totalAmount = groupItems.fold(java.math.BigDecimal.ZERO) { acc, item ->
                        acc.add(java.math.BigDecimal(item.itemTotal))
                    }.setScale(2, java.math.RoundingMode.HALF_UP).toString()
                    first.copy(
                        quantity = totalQty,
                        itemTotal = totalAmount
                    )
                }
            }
    }
}


