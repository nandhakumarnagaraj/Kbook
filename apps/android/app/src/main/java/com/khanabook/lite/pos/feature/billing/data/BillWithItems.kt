package com.khanabook.lite.pos.feature.billing.data

import androidx.room.Embedded
import androidx.room.Relation
import com.khanabook.lite.pos.feature.billing.data.BillEntity
import com.khanabook.lite.pos.feature.billing.data.BillItemEntity
import com.khanabook.lite.pos.feature.billing.data.BillPaymentEntity

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
    /**
     * Identity of a receipt line: the menu row and variant when they still exist, plus the name
     * and price so rows that lost their menu id are never merged with a different dish.
     *
     * `bill_items.menu_item_id` is `SET_NULL` on menu-item delete, so grouping on
     * `(menuItemId ?: 0L, variantId)` collapsed every deleted dish in a bill into one line with
     * the first row's name and a summed total (BUG-044). Two genuinely identical rows still share
     * all four components and merge as before.
     */
    private data class LineKey(
        val menuItemId: Long?,
        val variantId: Long?,
        val itemName: String,
        val price: String
    )

    fun getConsolidatedItems(): List<BillItemEntity> {
        return items.groupBy {
            LineKey(
                menuItemId = it.menuItemId,
                variantId = it.variantId,
                itemName = it.itemName.trim().lowercase(),
                price = it.price
            )
        }
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

enum class BillFinalizationOutcome {
    FINALIZED_NOW,
    ALREADY_FINALIZED_IDEMPOTENT,
    CONSTRAINT_RECOVERED_IDEMPOTENT
}

data class BillFinalizationResult(
    val billWithItems: BillWithItems,
    val outcome: BillFinalizationOutcome
)


