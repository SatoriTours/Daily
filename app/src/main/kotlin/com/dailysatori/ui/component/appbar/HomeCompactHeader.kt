package com.dailysatori.ui.component.appbar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.Spacing

data class HomeCompactTab(val label: String, val onClick: () -> Unit)

fun reminderBadgeLabel(count: Int): String? = when {
    count <= 0 -> null
    count > 9 -> "9+"
    else -> count.toString()
}

@Composable
fun HomeCompactHeader(
    avatarBadgeCount: Int,
    tabs: List<HomeCompactTab>,
    selectedTab: String,
    onAvatar: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box {
            IconButton(onClick = onAvatar, modifier = Modifier.size(Height.appBar)) {
                Icon(Icons.Default.AccountCircle, contentDescription = "个人中心")
            }
            reminderBadgeLabel(avatarBadgeCount)?.let { label ->
                Badge(modifier = Modifier.align(Alignment.TopEnd)) { Text(label) }
            }
        }
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                TextButton(onClick = tab.onClick) {
                    val selected = tab.label == selectedTab
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = tab.label, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        Box(Modifier.height(2.dp).fillMaxWidth().background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent))
                    }
                }
            }
        }
        onRefresh?.let { refresh ->
            IconButton(onClick = refresh, modifier = Modifier.size(Height.appBar)) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }
        }
        IconButton(onClick = onSearch, modifier = Modifier.size(Height.appBar)) {
            Icon(Icons.Default.Search, contentDescription = "搜索")
        }
    }
}
