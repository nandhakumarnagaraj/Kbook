package com.khanabook.lite.pos.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
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
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.designsystem.SignaturePad
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.DangerRed
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.KhanaRadii
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.viewmodel.AgreementEvent
import com.khanabook.lite.pos.ui.viewmodel.AgreementUiState
import com.khanabook.lite.pos.ui.viewmodel.MerchantAgreementViewModel
import androidx.compose.material3.ButtonDefaults

import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material3.OutlinedTextFieldDefaults
import com.khanabook.lite.pos.ui.theme.DarkBrown2
import com.khanabook.lite.pos.ui.theme.ErrorPink

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
    val isPrimaryDevice by viewModel.isPrimaryDevice.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var signerName by remember { mutableStateOf("") }
    var signatureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var clearTrigger by remember { mutableStateOf(0) }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = spacing.medium)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.large),
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(spacing.medium)
                    ) {
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
                                        if (status.hasAgreement) Icons.Filled.CheckCircle else Icons.Filled.Description,
                                        contentDescription = null,
                                        tint = if (status.hasAgreement) SuccessGreen else PrimaryGold,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(spacing.small))
                                    Text(
                                        if (status.hasAgreement) "Agreement Signed" else "Agreement Not Signed",
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

                        if (!isPrimaryDevice) {
                            KhanaBookCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = KhanaRadii.lg,
                                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.1f))
                            ) {
                                Column(modifier = Modifier.padding(spacing.medium)) {
                                    Text("Read-only on this device", fontWeight = FontWeight.Bold, color = DangerRed)
                                    Spacer(modifier = Modifier.height(spacing.small))
                                    Text(
                                        "Signing is only available on the primary device that " +
                                            "activated this restaurant. You can still view the signed " +
                                            "agreement above from any device.",
                                        color = TextGold
                                    )
                                }
                            }
                        }

                        if (isPrimaryDevice && !status.hasAgreement) {
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
                                        com.khanabook.lite.pos.ui.designsystem.SignaturePad(
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
                                            enabled = !isSubmitting,
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

    SnackbarHost(hostState = snackbarHostState)
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
