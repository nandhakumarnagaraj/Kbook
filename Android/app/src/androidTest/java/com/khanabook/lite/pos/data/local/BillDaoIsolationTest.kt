package com.khanabook.lite.pos.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khanabook.lite.pos.data.local.dao.BillDao
import com.khanabook.lite.pos.data.local.dao.KitchenPrintQueueDao
import com.khanabook.lite.pos.data.local.dao.RestaurantDao
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.BillPaymentEntity
import com.khanabook.lite.pos.data.local.entity.KitchenPrintDispatchStatus
import com.khanabook.lite.pos.data.local.entity.KitchenPrintQueueEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the bill READ path is isolated by restaurantId when two restaurants'
 * rows coexist in one Room DB (the shared-device scenario). See the home dashboard,
 * reports, drafts, online-payment matching, and KDS read paths.
 */
@RunWith(AndroidJUnit4::class)
class BillDaoIsolationTest {

    private lateinit var db: AppDatabase
    private lateinit var billDao: BillDao
    private lateinit var kdsDao: KitchenPrintQueueDao
    private lateinit var restaurantDao: RestaurantDao

    private val R1 = 101L
    private val R2 = 202L
    private val USER_A = 11L
    private val USER_B = 22L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        billDao = db.billDao()
        kdsDao = db.kitchenPrintQueueDao()
        restaurantDao = db.restaurantDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun bill(
        restaurantId: Long,
        createdAt: Long,
        orderStatus: String = "completed",
        paymentStatus: String = "paid",
        paymentMode: String = "cash",
        ownerUserId: Long? = null,
        customerWhatsapp: String? = null,
        createdTerminalId: String? = "A",
        currentOwnerTerminalId: String? = "A",
        deviceId: String = "dev-A",
        recordOrigin: String = "local_created",
        recordScope: String = "terminal_operational",
        orderType: String = "takeaway"
    ) = BillEntity(
        restaurantId = restaurantId,
        dailyOrderId = 1L,
        dailyOrderDisplay = "1",
        lifetimeOrderId = createdAt, // unique-ish per row
        orderType = orderType,
        subtotal = "100.0",
        totalAmount = "100.0",
        paymentMode = paymentMode,
        paymentStatus = paymentStatus,
        orderStatus = orderStatus,
        createdAt = createdAt,
        ownerUserId = ownerUserId,
        customerWhatsapp = customerWhatsapp,
        createdTerminalId = createdTerminalId,
        currentOwnerTerminalId = currentOwnerTerminalId,
        deviceId = deviceId,
        recordOrigin = recordOrigin,
        recordScope = recordScope
    )

    @Test
    fun getBillsByDateRange_returnsOnlyActiveRestaurant() = runBlocking {
        billDao.insertBill(bill(R1, createdAt = 1_000))
        billDao.insertBill(bill(R1, createdAt = 2_000))
        billDao.insertBill(bill(R2, createdAt = 1_500, createdTerminalId = "B", currentOwnerTerminalId = "B"))

        val r1Bills = billDao.getBillsByDateRange(0, 10_000, R1, "A").first()
        assertEquals(2, r1Bills.size)
        assertTrue(r1Bills.all { it.restaurantId == R1 })

        val r2Bills = billDao.getBillsByDateRange(0, 10_000, R2, "B").first()
        assertEquals(1, r2Bills.size)
        assertTrue(r2Bills.all { it.restaurantId == R2 })
    }

    @Test
    fun getLatestPendingOnlineBill_isScopedByRestaurantAndUser() = runBlocking {
        billDao.insertBill(
            bill(R1, createdAt = 1_000, orderStatus = "draft", paymentStatus = "pending",
                paymentMode = "upi", ownerUserId = USER_A)
        )
        billDao.insertBill(
            bill(R2, createdAt = 2_000, orderStatus = "draft", paymentStatus = "pending",
                paymentMode = "upi", ownerUserId = USER_B)
        )

        val forR1UserA = billDao.getLatestPendingOnlineBill(R1, USER_A, "A")
        assertNotNull(forR1UserA)
        assertEquals(R1, forR1UserA!!.restaurantId)

        // Active restaurant R1 must never resolve R2's pending UPI bill, even for a wrong user.
        val forR1UserB = billDao.getLatestPendingOnlineBill(R1, USER_B, "A")
        assertNull(forR1UserB)
    }

    @Test
    fun getRecentBillsWithCustomers_returnsOnlyActiveRestaurant() = runBlocking {
        billDao.insertBill(bill(R1, createdAt = 1_000, customerWhatsapp = "9000000001"))
        billDao.insertBill(bill(R2, createdAt = 2_000, customerWhatsapp = "9000000002"))

        val recents = billDao.getRecentBillsWithCustomers(R1, "A")
        assertEquals(1, recents.size)
        assertEquals("9000000001", recents.first().customerWhatsapp)
    }

