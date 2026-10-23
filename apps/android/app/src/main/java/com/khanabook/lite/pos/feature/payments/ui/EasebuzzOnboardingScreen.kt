@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.feature.payments.ui
import com.khanabook.lite.pos.core.theme.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.feature.payments.data.AddressProofType
import com.khanabook.lite.pos.core.designsystem.KhanaBookLoadingOverlay
import com.khanabook.lite.pos.core.designsystem.KhanaToast
import com.khanabook.lite.pos.core.designsystem.StickyBottomScaffold
import com.khanabook.lite.pos.core.designsystem.ToastKind
import com.khanabook.lite.pos.core.util.BrowserLauncher
import kotlinx.coroutines.launch
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.feature.payments.viewmodel.EasebuzzOnboardingViewModel
import com.khanabook.lite.pos.feature.payments.viewmodel.OnboardingEvent
import com.khanabook.lite.pos.feature.payments.viewmodel.OnboardingStep
import com.khanabook.lite.pos.feature.payments.viewmodel.OnboardingUiState

@Composable
fun EasebuzzOnboardingScreen(
    onBack: () -> Unit,
    onOpenComplianceDocs: (() -> Unit)? = null,
    viewModel: EasebuzzOnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val portalCredentials by viewModel.portalCredentials.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OnboardingEvent.Toast -> {
                    val result = KhanaToast.show(
                        event.message,
                        if (event.isError) ToastKind.Error else ToastKind.Success,
                        event.actionLabel
                    )
                    // An actionable message must actually be actionable: tapping opens the
                    // portal instead of leaving the merchant to hunt for a button.
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        event.actionUrl?.let { url ->
                            if (!BrowserLauncher.open(context, url)) {
                                KhanaToast.show(
                                    "No app on this device can open the portal. Link: $url",
                                    ToastKind.Error
                                )
                            }
                        }
                    }
                }
                is OnboardingEvent.OnboardingComplete -> onBack()
                is OnboardingEvent.OpenFile -> { /* handled in ComplianceDocumentsScreen */ }
            }
        }
    }

    Scaffold(
        containerColor = DarkBrown1,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Online Payments Setup",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!viewModel.goBack()) onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryGold
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBrown1)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso))),
            contentAlignment = Alignment.TopCenter
        ) {
          Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = layout.maxContentWidth)
                // No imePadding() here: each step (BusinessDetails/BankDetails via
                // StickyBottomScaffold) owns its own IME inset. Applying it on this
                // wrapper too double-counted the keyboard height.
        ) {
        when (val state = uiState) {
            is OnboardingUiState.Loading -> {
                KhanaBookLoadingOverlay(visible = true, message = "Checking status...")
            }
            is OnboardingUiState.NotStarted -> {
                LaunchedEffect(Unit) {
                    viewModel.startOnboarding()
                }
                BusinessDetailsStep(
                    viewModel = viewModel,
                    isSubmitting = isSubmitting,
                    spacing = spacing
                )
            }
            is OnboardingUiState.InProgress -> {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        (fadeIn() + slideInHorizontally { it / 3 }) togetherWith
                            (fadeOut() + slideOutHorizontally { -it / 3 })
                    },
                    label = "onboarding_step"
                ) { step ->
                    when (step) {
                        OnboardingStep.BusinessDetails -> BusinessDetailsStep(
                            viewModel = viewModel,
                            isSubmitting = isSubmitting,
                            spacing = spacing
                        )
                        OnboardingStep.BankDetails -> BankDetailsStep(
                            viewModel = viewModel,
                            isSubmitting = isSubmitting,
                            spacing = spacing
                        )
                        OnboardingStep.KycStatus -> KycStatusStep(
                            viewModel = viewModel,
                            status = null,
                            isSubmitting = isSubmitting,
                            onOpenComplianceDocs = onOpenComplianceDocs,
                            spacing = spacing
                        )
                    }
                }
            }
            is OnboardingUiState.AwaitingKyc -> {
                KycStatusStep(
                    viewModel = viewModel,
                    status = state.status,
                    isSubmitting = isSubmitting,
                    onOpenComplianceDocs = onOpenComplianceDocs,
                    spacing = spacing
                )
            }
            is OnboardingUiState.Active -> {
                ActiveContent(
                    status = state.status,
                    credentials = portalCredentials,
                    spacing = spacing,
                    onBack = onBack
                )
            }              is OnboardingUiState.Rejected -> {
                  RejectedContent(
                      viewModel = viewModel,
                      status = state.status,
                      isSubmitting = isSubmitting,
                      spacing = spacing
                  )
              }
              is OnboardingUiState.Expired -> {
                  ExpiredContent(
                      viewModel = viewModel,
                      status = state.status,
                      isSubmitting = isSubmitting,
                      spacing = spacing
                  )
              }
            is OnboardingUiState.Error -> {
                ErrorContent(message = state.message, onRetry = { viewModel.loadStatus() }, spacing = spacing)
            }
        }

        if (isSubmitting && uiState is OnboardingUiState.InProgress) {
            KhanaBookLoadingOverlay(visible = true, message = "Submitting...")
        }
        }
        }
    }
}

