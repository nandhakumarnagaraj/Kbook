package com.khanabook.lite.pos.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.data.repository.EasebuzzPaymentRepository
import com.khanabook.lite.pos.data.remote.dto.EasebuzzSubMerchantStatusResponse
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.theme.KbBrandSaffron
import com.khanabook.lite.pos.ui.theme.KbBrandVioletBright
import com.khanabook.lite.pos.ui.theme.KbMidnightGradient
import com.khanabook.lite.pos.ui.theme.KbGray300
import com.khanabook.lite.pos.ui.theme.KbGray500
import com.khanabook.lite.pos.ui.theme.KbOpacity
import com.khanabook.lite.pos.ui.theme.KbShape
import com.khanabook.lite.pos.ui.theme.KbSuccess
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.kbBgCard
import com.khanabook.lite.pos.ui.theme.kbBgGradient
import com.khanabook.lite.pos.ui.theme.kbOutlineSubtle
import com.khanabook.lite.pos.ui.theme.kbPrimary
import com.khanabook.lite.pos.ui.theme.kbSecondary
import com.khanabook.lite.pos.ui.theme.kbTertiary
import com.khanabook.lite.pos.ui.theme.kbTextOnBrand
import com.khanabook.lite.pos.ui.theme.kbTextPrimary
import com.khanabook.lite.pos.ui.theme.kbTextSecondary
import com.khanabook.lite.pos.ui.theme.kbTextTertiary

import kotlinx.coroutines.launch

private enum class KycStepState { COMPLETED, ACTIVE, PENDING, FAILED }

private data class KycStep(
    val number: Int,
    val label: String,
    val state: KycStepState
)