    @Test
    fun kds_pendingCountAndBills_areScopedByRestaurant() = runBlocking {
        val b1 = billDao.insertBill(bill(R1, createdAt = 1_000))
        val b2 = billDao.insertBill(bill(R2, createdAt = 2_000))

        kdsDao.upsert(
            KitchenPrintQueueEntity(
                billId = b1, restaurantId = R1, printerMac = "AA:BB",
                dispatchStatus = KitchenPrintDispatchStatus.PENDING
            )
        )
        kdsDao.upsert(
            KitchenPrintQueueEntity(
                billId = b2, restaurantId = R2, printerMac = "CC:DD",
                dispatchStatus = KitchenPrintDispatchStatus.PENDING
            )
        )

        assertEquals(1, kdsDao.getPendingCountFlow(R1).first())
        assertEquals(1, kdsDao.getPendingCountFlow(R2).first())

        val r1Pending = billDao.getBillsWithPendingKds(R1, "A")
        assertEquals(1, r1Pending.size)
        assertEquals(R1, r1Pending.first().restaurantId)
    }

    @Test
    fun stalePendingPushAcknowledgement_doesNotMarkFinalizedBillSynced() = runBlocking {
        val billId = billDao.insertBill(
            bill(
                R1,
                createdAt = 1_000,
                orderStatus = "draft",
                paymentStatus = "pending",
                paymentMode = "part_cash_upi"
            ).copy(
                updatedAt = 1_000,
                isSynced = false,
                syncStatus = "pending"
            )
        )
        val pushedDraft = billDao.getBillById(billId, R1)!!

        billDao.updateBill(
            pushedDraft.copy(
                orderStatus = "completed",
                paymentStatus = "success",
                paidAt = 2_000,
                updatedAt = pushedDraft.updatedAt,
                isSynced = false,
                syncStatus = "pending"
            )
        )

        val staleAckCount = billDao.markBillAsSyncedIfUnchanged(
            billId = billId,
            restaurantId = R1,
            pushedUpdatedAt = pushedDraft.updatedAt,
            pushedOrderStatus = pushedDraft.orderStatus,
            pushedPaymentStatus = pushedDraft.paymentStatus
        )
        val finalized = billDao.getBillById(billId, R1)!!

        assertEquals(0, staleAckCount)
        assertEquals("completed", finalized.orderStatus)
        assertEquals("success", finalized.paymentStatus)
        assertEquals(false, finalized.isSynced)
    }

    @Test
    fun pendingPull_preservesFreshLocalSplitPaymentsAndCompletedState() = runBlocking {
        val publicToken = "split-payment-race"
        val billId = billDao.insertBill(
            bill(
                R1,
                createdAt = 1_000,
                orderStatus = "completed",
                paymentStatus = "success",
                paymentMode = "part_cash_upi"
            ).copy(
                partAmount1 = "40.00",
                partAmount2 = "60.00",
                publicToken = publicToken,
                updatedAt = 2_000,
                isSynced = false,
                syncStatus = "pending"
            )
        )
        billDao.insertBillPayments(
            listOf(
                BillPaymentEntity(
                    billId = billId,
                    restaurantId = R1,
                    deviceId = "dev-A",
                    paymentMode = "cash",
                    amount = "40.00",
                    operationId = "$publicToken:payment:cash"
                ),
                BillPaymentEntity(
                    billId = billId,
                    restaurantId = R1,
                    deviceId = "dev-A",
                    paymentMode = "upi",
                    amount = "60.00",
                    operationId = "$publicToken:payment:upi"
                )
            )
        )

        val pendingServerCopy = billDao.getBillById(billId, R1)!!.copy(
            orderStatus = "draft",
            paymentStatus = "pending",
            paidAt = null,
            updatedAt = 1_000,
            isSynced = true,
            syncStatus = "synced",
            serverId = 500L,
            serverUpdatedAt = 3_000
        )
        billDao.insertSyncedBills(listOf(pendingServerCopy))

        val preservedBill = billDao.getBillById(billId, R1)!!
        val preservedPayments = billDao.getActivePaymentsForBill(billId, R1)

        assertEquals("completed", preservedBill.orderStatus)
        assertEquals("success", preservedBill.paymentStatus)
        assertEquals(false, preservedBill.isSynced)
        assertEquals(500L, preservedBill.serverId)
        assertEquals(2, preservedPayments.size)
        assertEquals(setOf("cash", "upi"), preservedPayments.map { it.paymentMode }.toSet())
    }

    @Test
    fun unchangedPushAcknowledgement_marksBillSynced() = runBlocking {
        val billId = billDao.insertBill(
            bill(R1, createdAt = 1_000).copy(
                updatedAt = 2_000,
                isSynced = false,
                syncStatus = "pending"
            )
        )
        val pushed = billDao.getBillById(billId, R1)!!

        val acknowledged = billDao.markBillAsSyncedIfUnchanged(
            billId = billId,
            restaurantId = R1,
            pushedUpdatedAt = pushed.updatedAt,
            pushedOrderStatus = pushed.orderStatus,
            pushedPaymentStatus = pushed.paymentStatus
        )

        assertEquals(1, acknowledged)
        assertEquals(true, billDao.getBillById(billId, R1)!!.isSynced)
    }

