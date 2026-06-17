package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.domain.model.OrderStatus
import com.khanabook.lite.pos.domain.model.PaymentStatus
import com.khanabook.lite.pos.ui.theme.KbBrandSaffron
import com.khanabook.lite.pos.ui.theme.KbError
import com.khanabook.lite.pos.ui.theme.KbSuccess
import com.khanabook.lite.pos.ui.theme.kbOutlineSubtle
import com.khanabook.lite.pos.ui.theme.kbTextPrimary
import com.khanabook.lite.pos.ui.theme.kbTextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Horizontal order lifecycle timeline: Created → Paid → Printed → Completed.
 *
 * State is derived from the bill's [BillEntity.orderStatus] / [BillEntity.paymentStatus]:
 *  - Created  — always done (we have [BillEntity.createdAt]).
 *  - Paid     — done when payment succeeded (or [BillEntity.paidAt] is set).
 *  - Printed/Completed — done when the order is completed.
 *  - Any not-yet-reached step on a cancelled order renders as a red ✕.
 *
 * Timestamps are shown only where the data exists (Created, Paid); we don't have
 * print/complete timestamps in the schema, so those nodes show only their label.
 */
private enum class TlState { DONE, PENDING, CANCELLED }

private data class TlStep(val label: String, val time: String?, val state: TlState)

private val tlTimeFormat get() = SimpleDateFormat("h:mm a", Locale.getDefault())

private fun formatTlTime(millis: Long?): String? =
    millis?.let { tlTimeFormat.format(Date(it)) }

private fun buildTimelineSteps(bill: BillEntity): List<TlStep> {
    val cancelled = bill.orderStatus == OrderStatus.CANCELLED.dbValue
    val completed = bill.orderStatus == OrderStatus.COMPLETED.dbValue
    val paid = bill.paymentStatus == PaymentStatus.SUCCESS.dbValue || bill.paidAt != null

    fun reached(done: Boolean): TlState = when {
        done -> TlState.DONE
        cancelled -> TlState.CANCELLED
        else -> TlState.PENDING
    }

    return listOf(
        TlStep("Created", formatTlTime(bill.createdAt), TlState.DONE),
        TlStep("Paid", formatTlTime(bill.paidAt), reached(paid)),
        TlStep("Printed", null, reached(completed)),
        TlStep("Completed", null, reached(completed)),
    )
}

@Composable
fun PaymentTimeline(bill: BillEntity, modifier: Modifier = Modifier) {
    val steps = buildTimelineSteps(bill)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "ORDER TIMELINE",
            color = KbBrandSaffron,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            steps.forEachIndexed { index, step ->
                val prev = steps.getOrNull(index - 1)
                val next = steps.getOrNull(index + 1)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Connector lines (behind) + node (on top)
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Connector(active = index != 0 && step.state == TlState.DONE,
                                cancelled = prev?.state == TlState.CANCELLED || step.state == TlState.CANCELLED && index != 0)
                            Connector(active = next != null && next.state == TlState.DONE,
                                cancelled = next?.state == TlState.CANCELLED)
                        }
                        TlNode(step)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = step.label,
                        color = when (step.state) {
                            TlState.DONE -> MaterialTheme.kbTextPrimary
                            TlState.CANCELLED -> KbError
                            TlState.PENDING -> MaterialTheme.kbTextTertiary
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (step.state == TlState.DONE) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        textAlign = TextAlign.Center
                    )
                    step.time?.let { t ->
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = t,
                            color = MaterialTheme.kbTextTertiary,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Connector(active: Boolean, cancelled: Boolean) {
    val color = when {
        active -> KbSuccess
        cancelled -> KbError.copy(alpha = 0.5f)
        else -> MaterialTheme.kbOutlineSubtle
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .height(2.dp)
            .background(color)
    )
}

@Composable
private fun TlNode(step: TlStep) {
    val size = 30.dp
    when (step.state) {
        TlState.DONE -> NodeWithIcon(size, KbSuccess, Icons.Default.Check)
        TlState.CANCELLED -> NodeWithIcon(size, KbError, Icons.Default.Close)
        TlState.PENDING -> Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(2.dp, MaterialTheme.kbOutlineSubtle), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.kbTextTertiary)
            )
        }
    }
}

@Composable
private fun NodeWithIcon(size: androidx.compose.ui.unit.Dp, bg: Color, icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}
