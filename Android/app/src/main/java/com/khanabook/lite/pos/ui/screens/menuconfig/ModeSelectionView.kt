@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens.menuconfig

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.ui.screens.MenuConfigurationTags
import com.khanabook.lite.pos.ui.theme.*

@Composable
fun ModeSelectionView(
    selectedCategoryName: String?,
    totalCategoriesCount: Int,
    totalItemsCount: Int,
    onManualClick: () -> Unit,
    onSmartImportClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onPdfClick: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .testTag(MenuConfigurationTags.modeSelectionRoot)
            .padding(horizontal = spacing.medium, vertical = spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        // Dashboard Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = CardBG),
                border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.2f)),
                shape = KhanaRadii.lg
            ) {
                Column(modifier = Modifier.padding(KhanaBookTheme.spacing.medium)) {
                    Text("Categories", color = TextGold.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(spacing.extraSmall))
                    Text("$totalCategoriesCount", color = PrimaryGold, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = CardBG),
                border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.2f)),
                shape = KhanaRadii.lg
            ) {
                Column(modifier = Modifier.padding(KhanaBookTheme.spacing.medium)) {
                    Text("Total Items", color = TextGold.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(spacing.extraSmall))
                    Text("$totalItemsCount", color = PrimaryGold, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(spacing.small))

        Text(
            "How would you like to add items?",
            color = PrimaryGold,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // 1. Manual Entry (View & Edit)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MenuConfigurationTags.manualEntryCard),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.35f))
        ) {
            Column {
                Row(modifier = Modifier.padding(spacing.medium), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = PrimaryGold.copy(alpha = 0.18f), shape = CircleShape, modifier = Modifier.size(iconSize.avatar)) {
                        Icon(Icons.Default.Edit, null, tint = PrimaryGold, modifier = Modifier.padding(14.dp))
                    }
                    Spacer(modifier = Modifier.width(spacing.medium))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Manual Entry", color = TextLight, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Add, view & Edit items one by one", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }

                HorizontalDivider(color = BorderGold.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = spacing.mediumLarge))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(KhanaBookTheme.spacing.medium),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SmartAIOption(
                        icon = Icons.Default.Add,
                        label = "Add",
                        onClick = onManualClick,
                        modifier = Modifier.weight(1f)
                    )
                    SmartAIOption(
                        icon = Icons.Default.Visibility,
                        label = "View",
                        onClick = onManualClick,
                        modifier = Modifier.weight(1f)
                    )
                    SmartAIOption(
                        icon = Icons.Default.Edit,
                        label = "Edit",
                        onClick = onManualClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Smart AI
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MenuConfigurationTags.smartAiCard),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.5f))
        ) {
            Column {
                Row(modifier = Modifier.padding(spacing.medium), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = PrimaryGold.copy(alpha = 0.2f), shape = CircleShape, modifier = Modifier.size(iconSize.avatar)) {
                        Icon(Icons.Default.AutoAwesome, null, tint = PrimaryGold, modifier = Modifier.padding(14.dp))
                    }
                    Spacer(modifier = Modifier.width(spacing.medium))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Smart AI", color = TextLight, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(spacing.small))
                            Surface(color = PrimaryGold, shape = KhanaRadii.sm) {
                                Text("AI", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, color = DarkBrown1)
                            }
                        }
                        Text("Extract from camera gallery, pdf.", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Column {
                    HorizontalDivider(color = BorderGold.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = spacing.mediumLarge))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(KhanaBookTheme.spacing.medium),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SmartAIOption(
                            icon = Icons.Default.CameraAlt,
                            label = "Camera",
                            onClick = onSmartImportClick,
                            testTag = MenuConfigurationTags.smartAiCamera,
                            modifier = Modifier.weight(1f)
                        )
                        SmartAIOption(
                            icon = Icons.Default.PhotoLibrary,
                            label = "Gallery",
                            onClick = onGalleryClick,
                            testTag = MenuConfigurationTags.smartAiGallery,
                            modifier = Modifier.weight(1f)
                        )
                        SmartAIOption(
                            icon = Icons.Default.PictureAsPdf,
                            label = "PDF",
                            onClick = onPdfClick,
                            testTag = MenuConfigurationTags.smartAiPdf,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = "AI can make mistakes.please view before saving..",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = spacing.medium, end = spacing.medium, bottom = spacing.medium)
                    )
                }
            }
        }
    }
}

@Composable
fun SmartAIOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(KhanaRadii.lg)
            .clickable { onClick() }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(KhanaBookTheme.spacing.smallMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = DarkBrown1,
            shape = CircleShape,
            modifier = Modifier.size(44.dp),
            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.2f))
        ) {
            Icon(icon, null, tint = PrimaryGold, modifier = Modifier.padding(KhanaBookTheme.spacing.smallMedium))
        }
        Spacer(modifier = Modifier.height(KhanaBookTheme.spacing.small))
        Text(
            label,
            color = TextLight,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
