package com.khanabook.lite.pos.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.domain.util.PdfOpener
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.theme.DangerRed
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.viewmodel.DeviceSessionViewModel
import com.khanabook.lite.pos.ui.viewmodel.EasebuzzOnboardingViewModel
import com.khanabook.lite.pos.ui.viewmodel.OnboardingEvent
import com.khanabook.lite.pos.ui.viewmodel.OnboardingUiState
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ComplianceDocumentsScreen(
    onBack: () -> Unit,
    onOpenAgreement: () -> Unit
) {
    val context = LocalContext.current
    val toastScope = rememberCoroutineScope()
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout

    val vm: EasebuzzOnboardingViewModel = hiltViewModel()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val deviceVm: DeviceSessionViewModel = hiltViewModel()

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val isSubmitting by vm.isSubmitting.collectAsStateWithLifecycle()
    val profile by settingsVm.profile.collectAsStateWithLifecycle()
    val isPrimaryDevice by deviceVm.isPrimaryDevice.collectAsStateWithLifecycle()
    val events = vm.events

    val status = when (val state = uiState) {
        is OnboardingUiState.Active -> state.status
        is OnboardingUiState.AwaitingKyc -> state.status
        is OnboardingUiState.Rejected -> state.status
        else -> null
    }

    var pendingDocType by remember { mutableStateOf<String?>(null) }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val docType = pendingDocType ?: return@rememberLauncherForActivityResult
        uri?.let {
            val file = copyUriToFile(context, it, "kyc_${docType}.pdf")
            if (file != null) {
                vm.uploadKycDocument(docType, file)
            } else {
                toastScope.launch { KhanaToast.show("Could not read selected file", ToastKind.Error) }
            }
        }
        pendingDocType = null
    }

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is OnboardingEvent.Toast -> {
                    toastScope.launch { KhanaToast.show(event.message, if (event.isError) ToastKind.Error else ToastKind.Success) }
                }
                is OnboardingEvent.OpenFile -> PdfOpener.open(context, event.file)
                else -> {}
            }
        }
    }

    Column(
        modifier = Modifier
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
            val fssai = profile?.fssaiNumber
            ComplianceCard(title = "FSSAI License") {
                if (fssai.isNullOrBlank()) {
                    Text("FSSAI license number is mandatory and not yet provided. Add it in Shop Profile.", color = DangerRed, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                        Text("FSSAI No: $fssai", color = TextLight, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!profile?.fssaiExpiryDate.isNullOrBlank()) {
                        Spacer(Modifier.height(spacing.extraSmall))
                        Text("Expiry: ${profile?.fssaiExpiryDate}", color = TextLight, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(spacing.medium))

            Text("Address Proofs (Easebuzz requires 2)", color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(spacing.medium))

            AddressProofCard(
                title = "Address Proof 1",
                isUploaded = status?.businessProof1Present ?: false,
                isPrimaryDevice = isPrimaryDevice,
                isSubmitting = isSubmitting,
                onUpload = { pendingDocType = "business_proof_1"; fileLauncher.launch("application/pdf") },
                onView = { vm.downloadKycDocument("business_proof_1") }
            )
            Spacer(Modifier.height(spacing.medium))
            AddressProofCard(
                title = "Address Proof 2",
                isUploaded = status?.businessProof2Present ?: false,
                isPrimaryDevice = isPrimaryDevice,
                isSubmitting = isSubmitting,
                onUpload = { pendingDocType = "business_proof_2"; fileLauncher.launch("application/pdf") },
                onView = { vm.downloadKycDocument("business_proof_2") }
            )

            Spacer(Modifier.height(spacing.large))

            OutlinedButton(
                onClick = onOpenAgreement,
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryGold),
                shape = com.khanabook.lite.pos.ui.theme.KhanaRadii.xl,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Description, contentDescription = null, tint = PrimaryGold)
                Spacer(Modifier.size(spacing.extraSmall))
                Text("View / Sign Merchant e-Agreement", color = PrimaryGold)
            }

if (!isPrimaryDevice) {
                Spacer(Modifier.height(spacing.medium))
                Text("Document uploads are only allowed on the primary device.", color = TextLight, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ComplianceCard(title: String, content: @Composable () -> Unit) {
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(spacing.medium)
    ) {
        Text(title, color = PrimaryGold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(spacing.small))
        content()
    }
}

@Composable
private fun AddressProofCard(
    title: String,
    isUploaded: Boolean,
    isPrimaryDevice: Boolean,
    isSubmitting: Boolean,
    onUpload: () -> Unit,
    onView: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.04f)
        ),
        shape = com.khanabook.lite.pos.ui.theme.KhanaRadii.lg
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                    Icon(
                        if (isUploaded) Icons.Default.CheckCircle else Icons.Default.UploadFile,
                        contentDescription = null,
                        tint = if (isUploaded) SuccessGreen else TextLight,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(title, color = TextGold, style = MaterialTheme.typography.titleSmall)
                }
                Text(if (isUploaded) "Uploaded" else "Pending", color = if (isUploaded) SuccessGreen else DangerRed, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(spacing.small))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                Button(
                    onClick = onUpload,
                    enabled = isPrimaryDevice && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                    shape = com.khanabook.lite.pos.ui.theme.KhanaRadii.xl
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBrown1, strokeWidth = 2.dp)
                    } else {
                        Text("Upload", color = DarkBrown1)
                    }
                }
                OutlinedButton(
                    onClick = onView,
                    enabled = isUploaded,
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryGold),
                    shape = com.khanabook.lite.pos.ui.theme.KhanaRadii.xl
                ) {
                    Text("View", color = PrimaryGold)
                }
            }
        }
    }
}

private fun copyUriToFile(context: Context, uri: Uri, fileName: String): File? = try {
    val target = File(context.cacheDir, fileName)
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { out -> input.copyTo(out) }
    }
    target
} catch (e: Exception) {
    null
}
