package com.dailysatori.ui.component.appbar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
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
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
                    Text(
                        text = tab.label,
                        color = if (tab.label == selectedTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        IconButton(onClick = onSearch, modifier = Modifier.size(Height.appBar)) {
            Icon(Icons.Default.Search, contentDescription = "搜索")
        }
    }
}
