package com.khanabook.lite.pos.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.khanabook.lite.pos.data.local.relation.MenuWithVariants
import com.khanabook.lite.pos.domain.manager.BluetoothPrinterManager
import com.khanabook.lite.pos.domain.util.*
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import java.io.File
import java.io.FileOutputStream
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

    // Improved default gesture support:
    // If we are in a sub-section, swiping back returns to the main Settings list.
    // If we are already in the main Settings list, swiping back returns to the Home tab.
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
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                            "menu" -> "Settings"
                            else -> "Settings"
                        },
                        modifier = Modifier.weight(1f),
                        color = PrimaryGold,
                        fontSize = if (section == "menu") 24.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (section) {
                    "menu" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            ProfileCard(currentUser, profile)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            if (isWideScreen) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        SettingsItem(icon = Icons.Filled.Store, text = "Shop Configuration") { section = "shop" }
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        SettingsItem(icon = Icons.AutoMirrored.Filled.ReceiptLong, text = "Menu Configuration") { section = "menu_config" }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        SettingsItem(icon = Icons.Filled.CreditCard, text = "Payment Configuration") { section = "payment" }
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        SettingsItem(icon = Icons.Filled.Print, text = "Printer Configuration") { section = "printer" }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        SettingsItem(icon = Icons.Filled.Settings, text = "Tax Configuration") { section = "tax" }
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            } else {
                                SettingsItem(icon = Icons.Filled.Store, text = "Shop/Restaurant Configuration") { section = "shop" }
                                SettingsItem(icon = Icons.AutoMirrored.Filled.ReceiptLong, text = "Menu Configuration") { section = "menu_config" }
                                SettingsItem(icon = Icons.Filled.CreditCard, text = "Payment Configuration") { section = "payment" }
                                SettingsItem(icon = Icons.Filled.Print, text = "Printer Configuration") { section = "printer" }
                                SettingsItem(icon = Icons.Filled.Settings, text = "Tax Configuration") { section = "tax" }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = CardBG),
                                border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.3f))
                            ) {
                                LogoutSection(logoutViewModel)
                            }
                            Spacer(modifier = Modifier.height(32.dp))
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
                }
            }
        }
    }
}

@Composable
fun ProfileCard(user: UserEntity?, profile: RestaurantProfileEntity?) {
    val displayName = profile?.shopName?.takeIf { it.isNotBlank() } ?: user?.name?.takeIf { it.isNotBlank() } ?: "Guest"
    val displayPhone = profile?.whatsappNumber?.takeIf { it.isNotBlank() } ?: user?.whatsappNumber ?: ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkBrown2.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(50.dp).background(PrimaryGold, CircleShape), contentAlignment = Alignment.Center) {
                Text(text = displayName.take(1).uppercase(), color = DarkBrown1, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = displayName, color = TextLight, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (displayPhone.isNotBlank()) {
                    Text(text = displayPhone, color = TextGold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardBG),
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = PrimaryGold, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(text, color = TextLight, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = PrimaryGold)
        }
    }
}

@Composable
fun ConfigCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBG),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.3f))
    ) { Column(modifier = Modifier.padding(20.dp)) { content() } }
}

