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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.util.ValidationUtils
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.VegGreen

@Composable
fun TaxConfigView(profile: RestaurantProfileEntity?, onSave: (RestaurantProfileEntity) -> Unit, onBack: () -> Unit) {
    val spacing = KhanaBookTheme.spacing
    val fssaiRegex = remember { Regex("^\\d{14}$") }
    var country by remember { mutableStateOf(profile?.country ?: "India") }
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
            ParchmentTextField(value = country, onValueChange = {}, label = "Country", enabled = false)
            Spacer(modifier = Modifier.height(spacing.medium))
            ParchmentTextField(
                value = fssaiNumber,
                onValueChange = { fssaiNumber = it.filter(Char::isDigit).take(14) },
                label = "FSSAI License *",
                isError = fssaiNumber.isNotEmpty() && !isFssaiValid,
                supportingText = if (fssaiNumber.isNotEmpty() && !isFssaiValid) "Enter exactly 14 digits" else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(modifier = Modifier.height(spacing.medium))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("GST Registered", color = TextGold, style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = gstEnabled,
                    onCheckedChange = { gstEnabled = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = VegGreen),
                    modifier = Modifier.scale(0.8f)
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
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(28.dp),
                    enabled = isSaveEnabled
                ) { Text("Save", color = Color.White, style = MaterialTheme.typography.titleMedium) }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(56.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Back", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