    @Test
    fun staleAcknowledgement_refusesWhenUpdatedAtAdvanced() = runBlocking {
        val billId = billDao.insertBill(
            bill(
                R1,
                createdAt = 1_000,
                orderStatus = "draft",
                paymentStatus = "pending"
            ).copy(updatedAt = 1_000, isSynced = false)
        )
        val pushed = billDao.getBillById(billId, R1)!!
        billDao.updateBill(
            pushed.copy(
                orderStatus = "completed",
                paymentStatus = "success",
                updatedAt = 2_000,
                isSynced = false
            )
        )

        val acknowledged = billDao.markBillAsSyncedIfUnchanged(
            billId = billId,
            restaurantId = R1,
            pushedUpdatedAt = pushed.updatedAt,
            pushedOrderStatus = pushed.orderStatus,
            pushedPaymentStatus = pushed.paymentStatus
        )

        assertEquals(0, acknowledged)
        assertEquals(false, billDao.getBillById(billId, R1)!!.isSynced)
    }

    @Test
    fun stalePulledClientFingerprint_doesNotAcknowledgeNewerLocalBill() = runBlocking {
        val publicToken = "stale-pull-fingerprint"
        val billId = billDao.insertBill(
            bill(
                R1,
                createdAt = 1_000,
                orderStatus = "completed",
                paymentStatus = "success",
                paymentMode = "part_cash_upi"
            ).copy(
                publicToken = publicToken,
                updatedAt = 2_000,
                isSynced = false
            )
        )
        val staleRemote = billDao.getBillById(billId, R1)!!.copy(
            orderStatus = "draft",
            paymentStatus = "pending",
            updatedAt = 1_000,
            serverUpdatedAt = 3_000,
            serverId = 500L,
            isSynced = true
        )

        billDao.insertSyncedBills(listOf(staleRemote))
        val acknowledged = billDao.markBillAsSyncedIfUnchanged(
            billId = billId,
            restaurantId = R1,
            pushedUpdatedAt = staleRemote.updatedAt,
            pushedOrderStatus = staleRemote.orderStatus,
            pushedPaymentStatus = staleRemote.paymentStatus
        )
        val local = billDao.getBillById(billId, R1)!!

        assertEquals(0, acknowledged)
        assertEquals("completed", local.orderStatus)
        assertEquals("success", local.paymentStatus)
        assertEquals(false, local.isSynced)
        assertEquals(3_000L, local.serverUpdatedAt)
    }

    @Test
    fun overwriteNewerRemote_preservesUnsyncedLocalPayments() = runBlocking {
        val billId = billDao.insertBill(
            bill(R1, createdAt = 1_000).copy(
                publicToken = "newer-remote",
                updatedAt = 1_000,
                isSynced = true
            )
        )
        billDao.insertBillPayment(
            BillPaymentEntity(
                billId = billId,
                restaurantId = R1,
                deviceId = "dev-A",
                paymentMode = "cash",
                amount = "100.00",
                operationId = "newer-remote:payment:cash",
                isSynced = false
            )
        )
        val newerRemote = billDao.getBillById(billId, R1)!!.copy(
            customerWhatsapp = "9000000099",
            updatedAt = 2_000,
            serverUpdatedAt = 3_000,
            serverId = 501L,
            isSynced = true
        )

        billDao.insertSyncedBills(listOf(newerRemote))

        assertEquals("9000000099", billDao.getBillById(billId, R1)!!.customerWhatsapp)
        val payments = billDao.getActivePaymentsForBill(billId, R1)
        assertEquals(1, payments.size)
        assertEquals(false, payments.single().isSynced)
    }

    @Test
    fun staleOlderRemote_doesNotOverwriteSyncedLocalBill() = runBlocking {
        val billId = billDao.insertBill(
            bill(
                R1,
                createdAt = 1_000,
                orderStatus = "completed",
                paymentStatus = "success"
            ).copy(
                publicToken = "stale-remote",
                updatedAt = 2_000,
                isSynced = true
            )
        )
        val staleRemote = billDao.getBillById(billId, R1)!!.copy(
            orderStatus = "draft",
            paymentStatus = "pending",
            updatedAt = 1_000,
            serverUpdatedAt = 3_000,
            serverId = 502L,
            isSynced = true
        )

        billDao.insertSyncedBills(listOf(staleRemote))
        val local = billDao.getBillById(billId, R1)!!

        assertEquals("completed", local.orderStatus)
        assertEquals("success", local.paymentStatus)
        assertEquals(2_000L, local.updatedAt)
        assertEquals(502L, local.serverId)
    }

