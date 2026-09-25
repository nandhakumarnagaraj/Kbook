package com.khanabook.lite.pos.feature.onboarding.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.core.designsystem.KhanaToast
import com.khanabook.lite.pos.core.designsystem.KhanaBookCard
import com.khanabook.lite.pos.core.designsystem.SignaturePad
import com.khanabook.lite.pos.core.designsystem.ToastKind
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.DangerRed
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.KhanaRadii
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.SuccessGreen
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.core.theme.TextLight
import com.khanabook.lite.pos.core.theme.BorderGold
import com.khanabook.lite.pos.feature.onboarding.viewmodel.AgreementEvent
import com.khanabook.lite.pos.feature.onboarding.viewmodel.AgreementUiState
import com.khanabook.lite.pos.feature.onboarding.viewmodel.MerchantAgreementViewModel
import androidx.compose.material3.ButtonDefaults

import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material3.OutlinedTextFieldDefaults
import com.khanabook.lite.pos.core.theme.DarkBrown2
import com.khanabook.lite.pos.core.theme.ErrorPink

@Composable
private fun outlinedTextFieldColors() =
    OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = DarkBrown1,
        focusedContainerColor = DarkBrown2,
        unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
        focusedBorderColor = PrimaryGold,
        cursorColor = PrimaryGold,
        unfocusedTextColor = TextLight,
        focusedTextColor = TextLight,
        unfocusedLabelColor = TextGold.copy(alpha = 0.7f),
        focusedLabelColor = TextGold.copy(alpha = 0.7f)
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantAgreementScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MerchantAgreementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val canSign = viewModel.canSignAgreement
    val context = LocalContext.current

    var signerName by remember { mutableStateOf("") }
    var signatureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var clearTrigger by remember { mutableStateOf(0) }
    var consentGiven by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AgreementEvent.Toast -> {
                    KhanaToast.show(
                        event.message,
                        if (event.isError) ToastKind.Error else ToastKind.Success
                    )
                }
                is AgreementEvent.OpenFile -> openAgreementPdf(context, event.file)
            }
        }
    }

    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout

    Scaffold(
        containerColor = DarkBrown1,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Merchant Agreement",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryGold
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBrown1)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .widthIn(max = layout.maxContentWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.large, vertical = spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
            when (val state = uiState) {
                is AgreementUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryGold)
                    }
                }
                is AgreementUiState.Error -> {
                    KhanaBookCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = state.message,
                            modifier = Modifier.padding(spacing.medium),
                            color = DangerRed,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Button(
                        onClick = { viewModel.load() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
                    ) {
                        Text("Retry", color = DarkBrown1)
                    }
                }
                is AgreementUiState.Ready -> {
                    val status = state.status
                    KhanaBookCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = KhanaRadii.lg
                    ) {
                        Column(
                            modifier = Modifier.padding(spacing.medium),
                            verticalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (status.hasCurrentAgreement) Icons.Filled.CheckCircle else Icons.Filled.Description,
                                    contentDescription = null,
                                    tint = if (status.hasCurrentAgreement) SuccessGreen else PrimaryGold,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(spacing.small))
                                Text(
                                    if (status.hasCurrentAgreement) "Payment Agreement Signed" else "Current Payment Agreement Required",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextLight
                                )
                            }
                            if (status.hasAgreement) {
                                status.signerName?.let { Text("Signed by: $it", color = TextGold, style = MaterialTheme.typography.bodyMedium) }
                                status.agreementVersion?.let { Text("Version: $it", color = TextGold, style = MaterialTheme.typography.bodyMedium) }
                                status.signedAt?.let { Text("Signed on: ${formatDate(it)}", color = TextGold, style = MaterialTheme.typography.bodyMedium) }
                                Spacer(modifier = Modifier.height(spacing.small))
                                Button(
                                    onClick = { viewModel.downloadAndOpen() },
                                    enabled = !isSubmitting,
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
                                ) {
                                    Text("View Agreement", color = DarkBrown1)
                                }
                            }
                        }
                    }

                    if (!canSign) {
                        KhanaBookCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = KhanaRadii.lg,
                            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(spacing.medium)) {
                                Text("Read-only for staff", fontWeight = FontWeight.Bold, color = DangerRed)
                                Spacer(modifier = Modifier.height(spacing.small))
                                Text(
                                    "Signing is restricted to restaurant owners. Staff members can view the signed agreement above.",
                                    color = TextGold
                                )
                            }
                        }
                    }

                    if (canSign && !status.hasCurrentAgreement) {
                        Text(
                            "Sign the agreement",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGold
                        )
                        KhanaBookCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = KhanaRadii.lg
                        ) {
                            Column(modifier = Modifier.padding(spacing.medium)) {
                                Text(
                                    status.currentTerms ?: "Loading agreement terms...",
                                    color = TextLight,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(spacing.medium))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = consentGiven, onCheckedChange = { consentGiven = it })
                                    Text(
                                        "I am authorized to sign for this restaurant and accept the terms shown above.",
                                        color = TextLight,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                androidx.compose.material3.OutlinedTextField(
                                    value = signerName,
                                    onValueChange = { signerName = it },
                                    label = { Text("Signer name", color = TextGold) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = outlinedTextFieldColors()
                                )
                                Spacer(modifier = Modifier.height(spacing.small))
                                Text("Signature", style = MaterialTheme.typography.labelLarge, color = TextGold)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    shape = KhanaRadii.lg
                                ) {
                                    com.khanabook.lite.pos.core.designsystem.SignaturePad(
                                        modifier = Modifier.fillMaxSize(),
                                        clearTrigger = clearTrigger,
                                        onSignatureChange = { signatureBitmap = it }
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                                    OutlinedButton(
                                        onClick = { clearTrigger++ },
                                        border = BorderStroke(1.dp, PrimaryGold),
                                        shape = KhanaRadii.xl
                                    ) { Text("Clear", color = PrimaryGold) }
                                    Button(
                                        onClick = { viewModel.signAndUpload(signerName, signatureBitmap) },
                                        enabled = !isSubmitting && consentGiven && !status.currentTerms.isNullOrBlank(),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                                        shape = KhanaRadii.xl
                                    ) {
                                        if (isSubmitting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = DarkBrown1
                                            )
                                        } else {
                                            Text("Review & Sign", color = DarkBrown1)
                                        }
                                    }
                                }
                                if (isSubmitting) {
                                    LinearProgressIndicator(color = PrimaryGold, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(epochMillis))

private suspend fun openAgreementPdf(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open Agreement PDF"))
    } catch (e: Exception) {
        KhanaToast.show("Unable to open PDF: ${e.message}", ToastKind.Error)
    }
}
