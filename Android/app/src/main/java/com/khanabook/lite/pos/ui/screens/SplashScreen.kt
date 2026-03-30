package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.DarkBrown2
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.TextGold
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.ui.viewmodel.SplashViewModel

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToMain: () -> Unit,
    onNavigateToInitialSync: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(id = R.string.khanabook),
                color = PrimaryGold,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(id = R.string.lite),
                color = TextGold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.smart_billing_slogan),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp
            )
        }
    }

    LaunchedEffect(state) {
        when (state) {
            is SplashViewModel.SplashState.NavigateToLogin -> onNavigateToLogin()
            is SplashViewModel.SplashState.NavigateToMain -> onNavigateToMain()
            is SplashViewModel.SplashState.NavigateToInitialSync -> onNavigateToInitialSync()
            else -> {}
        }
    }
}