    @Test
    fun duplicateIdentityOverwrite_retiresTokenAndPreservesPayments() = runBlocking {
        val survivorId = billDao.insertBill(
            bill(R1, createdAt = 1_000, deviceId = "dev-A").copy(
                id = 100L,
                publicToken = null,
                updatedAt = 1_000,
                isSynced = true
            )
        )
        val duplicateId = billDao.insertBill(
            bill(R1, createdAt = 1_100, deviceId = "dev-old").copy(
                id = 200L,
                publicToken = "canonical-token",
                serverId = 600L,
                updatedAt = 1_500,
                isSynced = true
            )
        )
        billDao.insertBillPayment(
            BillPaymentEntity(
                billId = duplicateId,
                restaurantId = R1,
                deviceId = "dev-old",
                paymentMode = "upi",
                amount = "100.00",
                operationId = "canonical-token:payment:upi"
            )
        )
        val incoming = bill(R1, createdAt = 1_000, deviceId = "dev-A").copy(
            id = survivorId,
            publicToken = "canonical-token",
            serverId = 600L,
            updatedAt = 2_000,
            serverUpdatedAt = 3_000,
            isSynced = true
        )

        billDao.insertSyncedBills(listOf(incoming))

        val survivor = billDao.getBillById(survivorId, R1)!!
        val duplicate = billDao.getBillById(duplicateId, R1)!!
        assertEquals("canonical-token", survivor.publicToken)
        assertEquals(600L, survivor.serverId)
        assertEquals(true, duplicate.isDeleted)
        assertNull(duplicate.publicToken)
        assertNull(duplicate.serverId)
        assertEquals(1, billDao.getActivePaymentsForBill(survivorId, R1).size)
    }

    @Test
    fun duplicateIdentityPreserve_retiresTokenAndKeepsNewerLocalState() = runBlocking {
        val survivorId = billDao.insertBill(
            bill(
                R1,
                createdAt = 1_000,
                orderStatus = "completed",
                paymentStatus = "success",
                deviceId = "dev-A"
            ).copy(
                id = 300L,
                publicToken = null,
                updatedAt = 3_000,
                isSynced = false
            )
        )
        val duplicateId = billDao.insertBill(
            bill(R1, createdAt = 1_100, deviceId = "dev-old").copy(
                id = 400L,
                publicToken = "preserve-token",
                serverId = 700L,
                updatedAt = 1_500,
                isSynced = true
            )
        )
        val incoming = bill(
            R1,
            createdAt = 1_000,
            orderStatus = "draft",
            paymentStatus = "pending",
            deviceId = "dev-A"
        ).copy(
            id = survivorId,
            publicToken = "preserve-token",
            serverId = 700L,
            updatedAt = 2_000,
            serverUpdatedAt = 4_000,
            isSynced = true
        )

        billDao.insertSyncedBills(listOf(incoming))

        val survivor = billDao.getBillById(survivorId, R1)!!
        val duplicate = billDao.getBillById(duplicateId, R1)!!
        assertEquals("completed", survivor.orderStatus)
        assertEquals("success", survivor.paymentStatus)
        assertEquals(false, survivor.isSynced)
        assertEquals("preserve-token", survivor.publicToken)
        assertEquals(700L, survivor.serverId)
        assertEquals(true, duplicate.isDeleted)
        assertNull(duplicate.publicToken)
    }

    // ── Terminal ownership isolation (record_scope / record_origin) ──────────────
    //
    // A bill created on Terminal A and pulled onto Terminal B during sync must NOT
    // appear in Terminal B's operational lists (active drafts, drafts, pending online,
    // recent orders, KDS). It is read-only restaurant history on B.

    @Test
    fun getActiveDraftBillsFlow_excludesPulledBillFromOtherTerminal() = runBlocking {
        // Local operational draft on Terminal A.
        billDao.insertBill(
            bill(R1, createdAt = 1_000, orderStatus = "draft", paymentStatus = "pending",
                createdTerminalId = "A", currentOwnerTerminalId = "A",
                recordOrigin = "local_created", recordScope = "terminal_operational")
        )
        // Same draft pulled onto Terminal B from the server (Terminal A is the origin).
        billDao.insertBill(
            bill(R1, createdAt = 1_100, orderStatus = "draft", paymentStatus = "pending",
                createdTerminalId = "A", currentOwnerTerminalId = "A",
                deviceId = "dev-A", recordOrigin = "server_imported", recordScope = "restaurant_history")
        )

        val drafts = billDao.getActiveDraftBillsFlow(R1, "A").first()
        assertEquals(1, drafts.size)
        assertEquals("local_created", drafts.first().recordOrigin)
        assertEquals("terminal_operational", drafts.first().recordScope)
    }

    @Test
    fun actionableDrafts_includeLocalInconsistentPaymentButExcludeOtherTerminal() = runBlocking {
        val localBillId = billDao.insertBill(
            bill(
                R1,
                createdAt = 1_000,
                orderStatus = "draft",
                paymentStatus = "pending",
                paymentMode = "upi"
            )
        )
        billDao.insertBillPayment(
            BillPaymentEntity(
                billId = localBillId,
                restaurantId = R1,
                paymentMode = "upi",
                amount = "50.00",
                operationId = "partial"
            )
        )
        billDao.insertBill(
            bill(
                R1,
                createdAt = 1_100,
                orderStatus = "draft",
                paymentStatus = "pending",
                createdTerminalId = "B",
                currentOwnerTerminalId = "B",
                recordOrigin = "server_imported",
                recordScope = "restaurant_history"
            )
        )

        val drafts = billDao.getActionableDraftBillsWithItemsFlow(R1, "A").first()

        assertEquals(1, drafts.size)
        assertEquals(localBillId, drafts.single().bill.id)
        assertEquals(1, drafts.single().payments.size)
    }

