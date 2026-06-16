@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.ui.components.KhanaDatePickerField
import com.khanabook.lite.pos.ui.designsystem.KhanaBookInputField
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.EasebuzzOnboardingViewModel
import com.khanabook.lite.pos.ui.viewmodel.EasebuzzOnboardingViewModel.FormState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EasebuzzOnboardingScreen(
    isResubmit: Boolean,
    onSubmitted: () -> Unit,
    onBack: () -> Unit,
    viewModel: EasebuzzOnboardingViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsState()
    val submitState by viewModel.submit.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(submitState) {
        val s = submitState
        if (s is EasebuzzOnboardingViewModel.SubmitState.Success) {
            android.widget.Toast.makeText(
                context,
                if (isResubmit) "Details resubmitted to Easebuzz" else "Submitted to Easebuzz — complete KYC next",
                android.widget.Toast.LENGTH_LONG
            ).show()
            viewModel.resetSubmit()
            onSubmitted()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kbBgPrimary)
    ) {
        // Brand header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.kbHeaderGradient)
                .statusBarsPadding()
                .padding(vertical = 16.dp, horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = KbOpacity.Pressed), CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = if (isResubmit) "Update KYC Details" else "Easebuzz Onboarding",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            InfoNote(
                "Documents (PAN, FSSAI, bank proof) are uploaded later on Easebuzz's secure KYC " +
                    "portal — not here. Enter your business and bank details below."
            )

            SectionLabel("BUSINESS DETAILS")
            Field("Business / trade name", form.businessName, form.errors["businessName"]) { v ->
                viewModel.update { it.copy(businessName = v) }
            }
            Field(
                "Legal entity name", form.legalEntityName, form.errors["legalEntityName"],
                supporting = "Must match PAN / GST exactly (CPV check)"
            ) { v -> viewModel.update { it.copy(legalEntityName = v) } }

            BusinessTypeDropdown(
                selected = form.businessType,
                options = viewModel.businessTypes,
                onSelected = { v -> viewModel.update { it.copy(businessType = v) } }
            )

            Field("PAN (optional)", form.pan, form.errors["pan"], cap = true) { v ->
                viewModel.update { it.copy(pan = v) }
            }
            Field("GST (optional)", form.gst, form.errors["gst"], cap = true) { v ->
                viewModel.update { it.copy(gst = v) }
            }
            Field(
                "FSSAI license number", form.fssaiNumber, form.errors["fssaiNumber"],
                keyboard = KeyboardType.Number,
                supporting = "Mandatory for food businesses"
            ) { v -> viewModel.update { it.copy(fssaiNumber = v) } }

            KhanaDatePickerField(
                label = "FSSAI expiry date (optional)",
                selectedDate = form.fssaiExpiryDate?.let {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
                } ?: "",
                onDateSelected = { dateStr ->
                    val millis = runCatching {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)?.time
                    }.getOrNull()
                    viewModel.update { it.copy(fssaiExpiryDate = millis) }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Field(
                "Business address", form.businessAddress, form.errors["businessAddress"],
                singleLine = false
            ) { v -> viewModel.update { it.copy(businessAddress = v) } }
            Field("State", form.state, form.errors["state"]) { v ->
                viewModel.update { it.copy(state = v) }
            }

            SectionLabel("BANK ACCOUNT (for settlements)")
            Field("Account holder name", form.beneficiaryName, form.errors["beneficiaryName"]) { v ->
                viewModel.update { it.copy(beneficiaryName = v) }
            }
            Field(
                "Account number", form.bankAccountNo, form.errors["bankAccountNo"],
                keyboard = KeyboardType.Number
            ) { v -> viewModel.update { it.copy(bankAccountNo = v) } }
            Field(
                "Confirm account number", form.confirmAccountNo, form.errors["confirmAccountNo"],
                keyboard = KeyboardType.Number
            ) { v -> viewModel.update { it.copy(confirmAccountNo = v) } }
            Field("IFSC code", form.ifsc, form.errors["ifsc"], cap = true) { v ->
                viewModel.update { it.copy(ifsc = v) }
            }
            Field("Bank name", form.bankName, form.errors["bankName"]) { v ->
                viewModel.update { it.copy(bankName = v) }
            }
            Field("Branch (optional)", form.branchName, form.errors["branchName"]) { v ->
                viewModel.update { it.copy(branchName = v) }
            }

            SectionLabel("CONTACT")
            Field(
                "Email", form.contactEmail, form.errors["contactEmail"],
                keyboard = KeyboardType.Email
            ) { v -> viewModel.update { it.copy(contactEmail = v) } }
            Field(
                "Phone", form.contactPhone, form.errors["contactPhone"],
                keyboard = KeyboardType.Phone
            ) { v -> viewModel.update { it.copy(contactPhone = v) } }

            (submitState as? EasebuzzOnboardingViewModel.SubmitState.Error)?.let {
                Text(
                    text = it.message,
                    color = KbError,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            val submitting = submitState is EasebuzzOnboardingViewModel.SubmitState.Submitting
            Button(
                onClick = { viewModel.submit(isResubmit) },
                enabled = !submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = KbShape.Medium,
                colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron)
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isResubmit) "Resubmit to Easebuzz" else "Submit to Easebuzz",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = KbBrandSaffron,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun InfoNote(text: String) {
    Surface(
        color = KbBrandSaffron.copy(alpha = 0.10f),
        shape = KbShape.Small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            color = MaterialTheme.kbTextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    error: String?,
    cap: Boolean = false,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    supporting: String? = null,
    onValueChange: (String) -> Unit
) {
    KhanaBookInputField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        isError = error != null,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (cap) androidx.compose.ui.text.input.KeyboardCapitalization.Characters
                             else androidx.compose.ui.text.input.KeyboardCapitalization.None
        ),
        supportingText = (error ?: supporting)?.let { msg ->
            {
                Text(
                    text = msg,
                    color = if (error != null) KbError else MaterialTheme.kbTextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

@Composable
private fun BusinessTypeDropdown(
    selected: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: selected

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        KhanaBookInputField(
            value = selectedLabel,
            onValueChange = {},
            label = "Business type",
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.kbSecondary)
            }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}