@Composable
private fun ShopConfigView(profile: RestaurantProfileEntity?, viewModel: SettingsViewModel, authViewModel: AuthViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
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

    // Dirty flag: true when any field differs from the saved profile snapshot.
    val isDirty = remember(name, address, whatsapp, email, consent, reviewUrl, logoPath, profile) {
        name != (profile?.shopName ?: "") ||
        address != (profile?.shopAddress ?: "") ||
        whatsapp != (profile?.whatsappNumber ?: "") ||
        email != (profile?.email ?: "") ||
        consent != (profile?.emailInvoiceConsent ?: false) ||
        reviewUrl != (profile?.reviewUrl ?: "") ||
        logoPath != profile?.logoPath
    }

    // Back-navigation guard: triggers on both arrow click and swipe gesture
    var showUnsavedDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = isDirty && !saveProfileLoading) {
        showUnsavedDialog = true
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            containerColor = DarkBrown2,
            title = { Text("Unsaved Changes", color = PrimaryGold, fontWeight = FontWeight.Bold) },
            text = { Text("You have unsaved changes. Discard them?", color = TextLight) },
            confirmButton = {
                TextButton(onClick = { showUnsavedDialog = false; onBack() }) {
                    Text("Discard", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog = false }) {
                    Text("Keep Editing", color = PrimaryGold)
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

    // Clear user check error if phone changes
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
            .padding(16.dp)
    ) {
        ConfigCard {
            Text("Shop Profile", color = PrimaryGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            if (isCompactWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.size(100.dp).background(Color.White).border(1.dp, Color.LightGray), contentAlignment = Alignment.Center) {
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
                            Icon(Icons.Default.Storefront, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                        }
                    }
                    OutlinedButton(onClick = { logoLauncher.launch("image/*") }, border = BorderStroke(1.dp, PrimaryGold), shape = RoundedCornerShape(20.dp)) { Text("Change Logo", color = PrimaryGold) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.size(100.dp).background(Color.White).border(1.dp, Color.LightGray), contentAlignment = Alignment.Center) {
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
                            Icon(Icons.Default.Storefront, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                        }
                    }
                    OutlinedButton(onClick = { logoLauncher.launch("image/*") }, border = BorderStroke(1.dp, PrimaryGold), shape = RoundedCornerShape(20.dp)) { Text("Change Logo", color = PrimaryGold) }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            ParchmentTextField(value = name, onValueChange = { name = it }, label = "Shop Name")
            Spacer(modifier = Modifier.height(12.dp))
            ParchmentTextField(value = address, onValueChange = { address = it }, label = "Shop Address")
            val isPhoneValid = ValidationUtils.isValidPhone(whatsapp)
            val numberChanged = whatsapp != (profile?.whatsappNumber ?: "")

            Spacer(modifier = Modifier.height(12.dp))
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
                        // Reverted to original number — treat as verified
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
                            modifier = Modifier.padding(end = 4.dp).height(36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            enabled = isPhoneValid && !isUserChecking && userExistsError == null
                        ) {
                            Text("Send OTP", color = DarkBrown1, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )

            if (otpSent && numberChanged) {
                Spacer(modifier = Modifier.height(12.dp))
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
                            Text(String.format("%02d:%02d", otpTimer / 60, otpTimer % 60), color = TextLight, fontSize = 14.sp, modifier = Modifier.padding(end = 16.dp))
                        } else if (isOtpVerified) {
                            Icon(Icons.Default.Lock, contentDescription = "Verified", tint = SuccessGreen, modifier = Modifier.padding(end = 16.dp))
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            ParchmentTextField(value = email, onValueChange = { email = it }, label = "Email")
            Spacer(modifier = Modifier.height(12.dp))
            ParchmentTextField(value = reviewUrl, onValueChange = { reviewUrl = it }, label = "Review Link (Google Review / Feedback)")
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = consent, onCheckedChange = { consent = it }, colors = CheckboxDefaults.colors(checkedColor = PrimaryGold))
                Text("Receive invoice copies on Email", color = TextGold, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Save is enabled only when there are actual changes AND WhatsApp is verified (if changed)
            val isSaveEnabled = isDirty && (!numberChanged || isOtpVerified) && !saveProfileLoading

            // Subtle animation: scale + alpha pulse when the button becomes enabled
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

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        // Guard: do nothing if not dirty or already saving
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
                                // Preserve all other settings
                                country = profile.country,
                                fssaiNumber = profile.fssaiNumber,
                                gstEnabled = profile.gstEnabled,
                                gstin = profile.gstin,
                                isTaxInclusive = profile.isTaxInclusive,
                                gstPercentage = profile.gstPercentage,
                                customTaxName = profile.customTaxName,
                                customTaxNumber = profile.customTaxNumber,
                                customTaxPercentage = profile.customTaxPercentage,
                                currency = profile.currency,
                                upiEnabled = profile.upiEnabled,
                                upiQrPath = profile.upiQrPath,
                                upiHandle = profile.upiHandle,
                                upiMobile = profile.upiMobile,
                                cashEnabled = profile.cashEnabled,
                                posEnabled = profile.posEnabled,
                                zomatoEnabled = profile.zomatoEnabled,
                                swiggyEnabled = profile.swiggyEnabled,
                                ownWebsiteEnabled = profile.ownWebsiteEnabled,
                                printerEnabled = profile.printerEnabled,
                                printerName = profile.printerName,
                                printerMac = profile.printerMac,
                                paperSize = profile.paperSize,
                                autoPrintOnSuccess = profile.autoPrintOnSuccess,
                                includeLogoInPrint = profile.includeLogoInPrint,
                                printCustomerWhatsapp = profile.printCustomerWhatsapp,
                                dailyOrderCounter = profile.dailyOrderCounter,
                                lifetimeOrderCounter = profile.lifetimeOrderCounter,
                                lastResetDate = profile.lastResetDate,
                                sessionTimeoutMinutes = profile.sessionTimeoutMinutes,
                                restaurantId = profile.restaurantId,
                                deviceId = profile.deviceId,
                                isSynced = false,
                                updatedAt = System.currentTimeMillis(),
                                timezone = profile.timezone,
                                isDeleted = profile.isDeleted,
                                showBranding = profile.showBranding,
                                maskCustomerPhone = profile.maskCustomerPhone,
                                serverId = profile.serverId,
                                serverUpdatedAt = profile.serverUpdatedAt
                            ) ?: RestaurantProfileEntity(
                                shopName = name,
                                shopAddress = address,
                                whatsappNumber = whatsapp,
                                email = email,
                                logoPath = logoPath,
                                emailInvoiceConsent = consent,
                                reviewUrl = reviewUrl,
                                upiMobile = whatsapp,
                                lastResetDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                            )
                            viewModel.saveProfile(updatedProfile)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .graphicsLayer {
                            scaleX = saveButtonScale
                            scaleY = saveButtonScale
                            alpha = saveButtonAlpha
                        },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(24.dp),
                    enabled = isSaveEnabled
                ) {
                    if (saveProfileLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Save", color = Color.White)
                    }
                }
                OutlinedButton(
                    onClick = {
                        viewModel.resetDailyCounter()
                        Toast.makeText(context, "Daily order counter reset", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    border = BorderStroke(1.dp, DangerRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                    shape = RoundedCornerShape(24.dp)
                ) { Text("Reset Counter", fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun PaymentConfigView(profile: RestaurantProfileEntity?, onSave: (RestaurantProfileEntity) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
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

    
    LaunchedEffect(profile) {
        profile?.let {
            currency = it.currency ?: "INR"
            upiSupported = it.upiEnabled
            upiHandle = it.upiHandle ?: ""
            upiMobile = it.upiMobile ?: ""
            qrPath = it.upiQrPath
            cashEnabled = it.cashEnabled
            posEnabled = it.posEnabled
            zomatoEnabled = it.zomatoEnabled
            swiggyEnabled = it.swiggyEnabled
            ownWebsiteEnabled = it.ownWebsiteEnabled
        }
    }

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
            .padding(16.dp)
    ) {
        ConfigCard {
            ParchmentTextField(value = currency, onValueChange = { currency = it }, label = "Currency *")
            Spacer(modifier = Modifier.height(24.dp))
            Text("Enable Payment Methods", color = PrimaryGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            PaymentToggle("Cash Payment", cashEnabled) { cashEnabled = it }
            PaymentToggle("POS Machine", posEnabled) { posEnabled = it }
            PaymentToggle("Zomato Orders", zomatoEnabled) { zomatoEnabled = it }
            PaymentToggle("Swiggy Orders", swiggyEnabled) { swiggyEnabled = it }
            PaymentToggle("Own Website", ownWebsiteEnabled) { ownWebsiteEnabled = it }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = upiSupported, onCheckedChange = { upiSupported = it }, colors = CheckboxDefaults.colors(checkedColor = SuccessGreen))
                Text("Enable UPI QR Payments", color = TextGold, fontSize = 14.sp)
            }
            if (upiSupported) {
                Spacer(modifier = Modifier.height(20.dp))
                if (isCompactWidth) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.size(100.dp).background(Color.White).border(1.dp, Color.LightGray).padding(4.dp), contentAlignment = Alignment.Center) {
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
                                Icon(Icons.Default.QrCode, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                            }
                        }
                        OutlinedButton(onClick = { qrLauncher.launch("image/*") }, border = BorderStroke(1.dp, PrimaryGold), shape = RoundedCornerShape(20.dp)) { Text("Upload QR Code", color = PrimaryGold) }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.size(100.dp).background(Color.White).border(1.dp, Color.LightGray).padding(4.dp), contentAlignment = Alignment.Center) {
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
                                Icon(Icons.Default.QrCode, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                            }
                        }
                        OutlinedButton(onClick = { qrLauncher.launch("image/*") }, border = BorderStroke(1.dp, PrimaryGold), shape = RoundedCornerShape(20.dp)) { Text("Upload QR Code", color = PrimaryGold) }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                ParchmentTextField(value = upiHandle, onValueChange = { upiHandle = it }, label = "UPI Handle")
                Spacer(modifier = Modifier.height(12.dp))
                ParchmentTextField(value = upiMobile, onValueChange = { upiMobile = it }, label = "UPI Mobile Number")
            }
            Spacer(modifier = Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            // Preserve all other settings
                            shopName = profile.shopName,
                            shopAddress = profile.shopAddress,
                            whatsappNumber = profile.whatsappNumber,
                            email = profile.email,
                            logoPath = profile.logoPath,
                            fssaiNumber = profile.fssaiNumber,
                            emailInvoiceConsent = profile.emailInvoiceConsent,
                            country = profile.country,
                            gstEnabled = profile.gstEnabled,
                            gstin = profile.gstin,
                            isTaxInclusive = profile.isTaxInclusive,
                            gstPercentage = profile.gstPercentage,
                            customTaxName = profile.customTaxName,
                            customTaxNumber = profile.customTaxNumber,
                            customTaxPercentage = profile.customTaxPercentage,
                            printerEnabled = profile.printerEnabled,
                            printerName = profile.printerName,
                            printerMac = profile.printerMac,
                            paperSize = profile.paperSize,
                            autoPrintOnSuccess = profile.autoPrintOnSuccess,
                            includeLogoInPrint = profile.includeLogoInPrint,
                            printCustomerWhatsapp = profile.printCustomerWhatsapp,
                            dailyOrderCounter = profile.dailyOrderCounter,
                            lifetimeOrderCounter = profile.lifetimeOrderCounter,
                            lastResetDate = profile.lastResetDate,
                            sessionTimeoutMinutes = profile.sessionTimeoutMinutes,
                            restaurantId = profile.restaurantId,
                            deviceId = profile.deviceId,
                            isSynced = false,
                            updatedAt = System.currentTimeMillis(),
                            timezone = profile.timezone,
                            reviewUrl = profile.reviewUrl,
                            isDeleted = profile.isDeleted,
                            showBranding = profile.showBranding,
                            maskCustomerPhone = profile.maskCustomerPhone,
                            serverId = profile.serverId,
                            serverUpdatedAt = profile.serverUpdatedAt
                        )?.let { onSave(it) }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(24.dp)
                ) { Text("Save", color = Color.White) }
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(48.dp), border = BorderStroke(1.dp, TextGold), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = TextGold)) { Text("Back") }
            }
        }
    }
}

@Composable
fun PaymentToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextGold, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedTrackColor = SuccessGreen))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrinterConfigView(profile: RestaurantProfileEntity?, onSave: (RestaurantProfileEntity) -> Unit, onBack: () -> Unit, viewModel: SettingsViewModel) {
    var enabled by remember { mutableStateOf(profile?.printerEnabled ?: false) }
    var paper58 by remember { mutableStateOf((profile?.paperSize ?: "58mm") == "58mm") }
    var autoPrint by remember { mutableStateOf(profile?.autoPrintOnSuccess ?: false) }
    var includeLogo by remember { mutableStateOf(profile?.includeLogoInPrint ?: true) }
    var printWhatsapp by remember { mutableStateOf(profile?.printCustomerWhatsapp ?: true) }
    var maskPhone by remember { mutableStateOf(profile?.maskCustomerPhone ?: true) }
    val context = LocalContext.current
    var isBtActive by remember { mutableStateOf(viewModel.isBluetoothEnabled(context)) }
    
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    isBtActive = (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_ON)
                }
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    val btDevices by viewModel.btDevices.collectAsStateWithLifecycle()
    val btIsScanning by viewModel.btIsScanning.collectAsStateWithLifecycle()
    val btIsConnecting by viewModel.btIsConnecting.collectAsStateWithLifecycle()
    val btConnectResult by viewModel.btConnectResult.collectAsStateWithLifecycle()
    val btIsConnected by viewModel.btIsConnected.collectAsStateWithLifecycle()
    var showBtSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(profile) {
        profile?.let {
            enabled = it.printerEnabled
            paper58 = it.paperSize == "58mm"
            autoPrint = it.autoPrintOnSuccess
            includeLogo = it.includeLogoInPrint
            printWhatsapp = it.printCustomerWhatsapp
            maskPhone = it.maskCustomerPhone
        }
    }

    val bluetoothLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            isBtActive = true
            if (enabled) {
                viewModel.startBluetoothScan(context)
                showBtSheet = true
            }
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms[Manifest.permission.BLUETOOTH_CONNECT] == true && perms[Manifest.permission.BLUETOOTH_SCAN] == true
        } else {
            perms[Manifest.permission.BLUETOOTH] == true && perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }
        if (ok) {
            if (!viewModel.isBluetoothEnabled(context)) {
                bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                viewModel.startBluetoothScan(context)
                showBtSheet = true
            }
        } else {
            Toast.makeText(context, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
            enabled = false
        }
    }

    LaunchedEffect(btConnectResult) {
        btConnectResult?.let { Toast.makeText(context, if (it) "Printer Connected!" else "Connection Failed", Toast.LENGTH_SHORT).show(); if (it) showBtSheet = false; viewModel.clearBtConnectResult() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp)
    ) {
        ConfigCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Bluetooth Printer", color = TextGold, fontWeight = FontWeight.Medium)
                Switch(
                    checked = enabled,
                    onCheckedChange = { 
                        enabled = it
                        if (it) {
                            if (!viewModel.hasBluetoothPermissions(context)) {
                                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                                } else {
                                    arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                                permissionLauncher.launch(perms)
                            } else if (!viewModel.isBluetoothEnabled(context)) {
                                bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            } else {
                                viewModel.startBluetoothScan(context)
                                showBtSheet = true 
                            }
                        }
                    }, 
                    colors = SwitchDefaults.colors(checkedTrackColor = VegGreen)
                )
            }
            if (enabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().border(1.dp, BorderGold.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(12.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Connected: ${profile?.printerName ?: "None"}", color = TextLight, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (btIsConnected) SuccessGreen else DangerRed, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (btIsConnected) "Online" else "Offline",
                                color = if (btIsConnected) SuccessGreen else DangerRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text("MAC: ${profile?.printerMac ?: "---"}", color = TextGold, fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { 
                        if (!viewModel.hasBluetoothPermissions(context)) {
                            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                            } else {
                                arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                            permissionLauncher.launch(perms)
                        } else if (!viewModel.isBluetoothEnabled(context)) {
                            bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        } else {
                            viewModel.startBluetoothScan(context)
                            showBtSheet = true 
                        }
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Brown500)) {
                        Text("Scan", color = Color.White)
                    }
                    Button(onClick = { viewModel.testPrint() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)) {
                        Text("Test Print", color = DarkBrown1)
                    }
                }
            }
        }
        ConfigCard {
            Text("Paper Size", color = PrimaryGold, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = paper58, onClick = { paper58 = true }, colors = RadioButtonDefaults.colors(selectedColor = PrimaryGold))
                Text("58mm", color = TextGold)
                Spacer(modifier = Modifier.width(32.dp))
                RadioButton(selected = !paper58, onClick = { paper58 = false }, colors = RadioButtonDefaults.colors(selectedColor = PrimaryGold))
                Text("80mm", color = TextGold)
            }
        }
        ConfigCard {
            Text("Print Options", color = PrimaryGold, fontWeight = FontWeight.Bold)
            PrinterOptionRow("Auto Print Success", autoPrint) { autoPrint = it }
            PrinterOptionRow("Include Logo", includeLogo) { includeLogo = it }
            PrinterOptionRow("Print WhatsApp", printWhatsapp) { printWhatsapp = it }
            PrinterOptionRow("Privacy-First (Mask Phone)", maskPhone) { maskPhone = it }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                profile?.copy(
                    printerEnabled = enabled,
                    paperSize = if (paper58) "58mm" else "80mm",
                    autoPrintOnSuccess = autoPrint,
                    includeLogoInPrint = includeLogo,
                    printCustomerWhatsapp = printWhatsapp,
                    maskCustomerPhone = maskPhone,
                    printerName = profile.printerName,
                    printerMac = profile.printerMac,
                    // Preserve all other settings
                    shopName = profile.shopName,
                    shopAddress = profile.shopAddress,
                    whatsappNumber = profile.whatsappNumber,
                    email = profile.email,
                    logoPath = profile.logoPath,
                    fssaiNumber = profile.fssaiNumber,
                    emailInvoiceConsent = profile.emailInvoiceConsent,
                    country = profile.country,
                    gstEnabled = profile.gstEnabled,
                    gstin = profile.gstin,
                    isTaxInclusive = profile.isTaxInclusive,
                    gstPercentage = profile.gstPercentage,
                    customTaxName = profile.customTaxName,
                    customTaxNumber = profile.customTaxNumber,
                    customTaxPercentage = profile.customTaxPercentage,
                    currency = profile.currency,
                    upiEnabled = profile.upiEnabled,
                    upiQrPath = profile.upiQrPath,
                    upiHandle = profile.upiHandle,
                    upiMobile = profile.upiMobile,
                    cashEnabled = profile.cashEnabled,
                    posEnabled = profile.posEnabled,
                    zomatoEnabled = profile.zomatoEnabled,
                    swiggyEnabled = profile.swiggyEnabled,
                    ownWebsiteEnabled = profile.ownWebsiteEnabled,
                    dailyOrderCounter = profile.dailyOrderCounter,
                    lifetimeOrderCounter = profile.lifetimeOrderCounter,
                    lastResetDate = profile.lastResetDate,
                    sessionTimeoutMinutes = profile.sessionTimeoutMinutes,
                    restaurantId = profile.restaurantId,
                    deviceId = profile.deviceId,
                    isSynced = false,
                    updatedAt = System.currentTimeMillis(),
                    timezone = profile.timezone,
                    reviewUrl = profile.reviewUrl,
                    isDeleted = profile.isDeleted,
                    showBranding = profile.showBranding,
                    serverId = profile.serverId,
                    serverUpdatedAt = profile.serverUpdatedAt
                )?.let { onSave(it) }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green800)
        ) { Text("SAVE SETTINGS", color = Color.White) }
    }

    if (showBtSheet) {
        ModalBottomSheet(onDismissRequest = { viewModel.stopBluetoothScan(); showBtSheet = false }, sheetState = sheetState, containerColor = DarkBrownSheet) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text("Select Printer", color = PrimaryGold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (btIsScanning) CircularProgressIndicator(color = PrimaryGold, modifier = Modifier.padding(16.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(btDevices, key = { it.address }) { device ->
                        val isSelected = device.address == profile?.printerMac
                        DeviceRow(
                            device = device, 
                            isConnecting = btIsConnecting,
                            isSelected = isSelected,
                            isConnected = isSelected && btIsConnected
                        ) { 
                            viewModel.connectToPrinter(context, device) 
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrinterOptionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked, 
            onCheckedChange = onCheckedChange, 
            colors = CheckboxDefaults.colors(checkedColor = PrimaryGold)
        )
        Text(label, color = TextGold, fontSize = 14.sp)
    }
}

@Composable
fun DeviceRow(device: BluetoothDevice, isConnecting: Boolean, isSelected: Boolean = false, isConnected: Boolean = false, onClick: () -> Unit) {
    @Suppress("MissingPermission")
    val name = device.name ?: "Unknown"
    val border = if (isSelected) BorderStroke(2.dp, PrimaryGold) else null
    val backgroundColor = if (isSelected) DarkBrown1 else DarkBrown1.copy(alpha = 0.5f)
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = !isConnecting) { onClick() }, 
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = border
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth, 
                null, 
                tint = if (isSelected) PrimaryGold else TextGold
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = TextLight, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                Text(device.address, color = if (isSelected) PrimaryGold.copy(alpha = 0.7f) else TextGold, fontSize = 11.sp)
            }
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PrimaryGold)
            } else if (isConnected) {
                Box(modifier = Modifier.size(8.dp).background(SuccessGreen, CircleShape))
            }
        }
    }
}