    @Test
    fun finalizeOnlineBillAtomically_isIdempotentForSamePaymentSet() = runBlocking {
        val billId = billDao.insertBill(
            bill(
                R1,
                createdAt = 1_000,
                orderStatus = "draft",
                paymentStatus = "pending",
                paymentMode = "part_cash_upi"
            ).copy(operationId = "bill-operation", publicToken = "bill-token")
        )
        val requested = listOf(
            BillPaymentEntity(
                billId = billId,
                paymentMode = "cash",
                amount = "40.00",
                operationId = "bill-operation:payment:cash"
            ),
            BillPaymentEntity(
                billId = billId,
                paymentMode = "upi",
                amount = "60.00",
                operationId = "bill-operation:payment:upi"
            )
        )

        val first = billDao.finalizeOnlineBillAtomically(billId, R1, "A", requested, 2_000)
        val second = billDao.finalizeOnlineBillAtomically(billId, R1, "A", requested, 3_000)

        assertEquals("completed", first.billWithItems.bill.orderStatus)
        assertEquals("success", first.billWithItems.bill.paymentStatus)
        assertEquals(2, first.billWithItems.payments.size)
        assertEquals(
            first.billWithItems.payments.map { it.id }.toSet(),
            second.billWithItems.payments.map { it.id }.toSet()
        )
        assertEquals(
            setOf(
                com.khanabook.lite.pos.data.local.relation.BillFinalizationOutcome.FINALIZED_NOW,
                com.khanabook.lite.pos.data.local.relation.BillFinalizationOutcome.ALREADY_FINALIZED_IDEMPOTENT
            ),
            setOf(first.outcome, second.outcome)
        )
    }

    @Test
    fun resetPaymentRecovery_repairsSyncedLegacyIdentityWithoutDeletingPayment() = runBlocking {
        val billId = billDao.insertBill(
            bill(
                R1,
                createdAt = 1_000,
                orderStatus = "draft",
                paymentStatus = "pending",
                paymentMode = "upi"
            ).copy(operationId = "bill-operation", publicToken = "bill-token")
        )
        billDao.insertBillPayment(
            BillPaymentEntity(
                billId = billId,
                paymentMode = "upi",
                amount = "100.00",
                restaurantId = R1,
                deviceId = "dev-A",
                terminalId = "A",
                billPublicToken = "bill-token",
                operationId = null,
                isSynced = true,
                syncStatus = "synced",
                serverId = 900L,
                gatewayTxnId = "gateway-900",
                gatewayStatus = "success",
                verifiedBy = "gateway"
            )
        )
        val paymentId = billDao.getActivePaymentsForBill(billId, R1).single().id

        billDao.resetUnverifiedPaymentRecoveryAtomically(billId, R1, "A", 2_000)

        val repaired = billDao.getActivePaymentsForBill(billId, R1).single()
        assertEquals(paymentId, repaired.id)
        assertEquals("bill-operation:payment:upi", repaired.operationId)
        assertEquals("100.00", repaired.amount)
        assertEquals("gateway-900", repaired.gatewayTxnId)
        assertEquals("success", repaired.gatewayStatus)
        assertEquals("gateway", repaired.verifiedBy)
        assertEquals(900L, repaired.serverId)
        assertEquals(false, repaired.isSynced)
        assertEquals("pending", repaired.syncStatus)

        val finalized = billDao.finalizeOnlineBillAtomically(
            billId,
            R1,
            "A",
            listOf(repaired),
            3_000
        )
        assertEquals("completed", finalized.billWithItems.bill.orderStatus)
        assertEquals("success", finalized.billWithItems.bill.paymentStatus)
        assertEquals(paymentId, finalized.billWithItems.payments.single().id)
    }

    @Test
    fun resetPaymentRecovery_discardsOnlyUnsyncedManualPayments() = runBlocking {
        val billId = billDao.insertBill(
            bill(
                R1,
                createdAt = 1_000,
                orderStatus = "draft",
                paymentStatus = "pending",
                paymentMode = "upi"
            ).copy(operationId = "bill-operation", publicToken = "bill-token")
        )
        billDao.insertBillPayment(
            BillPaymentEntity(
                billId = billId,
                paymentMode = "upi",
                amount = "100.00",
                restaurantId = R1,
                deviceId = "dev-A",
                terminalId = "A",
                billPublicToken = "bill-token",
                operationId = "bill-operation:payment:upi",
                isSynced = false,
                syncStatus = "pending",
                verifiedBy = "manual"
            )
        )

        billDao.resetUnverifiedPaymentRecoveryAtomically(billId, R1, "A", 2_000)

        assertTrue(billDao.getActivePaymentsForBill(billId, R1).isEmpty())
        val pendingBill = billDao.getOperationalBillById(billId, R1, "A")
        assertEquals("draft", pendingBill?.orderStatus)
        assertEquals("pending", pendingBill?.paymentStatus)
        assertEquals("0.0", pendingBill?.partAmount1)
        assertEquals("0.0", pendingBill?.partAmount2)
    }

