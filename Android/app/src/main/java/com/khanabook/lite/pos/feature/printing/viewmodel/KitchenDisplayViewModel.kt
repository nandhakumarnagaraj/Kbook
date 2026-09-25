package com.khanabook.lite.pos.feature.printing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.billing.data.getInvoiceNumberDisplay
import com.khanabook.lite.pos.feature.printing.data.KotEventDao
import com.khanabook.lite.pos.feature.printing.data.KotEventEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One live kitchen ticket: an unprinted KOT event joined with its bill. */
data class KitchenTicketUiModel(
    val publicToken: String,
    val kotRevision: String,
    val eventType: String,
    val eventTimeMs: Long,
    val billId: Long,
    val orderDisplay: String,
    val invoiceDisplay: String,
    val orderStatus: String,
    val cancelReason: String?,
    val itemSnapshotJson: String
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KitchenDisplayViewModel @Inject constructor(
    private val kotEventDao: KotEventDao,
    private val billRepository: com.khanabook.lite.pos.feature.billing.data.BillRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    /**
     * Unprinted KOT events (kitchen has NOT seen them) joined with bill headers.
     * Room invalidation notifications keep the board current when events arrive,
     * are synced from another terminal, or are acknowledged.
     */
    val pendingTickets: StateFlow<List<KitchenTicketUiModel>> =
        sessionManager.restaurantId
            .flatMapLatest { restaurantId ->
                if (restaurantId <= 0L) {
                    flowOf(emptyList())
                } else {
                    kotEventDao.observeUnprintedEvents().map { events ->
                        loadTickets(events)
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Marks one ticket acknowledged (kitchen has seen it on this screen). */
    fun acknowledgeTicket(ticket: KitchenTicketUiModel) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                kotEventDao.markPrinted(ticket.publicToken, ticket.kotRevision)
            } finally {
                _busy.value = false
            }
        }
    }

    /** Marks every listed ticket acknowledged (Ack All). */
    fun acknowledgeAll(tickets: List<KitchenTicketUiModel>) {
        if (_busy.value || tickets.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            try {
                tickets.forEach { ticket ->
                    kotEventDao.markPrinted(ticket.publicToken, ticket.kotRevision)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun loadTickets(events: List<KotEventEntity>): List<KitchenTicketUiModel> {
        val bills = billRepository.getBillsWithPendingKds()
        if (bills.isEmpty()) return emptyList()
        val billsByToken = bills.mapNotNull { billWithItems ->
            val token = billWithItems.bill.publicToken?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            token to billWithItems.bill
        }.toMap()

        return events.mapNotNull { event ->
            val bill = billsByToken[event.publicToken] ?: return@mapNotNull null
            KitchenTicketUiModel(
                publicToken = event.publicToken,
                kotRevision = event.kotRevision,
                eventType = event.eventType,
                eventTimeMs = event.createdAt,
                billId = bill.id,
                orderDisplay = bill.dailyOrderDisplay,
                invoiceDisplay = bill.getInvoiceNumberDisplay(),
                orderStatus = bill.orderStatus,
                cancelReason = bill.cancelReason.takeIf { it.isNotBlank() },
                itemSnapshotJson = event.itemSnapshotJson
            )
        }.sortedBy { it.eventTimeMs }
    }
}