@Composable
fun EasebuzzKycScreen(
    paymentRepository: EasebuzzPaymentRepository,
    onBack: () -> Unit,
    onStartOnboarding: () -> Unit = {},
    onResubmit: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spacing = KhanaBookTheme.spacing

    var isLoading by remember { mutableStateOf(true) }
    var subMerchantStatus by remember { mutableStateOf<EasebuzzSubMerchantStatusResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Owner KYC actions (portal + OTP)
    var otp by remember { mutableStateOf("") }
    var actionBusy by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var uploadType by remember { mutableStateOf<String?>(null) }

    suspend fun refreshStatus() {
        try {
            isLoading = true
            subMerchantStatus = paymentRepository.getSubMerchantStatus()
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to load KYC status"
        } finally {
            isLoading = false
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && uploadType != null) {
            scope.launch {
                try {
                    actionBusy = true
                    actionMessage = "Uploading document..."
                    paymentRepository.uploadKycDocument(uploadType!!, uri, context)
                    actionMessage = "Document uploaded successfully!"
                    refreshStatus()
                } catch (e: Exception) {
                    actionMessage = "Upload failed: ${e.message}"
                } finally {
                    actionBusy = false
                    uploadType = null
                }
            }
        }
    }

    fun openKycPortal(existingUrl: String?) {
        scope.launch {
            try {
                actionBusy = true
                actionMessage = null
                val url = existingUrl?.takeIf { it.isNotBlank() }
                    ?: paymentRepository.generateKycAccessKey().kycUrl
                if (url.isNullOrBlank()) {
                    actionMessage = "Could not get a KYC link. Try Refresh and retry."
                } else {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            } catch (e: Exception) {
                actionMessage = e.message ?: "Failed to open KYC portal"
            } finally {
                actionBusy = false
            }
        }
    }

    LaunchedEffect(Unit) { refreshStatus() }

    val steps = remember(subMerchantStatus) {
        val s = subMerchantStatus
        val hasMerchant = s?.hasSubMerchant == true
        val kycDone = s?.kycSubmissionDate != null
        val active = s?.isActive == true

        listOf(
            KycStep(1, "Identity\nProof", if (hasMerchant) KycStepState.COMPLETED else KycStepState.ACTIVE),
            KycStep(2, "Address\nProof", when {
                kycDone -> KycStepState.COMPLETED
                hasMerchant -> KycStepState.ACTIVE
                else -> KycStepState.PENDING
            }),
            KycStep(3, "Bank\nDetails", when {
                active -> KycStepState.COMPLETED
                kycDone -> KycStepState.ACTIVE
                else -> KycStepState.PENDING
            }),
            KycStep(4, "Submission", if (active) KycStepState.COMPLETED else KycStepState.PENDING)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KbMidnightGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.medium)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.khanabook.lite.pos.ui.designsystem.KhanaBookBackButton(onClick = onBack)
                Text(
                    text = "KYC Verification",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Complete your KYC to start receiving payments",
                style = MaterialTheme.typography.bodyMedium,
                color = KbBrandSaffron // Saffron accent matching brand palette
            )

            Spacer(modifier = Modifier.height(spacing.mediumLarge))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.kbPrimary)
                }
            } else if (subMerchantStatus != null) {
                val status = subMerchantStatus!!

                KycHorizontalStepper(
                    steps = steps,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(spacing.mediumLarge))

                val state = status.status.uppercase().replace(" ", "_")

                KhanaBookCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
                    shape = KbShape.Large
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Text(
                            text = "Status: " + kycStatusLabel(state),
                            color = MaterialTheme.kbTextPrimary,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (!status.subMerchantId.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Sub-merchant ID: ${status.subMerchantId}",
                                color = MaterialTheme.kbTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = kycStatusHint(state),
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.mediumLarge))

                Text(
                    text = "Upload KYC Documents to Server",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(vertical = spacing.small)
                )

                val isEditable = !status.isActive
                
                KycDocUploadCard(
                    label = "Identity Proof (PAN / Aadhaar)",
                    url = status.idProofUrl,
                    onSelectFile = {
                        uploadType = "id_proof"
                        documentPickerLauncher.launch("*/*")
                    },
                    busy = actionBusy,
                    isEditable = isEditable
                )
                Spacer(modifier = Modifier.height(spacing.small))
                KycDocUploadCard(
                    label = "Bank Proof (Cancelled Cheque / Statement)",
                    url = status.bankProofUrl,
                    onSelectFile = {
                        uploadType = "bank_proof"
                        documentPickerLauncher.launch("*/*")
                    },
                    busy = actionBusy,
                    isEditable = isEditable
                )
                Spacer(modifier = Modifier.height(spacing.small))
                KycDocUploadCard(
                    label = "Address Proof 1 (Proprietorship / Entity proof)",
                    url = status.businessProof1Url,
                    onSelectFile = {
                        uploadType = "business_proof_1"
                        documentPickerLauncher.launch("*/*")
                    },
                    busy = actionBusy,
                    isEditable = isEditable
                )
                Spacer(modifier = Modifier.height(spacing.small))
                KycDocUploadCard(
                    label = "Address Proof 2 (GST / FSSAI / Other proof)",
                    url = status.businessProof2Url,
                    onSelectFile = {
                        uploadType = "business_proof_2"
                        documentPickerLauncher.launch("*/*")
                    },
                    busy = actionBusy,
                    isEditable = isEditable
                )

                Spacer(modifier = Modifier.height(spacing.mediumLarge))

                actionMessage?.let {
                    Text(it, color = MaterialTheme.kbSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(spacing.small))
                }

                when {
                    !status.hasSubMerchant || state == "NOT_REGISTERED" || state == "NOT_STARTED" ||
                        state == "DRAFT" || state == "FAILED" -> {
                        BrandButton("Start Onboarding", onClick = onStartOnboarding)
                    }

                    status.isActive || state == "ACTIVE" -> {
                        Text(
                            "Your KYC is verified — online payments are active.",
                            color = KbSuccess,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    state == "REJECTED" -> {
                        BrandButton("Update & Resubmit Details", onClick = onResubmit)
                        Spacer(Modifier.height(spacing.small))
                        OutlinedButton(
                            onClick = { openKycPortal(status.kycUrl) },
                            enabled = !actionBusy,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = KbShape.Medium
                        ) { Text("Open KYC Portal", color = MaterialTheme.kbSecondary) }
                    }

                    state == "KYC_SUBMITTED" -> {
                        Text(
                            "Documents submitted. Easebuzz is reviewing — this can take a little while.",
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    else -> { // PENDING_KYC
                        BrandButton(
                            if (actionBusy) "Opening…" else "Open KYC Portal",
                            enabled = !actionBusy,
                            onClick = { openKycPortal(status.kycUrl) }
                        )
                        Spacer(Modifier.height(spacing.medium))
                        // OTP step — LIVE only (Easebuzz sandbox does not issue onboarding OTPs)
                        com.khanabook.lite.pos.ui.designsystem.KhanaBookInputField(
                            value = otp,
                            onValueChange = { otp = it },
                            label = "Onboarding OTP",
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )
                        Spacer(Modifier.height(spacing.small))
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            actionBusy = true; actionMessage = null
                                            val r = paymentRepository.verifyKycOtp(otp.trim())
                                            actionMessage = r.msg ?: r.error ?: "OTP submitted"
                                            refreshStatus()
                                        } catch (e: Exception) {
                                            actionMessage = e.message ?: "OTP verification failed"
                                        } finally { actionBusy = false }
                                    }
                                },
                                enabled = !actionBusy && otp.isNotBlank(),
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = KbShape.Medium,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.kbPrimary)
                            ) { Text("Verify OTP", color = MaterialTheme.kbTextOnBrand) }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            actionBusy = true; actionMessage = null
                                            val r = paymentRepository.resendKycOtp()
                                            actionMessage = r.msg ?: r.error ?: "OTP resent"
                                        } catch (e: Exception) {
                                            actionMessage = e.message ?: "Could not resend OTP"
                                        } finally { actionBusy = false }
                                    }
                                },
                                enabled = !actionBusy,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = KbShape.Medium
                            ) { Text("Resend", color = MaterialTheme.kbSecondary) }
                        }
                    }
                }

                if (!status.isActive) {
                    Spacer(modifier = Modifier.height(spacing.small))
                    OutlinedButton(
                        onClick = { scope.launch { refreshStatus() } },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = KbShape.Medium
                    ) { Text("Refresh Status", color = MaterialTheme.kbSecondary) }
                }

                Spacer(modifier = Modifier.height(spacing.medium))

                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = KbShape.Medium
                ) {
                    Text("Back", color = MaterialTheme.kbSecondary)
                }
            } else if (errorMessage != null) {
                KhanaBookCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(spacing.large)) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(spacing.medium))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    try {
                                        subMerchantStatus = paymentRepository.getSubMerchantStatus()
                                    } catch (e: Exception) {
                                        errorMessage = e.message
                                    }
                                    isLoading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = KbShape.Medium
                        ) {
                            Text("Retry", color = MaterialTheme.kbTertiary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.large))
        }
    }
}

