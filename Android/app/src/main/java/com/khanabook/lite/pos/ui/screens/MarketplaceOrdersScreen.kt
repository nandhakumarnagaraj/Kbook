@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.remote.dto.MarketplaceOrderDto
import com.khanabook.lite.pos.data.repository.MarketplaceOrderRepository
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.domain.util.UserMessageSanitizer
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

sealed class MarketplaceOrdersState {
    data object Loading : MarketplaceOrdersState()
    data class Success(val orders: List<MarketplaceOrderDto>) : MarketplaceOrdersState()
    data class Error(val message: String) : MarketplaceOrdersState()
}

@HiltViewModel
class MarketplaceOrdersViewModel @Inject constructor(
    private val repository: MarketplaceOrderRepository
) : ViewModel() {

    private val _state = MutableStateFlow<MarketplaceOrdersState>(MarketplaceOrdersState.Loading)
    val state: StateFlow<MarketplaceOrdersState> = _state.asStateFlow()

    private val _actionLoading = MutableStateFlow<Set<Long>>(emptySet())
    val actionLoading: StateFlow<Set<Long>> = _actionLoading.asStateFlow()

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

    init { loadOrders() }

    fun loadOrders() {
        viewModelScope.launch {
            _state.value = MarketplaceOrdersState.Loading
            try {
                val orders = repository.getPendingOrders()
                _state.value = MarketplaceOrdersState.Success(orders)
            } catch (e: Exception) {
                _state.value = MarketplaceOrdersState.Error(
                    UserMessageSanitizer.sanitize(e, "Failed to load marketplace orders.")
                )
            }
        }
    }

    fun acceptOrder(orderId: Long) {
        viewModelScope.launch {
            _actionLoading.update { it + orderId }
            try {
                repository.acceptOrder(orderId)
                loadOrders()
                _message.emit("Order accepted")
            } catch (e: Exception) {
                _message.emit(UserMessageSanitizer.sanitize(e, "Failed to accept order"))
            } finally {
                _actionLoading.update { it - orderId }
            }
        }
    }

    fun rejectOrder(orderId: Long, reason: String) {
        viewModelScope.launch {
            _actionLoading.update { it + orderId }
            try {
                repository.rejectOrder(orderId, reason)
                loadOrders()
                _message.emit("Order rejected")
            } catch (e: Exception) {
                _message.emit(UserMessageSanitizer.sanitize(e, "Failed to reject order"))
            } finally {
                _actionLoading.update { it - orderId }
            }
        }
    }

    fun markReady(orderId: Long) {
        viewModelScope.launch {
            _actionLoading.update { it + orderId }
            try {
                repository.markReady(orderId)
                loadOrders()
                _message.emit("Order marked ready")
            } catch (e: Exception) {
                _message.emit(UserMessageSanitizer.sanitize(e, "Failed to mark order ready"))
            } finally {
                _actionLoading.update { it - orderId }
            }
        }
    }
}