@Composable
private fun NotStartedContent(onStart: () -> Unit, spacing: Spacing) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = PrimaryGold,
            modifier = Modifier.size(KhanaBookTheme.iconSize.xxlarge)
        )
        Spacer(Modifier.height(spacing.large))
        Text(
            "Accept Online Payments",
            style = MaterialTheme.typography.headlineSmall,
            color = TextLight,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(spacing.medium))
        Text(
            "Register your business with Easebuzz to accept UPI, cards, and netbanking payments directly from customers.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextGold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(spacing.medium))
        Text(
            "You'll need:\n• PAN - proprietor's PAN for a sole proprietorship, firm/company PAN otherwise\n• Aadhaar of the proprietor / authorised signatory (masked or Aadhaar XML is fine; passport, voter ID or driving licence accepted instead)\n• Bank account number and IFSC\n• FSSAI number and one business proof (Shop & Establishment, Udyam/MSME, trade licence, utility bill or Import/Export certificate)\n• Phone number (used to sign in to the KYC portal; an OTP only if Easebuzz asks for one)\n\nGST is optional. You upload the documents yourself, signed in to Easebuzz's KYC portal at the end.",
            style = MaterialTheme.typography.bodySmall,
            color = TextGold.copy(alpha = 0.7f),
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(spacing.extraLarge))
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
            shape = KhanaRadii.button,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Registration", color = DarkBrown1)
        }
    }
}

