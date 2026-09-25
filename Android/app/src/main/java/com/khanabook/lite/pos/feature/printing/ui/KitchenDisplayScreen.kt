package com.khanabook.lite.pos.feature.printing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.BorderGold
import com.khanabook.lite.pos.core.theme.CardBG
import com.khanabook.lite.pos.core.theme.DangerRed
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.SuccessGreen
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.core.theme.TextLight
import com.khanabook.lite.pos.core.util.DateUtils
import com.khanabook.lite.pos.feature.printing.data.KotEventType
import com.khanabook.lite.pos.feature.printing.viewmodel.KitchenDisplayViewModel
import com.khanabook.lite.pos.feature.printing.viewmodel.KitchenTicketUiModel

/**
 * Kitchen Display — the printer-less KOT surface.
 *
 * Renders every unprinted KOT event (NEW / ADD / VOID / REPRINT / CANCEL) as a
 * live ticket card, exactly as the event was recorded. "Done" acknowledges the
 * ticket so it leaves the board; nothing is lost when no printer is configured.
 */
@Composable
fun KitchenDisplayScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KitchenDisplayViewModel = hiltViewModel()
) {
    val tickets by viewModel.pendingTickets.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBrown1)
            .padding(spacing.medium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextGold
                )
            }
            Spacer(modifier = Modifier.width(spacing.small))
            Icon(
                Icons.Default.Restaurant,
                contentDescription = null,
                tint = PrimaryGold,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(spacing.small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Kitchen Display",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (tickets.isEmpty()) "All caught up" else "${tickets.size} ticket(s) waiting",
                    color = TextGold.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (tickets.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.acknowledgeAll(tickets) },
                    enabled = !busy
                ) {
                    Text("Ack all", color = PrimaryGold)
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.medium))

        if (tickets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.large),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = TextGold.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(spacing.medium))
                    Text(
                        "No pending kitchen tickets",
                        color = TextLight,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(spacing.small))
                    Text(
                        "New orders, item changes and cancellations\nappear here live — with or without a printer.",
                        color = TextGold.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 360.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1120.dp)
                    .align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                items(tickets, key = { it.publicToken + ":" + it.kotRevision }) { ticket ->
                    KitchenTicketCard(
                        ticket = ticket,
                        busy = busy,
                        onAck = { viewModel.acknowledgeTicket(ticket) }
                    )
                }
            }
        }
    }
}

@Composable
private fun KitchenTicketCard(
    ticket: KitchenTicketUiModel,
    busy: Boolean,
    onAck: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val banner = when (ticket.eventType) {
        KotEventType.ADD -> "ADDED ITEMS" to SuccessGreen
        KotEventType.VOID -> "VOIDED" to DangerRed
        KotEventType.REPRINT -> "REPRINT" to TextGold
        KotEventType.CANCEL -> "ORDER CANCELLED" to DangerRed
        else -> null // NEW: plain first ticket
    }
    val isCancelled = ticket.orderStatus.equals("cancelled", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBG, RoundedCornerShape(12.dp))
            .padding(spacing.medium)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Order ${ticket.orderDisplay}",
                color = TextLight,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        banner?.first ?: "NEW",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    disabledContainerColor = (banner?.second ?: PrimaryGold).copy(alpha = 0.15f),
                    disabledLabelColor = banner?.second ?: PrimaryGold
                )
            )
        }
        Text(
            "Invoice ${ticket.invoiceDisplay} • ${DateUtils.formatDisplay(ticket.eventTimeMs)}",
            color = TextGold.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall
        )
        if (isCancelled) {
            Text(
                ticket.cancelReason?.let { "Cancelled: $it" } ?: "Order was cancelled",
                color = DangerRed,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(spacing.small))

        KotSnapshotItems(ticket.itemSnapshotJson)

        Spacer(modifier = Modifier.height(spacing.medium))
        Button(
            onClick = onAck,
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done", color = DarkBrown1, fontWeight = FontWeight.Bold)
        }
    }
}

/** Renders the event's exact item snapshot (qty x name + variant + note). */
@Composable
private fun KotSnapshotItems(itemSnapshotJson: String) {
    val parsed = remember(itemSnapshotJson) { parseSnapshotLines(itemSnapshotJson) }
    Column {
        parsed.forEach { line ->
            Text(
                line,
                color = if (line.startsWith(" ")) TextGold.copy(alpha = 0.8f) else TextLight,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun parseSnapshotLines(itemSnapshotJson: String): List<String> {
    if (itemSnapshotJson.isBlank()) return listOf("Items not captured")
    return try {
        val root = org.json.JSONArray(itemSnapshotJson)
        val lines = mutableListOf<String>()
        for (i in 0 until root.length()) {
            val obj = root.optJSONObject(i) ?: continue
            val qty = obj.optInt("quantity", 1)
            val name = obj.optString("itemName").ifBlank { "Item" }
            val variant = obj.optString("variantName").takeIf { it.isNotBlank() }
            val note = obj.optString("specialInstruction").takeIf { it.isNotBlank() }
            lines += "$qty x $name"
            variant?.let { lines += "  Variant: $it" }
            note?.let { lines += "  Note: $it" }
        }
        if (lines.isEmpty()) listOf("Items not captured") else lines
    } catch (e: Exception) {
        listOf("Items not captured")
    }
}
