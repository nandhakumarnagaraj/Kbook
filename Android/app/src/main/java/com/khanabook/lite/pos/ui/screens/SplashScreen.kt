@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.ui.theme.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.ui.viewmodel.SplashViewModel

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToMain: () -> Unit,
    onNavigateToInitialSync: () -> Unit,
    onNavigateToAppLock: () -> Unit = {},
    viewModel: SplashViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var contentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KbBrandSaffron)
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(animationSpec = tween(650)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = tween(650, easing = FastOutSlowInEasing)
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_khanabook_logo),
                    contentDescription = stringResource(id = R.string.cd_logo),
                    modifier = Modifier.size(80.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(id = R.string.khanabook),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Smart Restaurant POS",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                )
            }
        }

        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            color = Color.White,
            strokeWidth = 3.dp
        )
    }

    LaunchedEffect(state) {
        when (state) {
            is SplashViewModel.SplashState.NavigateToLogin -> onNavigateToLogin()
            is SplashViewModel.SplashState.NavigateToMain -> onNavigateToMain()
            is SplashViewModel.SplashState.NavigateToInitialSync -> onNavigateToInitialSync()
            is SplashViewModel.SplashState.NavigateToAppLock -> onNavigateToAppLock()
            else -> {}
        }
    }
}
