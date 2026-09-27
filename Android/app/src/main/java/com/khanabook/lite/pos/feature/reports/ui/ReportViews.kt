@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.feature.reports.ui
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.core.theme.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.model.OrderStatus
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.core.util.CurrencyUtils
import com.khanabook.lite.pos.core.util.DateUtils
import com.khanabook.lite.pos.feature.billing.ui.HeaderCell
import com.khanabook.lite.pos.feature.billing.ui.TableCell
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.feature.settings.viewmodel.SettingsViewModel

@Composable
fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryGold else Color.Transparent,
        animationSpec = tween(200),
        label = "chip_container"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) DarkBrown1 else TextLight,
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
        border = if (isSelected) null else BorderStroke(1.dp, BorderGold),
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

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
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
                    "Part-Payment",
                    color = TextGold,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = spacing.small, bottom = spacing.extraSmall)
                )
            }

            // One part-payment card per full row — same rhythm as the
            // Cash/UPI/POS cards above.
            items(partModes) { mode ->
                PartPaymentCard(
                    label = mode.displayLabel,
                    mode = mode,
                    totalAmount = breakdown[mode.displayLabel]?.toDoubleOrNull() ?: 0.0,
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
            .padding(top = spacing.small, bottom = spacing.small)
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
            height = 44.dp
        )
    }
}

