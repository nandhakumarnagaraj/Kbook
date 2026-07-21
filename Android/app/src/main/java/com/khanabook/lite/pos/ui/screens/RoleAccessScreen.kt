package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.DarkBrown2
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.KhanaRadii
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.RichEspresso
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight

@Composable
fun RoleAccessScreen(
    role: String?,
    onSignOut: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val isShopAdmin = role == "SHOP_ADMIN"
    val title = if (isShopAdmin) "Terminal administration" else "Platform administration"
    val message = if (isShopAdmin) {
        "Your account manages terminals and device requests in KhanaBook Web Admin. POS billing is available to restaurant owners."
    } else {
        "Your platform administrator account is designed for KhanaBook Web Admin and cannot run restaurant POS operations."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
            .padding(KhanaBookTheme.layout.contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = KhanaRadii.cardLarge,
            color = DarkBrown2,
            tonalElevation = spacing.extraSmall
        ) {
            Column(
                modifier = Modifier.padding(spacing.extraLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                Surface(shape = KhanaRadii.pill, color = PrimaryGold.copy(alpha = 0.14f)) {
                    Icon(
                        imageVector = Icons.Filled.AdminPanelSettings,
                        contentDescription = null,
                        tint = PrimaryGold,
                        modifier = Modifier.padding(spacing.medium)
                    )
                }
                Text(title, style = MaterialTheme.typography.headlineSmall, color = TextLight, textAlign = TextAlign.Center)
                Text(message, style = MaterialTheme.typography.bodyLarge, color = TextGold, textAlign = TextAlign.Center)
                Surface(shape = KhanaRadii.pill, color = DarkBrown1) {
                    Text(
                        text = role?.replace('_', ' ') ?: "LIMITED ACCESS",
                        modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                        style = MaterialTheme.typography.labelLarge,
                        color = PrimaryGold
                    )
                }
                Text(
                    text = "Open the KhanaBook Web Admin in your browser to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLight,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                    shape = KhanaRadii.button,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                    border = androidx.compose.foundation.BorderStroke(spacing.hairline, BorderGold)
                ) {
                    Text("Sign out")
                }
            }
        }
    }
}
