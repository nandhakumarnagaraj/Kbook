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
import com.khanabook.lite.pos.ui.designsystem.SignaturePad
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.viewmodel.AgreementEvent
import com.khanabook.lite.pos.ui.viewmodel.AgreementUiState
import com.khanabook.lite.pos.ui.viewmodel.MerchantAgreementViewModel
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Merchant Agreement") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is AgreementUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is AgreementUiState.Error -> {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.load() }) { Text("Retry") }
                    }
                }
                is AgreementUiState.Ready -> {
                    val status = state.status
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (status.hasAgreement) Icons.Filled.CheckCircle else Icons.Filled.Description,
                                        contentDescription = null,
                                        tint = if (status.hasAgreement) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (status.hasAgreement) "Agreement Signed" else "Agreement Not Signed",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (status.hasAgreement) {
                                    status.signerName?.let { Text("Signed by: $it") }
                                    status.agreementVersion?.let { Text("Version: $it") }
                                    status.signedAt?.let { Text("Signed on: ${formatDate(it)}") }
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.downloadAndOpen() },
                                        enabled = !isSubmitting
                                    ) { Text("View Agreement") }
                                }
                            }
                        }

                        if (!isPrimaryDevice) {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Read-only on this device", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Signing is only available on the primary device that " +
                                            "activated this restaurant. You can still view the signed " +
                                            "agreement above from any device."
                                    )
                                }
                            }
                        }

                        if (isPrimaryDevice && !status.hasAgreement) {
                            Text(
                                "Sign the agreement",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = signerName,
                                onValueChange = { signerName = it },
                                label = { Text("Signer name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Signature", style = MaterialTheme.typography.labelLarge)
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                SignaturePad(
                                    modifier = Modifier.fillMaxSize(),
                                    clearTrigger = clearTrigger,
                                    onSignatureChange = { signatureBitmap = it }
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { clearTrigger++ }) { Text("Clear") }
                                Button(
                                    onClick = { viewModel.signAndUpload(signerName, signatureBitmap) },
                                    enabled = !isSubmitting
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(
                                            Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Review & Sign")
                                    }
                                }
                            }
                            if (isSubmitting) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
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