@Composable
private fun BusinessDetailsStep(
    viewModel: EasebuzzOnboardingViewModel,
    isSubmitting: Boolean,
    spacing: Spacing
) {
    val focusManager = LocalFocusManager.current
    var businessName by remember { mutableStateOf(viewModel.businessName) }
    var legalEntityName by remember { mutableStateOf(viewModel.legalEntityName) }
    var businessType by remember { mutableStateOf(viewModel.businessType) }
    var restaurantFormat by remember { mutableStateOf(viewModel.restaurantFormat) }
    var pan by remember { mutableStateOf(viewModel.pan) }
    var gst by remember { mutableStateOf(viewModel.gst) }
    var fssaiNumber by remember { mutableStateOf(viewModel.fssaiNumber) }
    var businessAddress by remember { mutableStateOf(viewModel.businessAddress) }
    var state by remember { mutableStateOf(viewModel.state) }
    var contactEmail by remember { mutableStateOf(viewModel.contactEmail) }
    var contactPhone by remember { mutableStateOf(viewModel.contactPhone) }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val isPanValid = EasebuzzOnboardingViewModel.isValidPan(pan)
        // Easebuzz CPV matches the registered name against PAN/GST, so the server refuses any
        // submission without it (LEGAL_ENTITY_NAME_REQUIRED) - required for every entity type.
        val isLegalEntityValid = legalEntityName.isNotBlank()
        // FSSAI is mandatory for food merchants server-side (FSSAI_REQUIRED); a blank field here
        // would only fail after both steps are filled.
        val isFssaiValid = fssaiNumber.length == 14
        // Business type is required: it selects the Easebuzz nature code and decides whether the
        // proprietorship proof rules apply, so it can never be inferred by default.
        val isValid = businessName.isNotBlank() && businessType.isNotBlank() &&
            restaurantFormat.isNotBlank() && isPanValid &&
            isLegalEntityValid && isFssaiValid &&
            businessAddress.isNotBlank() && state.isNotBlank() &&
            contactEmail.contains("@") && contactPhone.length == 10

        StickyBottomScaffold(
            bottomBar = {
                Button(
                    onClick = {
                        viewModel.businessName = businessName
                        viewModel.legalEntityName = legalEntityName
                        viewModel.businessType = businessType
                        viewModel.restaurantFormat = restaurantFormat
                        viewModel.pan = pan
                        viewModel.gst = gst
                        viewModel.fssaiNumber = fssaiNumber
                        viewModel.businessAddress = businessAddress
                        viewModel.state = state
                        viewModel.contactEmail = contactEmail
                        viewModel.contactPhone = contactPhone
                        viewModel.submitBusinessDetails()
                    },
                    enabled = isValid && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                    shape = KhanaRadii.button,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Next → Bank Details", color = DarkBrown1)
                }
            },
            bottomBarContainerColor = DarkBrown1
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(spacing.medium)
            ) {
                StepIndicator(current = 1, total = 3, spacing = spacing)
                Spacer(Modifier.height(spacing.medium))
                Text("Business Details", style = MaterialTheme.typography.titleMedium, color = PrimaryGold)
                Spacer(Modifier.height(spacing.medium))

                OnboardingField("Business Name *", businessName, { businessName = it }, focusManager, ImeAction.Next)
                OnboardingField(
                    "Legal Entity Name *",
                    legalEntityName, { legalEntityName = it }, focusManager, ImeAction.Next
                )
                if (legalEntityName.isBlank()) {
                    Text(
                        "Name as registered with PAN/GST - CPV matches it exactly",
                        color = WarningYellow,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = spacing.small, bottom = spacing.small)
                    )
                }
                BusinessTypeDropdown(businessType) { businessType = it }
                // MCC is locked at sub-merchant creation, so the outlet format has to be chosen here.
                RestaurantFormatDropdown(restaurantFormat) { restaurantFormat = it }
                if (restaurantFormat.isBlank()) {
                    Text(
                        "Sets your payment category code - Easebuzz cannot change it after registration.",
                        color = TextGold.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = spacing.small, bottom = spacing.small)
                    )
                }
                OnboardingField("PAN *", pan, { pan = it.uppercase().take(10) }, focusManager, ImeAction.Next,
                    keyboardType = KeyboardType.Text, capitalization = KeyboardCapitalization.Characters)
                // PAN is mandatory for every entity (ERA answer, 2026-10-17) - the proprietor's PAN
                // for a sole proprietorship, the firm/company PAN for everyone else.
                if (!isPanValid) {
                    Text(
                        "Proprietor's PAN for a sole proprietorship; firm or company PAN otherwise.",
                        color = TextGold.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = spacing.small, bottom = spacing.small)
                    )
                }
                OnboardingField("GST Number", gst, { gst = it.uppercase().take(15) }, focusManager, ImeAction.Next,
                    capitalization = KeyboardCapitalization.Characters)
                // GST is optional: Easebuzz confirmed in writing (2026-10-17) that it is not
                // mandatory for sub-merchants. Udyam (URC) covers the business-proof requirement,
                // so this field is only sent when the merchant is GST-registered.
                if (gst.isBlank()) {
                    Text(
                        "Optional - enter your 15-character GSTIN if you are GST-registered. Easebuzz confirmed GST is not mandatory; an Udyam (URC) certificate covers the business proof.",
                        color = TextGold.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = spacing.small, bottom = spacing.small)
                    )
                }
                OnboardingField("FSSAI Number *", fssaiNumber, { 
                    fssaiNumber = it.filter { c -> c.isDigit() }.take(14)
                    if (fssaiNumber.length == 14) {
                        viewModel.lookupFssai(fssaiNumber)
                    } else {
                        viewModel.clearFssaiInfo()
                    }
                }, focusManager, ImeAction.Next, keyboardType = KeyboardType.Number)

                val fssaiInfo by viewModel.fssaiInfo.collectAsStateWithLifecycle()
                val isLookingUpFssai by viewModel.isLookingUpFssai.collectAsStateWithLifecycle()

                if (isLookingUpFssai) {
                    Row(
                        modifier = Modifier.padding(start = spacing.small, bottom = spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = PrimaryGold,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(spacing.small))
                        Text(
                            "Verifying FSSAI license...",
                            color = TextGold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else if (fssaiNumber.isNotBlank() && fssaiNumber.length != 14) {
                    Text(
                        "FSSAI must be 14 digits",
                        color = DangerRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = spacing.small, bottom = spacing.small)
                    )
                } else if (fssaiInfo != null && fssaiInfo?.valid == true) {
                    val info = fssaiInfo!!
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = spacing.small),
                        colors = CardDefaults.cardColors(containerColor = DarkBrown2),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(spacing.medium)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(spacing.small))
                                Text(
                                    "Verified FSSAI License",
                                    color = SuccessGreen,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            info.businessName?.let {
                                Text("Business: $it", color = TextLight, style = MaterialTheme.typography.bodySmall)
                            }
                            info.expiryDate?.let {
                                Text("Valid Upto: $it", color = TextGold, style = MaterialTheme.typography.bodySmall)
                            }
                            if (!info.pan.isNullOrBlank()) {
                                Text("Linked PAN: ${info.pan}", color = TextGold, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(spacing.small))
                            OutlinedButton(
                                onClick = {
                                    info.businessName?.let { if (it.isNotBlank()) businessName = it }
                                    info.legalEntityName?.let { if (it.isNotBlank()) legalEntityName = it }
                                    info.address?.let { if (it.isNotBlank()) businessAddress = it }
                                    info.state?.let { if (it.isNotBlank()) state = it }
                                    if (pan.isBlank() && !info.pan.isNullOrBlank()) {
                                        pan = info.pan.uppercase()
                                    }
                                    if (contactEmail.isBlank() && !info.contactEmail.isNullOrBlank()) {
                                        contactEmail = info.contactEmail.trim()
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryGold),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Auto-fill Details from FSSAI", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                OnboardingField("Business Address *", businessAddress, { businessAddress = it }, focusManager, ImeAction.Next)
                StateDropdown(state) { state = it }
                OnboardingField("Email *", contactEmail, { contactEmail = it.trim() }, focusManager, ImeAction.Next,
                    keyboardType = KeyboardType.Email)
                OnboardingField("Phone *", contactPhone, { contactPhone = it.filter { c -> c.isDigit() }.take(10) },
                    focusManager, ImeAction.Done, keyboardType = KeyboardType.Phone)

                Spacer(Modifier.height(spacing.medium))

                if (pan.length == 10 && !isPanValid) {
                    Text(
                        "Invalid PAN format (expected: AAAAA9999A)",
                        color = DangerRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = spacing.small, bottom = spacing.small)
                    )
                }

                Spacer(Modifier.height(spacing.large))
            }
        }
    }
}

