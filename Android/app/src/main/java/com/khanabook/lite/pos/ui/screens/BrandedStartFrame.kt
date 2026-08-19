@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.LightGold
import com.khanabook.lite.pos.ui.theme.TextLight

private val BurgundyTop = Color(0xFF170207)
private val BurgundyMid = Color(0xFF2D0A10)
private val BurgundyBottom = Color(0xFF150206)

/**
 * Seamless branded first frame shown right after the native splash while the
 * startup routing decision completes. Released the instant the decision is
 * ready (zero artificial delay); the native splash background blends into
 * this frame's gradient so startup reads as one continuous screen.
 */
@Composable
internal fun BrandedStartFrame(modifier: Modifier = Modifier) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BurgundyTop, BurgundyMid, BurgundyBottom)))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier.size(layout.heroImageSize + spacing.large + spacing.medium),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    LightGold.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Image(
                    painter = painterResource(id = R.drawable.splash_logo),
                    contentDescription = stringResource(id = R.string.cd_logo),
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(spacing.extraLarge))
            Text(
                text = stringResource(id = R.string.khanabook).uppercase(),
                color = TextLight,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = spacing.extraLarge)
        ) {
            Text(
                text = stringResource(id = R.string.splash_from),
                color = TextLight.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(spacing.small))
            Text(
                text = stringResource(id = R.string.splash_company_piquant) +
                    stringResource(id = R.string.splash_company_consultancy),
                color = TextLight,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(spacing.extraSmall))
            Text(
                text = stringResource(id = R.string.splash_company_services),
                color = TextLight.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}