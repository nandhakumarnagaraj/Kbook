package com.khanabook.lite.pos.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khanabook.lite.pos.data.local.dao.BillDao
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.BillPaymentEntity
import com.khanabook.lite.pos.data.local.relation.BillFinalizationOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Focused instrumented tests for the constraint-exception recovery path
 * and idempotent finalization in BillDao.finalizeOnlineBillAtomically().
 *
 * These tests simulate concurrent writes by inserting rows directly into the
 * SQLite database (bypassing Room's @Insert layer) to verify the pre-check
 * and recovery logic.
 *
 * The CONSTRAINT_RECOVERED_IDEMPOTENT path fires only in a true race where
 * a concurrent insert happens between the pre-check (getActivePaymentsForBill)
 * and the INSERT (insertBillPayments). Since Room's @Transaction serializes
 * writes in a single-process app, this path is defense-in-depth and cannot
 * be deterministically triggered in a unit test. The tests below verify the
 * behavior when the concurrent insert happens BEFORE the method call: the
 * pre-check detects it and completes the bill via the normal FINALIZED_NOW
 * path (with equivalent set detection).
 *
 * Pre-existing test coverage (in BillDaoIsolationTest.kt):
 *   - Exact concurrent retry (FINALIZED_NOW + ALREADY_FINALIZED_IDEMPOTENT)
 *   - Idempotent same-payment-set retry
 *   - Rejection of duplicate identities, modes, extra rows, changed amounts
 *   - Rejection of existing incomplete payment set
 *   - Concurrent call has exactly one FINALIZED_NOW outcome
 */
@RunWith(AndroidJUnit4::class)
class BillDaoConstraintRecoveryTest {

    private lateinit var db: AppDatabase
    private lateinit var billDao: BillDao

    private val R1 = 101L
    private val TERMINAL_A = "terminal-A"
    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        billDao = db.billDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Helper: creates a draft bill with a known operationId and publicToken.
     * Returns the inserted bill's local ID.
     */
    private fun insertDraftBill(
        restaurantId: Long = R1,
        terminalId: String = TERMINAL_A,
        totalAmount: String = "100.00",
        paymentMode: String = "cash",
        createdAt: Long = System.nanoTime(),
        operationId: String = "test-bill-op-${System.nanoTime()}",
        publicToken: String = "test-token-${System.nanoTime()}"
    ): Long = runBlocking {
        billDao.insertBill(
            BillEntity(
                restaurantId = restaurantId,
                dailyOrderId = 1L,
                dailyOrderDisplay = "1",
                lifetimeOrderId = createdAt,
                orderType = "takeaway",
                subtotal = totalAmount,
                totalAmount = totalAmount,
                paymentMode = paymentMode,
                paymentStatus = "pending",
                orderStatus = "draft",
                createdAt = createdAt,
                createdTerminalId = terminalId,
                currentOwnerTerminalId = terminalId,
                deviceId = "dev-$terminalId",
                recordOrigin = "local_created",
                recordScope = "terminal_operational",
                operationId = operationId,
                publicToken = publicToken
            )
        )
    }

    // ── Test 1: Concurrent exact retry produces one payment row ────────────
    //
    // Two callers race to finalize the same bill with the same payment set.
    // Exactly one payment row survives. One caller gets FINALIZED_NOW,
    // the other gets ALREADY_FINALIZED_IDEMPOTENT (not CONSTRAINT_RECOVERED
    // because Room's @Transaction serializes the writes).

    @Test
    fun concurrentExactRetry_producesOnePaymentRow() = runBlocking {
        val billId = insertDraftBill(
            totalAmount = "100.00",
            paymentMode = "upi"
        )
        val requested = listOf(
            BillPaymentEntity(
                billId = billId,
                paymentMode = "upi",
                amount = "100.00",
                operationId = "concurrent:payment:upi"
            )
        )

        val results = listOf(
            async(Dispatchers.IO) {
                billDao.finalizeOnlineBillAtomically(billId, R1, TERMINAL_A, requested, 2_000)
            },
            async(Dispatchers.IO) {
                billDao.finalizeOnlineBillAtomically(billId, R1, TERMINAL_A, requested, 2_001)
            }
        ).awaitAll()

        // Exactly one payment row exists
        assertEquals(1, billDao.getActivePaymentsForBill(billId, R1).size)

        // One caller finalized, the other found it already done
        val outcomes = results.map { it.outcome }.toSet()
        assertTrue(
            "Must contain FINALIZED_NOW: was $outcomes",
            outcomes.contains(BillFinalizationOutcome.FINALIZED_NOW)
        )

        // Bill is completed
        assertEquals("completed", results.first().billWithItems.bill.orderStatus)
        assertEquals("success", results.first().billWithItems.bill.paymentStatus)
    }

    // ── Test 2: Raw SQL insert on same opId → pre-check catches it → completed ──
    //
    // Manually insert a payment via raw execSQL on the same bill with the same
    // operation_id. Then call finalize with the same payment. The pre-check
    // (getActivePaymentsForBill) finds the existing row and since it matches
    // the requested set, completes the bill. Returns FINALIZED_NOW.

    @Test
    fun preCheckDetectsExistingPayment_completesBillCorrectly() = runBlocking {
        val billId = insertDraftBill(
            totalAmount = "100.00",
            paymentMode = "cash"
        )
        val opId = "precheck:payment:cash"

        // Direct SQL insert bypassing Room's @Insert layer.
        db.openHelper.writableDatabase.execSQL(
            """INSERT INTO bill_payments
               (bill_id, restaurant_id, payment_mode, amount, operation_id,
                is_deleted, created_at, updated_at, sync_status, verified_by)
               VALUES (?, ?, 'cash', '100.00', ?, 0, 1000, 1000, 'pending', 'manual')""",
            arrayOf(billId, R1, opId)
        )

        val requested = listOf(
            BillPaymentEntity(
                billId = billId,
                paymentMode = "cash",
                amount = "100.00",
                operationId = opId
            )
        )

        val result = billDao.finalizeOnlineBillAtomically(billId, R1, TERMINAL_A, requested, 2_000)
        assertEquals("completed", result.billWithItems.bill.orderStatus)
        assertEquals("success", result.billWithItems.bill.paymentStatus)

        // Exactly one payment row
        val payments = billDao.getActivePaymentsForBill(billId, R1)
        assertEquals(1, payments.size)
        assertEquals(opId, payments.first().operationId)
    }

    // ── Test 3: Partial set from raw SQL → does not match → rejected ──────
    //
    // Insert only ONE of two split-payment components via raw SQL,
    // then call finalize with the full set. The existing set (cash only)
    // does NOT match the requested set (cash + UPI) → rejected.

    @Test
    fun simulatedConstraintWithPartialSet_isRejectedAndRolledBack() = runBlocking {
        val billId = insertDraftBill(
            totalAmount = "100.00",
            paymentMode = "part_cash_upi"
        )
        val cashOpId = "split:payment:cash"
        val upiOpId = "split:payment:upi"

        db.openHelper.writableDatabase.execSQL(
            """INSERT INTO bill_payments
               (bill_id, restaurant_id, payment_mode, amount, operation_id,
                is_deleted, created_at, updated_at, sync_status, verified_by)
               VALUES (?, ?, 'cash', '40.00', ?, 0, 1000, 1000, 'pending', 'manual')""",
            arrayOf(billId, R1, cashOpId)
        )

        val requested = listOf(
            BillPaymentEntity(
                billId = billId,
                paymentMode = "cash",
                amount = "40.00",
                operationId = cashOpId
            ),
            BillPaymentEntity(
                billId = billId,
                paymentMode = "upi",
                amount = "60.00",
                operationId = upiOpId
            )
        )

        val result = runCatching {
            billDao.finalizeOnlineBillAtomically(billId, R1, TERMINAL_A, requested, 2_000)
        }

        assertTrue("Partial payment set from concurrent write must be rejected", result.isFailure)

        // Bill must remain draft
        val bill = billDao.getBillById(billId, R1)
        assertEquals("draft", bill?.orderStatus)
        assertEquals("pending", bill?.paymentStatus)

        // Only the cash row remains (UPI was never inserted)
        assertEquals(1, billDao.getActivePaymentsForBill(billId, R1).size)
    }

    // ── Test 4: Existing complete equivalent set recovers idempotently ────

    @Test
    fun existingCompleteEquivalentSet_recoversIdempotently() = runBlocking {
        val billId = insertDraftBill(
            totalAmount = "100.00",
            paymentMode = "part_cash_upi"
        )
        val cashOpId = "complete:payment:cash"
        val upiOpId = "complete:payment:upi"

        billDao.insertBillPayments(
            listOf(
                BillPaymentEntity(
                    billId = billId, restaurantId = R1,
                    paymentMode = "cash", amount = "40.00", operationId = cashOpId
                ),
                BillPaymentEntity(
                    billId = billId, restaurantId = R1,
                    paymentMode = "upi", amount = "60.00", operationId = upiOpId
                )
            )
        )

        val requested = listOf(
            BillPaymentEntity(billId = billId, paymentMode = "cash", amount = "40.00", operationId = cashOpId),
            BillPaymentEntity(billId = billId, paymentMode = "upi", amount = "60.00", operationId = upiOpId)
        )

        val result = billDao.finalizeOnlineBillAtomically(billId, R1, TERMINAL_A, requested, 2_000)
        assertEquals("completed", result.billWithItems.bill.orderStatus)
        assertEquals("success", result.billWithItems.bill.paymentStatus)
        assertEquals(2, billDao.getActivePaymentsForBill(billId, R1).size)
    }

    // ── Test 5: Existing incorrect amount → rejected → rollback ──────────

    @Test
    fun existingConflictingAmount_isRejectedAndRolledBack() = runBlocking {
        val billId = insertDraftBill(totalAmount = "100.00", paymentMode = "cash")
        val opId = "conflict:payment:cash"

        billDao.insertBillPayment(
            BillPaymentEntity(
                billId = billId, restaurantId = R1,
                paymentMode = "cash", amount = "50.00", operationId = opId
            )
        )

        val requested = listOf(
            BillPaymentEntity(billId = billId, paymentMode = "cash", amount = "100.00", operationId = opId)
        )

        val result = runCatching {
            billDao.finalizeOnlineBillAtomically(billId, R1, TERMINAL_A, requested, 2_000)
        }
        assertTrue("Conflicting amount must be rejected", result.isFailure)

        val bill = billDao.getBillById(billId, R1)
        assertEquals("draft", bill?.orderStatus)
        assertEquals("pending", bill?.paymentStatus)
    }

    // ── Test 6: Existing conflicting mode → rejected → rollback ──────────

    @Test
    fun existingConflictingMode_isRejectedAndRolledBack() = runBlocking {
        val billId = insertDraftBill(totalAmount = "100.00", paymentMode = "part_cash_upi")
        val upiOpId = "mode-conflict:upi"

        billDao.insertBillPayment(
            BillPaymentEntity(
                billId = billId, restaurantId = R1,
                paymentMode = "upi", amount = "100.00", operationId = upiOpId
            )
        )

        val requested = listOf(
            BillPaymentEntity(billId = billId, paymentMode = "cash", amount = "100.00", operationId = "mode-conflict:cash")
        )

        val result = runCatching {
            billDao.finalizeOnlineBillAtomically(billId, R1, TERMINAL_A, requested, 2_000)
        }
        assertTrue("Conflicting mode must be rejected", result.isFailure)

        val bill = billDao.getBillById(billId, R1)
        assertEquals("draft", bill?.orderStatus)
        assertEquals("pending", bill?.paymentStatus)
    }

    // ── Test 7: Different gateway transaction → rejected ─────────────────

    @Test
    fun existingMixedWithStaleGateway_isRejectedAndRolledBack() = runBlocking {
        val billId = insertDraftBill(totalAmount = "100.00", paymentMode = "part_cash_upi")
        val cashOpId = "stale-gateway:cash"
        val upiOpId = "stale-gateway:upi"

        billDao.insertBillPayments(
            listOf(
                BillPaymentEntity(billId = billId, restaurantId = R1, paymentMode = "cash", amount = "40.00", operationId = cashOpId, verifiedBy = "manual"),
                BillPaymentEntity(billId = billId, restaurantId = R1, paymentMode = "upi", amount = "60.00", operationId = upiOpId, verifiedBy = "gateway", gatewayTxnId = "OLD_TXN_123", gatewayStatus = "SUCCESS")
            )
        )

        val requested = listOf(
            BillPaymentEntity(billId = billId, paymentMode = "cash", amount = "40.00", operationId = cashOpId, verifiedBy = "manual"),
            BillPaymentEntity(billId = billId, paymentMode = "upi", amount = "60.00", operationId = upiOpId, verifiedBy = "gateway", gatewayTxnId = "NEW_TXN_456", gatewayStatus = "SUCCESS")
        )

        val result = runCatching {
            billDao.finalizeOnlineBillAtomically(billId, R1, TERMINAL_A, requested, 2_000)
        }
        assertTrue("Changed gateway transaction ID must be rejected", result.isFailure)

        val bill = billDao.getBillById(billId, R1)
        assertEquals("draft", bill?.orderStatus)
        assertEquals("pending", bill?.paymentStatus)
    }

    // ── Test 8: Existing equivalent payment → complete → isSynced=false ───

    @Test
    fun existingPaymentCompletion_doesNotMarkSynced() = runBlocking {
        val billId = insertDraftBill(totalAmount = "100.00", paymentMode = "cash")
        val opId = "nosync:payment:cash"

        db.openHelper.writableDatabase.execSQL(
            """INSERT INTO bill_payments
               (bill_id, restaurant_id, payment_mode, amount, operation_id,
                is_deleted, created_at, updated_at, sync_status, verified_by)
               VALUES (?, ?, 'cash', '100.00', ?, 0, 1000, 1000, 'pending', 'manual')""",
            arrayOf(billId, R1, opId)
        )

        val requested = listOf(
            BillPaymentEntity(billId = billId, paymentMode = "cash", amount = "100.00", operationId = opId)
        )

        billDao.finalizeOnlineBillAtomically(billId, R1, TERMINAL_A, requested, 2_000)

        // Bill is completed but NOT synced
        val bill = billDao.getBillById(billId, R1)
        assertFalse("Completed bill must not be marked synced", bill?.isSynced ?: true)
        assertEquals("pending", bill?.syncStatus)
        assertEquals("completed", bill?.orderStatus)
        assertEquals("success", bill?.paymentStatus)
        assertEquals(1, billDao.getActivePaymentsForBill(billId, R1).size)

        // Payment must also be unsynced
        assertFalse("Completed payment must not be marked synced",
            billDao.getActivePaymentsForBill(billId, R1).first().isSynced)
    }

    // ── Test 9: Pre-check catch + FINALIZED_NOW outcome ───────────────────

    @Test
    fun preCheckCatch_returnsFinalizedNow() = runBlocking {
        val billId = insertDraftBill(totalAmount = "100.00", paymentMode = "cash")
        val opId = "outcome:payment:cash"

        db.openHelper.writableDatabase.execSQL(
            """INSERT INTO bill_payments
               (bill_id, restaurant_id, payment_mode, amount, operation_id,
                is_deleted, created_at, updated_at, sync_status, verified_by)
               VALUES (?, ?, 'cash', '100.00', ?, 0, 1000, 1000, 'pending', 'manual')""",
            arrayOf(billId, R1, opId)
        )

        val requested = listOf(
            BillPaymentEntity(billId = billId, paymentMode = "cash", amount = "100.00", operationId = opId)
        )

        val result = billDao.finalizeOnlineBillAtomically(billId, R1, TERMINAL_A, requested, 2_000)

        // When payment exists BEFORE the method call, pre-check catches it → FINALIZED_NOW
        assertEquals(
            "Outcome must be FINALIZED_NOW when pre-check detects existing equivalent payment: was ${result.outcome}",
            BillFinalizationOutcome.FINALIZED_NOW,
            result.outcome
        )

        val reloaded = billDao.getBillWithItemsById(billId, R1)
        assertEquals("completed", reloaded?.bill?.orderStatus)
        assertEquals("success", reloaded?.bill?.paymentStatus)
        assertEquals(1, reloaded?.payments?.size)
    }
}
