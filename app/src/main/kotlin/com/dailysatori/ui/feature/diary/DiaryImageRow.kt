package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import java.io.File

@Composable
fun DiaryImageRow(
    images: List<String>,
    onRemove: (String) -> Unit,
) {
    if (images.isEmpty()) return
    val context = LocalContext.current
    Spacer(modifier = Modifier.height(Spacing.xs))
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        contentPadding = PaddingValues(vertical = Spacing.xs),
    ) {
        items(images.toList(), key = { it }) { imagePath ->
            Box(modifier = Modifier.size(103.dp)) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(context.filesDir, "DailySatori/$imagePath"))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(103.dp).clip(RoundedCornerShape(Radius.l)),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                        .size(18.dp)
                        .clip(RoundedCornerShape(topEnd = Radius.m, bottomStart = Radius.xs))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .clickable { onRemove(imagePath) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "删除",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
