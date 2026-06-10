@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private enum class MarketplaceFilter { ALL, ZOMATO, SWIGGY }

@Composable
fun MarketplaceOrdersScreen(
    onBack: () -> Unit,
    viewModel: MarketplaceOrdersViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val actionLoading by viewModel.actionLoading.collectAsState()
    val spacing = KhanaBookTheme.spacing
    var selectedFilter by remember { mutableStateOf(MarketplaceFilter.ALL) }

    LaunchedEffect(Unit) {
        viewModel.message.collect { msg ->
            KhanaToast.show(msg, ToastKind.Info)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kbBgGradient)
    ) {
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xFF1E1035), Color(0xFF0F081D))))
                        .statusBarsPadding()
                        .padding(top = 8.dp, bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = "Marketplace Orders",
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                MarketplaceFilterChips(
                    selectedFilter = selectedFilter,
                    onFilterSelected = { selectedFilter = it }
                )

                when (val s = state) {
                    is MarketplaceOrdersState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.kbSecondary)
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
                                tint = KbError,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(spacing.medium))
                            Text(s.message, color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(spacing.medium))
                            OutlinedButton(
                                onClick = { viewModel.loadOrders() },
                                border = BorderStroke(1.dp, MaterialTheme.kbPrimary),
                                shape = KbShape.Small
                            ) {
                                Text("Retry", color = MaterialTheme.kbPrimary)
                            }
                        }
                    }
                    is MarketplaceOrdersState.Success -> {
                        val filteredOrders = s.orders.filter { order ->
                            when (selectedFilter) {
                                MarketplaceFilter.ALL -> true
                                MarketplaceFilter.ZOMATO -> order.platform.equals("ZOMATO", ignoreCase = true)
                                MarketplaceFilter.SWIGGY -> order.platform.equals("SWIGGY", ignoreCase = true)
                            }
                        }
                        if (filteredOrders.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = MaterialTheme.kbTextSecondary.copy(alpha = 0.25f),
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(modifier = Modifier.height(spacing.small))
                                    Text(
                                        "No pending marketplace orders",
                                        color = MaterialTheme.kbTextSecondary.copy(alpha = 0.55f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredOrders, key = { it.id }) { order ->
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

private val ZomatoBrand = Color(0xFFE23744)
private val SwiggyBrand = Color(0xFFFC8019)

private val StatusNew = Color(0xFF0284C7)
private val StatusAccepted = KbSuccess
private val StatusPreparing = KbWarning
private val StatusReady = Color(0xFF9333EA)
private val StatusPickedUp = KbGray500

@Composable
private fun MarketplaceFilterChips(
    selectedFilter: MarketplaceFilter,
    onFilterSelected: (MarketplaceFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            FilterChip(
                selected = selectedFilter == MarketplaceFilter.ALL,
                onClick = { onFilterSelected(MarketplaceFilter.ALL) },
                label = {
                    Text(
                        "All",
                        fontWeight = if (selectedFilter == MarketplaceFilter.ALL) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KbBrandSaffron.copy(alpha = KbOpacity.StatusBg),
                    selectedLabelColor = KbBrandSaffron
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedFilter == MarketplaceFilter.ALL,
                    borderColor = KbBrandSaffron.copy(alpha = KbOpacity.StatusBorder),
                    selectedBorderColor = KbBrandSaffron.copy(alpha = KbOpacity.StatusBorder),
                    disabledBorderColor = Color.Transparent,
                    disabledSelectedBorderColor = Color.Transparent
                ),
                shape = KbShape.Medium
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == MarketplaceFilter.ZOMATO,
                onClick = { onFilterSelected(MarketplaceFilter.ZOMATO) },
                label = {
                    Text(
                        "Zomato",
                        fontWeight = if (selectedFilter == MarketplaceFilter.ZOMATO) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ZomatoBrand.copy(alpha = KbOpacity.StatusBg),
                    selectedLabelColor = ZomatoBrand
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedFilter == MarketplaceFilter.ZOMATO,
                    borderColor = ZomatoBrand.copy(alpha = KbOpacity.StatusBorder),
                    selectedBorderColor = ZomatoBrand.copy(alpha = KbOpacity.StatusBorder),
                    disabledBorderColor = Color.Transparent,
                    disabledSelectedBorderColor = Color.Transparent
                ),
                shape = KbShape.Medium
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == MarketplaceFilter.SWIGGY,
                onClick = { onFilterSelected(MarketplaceFilter.SWIGGY) },
                label = {
                    Text(
                        "Swiggy",
                        fontWeight = if (selectedFilter == MarketplaceFilter.SWIGGY) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SwiggyBrand.copy(alpha = KbOpacity.StatusBg),
                    selectedLabelColor = SwiggyBrand
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedFilter == MarketplaceFilter.SWIGGY,
                    borderColor = SwiggyBrand.copy(alpha = KbOpacity.StatusBorder),
                    selectedBorderColor = SwiggyBrand.copy(alpha = KbOpacity.StatusBorder),
                    disabledBorderColor = Color.Transparent,
                    disabledSelectedBorderColor = Color.Transparent
                ),
                shape = KbShape.Medium
            )
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
        isSwiggy -> SwiggyBrand
        isZomato -> ZomatoBrand
        else -> KbBrandSaffron
    }

    val statusLabel = when (order.orderStatus.lowercase()) {
        "pending" -> "New"
        "accepted" -> "Accepted"
        "preparing" -> "Preparing"
        "ready" -> "Ready"
        "completed" -> "Picked Up"
        else -> order.orderStatus.replace("_", " ")
    }
    val statusColor = when (order.orderStatus.lowercase()) {
        "pending" -> StatusNew
        "accepted" -> StatusAccepted
        "preparing" -> StatusPreparing
        "ready" -> StatusReady
        "completed" -> StatusPickedUp
        "rejected" -> KbError
        else -> MaterialTheme.kbTextSecondary
    }
    val isPending = order.orderStatus.lowercase() == "pending"

    val infiniteTransition = rememberInfiniteTransition(label = "pendingPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isPending) Modifier.border(
                    2.dp,
                    KbBrandSaffron.copy(alpha = pulseAlpha),
                    RoundedCornerShape(18.dp)
                ) else Modifier
            )
    ) {
        KhanaBookGlassCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = !expanded }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(platformColor.copy(alpha = KbOpacity.StatusBg), CircleShape)
                                .border(1.5.dp, platformColor.copy(alpha = KbOpacity.StatusBorder), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isSwiggy) "S" else if (isZomato) "Z" else "M",
                                color = platformColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Column {
                            Text(
                                order.customerName ?: "Guest",
                                color = MaterialTheme.kbTextPrimary,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                order.platformOrderId,
                                color = MaterialTheme.kbTextTertiary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Text(
                        CurrencyUtils.formatPrice(order.totalAmount.toDouble()),
                        color = MaterialTheme.kbTextPrimary,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = statusColor.copy(alpha = KbOpacity.StatusBg),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = KbOpacity.StatusBorder))
                    ) {
                        Text(
                            text = statusLabel.uppercase(),
                            color = statusColor,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Text(
                        formatTimestamp(order.createdAt),
                        color = MaterialTheme.kbTextTertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isPending && !expanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onAccept,
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KbBrandSaffron,
                                contentColor = Color.White
                            ),
                            shape = KbShape.Small,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Accept", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        OutlinedButton(
                            onClick = { showRejectDialog = true },
                            enabled = !isLoading,
                            border = BorderStroke(1.dp, KbError.copy(alpha = KbOpacity.StatusBorder)),
                            shape = KbShape.Small,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Close, null, tint = KbError, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reject", color = KbError, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (expanded) {
                    HorizontalDivider(color = MaterialTheme.kbOutlineSubtle)
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                        order.customerPhone?.takeIf { it.isNotBlank() }?.let {
                            DetailRow("Phone", it)
                        }
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
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = KbBrandSaffron,
                                        contentColor = Color.White
                                    ),
                                    shape = KbShape.Small,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    } else {
                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Accept", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showRejectDialog = true },
                                    enabled = !isLoading,
                                    border = BorderStroke(1.dp, KbError.copy(alpha = KbOpacity.StatusBorder)),
                                    shape = KbShape.Small,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Close, null, tint = KbError, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reject", color = KbError, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            "accepted", "preparing" -> {
                                Button(
                                    onClick = onReady,
                                    enabled = !isLoading,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = KbBrandSaffron,
                                        contentColor = Color.White
                                    ),
                                    shape = KbShape.Small,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    } else {
                                        Icon(Icons.Default.Done, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Mark Ready", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isPending) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(KbBrandSaffron, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "NEW",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
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
                selectedAccent = KbError,
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
                Text("Reject", color = KbError, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    ) {}
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.kbTextSecondary.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
        Text(value, color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
    }
}

private fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}
