@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens.applock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.data.local.dao.BillIdConflictBill
import com.khanabook.lite.pos.data.local.dao.BillIdDuplicateGroup
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.getInvoiceNumberDisplay
import com.khanabook.lite.pos.data.local.entity.SyncQuarantineEntity
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.DangerRed
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.KhanaRadii
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncCenterView(viewModel: SettingsViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val spacing = KhanaBookTheme.spacing
    val failedBills by viewModel.failedBillSyncs.collectAsStateWithLifecycle()
    val retryingIds by viewModel.retryingFailedBillIds.collectAsStateWithLifecycle()
    val quarantinedRecords by viewModel.quarantinedSyncRecords.collectAsStateWithLifecycle()
    val duplicateIdHealth by viewModel.duplicateIdHealth.collectAsStateWithLifecycle()
    val cancellingConflictIds by viewModel.cancellingConflictBillIds.collectAsStateWithLifecycle()
    val syncCenterMessage by viewModel.syncCenterMessage.collectAsStateWithLifecycle()
    val lastSyncTimestamp by viewModel.lastSyncTimestamp.collectAsStateWithLifecycle()
    val pendingCount = failedBills.size
    val retryingCount = retryingIds.size
    val quarantineCount = quarantinedRecords.size
    val idConflictCount = duplicateIdHealth.conflictGroupCount

    LaunchedEffect(Unit) {
        viewModel.refreshFailedBillSyncs()
        viewModel.refreshDuplicateIdHealth()
        viewModel.refreshLastSyncTimestamp()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.large, vertical = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        Text(
            "Sync Center",
            color = PrimaryGold,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Track bills that are waiting to sync and retry them from one place.",
            color = TextLight.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            if (lastSyncTimestamp > 0L) {
                "Last sync: ${formatSyncIssueTime(lastSyncTimestamp)}"
            } else {
                "Last sync: not completed yet"
            },
            color = TextGold.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall
        )
        syncCenterMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                message,
                color = if (message.startsWith("Unable", ignoreCase = true)) DangerRed else SuccessGreen,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            SyncCenterStatCard(
                label = "Blocked bills",
                value = pendingCount.toString(),
                tint = if (pendingCount > 0) DangerRed else SuccessGreen,
                modifier = Modifier.weight(1f)
            )
            SyncCenterStatCard(
                label = "Retrying now",
                value = retryingCount.toString(),
                tint = if (retryingCount > 0) PrimaryGold else TextGold,
                modifier = Modifier.weight(1f)
            )
        }

        SyncCenterStatCard(
            label = "Rows needing review",
            value = quarantineCount.toString(),
            tint = if (quarantineCount > 0) PrimaryGold else SuccessGreen,
            modifier = Modifier.fillMaxWidth()
        )

        SyncCenterStatCard(
            label = "ID conflicts",
            value = idConflictCount.toString(),
            tint = if (idConflictCount > 0) DangerRed else SuccessGreen,
            modifier = Modifier.fillMaxWidth()
        )

        SyncIssuesCard(
            failedBills = failedBills,
            retryingIds = retryingIds,
            onRefresh = viewModel::refreshFailedBillSyncs,
            onRetry = viewModel::retryFailedBillSync,
            onRetryAll = viewModel::retryAllFailedBillSyncs,
            onRepair = viewModel::repairFailedBillSync
        )

        QuarantineIssuesCard(
            quarantinedRecords = quarantinedRecords
        )

        OrderIdHealthCard(
            duplicateInvoiceGroups = duplicateIdHealth.duplicateInvoiceGroups,
            duplicateDailyGroups = duplicateIdHealth.duplicateDailyGroups,
            conflictBills = duplicateIdHealth.conflictBills,
            cancellingConflictIds = cancellingConflictIds,
            isRepairing = duplicateIdHealth.isRepairing,
            message = duplicateIdHealth.lastRepairMessage,
            onRefresh = viewModel::refreshDuplicateIdHealth,
            onRepair = viewModel::repairOrderIdCounters,
            onCancelDuplicate = viewModel::cancelDuplicateConflictBill
        )

        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = KhanaRadii.lg
        ) {
            Column(
                modifier = Modifier.padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Text(
                    "What this screen does",
                    color = TextLight,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Bills stay on this device when sync is delayed. Use Retry after the connection is back, or refresh to re-check the blocked list.",
                    color = TextGold.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Rows needing review are bill items or payments that need investigation even when the parent bill sync succeeds.",
                    color = TextGold.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Order and invoice IDs are checked across dine-in and takeaway bills. Prevent future duplicates moves the next bill number forward without renumbering old invoices.",
                    color = TextGold.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = {
                        viewModel.refreshFailedBillSyncs()
                        viewModel.refreshDuplicateIdHealth()
                        viewModel.refreshLastSyncTimestamp()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.45f)),
                    shape = KhanaRadii.md
                ) {
                    Icon(Icons.Default.Refresh, null, tint = PrimaryGold)
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text("Refresh sync health", color = TextGold)
                }
            }
        }
    }
}

