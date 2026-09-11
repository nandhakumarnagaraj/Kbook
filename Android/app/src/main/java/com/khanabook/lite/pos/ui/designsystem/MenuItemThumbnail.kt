package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.KhanaRadii
import com.khanabook.lite.pos.ui.theme.NonVegRed
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.VegGreen

/**
 * Standard Indian statutory FSSAI food classification badge (Veg Green / Non-Veg Red).
 */
@Composable
fun FoodTypeBadge(
    type: String,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp
) {
    val color = if (type.lowercase() == "veg") VegGreen else NonVegRed
    Box(
        modifier = modifier
            .size(size)
            .background(DarkBrown1.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
            .border(1.dp, color, RoundedCornerShape(2.dp))
            .padding(1.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color, RoundedCornerShape(100.dp))
        )
    }
}

/**
 * Renders an authentic dish photo with rounded corners and Coil disk caching.
 *
 * Resilience & Offline Strategy:
 * 1. Online: Displays the WebP image from CDN and caches to local disk cache.
 * 2. Offline with cache: Instantly loads WebP bitmap from Coil disk cache.
 * 3. Offline without cache, or no photo: Seamlessly renders a clean DarkBrown1
 *    container with the centered FSSAI FoodTypeBadge (Green/Red dot).
 * 4. Includes corner badge overlay so dietary type is always visible even over photos.
 */
@Composable
fun MenuItemThumbnail(
    imageUrl: String?,
    imageVersion: Int?,
    foodType: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    showAddPhotoHint: Boolean = false
) {
    var loadFailed by remember(imageUrl, imageVersion) { mutableStateOf(false) }
    val showImage = !imageUrl.isNullOrBlank() && !loadFailed

    Box(
        modifier = modifier
            .size(size)
            .clip(KhanaRadii.md)
            .background(DarkBrown1),
        contentAlignment = Alignment.Center
    ) {
        if (showImage) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .memoryCacheKey("$imageUrl:${imageVersion ?: 0}")
                    .diskCacheKey("$imageUrl:${imageVersion ?: 0}")
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { loadFailed = true }
            )
            FoodTypeBadge(
                type = foodType,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp),
                size = 10.dp
            )
        } else {
            if (showAddPhotoHint) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = "Add dish photo",
                    tint = TextGold.copy(alpha = 0.5f),
                    modifier = Modifier.size((size.value * 0.5f).dp.coerceIn(16.dp, 28.dp))
                )
                FoodTypeBadge(
                    type = foodType,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp),
                    size = 10.dp
                )
            } else {
                FoodTypeBadge(
                    type = foodType,
                    size = (size.value * 0.35f).dp.coerceIn(12.dp, 16.dp)
                )
            }
        }
    }
}
