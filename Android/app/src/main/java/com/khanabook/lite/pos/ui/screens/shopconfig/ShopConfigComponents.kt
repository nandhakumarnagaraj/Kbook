package com.khanabook.lite.pos.ui.screens.shopconfig

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.domain.model.OrderPaymentFlowMode
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.DarkBrown2
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.KhanaRadii
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight

@Composable
internal fun RestaurantPaymentFlowSelector(
    selectedMode: OrderPaymentFlowMode,
    onModeSelected: (OrderPaymentFlowMode) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.25f)), KhanaRadii.lg)
            .background(DarkBrown2.copy(alpha = 0.45f), KhanaRadii.lg)
            .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(KhanaBookTheme.iconSize.small))
            Column(modifier = Modifier.weight(1f)) {
                Text("Billing Mode", color = PrimaryGold, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (selectedMode == OrderPaymentFlowMode.PAY_AFTER_FOOD) {
                        "Dine-in orders can stay open until payment."
                    } else {
                        "Orders collect payment before food is served."
                    },
                    color = TextGold,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        PaymentFlowToggleSwitch(
            selectedMode = selectedMode,
            onModeSelected = onModeSelected
        )
    }
}

@Composable
internal fun PaymentFlowToggleSwitch(
    selectedMode: OrderPaymentFlowMode,
    onModeSelected: (OrderPaymentFlowMode) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        OrderPaymentFlowMode.values().forEach { mode ->
            val isSelected = mode == selectedMode
            Button(
                onClick = { onModeSelected(mode) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) PrimaryGold else DarkBrown1,
                    contentColor = if (isSelected) DarkBrown1 else TextLight
                )
            ) {
                Text(mode.displayLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
