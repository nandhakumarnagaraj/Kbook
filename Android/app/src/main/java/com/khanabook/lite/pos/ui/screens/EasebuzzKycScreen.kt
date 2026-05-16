package com.khanabook.lite.pos.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
                    val statusColor = when (status.status.lowercase()) {
                        "active" -> SuccessGreen
                        "pending kyc", "pending" -> PrimaryGold
                        "rejected" -> MaterialTheme.colorScheme.error
                        "suspended" -> Color(0xFFFF9800)
                        else -> TextGold
                    }

                    StatusRow(label = "Status", value = status.status, valueColor = statusColor)

                    if (status.kycSubmissionDate != null) {
                        Spacer(modifier = Modifier.height(spacing.medium))
                        StatusRow(label = "KYC Submitted", value = status.kycSubmissionDate)
                    }

                    if (status.activationDate != null) {
                        Spacer(modifier = Modifier.height(spacing.medium))
                        StatusRow(label = "Activated On", value = status.activationDate)
                    }

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
private fun StatusRow(
    label: String,
    value: String,
    valueColor: Color = TextLight
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextGold,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
