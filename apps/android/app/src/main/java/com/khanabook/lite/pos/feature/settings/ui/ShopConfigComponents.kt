package com.khanabook.lite.pos.feature.settings.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.feature.payments.domain.OrderPaymentFlowMode
import com.khanabook.lite.pos.core.designsystem.KhanaBookDialog
import com.khanabook.lite.pos.core.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.core.theme.BorderGold
import com.khanabook.lite.pos.core.theme.CardBG
import com.khanabook.lite.pos.core.theme.DangerRed
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.DarkBrown2
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.KhanaRadii
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.SuccessGreen
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.core.theme.TextLight
import com.khanabook.lite.pos.core.theme.TextMuted
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import android.net.Uri
import com.khanabook.lite.pos.core.util.AppAssetStore
import com.khanabook.lite.pos.core.util.BusinessDate
import com.khanabook.lite.pos.core.util.LogoFileInfo
import java.util.Locale

@Composable
fun ReadOnlyConfigNotice(
    message: String,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    Column(modifier = modifier) {
        Text(
            text = message,
            color = TextGold,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(spacing.medium))
    }
}

@Composable
internal fun RestaurantPaymentFlowSelector(
    selectedMode: OrderPaymentFlowMode,
    onModeSelected: (OrderPaymentFlowMode) -> Unit,
    enabled: Boolean = true
) {
    val spacing = KhanaBookTheme.spacing
    // Same surface as the other Restaurant Configuration cards (CardBG + 25% gold border);
    // it previously painted its own DarkBrown2 wash and read as a different, darker panel.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, BorderGold.copy(alpha = 0.25f)), KhanaRadii.lg)
            .background(CardBG, KhanaRadii.lg)
            .padding(spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Billing Mode",
                    color = TextLight,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Choose when customers settle the bill",
                    color = TextGold.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        PaymentFlowToggleSwitch(
            selectedMode = selectedMode,
            onModeSelected = onModeSelected,
            enabled = enabled
        )
    }
}

@Composable
internal fun PaymentFlowToggleSwitch(
    selectedMode: OrderPaymentFlowMode,
    onModeSelected: (OrderPaymentFlowMode) -> Unit,
    enabled: Boolean = true
) {
    val payAfterFood = selectedMode == OrderPaymentFlowMode.PAY_AFTER_FOOD
    val thumbSize = 44.dp
    val trackColor = if (enabled) PrimaryGold else PrimaryGold.copy(alpha = 0.45f)
    val thumbColor = if (enabled) DarkBrown1 else DarkBrown1.copy(alpha = 0.55f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(trackColor)
            .border(1.dp, PrimaryGold.copy(alpha = 0.55f), CircleShape)
            .toggleable(
                value = payAfterFood,
                enabled = enabled,
                onValueChange = { checked ->
                    onModeSelected(
                        if (checked) OrderPaymentFlowMode.PAY_AFTER_FOOD
                        else OrderPaymentFlowMode.PAY_BEFORE_FOOD
                    )
                }
            )
            .padding(6.dp)
    ) {
        val travel = (maxWidth - thumbSize).coerceAtLeast(0.dp)
        val thumbOffset by animateDpAsState(
            targetValue = if (payAfterFood) travel else 0.dp,
            animationSpec = tween(durationMillis = 220),
            label = "paymentFlowThumbOffset"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (payAfterFood) 0.dp else thumbSize,
                    end = if (payAfterFood) thumbSize else 0.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = selectedMode.displayLabel,
                color = thumbColor,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(thumbSize)
                .background(thumbColor, CircleShape)
        )
    }
}

/**
 * When the daily order number rolls over to 01, formatted for display (`12:30 AM`).
 *
 * Must mirror [BusinessDate.businessDate] exactly: an activated server cutoff wins,
 * otherwise the closing boundary (with the settlement buffer when the service crosses
 * midnight), and finally calendar midnight for a daytime shop. If this drifts the
 * Business Hours subtitle tells the shopkeeper a lie.
 */
internal fun businessDayRolloverLabel(
    cutoffTime: String?,
    cutoffEffectiveFrom: Long?,
    openingTime: String?,
    closingTime: String?
): String {
    val cutoff = cutoffTime?.trim()?.take(5)?.takeIf { it.isNotBlank() } ?: DEFAULT_CUTOFF
    // Mirrors BusinessDate.isCutoffActive on this device: a 00:00 cutoff is the legacy
    // calendar rule, and an unset activation date means the cutoff is not in force yet.
    val cutoffActive = cutoffEffectiveFrom != null && cutoff != DEFAULT_CUTOFF
    val boundary = BusinessDate.closingBoundary(openingTime, closingTime)
    val rollover = when {
        cutoffActive -> cutoff
        boundary != null -> boundary.toString().take(5)
        else -> DEFAULT_CUTOFF
    }
    return formatClockTime(rollover)
}

