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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.VegGreen

@Composable
fun TaxConfigView(profile: RestaurantProfileEntity?, onSave: (RestaurantProfileEntity) -> Unit, onBack: () -> Unit) {
    val spacing = KhanaBookTheme.spacing
    var country by remember { mutableStateOf(profile?.country ?: "India") }
    var gstEnabled by remember { mutableStateOf(profile?.gstEnabled ?: false) }
    var gstNumber by remember { mutableStateOf(profile?.gstin ?: "") }
    var gstPct by remember { mutableStateOf(profile?.gstPercentage?.toString() ?: "0") }
    var fssaiNumber by remember { mutableStateOf(profile?.fssaiNumber ?: "") }

    val isSaveEnabled = fssaiNumber.isNotBlank() && (!gstEnabled || gstNumber.isNotBlank())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(spacing.medium)
    ) {
        ConfigCard {
            ParchmentTextField(value = country, onValueChange = { country = it }, label = "Country")
            Spacer(modifier = Modifier.height(spacing.medium))
            ParchmentTextField(value = fssaiNumber, onValueChange = { fssaiNumber = it }, label = "FSSAI Number *")
            Spacer(modifier = Modifier.height(spacing.medium))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("GST Registered", color = TextGold, style = MaterialTheme.typography.bodyMedium)
                Switch(checked = gstEnabled, onCheckedChange = { gstEnabled = it }, colors = SwitchDefaults.colors(checkedTrackColor = VegGreen))
            }
            if (gstEnabled) {
                ParchmentTextField(value = gstNumber, onValueChange = { gstNumber = it.uppercase() }, label = "GSTIN *")
                Spacer(modifier = Modifier.height(spacing.small))
                ParchmentTextField(value = gstPct, onValueChange = { gstPct = it }, label = "GST % *")
            }
            Spacer(modifier = Modifier.height(spacing.extraLarge))
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
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                shape = RoundedCornerShape(28.dp),
                enabled = isSaveEnabled
            ) { Text("Save Tax Config", color = Color.White, style = MaterialTheme.typography.titleMedium) }
        }
    }
}