@Composable
fun MarketplaceOrdersScreen(
    onBack: () -> Unit,
    viewModel: MarketplaceOrdersViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val actionLoading by viewModel.actionLoading.collectAsState()
    val spacing = KhanaBookTheme.spacing

    var headerVisible by remember { mutableStateOf(false) }
    var bodyVisible by remember { mutableStateOf(false) }
    val enterSpec = fadeIn(tween(350)) + slideInVertically(
        initialOffsetY = { it / 6 },
        animationSpec = tween(350, easing = FastOutSlowInEasing)
    )
    val exitSpec = fadeOut(tween(200))

    LaunchedEffect(Unit) {
        headerVisible = true
        kotlinx.coroutines.delay(80)
        bodyVisible = true
    }

    LaunchedEffect(Unit) {
        viewModel.message.collect { msg ->
            KhanaToast.show(msg, ToastKind.Info)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = headerVisible, enter = enterSpec, exit = exitSpec) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.medium),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryGold)
                    }
                    Text(
                        text = "Online Orders",
                        modifier = Modifier.align(Alignment.Center),
                        color = PrimaryGold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }

            AnimatedVisibility(
                visible = bodyVisible,
                enter = enterSpec,
                exit = exitSpec,
                modifier = Modifier.weight(1f)
            ) {
                when (val s = state) {
                    is MarketplaceOrdersState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PrimaryGold)
                        }
                    }
                    is MarketplaceOrdersState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(spacing.medium),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = DangerRed,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(spacing.medium))
                            Text(s.message, color = TextLight, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(spacing.medium))
                            OutlinedButton(
                                onClick = { viewModel.loadOrders() },
                                border = BorderStroke(1.dp, PrimaryGold),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Retry", color = PrimaryGold)
                            }
                        }
                    }
                    is MarketplaceOrdersState.Success -> {
                        if (s.orders.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = TextGold.copy(alpha = 0.25f),
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(modifier = Modifier.height(spacing.small))
                                    Text(
                                        "No pending marketplace orders",
                                        color = TextGold.copy(alpha = 0.55f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = spacing.medium),
                                contentPadding = PaddingValues(vertical = spacing.small),
                                verticalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                items(s.orders, key = { it.id }) { order ->
                                    MarketplaceOrderCard(
                                        order = order,
                                        isLoading = actionLoading.contains(order.id),
                                        onAccept = { viewModel.acceptOrder(order.id) },
                                        onReject = { reason -> viewModel.rejectOrder(order.id, reason) },
                                        onReady = { viewModel.markReady(order.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketplaceOrderCard(
    order: MarketplaceOrderDto,
    isLoading: Boolean,
    onAccept: () -> Unit,
    onReject: (String) -> Unit,
    onReady: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }
    val spacing = KhanaBookTheme.spacing
    val isSwiggy = order.platform.equals("SWIGGY", ignoreCase = true)
    val isZomato = order.platform.equals("ZOMATO", ignoreCase = true)
    val platformColor = when {
        isSwiggy -> SwiggyOrange
        isZomato -> ZomatoRed
        else -> Brown500
    }

    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        color = DarkBrown1.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(platformColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSwiggy) Icons.Default.Restaurant else Icons.Default.Store,
                            contentDescription = order.platform,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            order.platformOrderId,
                            color = PrimaryGold,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            order.platform,
                            color = TextGold.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryGold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    order.customerName?.let {
                        Text(
                            it,
                            color = TextLight,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    order.customerPhone?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = TextGold, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    CurrencyUtils.formatPrice(order.totalAmount.toDouble()),
                    color = TextLight,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OrderStatusBadge(order.orderStatus)
                Text(
                    formatTimestamp(order.createdAt),
                    color = TextGold.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (expanded) {
                HorizontalDivider(color = BorderGold.copy(alpha = 0.2f))
                Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    order.subtotal?.let {
                        DetailRow("Subtotal", CurrencyUtils.formatPrice(it.toDouble()))
                    }
                    order.taxAmount?.let {
                        DetailRow("Tax", CurrencyUtils.formatPrice(it.toDouble()))
                    }
                    DetailRow("Total", CurrencyUtils.formatPrice(order.totalAmount.toDouble()))
                    order.acceptedAt?.let {
                        DetailRow("Accepted", formatTimestamp(it))
                    }
                    order.readyAt?.let {
                        DetailRow("Ready", formatTimestamp(it))
                    }
                    order.rejectedAt?.let {
                        DetailRow("Rejected", formatTimestamp(it))
                    }
                }

                Spacer(modifier = Modifier.height(spacing.small))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    when (order.orderStatus.lowercase()) {
                        "pending" -> {
                            Button(
                                onClick = onAccept,
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Accept", style = MaterialTheme.typography.labelLarge)
                            }
                            OutlinedButton(
                                onClick = { showRejectDialog = true },
                                enabled = !isLoading,
                                border = BorderStroke(1.dp, DangerRed),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Close, null, tint = DangerRed, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reject", color = DangerRed, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        "accepted", "preparing" -> {
                            Button(
                                onClick = onReady,
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = VegGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Done, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Mark Ready", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRejectDialog) {
        RejectOrderDialog(
            onDismiss = { showRejectDialog = false },
            onConfirm = { reason ->
                showRejectDialog = false
                onReject(reason)
            }
        )
    }
}

@Composable
private fun RejectOrderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val presetReasons = listOf("Out of stock", "Closed", "Too far", "Other")
    var selectedReason by remember { mutableStateOf("Out of stock") }
    var customReason by remember { mutableStateOf("") }
    val spacing = KhanaBookTheme.spacing

    KhanaBookSelectionDialog(
        title = "Reject Order",
        onDismissRequest = onDismiss,
        message = "Select a reason:",
        options = presetReasons.map { reason ->
            SelectionDialogOption(
                value = reason,
                title = reason,
                selectedAccent = DangerRed,
                onSelect = {
                    selectedReason = reason
                    if (reason != "Other") customReason = ""
                }
            )
        },
        selectedValue = selectedReason,
        trailingContent = {
            if (selectedReason == "Other") {
                KhanaBookInputField(
                    value = customReason,
                    onValueChange = { customReason = it },
                    label = "Other Reason",
                    placeholder = "Describe the reason...",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
            }
        },
        cancelLabel = "Keep Order",
        actions = {
            TextButton(
                onClick = {
                    val finalReason = if (selectedReason == "Other") customReason.trim() else selectedReason
                    if (finalReason.isNotBlank()) onConfirm(finalReason)
                },
                enabled = selectedReason.isNotBlank() && (selectedReason != "Other" || customReason.isNotBlank())
            ) {
                Text("Reject", color = DangerRed, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    ) {}
}

@Composable
private fun OrderStatusBadge(status: String) {
    val color = when (status.lowercase()) {
        "pending" -> WarningYellow
        "accepted" -> Brown500
        "preparing" -> SwiggyOrange
        "ready" -> VegGreen
        "completed" -> SuccessGreen
        "rejected" -> DangerRed
        else -> TextMuted
    }
    Surface(
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Text(
            text = status.uppercase().replace("_", " "),
            color = color,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextGold.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
        Text(value, color = TextLight, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
    }
}

private fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}