@Composable
private fun BankDetailsStep(
    viewModel: EasebuzzOnboardingViewModel,
    isSubmitting: Boolean,
    spacing: Spacing
) {
    val focusManager = LocalFocusManager.current
    var bankAccountNo by remember { mutableStateOf(viewModel.bankAccountNo) }
    var confirmAccountNo by remember { mutableStateOf(viewModel.bankAccountNo) }
    var ifsc by remember { mutableStateOf(viewModel.ifsc) }
    var bankName by remember { mutableStateOf(viewModel.bankName) }
    var branchName by remember { mutableStateOf(viewModel.branchName) }
    var beneficiaryName by remember { mutableStateOf(viewModel.beneficiaryName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val isIfscValid = EasebuzzOnboardingViewModel.isValidIfsc(ifsc)
        val accountsMatch = bankAccountNo == confirmAccountNo
        val isValid = bankAccountNo.length >= 8 && accountsMatch && isIfscValid &&
            bankName.isNotBlank() && branchName.isNotBlank() && beneficiaryName.isNotBlank()

        StickyBottomScaffold(
            bottomBar = {
                Button(
                    onClick = {
                        viewModel.bankAccountNo = bankAccountNo
                        viewModel.ifsc = ifsc
                        viewModel.bankName = bankName
                        viewModel.branchName = branchName
                        viewModel.beneficiaryName = beneficiaryName
                        viewModel.submitBankDetailsAndOnboard()
                    },
                    enabled = isValid && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                    shape = KhanaRadii.button,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSubmitting) "Submitting..." else "Submit & Continue", color = DarkBrown1)
                }
            },
            bottomBarContainerColor = DarkBrown1
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(spacing.medium)
            ) {
                StepIndicator(current = 2, total = 3, spacing = spacing)
                Spacer(Modifier.height(spacing.medium))
                Text("Bank Details", style = MaterialTheme.typography.titleMedium, color = PrimaryGold)
                Spacer(Modifier.height(spacing.small))
                Text(
                    "Payments will be settled directly to this account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGold.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(spacing.medium))

                OnboardingField("Account Number *", bankAccountNo,
                    { bankAccountNo = it.filter { c -> c.isDigit() } }, focusManager, ImeAction.Next,
                    keyboardType = KeyboardType.Number)
                OnboardingField("Confirm Account Number *", confirmAccountNo,
                    { confirmAccountNo = it.filter { c -> c.isDigit() } }, focusManager, ImeAction.Next,
                    keyboardType = KeyboardType.Number)
                if (confirmAccountNo.isNotBlank() && bankAccountNo != confirmAccountNo) {
                    Text(
                        "Account numbers do not match",
                        color = DangerRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = spacing.small, bottom = spacing.small)
                    )
                }
                OnboardingField("IFSC Code *", ifsc, { 
                    ifsc = it.uppercase().take(11)
                    if (ifsc.length == 11) viewModel.lookupIfsc(ifsc)
                }, focusManager, ImeAction.Next,
                    capitalization = KeyboardCapitalization.Characters)
                
                val ifscInfo by viewModel.ifscBankInfo.collectAsStateWithLifecycle()
                if (ifsc.length == 11 && !EasebuzzOnboardingViewModel.isValidIfsc(ifsc)) {
                    Text(
                        "Invalid IFSC format (expected: ABCD0123456)",
                        color = DangerRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = spacing.small, bottom = spacing.small)
                    )
                } else if (ifscInfo != null) {
                    Text(
                        "✓ ${ifscInfo!!.bankName}, ${ifscInfo!!.branchName}",
                        color = SuccessGreen,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = spacing.small, bottom = spacing.small)
                    )
                    // Auto-fill from IFSC lookup
                    LaunchedEffect(ifscInfo) {
                        ifscInfo?.let {
                            if (bankName.isBlank()) bankName = it.bankName
                            if (branchName.isBlank()) branchName = it.branchName
                        }
                    }
                }
                OnboardingField("Bank Name *", bankName, { bankName = it }, focusManager, ImeAction.Next)
                OnboardingField("Branch Name *", branchName, { branchName = it }, focusManager, ImeAction.Next)
                OnboardingField("Beneficiary Name *", beneficiaryName, { beneficiaryName = it }, focusManager, ImeAction.Done)

                Spacer(Modifier.height(spacing.large))
            }
        }
    }
}

