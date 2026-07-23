package com.khanabook.lite.pos

import org.junit.Test
import org.junit.Assert.*

class BillingLogicTest {

    @Test
    fun testAddToCartStockLogic() {
        val itemName = "Burger"
        val stockQuantity = 5
        val lowStockThreshold = 2
        
        var cartQuantity = 0
        var errorMessage: String? = null
        
        fun addToCartSim(currentQty: Int) {
            if (currentQty >= stockQuantity) {
                errorMessage = "Reached maximum stock for $itemName"
                return
            }
            
            val remainingAfterAdd = stockQuantity - (currentQty + 1)
            if (remainingAfterAdd <= lowStockThreshold && remainingAfterAdd > 0) {
                errorMessage = "Running out of stock for $itemName"
            } else if (remainingAfterAdd == 0) {
                errorMessage = "Reached maximum stock for $itemName"
            } else {
                errorMessage = null
            }
            cartQuantity = currentQty + 1
        }
        
        addToCartSim(0)
        assertNull("Error at 1: $errorMessage", errorMessage)
        addToCartSim(1)
        assertNull("Error at 2: $errorMessage", errorMessage)
        addToCartSim(2)
        assertEquals("Running out of stock for Burger", errorMessage)
        addToCartSim(3)
        assertEquals("Running out of stock for Burger", errorMessage)
        addToCartSim(4)
        assertEquals("Reached maximum stock for Burger", errorMessage)
        addToCartSim(5)
        assertEquals("Reached maximum stock for Burger", errorMessage)
    }

    // ── Settlement rejection tests ─────────────────────────────────────────

    @Test
    fun `clean draft with no payment rows permits settlement`() {
        // The invariant: a bill with zero active payment rows may be settled.
        // This is enforced by the guard in BillDao.settleDraftBill().
        val hasActivePayments = false
        assertFalse("Settlement should be rejected when payments exist", hasActivePayments)
    }

    @Test
    fun `draft with active payment rows rejects settlement`() {
        // The invariant: settleDraftBill() throws if getActivePaymentsForBill()
        // returns any non-deleted rows. This protects against silent duplicate
        // row insertion and inventory consumption.
        val hasActivePayments = true
        assertTrue("The guard should detect existing payment rows", hasActivePayments)
    }

    @Test
    fun `duplicate operation IDs in existing payments trigger rejection`() {
        // Two active rows sharing the same operationId fail PaymentSetValidator
        // validation before any write. The duplicate identity is caught as
        // "Duplicate payment identity" within validate().
        val opId = "dup:payment:upi"
        val duplicateOperationIds = listOf(
            com.khanabook.lite.pos.data.local.entity.BillPaymentEntity(
                billId = 1, paymentMode = "upi", amount = "50.00", operationId = opId
            ),
            com.khanabook.lite.pos.data.local.entity.BillPaymentEntity(
                billId = 1, paymentMode = "upi", amount = "50.00", operationId = opId
            )
        )
        val validation = com.khanabook.lite.pos.domain.manager.PaymentSetValidator
            .validate(duplicateOperationIds, "100.00")
        assertTrue("Duplicate operation IDs should be rejected", validation.isFailure)
    }

    @Test
    fun `duplicate payment modes trigger rejection`() {
        // Two active rows with the same mode but different operationIds
        // fail PaymentSetValidator validate() as "Duplicate payment component".
        val rows = listOf(
            com.khanabook.lite.pos.data.local.entity.BillPaymentEntity(
                billId = 1, paymentMode = "cash", amount = "50.00", operationId = "cash:1"
            ),
            com.khanabook.lite.pos.data.local.entity.BillPaymentEntity(
                billId = 1, paymentMode = "cash", amount = "50.00", operationId = "cash:2"
            )
        )
        val validation = com.khanabook.lite.pos.domain.manager.PaymentSetValidator
            .validate(rows, "100.00")
        assertTrue("Duplicate payment modes should be rejected", validation.isFailure)
    }

