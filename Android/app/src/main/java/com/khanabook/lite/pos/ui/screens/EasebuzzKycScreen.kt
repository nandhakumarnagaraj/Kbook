package com.khanabook.lite.pos.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.data.repository.EasebuzzPaymentRepository
import com.khanabook.lite.pos.data.remote.dto.EasebuzzSubMerchantStatusResponse
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.KbBrandSaffron
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spacing = KhanaBookTheme.spacing

    var isLoading by remember { mutableStateOf(true) }
    var subMerchantStatus by remember { mutableStateOf<EasebuzzSubMerchantStatusResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            subMerchantStatus = paymentRepository.getSubMerchantStatus()
            isLoading = false
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to load KYC status"
            isLoading = false
        }
    }

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

    // Midnight purple gradient background — matches Login/SignUp/Splash
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1E1035), Color(0xFF0F081D))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.medium)
        ) {
            Spacer(modifier = Modifier.height(spacing.small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
                Text(
                    text = "KYC Verification",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(spacing.small))

            Text(
                text = "Complete your KYC to start receiving payments",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFA78BFA)
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

                val activeStep = steps.indexOfFirst { it.state == KycStepState.ACTIVE }
                    .coerceAtLeast(0)

                KycDocumentUploadArea(
                    stepNumber = activeStep + 1,
                    stepLabel = steps.getOrNull(activeStep)?.label?.replace("\n", " ") ?: "Document",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(spacing.mediumLarge))

                if (status.kycUrl != null) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(status.kycUrl))
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.kbPrimary),
                        shape = KbShape.Medium
                    ) {
                        Text(
                            "Next",
                            color = MaterialTheme.kbTextOnBrand,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (status.status.lowercase() == "pending kyc" || status.status.lowercase() == "rejected") {
                    Spacer(modifier = Modifier.height(spacing.small))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    isLoading = true
                                    subMerchantStatus = paymentRepository.getSubMerchantStatus()
                                    isLoading = false
                                } catch (e: Exception) {
                                    errorMessage = e.message
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = KbShape.Medium
                    ) {
                        Text("Refresh Status", color = MaterialTheme.kbSecondary)
                    }
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
                    colors = CardDefaults.cardColors(containerColor = CardBG),
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