/** Legacy calendar rule — see [BusinessDate.DEFAULT_CUTOFF]. */
private const val DEFAULT_CUTOFF = "00:00"

/** Minutes past local midnight for an `HH:mm` or `HH:mm:ss` string, or null if unparseable. */
internal fun minutesOfDay(raw: String?): Int? {
    val parts = raw?.trim()?.split(":") ?: return null
    if (parts.size < 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

/** `"02:10"` → `"2:10 AM"`; unparseable input is echoed instead of throwing. */
internal fun formatClockTime(raw: String?): String {
    val minutes = minutesOfDay(raw) ?: return raw?.trim()?.take(5) ?: "--:--"
    val hour24 = minutes / 60
    val minute = minutes % 60
    val hour12 = when (hour24) {
        0 -> 12
        in 13..23 -> hour24 - 12
        else -> hour24
    }
    val suffix = if (hour24 < 12) "AM" else "PM"
    return "$hour12:${minute.toString().padStart(2, '0')} $suffix"
}

/**
 * Calculates human-readable business duration (e.g. "15 hrs") and checks that it does not exceed 24 hours.
 */
internal fun calculateBusinessHoursDuration(openingTime: String?, closingTime: String?): String {
    val opening = minutesOfDay(openingTime) ?: return ""
    val closing = minutesOfDay(closingTime) ?: return ""
    val durationMinutes = if (closing > opening) {
        closing - opening
    } else if (closing < opening) {
        (1440 - opening) + closing // overnight wrap
    } else {
        0
    }
    if (durationMinutes == 0) return ""
    val hours = durationMinutes / 60
    val mins = durationMinutes % 60
    return if (mins > 0) "${hours}h ${mins}m" else "${hours} hrs"
}

/**
 * Segmented switch for the Business Hours mode. Only the active segment is filled; the
 * caller renders exactly one content panel, so the two modes' inputs are never both shown.
 */
@Composable
internal fun BusinessHoursModeToggle(
    isDefault: Boolean,
    onDefaultSelected: () -> Unit,
    onCustomSelected: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(KhanaRadii.xl)
            .border(BorderStroke(1.dp, BorderGold.copy(alpha = 0.4f)), KhanaRadii.xl)
            .background(CardBG)
    ) {
        BusinessHoursModeSegment(
            label = "Default Time",
            isSelected = isDefault,
            onClick = onDefaultSelected,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        BusinessHoursModeSegment(
            label = "Custom Time",
            isSelected = !isDefault,
            onClick = onCustomSelected,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BusinessHoursModeSegment(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = KhanaRadii.xl,
        color = if (isSelected) PrimaryGold else Color.Transparent,
        contentColor = if (isSelected) DarkBrown1 else TextLight
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Shop Logo configuration block. One contextual primary action, one preview, and inline
 * status panels. The picker and upload stay owned by the caller so the existing
 * save-with-profile API contract is preserved.
 */
@Composable
internal fun ShopLogoSection(
    logoUrl: String?,
    logoPath: String?,
    logoVersion: Int,
    pendingLogoUri: Uri?,
    isUploading: Boolean,
    uploadSuccess: LogoFileInfo?,
    uploadError: String?,
    readOnly: Boolean,
    onPickLogo: () -> Unit,
    onRemovePending: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val iconSize = KhanaBookTheme.iconSize
    var showInfo by remember { mutableStateOf(false) }
    var showFileDetails by remember { mutableStateOf(false) }

    val previewSize = when {
        layout.isExpanded -> 168.dp
        layout.isMedium -> 140.dp
        else -> 116.dp
    }
    val localPath = AppAssetStore.resolveAssetPath(logoPath)
    val hasExistingLogo = !logoUrl.isNullOrBlank() || !localPath.isNullOrBlank()
    val hasPreview = pendingLogoUri != null || hasExistingLogo

    if (showInfo) {
        KhanaBookDialog(
            onDismissRequest = { showInfo = false },
            title = "Shop logo",
            message = "Your restaurant logo will appear on customer bills and receipts.",
            actions = {
                TextButton(onClick = { showInfo = false }) {
                    Text("Got it", color = PrimaryGold, style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Shop Logo",
                    color = TextLight,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Shown on bills and receipts",
                    color = TextGold.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "About shop logo",
                    tint = TextGold,
                    modifier = Modifier.size(iconSize.medium)
                )
            }
        }

        // Square preview. ContentScale.Fit keeps the source aspect ratio and lets
        // transparent PNGs show the white receipt-like surface behind the logo.
        Box(
            modifier = Modifier
                .size(previewSize)
                .clip(KhanaRadii.md)
                .background(Color.White)
                .border(1.dp, BorderGold.copy(alpha = 0.5f), KhanaRadii.md),
            contentAlignment = Alignment.Center
        ) {
            val pendingModel = pendingLogoUri?.toString()
            val remoteModel = logoUrl?.takeIf { it.isNotBlank() }
            val model = pendingModel ?: remoteModel ?: localPath
            if (!model.isNullOrBlank()) {
                var loadStage by remember(model) { mutableIntStateOf(0) }
                var isLoading by remember(model) { mutableStateOf(true) }
                val canFallback = pendingModel == null && remoteModel != null && localPath != null
                val effectiveModel = when (loadStage) {
                    0 -> model
                    1 -> if (canFallback) localPath else null
                    else -> null
                }
                if (effectiveModel != null) {
                    val cacheKey = "$effectiveModel:$logoVersion"
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(effectiveModel)
                                .crossfade(true)
                                .memoryCacheKey(cacheKey)
                                .diskCacheKey(cacheKey)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = "Shop logo preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(spacing.extraSmall),
                            onLoading = { isLoading = true },
                            onSuccess = { isLoading = false },
                            onError = {
                                isLoading = false
                                loadStage = if (loadStage == 0 && canFallback) 1 else 2
                            }
                        )
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(iconSize.medium).align(Alignment.Center),
                                color = PrimaryGold,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(iconSize.large)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(iconSize.large)
                )
            }
            if (isUploading) {
                Box(
                    modifier = Modifier.fillMaxSize().background(DarkBrown1.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryGold, strokeWidth = 3.dp)
                }
            }
        }

        if (!hasPreview) {
            Text(
                text = "Upload your restaurant logo",
                color = TextGold.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Progressive disclosure: one short line up front, details on demand.
        Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
            Text(
                text = "PNG, JPG or WebP · up to 10 MB · square images work best",
                color = TextGold.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )
            TextButton(
                onClick = { showFileDetails = !showFileDetails },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
            ) {
                Text(
                    text = if (showFileDetails) "Hide file details" else "File details",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (showFileDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("• At least 64 × 64 px.", color = TextGold.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    Text("• Very wide or tall images are not accepted.", color = TextGold.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    Text("• Large images are resized and compressed automatically.", color = TextGold.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    Text("• Uploaded files stay under 2 MB.", color = TextGold.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (uploadError != null) {
            Surface(
                shape = KhanaRadii.md,
                color = DangerRed.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(iconSize.medium))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Logo couldn't be updated", color = TextLight, style = MaterialTheme.typography.titleSmall)
                        Text(uploadError, color = TextLight, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onRetry, enabled = !isUploading) {
                            Text("Retry", color = PrimaryGold, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        if (uploadSuccess != null) {
            Surface(
                shape = KhanaRadii.md,
                color = SuccessGreen.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(iconSize.medium))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Logo uploaded successfully", color = TextLight, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = uploadSuccess.fileName,
                            color = TextLight,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uploadSuccess.fileSizeBytes > 0) {
                            Text(
                                text = formatFileSize(uploadSuccess.fileSizeBytes),
                                color = TextGold.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = onPickLogo,
            enabled = !readOnly && !isUploading,
            modifier = Modifier.fillMaxWidth().height(spacing.buttonHeight),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryGold,
                contentColor = DarkBrown1,
                disabledContainerColor = PrimaryGold.copy(alpha = 0.4f)
            ),
            shape = KhanaRadii.xl
        ) {
            Icon(
                imageVector = if (hasExistingLogo) Icons.Default.Edit else Icons.Default.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(iconSize.small)
            )
            Spacer(modifier = Modifier.width(spacing.extraSmall))
            Text(
                text = if (hasExistingLogo) "Change Logo" else "Upload Logo",
                style = MaterialTheme.typography.labelLarge
            )
        }

        if (pendingLogoUri != null) {
            Text(
                text = if (isUploading) "Uploading your logo…" else "Your logo will be uploaded when you tap Save Configuration.",
                color = TextGold.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall
            )
            TextButton(onClick = onRemovePending, enabled = !isUploading) {
                Text("Remove selected image", color = TextGold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String =
    if (bytes < 1024) "$bytes B" else String.format(Locale.US, "%.1f KB", bytes / 1024.0)

/** Read-only summary of the built-in full-day schedule shown when Default Time is active. */
@Composable
internal fun BusinessHoursDefaultSummary(modifier: Modifier = Modifier) {
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(KhanaRadii.md)
            .background(PrimaryGold.copy(alpha = 0.08f))
            .border(BorderStroke(1.dp, BorderGold.copy(alpha = 0.25f)), KhanaRadii.md)
            .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
    ) {
        Text(
            text = "12:00 AM – 11:59 PM",
            color = TextLight,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        Text(
            text = "Full day (Midnight reset)",
            color = TextGold.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