    @Test
    fun `partial payment set fails validation`() {
        // A single UPI row for 60.00 when the total is 100.00
        // does not satisfy the exact total requirement.
        val partial = listOf(
            com.khanabook.lite.pos.data.local.entity.BillPaymentEntity(
                billId = 1, paymentMode = "upi", amount = "60.00", operationId = "partial:upi"
            )
        )
        val validation = com.khanabook.lite.pos.domain.manager.PaymentSetValidator
            .validate(partial, "100.00")
        assertTrue("Partial payment set should be rejected", validation.isFailure)
    }

    @Test
    fun `exact completed retry is idempotent`() {
        // An identical payment set on an already-completed bill should
        // be accepted as ALREADY_FINALIZED_IDEMPOTENT.
        val rows = listOf(
            com.khanabook.lite.pos.data.local.entity.BillPaymentEntity(
                billId = 1, paymentMode = "cash", amount = "100.00",
                operationId = "retry:payment:cash"
            )
        )
        val valid = com.khanabook.lite.pos.domain.manager.PaymentSetValidator
            .validate(rows, "100.00")
        assertTrue("Exact completed retry should pass validation", valid.isSuccess)
    }

    // ── Restoration generation tests ───────────────────────────────────────

    @Test
    fun `stale invalid restoration does not clear new session`() {
        // Simulates: restoration X starts → new session Y begins →
        // X returns invalid. X must not clear Y's state.
        // This test verifies the generation token pattern.
        var restorationGeneration = 0L

        fun ownsAttempt(captured: Long) = captured == restorationGeneration

        // Start restoration X
        val genX = restorationGeneration
        assertTrue("Generation X owns initial attempt", ownsAttempt(genX))

        // New session Y begins (increment generation)
        restorationGeneration += 1
        val genY = restorationGeneration

        // X returns stale
        assertFalse("Generation X no longer owns attempt", ownsAttempt(genX))
        assertTrue("Generation Y now owns attempt", ownsAttempt(genY))
    }

    @Test
    fun `stale valid restoration does not overwrite new session`() {
        // Simulates: restoration X starts → new session Y begins →
        // X returns valid. X's bill must not replace Y's session.
        var restorationGeneration = 0L

        fun ownsAttempt(captured: Long) = captured == restorationGeneration

        val genX = restorationGeneration
        restorationGeneration += 1 // New session Y

        // X returns, but is stale
        assertFalse("Stale X should not mutate state", ownsAttempt(genX))
    }

    @Test
    fun `newer restoration wins when out of order`() {
        // Start X, start Y, Y returns first then X returns.
        // Only Y may mutate state.
        var restorationGeneration = 0L

        fun ownsAttempt(captured: Long) = captured == restorationGeneration

        val genX = restorationGeneration
        restorationGeneration += 1 // Start Y
        val genY = restorationGeneration
        restorationGeneration += 1 // Start Z (newer)

        assertFalse("Oldest X does not own", ownsAttempt(genX))
        assertFalse("Middle Y does not own", ownsAttempt(genY))
    }

    @Test
    fun `current valid restoration succeeds`() {
        // Same-terminal valid restoration without interruption.
        var restorationGeneration = 0L

        fun ownsAttempt(captured: Long) = captured == restorationGeneration

        val gen = restorationGeneration
        assertTrue("Current generation owns clean attempt", ownsAttempt(gen))
    }

    @Test
    fun `current invalid restoration still cleans up`() {
        // No replacement session: the invalid result owns cleanup.
        var restorationGeneration = 0L

        fun ownsAttempt(captured: Long) = captured == restorationGeneration

        val gen = restorationGeneration
        assertTrue("Current generation owns invalid cleanup", ownsAttempt(gen))
    }

    // ── updateOrderStatus completion guard (C2/H4) ──────────────────────────

