package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.DailyClosingData
import com.khanabook.lite.pos.ui.viewmodel.DailyClosingViewModel
import com.khanabook.lite.pos.ui.viewmodel.PaymentSplit
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyClosingScreen(
    onBack: () -> Unit,
    viewModel: DailyClosingViewModel = hiltViewModel()
) {
    val data by viewModel.data.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Closing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            if (error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = error ?: "",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Button(onClick = { viewModel.load() }) { Text("Retry") }
            }

            data?.let { closing -> DailyClosingContent(closing) }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DailyClosingContent(data: DailyClosingData) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))

    // Hero card - Net Revenue
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Net Revenue",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = currencyFormat.format(data.netRevenue),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (data.refundedAmount > 0) {
                Text(
                    text = "After ${currencyFormat.format(data.refundedAmount)} refunds",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }

    // Stats row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Total Billed",
            value = currencyFormat.format(data.totalRevenue),
            subtitle = "${data.completedOrders} orders"
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Cancelled",
            value = "${data.cancelledOrders}",
            subtitle = if (data.cancelledOrders > 0) "${data.cancelledOrders} orders" else "None",
            isWarning = data.cancelledOrders > 0
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Open/Draft",
            value = "${data.draftOrders}",
            subtitle = if (data.draftOrders > 0) "Unsettled" else "None",
            isWarning = data.draftOrders > 0
        )
    }

    // Payment splits
    if (data.paymentSplits.isNotEmpty()) {
        Text(
            text = "Payment Breakdown",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(top = 8.dp)
        )

        data.paymentSplits.forEach { split ->
            PaymentSplitRow(split, currencyFormat)
        }
    }

    // Expected cash
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Expected Cash in Drawer",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Count your physical cash and compare",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                )
            }
            Text(
                text = currencyFormat.format(data.expectedCash),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }

    // Summary
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Order Summary",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SummaryRow("Total orders today", "${data.totalOrders}")
            SummaryRow("Completed (paid)", "${data.completedOrders}", isPositive = true)
            SummaryRow("Cancelled", "${data.cancelledOrders}", isNegative = data.cancelledOrders > 0)
            SummaryRow("Still open (draft)", "${data.draftOrders}", isWarning = data.draftOrders > 0)
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subtitle: String,
    isWarning: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isWarning)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun PaymentSplitRow(split: PaymentSplit, currencyFormat: NumberFormat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = getPaymentIcon(split.mode),
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = split.label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = "${split.count} orders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        Text(
            text = currencyFormat.format(split.total),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, isPositive: Boolean = false, isNegative: Boolean = false, isWarning: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = when {
                isPositive -> MaterialTheme.colorScheme.primary
                isNegative -> MaterialTheme.colorScheme.error
                isWarning -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

private fun getPaymentIcon(mode: String): String = when {
    mode.contains("cash") && mode.contains("upi") -> "💵📱"
    mode.contains("cash") && mode.contains("pos") -> "💵💳"
    mode.contains("upi") && mode.contains("pos") -> "📱💳"
    mode == "cash" -> "💵"
    mode == "upi" -> "📱"
    mode == "pos" || mode == "card" -> "💳"
    else -> "💰"
}
