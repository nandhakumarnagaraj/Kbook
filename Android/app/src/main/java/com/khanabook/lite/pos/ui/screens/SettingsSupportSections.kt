package com.khanabook.lite.pos.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.ui.designsystem.KhanaBookDialog
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.khanabook.lite.pos.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// ABOUT APP VIEW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AboutAppView() {
    val uriHandler = LocalUriHandler.current
    val context   = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {

        // ── Hero — full-width login-page style gradient strip ─────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.kbHeaderGradient
                )
                .padding(vertical = 36.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // White elevated logo card — same as login page
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.size(100.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.khanabook_logo),
                            contentDescription = "KhanaBook logo",
                            modifier = Modifier.size(68.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "KhanaBook",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "Restaurant POS & Management",
                    color = KbLavender,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))

                // Version + "Up to date" pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(KbPurpleAccent.copy(alpha = 0.30f), RoundedCornerShape(50))
                            .border(1.dp, KbPurpleAccent.copy(alpha = 0.50f), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "v${BuildConfig.VERSION_NAME}",
                            color = Color(0xFFC4B5FD),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(KbBrandGreen.copy(alpha = 0.20f), RoundedCornerShape(50))
                            .border(1.dp, KbBrandGreen.copy(alpha = 0.45f), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "Up to date",
                            color = Color(0xFF4ADE80),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Info rows card ────────────────────────────────────────────────────
        AboutInfoCard {
            AboutInfoRow(label = "App Version",   value = BuildConfig.VERSION_NAME)
            HairlineDivider()
            AboutInfoRow(label = "Build Number",  value = BuildConfig.VERSION_CODE.toString())
            HairlineDivider()
            AboutInfoRow(label = "Developer",     value = "Piquant Services Pvt. Ltd.")
            HairlineDivider()
            AboutInfoRow(
                label = "Support Email",
                value = "support@khanabook.in",
                valueColor = KbBrandSaffron,
                clickable = true,
                onClick = {
                    runCatching {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:support@khanabook.in")
                            putExtra(Intent.EXTRA_SUBJECT, "KhanaBook Support")
                        }
                        context.startActivity(intent)
                    }
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── Legal links card ──────────────────────────────────────────────────
        AboutInfoCard {
            AboutLinkRow(
                label = "Privacy Policy",
                onClick = { uriHandler.openUri("https://khanabook.com/legal-privacy.html") }
            )
            HairlineDivider()
            AboutLinkRow(
                label = "Terms of Service",
                onClick = { uriHandler.openUri("https://khanabook.com/legal-privacy.html") }
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Footer ────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Made with ", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.labelSmall)
                Text("❤️", style = MaterialTheme.typography.labelSmall)
                Text(" for Indian restaurants", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "© 2026 Piquant Services Pvt. Ltd.",
                color = MaterialTheme.kbTextSecondary.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun AboutInfoCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.kbBgCard)
            .border(1.dp, MaterialTheme.kbOutlineSubtle, RoundedCornerShape(16.dp)),
        content = content
    )
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.kbTextPrimary,
    clickable: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (clickable && onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AboutLinkRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.bodyMedium)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.kbTextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun HairlineDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        thickness = 0.5.dp, color = MaterialTheme.kbOutlineSubtle
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// HELP & SUPPORT VIEW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HelpSupportView(searchQuery: String) {
    val context    = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val faqs = remember {
        listOf(
            FaqItem(
                q = "How do I add a new menu item?",
                a = "Go to Profile → Menu Config → tap a category → Add Item."
            ),
            FaqItem(
                q = "Can I use KhanaBook offline?",
                a = "Yes. Orders are saved locally and synced when internet is restored."
            ),
            FaqItem(
                q = "How to connect a Bluetooth printer?",
                a = "Go to Profile → Printer Config → tap Scan and select your printer."
            ),
            FaqItem(
                q = "How to generate a daily report?",
                a = "Open Reports tab and tap Download Report for a PDF summary."
            ),
        )
    }

    val filteredFaqs = remember(searchQuery) {
        if (searchQuery.isBlank()) faqs
        else faqs.filter {
            it.q.contains(searchQuery, ignoreCase = true) || it.a.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(16.dp))

        // ── Quick action 2×2 grid ─────────────────────────────────────────────
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SupportActionCard(
                    icon         = Icons.Default.ChatBubble,
                    iconBg       = KbPurpleAccent.copy(alpha = 0.18f),
                    iconTint     = KbLavender,
                    title        = "Live Chat",
                    subtitle     = "Avg reply 5 min",
                    modifier     = Modifier.weight(1f),
                    onClick      = {
                        runCatching {
                            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/919789012345"))
                            context.startActivity(i)
                        }
                    }
                )
                SupportActionCard(
                    icon         = Icons.Default.Email,
                    iconBg       = KbBrandSaffron.copy(alpha = 0.14f),
                    iconTint     = KbBrandSaffron,
                    title        = "Email Us",
                    subtitle     = "support@khanabook.in",
                    modifier     = Modifier.weight(1f),
                    onClick      = {
                        runCatching {
                            val i = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:support@khanabook.in")
                                putExtra(Intent.EXTRA_SUBJECT, "KhanaBook Support")
                            }
                            context.startActivity(i)
                        }
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SupportActionCard(
                    icon         = Icons.Default.Call,
                    iconBg       = KbBrandGreen.copy(alpha = 0.14f),
                    iconTint     = KbBrandGreen,
                    title        = "Call Support",
                    subtitle     = "Mon–Sat 9am–6pm",
                    modifier     = Modifier.weight(1f),
                    onClick      = {
                        runCatching {
                            val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+919789012345"))
                            context.startActivity(i)
                        }
                    }
                )
                SupportActionCard(
                    icon         = Icons.AutoMirrored.Outlined.MenuBook,
                    iconBg       = KbBlueDark.copy(alpha = 0.5f),
                    iconTint     = Color(0xFF7CB9F4),
                    title        = "User Guide",
                    subtitle     = "PDF documentation",
                    modifier     = Modifier.weight(1f),
                    onClick      = {
                        runCatching { uriHandler.openUri("https://khanabook.com/guide") }
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── FAQ section ───────────────────────────────────────────────────────
        Text(
            "FREQUENTLY ASKED",
            color = MaterialTheme.kbTextSecondary,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.kbBgCard)
                .border(1.dp, MaterialTheme.kbOutlineSubtle, RoundedCornerShape(16.dp))
        ) {
            filteredFaqs.forEachIndexed { index, faq ->
                FaqRow(faq = faq)
                if (index < filteredFaqs.lastIndex) {
                    HairlineDivider()
                }
            }
            if (filteredFaqs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No results for \"$searchQuery\"",
                        color = MaterialTheme.kbTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Rate KhanaBook banner ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(KbBrandAmber.copy(alpha = 0.12f), KbBrandSaffron.copy(alpha = 0.08f))
                    )
                )
                .border(1.dp, KbBrandAmber.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Star icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(KbBrandAmber.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = KbBrandAmber,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Rate KhanaBook",
                    color = MaterialTheme.kbTextPrimary,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    "Help us improve with your feedback",
                    color = MaterialTheme.kbTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    runCatching {
                        val i = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                        context.startActivity(i)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = KbBrandAmber),
                shape = KbShape.Small,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Star, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Rate", color = Color.White, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SupportActionCard(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.kbBgCard)
            .border(1.dp, MaterialTheme.kbOutlineSubtle, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBg, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(title, color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private data class FaqItem(val q: String, val a: String)

@Composable
private fun FaqRow(faq: FaqItem) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "chevron"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                faq.q,
                color = MaterialTheme.kbTextPrimary,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.kbTextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation)
            )
        }
        if (expanded) {
            Text(
                faq.a,
                color = MaterialTheme.kbTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LEGACY: AppInfoSection & LogoutSection (unchanged)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun AppInfoSection() {
    val context = LocalContext.current
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
    ) {
        Text(
            "KBook v${BuildConfig.VERSION_NAME}",
            color = MaterialTheme.kbSecondary.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
        TextButton(onClick = {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:kbook@pcts.tech")
                putExtra(Intent.EXTRA_SUBJECT, "KhanaBook Support")
            }
            try { context.startActivity(intent) } catch (_: Exception) {}
        }) {
            Text("Contact Support", color = MaterialTheme.kbTertiary.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun LogoutSection(viewModel: com.khanabook.lite.pos.ui.viewmodel.LogoutViewModel) {
    val spacing  = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val context  = LocalContext.current
    val logoutState by viewModel.logoutState.collectAsStateWithLifecycle()
    val appLockViewModel: com.khanabook.lite.pos.ui.viewmodel.AppLockViewModel = hiltViewModel()
    val enteredPin by appLockViewModel.enteredPin.collectAsStateWithLifecycle()
    val pinError   by appLockViewModel.errorMessage.collectAsStateWithLifecycle()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showPinDialog     by remember { mutableStateOf(false) }
    val isPinEnabled = remember(logoutState) { appLockViewModel.isPinEnabled() }

    val toastScope = rememberCoroutineScope()
    LaunchedEffect(logoutState) {
        if (logoutState is com.khanabook.lite.pos.ui.viewmodel.LogoutState.LoggedOut) {
            toastScope.launch { KhanaToast.show(context.getString(R.string.toast_signed_out), ToastKind.Success) }
        }
    }

    LaunchedEffect(enteredPin, showPinDialog) {
        if (showPinDialog && enteredPin.length == 4) {
            appLockViewModel.verifyPin(
                onSuccess = {
                    appLockViewModel.clearPin()
                    showPinDialog = false
                    viewModel.forceLogoutDespiteWarning()
                }
            )
        }
    }

    val isLoading = logoutState is com.khanabook.lite.pos.ui.viewmodel.LogoutState.AttemptingPush ||
        logoutState is com.khanabook.lite.pos.ui.viewmodel.LogoutState.ClearingData

    if (isLoading) {
        val loadingMessage = if (logoutState is com.khanabook.lite.pos.ui.viewmodel.LogoutState.ClearingData) {
            "Clearing data..."
        } else {
            "Syncing data before sign out..."
        }
        KhanaBookDialog(
            onDismissRequest = {},
            title = "Signing Out",
            content = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(iconSize.medium),
                        color = MaterialTheme.kbSecondary,
                        strokeWidth = 3.dp
                    )
                    Text(loadingMessage, color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        ) {}
    }

    if (logoutState is com.khanabook.lite.pos.ui.viewmodel.LogoutState.WarningOfflineData) {
        val warning = logoutState as com.khanabook.lite.pos.ui.viewmodel.LogoutState.WarningOfflineData
        KhanaBookDialog(
            onDismissRequest = { viewModel.cancelLogout() },
            title = "Unsynced Data Warning",
            content = {
                Text(
                    buildString {
                        append("${warning.totalCount} records (${warning.summary}) are not yet synced to the server.\n\n")
                        append("Your data is safe — it will stay on this device and sync automatically after you log back in.")
                        if (isPinEnabled) append("\n\nEnter your app PIN to continue.")
                    },
                    color = MaterialTheme.kbTextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        ) {
            TextButton(onClick = { showPinDialog = false; appLockViewModel.clearPin(); viewModel.cancelLogout() }) {
                Text("Cancel", color = MaterialTheme.kbTextSecondary)
            }
            TextButton(onClick = {
                if (isPinEnabled) { appLockViewModel.clearPin(); showPinDialog = true }
                else viewModel.forceLogoutDespiteWarning()
            }) {
                Text(if (isPinEnabled) "Enter PIN" else "Logout Anyway", color = KbError)
            }
        }
    }

    if (showPinDialog) {
        KhanaBookDialog(
            onDismissRequest = { showPinDialog = false; appLockViewModel.clearPin() },
            title = "Enter App PIN",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    Text(
                        "Unsynced data will be removed from this device after sign out.",
                        color = MaterialTheme.kbSecondary.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    InlinePinEntry(
                        pin = enteredPin,
                        onDigit = { appLockViewModel.appendDigit(it) },
                        onDelete = { appLockViewModel.deleteDigit() },
                        errorMessage = pinError
                    )
                }
            }
        ) {
            TextButton(onClick = { showPinDialog = false; appLockViewModel.clearPin() }) {
                Text("Cancel", color = MaterialTheme.kbTextSecondary)
            }
        }
    }

    if (showConfirmDialog) {
        KhanaBookDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = "Sign Out?",
            message = "You will be signed out of this device."
        ) {
            TextButton(onClick = { showConfirmDialog = false }) {
                Text("Cancel", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { showConfirmDialog = false; viewModel.initiateLogout() }) {
                Text("Sign Out", color = KbError, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }

    Button(
        onClick = { if (!isLoading) showConfirmDialog = true },
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(KbButtonSize.HeightLarge),
        colors = ButtonDefaults.buttonColors(
            containerColor = DangerRed,
            disabledContainerColor = DangerRed.copy(alpha = 0.4f)
        ),
        shape = KbShape.Medium
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(iconSize.small))
        Spacer(modifier = Modifier.width(spacing.small))
        Text("Sign Out", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}
