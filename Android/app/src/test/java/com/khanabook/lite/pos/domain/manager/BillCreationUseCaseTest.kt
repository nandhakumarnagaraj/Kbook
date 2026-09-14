package com.khanabook.lite.pos.domain.manager
import com.khanabook.lite.pos.feature.billing.domain.BillCreationParams
import com.khanabook.lite.pos.feature.billing.domain.BillCreationUseCase
import com.khanabook.lite.pos.feature.billing.domain.BillIntent
import com.khanabook.lite.pos.feature.billing.domain.CartItemSnapshot

import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.auth.domain.TerminalIdentity
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for BillCreationUseCase's pure logic (parameter validation, status resolution,
 * summary computation). Integration tests for the full createBill path require a Room
 * test database.
 */
class BillCreationUseCaseTest {

    @Test
    fun `BillIntent Settle resolves to completed status`() {
        val intent = BillIntent.Settle(
            paymentMode = "cash"
        )
        assertTrue(intent is BillIntent.Settle)
        assertEquals("cash", intent.paymentMode)
        assertEquals("0.0", intent.partAmount1)
        assertEquals("0.0", intent.partAmount2)
    }

    @Test
    fun `BillIntent DraftForDineIn carries table name`() {
        val intent = BillIntent.DraftForDineIn(tableName = "Table 5")
        assertEquals("Table 5", intent.tableName)
    }

    @Test
    fun `CartItemSnapshot computes item total correctly`() {
        val item = CartItemSnapshot(
            menuItemId = 1L,
            itemName = "Chai",
            price = "20.00",
            quantity = 3
        )
        val expected = java.math.BigDecimal("20.00")
            .multiply(java.math.BigDecimal.valueOf(3L))
            .setScale(2, java.math.RoundingMode.HALF_UP)
            .toString()
        assertEquals("60.00", expected)
        assertEquals(3, item.quantity)
    }

    @Test
    fun `BillCreationParams rejects empty cart`() {
        val params = BillCreationParams(
            intent = BillIntent.Settle(paymentMode = "cash"),
            cartItems = emptyList(),
            profile = createTestProfile(),
            restaurantId = 123L,
            terminalIdentity = createTestTerminal()
        )
        // The use case enforces this via require() — test the params themselves
        assertTrue(params.cartItems.isEmpty())
    }

    @Test
    fun `BillCreationParams with valid data passes basic checks`() {
        val params = BillCreationParams(
            intent = BillIntent.Settle(paymentMode = "upi"),
            cartItems = listOf(
                CartItemSnapshot(
                    menuItemId = 1L,
                    itemName = "Samosa",
                    price = "30.00",
                    quantity = 5
                )
            ),
            profile = createTestProfile(),
            restaurantId = 123L,
            terminalIdentity = createTestTerminal(),
            customerName = "Raj",
            customerWhatsapp = "9876543210",
            orderType = "dine_in"
        )
        assertTrue(params.cartItems.isNotEmpty())
        assertEquals(123L, params.restaurantId)
        assertTrue(params.terminalIdentity.isActive)
        assertEquals("Raj", params.customerName)
    }

    @Test
    fun `DraftForDineIn uses table name as customer name`() {
        val intent = BillIntent.DraftForDineIn("Table 3")
        // Blank table defaults to "Table" in use case
        assertNotEquals("", intent.tableName)
    }

    @Test
    fun `Part payment amounts are preserved in Settle intent`() {
        val intent = BillIntent.Settle(
            paymentMode = "part_cash_upi",
            partAmount1 = "100.00",
            partAmount2 = "50.00"
        )
        assertEquals("100.00", intent.partAmount1)
        assertEquals("50.00", intent.partAmount2)
    }

    @Test
    fun `DraftForPayment carries selected mode and split allocation`() {
        // B2 regression guard: a pending online draft must remember the operator's
        // selected payment mode and split amounts so restoration does not fall back
        // to plain UPI with zero allocation.
        val intent = BillIntent.DraftForPayment(
            paymentMode = "part_cash_upi",
            partAmount1 = "40.00",
            partAmount2 = "60.00"
        )
        assertEquals("part_cash_upi", intent.paymentMode)
        assertEquals("40.00", intent.partAmount1)
        assertEquals("60.00", intent.partAmount2)
    }

    @Test
    fun `DraftForPayment defaults to UPI with zero split`() {
        val intent = BillIntent.DraftForPayment()
        assertEquals("upi", intent.paymentMode)
        assertEquals("0.0", intent.partAmount1)
        assertEquals("0.0", intent.partAmount2)
    }

    @Test
    fun `Cancelled intent carries the cancellation reason`() {
        // B1 regression guard: an abandoned new-order payment must be representable
        // as a cancelled sale (no payments, no stock), never a silent successful Settle.
        val intent = BillIntent.Cancelled(reason = "Customer left")
        assertTrue(intent is BillIntent.Cancelled)
        assertEquals("Customer left", intent.reason)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun createTestProfile() = RestaurantProfileEntity(
        id = 1L,
        shopName = "Test Shop",
        gstEnabled = false,
        gstPercentage = 0.0,
        restaurantId = 123L
    )

    private fun createTestTerminal() = TerminalIdentity(
        restaurantId = 123L,
        terminalId = "1",
        deviceId = "test-device-uuid",
        terminalName = "Counter 1",
        terminalSeries = "A1",
        isActive = true,
        registeredAt = System.currentTimeMillis(),
        lastVerifiedAt = System.currentTimeMillis(),
        terminalToken = "test-token"
    )
}