@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, ExperimentalLayoutApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.HomeViewModel
import com.khanabook.lite.pos.ui.designsystem.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import kotlin.math.abs
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.khanabook.lite.pos.R

@Composable
fun HomeScreen(
    onNewBill: () -> Unit,
    onSearchBill: () -> Unit,
    onReprintKds: () -> Unit,
    onOrderStatus: () -> Unit,
    onCallCustomer: () -> Unit,
    onMarketplaceOrders: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    authViewModel: com.khanabook.lite.pos.ui.viewmodel.AuthViewModel = hiltViewModel()
) {
    // reason: senior mobile UI designer token system to separate layers and ensure WCAG AA contrast.
    val pageBg = if (globalIsDark) KbMidnightBase else Color(0xFFFAF7F4) // ⚠ override: warm off-white page background
    val textPrimaryColor = if (globalIsDark) TextLight else Color(0xFF1A1A1A) // ⚠ override: high contrast primary text
    val textSecondaryColor = if (globalIsDark) TextGold else Color(0xFF7A7068) // ⚠ override: greeting color
    val textMutedColor = if (globalIsDark) TextGold.copy(alpha = 0.7f) else Color(0xFF6B6258) // ⚠ override: section label color

    val stats by viewModel.todayStats.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val unsyncedCount by viewModel.unsyncedCount.collectAsState()
    val shopName by viewModel.shopName.collectAsState()
    val greeting = viewModel.greeting
    val marketplacePendingCount by viewModel.marketplacePendingCount.collectAsState()
    val complianceAlerts by viewModel.complianceAlerts.collectAsState()
    val dismissedAlerts = remember { mutableListOf<String>() }
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout

    val statsReady by viewModel.statsReady.collectAsState()

    val newBillInteractionSource = remember { MutableInteractionSource() }
    val isNewBillPressed by newBillInteractionSource.collectIsPressedAsState()
    val newBillScale by animateFloatAsState(
        targetValue = if (isNewBillPressed) 0.96f else 1f,
        label = "new_bill_scale"
    )

    var headerVisible by remember { mutableStateOf(false) }
    var statsVisible by remember { mutableStateOf(false) }
    var primaryVisible by remember { mutableStateOf(false) }
    var actionsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        headerVisible = true
        delay(80)
        statsVisible = true
        delay(80)
        primaryVisible = true
        delay(80)
        actionsVisible = true
    }

    LaunchedEffect(Unit) {
        viewModel.message.collect { message ->
            KhanaToast.show(message, ToastKind.Info)
        }
    }

    val enterSpec = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInVertically(
        initialOffsetY = { it / 6 },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    )
    val exitSpec = fadeOut(spring(stiffness = Spring.StiffnessMediumLow))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg) // reason: Layer 1 - warm off-white page background
    ) {
        // reason: Keep the mesh gradient accents only in dark mode to preserve visual clarity in light mode.
        if (globalIsDark) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(KbMeshGradientSaffron)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(KbMeshGradientAmber)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            // reason: Equal flex space at top/bottom for vertical centering on tall screens.
            Spacer(modifier = Modifier.weight(1f, fill = true))

            AnimatedVisibility(visible = headerVisible, enter = enterSpec, exit = exitSpec) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = spacing.medium),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_khanabook_logo),
                            contentDescription = "KhanaBook",
                            modifier = Modifier.size(36.dp),
                            contentScale = ContentScale.Fit
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = greeting,
                                color = textSecondaryColor,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 13.sp, // reason: Greeting typography 13sp regular
                                    fontWeight = FontWeight.Normal
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = shopName,
                                color = textPrimaryColor,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontSize = 20.sp, // reason: Screen title typography 20sp bold
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(modifier = Modifier.widthIn(max = 160.dp)) {
                            SyncStatusHeader(connectionStatus, unsyncedCount, authViewModel)
                        }
                    }

                    // Compliance warning banners
                    val visibleAlerts = complianceAlerts.filter { it.label !in dismissedAlerts }
                    if (visibleAlerts.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            visibleAlerts.forEach { alert ->
                                ComplianceBanner(
                                    alert = alert,
                                    onDismiss = { dismissedAlerts.add(alert.label) }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = statsVisible, enter = enterSpec, exit = exitSpec) {
                if (!statsReady) {
                    SkeletonCard(modifier = Modifier.fillMaxWidth())
                } else {
                    // reason: Summary card container matching #F5F0EB fill, #E0D8D0 border, and 0dp elevation.
                    val summaryCardBg = if (globalIsDark) MaterialTheme.kbBgCard.copy(alpha = 0.5f) else Color(0xFFF5F0EB)
                    val summaryCardBorder = if (globalIsDark) BorderGold.copy(alpha = 0.2f) else Color(0xFFE0D8D0)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, summaryCardBorder, RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = summaryCardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(spacing.medium)
                        ) {
                            Text(
                                text = "Today's Summary",
                                color = textMutedColor,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 12.sp, // reason: Section label typography 12sp medium
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.6.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(spacing.small))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                                verticalArrangement = Arrangement.spacedBy(spacing.small),
                                maxItemsInEachRow = 3
                            ) {
                                val statMod = Modifier.weight(1f)
                                SummaryStatItem("Orders", stats.orderCount.toString(), textPrimaryColor, textMutedColor, statMod)
                                SummaryStatItem("Revenue", CurrencyUtils.formatPriceCompact(stats.revenue), textPrimaryColor, textMutedColor, statMod)
                                SummaryStatItem("Customers", stats.customerCount.toString(), textPrimaryColor, textMutedColor, statMod)
                                if (stats.orderCount > 0 || stats.kdsPendingCount > 0) {
                                    SummaryStatItem("Avg Order", CurrencyUtils.formatPriceCompact(stats.avgOrderValue), textPrimaryColor, textMutedColor, statMod)
                                    SummaryStatItem("Cancelled", stats.cancelledCount.toString(), textPrimaryColor, textMutedColor, statMod)
                                    SummaryStatItem("KDS Pending", stats.kdsPendingCount.toString(), textPrimaryColor, textMutedColor, statMod)
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = primaryVisible, enter = enterSpec, exit = exitSpec) {
                Box(contentAlignment = Alignment.Center) {
                    if (globalIsDark) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(130.dp)
                                .background(KbSaffronGlowStrong)
                        )
                    }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(newBillScale)
                            .shadow(4.dp, shape = RoundedCornerShape(18.dp)) // reason: Subtle shadow elevation of 4dp as specified
                            .border(
                                width = 1.dp,
                                color = KbBrandSaffronLight.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(18.dp)
                            ),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(KbMeshHeroGradient)
                                .clickable(
                                    interactionSource = newBillInteractionSource,
                                    indication = ripple(bounded = true, color = Color.White),
                                    onClick = { onNewBill() }
                                )
                                .padding(spacing.large)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Create New Bill",
                                        color = Color.White,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "Start taking orders",
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        modifier = Modifier.padding(top = spacing.extraSmall)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.AddCircle,
                                    contentDescription = "Create New Bill",
                                    tint = Color.White,
                                    modifier = Modifier.size(spacing.huge)
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = primaryVisible, enter = enterSpec, exit = exitSpec) {
                val infiniteTransition = rememberInfiniteTransition(label = "badge_pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.05f,
                    targetValue = 0.25f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )

                Box {
                    if (marketplacePendingCount > 0) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(KbBrandSaffron.copy(alpha = pulseAlpha), Color.Transparent)
                                    )
                                )
                        )
                    }
                    
                    HomeActionCard(
                        text = "Online Orders",
                        onClick = onMarketplaceOrders,
                        badgeCount = marketplacePendingCount
                    )
                }
            }

            AnimatedVisibility(visible = actionsVisible, enter = enterSpec, exit = exitSpec) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.smallMedium)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.smallMedium)
                    ) {
                        HomeActionGridCard(
                            text = "Find Bill",
                            icon = Icons.Default.Search,
                            isPrimary = true, // reason: Primary tier action gets green left-border accent
                            modifier = Modifier.weight(1f),
                            onClick = onSearchBill
                        )
                        HomeActionGridCard(
                            text = "Reprint KDS",
                            icon = Icons.Default.Restaurant,
                            isPrimary = false, // reason: Secondary tier action
                            modifier = Modifier.weight(1f),
                            onClick = onReprintKds
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.smallMedium)
                    ) {
                        HomeActionGridCard(
                            text = "Order Status",
                            icon = Icons.Default.Info,
                            isPrimary = true, // reason: Primary tier action gets green left-border accent
                            modifier = Modifier.weight(1f),
                            onClick = onOrderStatus
                        )
                        HomeActionGridCard(
                            text = "Call Customers",
                            icon = Icons.Default.Call,
                            isPrimary = false, // reason: Secondary tier action
                            modifier = Modifier.weight(1f),
                            onClick = onCallCustomer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = true))
        }
    }
}

