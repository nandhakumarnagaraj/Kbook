package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.ui.theme.*
import kotlinx.coroutines.delay

enum class ToastKind { Success, Error, Warning, Info }

class KhanaSnackbarVisuals(
    override val message: String,
    val kind: ToastKind = ToastKind.Info,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration =
        if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite,
) : SnackbarVisuals

object KhanaToast {
    val host: SnackbarHostState = SnackbarHostState()

    suspend fun show(
        message: String,
        kind: ToastKind = ToastKind.Info,
        actionLabel: String? = null,
    ): SnackbarResult = host.showSnackbar(
        KhanaSnackbarVisuals(message = message, kind = kind, actionLabel = actionLabel)
    )
}

private data class KindStyle(
    val container: Color,
    val content: Color,
    val accent: Color,
    val icon: ImageVector,
)

private fun styleFor(kind: ToastKind): KindStyle = when (kind) {
    ToastKind.Success -> KindStyle(DarkBrown2, TextLight, SuccessGreen, Icons.Default.CheckCircle)
    ToastKind.Error -> KindStyle(DarkBrown2, TextLight, DangerRed, Icons.Default.Error)
    ToastKind.Warning -> KindStyle(DarkBrown2, TextLight, WarningYellow, Icons.Default.Warning)
    ToastKind.Info -> KindStyle(DarkBrown2, TextLight, PrimaryGold, Icons.Default.Info)
}

@Composable
fun KhanaBookSnackbar(data: SnackbarData) {
    val kind = (data.visuals as? KhanaSnackbarVisuals)?.kind ?: ToastKind.Info
    val style = styleFor(kind)

    LaunchedEffect(data) {
        if (data.visuals.actionLabel == null) {
            delay(2_000)
            data.dismiss()
        }
    }

    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        color = style.container,
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .background(style.container)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = data.visuals.message,
                color = style.content,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f),
            )
            data.visuals.actionLabel?.let { label ->
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { data.performAction() }) {
                    Text(label, color = style.accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun KhanaBookSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier.padding(bottom = 28.dp)) { data ->
        KhanaBookSnackbar(data)
    }
}