@Composable
private fun KycStatusStep(
    viewModel: EasebuzzOnboardingViewModel,
    status: com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingStatusResponse?,
    isSubmitting: Boolean,
    onOpenComplianceDocs: (() -> Unit)?,
    spacing: Spacing
) {
    KycStatusContent(
        status = status,
        isSubmitting = isSubmitting,
        onRefreshLink = { viewModel.refreshKycPortalLink() },
        onRefreshStatus = { viewModel.loadStatus() },
        onOpenComplianceDocs = onOpenComplianceDocs,
        spacing = spacing
    )
}

/**
 * The KYC status screen without the ViewModel, so its states can be asserted in a UI test.
 *
 * Every action is a callback rather than a viewModel call: the screen has two different failure
 * endings ("link issued" and "no link at all") that look alike unless the copy and the available
 * buttons are checked directly.
 */
@Composable
internal fun KycStatusContent(
    status: com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingStatusResponse?,
    isSubmitting: Boolean,
    onRefreshLink: () -> Unit,
    onRefreshStatus: () -> Unit,
    onOpenComplianceDocs: (() -> Unit)?,
    spacing: Spacing
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(spacing.large))
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = WarningYellow,
            modifier = Modifier.size(KhanaBookTheme.iconSize.xxlarge)
        )
        Spacer(Modifier.height(spacing.medium))
        val hasPortalLink = !status?.kycUrl.isNullOrBlank()
        val titleText = when {
            status?.status == "CPV_PENDING" -> "CPV Physical Verification Pending"
            // Registered but no link: no document has reached Easebuzz, so a copy that says the
            // KYC is under review describes a review nobody started.
            status != null && !hasPortalLink -> "KYC Documents Not Submitted"
            else -> "KYC Verification Pending"
        }
        Text(titleText, style = MaterialTheme.typography.titleMedium, color = TextLight)
        Spacer(Modifier.height(spacing.medium))
        Text(
            if (status != null && !hasPortalLink) {
                "Your outlet is registered with Easebuzz, but no KYC portal link has been issued yet, " +
                    "so your documents have not been submitted for review.\n\n" +
                    "Request a link below. If Easebuzz still cannot issue one, the sub-merchant " +
                    "account needs to be checked before online payments can be activated."
            } else {
                "Your registration has been submitted to Easebuzz for verification. " +
                    "This usually takes 24-48 hours.\n\n" +
                    "You'll receive a notification once your KYC is approved and online payments are activated."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextGold,
            textAlign = TextAlign.Center
        )

        if (status != null) {
            Spacer(Modifier.height(spacing.large))
            StatusCard("Status", status.status, spacing)
            if (!status.kycStatus.isNullOrBlank()) {
                StatusCard("KYC", status.kycStatus, spacing)
            }
            if (!status.subMerchantId.isNullOrBlank()) {
                StatusCard("Merchant ID", status.subMerchantId, spacing)
            }

            if (status.status == "CPV_PENDING") {
                Spacer(Modifier.height(spacing.small))
                Surface(
                    color = WarningYellow.copy(alpha = 0.15f),
                    shape = KhanaRadii.card,
                    border = BorderStroke(1.dp, WarningYellow),
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.small)
                ) {
                    Row(
                        modifier = Modifier.padding(spacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        Text("⏳", style = MaterialTheme.typography.titleMedium)
                        Column {
                            Text(
                                "CPV Verification In Progress",
                                color = WarningYellow,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(spacing.extraSmall))
                            Text(
                                "Easebuzz is verifying your restaurant premises. Keep counter originals and storefront signage ready.",
                                color = TextLight,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(spacing.medium))

            // Address Proofs & KYC Documents Summary Card
            Surface(
                color = DarkBrown2,
                shape = KhanaRadii.card,
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.small)
            ) {
                Column(modifier = Modifier.padding(spacing.medium)) {
                    Text(
                        "KYC Compliance Documents",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(spacing.small))

                    val p1Label = AddressProofType.fromCode(status.businessProof1Type)?.label ?: status.businessProof1Type ?: "Proof 1"
                    val p2Label = AddressProofType.fromCode(status.businessProof2Type)?.label ?: status.businessProof2Type ?: "Proof 2"

                    // "Missing" is only knowable when there is no portal: once a link exists the
                    // document may already be uploaded on Easebuzz, and KhanaBook never receives
                    // a copy it could check. Claiming it is missing would flag a merchant who did
                    // exactly what the app asked.
                    val proof1State = when {
                        status.businessProof1Present -> "✓ Uploaded"
                        hasPortalLink -> "On Easebuzz portal"
                        else -> "⚠️ Missing"
                    }
                    val proof1Color = when {
                        status.businessProof1Present -> SuccessGreen
                        hasPortalLink -> TextGold.copy(alpha = 0.6f)
                        else -> DangerRed
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Address Proof 1 ($p1Label):", color = TextGold, style = MaterialTheme.typography.bodySmall)
                        Text(
                            proof1State,
                            color = proof1Color,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Address Proof 2 ($p2Label - Optional):", color = TextGold, style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (status.businessProof2Present) "✓ Uploaded" else "Optional",
                            color = if (status.businessProof2Present) SuccessGreen else TextGold.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("FSSAI License:", color = TextGold, style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (!status.fssaiNumber.isNullOrBlank()) "✓ Provided" else "⚠️ Missing",
                            color = if (!status.fssaiNumber.isNullOrBlank()) SuccessGreen else DangerRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    val openPortal = rememberPortalOpener()
                    if (!status.kycUrl.isNullOrBlank()) {
                        Spacer(Modifier.height(spacing.medium))
                        // Easebuzz requires the merchant to sign in and upload documents themselves;
                        // KhanaBook cannot upload on their behalf (ERA clarification, 2026-10-17).
                        Text(
                            "Sign in on the portal with your registered email or phone to upload your documents. " +
                                "Aadhaar (masked or XML) - or passport, voter ID or driving licence if you have no Aadhaar.",
                            color = TextGold.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(spacing.small))
                        Button(
                            onClick = { openPortal(status.kycUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                            shape = KhanaRadii.button,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val buttonText = if (status.status == "CPV_PENDING") {
                                "Complete Agentless CPV Verification ↗"
                            } else {
                                "Open Easebuzz KYC Portal (Upload Proofs) ↗"
                            }
                            Text(buttonText, color = DarkBrown1)
                        }
                    } else {
                        // Without this branch the screen had no way to ask for a link again: the
                        // only other action was "Refresh Status", which re-reads the stored row
                        // and can never mint the missing URL.
                        Spacer(Modifier.height(spacing.medium))
                        Text(
                            "No KYC portal link has been issued for this outlet yet. Request one to " +
                                "upload your documents on Easebuzz.",
                            color = DangerRed,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(spacing.small))
                        Button(
                            onClick = onRefreshLink,
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                            shape = KhanaRadii.button,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Get KYC Portal Link", color = DarkBrown1)
                        }
                    }

                    if (onOpenComplianceDocs != null) {
                        Spacer(Modifier.height(spacing.small))
                        OutlinedButton(
                            onClick = onOpenComplianceDocs,
                            shape = KhanaRadii.button,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Upload & Manage Address Proofs", color = PrimaryGold)
                        }
                    }

                }
            }

            Spacer(Modifier.height(spacing.small))

            // CPV Physical / Agentless Verification Notice. Hidden without a portal link: the copy
            // points at "the link above", and there is none until Easebuzz issues one.
            if (hasPortalLink) {
                Surface(
                    color = DarkBrown2.copy(alpha = 0.6f),
                    shape = KhanaRadii.card,
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.small)
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Text(
                            "📍 CPV Verification (Fast 48-hr Approval)",
                            color = PrimaryGold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(spacing.extraSmall))
                        Text(
                            "Complete verification online via the link above (geotagged signboard photo, shop interior, and owner consent) or keep printed counter originals ready for physical verification.",
                            color = TextGold.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(spacing.extraLarge))
        OutlinedButton(
            onClick = onRefreshStatus,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh Status")
        }
    }
}

@Composable
private fun ActiveContent(
    status: com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingStatusResponse,
    credentials: com.khanabook.lite.pos.feature.payments.data.EasebuzzPortalCredentials?,
    spacing: Spacing,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(spacing.large))
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = SuccessGreen,
            modifier = Modifier.size(KhanaBookTheme.iconSize.heroCircle)
        )
        Spacer(Modifier.height(spacing.large))
        Text("Online Payments Active! 🎉", style = MaterialTheme.typography.headlineSmall, color = TextLight)
        Spacer(Modifier.height(spacing.medium))
        Text(
            "Your business is verified and ready to accept online payments via UPI, cards, and netbanking.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextGold,
            textAlign = TextAlign.Center
        )
        if (!status.subMerchantId.isNullOrBlank()) {
            Spacer(Modifier.height(spacing.medium))
            StatusCard("Merchant ID", status.subMerchantId, spacing)
        }
        if (credentials != null) {
            Spacer(Modifier.height(spacing.medium))
            DashboardLoginCard(credentials, spacing)
        }
        Spacer(Modifier.height(spacing.extraLarge))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
            shape = KhanaRadii.button,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done", color = DarkBrown1)
        }
    }
}

/**
 * How the owner signs in to the Easebuzz dashboard after activation.
 *
 * The password is issued once, at sub-merchant creation, and Easebuzz has no self-service reset
 * that does not involve support — so it is shown here, with a copy action, rather than left in a
 * database nobody can reach.
 */
@Composable
private fun DashboardLoginCard(
    credentials: com.khanabook.lite.pos.feature.payments.data.EasebuzzPortalCredentials,
    spacing: Spacing
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val password = credentials.password

    Surface(
        color = DarkBrown2,
        shape = KhanaRadii.card,
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Text(
                "Easebuzz Dashboard Login",
                color = PrimaryGold,
                style = MaterialTheme.typography.titleSmall
            )

            if (password.isNullOrBlank()) {
                Text(
                    "No dashboard password is stored for this outlet. Ask Easebuzz support to reset it — "
                        + "your online payments are unaffected.",
                    color = TextGold.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                if (!credentials.email.isNullOrBlank()) {
                    StatusCard("Email / Login", credentials.email, spacing)
                }
                StatusCard("Password", password, spacing)
                Text(
                    "Sign in at " + (credentials.dashboardUrl ?: "dashboard.easebuzz.in")
                        + " to see settlements and bank details.",
                    color = TextGold.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(password))
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                    shape = KhanaRadii.button,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copy password")
                }
            }
        }
    }
}

@Composable
private fun RejectedContent(
    viewModel: EasebuzzOnboardingViewModel,
    status: com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingStatusResponse,
    isSubmitting: Boolean,
    spacing: Spacing
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(spacing.extraLarge))
        Text("⚠️", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(spacing.medium))
        Text("KYC Rejected", style = MaterialTheme.typography.titleMedium, color = DangerRed)
        Spacer(Modifier.height(spacing.medium))
        Text(
            "Your KYC verification was not approved. Please review your details and resubmit with correct information.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextGold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(spacing.large))
        Button(
            onClick = { viewModel.resubmitAfterRejection() },
            enabled = !isSubmitting,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
            shape = KhanaRadii.button,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Edit & Resubmit", color = DarkBrown1)
        }
    }
}  /**
   * KYC lapsed after approval. Easebuzz sends no expiry webhook, so the server flags it from the
   * stored expiry date and refuses new payment links - waiting here would never resolve itself.
   */
  @Composable
  private fun ExpiredContent(
      viewModel: EasebuzzOnboardingViewModel,
      status: com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingStatusResponse,
      isSubmitting: Boolean,
      spacing: Spacing
  ) {
      Column(
          modifier = Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(spacing.medium),
          horizontalAlignment = Alignment.CenterHorizontally
      ) {
          Spacer(Modifier.height(spacing.extraLarge))
          Text("⚠️", style = MaterialTheme.typography.displaySmall)
          Spacer(Modifier.height(spacing.medium))
          Text("KYC Expired", style = MaterialTheme.typography.titleMedium, color = DangerRed)
          Spacer(Modifier.height(spacing.medium))
          Text(
              "This outlet's KYC has lapsed, so online payments are paused. " +
                  "Re-verify on the Easebuzz portal to start taking payments again.",
              style = MaterialTheme.typography.bodyMedium,
              color = TextGold,
              textAlign = TextAlign.Center
          )
          Spacer(Modifier.height(spacing.large))

          if (!status.kycUrl.isNullOrBlank()) {
              val openPortal = rememberPortalOpener()
              Button(
                  onClick = { openPortal(status.kycUrl) },
                  colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                  shape = KhanaRadii.button,
                  modifier = Modifier.fillMaxWidth()
              ) {
                  Text("Open Easebuzz KYC Portal ↗", color = DarkBrown1)
              }
              Spacer(Modifier.height(spacing.small))
          }

          Button(
              onClick = { viewModel.resubmitAfterRejection() },
              enabled = !isSubmitting,
              colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
              shape = KhanaRadii.button,
              modifier = Modifier.fillMaxWidth()
          ) {
              Text("Edit & Resubmit", color = DarkBrown1)
          }
      }
  }

  @Composable
  private fun ErrorContent(message: String, onRetry: () -> Unit, spacing: Spacing) {
    Column(
        modifier = Modifier.fillMaxSize().padding(spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Something went wrong", style = MaterialTheme.typography.titleMedium, color = DangerRed)
        Spacer(Modifier.height(spacing.small))
        Text(message, style = MaterialTheme.typography.bodySmall, color = TextGold)
        Spacer(Modifier.height(spacing.large))
        OutlinedButton(onClick = onRetry) { Text("Retry", color = PrimaryGold) }
    }
}

// ─── Shared Components ──────────────────────────────────────────────────────

@Composable
private fun StepIndicator(current: Int, total: Int, spacing: Spacing) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(total) { index ->
            val isActive = index < current
            val isCurrent = index == current - 1
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .background(
                        color = when {
                            isCurrent -> PrimaryGold
                            isActive -> SuccessGreen
                            else -> BorderGold.copy(alpha = 0.3f)
                        },
                        shape = KhanaRadii.pill
                    )
            )
        }
    }
}

@Composable
private fun StatusCard(label: String, value: String, spacing: Spacing) {
    Surface(
        color = DarkBrown2,
        shape = KhanaRadii.card,
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.small)
    ) {
        Row(
            modifier = Modifier.padding(spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = TextGold, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(spacing.medium))
            Text(
                value,
                color = TextLight,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun OnboardingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    imeAction: ImeAction,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
            capitalization = capitalization
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { focusManager.clearFocus() }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryGold,
            unfocusedBorderColor = BorderGold,
            focusedLabelColor = PrimaryGold,
            unfocusedLabelColor = TextGold.copy(alpha = 0.6f),
            cursorColor = PrimaryGold,
            focusedTextColor = TextLight,
            unfocusedTextColor = TextLight
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BusinessTypeDropdown(selected: String, onSelect: (String) -> Unit) {
    SelectField(
        label = "Business Type *",
        options = BUSINESS_TYPE_OPTIONS,
        selected = selected,
        placeholder = "Select business type",
        onSelect = onSelect
    )
}

/** Easebuzz validates the state name string, so a typo here becomes a CPV failure. */
@Composable
private fun StateDropdown(selected: String, onSelect: (String) -> Unit) {
    SelectField(
        label = "State *",
        options = INDIAN_STATES.map { it to it },
        selected = selected,
        placeholder = "Select state / UT",
        onSelect = onSelect
    )
}

@Composable
private fun RestaurantFormatDropdown(selected: String, onSelect: (String) -> Unit) {
    SelectField(
        label = "Outlet Format *",
        options = RESTAURANT_FORMAT_OPTIONS,
        selected = selected,
        placeholder = "Select outlet format",
        onSelect = onSelect
    )
}

// Codes map to Easebuzz MCC: DINE_IN 5812, QSR 5814, BAR 5813, CATERER 5811.
private val RESTAURANT_FORMAT_OPTIONS = listOf(
    "DINE_IN" to "Dine-in / restaurant",
    "QSR" to "QSR / fast food",
    "BAR" to "Bar / pub",
    "CATERER" to "Caterer"
)

private val BUSINESS_TYPE_OPTIONS = listOf(
    "SOLE_PROPRIETORSHIP" to "Sole Proprietorship",
    "PARTNERSHIP" to "Partnership",
    "PRIVATE_LIMITED" to "Private Limited",
    "LLP" to "LLP",
    "PUBLIC_LIMITED" to "Public Limited",
    "TRUST" to "Trust/Society"
)

private val INDIAN_STATES = listOf(
    "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat",
    "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh",
    "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab",
    "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh",
    "Uttarakhand", "West Bengal",
    // Union territories
    "Andaman & Nicobar Islands", "Chandigarh", "Dadra & Nagar Haveli and Daman & Diu", "Delhi",
    "Jammu & Kashmir", "Ladakh", "Lakshadweep", "Puducherry"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectField(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    placeholder: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayName = options.firstOrNull { it.first == selected }?.second ?: selected

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder, color = TextGold.copy(alpha = 0.4f)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryGold,
                unfocusedBorderColor = BorderGold,
                focusedLabelColor = PrimaryGold,
                unfocusedLabelColor = TextGold.copy(alpha = 0.6f),
                focusedTextColor = TextLight,
                unfocusedTextColor = TextLight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = DarkBrown2
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label, color = TextLight) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Opens the KYC portal, telling the merchant when the device cannot. POS terminals are often
 * imaged without a browser, where startActivity throws and the button simply appears broken.
 */
@Composable
private fun rememberPortalOpener(): (String?) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) {
        { url ->
            if (!BrowserLauncher.open(context, url)) {
                scope.launch {
                    KhanaToast.show(
                        "No app on this device can open the portal. Link: ${url.orEmpty()}",
                        ToastKind.Error
                    )
                }
            }
        }
    }
}
