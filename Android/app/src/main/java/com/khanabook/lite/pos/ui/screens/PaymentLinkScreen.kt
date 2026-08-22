package com.khanabook.lite.pos.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import com.khanabook.lite.pos.ui.theme.KhanaRadii
import com.khanabook.lite.pos.ui.theme.KhanaShapes
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.StickyBottomScaffold
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.DarkBrown2
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.RichEspresso
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.viewmodel.PaymentLinkState
import com.khanabook.lite.pos.ui.viewmodel.PaymentLinkViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentLinkScreen(
    onBack: () -> Unit,
    viewModel: PaymentLinkViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var customerName by remember { mutableStateOf("") }
    var customerEmail by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var merchantTxn by remember { mutableStateOf("") }

    val currentState = state
    LaunchedEffect(currentState) {
        when (currentState) {
            is PaymentLinkState.Success -> {
                KhanaToast.show("Payment link created!", ToastKind.Success)
            }
            is PaymentLinkState.Error -> {
                KhanaToast.show(currentState.error, ToastKind.Error)
            }
            is PaymentLinkState.Loading -> {
                // Loading handled by UI
            }
            else -> {
                // Idle - do nothing
            }
        }
    }

    androidx.compose.material3.Scaffold(
        containerColor = DarkBrown1,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { androidx.compose.material3.Text("Create Payment Link", color = TextLight) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBrown1)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso))
                )
        ) {
            StickyBottomScaffold(
                bottomBar = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        Button(
                            onClick = {
                                if (customerName.isBlank() || customerEmail.isBlank() || customerPhone.isBlank() || amount.isBlank()) {
                                    coroutineScope.launch { KhanaToast.show("Please fill all required fields", ToastKind.Warning) }
                                    return@Button
                                }
                                val amt = amount.toDoubleOrNull()
                                if (amt == null || amt <= 0) {
                                    coroutineScope.launch { KhanaToast.show("Enter a valid amount", ToastKind.Warning) }
                                    return@Button
                                }
                                viewModel.createLink(
                                    customerName = customerName,
                                    customerEmail = customerEmail,
                                    customerPhone = customerPhone,
                                    amount = amount,
                                    message = message,
                                    merchantTxn = merchantTxn
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state is PaymentLinkState.Loading) PrimaryGold.copy(alpha = 0.5f) else PrimaryGold
                            ),
                            shape = KhanaShapes.large,
                            enabled = state !is PaymentLinkState.Loading
                        ) {
                            if (state is PaymentLinkState.Loading) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(spacing.small))
                            }
                            Text(
                                text = when {
                                    state is PaymentLinkState.Loading -> "Creating Link..."
                                    else -> "Create Payment Link"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Share buttons - only visible after success
                        if (state is PaymentLinkState.Success) {
                            val linkUrl = (state as PaymentLinkState.Success).linkUrl
                            val ref = (state as PaymentLinkState.Success).merchantTxn

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                            ) {
                                Button(
                                    onClick = {
                                        copyToClipboard(context, linkUrl)
                                        coroutineScope.launch { KhanaToast.show("Link copied to clipboard!", ToastKind.Success) }
                                    },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkBrown2),
                                    shape = KhanaShapes.large
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = PrimaryGold, modifier = Modifier.size(KhanaBookTheme.iconSize.small))
                                    Spacer(modifier = Modifier.width(spacing.small))
                                    Text("Copy Link", style = MaterialTheme.typography.labelLarge, color = TextLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                Button(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "Payment Link: $linkUrl\nReference: $ref")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Payment Link"))
                                    },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                                    shape = KhanaShapes.large
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(KhanaBookTheme.iconSize.small))
                                    Spacer(modifier = Modifier.width(spacing.small))
                                    Text("Share", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                },
                bottomBarContainerColor = DarkBrown1
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    Spacer(modifier = Modifier.height(spacing.small))

                    // Customer Details Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = DarkBrown2,
                            contentColor = TextLight
                        ),
                        shape = KhanaShapes.large
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.large),
                            verticalArrangement = Arrangement.spacedBy(spacing.medium)
                        ) {
                            Text("Customer Details", style = MaterialTheme.typography.titleMedium, color = TextLight)

                            TextField(
                                value = customerName,
                                onValueChange = { customerName = it },
                                label = { Text("Customer Name") },
                                placeholder = { Text("Enter customer name") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Text),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                isError = customerName.isBlank()
                            )

                            TextField(
                                value = customerEmail,
                                onValueChange = { customerEmail = it },
                                label = { Text("Email") },
                                placeholder = { Text("customer@example.com") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                isError = customerEmail.isBlank()
                            )

                            TextField(
                                value = customerPhone,
                                onValueChange = { customerPhone = it },
                                label = { Text("Phone") },
                                placeholder = { Text("9876543210") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                isError = customerPhone.isBlank()
                            )
                        }
                    }

                    // Payment Details Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = DarkBrown2,
                            contentColor = TextLight
                        ),
                        shape = KhanaShapes.large
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.large),
                            verticalArrangement = Arrangement.spacedBy(spacing.medium)
                        ) {
                            Text("Payment Details", style = MaterialTheme.typography.titleMedium, color = TextLight)

                            TextField(
                                value = amount,
                                onValueChange = { amount = it.filter { it.isDigit() || it == '.' } },
                                label = { Text("Amount (₹)") },
                                placeholder = { Text("0.00") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                isError = amount.isBlank()
                            )

                            TextField(
                                value = merchantTxn,
                                onValueChange = { merchantTxn = it },
                                label = { Text("Payment Reference (Optional)") },
                                placeholder = { Text("Auto-generated if empty") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Text),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            )

                            TextField(
                                value = message,
                                onValueChange = { message = it },
                                label = { Text("Message / Purpose") },
                                placeholder = { Text("Payment for order #123") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Text),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            )
                        }
                    }

                    // Show link preview after success
                    if (state is PaymentLinkState.Success) {
                        val linkUrl = (state as PaymentLinkState.Success).linkUrl
                        val ref = (state as PaymentLinkState.Success).merchantTxn

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = DarkBrown2,
                                contentColor = TextLight
                            ),
                            shape = KhanaShapes.medium
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(spacing.large),
                                verticalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                Text("Payment Link Created", style = MaterialTheme.typography.titleSmall, color = SuccessGreen)
                                Text("Reference: $ref", style = MaterialTheme.typography.bodySmall, color = TextLight.copy(alpha = 0.7f))
                                TextField(
                                    value = linkUrl,
                                    onValueChange = {},
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(spacing.small)
                                        .background(DarkBrown2)
                                        .height(80.dp),
                                    singleLine = false,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextLight),
                                    readOnly = true
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.medium))
                }
            }
        }
    }
}

private fun copyToClipboard(context: android.content.Context, text: String) {
    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clipData = android.content.ClipData.newPlainText("Payment Link", text)
    clipboardManager.setPrimaryClip(clipData)
}