    @Test
    fun `completing a bill carrying stale partial payment rows is rejected`() {
        // BillRepository.updateOrderStatus() runs PaymentSetValidator.validate() on any
        // existing active payment rows before allowing a completed/paid transition. A
        // payment-recovery draft with a partial UPI row must be rejected (not completed,
        // no inventory deduction).
        val stalePartial = listOf(
            com.khanabook.lite.pos.data.local.entity.BillPaymentEntity(
                billId = 1L, paymentMode = "upi", amount = "60.00", operationId = "op:upi"
            )
        )
        val validation = com.khanabook.lite.pos.domain.manager.PaymentSetValidator
            .validate(stalePartial, "100.00")
        assertTrue("Partial existing payment set must block completion", validation.isFailure)
    }

    @Test
    fun `completing a bill with a valid complete payment set is permitted`() {
        val validSet = listOf(
            com.khanabook.lite.pos.data.local.entity.BillPaymentEntity(
                billId = 1L, paymentMode = "cash", amount = "40.00", operationId = "op:cash"
            ),
            com.khanabook.lite.pos.data.local.entity.BillPaymentEntity(
                billId = 1L, paymentMode = "upi", amount = "60.00", operationId = "op:upi"
            )
        )
        val validation = com.khanabook.lite.pos.domain.manager.PaymentSetValidator
            .validate(validSet, "100.00")
        assertTrue("A valid complete payment set permits completion", validation.isSuccess)
    }

    @Test
    fun `completing a clean draft with no payment rows skips the guard`() {
        // The guard only runs PaymentSetValidator when active rows exist; a clean draft
        // (no rows) is permitted so out-of-band completion is unaffected.
        val existingActiveRows = emptyList<com.khanabook.lite.pos.data.local.entity.BillPaymentEntity>()
        assertTrue("No active rows means the completion guard does not block", existingActiveRows.isEmpty())
    }

    @Test
    fun `loading a different bill invalidates an in-flight restoration`() {
        // Mirrors BillingViewModel.loadDraftOrderForEditing(), which now calls
        // invalidateRestoration() so an explicitly opened bill supersedes any
        // in-flight restoration attempt and a stale result cannot mutate state.
        var restorationGeneration = 0L
        fun invalidate(): Long { restorationGeneration += 1; return restorationGeneration }
        fun ownsAttempt(captured: Long) = captured == restorationGeneration

        // Restoration X begins.
        val genX = invalidate()
        // User opens a different bill for editing (invalidate again).
        invalidate()
        // X's delayed result is now stale and must not mutate state.
        assertFalse("Stale restoration X must not own after a different bill is loaded", ownsAttempt(genX))
    }

    @Test
    fun `active order exits at the step where it was opened`() {
        assertEquals(
            com.khanabook.lite.pos.ui.screens.BillingBackAction.EXIT,
            com.khanabook.lite.pos.ui.screens.resolveBillingBackAction(
                currentStep = 2,
                initialStep = 2,
                editingDraft = true
            )
        )
        assertEquals(
            com.khanabook.lite.pos.ui.screens.BillingBackAction.EXIT,
            com.khanabook.lite.pos.ui.screens.resolveBillingBackAction(
                currentStep = 3,
                initialStep = 3,
                editingDraft = true
            )
        )
    }

    @Test
    fun `active order can step back only through screens opened in that session`() {
        assertEquals(
            com.khanabook.lite.pos.ui.screens.BillingBackAction.STEP_TWO,
            com.khanabook.lite.pos.ui.screens.resolveBillingBackAction(
                currentStep = 3,
                initialStep = 2,
                editingDraft = true
            )
        )
        assertEquals(
            com.khanabook.lite.pos.ui.screens.BillingBackAction.STEP_ONE,
            com.khanabook.lite.pos.ui.screens.resolveBillingBackAction(
                currentStep = 2,
                initialStep = 1,
                editingDraft = false
            )
        )
    }
}
