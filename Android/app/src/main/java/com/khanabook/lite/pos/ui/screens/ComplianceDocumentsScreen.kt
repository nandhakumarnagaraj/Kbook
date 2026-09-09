package com.khanabook.lite.pos.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.data.remote.dto.AddressProofType
import com.khanabook.lite.pos.domain.util.PdfOpener
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.theme.*
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

    val vm: EasebuzzOnboardingViewModel = hiltViewModel()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val deviceVm: DeviceSessionViewModel = hiltViewModel()

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val isSubmitting by vm.isSubmitting.collectAsStateWithLifecycle()
    val profile by settingsVm.profile.collectAsStateWithLifecycle()
    val canUpload = deviceVm.canUploadDocuments
    val events = vm.events

    val status = when (val state = uiState) {
        is OnboardingUiState.Active -> state.status
        is OnboardingUiState.AwaitingKyc -> state.status
        is OnboardingUiState.Rejected -> state.status
        else -> null
    }

    var pendingDocType by remember { mutableStateOf<String?>(null) }
    var pendingProofType by remember { mutableStateOf<String?>(null) }

    var selectedProof1Type by remember {
        mutableStateOf(
            AddressProofType.fromCode(status?.businessProof1Type) ?: AddressProofType.ELECTRICITY_BILL
        )
    }
    var selectedProof2Type by remember {
        mutableStateOf(
            AddressProofType.fromCode(status?.businessProof2Type) ?: AddressProofType.GST_CERTIFICATE
        )
    }

    // Keep selected types updated when status loads
    LaunchedEffect(status?.businessProof1Type, status?.businessProof2Type) {
        status?.businessProof1Type?.let { code ->
            AddressProofType.fromCode(code)?.let { selectedProof1Type = it }
        }
        status?.businessProof2Type?.let { code ->
            AddressProofType.fromCode(code)?.let { selectedProof2Type = it }
        }
    }

    val isDuplicateTypeError = (selectedProof1Type == selectedProof2Type) && (status?.businessProof2Present == true)

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val docType = pendingDocType ?: return@rememberLauncherForActivityResult
        val proofType = pendingProofType
        uri?.let {
            val file = copyUriToFile(context, it, "kyc_${docType}_${System.currentTimeMillis()}")
            if (file != null) {
                vm.uploadKycDocument(docType, file, proofType)
            } else {
                toastScope.launch { KhanaToast.show("Could not read selected file", ToastKind.Error) }
            }
        }
        pendingDocType = null
        pendingProofType = null
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
            // ── 1. Shop Details & CPV Alignment Card ─────────────────────
            ShopDetailsVerificationCard(
                tradeName = profile?.shopName ?: status?.tradeName ?: "Not configured",
                legalName = status?.legalEntityName ?: profile?.shopName ?: "Not configured",
                shopAddress = profile?.shopAddress ?: status?.businessAddress ?: "Not configured",
                spacing = spacing
            )

            // ── 2. FSSAI License Card ──────────────────────────────────
            val fssai = profile?.fssaiNumber ?: status?.fssaiNumber
            ComplianceCard(title = "FSSAI License (Mandatory for Food Merchants)") {
                if (fssai.isNullOrBlank()) {
                    Text(
                        "FSSAI license number is mandatory. Easebuzz requires an active license before activation.",
                        color = DangerRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("FSSAI No: $fssai", color = TextLight, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!profile?.fssaiExpiryDate.isNullOrBlank()) {
                        Spacer(Modifier.height(spacing.extraSmall))
                        Text("Expiry: ${profile?.fssaiExpiryDate}", color = TextLight, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(spacing.small))

            // ── 3. Address Proofs Section Header ────────────────────────
            Text(
                "Business Address Proof (At least 1 Document Required)",
                color = PrimaryGold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Upload clear photos or PDFs. Any 1 valid document (Electricity Bill, GST, Shop Act, Trade License, Udyam, or Rent Agreement) is accepted for CPV. You may optionally upload a 2nd distinct document.",
                color = TextGold.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall
            )

            if (isDuplicateTypeError) {
                Surface(
                    color = DangerRed.copy(alpha = 0.15f),
                    shape = KhanaRadii.card,
                    border = BorderStroke(1.dp, DangerRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(spacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = DangerRed)
                        Text(
                            "Proof 1 and Proof 2 cannot be the same document type if you choose to upload a second proof.",
                            color = DangerRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // ── 4. Address Proof 1 ──────────────────────────────────────
            AddressProofCard(
                title = "Address Proof 1 (Mandatory)",
                selectedType = selectedProof1Type,
                onTypeSelected = { selectedProof1Type = it },
                uploadedType = status?.businessProof1Type,
                isUploaded = status?.businessProof1Present ?: false,
                canView = status?.businessProof1DownloadPath != null,
                isAllowed = canUpload,
                isSubmitting = isSubmitting,
                onUpload = {
                    pendingDocType = "business_proof_1"
                    pendingProofType = selectedProof1Type.code
                    fileLauncher.launch("*/*")
                },
                onView = { vm.downloadKycDocument("business_proof_1") },
                spacing = spacing
            )

            // ── 5. Address Proof 2 ──────────────────────────────────────
            AddressProofCard(
                title = "Address Proof 2 (Optional)",
                selectedType = selectedProof2Type,
                onTypeSelected = { selectedProof2Type = it },
                uploadedType = status?.businessProof2Type,
                isUploaded = status?.businessProof2Present ?: false,
                canView = status?.businessProof2DownloadPath != null,
                isAllowed = canUpload && (selectedProof1Type != selectedProof2Type),
                isSubmitting = isSubmitting,
                onUpload = {
                    pendingDocType = "business_proof_2"
                    pendingProofType = selectedProof2Type.code
                    fileLauncher.launch("*/*")
                },
                onView = { vm.downloadKycDocument("business_proof_2") },
                spacing = spacing
            )

            if (selectedProof1Type == selectedProof2Type && !(status?.businessProof2Present ?: false)) {
                Text(
                    "To upload a second proof, select a document type distinct from Proof 1.",
                    color = TextGold.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = spacing.extraSmall)
                )
            }

            Spacer(Modifier.height(spacing.medium))

            // ── 6. CPV Physical Inspection Guide Card ───────────────────
            CpvChecklistCard(spacing = spacing)

            Spacer(Modifier.height(spacing.medium))

            // ── 7. Merchant Agreement ──────────────────────────────────
            OutlinedButton(
                onClick = onOpenAgreement,
                border = BorderStroke(1.dp, PrimaryGold),
                shape = KhanaRadii.xl,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Description, contentDescription = null, tint = PrimaryGold)
                Spacer(Modifier.size(spacing.extraSmall))
                Text("View / Sign Merchant e-Agreement", color = PrimaryGold)
            }

            if (!canUpload) {
                Spacer(Modifier.height(spacing.small))
                Text(
                    "Document uploads are restricted to restaurant owners.",
                    color = TextLight.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(spacing.large))
        }
    }
}

@Composable
private fun ShopDetailsVerificationCard(
    tradeName: String,
    legalName: String,
    shopAddress: String,
    spacing: Spacing
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkBrown2),
        shape = KhanaRadii.card,
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(spacing.medium)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Icon(Icons.Default.Storefront, contentDescription = null, tint = PrimaryGold)
                Text(
                    "Shop Profile (Verification Reference)",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(spacing.small))
            Text(
                "Trade Name: $tradeName",
                color = TextLight,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Legal Entity Name: $legalName",
                color = TextGold,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Shop Address: $shopAddress",
                color = TextLight.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(spacing.small))
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = TextGold.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Text(
                    "The business name and physical address printed on your uploaded proofs MUST match these details to pass Easebuzz physical CPV verification.",
                    color = TextGold.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun ComplianceCard(title: String, content: @Composable () -> Unit) {
    val spacing = KhanaBookTheme.spacing
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
        shape = KhanaRadii.card
    ) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressProofCard(
    title: String,
    selectedType: AddressProofType,
    onTypeSelected: (AddressProofType) -> Unit,
    uploadedType: String?,
    isUploaded: Boolean,
    canView: Boolean,
    isAllowed: Boolean,
    isSubmitting: Boolean,
    onUpload: () -> Unit,
    onView: () -> Unit,
    spacing: Spacing
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
        shape = KhanaRadii.lg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    Icon(
                        if (isUploaded) Icons.Default.CheckCircle else Icons.Default.UploadFile,
                        contentDescription = null,
                        tint = if (isUploaded) SuccessGreen else TextLight,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(title, color = TextGold, style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    if (isUploaded) "Uploaded" else "Pending",
                    color = if (isUploaded) SuccessGreen else DangerRed,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(spacing.small))

            // Document type dropdown selector
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedType.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Document Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGold,
                        unfocusedBorderColor = BorderGold,
                        focusedLabelColor = PrimaryGold,
                        unfocusedLabelColor = TextGold.copy(alpha = 0.6f),
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    containerColor = DarkBrown2
                ) {
                    AddressProofType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(type.label, color = TextLight, style = MaterialTheme.typography.bodyMedium)
                                    Text(type.description, color = TextGold.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            onClick = {
                                onTypeSelected(type)
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            if (uploadedType != null && isUploaded) {
                Spacer(Modifier.height(spacing.extraSmall))
                val displayUploaded = AddressProofType.fromCode(uploadedType)?.label ?: uploadedType
                Text(
                    "Current active: $displayUploaded",
                    color = SuccessGreen,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.height(spacing.medium))

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                Button(
                    onClick = onUpload,
                    enabled = isAllowed && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                    shape = KhanaRadii.xl
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = DarkBrown1,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Upload (PDF/Image)", color = DarkBrown1)
                    }
                }
                OutlinedButton(
                    onClick = onView,
                    enabled = isUploaded && canView,
                    border = BorderStroke(1.dp, PrimaryGold),
                    shape = KhanaRadii.xl
                ) {
                    Text("View", color = PrimaryGold)
                }
            }
        }
    }
}

@Composable
private fun CpvChecklistCard(spacing: Spacing) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkBrown2.copy(alpha = 0.7f)),
        shape = KhanaRadii.card,
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(spacing.medium)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = PrimaryGold)
                Text(
                    "Contact Point Verification (CPV) Guide",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(spacing.small))
            Text(
                "Easebuzz uses fast agentless CPV (self-service via secure link) for most merchants. For physical visits (high-risk or discrepancy cases), prepare:",
                color = TextGold.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(spacing.small))
            CpvChecklistItem("1. Storefront Signage: Ensure outlet name board displays your Trade Name.")
            CpvChecklistItem("2. Counter Originals: Keep printed copies of uploaded FSSAI & address proofs at the desk.")
            CpvChecklistItem("3. Operational Kitchen: Counter, menu card, and food service setup must be visible.")
            CpvChecklistItem("4. GPS Match: Field check or self-service photo capture is executed inside the registered shop premises.")
        }
    }
}

@Composable
private fun CpvChecklistItem(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("•", color = PrimaryGold, style = MaterialTheme.typography.bodySmall)
        Text(text, color = TextLight, style = MaterialTheme.typography.bodySmall)
    }
}

private fun copyUriToFile(context: Context, uri: Uri, baseName: String): File? = try {
    val mime = context.contentResolver.getType(uri) ?: ""
    val ext = when {
        mime.contains("pdf") -> ".pdf"
        mime.contains("png") -> ".png"
        mime.contains("jpeg") || mime.contains("jpg") -> ".jpg"
        mime.contains("webp") -> ".webp"
        else -> {
            val lastPath = uri.lastPathSegment?.lowercase() ?: ""
            if (lastPath.endsWith(".png")) ".png"
            else if (lastPath.endsWith(".jpg") || lastPath.endsWith(".jpeg")) ".jpg"
            else if (lastPath.endsWith(".webp")) ".webp"
            else ".pdf"
        }
    }
    val target = File(context.cacheDir, "$baseName$ext")
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { out -> input.copyTo(out) }
    }
    target
} catch (e: Exception) {
    null
}
