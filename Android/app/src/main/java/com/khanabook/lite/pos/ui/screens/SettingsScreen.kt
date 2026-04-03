@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.khanabook.lite.pos.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.domain.util.ValidationUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.khanabook.lite.pos.data.local.entity.*
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import com.khanabook.lite.pos.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy

import androidx.compose.ui.platform.LocalConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    navController: NavController,
    onScanClick: (String?) -> Unit = {},
    menuViewModel: MenuViewModel,
    viewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    logoutViewModel: com.khanabook.lite.pos.ui.viewmodel.LogoutViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf("menu") }
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600
    val spacing = KhanaBookTheme.spacing

    BackHandler {
        if (section != "menu") {
            section = "menu"
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2)))
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            if (section != "menu_config") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(spacing.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (section == "menu") onBack() else section = "menu" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PrimaryGold)
                    }
                    Text(
                        text = when (section) {
                            "shop" -> "Shop Configuration"
                            "payment" -> "Payment Configuration"
                            "printer" -> "Printer Configuration"
                            "tax" -> "Tax Configuration"
                            "security" -> "App Lock"
                            "menu" -> "Settings"
                            else -> "Settings"
                        },
                        modifier = Modifier.weight(1f),
                        color = PrimaryGold,
                        style = if (section == "menu") MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.width(spacing.huge))
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (section) {
                    "menu" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = spacing.medium)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Spacer(modifier = Modifier.height(spacing.small))
                            val lastSyncTs = remember { viewModel.getLastSyncTimestamp() }
                            ProfileCard(currentUser, profile, lastSyncTs)
                            Spacer(modifier = Modifier.height(spacing.medium))
                            
                            if (isWideScreen) {
                                Row(horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        SettingsItem(icon = Icons.Filled.Store, text = "Shop Configuration") { section = "shop" }
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        SettingsItem(icon = Icons.AutoMirrored.Filled.ReceiptLong, text = "Menu Configuration") { section = "menu_config" }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        SettingsItem(icon = Icons.Filled.CreditCard, text = "Payment Configuration") { section = "payment" }
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        SettingsItem(icon = Icons.Filled.Print, text = "Printer Configuration") { section = "printer" }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        SettingsItem(icon = Icons.Filled.Settings, text = "Tax Configuration") { section = "tax" }
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        SettingsItem(icon = Icons.Filled.Lock, text = "App Lock (PIN / Biometric)") { section = "security" }
                                    }
                                }
                            } else {
                                SettingsItem(icon = Icons.Filled.Store, text = "Shop/Restaurant Configuration") { section = "shop" }
                                SettingsItem(icon = Icons.AutoMirrored.Filled.ReceiptLong, text = "Menu Configuration") { section = "menu_config" }
                                SettingsItem(icon = Icons.Filled.CreditCard, text = "Payment Configuration") { section = "payment" }
                                SettingsItem(icon = Icons.Filled.Print, text = "Printer Configuration") { section = "printer" }
                                SettingsItem(icon = Icons.Filled.Settings, text = "Tax Configuration") { section = "tax" }
                                SettingsItem(icon = Icons.Filled.Lock, text = "App Lock (PIN / Biometric)") { section = "security" }
                            }
                            
                            Spacer(modifier = Modifier.height(spacing.medium))
                            
                            KhanaBookCard(
                                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.small),
                                colors = CardDefaults.cardColors(containerColor = CardBG),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                LogoutSection(logoutViewModel)
                            }
                            AppInfoSection()
                            Spacer(modifier = Modifier.height(spacing.extraLarge))
                        }
                    }
                    "shop" -> {
                        ShopConfigView(profile, viewModel, authViewModel) { section = "menu" }
                    }
                    "menu_config" -> {
                        MenuConfigurationScreen(
                            navController = navController,
                            onBackClick = { section = "menu" },
                            viewModel = menuViewModel
                        )
                    }
                    "payment" -> {
                        val ctx = LocalContext.current
                        PaymentConfigView(profile, onSave = { viewModel.saveProfile(it); Toast.makeText(ctx, "Saved Payment Config", Toast.LENGTH_SHORT).show(); section = "menu" }, onBack = { section = "menu" })
                    }
                    "printer" -> {
                        val ctx = LocalContext.current
                        PrinterConfigView(profile, onSave = { viewModel.saveProfile(it); Toast.makeText(ctx, "Saved Printer Config", Toast.LENGTH_SHORT).show(); section = "menu" }, onBack = { section = "menu" }, viewModel = viewModel)
                    }
                    "tax" -> {
                        val ctx = LocalContext.current
                        TaxConfigView(profile, onSave = { viewModel.saveProfile(it); Toast.makeText(ctx, "Saved Tax Config", Toast.LENGTH_SHORT).show(); section = "menu" }, onBack = { section = "menu" })
                    }
                    "security" -> {
                        AppLockConfigView(onBack = { section = "menu" })
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileCard(user: UserEntity?, profile: RestaurantProfileEntity?, lastSyncTimestamp: Long = 0L) {
    val displayName = profile?.shopName?.takeIf { it.isNotBlank() } ?: user?.name?.takeIf { it.isNotBlank() } ?: "Guest"
    val displayPhone = profile?.whatsappNumber?.takeIf { it.isNotBlank() } ?: user?.whatsappNumber ?: ""
    val spacing = KhanaBookTheme.spacing
    val syncLabel = remember(lastSyncTimestamp) {
        if (lastSyncTimestamp > 0L) {
            "Last sync: " + SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(lastSyncTimestamp))
        } else "Not synced yet"
    }

    KhanaBookCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkBrown2.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(spacing.medium), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(KhanaBookTheme.iconSize.avatar).background(PrimaryGold, CircleShape), contentAlignment = Alignment.Center) {
                Text(text = displayName.take(1).uppercase(), color = DarkBrown1, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(modifier = Modifier.width(spacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = displayName, color = TextLight, style = MaterialTheme.typography.titleLarge)
                if (displayPhone.isNotBlank()) {
                    Text(text = displayPhone, color = TextGold, style = MaterialTheme.typography.bodySmall)
                }
                Text(text = syncLabel, color = TextGold.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    KhanaBookCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = CardBG),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(spacing.medium), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = PrimaryGold, modifier = Modifier.size(iconSize.medium))
                Spacer(modifier = Modifier.width(spacing.medium))
                Text(text, color = TextLight, style = MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = PrimaryGold)
        }
    }
}

