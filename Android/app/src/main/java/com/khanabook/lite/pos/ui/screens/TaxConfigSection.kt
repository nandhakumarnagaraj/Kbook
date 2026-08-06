package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.util.ValidationUtils
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.ui.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.ui.theme.DangerRed
import com.khanabook.lite.pos.ui.theme.DarkBrownSheet
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.VegGreen
import com.khanabook.lite.pos.ui.theme.WarningYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxConfigView(profile: RestaurantProfileEntity?, onSave: (RestaurantProfileEntity) -> Unit, onBack: () -> Unit) {
    val spacing = KhanaBookTheme.spacing
    val fssaiRegex = remember { Regex("^\\d{14}$") }
    var country by remember { mutableStateOf("India") }
    var countryExpanded by remember { mutableStateOf(false) }
    var gstEnabled by remember { mutableStateOf(profile?.gstEnabled ?: false) }
    var gstNumber by remember { mutableStateOf(profile?.gstin ?: "") }
    var gstPct by remember { mutableStateOf(profile?.gstPercentage?.toInt()?.toString() ?: "0") }
    var fssaiNumber by remember { mutableStateOf(profile?.fssaiNumber ?: "") }

    val isFssaiValid = fssaiRegex.matches(fssaiNumber)
    val isGstValid = !gstEnabled || ValidationUtils.isValidGst(gstNumber)
    val isGstPctValid = !gstEnabled || ValidationUtils.isValidTaxPercentage(gstPct)
    val isSaveEnabled = isFssaiValid && isGstValid && isGstPctValid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(spacing.medium)
    ) {
        ConfigCard {
            ExposedDropdownMenuBox(
                expanded = countryExpanded,
                onExpandedChange = { countryExpanded = it }
            ) {
                ParchmentTextField(
                    value = country,
                    onValueChange = {},
                    label = "Country",
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded) },
                    modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = countryExpanded,
                    onDismissRequest = { countryExpanded = false },
                    containerColor = DarkBrownSheet
                ) {
                    DropdownMenuItem(
                        text = { Text("India", color = TextGold) },
                        onClick = {
                            country = "India"
                            countryExpanded = false
                        },
                        colors = MenuDefaults.itemColors(textColor = TextGold)
                    )
                }
            }
            Spacer(modifier = Modifier.height(spacing.medium))
            ParchmentTextField(
                value = fssaiNumber,
                onValueChange = { fssaiNumber = it.filter(Char::isDigit).take(14) },
                label = "FSSAI License *",
                isError = fssaiNumber.isNotEmpty() && !isFssaiValid,
                supportingText = if (fssaiNumber.isNotEmpty() && !isFssaiValid) "Enter exactly 14 digits" else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            profile?.fssaiExpiryDate?.takeIf { it.isNotBlank() }?.let { expiry ->
                val expiryWarning = expiryWarningText(expiry)
                Spacer(modifier = Modifier.height(spacing.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    if (expiryWarning != null) {
                        Text(
                            text = "\u26A0",
                            color = if (expiryWarning.first) DangerRed else WarningYellow,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = "FSSAI valid until ${formatExpiry(expiry)}${if (expiryWarning != null) " · ${expiryWarning.second}" else ""}",
                        color = if (expiryWarning != null && expiryWarning.first) DangerRed else TextGold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(spacing.medium))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("GST Registered", color = TextGold, style = MaterialTheme.typography.bodyMedium)
                KhanaBookSwitch(
                    checked = gstEnabled,
                    onCheckedChange = { gstEnabled = it },
                    checkedTrackColor = VegGreen
                )
            }
            if (gstEnabled) {
                ParchmentTextField(
                    value = gstNumber,
                    onValueChange = {
                        gstNumber = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(15)
                    },
                    label = "GST Identification Number (GSTIN) *",
                    isError = gstNumber.isNotEmpty() && !isGstValid,
                    supportingText = if (gstNumber.isNotEmpty() && !isGstValid) "Enter valid 15-character GSTIN format" else null
                )
                Spacer(modifier = Modifier.height(spacing.small))
                ParchmentTextField(
                    value = gstPct,
                    onValueChange = { gstPct = it.filter(Char::isDigit).take(3) },
                    label = "GST % *",
                    isError = gstPct.isNotEmpty() && !isGstPctValid,
                    supportingText = if (gstPct.isNotEmpty() && !isGstPctValid) "Enter whole number from 0 to 100" else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Spacer(modifier = Modifier.height(spacing.extraLarge))
            ConfigActionButtons(
                onSave = {
                        profile?.copy(
                            country = country,
                            gstEnabled = gstEnabled,
                            gstin = gstNumber,
                            gstPercentage = gstPct.toDoubleOrNull() ?: 0.0,
                            fssaiNumber = fssaiNumber,
                            isSynced = false,
                            updatedAt = System.currentTimeMillis()
                        )?.let { onSave(it) }
                },
                onBack = onBack,
                saveEnabled = isSaveEnabled
            )
        }
    }
}

private fun formatExpiry(isoDate: String): String {
    return try {
        java.time.LocalDate.parse(isoDate)
            .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"))
    } catch (e: Exception) {
        isoDate
    }
}

private fun expiryWarningText(isoDate: String): Pair<Boolean, String>? {
    return try {
        val expiry = java.time.LocalDate.parse(isoDate)
        val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(
            java.time.LocalDate.now(java.time.ZoneId.of(com.khanabook.lite.pos.domain.util.AppConstants.DEFAULT_TIMEZONE)),
            expiry
        )
        when {
            daysLeft < 0 -> true to "Expired"
            daysLeft <= 30 -> true to "$daysLeft days left"
            daysLeft <= 90 -> false to "$daysLeft days left"
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}
