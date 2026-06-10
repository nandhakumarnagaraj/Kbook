package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.util.ValidationUtils
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.ui.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.KbSuccess
import com.khanabook.lite.pos.ui.theme.kbPrimary
import com.khanabook.lite.pos.ui.theme.kbSecondary

data class LookupUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val result: LookupResult? = null
)

data class LookupResult(
    val businessName: String?,
    val address: String?,
    val fssaiNo: String?,
    val gstin: String?
)

@Composable
fun TaxConfigView(
    profile: RestaurantProfileEntity?,
    onSave: (RestaurantProfileEntity) -> Unit,
    onBack: () -> Unit,
    lookupState: LookupUiState = LookupUiState(),
    onLookupGst: (String) -> Unit = {},
    onLookupFssai: (String) -> Unit = {},
    onLookupBoth: (String, String) -> Unit = { _, _ -> },
    onApplyLookup: (LookupResult) -> Unit = {},
    onClearLookup: () -> Unit = {}
) {
    val spacing = KhanaBookTheme.spacing
    val fssaiRegex = remember { Regex("^\\d{14}$") }
    var country by remember(profile) { mutableStateOf(profile?.country ?: "India") }
    var gstEnabled by remember(profile) { mutableStateOf(profile?.gstEnabled ?: false) }
    var gstNumber by remember(profile) { mutableStateOf(profile?.gstin ?: "") }
    var gstPct by remember(profile) { mutableStateOf(profile?.gstPercentage?.toInt()?.toString() ?: "0") }
    var fssaiNumber by remember(profile) { mutableStateOf(profile?.fssaiNumber ?: "") }

    val isFssaiValid = fssaiRegex.matches(fssaiNumber)
    val isGstValid = !gstEnabled || ValidationUtils.isValidGst(gstNumber)
    val isGstPctValid = !gstEnabled || ValidationUtils.isValidTaxPercentage(gstPct)
    val isSaveEnabled = isFssaiValid && isGstValid && isGstPctValid

    // Dialog for lookup results
    lookupState.result?.let { result ->
        AlertDialog(
            onDismissRequest = onClearLookup,
            title = { Text("Lookup Result", color = MaterialTheme.kbSecondary) },
            text = {
                Column {
                    result.businessName?.let {
                        Text("Business Name: $it", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    result.address?.let {
                        Text("Address: $it", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    result.gstin?.let {
                        Text("GSTIN: $it", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    result.fssaiNo?.let {
                        Text("FSSAI: $it", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onApplyLookup(result)
                        onClearLookup()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KbSuccess)
                ) { Text("Apply", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = onClearLookup) { Text("Dismiss") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.medium)
    ) {
        ConfigCard {
            ParchmentTextField(value = country, onValueChange = {}, label = "Country", enabled = false)
            Spacer(modifier = Modifier.height(spacing.medium))

            // FSSAI + lookup
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalAlignment = Alignment.Top
            ) {
                ParchmentTextField(
                    value = fssaiNumber,
                    onValueChange = { fssaiNumber = it.filter(Char::isDigit).take(14) },
                    label = "FSSAI License *",
                    isError = fssaiNumber.isNotEmpty() && !isFssaiValid,
                    supportingText = if (fssaiNumber.isNotEmpty() && !isFssaiValid) "Enter exactly 14 digits" else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { onLookupFssai(fssaiNumber) },
                    enabled = isFssaiValid && !lookupState.loading,
                    modifier = Modifier.height(56.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.kbPrimary.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.kbPrimary),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Fetch", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("GST Registered", color = MaterialTheme.kbSecondary, style = MaterialTheme.typography.bodyMedium)
                KhanaBookSwitch(
                    checked = gstEnabled,
                    onCheckedChange = { gstEnabled = it },
                    checkedTrackColor = KbSuccess
                )
            }
            if (gstEnabled) {
                // GSTIN + lookup
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalAlignment = Alignment.Top
                ) {
                    ParchmentTextField(
                        value = gstNumber,
                        onValueChange = {
                            gstNumber = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(15)
                        },
                        label = "GST Identification Number (GSTIN) *",
                        isError = gstNumber.isNotEmpty() && !isGstValid,
                        supportingText = if (gstNumber.isNotEmpty() && !isGstValid) "Enter valid 15-character GSTIN format" else null,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { onLookupGst(gstNumber) },
                        enabled = isGstValid && !lookupState.loading,
                        modifier = Modifier.height(56.dp),                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.kbPrimary.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.kbPrimary),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Fetch", style = MaterialTheme.typography.labelLarge)
                }
            }
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

            // Both lookup button
            if (isFssaiValid && gstEnabled && isGstValid) {
                Spacer(modifier = Modifier.height(spacing.medium))
                OutlinedButton(
                    onClick = { onLookupBoth(gstNumber, fssaiNumber) },
                    enabled = !lookupState.loading,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.kbPrimary.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.kbPrimary),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Fetch Both (GST + FSSAI)", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (lookupState.loading) {
                Spacer(modifier = Modifier.height(spacing.medium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = MaterialTheme.kbSecondary, modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text("Looking up…", color = MaterialTheme.kbSecondary)
                }
            }

            lookupState.error?.let {
                Spacer(modifier = Modifier.height(spacing.small))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(spacing.medium))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Button(
                    onClick = {
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
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KbSuccess),
                    shape = RoundedCornerShape(24.dp),
                    enabled = isSaveEnabled
                ) { Text("Save", color = Color.White, style = MaterialTheme.typography.titleMedium) }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(48.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.kbPrimary.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.kbPrimary),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Back", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
