@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, ExperimentalLayoutApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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
import com.khanabook.lite.pos.ui.viewmodel.HomeViewModel.HomeStats
import com.khanabook.lite.pos.ui.designsystem.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
    onMenuClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    authViewModel: com.khanabook.lite.pos.ui.viewmodel.AuthViewModel = hiltViewModel()
) {
    val pageBg = KbMidnightBase
    val spacing = KhanaBookTheme.spacing

    val stats by viewModel.todayStats.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val unsyncedCount by viewModel.unsyncedCount.collectAsState()
    val shopName by viewModel.shopName.collectAsState()
    val greeting = viewModel.greeting
    val marketplacePendingCount by viewModel.marketplacePendingCount.collectAsState()
    val complianceAlerts by viewModel.complianceAlerts.collectAsState()
    val dismissedAlerts: MutableList<String> = remember { mutableListOf() }
    val statsReady by viewModel.statsReady.collectAsState()

    var headerVisible by remember { mutableStateOf(false) }
    var statsVisible by remember { mutableStateOf(false) }
    var actionsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        headerVisible = true
        delay(80)
        statsVisible = true
        delay(80)
        actionsVisible = true
    }

    LaunchedEffect(Unit) {
        viewModel.message.collect { message ->
            KhanaToast.show(message, ToastKind.Info)
        }
    }

    val enterSpec: EnterTransition = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
        slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 }
    val exitSpec: ExitTransition = fadeOut(spring(stiffness = Spring.StiffnessMediumLow))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedVisibility(visible = headerVisible, enter = enterSpec, exit = exitSpec) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(
                                colors = listOf(Color(0xFF1A1535), Color(0xFF130F29))
                            ))
                    ) {
                        Column(
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // White rounded brand logo box
                                Card(
                                    modifier = Modifier.size(56.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_khanabook_logo),
                                            contentDescription = "Logo",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = greeting,
                                        color = Color(0xFFFDBA74), // Warm gold/orange greeting
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = shopName,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SyncStatusHeader(connectionStatus, unsyncedCount, authViewModel)
                                    
                                    // Custom notification button matching mockup
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.2f))
                                            .clickable { /* Handle click */ },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = "Notifications",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        // Red badge dot
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(top = 8.dp, end = 8.dp)
                                                .size(8.dp)
                                                .background(Color(0xFFEF4444), CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    val visibleAlerts = complianceAlerts.filter { it.label !in dismissedAlerts }
                    if (visibleAlerts.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(spacing.small),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
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

            AnimatedVisibility(
                visible = statsVisible && connectionStatus == com.khanabook.lite.pos.domain.util.ConnectionStatus.Unavailable,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(300)),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(200))
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    color = KbWarning.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "You're offline — bills will sync when reconnected",
                            color = KbWarning,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            val isWideScreen = !KhanaBookTheme.layout.isCompact

            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AnimatedVisibility(
                        visible = statsVisible,
                        enter = enterSpec,
                        exit = exitSpec,
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Today's Summary",
                                color = MaterialTheme.kbTextSecondary,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.6.sp
                                )
                            )
                            if (!statsReady || (stats.orderCount == 0 && stats.revenue == 0.0)) {
                                TodaySummaryEmpty(onNewBill = onNewBill)
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    MetricCard(
                                        value = stats.orderCount.toString(),
                                        label = "Total Orders",
                                        accentColor = Color(0xFFF97316),
                                        badge = "today",
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricCard(
                                        value = CurrencyUtils.formatPriceCompact(stats.revenue),
                                        label = "Revenue",
                                        accentColor = Color(0xFF8B5CF6),
                                        badge = "today",
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricCard(
                                        value = CurrencyUtils.formatPriceCompact(
                                            if (stats.orderCount > 0) stats.revenue / stats.orderCount else 0.0
                                        ),
                                        label = "Avg Order",
                                        accentColor = Color(0xFF10B981),
                                        badge = "today",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = actionsVisible,
                        enter = enterSpec,
                        exit = exitSpec,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Quick Actions",
                                color = MaterialTheme.kbTextSecondary,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.6.sp
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // "New Bill" Saffron Filled Button
                                Button(
                                    onClick = onNewBill,
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(48.dp)
                                        .shadow(2.dp, KbShape.Medium),
                                    shape = KbShape.Medium,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF97316)
                                    )
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("New Bill", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                
                                // "Orders" White/Card Button
                                Button(
                                    onClick = onSearchBill,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .border(1.dp, MaterialTheme.kbOutlineSubtle, KbShape.Medium)
                                        .shadow(1.dp, KbShape.Medium),
                                    shape = KbShape.Medium,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.kbBgCard,
                                        contentColor = MaterialTheme.kbTextPrimary
                                    )
                                ) {
                                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF8B5CF6))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Orders", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                
                                // "Menu" Light Saffron Outlined/Tinted Button
                                Button(
                                    onClick = onMenuClick,
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .height(48.dp)
                                        .border(1.dp, Color(0xFFF97316).copy(alpha = 0.5f), KbShape.Medium),
                                    shape = KbShape.Medium,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFFF7ED),
                                        contentColor = Color(0xFFF97316)
                                    )
                                ) {
                                    Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFF97316))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Menu", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }

                            if (marketplacePendingCount > 0) {
                                HomeActionCard(
                                    text = "Online Orders",
                                    onClick = onMarketplaceOrders,
                                    badgeCount = marketplacePendingCount
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HomeActionGridCard(
                                    text = "Find Bill",
                                    icon = Icons.Default.Search,
                                    borderColor = Color(0xFFC084FC),
                                    iconColor = Color(0xFF8B5CF6),
                                    iconBgColor = Color(0xFFF3E8FF),
                                    modifier = Modifier.weight(1f),
                                    onClick = onSearchBill
                                )
                                HomeActionGridCard(
                                    text = "Reprint KDS",
                                    icon = Icons.Default.Print,
                                    borderColor = Color(0xFF34D399),
                                    iconColor = Color(0xFF10B981),
                                    iconBgColor = Color(0xFFECFDF5),
                                    modifier = Modifier.weight(1f),
                                    onClick = onReprintKds
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HomeActionGridCard(
                                    text = "Order Status",
                                    icon = Icons.Default.AccessTime,
                                    borderColor = Color(0xFF60A5FA),
                                    iconColor = Color(0xFF3B82F6),
                                    iconBgColor = Color(0xFFEFF6FF),
                                    modifier = Modifier.weight(1f),
                                    onClick = onOrderStatus
                                )
                                HomeActionGridCard(
                                    text = "Call Customers",
                                    icon = Icons.Default.Call,
                                    borderColor = Color(0xFFF87171),
                                    iconColor = Color(0xFFEF4444),
                                    iconBgColor = Color(0xFFFEF2F2),
                                    modifier = Modifier.weight(1f),
                                    onClick = onCallCustomer
                                )
                            }
                        }
                    }
                }
            } else {
                AnimatedVisibility(visible = statsVisible, enter = enterSpec, exit = exitSpec) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Today's Summary",
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.6.sp
                            )
                        )
                        if (!statsReady || (stats.orderCount == 0 && stats.revenue == 0.0)) {
                            TodaySummaryEmpty(onNewBill = onNewBill)
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricCard(
                                    value = stats.orderCount.toString(),
                                    label = "Total Orders",
                                    accentColor = Color(0xFFF97316),
                                    badge = "today",
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    value = CurrencyUtils.formatPriceCompact(stats.revenue),
                                    label = "Revenue",
                                    accentColor = Color(0xFF8B5CF6),
                                    badge = "today",
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    value = CurrencyUtils.formatPriceCompact(
                                        if (stats.orderCount > 0) stats.revenue / stats.orderCount else 0.0
                                    ),
                                    label = "Avg Order",
                                    accentColor = Color(0xFF10B981),
                                    badge = "today",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = actionsVisible, enter = enterSpec, exit = exitSpec) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Quick Actions",
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.6.sp
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // "New Bill" Saffron Filled Button
                            Button(
                                onClick = onNewBill,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(48.dp)
                                    .shadow(2.dp, KbShape.Medium),
                                shape = KbShape.Medium,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF97316)
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("New Bill", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            
                            // "Orders" White/Card Button
                            Button(
                                onClick = onSearchBill,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .border(1.dp, MaterialTheme.kbOutlineSubtle, KbShape.Medium)
                                    .shadow(1.dp, KbShape.Medium),
                                shape = KbShape.Medium,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.kbBgCard,
                                    contentColor = MaterialTheme.kbTextPrimary
                                )
                            ) {
                                Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF8B5CF6))
                                Spacer(Modifier.width(6.dp))
                                Text("Orders", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            
                            // "Menu" Light Saffron Outlined/Tinted Button
                            Button(
                                onClick = onMenuClick,
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(48.dp)
                                    .border(1.dp, Color(0xFFF97316).copy(alpha = 0.5f), KbShape.Medium),
                                shape = KbShape.Medium,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFF7ED),
                                    contentColor = Color(0xFFF97316)
                                )
                            ) {
                                Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFF97316))
                                Spacer(Modifier.width(6.dp))
                                Text("Menu", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        if (marketplacePendingCount > 0) {
                            HomeActionCard(
                                text = "Online Orders",
                                onClick = onMarketplaceOrders,
                                badgeCount = marketplacePendingCount
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HomeActionGridCard(
                                text = "Find Bill",
                                icon = Icons.Default.Search,
                                borderColor = Color(0xFFC084FC),
                                iconColor = Color(0xFF8B5CF6),
                                iconBgColor = Color(0xFFF3E8FF),
                                modifier = Modifier.weight(1f),
                                onClick = onSearchBill
                            )
                            HomeActionGridCard(
                                text = "Reprint KDS",
                                icon = Icons.Default.Print,
                                borderColor = Color(0xFF34D399),
                                iconColor = Color(0xFF10B981),
                                iconBgColor = Color(0xFFECFDF5),
                                modifier = Modifier.weight(1f),
                                onClick = onReprintKds
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HomeActionGridCard(
                                text = "Order Status",
                                icon = Icons.Default.AccessTime,
                                borderColor = Color(0xFF60A5FA),
                                iconColor = Color(0xFF3B82F6),
                                iconBgColor = Color(0xFFEFF6FF),
                                modifier = Modifier.weight(1f),
                                onClick = onOrderStatus
                            )
                            HomeActionGridCard(
                                text = "Call Customers",
                                icon = Icons.Default.Call,
                                borderColor = Color(0xFFF87171),
                                iconColor = Color(0xFFEF4444),
                                iconBgColor = Color(0xFFFEF2F2),
                                modifier = Modifier.weight(1f),
                                onClick = onCallCustomer
                            )
                        }
                    }
                }
            }

            RestaurantStatusBanner(stats = stats, lastOrderTime = stats.lastOrderTime)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MetricCard(
    value: String,
    label: String,
    accentColor: Color,
    badge: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                clip = false
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard)
    ) {
        Column(
            modifier = Modifier.padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(accentColor)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = value,
                color = MaterialTheme.kbTextPrimary,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                color = MaterialTheme.kbTextSecondary,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                ),
                maxLines = 1
            )
            if (badge != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = badge,
                    color = accentColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun TodaySummaryContent(
    stats: HomeStats,
    textPrimaryColor: Color,
    textMutedColor: Color
) {
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatColumn(
            value = stats.orderCount.toString(),
            label = "Orders",
            subtitle = "today",
            valueColor = textPrimaryColor,
            labelColor = textMutedColor,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(48.dp)
                .background(MaterialTheme.kbOutlineSubtle)
        )
        StatColumn(
            value = CurrencyUtils.formatPriceCompact(stats.revenue),
            label = "Revenue",
            subtitle = "today",
            valueColor = textPrimaryColor,
            labelColor = textMutedColor,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(48.dp)
                .background(MaterialTheme.kbOutlineSubtle)
        )
        StatColumn(
            value = stats.customerCount.toString(),
            label = "Customers",
            subtitle = "today",
            valueColor = textPrimaryColor,
            labelColor = textMutedColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatColumn(
    value: String,
    label: String,
    subtitle: String?,
    valueColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = labelColor,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = labelColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TodaySummaryEmpty(onNewBill: () -> Unit) {
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(spacing.small))
        Icon(
            Icons.Default.Receipt,
            contentDescription = null,
            tint = MaterialTheme.kbTextTertiary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(spacing.small))
        Text(
            "No orders yet today",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.kbTextSecondary
        )
        Text(
            "Start your first bill to see today's summary",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.kbTextTertiary
        )
        Spacer(Modifier.height(spacing.medium))
        Button(
            onClick = onNewBill,
            modifier = Modifier.height(48.dp),
            shape = KbShape.Medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.kbPrimary
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Create New Bill")
        }
        Spacer(Modifier.height(spacing.small))
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
            text = label,
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

    val (textColor, borderColor) = when {
        !isOnline -> Pair(KbError, KbError)
        !isSessionValid -> Pair(KbWarning, KbWarning)
        unsyncedCount > 0 -> Pair(KbBrandSaffron, KbBrandSaffron)
        else -> Pair(Color(0xFF10B981), Color(0xFF10B981))
    }
    val bgColor = Color(0xFF1B1A3F)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
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
    val spacing = KhanaBookTheme.spacing
    val cardBg = MaterialTheme.kbBgCard
    val cardBorderColor = MaterialTheme.kbOutlineSubtle
    val accentColor = KbSuccess

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
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(spacing.medium))
                Column {
                    Text(
                        text = text,
                        color = MaterialTheme.kbTextPrimary,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (badgeCount > 0) {
                        Text(
                            text = "$badgeCount pending",
                            color = KbWarning,
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
                            .background(KbError, CircleShape),
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
    borderColor: Color,
    iconColor: Color,
    iconBgColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    badgeCount: Int = 0
) {
    val spacing = KhanaBookTheme.spacing
    val cardBg = MaterialTheme.kbBgCard

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                clip = false
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.medium)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconBgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(spacing.smallMedium))
            Text(
                text = text,
                color = MaterialTheme.kbTextPrimary,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RestaurantStatusBanner(
    stats: HomeStats,
    lastOrderTime: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(1.dp, MaterialTheme.kbOutlineSubtle, RoundedCornerShape(16.dp))
            .shadow(2.dp, RoundedCornerShape(16.dp), clip = false),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(20.dp)
                    .background(Color(0xFFE6F4EA), CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFF137333), CircleShape)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Restaurant is Open",
                    color = MaterialTheme.kbTextPrimary,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                val orderSuffix = if (stats.orderCount == 1) "order" else "orders"
                val ordersText = "${stats.orderCount} $orderSuffix placed today"
                val lastTimeText = lastOrderTime?.let { " • Last at $it" } ?: ""
                Text(
                    text = "$ordersText$lastTimeText",
                    color = MaterialTheme.kbTextSecondary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.kbTextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ComplianceBanner(
    alert: HomeViewModel.ComplianceAlert,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize

    val result = when (alert.urgency) {
        HomeViewModel.ComplianceAlert.Urgency.EXPIRED  -> Triple(KbRedSubtle, KbError, Icons.Default.GppBad) to ("Expired" to "EXPIRED")
        HomeViewModel.ComplianceAlert.Urgency.CRITICAL -> Triple(KbRedSubtle, KbError, Icons.Default.Warning) to ("Critical warning" to "${alert.daysLeft}d left")
        HomeViewModel.ComplianceAlert.Urgency.HIGH     -> Triple(KbWarning.copy(alpha = KbOpacity.StatusBg), KbWarning, Icons.Default.WarningAmber) to ("High priority warning" to "${alert.daysLeft}d left")
        HomeViewModel.ComplianceAlert.Urgency.MEDIUM   -> Triple(KbSuccess.copy(alpha = KbOpacity.StatusBg), KbSuccess, Icons.Default.Info) to ("Info" to "${alert.daysLeft}d left")
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
                    modifier = Modifier.size(48.dp)
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
