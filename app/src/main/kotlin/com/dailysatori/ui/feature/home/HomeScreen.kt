package com.dailysatori.ui.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Language
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.feature.aichat.AiChatScreen
import com.dailysatori.ui.feature.book.BooksScreen
import com.dailysatori.ui.feature.records.RecordsScreen
import com.dailysatori.ui.feature.settings.SettingsViewModel
import com.dailysatori.ui.feature.settings.SettingsScreen
import com.dailysatori.ui.feature.unifiednews.UnifiedNewsScreen

data class TabItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

const val TODAY_TAB_INDEX = 0
const val RECORDS_TAB_INDEX = 1
const val READING_TAB_INDEX = 2
const val AI_CHAT_TAB_INDEX = 3

val tabs = listOf(
    TabItem("今日", Icons.Filled.Language, Icons.Outlined.Language),
    TabItem("记录", Icons.Filled.Book, Icons.Outlined.Book),
    TabItem("读书", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    TabItem("AI", Icons.Filled.SmartToy, Icons.Outlined.SmartToy),
)

fun homeBottomBarVisibleForTab(index: Int): Boolean = index in tabs.indices

@Composable
fun HomeScreen(
    selectedBookId: Long? = null,
    selectedViewpointId: Long? = null,
    bookAnalysisMessage: String? = null,
    onSelectedBookConsumed: () -> Unit = {},
    onArticleClick: (Long) -> Unit = {},
    onAiArticleClick: (Long) -> Unit = {},
    settingsViewModel: SettingsViewModel,
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var showMy by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(tabs.size) {
        if (selectedIndex !in tabs.indices) selectedIndex = 0
    }

    LaunchedEffect(selectedBookId) {
        if (selectedBookId != null) selectedIndex = READING_TAB_INDEX
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
                if (showMy) {
                    SettingsScreen(settingsViewModel, onBack = { showMy = false })
                    return@Crossfade
                }
                val openMy = { showMy = true }
                when (index) {
                    TODAY_TAB_INDEX -> UnifiedNewsScreen(settingsViewModel = settingsViewModel, onArticleClick = onArticleClick, onMyClick = openMy)
                    RECORDS_TAB_INDEX -> RecordsScreen(onArticleClick = onArticleClick, onMyClick = openMy)
                    READING_TAB_INDEX -> BooksScreen(
                        selectedBookId = selectedBookId,
                        selectedViewpointId = selectedViewpointId,
                        bookAnalysisMessage = bookAnalysisMessage,
                        onSelectedBookConsumed = onSelectedBookConsumed,
                        onMyClick = openMy,
                    )
                    AI_CHAT_TAB_INDEX -> AiChatScreen(onArticleClick = onAiArticleClick, onMyClick = openMy)
                    else -> UnifiedNewsScreen(settingsViewModel = settingsViewModel, onArticleClick = onArticleClick, onMyClick = openMy)
                }
            }
        }
    }
}