@Composable
fun SummaryStatItem(
    label: String,
    value: String,
    valueColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    // reason: Custom summary stat item to support sentence case, 20sp bold values, and 11sp medium labels as per designer specification.
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label, // reason: Sentence case (e.g. "Orders", "Avg order")
            color = labelColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun SyncStatusHeader(
    connectionStatus: com.khanabook.lite.pos.domain.util.ConnectionStatus,
    unsyncedCount: Int,
    authViewModel: com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
) {
    // reason: Cloud synced status badge following design spec: #E6F4EE bg, #0F6E56 text/icon, #B0DBC9 border, RoundedCornerShape(20dp).
    // ⚠ override: Specific background and border colors overriding the default M3 secondary colors for cleaner badge aesthetics.
    val isOnline = connectionStatus == com.khanabook.lite.pos.domain.util.ConnectionStatus.Available
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val currentUser by authViewModel.currentUser.collectAsState()
    val isSessionValid = currentUser != null
    val shouldShowSync = isOnline && isSessionValid && unsyncedCount > 0

    val rotation: Float
    if (shouldShowSync) {
        val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
        rotation = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        ).value
    } else {
        rotation = 0f
    }

    val (bgColor, textColor, borderColor) = when {
        !isOnline -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Color(0xFFFFCDD2))
        !isSessionValid -> Triple(Color(0xFFFFFDE7), Color(0xFFF57F17), Color(0xFFFFF9C4))
        unsyncedCount > 0 -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), Color(0xFFFFE0B2))
        else -> Triple(Color(0xFFE6F4EE), Color(0xFF0F6E56), Color(0xFFB0DBC9)) // ⚠ override: Cloud Synced spec
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp)) // ⚠ override: 20dp pill shape
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = spacing.smallMedium, vertical = spacing.small - spacing.hairline)
        ) {
            Icon(
                imageVector = when {
                    !isOnline -> Icons.Default.CloudOff
                    !isSessionValid -> Icons.Default.Lock
                    unsyncedCount > 0 -> Icons.Default.Sync
                    else -> Icons.Default.CloudDone
                },
                contentDescription = null,
                tint = textColor,
                modifier = Modifier
                    .size(iconSize.small)
                    .then(
                        if (shouldShowSync)
                            Modifier.rotate(rotation)
                        else Modifier
                    )
            )
            
            Spacer(modifier = Modifier.width(spacing.small))
            
            AnimatedContent(
                targetState = when {
                    !isOnline -> "Offline"
                    !isSessionValid -> "Auth Required"
                    unsyncedCount > 0 -> "$unsyncedCount"
                    else -> "Cloud Synced"
                },
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "sync_text"
            ) { targetText ->
                Text(
                    text = targetText,
                    color = textColor,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
fun HomeActionCard(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    badgeCount: Int = 0
) {
    // reason: Styled to match flat white card style (#FFFFFF, 1dp #E8E2DA border, 15sp semibold label).
    // ⚠ override: Custom text size of 15sp and accent color of #1B6B4A for consistency with primary actions.
    val spacing = KhanaBookTheme.spacing
    val cardBg = Color.White
    val cardBorderColor = Color(0xFFE8E2DA)
    val accentColor = Color(0xFF1B6B4A) // Accent / icon green
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, cardBorderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp) // reason: Specified target icon size
                )
                Spacer(modifier = Modifier.width(spacing.medium))
                Column {
                    Text(
                        text = text,
                        color = Color(0xFF1A1A1A),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp, // reason: Body/button label 15sp semi-bold
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (badgeCount > 0) {
                        Text(
                            text = "$badgeCount pending",
                            color = WarningYellow,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(end = spacing.small)
                            .size(28.dp)
                            .background(DangerRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeCount.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun HomeActionGridCard(
    text: String,
    icon: ImageVector,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    badgeCount: Int = 0
) {
    // reason: Grid cards supporting hierarchy: primary actions have left border accent, secondary do not.
    // ⚠ override: Card uses explicit custom colors (#FAF7F4 layer separation, #E8E2DA border, and #1B6B4A icons).
    val spacing = KhanaBookTheme.spacing
    val cardBg = Color.White
    val cardBorderColor = Color(0xFFE8E2DA)
    val accentColor = Color(0xFF1B6B4A) // Accent / icon green
    val textColor = if (isPrimary) Color(0xFF1A1A1A) else Color(0xFF4A4540)
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, cardBorderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min), // reason: Required to let left border expand to cover the card height
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isPrimary) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(accentColor) // reason: left border accent 3dp for primary tier
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = spacing.medium)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp) // reason: Specified target icon size
                    )
                    Spacer(modifier = Modifier.height(spacing.small))
                    Text(
                        text = text,
                        color = textColor,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp, // reason: Body/button label 15sp semi-bold
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                if (badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = spacing.small)
                            .size(24.dp)
                            .background(DangerRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeCount.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ComplianceBanner(
    alert: HomeViewModel.ComplianceAlert,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // reason: Redesigned warning alerts with high contrast background and text to satisfy WCAG AA on off-white.
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize

    val result = when (alert.urgency) {
        HomeViewModel.ComplianceAlert.Urgency.EXPIRED  -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Icons.Default.GppBad) to ("Expired" to "EXPIRED")
        HomeViewModel.ComplianceAlert.Urgency.CRITICAL -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Icons.Default.Warning) to ("Critical warning" to "${alert.daysLeft}d left")
        HomeViewModel.ComplianceAlert.Urgency.HIGH     -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), Icons.Default.WarningAmber) to ("High priority warning" to "${alert.daysLeft}d left")
        HomeViewModel.ComplianceAlert.Urgency.MEDIUM   -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Icons.Default.Info) to ("Info" to "${alert.daysLeft}d left")
    }
    val bannerBg = result.first.first
    val textColor = result.first.second
    val alertIcon = result.first.third
    val contentDesc = result.second.first
    val status = result.second.second

    val message = when (alert.urgency) {
        HomeViewModel.ComplianceAlert.Urgency.EXPIRED  -> "${alert.label} has expired! Renew immediately."
        HomeViewModel.ComplianceAlert.Urgency.CRITICAL -> "${alert.label} expires in ${abs(alert.daysLeft)} day(s). Renew now!"
        HomeViewModel.ComplianceAlert.Urgency.HIGH     -> "${alert.label} expires in ${alert.daysLeft} days. Renew soon."
        HomeViewModel.ComplianceAlert.Urgency.MEDIUM   -> "${alert.label} expires in ${alert.daysLeft} days."
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = bannerBg,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Icon(
                imageVector = alertIcon,
                contentDescription = contentDesc,
                tint = textColor,
                modifier = Modifier.size(iconSize.small)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message,
                    color = textColor,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = textColor.copy(alpha = 0.18f)
            ) {
                Text(
                    text = status,
                    color = textColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp) // reason: Standard 48dp minimum tap target
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss alert",
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(iconSize.small)
                    )
                }
            }
        }
    }
}
