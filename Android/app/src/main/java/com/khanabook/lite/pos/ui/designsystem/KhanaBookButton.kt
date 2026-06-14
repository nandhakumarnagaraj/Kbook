package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import com.khanabook.lite.pos.ui.theme.*

// ═══════════════════════════════════════════════════════════════
// KHANABOOK CANONICAL BUTTONS
// Single source of truth for CTA sizing/shape/colour so screens stop
// hand-rolling Button(...) with random heights (42/45/52/56) and radii.
//   • height : KbButtonSize.HeightLarge (48.dp)
//   • shape  : KbShape.Medium (14.dp)
//   • primary: KbBrandSaffron bg / white text
// ═══════════════════════════════════════════════════════════════

/**
 * Primary call-to-action. Saffron filled. Use for the single most important
 * action on a screen ("Save", "Continue", "Pay Now", "Apply Settings").
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(KbButtonSize.HeightLarge),
        enabled = enabled && !loading,
        shape = KbShape.Medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = KbBrandSaffron,
            contentColor = Color.White,
            disabledContainerColor = KbBrandSaffron.copy(alpha = KbOpacity.Disabled),
            disabledContentColor = Color.White.copy(alpha = KbOpacity.Muted)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = KbElevation.Low)
    ) {
        ButtonInner(text = text, loading = loading, leadingIcon = leadingIcon, contentColor = Color.White)
    }
}

/**
 * Secondary action. Outlined, transparent fill. Use beside a [PrimaryButton]
 * for the lower-emphasis choice ("Cancel", "Edit", "Save Draft").
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val contentColor = MaterialTheme.kbTextPrimary
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(KbButtonSize.HeightLarge),
        enabled = enabled && !loading,
        shape = KbShape.Medium,
        border = BorderStroke(1.dp, MaterialTheme.kbOutlineBold),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            disabledContentColor = contentColor.copy(alpha = KbOpacity.Disabled)
        )
    ) {
        ButtonInner(text = text, loading = loading, leadingIcon = leadingIcon, contentColor = contentColor)
    }
}

/**
 * Destructive action. Red filled. Use for "Sign Out", "Delete", "Remove".
 */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(KbButtonSize.HeightLarge),
        enabled = enabled && !loading,
        shape = KbShape.Medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = KbError,
            contentColor = Color.White,
            disabledContainerColor = KbError.copy(alpha = KbOpacity.Disabled),
            disabledContentColor = Color.White.copy(alpha = KbOpacity.Muted)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = KbElevation.Low)
    ) {
        ButtonInner(text = text, loading = loading, leadingIcon = leadingIcon, contentColor = Color.White)
    }
}

@Composable
private fun ButtonInner(
    text: String,
    loading: Boolean,
    leadingIcon: ImageVector?,
    contentColor: Color,
) {
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = contentColor,
            strokeWidth = 2.dp
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(KbIconSize.S))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        )
    }
}

@Preview(name = "Buttons Dark", showBackground = true, backgroundColor = 0xFF121212)
@Preview(name = "Buttons Light", showBackground = true, backgroundColor = 0xFFFAF8F5)
@Composable
private fun KhanaBookButtonPreview() {
    KhanaBookLiteTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            PrimaryButton(text = "Apply Settings", onClick = {})
            Spacer(Modifier.height(12.dp))
            SecondaryButton(text = "Cancel", onClick = {})
            Spacer(Modifier.height(12.dp))
            DangerButton(text = "Sign Out", onClick = {})
            Spacer(Modifier.height(12.dp))
            PrimaryButton(text = "Loading", onClick = {}, loading = true)
        }
    }
}