@Composable
private fun TaxConfigView(profile: RestaurantProfileEntity?, onSave: (RestaurantProfileEntity) -> Unit, onBack: () -> Unit) {
    var country by remember { mutableStateOf(profile?.country ?: "India") }
    var gstEnabled by remember { mutableStateOf(profile?.gstEnabled ?: false) }
    var gstNumber by remember { mutableStateOf(profile?.gstin ?: "") }
    var gstPct by remember { mutableStateOf(profile?.gstPercentage?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "0") }
    var fssaiNumber by remember { mutableStateOf(profile?.fssaiNumber ?: "") }
    
    val isFssaiValid = if (country.equals("India", true)) {
        fssaiNumber.isNotBlank() && fssaiNumber.length >= 10
    } else {
        true 
    }
    val isGstValid = !gstEnabled || (gstNumber.isNotBlank() && ValidationUtils.isValidTaxPercentage(gstPct))
    val isSaveEnabled = isFssaiValid && isGstValid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp)
    ) {
        ConfigCard {
            ParchmentTextField(value = country, onValueChange = { country = it }, label = "Country")
            Spacer(modifier = Modifier.height(16.dp))
            ParchmentTextField(
                value = fssaiNumber, 
                onValueChange = { if (it.length <= 14) fssaiNumber = it }, 
                label = "FSSAI Number (Mandatory)",
                isError = fssaiNumber.isNotEmpty() && !isFssaiValid,
                supportingText = if (fssaiNumber.isNotEmpty() && !isFssaiValid) "Invalid FSSAI Number" else null
            )
            if (country.equals("India", true)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("GST Registered", color = TextGold)
                    Switch(checked = gstEnabled, onCheckedChange = { gstEnabled = it }, colors = SwitchDefaults.colors(checkedTrackColor = VegGreen))
                }
                if (gstEnabled) {
                    ParchmentTextField(
                        value = gstNumber, 
                        onValueChange = { if (it.length <= 15 && it.all { char -> char.isLetterOrDigit() }) gstNumber = it.uppercase() }, 
                        label = "GSTIN (Mandatory)",
                        isError = gstEnabled && gstNumber.isEmpty()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ParchmentTextField(
                        value = gstPct, 
                        onValueChange = { gstPct = it }, 
                        label = "GST % (Mandatory)",
                        isError = gstEnabled && !ValidationUtils.isValidTaxPercentage(gstPct),
                        supportingText = if (gstEnabled && !ValidationUtils.isValidTaxPercentage(gstPct)) "Invalid GST %" else null
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    profile?.copy(
                        country = country,
                        gstEnabled = gstEnabled,
                        gstin = gstNumber,
                        gstPercentage = gstPct.toDoubleOrNull() ?: 0.0,
                        fssaiNumber = fssaiNumber,
                        // Preserve all other settings
                        shopName = profile.shopName,
                        shopAddress = profile.shopAddress,
                        whatsappNumber = profile.whatsappNumber,
                        email = profile.email,
                        logoPath = profile.logoPath,
                        emailInvoiceConsent = profile.emailInvoiceConsent,
                        isTaxInclusive = profile.isTaxInclusive,
                        customTaxName = profile.customTaxName,
                        customTaxNumber = profile.customTaxNumber,
                        customTaxPercentage = profile.customTaxPercentage,
                        currency = profile.currency,
                        upiEnabled = profile.upiEnabled,
                        upiQrPath = profile.upiQrPath,
                        upiHandle = profile.upiHandle,
                        upiMobile = profile.upiMobile,
                        cashEnabled = profile.cashEnabled,
                        posEnabled = profile.posEnabled,
                        zomatoEnabled = profile.zomatoEnabled,
                        swiggyEnabled = profile.swiggyEnabled,
                        ownWebsiteEnabled = profile.ownWebsiteEnabled,
                        printerEnabled = profile.printerEnabled,
                        printerName = profile.printerName,
                        printerMac = profile.printerMac,
                        paperSize = profile.paperSize,
                        autoPrintOnSuccess = profile.autoPrintOnSuccess,
                        includeLogoInPrint = profile.includeLogoInPrint,
                        printCustomerWhatsapp = profile.printCustomerWhatsapp,
                        dailyOrderCounter = profile.dailyOrderCounter,
                        lifetimeOrderCounter = profile.lifetimeOrderCounter,
                        lastResetDate = profile.lastResetDate,
                        sessionTimeoutMinutes = profile.sessionTimeoutMinutes,
                        restaurantId = profile.restaurantId,
                        deviceId = profile.deviceId,
                        isSynced = false,
                        updatedAt = System.currentTimeMillis(),
                        timezone = profile.timezone,
                        reviewUrl = profile.reviewUrl,
                        isDeleted = profile.isDeleted,
                        showBranding = profile.showBranding,
                        maskCustomerPhone = profile.maskCustomerPhone,
                        serverId = profile.serverId,
                        serverUpdatedAt = profile.serverUpdatedAt
                    )?.let { onSave(it) }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                enabled = isSaveEnabled
            ) { Text("Save") }
        }
    }
}

private fun copyUriToInternalStorage(context: Context, uri: Uri, fileName: String): String? {
    return try { context.contentResolver.openInputStream(uri)?.use { input -> File(context.filesDir, fileName).let { file -> FileOutputStream(file).use { output -> input.copyTo(output) }; file.absolutePath } } } catch (_: Exception) { null }
}

private fun loadBitmap(path: String): Bitmap? {
    return try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null }
}

@Composable
fun LogoutSection(viewModel: com.khanabook.lite.pos.ui.viewmodel.LogoutViewModel) {
    val context = LocalContext.current
    val logoutState by viewModel.logoutState.collectAsStateWithLifecycle()

    LaunchedEffect(logoutState) {
        if (logoutState is com.khanabook.lite.pos.ui.viewmodel.LogoutState.LoggedOut) {
            Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
        }
    }

    if (logoutState is com.khanabook.lite.pos.ui.viewmodel.LogoutState.WarningOfflineData) {
        val count = (logoutState as com.khanabook.lite.pos.ui.viewmodel.LogoutState.WarningOfflineData).count
        AlertDialog(
            onDismissRequest = { viewModel.cancelLogout() },
            title = { Text("Offline Data Warning", color = Color.Red) },
            text = { Text("You have $count unsynced bills. Logging out will delete them. Proceed?") },
            confirmButton = {
                TextButton(onClick = { viewModel.forceLogoutDespiteWarning() }) {
                    Text("Logout Anyway", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelLogout() }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Account Session", color = TextLight, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { viewModel.initiateLogout() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out", fontSize = 14.sp)
            }
        }
    }
}