@Composable
private fun KycHorizontalStepper(
    steps: List<KycStep>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            steps.forEachIndexed { index, step ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val circleSize = when (step.state) {
                            KycStepState.ACTIVE -> 36.dp
                            else -> 32.dp
                        }
                        val circleColor = when (step.state) {
                            KycStepState.COMPLETED -> Color.Transparent
                            KycStepState.ACTIVE -> KbBrandSaffron.copy(alpha = 0.1f)
                            KycStepState.PENDING -> Color.Transparent
                            KycStepState.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        }
                        Box(
                            modifier = Modifier
                                .size(circleSize)
                                .clip(CircleShape)
                                .background(circleColor)
                                .then(
                                    if (step.state == KycStepState.PENDING) {
                                        Modifier
                                            .background(Color.Transparent)
                                            .clip(CircleShape)
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when (step.state) {
                                KycStepState.COMPLETED -> Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = "Completed",
                                    tint = KbBrandSaffron,
                                    modifier = Modifier.size(24.dp)
                                )
                                KycStepState.ACTIVE -> CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = KbBrandSaffron,
                                    strokeWidth = 2.dp
                                )
                                KycStepState.PENDING -> Icon(
                                    Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = "Pending",
                                    tint = MaterialTheme.kbOutlineSubtle,
                                    modifier = Modifier.size(20.dp)
                                )
                                KycStepState.FAILED -> Icon(
                                    Icons.Rounded.Cancel,
                                    contentDescription = "Failed",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = step.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (step.state) {
                            KycStepState.COMPLETED -> KbSuccess
                            KycStepState.ACTIVE -> KbBrandSaffron
                            KycStepState.PENDING -> MaterialTheme.kbTextTertiary
                            KycStepState.FAILED -> MaterialTheme.colorScheme.error
                        },
                        fontWeight = if (step.state == KycStepState.ACTIVE) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        lineHeight = MaterialTheme.typography.labelSmall.lineHeight
                    )
                }

                if (index < steps.size - 1) {
                    val lineColor = when {
                        steps[index].state == KycStepState.COMPLETED && steps[index + 1].state != KycStepState.PENDING -> KbSuccess
                        steps[index].state == KycStepState.COMPLETED -> KbBrandSaffron.copy(alpha = 0.4f)
                        else -> KbGray300.copy(alpha = 0.4f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(0.6f)
                            .padding(top = 16.dp)
                            .height(2.dp)
                            .background(lineColor, RoundedCornerShape(1.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun KycDocumentUploadArea(
    stepNumber: Int,
    stepLabel: String,
    modifier: Modifier = Modifier
) {
    val borderColor = MaterialTheme.kbOutlineSubtle

    Box(
        modifier = modifier
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.kbBgCard.copy(alpha = 0.5f))
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
            )
            drawRoundRect(
                color = borderColor,
                style = stroke,
                cornerRadius = CornerRadius(12.dp.toPx()),
                size = Size(size.width, size.height)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(KbBrandSaffron.copy(alpha = KbOpacity.Selected)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = KbBrandSaffron,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Upload $stepLabel",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.kbTextPrimary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Tap to select files for step $stepNumber",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.kbTextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(KbBrandSaffron.copy(alpha = KbOpacity.StatusBg))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Select File",
                    style = MaterialTheme.typography.labelMedium,
                    color = KbBrandSaffron,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun BrandButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = KbShape.Medium,
        colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron)
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

private fun kycStatusLabel(state: String): String = when (state) {
    "ACTIVE" -> "Verified"
    "PENDING_KYC" -> "Pending KYC"
    "KYC_SUBMITTED" -> "Under review"
    "REJECTED" -> "Rejected"
    "FAILED" -> "Submission failed"
    "DRAFT" -> "Draft"
    "NOT_REGISTERED", "NOT_STARTED" -> "Not started"
    else -> state
}

private fun kycStatusHint(state: String): String = when (state) {
    "ACTIVE" -> "You can accept online payments."
    "PENDING_KYC" -> "Open the Easebuzz portal to upload your documents."
    "KYC_SUBMITTED" -> "Easebuzz is reviewing your documents."
    "REJECTED" -> "Your KYC was rejected. Update your details and resubmit."
    "FAILED" -> "Onboarding submission failed. Review details and try again."
    "DRAFT" -> "Finish onboarding to submit to Easebuzz."
    "NOT_REGISTERED", "NOT_STARTED" -> "Start onboarding to register as an Easebuzz sub-merchant."
    else -> ""
}

@Composable
private fun KycDocUploadCard(
    label: String,
    url: String?,
    onSelectFile: () -> Unit,
    busy: Boolean,
    isEditable: Boolean,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    KhanaBookCard(
        modifier = modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
        shape = KbShape.Medium
    ) {
        Row(
            modifier = Modifier.padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = MaterialTheme.kbTextPrimary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (!url.isNullOrBlank()) {
                    Text(
                        text = "Uploaded (local CDN copy)",
                        color = KbSuccess,
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    Text(
                        text = "Not uploaded",
                        color = MaterialTheme.kbTextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            
            if (!url.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.CheckCircle,
                        contentDescription = "Uploaded",
                        tint = KbSuccess,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    val context = LocalContext.current
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        modifier = Modifier.height(36.dp),
                        shape = KbShape.Small
                    ) {
                        Text("View")
                    }
                    if (isEditable) {
                        Spacer(modifier = Modifier.width(spacing.small))
                        OutlinedButton(
                            onClick = onSelectFile,
                            enabled = !busy,
                            modifier = Modifier.height(36.dp),
                            shape = KbShape.Small
                        ) {
                            Text("Replace")
                        }
                    }
                }
            } else {
                if (isEditable) {
                    Button(
                        onClick = onSelectFile,
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron),
                        shape = KbShape.Small,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(spacing.extraSmall))
                        Text("Upload", color = Color.White)
                    }
                }
            }
        }
    }
}