@Composable
fun ConfigCard(content: @Composable ColumnScope.() -> Unit) {
    val spacing = KhanaBookTheme.spacing
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = spacing.medium),
        colors = CardDefaults.cardColors(containerColor = CardBG),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.2f))
    ) { Column(modifier = Modifier.padding(spacing.large)) { content() } }
}

@Composable
private fun ShopConfigView(profile: RestaurantProfileEntity?, viewModel: SettingsViewModel, authViewModel: AuthViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val spacing = KhanaBookTheme.spacing
    val isCompactWidth = LocalConfiguration.current.screenWidthDp < 400
    var name by remember { mutableStateOf(profile?.shopName ?: "") }
    var address by remember { mutableStateOf(profile?.shopAddress ?: "") }
    var whatsapp by remember { mutableStateOf(profile?.whatsappNumber ?: "") }
    var email by remember { mutableStateOf(profile?.email ?: "") }
    var logoPath by remember { mutableStateOf(profile?.logoPath) }
    var consent by remember { mutableStateOf(profile?.emailInvoiceConsent ?: false) }
    var reviewUrl by remember { mutableStateOf(profile?.reviewUrl ?: "") }
    var logoUpdateTrigger by remember { mutableStateOf(0L) }

    val saveProfileLoading by viewModel.saveProfileLoading.collectAsState()
    val saveProfileError by viewModel.saveProfileError.collectAsState()
    val saveProfileSuccess by viewModel.saveProfileSuccess.collectAsState()
    val isUserChecking by viewModel.isUserChecking.collectAsStateWithLifecycle()
    val userExistsError by viewModel.userExistsError.collectAsStateWithLifecycle()

    val isDirty = remember(name, address, whatsapp, email, consent, reviewUrl, logoPath, profile) {
        name != (profile?.shopName ?: "") ||
        address != (profile?.shopAddress ?: "") ||
        whatsapp != (profile?.whatsappNumber ?: "") ||
        email != (profile?.email ?: "") ||
        consent != (profile?.emailInvoiceConsent ?: false) ||
        reviewUrl != (profile?.reviewUrl ?: "") ||
        logoPath != profile?.logoPath
    }

    var showUnsavedDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = isDirty && !saveProfileLoading) {
        showUnsavedDialog = true
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            containerColor = DarkBrown2,
            title = { Text("Unsaved Changes", color = PrimaryGold, style = MaterialTheme.typography.titleLarge) },
            text = { Text("You have unsaved changes. Discard them?", color = TextLight, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showUnsavedDialog = false; onBack() }) {
                    Text("Discard", color = DangerRed, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog = false }) {
                    Text("Keep Editing", color = PrimaryGold, style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    LaunchedEffect(saveProfileError) {
        saveProfileError?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearSaveProfileState()
        }
    }

    LaunchedEffect(saveProfileSuccess) {
        if (saveProfileSuccess) {
            Toast.makeText(context, "Profile saved successfully", Toast.LENGTH_SHORT).show()
            viewModel.clearSaveProfileState()
            authViewModel.clearOtpStatus()
            onBack()
        }
    }

    LaunchedEffect(profile) {
        profile?.let {
            name = it.shopName ?: ""
            address = it.shopAddress ?: ""
            whatsapp = it.whatsappNumber ?: ""
            email = it.email ?: ""
            logoPath = it.logoPath
            consent = it.emailInvoiceConsent
            reviewUrl = it.reviewUrl ?: ""
        }
    }

    LaunchedEffect(whatsapp) {
        if (whatsapp.length < 10) {
            viewModel.clearUserCheck()
        }
    }

    var otpValue by remember { mutableStateOf("") }
    val otpStatus by authViewModel.otpVerificationStatus.collectAsState()

    var otpSent by remember { mutableStateOf(false) }
    var otpTimer by remember { mutableIntStateOf(120) }
    var isOtpVerified by remember { mutableStateOf(false) }

    LaunchedEffect(otpStatus) {
        when (otpStatus) {
            is AuthViewModel.OtpVerificationResult.OtpSent -> {
                otpSent = true
                isOtpVerified = false
                otpTimer = 120
                Toast.makeText(context, "OTP Sent to your WhatsApp!", Toast.LENGTH_SHORT).show()
            }
            is AuthViewModel.OtpVerificationResult.Success -> {
                isOtpVerified = true
                Toast.makeText(context, "Verified successfully!", Toast.LENGTH_SHORT).show()
                authViewModel.clearOtpStatus()
            }
            is AuthViewModel.OtpVerificationResult.Error -> {
                val errorMsg = (otpStatus as AuthViewModel.OtpVerificationResult.Error).message
                isOtpVerified = false
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                authViewModel.clearOtpStatus()
            }
            else -> {}
        }
    }

    LaunchedEffect(otpSent, otpTimer) {
        if (otpSent && otpTimer > 0) {
            kotlinx.coroutines.delay(1000)
            otpTimer--
        }
    }

    val logoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            logoPath = copyUriToInternalStorage(context, it, "shop_logo.png")
            logoUpdateTrigger = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(spacing.medium)
    ) {
        ConfigCard {
            Text("Shop Profile", color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(spacing.medium))
            
            val logoContent = @Composable {
                Box(modifier = Modifier.size(KhanaBookTheme.iconSize.hero).background(Color.White).border(1.dp, Color.LightGray), contentAlignment = Alignment.Center) {
                    if (!logoPath.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(logoPath)
                                .setParameter("refresh", logoUpdateTrigger)
                                .crossfade(true)
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .build(),
                            contentDescription = "Logo",
                            modifier = Modifier.fillMaxSize().padding(4.dp)
                        )
                    } else {
                        Icon(Icons.Default.Storefront, null, tint = Color.LightGray, modifier = Modifier.size(KhanaBookTheme.iconSize.xlarge))
                    }
                }
                OutlinedButton(onClick = { logoLauncher.launch("image/*") }, border = BorderStroke(1.dp, PrimaryGold), shape = RoundedCornerShape(20.dp)) { Text("Change Logo", color = PrimaryGold) }
            }

            if (isCompactWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
                    logoContent()
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
                    logoContent()
                }
            }
            
            Spacer(modifier = Modifier.height(spacing.large))
            ParchmentTextField(value = name, onValueChange = { name = it }, label = "Shop Name")
            Spacer(modifier = Modifier.height(spacing.medium))
            ParchmentTextField(value = address, onValueChange = { address = it }, label = "Shop Address")
            val isPhoneValid = ValidationUtils.isValidPhone(whatsapp)
            val numberChanged = whatsapp != (profile?.whatsappNumber ?: "")

            Spacer(modifier = Modifier.height(spacing.medium))
            ParchmentTextField(
                value = whatsapp,
                onValueChange = {
                    val filtered = it.filter { ch -> ch.isDigit() }.take(10)
                    whatsapp = filtered
                    if (filtered != (profile?.whatsappNumber ?: "")) {
                        otpSent = false
                        isOtpVerified = false
                        otpValue = ""
                        if (filtered.length == 10) {
                            viewModel.checkUserExists(filtered, profile?.whatsappNumber ?: "")
                        }
                    } else {
                        isOtpVerified = true
                        viewModel.clearUserCheck()
                    }
                },
                label = "Whatsapp Number",
                isError = (whatsapp.isNotEmpty() && !isPhoneValid) || userExistsError != null,
                supportingText = if (userExistsError != null) userExistsError else if (whatsapp.isNotEmpty() && !isPhoneValid) "Enter 10-digit number" else null,
                trailingIcon = {
                    if (isUserChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryGold
                        )
                    } else if (numberChanged && (!otpSent || otpTimer == 0)) {
                        Button(
                            onClick = {
                                if (isPhoneValid && userExistsError == null) authViewModel.sendOtp(whatsapp, "update_whatsapp")
                            },
                            modifier = Modifier.padding(end = 4.dp).height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            enabled = isPhoneValid && !isUserChecking && userExistsError == null
                        ) {
                            Text("Send OTP", color = DarkBrown1, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            )

            if (otpSent && numberChanged) {
                Spacer(modifier = Modifier.height(spacing.medium))
                ParchmentTextField(
                    value = otpValue,
                    onValueChange = {
                        if (it.length <= 6) {
                            otpValue = it
                            if (it.length == 6) {
                                authViewModel.confirmMobileNumberUpdate(whatsapp, it)
                            } else {
                                isOtpVerified = false
                            }
                        }
                    },
                    label = "Enter 6-digit OTP",
                    isError = otpValue.length == 6 && !isOtpVerified,
                    supportingText = if (otpValue.length == 6 && !isOtpVerified) "Invalid OTP code" else null,
                    trailingIcon = {
                        if (otpTimer > 0 && !isOtpVerified) {
                            Text(String.format("%02d:%02d", otpTimer / 60, otpTimer % 60), color = TextLight, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 16.dp))
                        } else if (isOtpVerified) {
                            Icon(Icons.Default.Lock, contentDescription = "Verified", tint = SuccessGreen, modifier = Modifier.padding(end = 16.dp))
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))
            ParchmentTextField(value = email, onValueChange = { email = it }, label = "Email")
            Spacer(modifier = Modifier.height(spacing.medium))
            ParchmentTextField(value = reviewUrl, onValueChange = { reviewUrl = it }, label = "Review Link")
            Spacer(modifier = Modifier.height(spacing.medium))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = consent, onCheckedChange = { consent = it }, colors = CheckboxDefaults.colors(checkedColor = PrimaryGold))
                Text("Receive invoice copies on Email", color = TextGold, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(spacing.large))

            val isSaveEnabled = isDirty && (!numberChanged || isOtpVerified) && !saveProfileLoading

            val saveButtonScale by animateFloatAsState(
                targetValue = if (isSaveEnabled) 1f else 0.97f,
                animationSpec = tween(durationMillis = 250),
                label = "save_scale"
            )
            val saveButtonAlpha by animateFloatAsState(
                targetValue = if (isSaveEnabled) 1f else 0.45f,
                animationSpec = tween(durationMillis = 250),
                label = "save_alpha"
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                Button(
                    onClick = {
                        if (!isSaveEnabled) return@Button
                        if (numberChanged && !isOtpVerified) {
                            Toast.makeText(context, "Please verify the new WhatsApp number", Toast.LENGTH_SHORT).show()
                        } else {
                            val updatedProfile = profile?.copy(
                                shopName = name,
                                shopAddress = address,
                                whatsappNumber = whatsapp,
                                email = email,
                                logoPath = logoPath,
                                emailInvoiceConsent = consent,
                                reviewUrl = reviewUrl,
                                isSynced = false,
                                updatedAt = System.currentTimeMillis()
                            )
                            updatedProfile?.let { viewModel.saveProfile(it) }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .graphicsLayer {
                            scaleX = saveButtonScale
                            scaleY = saveButtonScale
                            alpha = saveButtonAlpha
                        },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(28.dp),
                    enabled = isSaveEnabled
                ) {
                    if (saveProfileLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Save Changes", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }
                OutlinedButton(
                    onClick = {
                        viewModel.resetDailyCounter()
                        Toast.makeText(context, "Daily order counter reset", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    border = BorderStroke(1.dp, DangerRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                    shape = RoundedCornerShape(28.dp)
                ) { Text("Reset Daily Counter", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun PaymentConfigView(profile: RestaurantProfileEntity?, onSave: (RestaurantProfileEntity) -> Unit, onBack: () -> Unit) {
    val spacing = KhanaBookTheme.spacing
    val isCompactWidth = LocalConfiguration.current.screenWidthDp < 400
    var currency by remember { mutableStateOf(profile?.currency ?: "INR") }
    var upiSupported by remember { mutableStateOf(profile?.upiEnabled ?: false) }
    var upiHandle by remember { mutableStateOf(profile?.upiHandle ?: "") }
    var upiMobile by remember { mutableStateOf(profile?.upiMobile ?: "") }
    var qrPath by remember { mutableStateOf(profile?.upiQrPath) }
    var cashEnabled by remember { mutableStateOf(profile?.cashEnabled ?: true) }
    var posEnabled by remember { mutableStateOf(profile?.posEnabled ?: false) }
    var zomatoEnabled by remember { mutableStateOf(profile?.zomatoEnabled ?: false) }
    var swiggyEnabled by remember { mutableStateOf(profile?.swiggyEnabled ?: false) }
    var ownWebsiteEnabled by remember { mutableStateOf(profile?.ownWebsiteEnabled ?: false) }
    var qrUpdateTrigger by remember { mutableStateOf(0L) }

    val context = LocalContext.current
    val qrLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { 
            qrPath = copyUriToInternalStorage(context, it, "upi_qr.png")
            qrUpdateTrigger = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(spacing.medium)
    ) {
        ConfigCard {
            ParchmentTextField(value = currency, onValueChange = { currency = it }, label = "Currency *")
            Spacer(modifier = Modifier.height(spacing.large))
            Text("Enable Payment Methods", color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
            PaymentToggle("Cash Payment", cashEnabled) { cashEnabled = it }
            PaymentToggle("POS Machine", posEnabled) { posEnabled = it }
            PaymentToggle("Zomato Orders", zomatoEnabled) { zomatoEnabled = it }
            PaymentToggle("Swiggy Orders", swiggyEnabled) { swiggyEnabled = it }
            PaymentToggle("Own Website", ownWebsiteEnabled) { ownWebsiteEnabled = it }
            
            Spacer(modifier = Modifier.height(spacing.medium))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = upiSupported, onCheckedChange = { upiSupported = it }, colors = CheckboxDefaults.colors(checkedColor = SuccessGreen))
                Text("Enable UPI QR Payments", color = TextGold, style = MaterialTheme.typography.bodyMedium)
            }
            if (upiSupported) {
                Spacer(modifier = Modifier.height(spacing.medium))
                
                val qrContent = @Composable {
                    Box(modifier = Modifier.size(KhanaBookTheme.iconSize.hero).background(Color.White).border(1.dp, Color.LightGray).padding(4.dp), contentAlignment = Alignment.Center) {
                        if (!qrPath.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(qrPath)
                                    .setParameter("refresh", qrUpdateTrigger)
                                    .crossfade(true)
                                    .diskCachePolicy(CachePolicy.DISABLED)
                                    .build(),
                                contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.QrCode, null, tint = Color.LightGray, modifier = Modifier.size(KhanaBookTheme.iconSize.xlarge))
                        }
                    }
                    OutlinedButton(onClick = { qrLauncher.launch("image/*") }, border = BorderStroke(1.dp, PrimaryGold), shape = RoundedCornerShape(20.dp)) { Text("Upload QR", color = PrimaryGold) }
                }

                if (isCompactWidth) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) { qrContent() }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.medium)) { qrContent() }
                }
                
                Spacer(modifier = Modifier.height(spacing.medium))
                ParchmentTextField(value = upiHandle, onValueChange = { upiHandle = it }, label = "UPI Handle")
                Spacer(modifier = Modifier.height(spacing.small))
                ParchmentTextField(value = upiMobile, onValueChange = { upiMobile = it }, label = "UPI Mobile Number")
            }
            Spacer(modifier = Modifier.height(spacing.extraLarge))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                Button(
                    onClick = {
                        profile?.copy(
                            currency = currency,
                            upiEnabled = upiSupported,
                            upiHandle = upiHandle,
                            upiMobile = upiMobile,
                            upiQrPath = qrPath,
                            cashEnabled = cashEnabled,
                            posEnabled = posEnabled,
                            zomatoEnabled = zomatoEnabled,
                            swiggyEnabled = swiggyEnabled,
                            ownWebsiteEnabled = ownWebsiteEnabled,
                            isSynced = false,
                            updatedAt = System.currentTimeMillis()
                        )?.let { onSave(it) }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(28.dp)
                ) { Text("Save Config", color = Color.White, style = MaterialTheme.typography.titleMedium) }
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(56.dp), border = BorderStroke(1.dp, TextGold), shape = RoundedCornerShape(28.dp)) { Text("Back") }
            }
        }
    }
}

@Composable
fun PaymentToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextGold, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedTrackColor = SuccessGreen))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrinterConfigView(profile: RestaurantProfileEntity?, onSave: (RestaurantProfileEntity) -> Unit, onBack: () -> Unit, viewModel: SettingsViewModel) {
    val spacing = KhanaBookTheme.spacing
    var enabled by remember { mutableStateOf(profile?.printerEnabled ?: false) }
    var paper58 by remember { mutableStateOf((profile?.paperSize ?: "58mm") == "58mm") }
    var autoPrint by remember { mutableStateOf(profile?.autoPrintOnSuccess ?: false) }
    var includeLogo by remember { mutableStateOf(profile?.includeLogoInPrint ?: true) }
    var printWhatsapp by remember { mutableStateOf(profile?.printCustomerWhatsapp ?: true) }
    var maskPhone by remember { mutableStateOf(profile?.maskCustomerPhone ?: true) }
    val context = LocalContext.current
    var isBtActive by remember { mutableStateOf(viewModel.isBluetoothEnabled(context)) }
    
    val btDevices by viewModel.btDevices.collectAsStateWithLifecycle()
    val btIsScanning by viewModel.btIsScanning.collectAsStateWithLifecycle()
    val btIsConnected by viewModel.btIsConnected.collectAsStateWithLifecycle()
    val btIsConnecting by viewModel.btIsConnecting.collectAsStateWithLifecycle()
    val btConnectResult by viewModel.btConnectResult.collectAsStateWithLifecycle()
    var showBtSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            isBtActive = true
            if (enabled) { viewModel.startBluetoothScan(context); showBtSheet = true }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) perms[Manifest.permission.BLUETOOTH_CONNECT] == true && perms[Manifest.permission.BLUETOOTH_SCAN] == true else perms[Manifest.permission.BLUETOOTH] == true && perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (ok) { if (!viewModel.isBluetoothEnabled(context)) bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) else { viewModel.startBluetoothScan(context); showBtSheet = true } }
        else { Toast.makeText(context, "Permissions required", Toast.LENGTH_SHORT).show(); enabled = false }
    }

    LaunchedEffect(btConnectResult) {
        btConnectResult?.let { Toast.makeText(context, if (it) "Connected!" else "Failed", Toast.LENGTH_SHORT).show(); if (it) showBtSheet = false; viewModel.clearBtConnectResult() }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(spacing.medium)
    ) {
        ConfigCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Bluetooth Printer", color = TextGold, style = MaterialTheme.typography.titleMedium)
                Switch(checked = enabled, onCheckedChange = { 
                    enabled = it
                    if (it) {
                        if (!viewModel.hasBluetoothPermissions(context)) {
                            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN) else arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
                            permissionLauncher.launch(perms)
                        } else if (!viewModel.isBluetoothEnabled(context)) bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        else { viewModel.startBluetoothScan(context); showBtSheet = true }
                    }
                }, colors = SwitchDefaults.colors(checkedTrackColor = VegGreen))
            }
            if (enabled) {
                Spacer(modifier = Modifier.height(spacing.medium))
                Box(modifier = Modifier.fillMaxWidth().border(1.dp, BorderGold.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(spacing.medium)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${profile?.printerName ?: "No Printer"}", color = TextLight, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(spacing.small))
                            Box(modifier = Modifier.size(8.dp).background(if (btIsConnected) SuccessGreen else DangerRed, CircleShape))
                        }
                        Text("MAC: ${profile?.printerMac ?: "---"}", color = TextGold, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(modifier = Modifier.height(spacing.medium))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    Button(onClick = { if (!viewModel.isBluetoothEnabled(context)) bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) else { viewModel.startBluetoothScan(context); showBtSheet = true } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Brown500)) { Text("Scan") }
                    Button(onClick = { viewModel.testPrint() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)) { Text("Test Print", color = DarkBrown1) }
                }
            }
        }
        ConfigCard {
            Text("Paper Size", color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = paper58, onClick = { paper58 = true }, colors = RadioButtonDefaults.colors(selectedColor = PrimaryGold))
                Text("58mm", color = TextGold)
                Spacer(modifier = Modifier.width(spacing.large))
                RadioButton(selected = !paper58, onClick = { paper58 = false }, colors = RadioButtonDefaults.colors(selectedColor = PrimaryGold))
                Text("80mm", color = TextGold)
            }
        }
        ConfigCard {
            Text("Print Options", color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
            PrinterOptionRow("Auto Print Success", autoPrint) { autoPrint = it }
            PrinterOptionRow("Include Logo", includeLogo) { includeLogo = it }
            PrinterOptionRow("Print WhatsApp", printWhatsapp) { printWhatsapp = it }
            PrinterOptionRow("Mask Customer Phone", maskPhone) { maskPhone = it }
        }
        Spacer(modifier = Modifier.height(spacing.large))
        Button(onClick = {
            profile?.copy(printerEnabled = enabled, paperSize = if (paper58) "58mm" else "80mm", autoPrintOnSuccess = autoPrint, includeLogoInPrint = includeLogo, printCustomerWhatsapp = printWhatsapp, maskCustomerPhone = maskPhone, isSynced = false, updatedAt = System.currentTimeMillis())?.let { onSave(it) }
        }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Green800), shape = RoundedCornerShape(28.dp)) { Text("SAVE PRINTER SETTINGS", color = Color.White, style = MaterialTheme.typography.titleMedium) }
    }

    if (showBtSheet) {
        ModalBottomSheet(onDismissRequest = { viewModel.stopBluetoothScan(); showBtSheet = false }, sheetState = sheetState, containerColor = DarkBrownSheet) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(spacing.large).padding(bottom = spacing.large)) {
                Text("Select Printer", color = PrimaryGold, style = MaterialTheme.typography.headlineSmall)
                if (btIsScanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = spacing.medium), color = PrimaryGold)
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp), contentPadding = PaddingValues(bottom = spacing.small)) {
                    items(btDevices) { device ->
                        DeviceRow(device = device, isConnecting = btIsConnecting, isSelected = device.address == profile?.printerMac, isConnected = device.address == profile?.printerMac && btIsConnected) { viewModel.connectToPrinter(context, device) }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceRow(
    device: BluetoothDevice, 
    isConnecting: Boolean, 
    isSelected: Boolean = false, 
    isConnected: Boolean = false, 
    onClick: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    @Suppress("MissingPermission")
    val name = device.name ?: "Unknown"
    val border = if (isSelected) BorderStroke(2.dp, PrimaryGold) else null
    val backgroundColor = if (isSelected) DarkBrown1 else DarkBrown1.copy(alpha = 0.5f)
    
    KhanaBookCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), 
        onClick = if (!isConnecting) onClick else null,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        if (border != null) {
            Modifier.border(border, RoundedCornerShape(8.dp))
        }
        Row(modifier = Modifier.padding(spacing.medium), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                null,
                tint = if (isSelected) PrimaryGold else TextGold,
                modifier = Modifier.size(iconSize.medium)
            )
            Spacer(modifier = Modifier.width(spacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = TextLight, style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium))
                Text(device.address, color = if (isSelected) PrimaryGold.copy(alpha = 0.7f) else TextGold, style = MaterialTheme.typography.labelSmall)
            }
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PrimaryGold, strokeWidth = 2.dp)
            } else if (isConnected) {
                Box(modifier = Modifier.size(8.dp).background(SuccessGreen, CircleShape))
            }
        }
    }
}

