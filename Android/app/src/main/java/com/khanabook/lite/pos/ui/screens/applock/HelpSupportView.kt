@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens.applock

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.KhanaRadii
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.time.Year
import androidx.compose.foundation.BorderStroke

private const val SUPPORT_WHATSAPP = "919471676935"
private const val SUPPORT_EMAIL = "kbook@pcts.tech"

@Composable
fun HelpSupportView(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val toastScope = rememberCoroutineScope()
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val failedBills by viewModel.failedBillSyncs.collectAsStateWithLifecycle()
    val retryingIds by viewModel.retryingFailedBillIds.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshFailedBillSyncs()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.large, vertical = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.medium),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(layout.logoSize)
                    .background(PrimaryGold.copy(alpha = 0.12f), CircleShape)
                    .border(2.dp, PrimaryGold.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    tint = PrimaryGold,
                    modifier = Modifier.size(58.dp)
                )
            }
        }

        Text(
            "We're here to help you succeed",
            color = PrimaryGold,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Need help with KhanaBook POS? Reach out to our support team — we respond quickly.",
            color = TextLight.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(spacing.small))

        SyncIssuesCard(
            failedBills = failedBills,
            retryingIds = retryingIds,
            onRefresh = viewModel::refreshFailedBillSyncs,
            onRetry = viewModel::retryFailedBillSync,
            onRetryAll = viewModel::retryAllFailedBillSyncs,
            onRepair = viewModel::repairFailedBillSync
        )

        Button(
            onClick = {
                val url = "https://wa.me/$SUPPORT_WHATSAPP?text=Hi%2C%20I%20need%20help%20with%20KhanaBook%20POS"
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                    toastScope.launch {
                        KhanaToast.show("WhatsApp is not available", ToastKind.Error)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(72.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color.White),
            shape = KhanaRadii.lg,
            contentPadding = PaddingValues(horizontal = spacing.medium)
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(KhanaBookTheme.iconSize.large))
            Spacer(modifier = Modifier.width(spacing.medium))
            Column(horizontalAlignment = Alignment.Start) {
                Text("Chat on WhatsApp", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text("Fastest reply", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            }
        }

        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$SUPPORT_EMAIL")
                    putExtra(Intent.EXTRA_SUBJECT, "KhanaBook POS Support")
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    toastScope.launch {
                        KhanaToast.show("No email app is available", ToastKind.Error)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(72.dp),
            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.6f)),
            shape = KhanaRadii.lg,
            contentPadding = PaddingValues(horizontal = spacing.medium)
        ) {
            Icon(Icons.Default.Email, null, tint = PrimaryGold, modifier = Modifier.size(KhanaBookTheme.iconSize.large))
            Spacer(modifier = Modifier.width(spacing.medium))
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                Text("Email Support", color = PrimaryGold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(SUPPORT_EMAIL, color = TextGold.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(spacing.small))

        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = KhanaRadii.lg
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(spacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = PrimaryGold.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(spacing.medium))
                Column {
                    Text("Support Hours", color = TextLight, style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Mon – Sat, 10 AM – 7 PM IST",
                        color = TextGold.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun AboutAppView() {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val currentYear = Year.now().value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.large, vertical = spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.large)
    ) {
        Box(
            modifier = Modifier
                .size(layout.heroImageSize)
                .background(PrimaryGold.copy(alpha = 0.12f), CircleShape)
                .border(2.dp, PrimaryGold.copy(alpha = 0.3f), CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.about_app_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
        ) {
            Text(
                "KhanaBook Lite",
                color = PrimaryGold,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                color = TextGold.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        HorizontalDivider(
            color = BorderGold.copy(alpha = 0.2f),
            modifier = Modifier.padding(horizontal = spacing.large)
        )

        Text(
            "A smart, offline-first POS solution built for restaurants and food businesses. Manage orders, track payments, and generate reports — all from your Android device.",
            color = TextLight.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(spacing.large))

        Text(
            "© $currentYear KhanaBook. All rights reserved.",
            color = TextGold.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
