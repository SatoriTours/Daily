package com.dailysatori.ui.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.feature.aichat.AiChatScreen
import com.dailysatori.ui.feature.article.ArticleListScreen
import com.dailysatori.ui.feature.book.BooksScreen
import com.dailysatori.ui.feature.diary.DiaryScreen
import com.dailysatori.ui.feature.settings.SettingsScreen

data class TabItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val tabs = listOf(
    TabItem("文章", Icons.AutoMirrored.Filled.Article, Icons.AutoMirrored.Outlined.Article),
    TabItem("日记", Icons.Filled.Book, Icons.Outlined.Book),
    TabItem("读书", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    TabItem("AI", Icons.Filled.SmartToy, Icons.Outlined.SmartToy),
    TabItem("设置", Icons.Filled.Settings, Icons.Outlined.Settings),
)

const val AI_CHAT_TAB_INDEX = 3

fun homeBottomBarVisibleForTab(index: Int): Boolean = index != AI_CHAT_TAB_INDEX

@Composable
fun HomeScreen(
    selectedBookId: Long? = null,
    selectedViewpointId: Long? = null,
    bookAnalysisMessage: String? = null,
    onSelectedBookConsumed: () -> Unit = {},
    onArticleClick: (Long) -> Unit = {},
    onAiArticleClick: (Long) -> Unit = {},
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(selectedBookId) {
        if (selectedBookId != null) selectedIndex = 2
    }

    Scaffold(
        bottomBar = {
            if (homeBottomBarVisibleForTab(selectedIndex)) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (selectedIndex == index) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(28.dp),
                                )
                            },
                            selected = selectedIndex == index,
                            onClick = { selectedIndex = index },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.navigationBars,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Crossfade(targetState = selectedIndex) { index ->
                when (index) {
                    0 -> ArticleListScreen(onArticleClick = onArticleClick)
                    1 -> DiaryScreen()
                    2 -> BooksScreen(
                        selectedBookId = selectedBookId,
                        selectedViewpointId = selectedViewpointId,
                        bookAnalysisMessage = bookAnalysisMessage,
                        onSelectedBookConsumed = onSelectedBookConsumed,
                    )
                    AI_CHAT_TAB_INDEX -> AiChatScreen(onArticleClick = onAiArticleClick)
                    4 -> SettingsScreen()
                }
            }
        }
    }
}