    @Test
    fun finalizeOnlineBillAtomically_concurrentCallsOwnTransitionOnce() = runBlocking {
        val billId = billDao.insertBill(
            bill(R1, 1_000, "draft", "pending", "upi")
                .copy(operationId = "concurrent", publicToken = "concurrent-token")
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
                billDao.finalizeOnlineBillAtomically(billId, R1, "A", requested, 2_000)
            },
            async(Dispatchers.IO) {
                billDao.finalizeOnlineBillAtomically(billId, R1, "A", requested, 2_001)
            }
        ).awaitAll()

        assertEquals(1, results.count {
            it.outcome == com.khanabook.lite.pos.data.local.relation.BillFinalizationOutcome.FINALIZED_NOW
        })
        assertEquals(1, results.count {
            it.outcome == com.khanabook.lite.pos.data.local.relation.BillFinalizationOutcome.ALREADY_FINALIZED_IDEMPOTENT
        })
        assertEquals(1, billDao.getActivePaymentsForBill(billId, R1).size)
    }

    @Test
    fun finalizeOnlineBillAtomically_rejectsDuplicateExistingIdentitiesAndModes() = runBlocking {
        val billId = billDao.insertBill(
            bill(R1, 1_000, "draft", "pending", "upi")
                .copy(operationId = "duplicate", publicToken = "duplicate-token")
        )
        // Simulate pre-constraint malformed data: two rows for the same UPI payment.
        // The second uses NULL operation_id (legacy/unidentified) to avoid the new
        // unique constraint while still representing a duplicate-mode scenario.
        billDao.insertBillPayments(
            listOf(
                BillPaymentEntity(
                    billId = billId,
                    restaurantId = R1,
                    paymentMode = "upi",
                    amount = "50.00",
                    operationId = "duplicate:payment:upi"
                ),
                BillPaymentEntity(
                    billId = billId,
                    restaurantId = R1,
                    paymentMode = "upi",
                    amount = "50.00",
                    operationId = null
                )
            )
        )

        val failure = runCatching {
            billDao.finalizeOnlineBillAtomically(
                billId,
                R1,
                "A",
                listOf(
                    BillPaymentEntity(
                        billId = billId,
                        paymentMode = "upi",
                        amount = "100.00",
                        operationId = "duplicate:payment:upi"
                    )
                ),
                2_000
            )
        }

        assertTrue(failure.isFailure)
        assertEquals("draft", billDao.getBillById(billId, R1)?.orderStatus)
        assertEquals("pending", billDao.getBillById(billId, R1)?.paymentStatus)
        assertEquals(2, billDao.getActivePaymentsForBill(billId, R1).size)
        assertEquals(1, billDao.getActionableDraftBillsWithItemsFlow(R1, "A").first().size)
    }

    @Test
    fun restorablePendingOnlineBill_isTerminalAndStateScoped() = runBlocking {
        val validId = billDao.insertBill(
            bill(R1, 1_000, "draft", "pending", "upi")
        )
        val otherTerminalId = billDao.insertBill(
            bill(
                R1,
                1_001,
                "draft",
                "pending",
                "upi",
                createdTerminalId = "B",
                currentOwnerTerminalId = "B"
            )
        )
        val completedId = billDao.insertBill(
            bill(R1, 1_002, "completed", "success", "upi")
        )

        assertNotNull(billDao.getRestorablePendingOnlineBillWithItems(validId, R1, "A"))
        assertNull(billDao.getRestorablePendingOnlineBillWithItems(otherTerminalId, R1, "A"))
        assertNull(billDao.getRestorablePendingOnlineBillWithItems(completedId, R1, "A"))
        assertNull(billDao.getRestorablePendingOnlineBillWithItems(validId, R2, "A"))
    }

    @Test
    fun finalizeOnlineBillAtomically_rejectsDuplicateModeExtraRowAndChangedAmount() = runBlocking {
        var callId = 0
        suspend fun rejected(existing: List<BillPaymentEntity>, requested: List<BillPaymentEntity>) {
            callId++
            val token = "malformed-${System.nanoTime()}"
            val billId = billDao.insertBill(
                bill(R1, System.nanoTime(), "draft", "pending", "part_cash_upi")
                    .copy(operationId = token, publicToken = token)
            )
            // Scope operation_ids per call to avoid cross-call uniqueness collisions
            billDao.insertBillPayments(existing.map { it.copy(billId = billId, restaurantId = R1, operationId = "${it.operationId}:$callId") })
            val result = runCatching {
                billDao.finalizeOnlineBillAtomically(
                    billId,
                    R1,
                    "A",
                    requested.map { it.copy(billId = billId, operationId = "${it.operationId}:$callId") },
                    System.currentTimeMillis()
                )
            }
            assertTrue(result.isFailure)
            assertEquals("draft", billDao.getBillById(billId, R1)?.orderStatus)
            assertEquals(existing.size, billDao.getActivePaymentsForBill(billId, R1).size)
        }

        val cash = BillPaymentEntity(0, 0, "cash", "40.00", operationId = "x:cash")
        val upi = BillPaymentEntity(0, 0, "upi", "60.00", operationId = "x:upi")
        rejected(
            listOf(upi, upi.copy(amount = "40.00", operationId = "x:upi-2")),
            listOf(cash, upi)
        )
        rejected(
            listOf(cash, upi, BillPaymentEntity(0, 0, "pos", "1.00", operationId = "x:pos")),
            listOf(cash, upi)
        )
        rejected(
            listOf(cash.copy(amount = "50.00"), upi.copy(amount = "50.00")),
            listOf(cash, upi)
        )
    }

    @Test(expected = IllegalStateException::class)
    fun finalizeOnlineBillAtomically_rejectsExistingIncompletePaymentSet() = runBlocking {
        val billId = billDao.insertBill(
            bill(
                R1,
                createdAt = 1_000,
                orderStatus = "draft",
                paymentStatus = "pending",
                paymentMode = "part_cash_upi"
            ).copy(operationId = "bill-operation", publicToken = "partial-token")
        )
        billDao.insertBillPayment(
            BillPaymentEntity(
                billId = billId,
                restaurantId = R1,
                paymentMode = "upi",
                amount = "60.00",
                operationId = "stale-operation"
            )
        )
        billDao.finalizeOnlineBillAtomically(
            billId,
            R1,
            "A",
            listOf(
                BillPaymentEntity(
                    billId = billId,
                    paymentMode = "cash",
                    amount = "40.00",
                    operationId = "bill-operation:payment:cash"
                ),
                BillPaymentEntity(
                    billId = billId,
                    paymentMode = "upi",
                    amount = "60.00",
                    operationId = "bill-operation:payment:upi"
                )
            ),
            2_000
        )
        Unit
    }

    @Test
    fun getDraftBills_excludesPulledBillFromOtherTerminal() = runBlocking {
        billDao.insertBill(
            bill(R1, createdAt = 1_000, orderStatus = "draft", paymentStatus = "pending",
                createdTerminalId = "A", currentOwnerTerminalId = "A",
                recordOrigin = "local_created", recordScope = "terminal_operational")
        )
        billDao.insertBill(
            bill(R1, createdAt = 1_100, orderStatus = "draft", paymentStatus = "pending",
                createdTerminalId = "B", currentOwnerTerminalId = "B",
                recordOrigin = "server_imported", recordScope = "restaurant_history")
        )

        val drafts = billDao.getDraftBills(R1, "A").first()
        assertEquals(1, drafts.size)
        assertEquals("A", drafts.first().createdTerminalId)
    }

    @Test
    fun getPendingOnlineBillsFlow_excludesPulledBillFromOtherTerminal() = runBlocking {
        billDao.insertBill(
            bill(R1, createdAt = 1_000, orderStatus = "draft", paymentStatus = "pending",
                paymentMode = "upi", ownerUserId = USER_A,
                createdTerminalId = "A", currentOwnerTerminalId = "A",
                recordOrigin = "local_created", recordScope = "terminal_operational")
        )
        billDao.insertBill(
            bill(R1, createdAt = 1_100, orderStatus = "draft", paymentStatus = "pending",
                paymentMode = "upi", ownerUserId = USER_A,
                createdTerminalId = "B", currentOwnerTerminalId = "B",
                recordOrigin = "server_imported", recordScope = "restaurant_history")
        )

        val pending = billDao.getPendingOnlineBillsFlow(R1, "A").first()
        assertEquals(1, pending.size)
        assertEquals("A", pending.first().createdTerminalId)
    }

    @Test
    fun getRecentBillsWithCustomers_excludesPulledBillFromOtherTerminal() = runBlocking {
        billDao.insertBill(
            bill(R1, createdAt = 1_000, customerWhatsapp = "9000000001",
                createdTerminalId = "A", recordOrigin = "local_created", recordScope = "terminal_operational")
        )
        billDao.insertBill(
            bill(R1, createdAt = 1_100, customerWhatsapp = "9000000002",
                createdTerminalId = "B", recordOrigin = "server_imported", recordScope = "restaurant_history")
        )

        val recents = billDao.getRecentBillsWithCustomers(R1, "A")
        assertEquals(1, recents.size)
        assertEquals("9000000001", recents.first().customerWhatsapp)
    }

    @Test
    fun getBillsByDateRange_report_excludesPulledBillFromOtherTerminal() = runBlocking {
        billDao.insertBill(
            bill(R1, createdAt = 1_000, createdTerminalId = "A",
                recordOrigin = "local_created", recordScope = "terminal_operational")
        )
        billDao.insertBill(
            bill(R1, createdAt = 1_100, createdTerminalId = "B",
                recordOrigin = "server_imported", recordScope = "restaurant_history")
        )

        val report = billDao.getBillsByDateRange(0, 10_000, R1, "A").first()
        assertEquals(1, report.size)
        assertEquals("A", report.first().createdTerminalId)
    }

    @Test
    fun getBillsWithPendingKds_excludesPulledBillFromOtherTerminal() = runBlocking {
        val local = billDao.insertBill(
            bill(R1, createdAt = 1_000, createdTerminalId = "A",
                recordOrigin = "local_created", recordScope = "terminal_operational")
        )
        val pulled = billDao.insertBill(
            bill(R1, createdAt = 1_100, createdTerminalId = "B",
                recordOrigin = "server_imported", recordScope = "restaurant_history")
        )
        kdsDao.upsert(
            KitchenPrintQueueEntity(billId = local, restaurantId = R1, printerMac = "AA:BB",
                dispatchStatus = KitchenPrintDispatchStatus.PENDING)
        )
        kdsDao.upsert(
            KitchenPrintQueueEntity(billId = pulled, restaurantId = R1, printerMac = "BB:CC",
                dispatchStatus = KitchenPrintDispatchStatus.PENDING)
        )

        val pending = billDao.getBillsWithPendingKds(R1, "A")
        assertEquals(1, pending.size)
        assertEquals("A", pending.first().createdTerminalId)
    }

    @Test
    fun repairFailedDailyOrderIdentity_correctsStaleDateWithoutRenumbering() = runBlocking {
        billDao.insertBill(
            bill(R1, createdAt = 1_000).copy(
                dailyOrderId = 1L,
                dailyOrderDisplay = "L-01",
                lastResetDate = "2026-07-23",
                terminalSeries = "L",
                isSynced = true,
                syncStatus = "synced",
                serverId = 305L
            )
        )
        val failedId = billDao.insertBill(
            bill(R1, createdAt = 2_000).copy(
                dailyOrderId = 1L,
                dailyOrderDisplay = "L-01",
                lastResetDate = "2026-07-23",
                terminalSeries = "L",
                isSynced = false,
                syncStatus = "failed_permanent",
                syncFailureReason = "Duplicate order #L-01 already exists for 2026-07-23."
            )
        )

        val repaired = billDao.repairFailedDailyOrderIdentity(
            billId = failedId,
            restaurantId = R1,
            correctedDate = "2026-07-28",
            updatedAt = 3_000
        )

        assertEquals(1L, repaired.dailyOrderId)
        assertEquals("L-01", repaired.dailyOrderDisplay)
        assertEquals("2026-07-28", repaired.lastResetDate)
        assertEquals("completed", repaired.orderStatus)
        assertEquals("pending", repaired.syncStatus)
        assertNull(repaired.syncFailureReason)
    }

    @Test
    fun repairFailedDailyOrderIdentity_renumbersWhenCorrectedIdentityIsOccupied() = runBlocking {
        billDao.insertBill(
            bill(R1, createdAt = 1_000).copy(
                dailyOrderId = 1L,
                dailyOrderDisplay = "L-01",
                lastResetDate = "2026-07-28",
                terminalSeries = "L",
                isSynced = true,
                syncStatus = "synced",
                serverId = 305L
            )
        )
        billDao.insertBill(
            bill(R1, createdAt = 2_000).copy(
                dailyOrderId = 4L,
                dailyOrderDisplay = "L-04",
                lastResetDate = "2026-07-28",
                terminalSeries = "L",
                isSynced = true,
                syncStatus = "synced",
                serverId = 308L
            )
        )
        val failedId = billDao.insertBill(
            bill(R1, createdAt = 3_000).copy(
                dailyOrderId = 1L,
                dailyOrderDisplay = "L-01",
                lastResetDate = "2026-07-23",
                terminalSeries = "L",
                isSynced = false,
                syncStatus = "failed_permanent",
                syncFailureReason = "Duplicate order #L-01 already exists for 2026-07-23."
            )
        )

        val repaired = billDao.repairFailedDailyOrderIdentity(
            billId = failedId,
            restaurantId = R1,
            correctedDate = "2026-07-28",
            updatedAt = 4_000
        )

        assertEquals(5L, repaired.dailyOrderId)
        assertEquals("L-05", repaired.dailyOrderDisplay)
        assertEquals("2026-07-28", repaired.lastResetDate)
        assertEquals("pending", repaired.syncStatus)
    }

    @Test
    fun terminalDailyCounter_startsAfterMaximumForSameServerIdentityDateAndSeries() = runBlocking {
        billDao.insertBill(
            bill(R1, createdAt = 1_000).copy(
                dailyOrderId = 99L,
                dailyOrderDisplay = "L-99",
                lastResetDate = "2026-07-23",
                terminalSeries = "L"
            )
        )
        billDao.insertBill(
            bill(R1, createdAt = 2_000).copy(
                dailyOrderId = 4L,
                dailyOrderDisplay = "L-04",
                lastResetDate = "2026-07-28",
                terminalSeries = "L"
            )
        )
        billDao.insertBill(
            bill(R1, createdAt = 3_000).copy(
                dailyOrderId = 8L,
                dailyOrderDisplay = "M-08",
                lastResetDate = "2026-07-28",
                terminalSeries = "M"
            )
        )

        val allocated = restaurantDao.incrementAndGetTerminalDailyCounter(
            restaurantId = R1,
            terminalId = "terminal-L",
            terminalSeries = "L",
            date = "2026-07-28"
        )

        assertEquals(5L, allocated)
    }
}