@Composable
private fun OrderIdHealthCard(
    duplicateInvoiceGroups: List<BillIdDuplicateGroup>,
    duplicateDailyGroups: List<BillIdDuplicateGroup>,
    conflictBills: List<BillIdConflictBill>,
    cancellingConflictIds: Set<Long>,
    isRepairing: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onRepair: () -> Unit,
    onCancelDuplicate: (Long) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val hasConflicts = duplicateInvoiceGroups.isNotEmpty() || duplicateDailyGroups.isNotEmpty()
    KhanaBookCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBG),
        shape = KhanaRadii.lg
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hasConflicts) Icons.Default.SyncProblem else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (hasConflicts) DangerRed else SuccessGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Column {
                        Text("Order / Invoice IDs", color = TextLight, style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (hasConflicts) "Conflict groups found" else "No duplicate IDs found",
                            color = TextGold.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh ID health", tint = PrimaryGold)
                }
            }

            Text(
                "This check covers dine-in and takeaway bills together, so both flows share the same invoice/order counter safety.",
                color = TextGold.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall
            )

            message?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = if (it.startsWith("Unable", ignoreCase = true)) DangerRed else SuccessGreen,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (hasConflicts) {
                conflictBills.take(8).forEach { bill ->
                    DuplicateConflictBillRow(
                        bill = bill,
                        isCancelling = cancellingConflictIds.contains(bill.id),
                        onCancel = { onCancelDuplicate(bill.id) }
                    )
                }
                duplicateInvoiceGroups.take(3).forEach { group ->
                    DuplicateIdRow(
                        title = "Invoice ${group.idValue}",
                        group = group
                    )
                }
                duplicateDailyGroups.take(3).forEach { group ->
                    DuplicateIdRow(
                        title = "Order ${group.idValue}",
                        group = group
                    )
                }
                Text(
                    "Prevent future duplicates stops the next bill from colliding. Completed duplicate invoices stay listed for manual review.",
                    color = TextGold.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = onRepair,
                enabled = !isRepairing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                shape = KhanaRadii.md
            ) {
                if (isRepairing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = TextLight
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text("Repairing...", color = TextLight)
                } else {
                    Text("Prevent future duplicates", color = TextLight)
                }
            }
        }
    }
}

