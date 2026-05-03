package com.dailysatori.ui.component.media

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.dailysatori.ui.theme.Radius
import java.io.File

@Composable
fun SmartImage(
    imagePath: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 100.dp,
    placeholder: @Composable () -> Unit = {
        Icon(
            Icons.Filled.DateRange,
            contentDescription = null,
            modifier = Modifier.size(size),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    },
) {
    if (imagePath.isNullOrBlank()) {
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            placeholder()
        }
    } else {
        val context = LocalContext.current
        val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val isLocal = !imagePath.startsWith("http://") && !imagePath.startsWith("https://")
        val resolvedPath = remember(imagePath) {
            if (isLocal && !imagePath.startsWith("/")) {
                File(context.filesDir, "DailySatori/$imagePath").absolutePath
            } else {
                imagePath
            }
        }
        Box(modifier = Modifier.size(size).then(modifier)) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolvedPath)
                    .crossfade(true)
                    .build(),
                placeholder = painterResource(android.R.drawable.ic_menu_gallery),
                error = painterResource(android.R.drawable.ic_menu_report_image),
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(Radius.xs)),
                contentScale = ContentScale.Crop,
            )
            if (isDebug) {
                Icon(
                    imageVector = if (isLocal) Icons.Filled.PhoneAndroid else Icons.Filled.Wifi,
                    contentDescription = if (isLocal) "本地图片" else "远程图片",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .background(Color(0xCC000000), CircleShape),
                    tint = if (isLocal) Color(0xFF4CAF50) else Color(0xFF2196F3),
                )
            }
        }
    }
}
