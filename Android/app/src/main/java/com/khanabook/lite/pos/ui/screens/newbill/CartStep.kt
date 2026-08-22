@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens.newbill

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.domain.util.ValidationUtils
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.BillingViewModel
import kotlinx.coroutines.flow.flowOf

@Composable
fun CustomerInfoStep(
    onNext: (String, String) -> Unit,
    onBack: () -> Unit,
    hideHeader: Boolean = false,
    billingViewModel: com.khanabook.lite.pos.ui.viewmodel.BillingViewModel? = null,
    activeDraftBills: List<BillEntity> = emptyList(),
    onOpenDraftOrder: (billId: Long, targetStep: Int) -> Unit = { _, _ -> }
) {
    var name by remember { mutableStateOf(billingViewModel?.customerName?.value ?: "") }
    var whatsapp by remember { mutableStateOf(billingViewModel?.customerWhatsapp?.value ?: "") }
    val spacing = KhanaBookTheme.spacing

    val recentCustomers by (billingViewModel?.recentCustomers ?: kotlinx.coroutines.flow.flowOf(emptyList<Pair<String,String>>())).collectAsStateWithLifecycle(emptyList())
    val recentDineInCustomers by (billingViewModel?.recentDineInCustomers ?: kotlinx.coroutines.flow.flowOf(emptyList<Pair<String,String>>())).collectAsStateWithLifecycle(emptyList())
    val currentOrderType by (billingViewModel?.orderType ?: kotlinx.coroutines.flow.flowOf("dine_in")).collectAsStateWithLifecycle("dine_in")
    var selectedOrderType by remember { mutableStateOf(if (currentOrderType == "takeaway") "takeaway" else "dine_in") }

    LaunchedEffect(Unit) {
        billingViewModel?.loadRecentCustomers()
        billingViewModel?.loadRecentDineInCustomers()
    }
    LaunchedEffect(currentOrderType) {
        selectedOrderType = if (currentOrderType == "takeaway") "takeaway" else "dine_in"
    }

    val showPhoneError = whatsapp.isNotEmpty() && !ValidationUtils.isValidPhone(whatsapp)
    val isNextEnabled = when (selectedOrderType) {
        "dine_in" -> ValidationUtils.isValidPhone(whatsapp)
        "takeaway" -> ValidationUtils.isValidPhone(whatsapp)
        else -> false
    }

    val layout = KhanaBookTheme.layout

    com.khanabook.lite.pos.ui.designsystem.StickyBottomScaffold(
        bottomBar = {
            if (selectedOrderType != "active_order") {
                Button(
                    onClick = { if (isNextEnabled) onNext(name, whatsapp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(KhanaBookTheme.spacing.buttonHeightLarge),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNextEnabled) PrimaryGold else Color.Gray
                    ),
                    shape = KhanaRadii.lg,
                    enabled = isNextEnabled
                ) {
                    Text(
                        "Continue",
                        color = if (isNextEnabled) DarkBrown1 else Color.LightGray,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    ) {
    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = layout.contentPadding, vertical = spacing.medium)
    ) {
        if (!hideHeader) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryGold
                    )
                }
                Column {
                    Text(
                            "New Bill",
                            color = PrimaryGold,
                            style = MaterialTheme.typography.headlineMedium
                    )
                    Text("Customer Details & Order Type", color = TextGold, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(spacing.extraLarge))
        }

        Text(
            "Order Type",
            color = TextGold,
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(spacing.small))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            OrderTypeButton(
                text = "Dine-In",
                isSelected = selectedOrderType == "dine_in",
                modifier = Modifier.weight(1f)
            ) {
                selectedOrderType = "dine_in"
                billingViewModel?.setOrderType("dine_in")
            }
            OrderTypeButton(
                text = "Takeaway",
                isSelected = selectedOrderType == "takeaway",
                modifier = Modifier.weight(1f)
            ) {
                selectedOrderType = "takeaway"
                billingViewModel?.setOrderType("takeaway")
            }
        }
        Spacer(modifier = Modifier.height(spacing.large))

        if (selectedOrderType == "active_order") {
            Text(
                "Active Orders",
                color = TextGold,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(spacing.extraSmall))
            if (activeDraftBills.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = KhanaRadii.lg,
                    color = DarkBrown2,
                    border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = "No active orders right now.",
                        modifier = Modifier.padding(spacing.medium),
                        color = TextGold.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    activeDraftBills.forEach { bill ->
                        Surface(
                            shape = KhanaRadii.lg,
                            color = DarkBrown2,
                            border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(min = 160.dp, max = 220.dp)
                                    .padding(spacing.medium),
                                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                            ) {
                                Text(
                                    text = bill.customerName ?: "Table",
                                    color = PrimaryGold,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = CurrencyUtils.formatPrice(bill.totalAmount.toDoubleOrNull() ?: 0.0),
                                    color = TextLight,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                                    TextButton(
                                        onClick = { onOpenDraftOrder(bill.id, 2) },
                                        contentPadding = PaddingValues(horizontal = spacing.small, vertical = 0.dp)
                                    ) {
                                        Text("Add", color = VegGreen, style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton(
                                        onClick = { onOpenDraftOrder(bill.id, 3) },
                                        contentPadding = PaddingValues(horizontal = spacing.small, vertical = 0.dp)
                                    ) {
                                        Text("Settle", color = PrimaryGold, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(spacing.large))
        }

        if (recentCustomers.isNotEmpty() && selectedOrderType == "takeaway") {
            Text(
                "Recent Customers",
                color = TextGold,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(spacing.extraSmall))
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                recentCustomers.forEach { customer ->
                    val phone = customer.first
                    val customerName = customer.second
                    Surface(
                        onClick = {
                            whatsapp = phone
                            name = customerName
                        },
                        shape = KhanaRadii.xl,
                        color = DarkBrown2,
                        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.widthIn(max = (120 * LocalDensity.current.fontScale).dp).padding(horizontal = spacing.medium, vertical = spacing.small)) {
                            Text(
                                text = if (customerName.isNotBlank()) customerName else phone,
                                color = TextLight,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (customerName.isNotBlank()) {
                                Text(phone, color = TextGold.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(spacing.medium))
        }

        if (selectedOrderType == "dine_in") {
            if (recentDineInCustomers.isNotEmpty()) {
                Text(
                    "Recent Tables",
                    color = TextGold,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(spacing.extraSmall))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    recentDineInCustomers.forEach { customer ->
                        val phone = customer.first
                        val customerName = customer.second
                        Surface(
                            onClick = {
                                whatsapp = phone
                                name = customerName
                                billingViewModel?.setCustomerInfo(customerName, phone)
                            },
                            shape = KhanaRadii.xl,
                            color = DarkBrown2,
                            border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.widthIn(max = (120 * LocalDensity.current.fontScale).dp).padding(horizontal = spacing.medium, vertical = spacing.small)) {
                                Text(
                                    text = if (customerName.isNotBlank()) customerName else phone,
                                    color = TextLight,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (customerName.isNotBlank() && phone.isNotBlank()) {
                                    Text(phone, color = TextGold.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(spacing.medium))
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    billingViewModel?.setCustomerInfo(it, whatsapp)
                },
                label = { Text("Table name / Customer name") },
                modifier = Modifier.fillMaxWidth(),
                colors = menuTextFieldColors(),
                leadingIcon = { Icon(Icons.Default.Person, null, tint = PrimaryGold) }
            )
            Spacer(modifier = Modifier.height(spacing.medium))
            OutlinedTextField(
                value = whatsapp,
                onValueChange = {
                    val filtered = it.filter { ch -> ch.isDigit() }.take(10)
                    whatsapp = filtered
                    billingViewModel?.setCustomerInfo(name, filtered)
                },
                label = { Text("WhatsApp Number *") },
                modifier = Modifier.fillMaxWidth(),
                colors = menuTextFieldColors(),
                leadingIcon = { Icon(Icons.Default.Phone, null, tint = VegGreen) },
                isError = showPhoneError,
                supportingText = {
                    if (showPhoneError) Text("Enter 10-digit number", color = DangerRed)
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
            )
        } else if (selectedOrderType == "takeaway") {
            OutlinedTextField(
                value = whatsapp,
                onValueChange = {
                    val filtered = it.filter { ch -> ch.isDigit() }.take(10)
                    whatsapp = filtered
                    billingViewModel?.setCustomerInfo(name, filtered)
                },
                label = { Text("WhatsApp Number *") },
                modifier = Modifier.fillMaxWidth(),
                colors = menuTextFieldColors(),
                leadingIcon = { Icon(Icons.Default.Phone, null, tint = VegGreen) },
                isError = showPhoneError,
                supportingText = {
                    if (showPhoneError) Text("Enter 10-digit number", color = DangerRed)
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
            )
            Spacer(modifier = Modifier.height(spacing.medium))
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    billingViewModel?.setCustomerInfo(it, whatsapp)
                },
                label = { Text("Customer Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = menuTextFieldColors(),
                leadingIcon = { Icon(Icons.Default.Person, null, tint = PrimaryGold) }
            )
        }

    }
    }
}