@Composable
private fun DuplicateConflictBillRow(
    bill: BillIdConflictBill,
    isCancelling: Boolean,
    onCancel: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val canCancel = !bill.orderStatus.equals("completed", ignoreCase = true) &&
        !bill.orderStatus.equals("paid", ignoreCase = true) &&
        !bill.orderStatus.equals("cancelled", ignoreCase = true)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBrown1.copy(alpha = 0.45f), KhanaRadii.md)
            .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Local ${bill.id} - ${bill.dailyOrderDisplay}/${bill.invoiceNumber
                        ?: bill.lifetimeOrderId?.takeIf { it > 0 }?.let { "INV$it" }
                        ?: "legacy"}",
                    color = TextLight,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${bill.orderType.replace('_', ' ')} - ${bill.orderStatus} - ${bill.paymentStatus}",
                    color = TextGold.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                bill.totalAmount,
                color = PrimaryGold,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            "${bill.paymentMode} - ${formatSyncIssueTime(bill.createdAt)}",
            color = TextGold.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodySmall
        )
        if (canCancel) {
            TextButton(onClick = onCancel, enabled = !isCancelling) {
                if (isCancelling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryGold
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text("Cancelling...", color = PrimaryGold)
                } else {
                    Text("Mark duplicate cancelled", color = PrimaryGold)
                }
            }
        } else {
            Text(
                if (bill.orderStatus.equals("cancelled", ignoreCase = true)) {
                    "Already cancelled"
                } else {
                    "Completed bill: review only"
                },
                color = TextGold.copy(alpha = 0.62f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DuplicateIdRow(
    title: String,
    group: BillIdDuplicateGroup
) {
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBrown1.copy(alpha = 0.45f), KhanaRadii.md)
            .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
    ) {
        Text(
            "$title - ${group.duplicateCount} bills",
            color = TextLight,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        group.sampleBills?.takeIf { it.isNotBlank() }?.let { sample ->
            Text(
                sample,
                color = TextGold.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun QuarantineIssuesCard(
    quarantinedRecords: List<SyncQuarantineEntity>
) {
    val spacing = KhanaBookTheme.spacing
    KhanaBookCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBG),
        shape = KhanaRadii.lg
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (quarantinedRecords.isEmpty()) Icons.Default.CheckCircle else Icons.Default.SyncProblem,
                        contentDescription = null,
                        tint = if (quarantinedRecords.isEmpty()) SuccessGreen else PrimaryGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Column {
                        Text("Rows Needing Review", color = TextLight, style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (quarantinedRecords.isEmpty()) "No child rows need review" else "${quarantinedRecords.size} child row(s) need review",
                            color = TextGold.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (quarantinedRecords.isNotEmpty()) {
                quarantinedRecords.take(5).forEach { record ->
                    QuarantineRecordRow(record)
                }
            }
        }
    }
}

@Composable
private fun QuarantineRecordRow(record: SyncQuarantineEntity) {
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBrown1.copy(alpha = 0.45f), KhanaRadii.md)
            .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
    ) {
        Text(
            text = record.childDisplayName?.takeIf { it.isNotBlank() }
                ?: record.childEntityType.replace('_', ' '),
            color = TextLight,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = buildString {
                append("Bill #")
                append(record.parentBillDisplay?.takeIf { it.isNotBlank() } ?: record.parentBillId.toString())
                append(" • ")
                append(record.childSummary?.takeIf { it.isNotBlank() } ?: "Local ID ${record.childLocalId}")
            },
            color = TextGold.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodySmall
        )
        record.syncFailureReason?.takeIf { it.isNotBlank() }?.let { reason ->
            Text(
                text = reason,
                color = TextGold.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SyncCenterStatCard(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    KhanaBookCard(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBG),
        shape = KhanaRadii.lg
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
        ) {
            Text(
                text = value,
                color = tint,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = TextGold.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
internal fun SyncIssuesCard(
    failedBills: List<BillEntity>,
    retryingIds: Set<Long>,
    onRefresh: () -> Unit,
    onRetry: (Long) -> Unit,
    onRetryAll: () -> Unit,
    onRepair: (Long) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    KhanaBookCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBG),
        shape = KhanaRadii.lg
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (failedBills.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (failedBills.isEmpty()) SuccessGreen else DangerRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Column {
                        Text("Sync Issues", color = TextLight, style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (failedBills.isEmpty()) "No blocked bills" else "${failedBills.size} blocked bill(s)",
                            color = TextGold.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh sync issues", tint = PrimaryGold)
                }
            }

            if (failedBills.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onRetryAll, enabled = retryingIds.size < failedBills.size) {
                        Text("Retry all", color = PrimaryGold)
                    }
                }
            }

            if (failedBills.isNotEmpty()) {
                failedBills.take(5).forEach { bill ->
                    FailedBillSyncRow(
                        bill = bill,
                        isRetrying = retryingIds.contains(bill.id),
                        onRetry = { onRetry(bill.id) },
                        onRepair = { onRepair(bill.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FailedBillSyncRow(
    bill: BillEntity,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onRepair: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBrown1.copy(alpha = 0.45f), KhanaRadii.md)
            .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Bill #${bill.dailyOrderDisplay.ifBlank { bill.getInvoiceNumberDisplay() }}",
                color = TextLight,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                if (bill.syncFailureReason?.contains("Duplicate order", ignoreCase = true) == true) {
                    TextButton(onClick = onRepair, enabled = !isRetrying) {
                        Text("Repair", color = SuccessGreen, style = MaterialTheme.typography.labelMedium)
                    }
                }
                TextButton(onClick = onRetry, enabled = !isRetrying) {
                    if (isRetrying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryGold
                        )
                    } else {
                        Text("Retry", color = PrimaryGold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        Text(
            bill.syncFailureReason ?: "Sync rejected after automatic recovery.",
            color = TextGold.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodySmall
        )
        bill.syncFailedAt?.let { failedAt ->
            Text(
                "Failed ${formatSyncIssueTime(failedAt)}",
                color = TextGold.copy(alpha = 0.52f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

internal fun formatSyncIssueTime(timestamp: Long): String {
    return SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(timestamp))
}
