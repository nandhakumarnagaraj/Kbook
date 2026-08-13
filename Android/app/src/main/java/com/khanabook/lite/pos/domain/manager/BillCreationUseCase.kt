package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.domain.util.AppConstants

import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.BillItemEntity
import com.khanabook.lite.pos.data.local.entity.BillPaymentEntity
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import com.khanabook.lite.pos.domain.model.TerminalIdentity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The intent of a new bill — determines how it's saved (order status, payment status,
 * whether payments are attached, whether invoice is allocated).
 */
sealed class BillIntent {
    /**
     * Instant settlement: bill is created as COMPLETED with payment attached.
     * Used for pay-before-food (cash/POS) and pay-before-food (UPI already collected).
     * Payment entities are built internally using the bill's operationId for consistency.
     */
    data class Settle(
        val paymentMode: String,
        val partAmount1: String = "0.0",
        val partAmount2: String = "0.0"
    ) : BillIntent()

    /**
     * Draft for online payment: bill is saved as DRAFT + PENDING.
     * Used for UPI pay-before-food where the gateway hasn't confirmed yet.
     */
    object DraftForPayment : BillIntent()

    /**
     * Draft for dine-in: bill is saved as DRAFT + PENDING.
     * The customer eats first, pays later. Table name goes into customerName.
     */
    data class DraftForDineIn(val tableName: String) : BillIntent()
}

/**
 * All inputs needed to create a bill. Constructed by the ViewModel, validated here.
 */
data class BillCreationParams(
    val intent: BillIntent,
    val cartItems: List<CartItemSnapshot>,
    val profile: RestaurantProfileEntity,
    val restaurantId: Long,
    val terminalIdentity: TerminalIdentity,
    val customerName: String? = null,
    val customerWhatsapp: String? = null,
    val orderType: String = "dine_in",
    val activeUserId: Long? = null
)

/**
 * Immutable snapshot of a cart item — decoupled from UI CartItem to avoid ViewModel dependency.
 */
data class CartItemSnapshot(
    val menuItemId: Long,
    val itemName: String,
    val variantId: Long? = null,
    val variantName: String? = null,
    val price: String,
    val quantity: Int,
    val note: String = ""
)

/**
 * Result of a successful bill creation.
 */
data class BillCreationResult(
    val billId: Long,
    val billWithItems: BillWithItems?,
    val invoiceNumber: String?,
    val dailyOrderDisplay: String
)

/**
 * Single source of truth for creating bills.
 *
 * This class eliminates the triplicated bill-creation logic that previously existed
 * in BillingViewModel (completeOrder, createDraftOnlineBill, saveDraftOrder).
 * All three paths now pass through here, ensuring:
 * - Consistent field population (30+ fields, populated identically)
 * - Single place to fix bugs (invoice allocation, counter increment, publicToken)
 * - Testable without ViewModel/UI dependencies
 *
 * The ViewModel still owns:
 * - Loading/error state management
 * - Cart state
 * - Navigation decisions
 * - Print dispatch (post-creation)
 * - Sync trigger (post-creation)
 */