@Composable
private fun PrinterOptionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, colors = CheckboxDefaults.colors(checkedColor = PrimaryGold))
        Text(label, color = TextGold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TaxConfigView(profile: RestaurantProfileEntity?, onSave: (RestaurantProfileEntity) -> Unit, onBack: () -> Unit) {
    val spacing = KhanaBookTheme.spacing
    var country by remember { mutableStateOf(profile?.country ?: "India") }
    var gstEnabled by remember { mutableStateOf(profile?.gstEnabled ?: false) }
    var gstNumber by remember { mutableStateOf(profile?.gstin ?: "") }
    var gstPct by remember { mutableStateOf(profile?.gstPercentage?.toString() ?: "0") }
    var fssaiNumber by remember { mutableStateOf(profile?.fssaiNumber ?: "") }
    
    val isSaveEnabled = fssaiNumber.isNotBlank() && (!gstEnabled || gstNumber.isNotBlank())

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(spacing.medium)) {
        ConfigCard {
            ParchmentTextField(value = country, onValueChange = { country = it }, label = "Country")
            Spacer(modifier = Modifier.height(spacing.medium))
            ParchmentTextField(value = fssaiNumber, onValueChange = { fssaiNumber = it }, label = "FSSAI Number *")
            Spacer(modifier = Modifier.height(spacing.medium))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("GST Registered", color = TextGold, style = MaterialTheme.typography.bodyMedium)
                Switch(checked = gstEnabled, onCheckedChange = { gstEnabled = it }, colors = SwitchDefaults.colors(checkedTrackColor = VegGreen))
            }
            if (gstEnabled) {
                ParchmentTextField(value = gstNumber, onValueChange = { gstNumber = it.uppercase() }, label = "GSTIN *")
                Spacer(modifier = Modifier.height(spacing.small))
                ParchmentTextField(value = gstPct, onValueChange = { gstPct = it }, label = "GST % *")
            }
            Spacer(modifier = Modifier.height(spacing.extraLarge))
            Button(onClick = {
                profile?.copy(country = country, gstEnabled = gstEnabled, gstin = gstNumber, gstPercentage = gstPct.toDoubleOrNull() ?: 0.0, fssaiNumber = fssaiNumber, isSynced = false, updatedAt = System.currentTimeMillis())?.let { onSave(it) }
            }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen), shape = RoundedCornerShape(28.dp), enabled = isSaveEnabled) { Text("Save Tax Config", color = Color.White, style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun AppInfoSection() {
    val context = LocalContext.current
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = spacing.small, bottom = spacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
    ) {
        Text(
            "KhanaBook Lite v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            color = TextGold.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
        TextButton(onClick = {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:support@khanabook.com")
                putExtra(Intent.EXTRA_SUBJECT, "KhanaBook Lite Support")
            }
            try { context.startActivity(intent) } catch (_: Exception) {}
        }) {
            Text("Contact Support", color = PrimaryGold.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun copyUriToInternalStorage(context: Context, uri: Uri, fileName: String): String? {
    return try { context.contentResolver.openInputStream(uri)?.use { input -> File(context.filesDir, fileName).let { file -> FileOutputStream(file).use { output -> input.copyTo(output) }; file.absolutePath } } } catch (_: Exception) { null }
}

@Composable
fun LogoutSection(viewModel: com.khanabook.lite.pos.ui.viewmodel.LogoutViewModel) {
    val spacing = KhanaBookTheme.spacing
    val context = LocalContext.current
    val logoutState by viewModel.logoutState.collectAsStateWithLifecycle()
    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(logoutState) { if (logoutState is com.khanabook.lite.pos.ui.viewmodel.LogoutState.LoggedOut) Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show() }

    if (logoutState is com.khanabook.lite.pos.ui.viewmodel.LogoutState.WarningOfflineData) {
        val warning = logoutState as com.khanabook.lite.pos.ui.viewmodel.LogoutState.WarningOfflineData
        AlertDialog(onDismissRequest = { viewModel.cancelLogout() }, title = { Text("Unsynced Data Warning", color = DangerRed) }, text = { Text("You have ${warning.totalCount} records not synced. Logging out will delete them.") }, confirmButton = { TextButton(onClick = { viewModel.forceLogoutDespiteWarning() }) { Text("Logout Anyway", color = DangerRed) } }, dismissButton = { TextButton(onClick = { viewModel.cancelLogout() }) { Text("Cancel") } })
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = DarkBrown2,
            title = { Text("Sign Out?", color = TextLight, style = MaterialTheme.typography.titleLarge) },
            text = { Text("You will be signed out of this device.", color = TextGold.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showConfirmDialog = false; viewModel.initiateLogout() }) {
                    Text("Sign Out", color = DangerRed, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel", color = PrimaryGold, style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    val iconSize = KhanaBookTheme.iconSize
    Column(modifier = Modifier.fillMaxWidth().padding(spacing.medium), verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
        Text("Account Session", color = TextLight, style = MaterialTheme.typography.titleMedium)
        Button(onClick = { showConfirmDialog = true }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = DangerRed), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(iconSize.small))
            Spacer(modifier = Modifier.width(spacing.small))
            Text("Sign Out", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ─── App Lock Configuration ──────────────────────────────────────────────────

@Composable
fun AppLockConfigView(
    onBack: () -> Unit,
    viewModel: com.khanabook.lite.pos.ui.viewmodel.AppLockViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize

    val setupState by viewModel.pinSetupState.collectAsStateWithLifecycle()
    var isEnabled by remember { mutableStateOf(viewModel.isPinEnabled()) }
    val showBiometric = remember { viewModel.hasBiometric(context) }

    LaunchedEffect(setupState) {
        if (setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Success) {
            isEnabled = viewModel.isPinEnabled()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        // Status card
        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(spacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (isEnabled) SuccessGreen else TextGold.copy(alpha = 0.5f),
                    modifier = Modifier.size(iconSize.medium)
                )
                Spacer(modifier = Modifier.width(spacing.medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text("App Lock", color = TextLight, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (isEnabled) "PIN lock is active" else "Disabled — anyone can open the app",
                        color = if (isEnabled) SuccessGreen else TextGold.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { enable ->
                        if (enable) viewModel.startEnablePin()
                        else viewModel.startDisablePin()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SuccessGreen,
                        checkedTrackColor = SuccessGreen.copy(alpha = 0.4f),
                        uncheckedThumbColor = TextGold.copy(alpha = 0.5f),
                        uncheckedTrackColor = DarkBrown1
                    )
                )
            }
        }

        if (isEnabled && setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Idle) {
            KhanaBookCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBG),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(spacing.medium), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    Text("PIN Options", color = TextLight, style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(
                        onClick = { viewModel.startChangePin() },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, BorderGold),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Lock, null, tint = PrimaryGold, modifier = Modifier.size(iconSize.xsmall))
                        Spacer(modifier = Modifier.width(spacing.small))
                        Text("Change PIN", color = PrimaryGold, style = MaterialTheme.typography.labelLarge)
                    }
                    if (showBiometric) {
                        Text(
                            "Biometric unlock is available on this device and will be used alongside your PIN.",
                            color = TextGold.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Success) {
            Text(
                (setupState as com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Success).message,
                color = SuccessGreen,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        // PIN entry steps driven by ViewModel state
        when (val state = setupState) {
            is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.EnterNew -> {
                Text(
                    "Set a new 4-digit PIN",
                    color = TextLight,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                InlinePinEntry(
                    pin = state.pin,
                    onDigit = { viewModel.onSetupDigit(it) },
                    onDelete = { viewModel.onSetupDelete() },
                    errorMessage = state.error
                )
                TextButton(onClick = { viewModel.resetSetupState() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Cancel", color = TextGold.copy(alpha = 0.6f))
                }
            }
            is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.ConfirmNew -> {
                Text(
                    "Confirm your PIN",
                    color = TextLight,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                InlinePinEntry(
                    pin = state.pin,
                    onDigit = { viewModel.onSetupDigit(it) },
                    onDelete = { viewModel.onSetupDelete() },
                    errorMessage = state.error
                )
                TextButton(onClick = { viewModel.resetSetupState() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Cancel", color = TextGold.copy(alpha = 0.6f))
                }
            }
            is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.EnterCurrent -> {
                Text(
                    if (state.nextStep != null) "Enter current PIN to verify" else "Enter current PIN to disable",
                    color = TextLight,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                InlinePinEntry(
                    pin = state.pin,
                    onDigit = { viewModel.onSetupDigit(it) },
                    onDelete = { viewModel.onSetupDelete() },
                    errorMessage = state.error
                )
                TextButton(onClick = { viewModel.resetSetupState() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Cancel", color = TextGold.copy(alpha = 0.6f))
                }
            }
            else -> {}
        }

        Spacer(modifier = Modifier.height(spacing.extraLarge))
    }
}
