package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.ui.theme.KbShape
import com.khanabook.lite.pos.ui.theme.kbTextSecondary
import com.khanabook.lite.pos.ui.theme.kbTextTertiary

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val throwable: Throwable? = null) : UiState<Nothing>
    data object Empty : UiState<Nothing>
}

@Composable
fun <T> UiStateContent(
    state: UiState<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    emptyIcon: ImageVector = Icons.Default.Inbox,
    emptyTitle: String = "No data found",
    loadingContent: @Composable (() -> Unit)? = null,
    emptyContent: @Composable (() -> Unit)? = null,
    errorContent: @Composable ((String) -> Unit)? = null,
    content: @Composable (T) -> Unit
) {
    when (state) {
        is UiState.Loading -> {
            if (loadingContent != null) {
                loadingContent()
            } else {
                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
        }
        is UiState.Success -> content(state.data)
        is UiState.Error -> {
            if (errorContent != null) {
                errorContent(state.message)
            } else {
                ErrorStateContent(
                    message = state.message,
                    onRetry = onRetry,
                    modifier = modifier
                )
            }
        }
        is UiState.Empty -> {
            if (emptyContent != null) {
                emptyContent()
            } else {
                EmptyStateContent(
                    icon = emptyIcon,
                    title = emptyTitle,
                    modifier = modifier
                )
            }
        }
    }
}

@Composable
fun ErrorStateContent(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.CloudOff
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.kbTextTertiary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.kbTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (onRetry != null) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    shape = KbShape.Small
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
fun EmptyStateContent(
    icon: ImageVector = Icons.Default.Inbox,
    title: String = "No data found",
    modifier: Modifier = Modifier,
    description: String? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.kbTextTertiary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.kbTextSecondary,
                textAlign = TextAlign.Center
            )
            if (description != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.kbTextTertiary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
