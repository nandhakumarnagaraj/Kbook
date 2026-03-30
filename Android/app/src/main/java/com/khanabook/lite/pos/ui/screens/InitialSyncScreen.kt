package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.ui.viewmodel.InitialSyncState
import com.khanabook.lite.pos.ui.viewmodel.InitialSyncViewModel

@Composable
fun InitialSyncScreen(
    onSyncCompleteNavigateToMain: () -> Unit,
    viewModel: InitialSyncViewModel = hiltViewModel()
) {
    val syncState by viewModel.syncState.collectAsState()

    
    LaunchedEffect(syncState) {
        if (syncState is InitialSyncState.Success) {
            onSyncCompleteNavigateToMain()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val state = syncState) {
                is InitialSyncState.Syncing, InitialSyncState.Idle -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Setting up your workspace...")
                    Text("Please wait. Do not close the app.", style = MaterialTheme.typography.bodySmall)
                }
                is InitialSyncState.Error -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Sync Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { viewModel.startInitialSync() }) {
                        Text("Retry")
                    }
                }
                is InitialSyncState.Success -> {
                    
                    Text("Setup Complete!")
                }
            }
        }
    }
}
