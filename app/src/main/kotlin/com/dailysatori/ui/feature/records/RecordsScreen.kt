package com.dailysatori.ui.feature.records

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.article.ArticleListScreen
import com.dailysatori.ui.feature.diary.DiaryScreen
import com.dailysatori.ui.theme.Spacing

enum class RecordsDestination { Diary, Articles, Favorites }

data class RecordsDestinationItem(
    val title: String,
    val subtitle: String,
    val destination: RecordsDestination,
)

fun recordsDestinations(): List<RecordsDestinationItem> = listOf(
    RecordsDestinationItem("日记", "写下和回看每天的记录", RecordsDestination.Diary),
    RecordsDestinationItem("文章", "管理保存的网页文章", RecordsDestination.Articles),
    RecordsDestinationItem("本地收藏", "集中查看想继续读的内容", RecordsDestination.Favorites),
)

@Composable
fun RecordsScreen(
    onArticleClick: (Long) -> Unit = {},
    onMyClick: () -> Unit = {},
) {
    var selectedDestination by remember { mutableStateOf<RecordsDestination?>(null) }
    BackHandler(enabled = selectedDestination != null) { selectedDestination = null }

    when (selectedDestination) {
        RecordsDestination.Diary -> DiaryScreen(onMyClick = onMyClick)
        RecordsDestination.Articles -> ArticleListScreen(onArticleClick = onArticleClick)
        RecordsDestination.Favorites -> ArticleListScreen(
            onArticleClick = onArticleClick,
            showFavoritesOnly = true,
            lockFavoritesFilter = true,
        )
        null -> RecordsHub(onSelect = { selectedDestination = it }, onMyClick = onMyClick)
    }
}

@Composable
private fun RecordsHub(
    onSelect: (RecordsDestination) -> Unit,
    onMyClick: () -> Unit,
) {
    AppScaffold(
        title = "记录",
        showBack = false,
        myNavigationLabel = "我的",
        onMyNavigationClick = onMyClick,
    ) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().padding(top = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    recordsDestinations().forEachIndexed { index, item ->
                        RecordsRow(item = item, onClick = { onSelect(item.destination) })
                        if (index != recordsDestinations().lastIndex) HorizontalDivider(modifier = Modifier.padding(start = Spacing.xl))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsRow(item: RecordsDestinationItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.m, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(recordsDestinationIcon(item.destination), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun recordsDestinationIcon(destination: RecordsDestination): ImageVector = when (destination) {
    RecordsDestination.Diary -> Icons.AutoMirrored.Filled.MenuBook
    RecordsDestination.Articles -> Icons.AutoMirrored.Filled.Article
    RecordsDestination.Favorites -> Icons.Default.Bookmark
}
