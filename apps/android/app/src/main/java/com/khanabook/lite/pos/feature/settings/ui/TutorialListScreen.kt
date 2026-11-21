package com.khanabook.lite.pos.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khanabook.lite.pos.core.designsystem.KhanaBookScreenScaffold
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.DarkBrown2
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.RichEspresso
import com.khanabook.lite.pos.core.theme.TextLight
import com.khanabook.lite.pos.core.theme.TextMuted
import com.khanabook.lite.pos.feature.settings.viewmodel.TutorialListViewModel

/**
 * Catalogue shown when staff tap "Watch Tutorials". The list comes from
 * `/cdn/tutorials/videos.json` when that manifest is deployed; otherwise the bundled fallback
 * keeps the already published videos reachable. Selecting a row opens [InAppWebViewScreen].
 */
@Composable
fun TutorialListScreen(
    onBack: () -> Unit,
    onSelectVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TutorialListViewModel = viewModel()
) {
    val spacing = KhanaBookTheme.spacing
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BoxWithGradient(modifier = modifier) {
        KhanaBookScreenScaffold(
            title = "Watch Tutorials",
            onBack = onBack,
            // This screen is a nav destination outside MainScreen, so nothing else
            // applies the top inset. Keep the header clear of the status bar.
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            if (uiState.loading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(spacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    CircularProgressIndicator(color = PrimaryGold)
                    Spacer(Modifier.height(spacing.medium))
                    Text("Loading tutorials…", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = spacing.medium,
                        vertical = spacing.small
                    ),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing.small)
                ) {
                    items(uiState.videos, key = { it.fileName }) { video ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectVideo(video.fileName) },
                            colors = CardDefaults.cardColors(containerColor = DarkBrown2),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(spacing.medium),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = PrimaryGold,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.size(spacing.medium))
                                Text(
                                    text = video.title,
                                    color = TextLight,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxWithGradient(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
    ) {
        content()
    }
}