@Singleton
class BillCreationUseCase @Inject constructor(
    private val billRepository: BillRepository,
    private val restaurantRepository: RestaurantRepository,
    private val sessionManager: SessionManager
) {
    /**
     * Creates a bill with items and optional payments in a single Room transaction.
     *
     * @throws IllegalStateException if cart is empty or terminal/restaurant not ready
     * @throws IllegalArgumentException if payment validation fails
     */
    suspend fun createBill(params: BillCreationParams): BillCreationResult {
        require(params.cartItems.isNotEmpty()) { "Cart cannot be empty" }
        require(params.restaurantId > 0L) { "Restaurant ID not set" }
        require(params.terminalIdentity.isActive) { "Terminal is not active" }

        val zoneId = ZoneId.of(AppConstants.DEFAULT_TIMEZONE)
        val today = LocalDate.now(zoneId).toString()

        // 1. Allocate daily counter (per-terminal, per-day)
        val dailyCounter = restaurantRepository.incrementAndGetTerminalDailyCounter(
            params.terminalIdentity.terminalId,
            params.terminalIdentity.terminalSeries,
            today
        )
        val terminalSeries = params.terminalIdentity.terminalSeries
        val displayId = OrderIdManager.getDailyOrderDisplay(today, dailyCounter, terminalSeries)

        // 2. Allocate invoice identity
        val createdAt = System.currentTimeMillis()
        val invoice = allocateInvoiceIdentity(createdAt, terminalSeries)

        // 3. Generate unique bill identity
        val publicToken = UUID.randomUUID().toString()
        val operationId = "${params.restaurantId}:${params.terminalIdentity.terminalId}:$publicToken:create_bill"

        // 4. Resolve order/payment status from intent
        val (orderStatus, paymentStatus, paymentMode, paidAt) = resolveStatuses(params)

        // 5. Compute totals
        val summary = computeSummary(params.cartItems, params.profile)

        // 6. Resolve customer name (dine-in draft uses table name)
        val customerName = when (val intent = params.intent) {
            is BillIntent.DraftForDineIn -> intent.tableName.ifBlank { "Table" }
            else -> params.customerName?.ifBlank { null }
        }

        // 7. Build bill entity
        val bill = BillEntity(
            restaurantId = params.restaurantId,
            deviceId = params.terminalIdentity.deviceId,
            terminalId = params.terminalIdentity.terminalId,
            createdTerminalId = params.terminalIdentity.terminalId,
            createdDeviceId = params.terminalIdentity.deviceId,
            currentOwnerTerminalId = params.terminalIdentity.terminalId,
            dailyOrderId = dailyCounter,
            dailyOrderDisplay = displayId,
            lifetimeOrderId = null,
            terminalSeries = terminalSeries,
            financialYear = invoice?.financialYear,
            invoiceSeries = invoice?.invoiceSeries,
            invoiceSequence = invoice?.invoiceSequence,
            invoiceNumber = invoice?.invoiceNumber,
            orderType = if (params.intent is BillIntent.DraftForDineIn) "dine_in" else params.orderType,
            customerName = customerName,
            customerWhatsapp = params.customerWhatsapp?.ifBlank { null },
            subtotal = summary.subtotal,
            gstPercentage = params.profile.gstPercentage.toString(),
            cgstAmount = summary.cgst,
            sgstAmount = summary.sgst,
            customTaxAmount = summary.customTax,
            totalAmount = summary.total,
            paymentMode = paymentMode,
            partAmount1 = when (val intent = params.intent) {
                is BillIntent.Settle -> intent.partAmount1
                else -> "0.0"
            },
            partAmount2 = when (val intent = params.intent) {
                is BillIntent.Settle -> intent.partAmount2
                else -> "0.0"
            },
            paymentStatus = paymentStatus,
            orderStatus = orderStatus,
            cancelReason = "",
            createdBy = params.activeUserId,
            createdAt = createdAt,
            paidAt = paidAt,
            lastResetDate = today,
            publicToken = publicToken,
            ownerUserId = params.activeUserId,
            ownerRestaurantId = params.restaurantId,
            operationId = operationId
        )

        // 8. Build bill items
        val items = params.cartItems.map { cartItem ->
            val itemTotal = BigDecimal(cartItem.price)
                .multiply(BigDecimal.valueOf(cartItem.quantity.toLong()))
                .setScale(2, RoundingMode.HALF_UP)
                .toString()

            BillItemEntity(
                billId = 0, // Set by Room insert
                menuItemId = cartItem.menuItemId,
                itemName = cartItem.itemName,
                variantId = cartItem.variantId,
                variantName = cartItem.variantName,
                price = cartItem.price,
                quantity = cartItem.quantity,
                itemTotal = itemTotal,
                specialInstruction = cartItem.note.ifBlank { null },
                sentToKot = false
            )
        }

        // 9. Resolve payments (only for instant settlement — built here so operationId is consistent)
        val payments: List<BillPaymentEntity> = when (val intent = params.intent) {
            is BillIntent.Settle -> {
                PaymentModeManager.getPaymentComponents(
                    mode = com.khanabook.lite.pos.domain.model.PaymentMode.fromDbValue(intent.paymentMode),
                    totalAmount = summary.total,
                    partAmount1 = intent.partAmount1,
                    partAmount2 = intent.partAmount2
                ).map { component ->
                    BillPaymentEntity(
                        billId = 0,
                        paymentMode = component.mode.dbValue,
                        amount = component.amount,
                        operationId = "$operationId:payment:${component.mode.dbValue}",
                        deviceId = params.terminalIdentity.deviceId,
                        restaurantId = params.restaurantId,
                        verifiedBy = "manual"
                    )
                }
            }
            else -> emptyList()
        }

        // 10. Insert atomically
        val insertedBillId = billRepository.insertFullBill(bill, items, payments, false)
        val billWithItems = billRepository.getBillWithItemsById(insertedBillId)

        return BillCreationResult(
            billId = insertedBillId,
            billWithItems = billWithItems,
            invoiceNumber = invoice?.invoiceNumber,
            dailyOrderDisplay = displayId
        )
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private data class InvoiceIdentity(
        val financialYear: String,
        val invoiceSeries: String,
        val invoiceSequence: Long,
        val invoiceNumber: String
    )

    private suspend fun allocateInvoiceIdentity(createdAt: Long, terminalSeries: String): InvoiceIdentity? {
        val series = terminalSeries.trim().takeIf { it.isNotEmpty() } ?: return null
        val displaySeries = series.first().uppercaseChar().toString()
        val zoneId = ZoneId.of(AppConstants.DEFAULT_TIMEZONE)
        val date = java.time.Instant.ofEpochMilli(createdAt).atZone(zoneId).toLocalDate()
        val financialYearStart = if (date.monthValue >= 4) date.year else date.year - 1
        val financialYear = (financialYearStart % 100).toString().padStart(2, '0')
        val invoiceSeries = "$financialYear$series"
        val sequence = billRepository.getMaxInvoiceSequence(invoiceSeries) + 1L
        return InvoiceIdentity(
            financialYear = financialYear,
            invoiceSeries = invoiceSeries,
            invoiceSequence = sequence,
            invoiceNumber = "$displaySeries${sequence.toString().padStart(2, '0')}"
        )
    }

    private data class Statuses(
        val orderStatus: String,
        val paymentStatus: String,
        val paymentMode: String,
        val paidAt: Long?
    )

    private fun resolveStatuses(params: BillCreationParams): Statuses {
        return when (val intent = params.intent) {
            is BillIntent.Settle -> Statuses(
                orderStatus = "completed",
                paymentStatus = "success",
                paymentMode = intent.paymentMode,
                paidAt = System.currentTimeMillis()
            )
            is BillIntent.DraftForPayment -> Statuses(
                orderStatus = "draft",
                paymentStatus = "pending",
                paymentMode = "upi", // Drafts for payment are always UPI-initiated
                paidAt = null
            )
            is BillIntent.DraftForDineIn -> Statuses(
                orderStatus = "draft",
                paymentStatus = "pending",
                paymentMode = "cash", // Default; changed at settlement
                paidAt = null
            )
        }
    }

    data class BillSummary(
        val subtotal: String = "0.0",
        val cgst: String = "0.0",
        val sgst: String = "0.0",
        val customTax: String = "0.0",
        val total: String = "0.0"
    )

    private fun computeSummary(items: List<CartItemSnapshot>, profile: RestaurantProfileEntity): BillSummary {
        val subtotal = BillCalculator.calculateSubtotal(items.map { it.price to it.quantity })

        var cgst = "0.0"
        var sgst = "0.0"
        var customTax = "0.0"

        if (profile.gstEnabled) {
            val gst = BillCalculator.calculateGST(subtotal, profile.gstPercentage)
            cgst = gst.cgst
            sgst = gst.sgst
        } else if (profile.customTaxPercentage > 0) {
            customTax = BillCalculator.calculateCustomTax(subtotal, profile.customTaxPercentage)
        }

        val total = BillCalculator.calculateTotal(subtotal, cgst, sgst, customTax)
        return BillSummary(subtotal, cgst, sgst, customTax, total)
    }
}

/**
 * Converts a BillingViewModel.CartItem to a CartItemSnapshot for use with BillCreationUseCase.
 * This decouples the use case from the ViewModel's inner types.
 */
fun com.khanabook.lite.pos.ui.viewmodel.BillingViewModel.CartItem.toSnapshot(): CartItemSnapshot =
    CartItemSnapshot(
        menuItemId = item.id,
        itemName = item.name,
        variantId = variant?.id,
        variantName = variant?.variantName,
        price = variant?.price ?: item.basePrice,
        quantity = quantity,
        note = note
    )
