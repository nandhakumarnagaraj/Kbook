package com.khanabook.lite.pos.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.data.repository.EasebuzzPaymentRepository
import com.khanabook.lite.pos.data.remote.dto.EasebuzzSubMerchantStatusResponse
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import kotlinx.coroutines.launch

@Composable
fun EasebuzzKycScreen(
    paymentRepository: EasebuzzPaymentRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(layout.contentPadding)
    ) {
        Spacer(modifier = Modifier.height(spacing.medium))

        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(spacing.large)) {
                Text(
                    text = "Easebuzz KYC Status",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(spacing.large))

                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryGold)
                    }
                } else if (subMerchantStatus != null) {
                    val status = subMerchantStatus!!
                    KycTimeline(status = status, spacing = spacing)

                    Spacer(modifier = Modifier.height(spacing.large))

                    if (status.kycUrl != null) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(status.kycUrl))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text(
                                "Complete KYC",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.small))
                    }

                    if (status.status.lowercase() == "pending kyc" || status.status.lowercase() == "rejected") {
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
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            border = BorderStroke(1.dp, TextGold),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text("Refresh Status", color = TextGold)
                        }
                    }
                } else if (errorMessage != null) {
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
                        border = BorderStroke(1.dp, PrimaryGold),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Retry", color = PrimaryGold)
                    }
                }

                Spacer(modifier = Modifier.height(spacing.large))

                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    border = BorderStroke(1.dp, TextGold),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Back", color = TextGold)
                }
            }
        }
    }
}

@Composable
fun KycTimeline(
    status: EasebuzzSubMerchantStatusResponse,
    spacing: com.khanabook.lite.pos.ui.theme.Spacing
) {
    val isSubmitted = status.kycSubmissionDate != null
    val isActive = status.status.lowercase() == "active"
    val isRejected = status.status.lowercase() == "rejected"
    val isSuspended = status.status.lowercase() == "suspended"
    
    val steps = listOf(
        TimelineStep(
            title = "Account Setup",
            description = "Merchant account registered",
            state = StepState.COMPLETED,
            date = null
        ),
        TimelineStep(
            title = "KYC Document Verification",
            description = when {
                isRejected -> "Documents rejected. Please re-submit."
                isSubmitted -> "Documents successfully uploaded"
                else -> "Upload ID and Bank proof to proceed"
            },
            state = when {
                isRejected -> StepState.ERROR
                isSubmitted -> StepState.COMPLETED
                else -> StepState.PENDING
            },
            date = status.kycSubmissionDate
        ),
        TimelineStep(
            title = "Settlement Activation",
            description = when {
                isSuspended -> "Settlements temporarily suspended"
                isActive -> "Account active — splits & settlements enabled"
                else -> "Awaiting compliance review"
            },
            state = when {
                isSuspended -> StepState.ERROR
                isActive -> StepState.COMPLETED
                else -> StepState.PENDING
            },
            date = status.activationDate
        )
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        steps.forEachIndexed { index, step ->
            KycTimelineItem(
                step = step,
                isLast = index == steps.size - 1,
                spacing = spacing
            )
        }
    }
}

enum class StepState {
    COMPLETED, PENDING, ERROR
}

data class TimelineStep(
    val title: String,
    val description: String,
    val state: StepState,
    val date: String?
)

@Composable
fun KycTimelineItem(
    step: TimelineStep,
    isLast: Boolean,
    spacing: com.khanabook.lite.pos.ui.theme.Spacing
) {
    val circleColor = when (step.state) {
        StepState.COMPLETED -> SuccessGreen
        StepState.ERROR -> MaterialTheme.colorScheme.error
        StepState.PENDING -> TextGold.copy(alpha = 0.35f)
    }
    
    val icon = when (step.state) {
        StepState.COMPLETED -> Icons.Default.Check
        StepState.ERROR -> Icons.Default.Close
        StepState.PENDING -> Icons.Default.Circle
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        // Timeline Indicator Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(circleColor.copy(alpha = 0.15f))
                    .border(1.5.dp, circleColor, CircleShape)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = circleColor,
                    modifier = Modifier.size(if (step.state == StepState.PENDING) 8.dp else 14.dp)
                )
            }
            
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(36.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(circleColor, TextGold.copy(alpha = 0.15f))
                            )
                        )
                )
            }
        }
        
        // Content Column
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else spacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = step.title,
                    color = if (step.state == StepState.PENDING) TextGold.copy(alpha = 0.6f) else TextLight,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (step.date != null) {
                    Text(
                        text = step.date,
                        color = TextGold.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = step.description,
                color = if (step.state == StepState.PENDING) TextGold.copy(alpha = 0.45f) else TextGold,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
