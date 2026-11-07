@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.feature.reports.ui
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.core.theme.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.model.OrderStatus
import com.khanabook.lite.pos.domain.model.OrderType
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.core.util.CurrencyUtils
import com.khanabook.lite.pos.core.util.DateUtils
import com.khanabook.lite.pos.feature.billing.ui.HeaderCell
import com.khanabook.lite.pos.feature.billing.ui.TableCell
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.feature.settings.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) OrdersGold else Color.Transparent,
        animationSpec = tween(200),
        label = "chip_container"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) OrdersBg else OrdersTextPrimary,
        animationSpec = tween(200),
        label = "chip_content"
    )
    val typeScale = KhanaBookTheme.typeScale
    val chipMinHeight = when (typeScale) {
        TypeScaleTier.Tablet -> 44.dp
        TypeScaleTier.LargePhone -> 40.dp
        else -> 36.dp
    }
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = chipMinHeight),
        shape = KhanaRadii.md,
        color = containerColor,
        border = if (isSelected) null else BorderStroke(1.dp, OrdersGold.copy(alpha = 0.45f)),
        contentColor = contentColor
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = KhanaBookTheme.spacing.smallMedium, vertical = KhanaBookTheme.spacing.small)
        ) {
            Text(
                text = label,
                style = if (isSelected) MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * Date Filter Tab Bar (Today / Yesterday / Custom) for Orders Screen.
 */
@Composable
fun OrderDateFilterComponent(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    canViewFullReports: Boolean = true,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    val availableFilters = if (canViewFullReports) {
        listOf("Today", "Yesterday", "Custom")
    } else {
        listOf("Today")
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        availableFilters.forEach { filter ->
            FilterChip(
                label = filter,
                isSelected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Idiomatic Compose alias for OrderDateFilterComponent */
@Composable
fun OrderDateFilterTabs(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    canViewFullReports: Boolean = true,
    modifier: Modifier = Modifier
) = OrderDateFilterComponent(selectedFilter, onFilterSelected, canViewFullReports, modifier)

@Composable
fun ReportTypeToggle(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = KhanaBookTheme.spacing
    val typeScale = KhanaBookTheme.typeScale
    val toggleMinHeight = when (typeScale) {
        TypeScaleTier.Tablet -> 52.dp
        TypeScaleTier.LargePhone -> 48.dp
        else -> 44.dp
    }
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = toggleMinHeight),
        shape = KhanaRadii.md,
        color = if (isSelected) BrownSelected.copy(alpha = 0.8f) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, PrimaryGold) else BorderStroke(1.dp, BorderGold.copy(alpha = 0.3f)),
        contentColor = if (isSelected) PrimaryGold else TextGold.copy(alpha = 0.7f)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = spacing.smallMedium, vertical = spacing.small)
        ) {
            Text(
                text = label,
                style = if (isSelected) MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PaymentLevelView(
    breakdown: Map<String, String>,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val profile by settingsViewModel.profile.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing
    val enabledModes = profile?.let { com.khanabook.lite.pos.feature.payments.domain.PaymentModeManager.getEnabledModes(it) } ?: listOf(PaymentMode.CASH)
    
    val mainModes = enabledModes.filter { !com.khanabook.lite.pos.feature.payments.domain.PaymentModeManager.isPartPayment(it) }
    val partModes = enabledModes.filter { com.khanabook.lite.pos.feature.payments.domain.PaymentModeManager.isPartPayment(it) }

    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .verticalScrollbar(listState)
            .padding(horizontal = spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        contentPadding = PaddingValues(bottom = spacing.medium)
    ) {
        items(mainModes) { mode ->
            PaymentModeItem(
                mode = mode,
                amount = breakdown[mode.displayLabel]?.toDoubleOrNull() ?: 0.0
            )
        }

        if (partModes.isNotEmpty()) {
            item {
                Text(
                    "Part-Payment (already counted in the modes above)",
                    color = TextGold,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = spacing.small, bottom = spacing.extraSmall)
                )
            }

            // One part-payment card per full row — same rhythm as the
            // Cash/UPI/POS cards above. The card shows the split per side rather than a
            // combined total: each half is already included in its mode's row above, so a
            // combined figure here read as extra revenue and double-counted the bill.
            items(partModes) { mode ->
                val labels = com.khanabook.lite.pos.feature.payments.domain.PaymentModeManager
                    .getPartLabels(mode)
                PartPaymentCard(
                    label = mode.displayLabel,
                    mode = mode,
                    firstLabel = labels.first,
                    firstAmount = breakdown["${mode.displayLabel}_part1"]?.toDoubleOrNull() ?: 0.0,
                    secondLabel = labels.second,
                    secondAmount = breakdown["${mode.displayLabel}_part2"]?.toDoubleOrNull() ?: 0.0,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        item { Spacer(modifier = Modifier.height(spacing.small)) }
    }
}

@Composable
fun ReportDownloadBottomBar(
    onDownloadClick: () -> Unit,
    isExporting: Boolean = false,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = spacing.small, bottom = spacing.medium)
    ) {
        KhanaPrimaryButton(
            text = if (isExporting) "Exporting..." else "Download Reports",
            onClick = onDownloadClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium),
            enabled = !isExporting,
            isLoading = isExporting,
            leadingIcon = Icons.Default.Download,
            height = spacing.buttonHeightLarge
        )
    }
}

@Composable
fun PaymentModeItem(mode: PaymentMode, amount: Double) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val modeColor = getPayModeIconTint(mode)
    val modeIcon = getPayModeIcon(mode)
    // Single flat box styled like Settings cards: warm CardBG container,
    // gold-tinted icon circle, gold chevron. No elevation/border stack.
    KhanaBookCard(
        modifier = Modifier.fillMaxWidth(),
        shape = KhanaRadii.lg,
        colors = CardDefaults.cardColors(containerColor = CardBG),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(spacing.medium)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(modeColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modeIcon,
                    contentDescription = null,
                    tint = modeColor,
                    modifier = Modifier.size(iconSize.medium)
                )
            }
            Spacer(modifier = Modifier.width(spacing.medium))
            Text(mode.displayLabel, color = TextLight, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(CurrencyUtils.formatPrice(amount), color = PrimaryGold, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun PartPaymentCard(
    label: String,
    mode: PaymentMode,
    firstLabel: String,
    firstAmount: Double,
    secondLabel: String,
    secondAmount: Double,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val modeColor = getPayModeIconTint(mode)
    // Single flat box styled like Settings cards: warm CardBG container,
    // mode-tinted icon, no elevation/border stack.
    KhanaBookCard(
        modifier = modifier,
        shape = KhanaRadii.lg,
        colors = CardDefaults.cardColors(containerColor = CardBG),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        // Same rhythm as PaymentModeItem: icon circle, label, gold total, chevron.
        Column(modifier = Modifier.padding(spacing.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(modeColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        getPayModeIcon(mode),
                        contentDescription = null,
                        tint = modeColor,
                        modifier = Modifier.size(iconSize.medium)
                    )
                }
                Spacer(modifier = Modifier.width(spacing.medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, color = TextLight, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$firstLabel ${CurrencyUtils.formatPrice(firstAmount)}  ·  " +
                            "$secondLabel ${CurrencyUtils.formatPrice(secondAmount)}",
                        color = TextGold,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

private val COL_ORDER   = 1.15f
private val COL_AMOUNT  = 1.05f
private val COL_TIME    = 1.05f
private val COL_ACTION  = 0.75f

/** Time-of-day for the Orders table "Time" column. */
private fun formatOrderRowTime(row: com.khanabook.lite.pos.feature.reports.domain.OrderLevelRow): String {
    if (row.createdAt > 0L) {
        return java.time.Instant.ofEpochMilli(row.createdAt)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.US))
    }
    // Legacy rows: `date` already carries "dd MMM yyyy, hh:mm a"
    return row.date.substringAfter(", ", row.date)
}

/**
 * Amount as the Orders table shows it: no space between the currency symbol and the
 * number ("\u20b9470.00"). Scoped to this table so receipts, PDFs and invoices keep
 * the shared CurrencyUtils spacing.
 */
private fun formatOrderRowAmount(amount: String?): String =
    com.khanabook.lite.pos.core.util.CurrencyUtils.formatPrice(amount)
        .replace("\u20B9 ", "\u20B9")

@Composable
fun RowScope.OrderTableHeaderCell(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.Center,
    paddingStart: androidx.compose.ui.unit.Dp = 0.dp,
    paddingEnd: androidx.compose.ui.unit.Dp = 0.dp
) {
    val layout = KhanaBookTheme.layout
    Text(
        text = text,
        modifier = Modifier
            .weight(weight)
            .padding(start = paddingStart, end = paddingEnd),
        color = TextGold,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = layout.tableHeaderFontSize,
            fontWeight = FontWeight.Bold
        ),
        textAlign = align,
        maxLines = 1
    )
}

/**
 * Table Header for Orders Data Table: OrderNo, Amount, Time, Action.
 */
@Composable
fun OrderTableHeader(modifier: Modifier = Modifier) {
    val spacing = KhanaBookTheme.spacing
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium),
        shape = KhanaRadii.md,
        color = OrdersCard.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, OrdersDivider.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.small, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OrderTableHeaderCell("OrderNo", COL_ORDER, align = TextAlign.Start, paddingStart = spacing.extraSmall)
            OrderTableHeaderCell("Amount", COL_AMOUNT, align = TextAlign.Center)
            OrderTableHeaderCell("Time", COL_TIME, align = TextAlign.Center)
            OrderTableHeaderCell("Action", COL_ACTION, align = TextAlign.Center)
        }
    }
}

/**
 * 36dp circular gold Eye icon action button.
 */
@Composable
fun ViewOrderAction(
    dailyId: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = OrdersGold.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, OrdersGold.copy(alpha = 0.55f)),
        shape = CircleShape,
        modifier = modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Visibility,
                contentDescription = "View order $dailyId",
                tint = OrdersGold,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Individual row in the Orders Data Table.
 */
@Composable
fun OrderTableRow(
    row: com.khanabook.lite.pos.feature.reports.domain.OrderLevelRow,
    profile: RestaurantProfileEntity?,
    enabledModes: List<PaymentMode> = emptyList(),
    compactLayout: Boolean = false,
    onStatusChange: (String) -> Unit = {},
    onPayModeChange: (PaymentMode) -> Unit = {},
    onRequestCancel: () -> Unit = {},
    onViewDetails: (Long) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val isCancelled = row.orderStatus == OrderStatus.CANCELLED

    KhanaBookCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isCancelled) DangerRed.copy(alpha = 0.35f) else OrdersDivider.copy(alpha = 0.7f),
                KhanaRadii.md
            )
            .clickable { onViewDetails(row.billId) },
        colors = CardDefaults.cardColors(containerColor = OrdersCard),
        shape = KhanaRadii.md
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.small, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. OrderNo (Left-aligned) + Secondary details
            Column(
                modifier = Modifier
                    .weight(COL_ORDER)
                    .padding(start = spacing.extraSmall)
            ) {
                Text(
                    text = if (row.dailyId.startsWith("#")) row.dailyId else "#${row.dailyId}",
                    color = if (isCancelled) OrdersTextSecondary.copy(alpha = 0.5f) else OrdersTextPrimary,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val detailsLabel = buildString {
                    if (row.orderType.isNotBlank()) {
                        val type = when (row.orderType.lowercase()) {
                            com.khanabook.lite.pos.domain.model.OrderType.DINE_IN -> "Dine-in"
                            com.khanabook.lite.pos.domain.model.OrderType.TAKEAWAY -> "Takeaway"
                            else -> row.orderType.replaceFirstChar { it.uppercase() }
                        }
                        append(type)
                    }
                    if (row.paymentMode.displayLabel.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(row.paymentMode.displayLabel)
                    }
                }
                if (detailsLabel.isNotBlank()) {
                    Text(
                        text = detailsLabel,
                        color = OrdersTextSecondary.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 2. Amount (Center-aligned, tabular nums, strikethrough if cancelled)
            Text(
                text = formatOrderRowAmount(row.totalAmount),
                modifier = Modifier.weight(COL_AMOUNT),
                color = if (isCancelled) OrdersTextSecondary.copy(alpha = 0.5f) else OrdersBrightGold,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                textDecoration = if (isCancelled) TextDecoration.LineThrough else TextDecoration.None,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            // 3. Time (Center-aligned)
            Text(
                text = formatOrderRowTime(row),
                modifier = Modifier.weight(COL_TIME),
                color = if (isCancelled) OrdersTextSecondary.copy(alpha = 0.5f) else OrdersTextSecondary,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFeatureSettings = "tnum"
                ),
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            // 4. Action (View Eye icon button - Circular 36dp)
            Box(modifier = Modifier.weight(COL_ACTION), contentAlignment = Alignment.Center) {
                ViewOrderAction(
                    dailyId = row.dailyId,
                    onClick = { onViewDetails(row.billId) }
                )
            }
        }
    }
}

/** Backward-compatibility alias for OrderTableRow */
@Composable
fun OrderRowItem(
    row: com.khanabook.lite.pos.feature.reports.domain.OrderLevelRow,
    profile: RestaurantProfileEntity?,
    enabledModes: List<PaymentMode> = emptyList(),
    compactLayout: Boolean = false,
    onStatusChange: (String) -> Unit = {},
    onPayModeChange: (PaymentMode) -> Unit = {},
    onRequestCancel: () -> Unit = {},
    onViewDetails: (Long) -> Unit
) = OrderTableRow(
    row = row,
    profile = profile,
    enabledModes = enabledModes,
    compactLayout = compactLayout,
    onStatusChange = onStatusChange,
    onPayModeChange = onPayModeChange,
    onRequestCancel = onRequestCancel,
    onViewDetails = onViewDetails
)

/**
 * Orders Data Table Component displaying the list of orders with headers and empty state.
 */
@Composable
fun OrderHistoryTableComponent(
    rows: List<com.khanabook.lite.pos.feature.reports.domain.OrderLevelRow>,
    profile: RestaurantProfileEntity?,
    enabledModes: List<PaymentMode> = emptyList(),
    compactLayout: Boolean = false,
    onStatusChange: (Long, String) -> Unit = { _, _ -> },
    onPayModeChange: (Long, PaymentMode) -> Unit = { _, _ -> },
    onRequestCancel: (Long) -> Unit = {},
    onViewDetails: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    Column(modifier = modifier.fillMaxSize()) {
        OrderTableHeader()

        if (rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.extraLarge),
                contentAlignment = Alignment.Center
            ) {
                KhanaEmptyState(
                    title = "No orders in this period",
                    message = "Try another date or report filter.",
                    icon = Icons.Default.Description
                )
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScrollbar(listState)
                    .padding(horizontal = spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
                contentPadding = PaddingValues(top = spacing.extraSmall, bottom = spacing.medium)
            ) {
                items(rows) { row ->
                    OrderTableRow(
                        row = row,
                        profile = profile,
                        enabledModes = enabledModes,
                        compactLayout = compactLayout,
                        onStatusChange = { newStatus -> onStatusChange(row.billId, newStatus) },
                        onPayModeChange = { newMode -> onPayModeChange(row.billId, newMode) },
                        onRequestCancel = { onRequestCancel(row.billId) },
                        onViewDetails = onViewDetails
                    )
                }
            }
        }
    }
}

/** Compose idiomatic alias for OrderHistoryTableComponent */
@Composable
fun OrderHistoryTable(
    rows: List<com.khanabook.lite.pos.feature.reports.domain.OrderLevelRow>,
    profile: RestaurantProfileEntity?,
    enabledModes: List<PaymentMode> = emptyList(),
    compactLayout: Boolean = false,
    onStatusChange: (Long, String) -> Unit = { _, _ -> },
    onPayModeChange: (Long, PaymentMode) -> Unit = { _, _ -> },
    onRequestCancel: (Long) -> Unit = {},
    onViewDetails: (Long) -> Unit,
    modifier: Modifier = Modifier
) = OrderHistoryTableComponent(
    rows = rows,
    profile = profile,
    enabledModes = enabledModes,
    compactLayout = compactLayout,
    onStatusChange = onStatusChange,
    onPayModeChange = onPayModeChange,
    onRequestCancel = onRequestCancel,
    onViewDetails = onViewDetails,
    modifier = modifier
)

/** Backward-compatibility alias for OrderLevelView */
@Composable
fun OrderLevelView(
    rows: List<com.khanabook.lite.pos.feature.reports.domain.OrderLevelRow>,
    profile: RestaurantProfileEntity?,
    enabledModes: List<PaymentMode> = emptyList(),
    compactLayout: Boolean = false,
    onStatusChange: (Long, String) -> Unit = { _, _ -> },
    onPayModeChange: (Long, PaymentMode) -> Unit = { _, _ -> },
    onRequestCancel: (Long) -> Unit = {},
    onViewDetails: (Long) -> Unit
) = OrderHistoryTableComponent(
    rows = rows,
    profile = profile,
    enabledModes = enabledModes,
    compactLayout = compactLayout,
    onStatusChange = onStatusChange,
    onPayModeChange = onPayModeChange,
    onRequestCancel = onRequestCancel,
    onViewDetails = onViewDetails
)

/**
 * Order Search input bar with clear action button.
 */
@Composable
fun OrderSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium)
            .padding(bottom = spacing.smallMedium)
    ) {
        KhanaBookInputField(
            value = query,
            onValueChange = onQueryChange,
            label = "Search orders",
            placeholder = "OrderNo, InvoiceNo, CustomerName, PhoneNumber, BillAmount, PaymentMode, OrderType",
            marqueePlaceholder = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = TextGold
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}
