@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.InitialSyncState
import com.khanabook.lite.pos.ui.viewmodel.InitialSyncViewModel

@Composable
fun InitialSyncScreen(
    onSyncCompleteNavigateToMain: () -> Unit,
    viewModel: InitialSyncViewModel = hiltViewModel()
) {
    val syncState by viewModel.syncState.collectAsState()
    val spacing = KhanaBookTheme.spacing

    LaunchedEffect(syncState) {
        if (syncState is InitialSyncState.Success) {
            onSyncCompleteNavigateToMain()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
        Column(
            modifier = Modifier.fillMaxSize().padding(spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val state = syncState) {
                is InitialSyncState.Syncing, InitialSyncState.Idle -> {
                    CircularProgressIndicator(color = PrimaryGold)
                    Spacer(modifier = Modifier.height(spacing.large))
                    Text(
                        "Setting up your workspace...",
                        color = TextLight,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Please wait. Do not close the app.", 
                        color = TextGold,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = spacing.small)
                    )
                }
                is InitialSyncState.Error -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Sync Error",
                        tint = ErrorPink,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(spacing.large))
                    Text(
                        text = state.message, 
                        color = ErrorPink,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(spacing.extraLarge))
                    Button(
                        onClick = { viewModel.startInitialSync() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("Retry", color = DarkBrown1, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
                is InitialSyncState.Success -> {
                    Text(
                        "Setup Complete!",
                        color = SuccessGreen,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
        }
    }
}