@Composable
fun PaymentModeItem(mode: PaymentMode, amount: Double) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val modeColor = getPayModeIconTint(mode)
    val modeIcon = when (mode) {
        PaymentMode.CASH -> Icons.Default.Payments
        PaymentMode.UPI -> Icons.Default.QrCode2
        PaymentMode.POS -> Icons.Default.PointOfSale
        PaymentMode.EASEBUZZ, PaymentMode.PAYMENT_LINK -> Icons.Default.CreditCard
        else -> Icons.Default.CallSplit
    }
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
    totalAmount: Double,
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
                        Icons.Default.CallSplit,
                        contentDescription = null,
                        tint = modeColor,
                        modifier = Modifier.size(iconSize.medium)
                    )
                }
                Spacer(modifier = Modifier.width(spacing.medium))
                Text(label, color = TextLight, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    CurrencyUtils.formatPrice(totalAmount),
                    color = PrimaryGold,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

private val COL_ORDER   = 0.8f
private val COL_INVOICE = 1.3f
private val COL_DATE    = 1.0f
private val COL_MODE    = 1.1f
private val COL_STATUS  = 1.2f
private val COL_ACTION  = 0.8f


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
) {
    val spacing = KhanaBookTheme.spacing
    val invoiceHeader = if (profile?.gstEnabled == true) "Tax Invoice No" else "Invoice No"
    Column(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium)
                .background(DarkBrown1.copy(alpha = 0.7f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .padding(horizontal = spacing.extraSmall, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (compactLayout) {
                // Phone table: OrderNo | InvoiceNo | Mode | Status | Action
                HeaderCell("OrderNo", COL_ORDER)
                HeaderCell("InvoiceNo", COL_INVOICE)
                HeaderCell("Mode", COL_MODE)
                HeaderCell("Status", COL_STATUS)
                HeaderCell("Action", COL_ACTION)
            } else {
                HeaderCell("Order No", COL_ORDER)
                HeaderCell(invoiceHeader, COL_INVOICE)
                HeaderCell("Date", COL_DATE)
                HeaderCell("Mode", COL_MODE)
                HeaderCell("Status", COL_STATUS)
                HeaderCell("Action", COL_ACTION)
            }
        }

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
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                contentPadding = PaddingValues(bottom = spacing.small)
            ) {
                items(rows) { row ->
                    OrderRowItem(
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
) {
    var statusExpanded by remember { mutableStateOf(false) }
    var payModeExpanded by remember { mutableStateOf(false) }
    val spacing = KhanaBookTheme.spacing
    val isCancelled = row.orderStatus == OrderStatus.CANCELLED
    // Mode/status edits are allowed only on the SAME DAY the bill was taken
    // (owner request). Unknown timestamps (createdAt == 0, legacy callers) stay
    // editable so we never over-restrict.
    val canEdit = !isCancelled && run {
        if (row.createdAt <= 0L) {
            true
        } else {
            val taken = java.util.Calendar.getInstance().apply { timeInMillis = row.createdAt }
            val now = java.util.Calendar.getInstance()
            taken.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
                taken.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)
        }
    }
    // Dine-in "pay after food" drafts are unsettled bills: their payment mode is
    // decided at settlement time on the billing screen, and they must never be
    // marked Completed from the report (that would skip payment capture).
    // They may only be cancelled from here.
    val isPayAfterFoodDraft = row.orderStatus == OrderStatus.DRAFT &&
        row.orderType.trim().lowercase() in setOf("dine_in", "dine-in", "dinein") &&
        com.khanabook.lite.pos.feature.payments.domain.OrderPaymentFlowMode
            .fromDbValue(profile?.orderPaymentFlowMode) ==
            com.khanabook.lite.pos.feature.payments.domain.OrderPaymentFlowMode.PAY_AFTER_FOOD

    // Details open ONLY via the View action button — the row itself is not clickable,
    // so stray taps while scrolling never pop the dialog.
    KhanaBookCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkBrown1.copy(alpha = 0.3f)),
        shape = KhanaRadii.sm
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.extraSmall, vertical = spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableCell(row.dailyId, COL_ORDER)
            TableCell(
                row.invoiceDisplay,
                COL_INVOICE,
                fontWeight = FontWeight.Bold,
                color = if (isCancelled) TextLight.copy(alpha = 0.35f) else TextLight
            )
            if (!compactLayout) {
                TableCell(
                    row.date,
                    COL_DATE,
                    color = if (isCancelled) TextLight.copy(alpha = 0.5f) else TextMuted
                )
            }

            // Mode dropdown
            Box(modifier = Modifier.weight(COL_MODE), contentAlignment = Alignment.Center) {
                val modeColor = getPayModeColor(row.paymentMode)
                Surface(
                    onClick = { if (canEdit && !isPayAfterFoodDraft) payModeExpanded = true },
                    color = modeColor,
                    shape = KhanaRadii.sm,
                    modifier = Modifier.padding(horizontal = spacing.hairline)
                ) {
                    Text(
                        text = row.paymentMode.displayLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = spacing.extraSmall, vertical = spacing.extraSmall),
                        maxLines = 1
                    )
                }
                DropdownMenu(
                    expanded = payModeExpanded,
                    onDismissRequest = { payModeExpanded = false },
                    modifier = Modifier.background(DarkBrown2)
                ) {
                    enabledModes.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.displayLabel, color = TextLight, style = MaterialTheme.typography.bodySmall) },
                            onClick = { onPayModeChange(mode); payModeExpanded = false }
                        )
                    }
                }
            }

            // Status dropdown
            Box(modifier = Modifier.weight(COL_STATUS), contentAlignment = Alignment.Center) {
                val statusColor = when (row.orderStatus) {
                    OrderStatus.COMPLETED -> SuccessGreen
                    OrderStatus.CANCELLED -> DangerRed
                    OrderStatus.DRAFT -> PrimaryGold
                    else -> TextMuted
                }
                Surface(
                    onClick = { if (canEdit) statusExpanded = true },
                    color = statusColor,
                    shape = KhanaRadii.sm,
                    modifier = Modifier.padding(horizontal = spacing.extraSmall)
                ) {
                    Text(
                        text = when (row.orderStatus) {
                            OrderStatus.COMPLETED -> "Completed"
                            OrderStatus.CANCELLED -> "Cancelled"
                            OrderStatus.DRAFT -> "Pending"
                            else -> row.orderStatus.name.lowercase().replaceFirstChar { it.uppercase() }
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = spacing.extraSmall, vertical = spacing.extraSmall),
                        maxLines = 1
                    )
                }
                DropdownMenu(
                    expanded = statusExpanded,
                    onDismissRequest = { statusExpanded = false },
                    modifier = Modifier.background(DarkBrown2)
                ) {
                    // Pay-after-food dine-in drafts: no direct "Completed" — settle
                    // on the billing screen, or cancel here.
                    if (row.orderStatus != OrderStatus.COMPLETED && !isPayAfterFoodDraft) {
                        DropdownMenuItem(
                            text = { Text("Completed", color = TextLight, style = MaterialTheme.typography.bodySmall) },
                            onClick = { onStatusChange(OrderStatus.COMPLETED.dbValue); statusExpanded = false }
                        )
                    }
                    if (row.orderStatus != OrderStatus.DRAFT) {
                        DropdownMenuItem(
                            text = { Text("Pending", color = TextLight, style = MaterialTheme.typography.bodySmall) },
                            onClick = { onStatusChange(OrderStatus.DRAFT.dbValue); statusExpanded = false }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Cancel Order", color = DangerRed, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)) },
                        onClick = { onRequestCancel(); statusExpanded = false }
                    )
                }
            }

            // Action (View) — the ONLY way to open order details
            Box(modifier = Modifier.weight(COL_ACTION), contentAlignment = Alignment.Center) {
                Surface(
                    onClick = { onViewDetails(row.billId) },
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, PrimaryGold),
                    shape = KhanaRadii.sm
                ) {
                    Text(
                        "View",
                        color = TextLight,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = spacing.small - spacing.hairline, vertical = spacing.hairline)
                    )
                }
            }
        }
    }
}
