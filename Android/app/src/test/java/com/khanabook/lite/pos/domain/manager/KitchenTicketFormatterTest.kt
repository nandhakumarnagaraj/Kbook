package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.BillItemEntity
import com.khanabook.lite.pos.data.local.entity.PrinterProfileEntity
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.domain.model.PrinterRole
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class KitchenTicketFormatterTest {

    @Test
    fun `kitchen ticket contains order items and notes`() {
        val bill = BillWithItems(
            bill = BillEntity(
                id = 1,
                dailyOrderId = 12,
                dailyOrderDisplay = "2026-04-11-12",
                lifetimeOrderId = 120,
                customerName = "Arun",
                subtotal = "180.0",
                totalAmount = "180.0",
                paymentMode = "cash",
                paymentStatus = "paid",
                orderStatus = "completed"
            ),
            items = listOf(
                BillItemEntity(
                    id = 1,
                    billId = 1,
                    menuItemId = 10,
                    itemName = "Chicken Fried Rice",
                    price = "180.0",
                    quantity = 2,
                    itemTotal = "360.0",
                    specialInstruction = "Less spicy"
                )
            ),
            payments = emptyList()
        )
        val restaurant = RestaurantProfileEntity(shopName = "KhanaBook")
        val printer = PrinterProfileEntity(
            role = PrinterRole.KITCHEN.name,
            name = "Kitchen Printer",
            macAddress = "00:11:22:33:44:55"
        )

        val output = String(
            KitchenTicketFormatter.format(bill, restaurant, printer),
            Charset.forName("GBK")
        )

        assertTrue(output.contains("Chicken Fried Rice"))
        assertTrue(output.contains("Less spicy"))
        assertTrue(output.contains("2026-04-11-12"))
    }
}
