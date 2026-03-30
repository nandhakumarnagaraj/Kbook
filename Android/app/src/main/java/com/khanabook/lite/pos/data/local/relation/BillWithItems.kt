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
)


