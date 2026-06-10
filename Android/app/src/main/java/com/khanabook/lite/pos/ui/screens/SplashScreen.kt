@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // Midnight purple gradient background — matches Login/SignUp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.kbHeaderGradient
            )
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
                // White elevated logo card — same as Login/SignUp
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.size(120.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_khanabook_logo),
                            contentDescription = stringResource(id = R.string.cd_logo),
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(id = R.string.khanabook),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Restaurant POS & Management",
                    color = KbLavender,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Saffron spinner at bottom — matches brand color
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            color = KbBrandSaffron,
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
