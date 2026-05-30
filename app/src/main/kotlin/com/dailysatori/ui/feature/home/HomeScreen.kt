package com.dailysatori.ui.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.feature.aichat.AiChatInputController
import com.dailysatori.ui.feature.aichat.AiChatScreen
import com.dailysatori.ui.feature.aichat.ChatInputField
import com.dailysatori.ui.feature.book.BooksScreen
import com.dailysatori.ui.feature.diary.DiaryScreen
import com.dailysatori.ui.feature.settings.SettingsViewModel
import com.dailysatori.ui.feature.settings.SettingsScreen
import com.dailysatori.ui.feature.unifiednews.UnifiedNewsScreen
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

data class TabItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

const val TODAY_TAB_INDEX = 0
const val DIARY_TAB_INDEX = 1
const val READING_TAB_INDEX = 2
const val AI_CHAT_TAB_INDEX = 3

val tabs = listOf(
    TabItem("今日", Icons.Filled.Language, Icons.Outlined.Language),
    TabItem("日记", Icons.Filled.Book, Icons.Outlined.Book),
    TabItem("读书", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    TabItem("AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
)

private val HomeBottomBarHeight = Height.navBar
private val HomeBottomBarIconSize = IconSize.l

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
    var aiInputController by remember { mutableStateOf<AiChatInputController?>(null) }

    LaunchedEffect(tabs.size) {
        if (selectedIndex !in tabs.indices) selectedIndex = 0
    }

    LaunchedEffect(selectedBookId) {
        if (selectedBookId != null) selectedIndex = READING_TAB_INDEX
    }

    Scaffold(
        bottomBar = {
            if (homeBottomBarVisibleForTab(selectedIndex)) {
                HomeBottomBarSurface(
                    selectedIndex = selectedIndex,
                    aiInputController = aiInputController,
                    onTabSelected = { selectedIndex = it },
                    onHomeClick = { selectedIndex = TODAY_TAB_INDEX },
                )
            }
        },
        contentWindowInsets = WindowInsets.navigationBars,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            Crossfade(targetState = selectedIndex) { index ->
                if (showMy) {
                    SettingsScreen(settingsViewModel, onBack = { showMy = false })
                    return@Crossfade
                }
                val openMy = { showMy = true }
                when (index) {
                    TODAY_TAB_INDEX -> UnifiedNewsScreen(settingsViewModel = settingsViewModel, onArticleClick = onArticleClick, onMyClick = openMy)
                    DIARY_TAB_INDEX -> DiaryScreen(onMyClick = openMy)
                    READING_TAB_INDEX -> BooksScreen(
                        selectedBookId = selectedBookId,
                        selectedViewpointId = selectedViewpointId,
                        bookAnalysisMessage = bookAnalysisMessage,
                        onSelectedBookConsumed = onSelectedBookConsumed,
                        onMyClick = openMy,
                    )
                    AI_CHAT_TAB_INDEX -> AiChatScreen(
                        onArticleClick = onAiArticleClick,
                        onMyClick = openMy,
                        onInputControllerChange = { aiInputController = it },
                    )
                    else -> UnifiedNewsScreen(settingsViewModel = settingsViewModel, onArticleClick = onArticleClick, onMyClick = openMy)
                }
            }
        }
    }
}

@Composable
private fun HomeBottomBarSurface(
    selectedIndex: Int,
    aiInputController: AiChatInputController?,
    onTabSelected: (Int) -> Unit,
    onHomeClick: () -> Unit,
) {
    val transition = updateTransition(targetState = selectedIndex == AI_CHAT_TAB_INDEX, label = "home-bottom-ai")
    val aiProgress by transition.animateFloat(label = "home-bottom-ai-progress") { isAiMode ->
        if (isAiMode) 1f else 0f
    }
    val inputWeight by transition.animateFloat(label = "home-bottom-ai-input-weight") { isAiMode ->
        if (isAiMode) 1f else 0.001f
    }
    val tabsWeight by transition.animateFloat(label = "home-bottom-tabs-weight") { isAiMode ->
        if (isAiMode) 0.001f else 1f
    }
    val containerAlpha by transition.animateFloat(label = "home-bottom-container-alpha") { isAiMode ->
        if (isAiMode) 0f else 0.92f
    }
    Surface(
        modifier = Modifier.navigationBarsPadding()
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        shape = RoundedCornerShape(Radius.circular),
        color = MaterialTheme.colorScheme.surface.copy(alpha = containerAlpha),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outlineVariant.copy(alpha = containerAlpha)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(HomeBottomBarHeight).padding(Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AiCompactInputRow(
                aiInputController = aiInputController,
                inputWeight = inputWeight,
                progress = aiProgress,
                onHomeClick = onHomeClick,
                modifier = Modifier.weight(inputWeight),
            )
            HomeTabNavigationBar(
                selectedIndex = selectedIndex,
                tabsWeight = tabsWeight,
                progress = aiProgress,
                onTabSelected = onTabSelected,
                modifier = Modifier.weight(tabsWeight),
            )
        }
    }
}

@Composable
private fun AiCompactInputRow(
    aiInputController: AiChatInputController?,
    inputWeight: Float,
    progress: Float,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.alpha(progress),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onHomeClick, modifier = Modifier.size(HomeBottomBarHeight - Spacing.s)) {
            Icon(
                Icons.Filled.Language,
                contentDescription = "回到今日",
                modifier = Modifier.size(HomeBottomBarIconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        ChatInputField(
            inputText = aiInputController?.inputText.orEmpty(),
            onInputChange = aiInputController?.onInputChange ?: {},
            onSend = aiInputController?.onSend ?: {},
            onStop = aiInputController?.onStop ?: {},
            isProcessing = aiInputController?.isProcessing ?: false,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeTabNavigationBar(
    selectedIndex: Int,
    tabsWeight: Float,
    progress: Float,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.height(HomeBottomBarHeight).alpha(1f - progress),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        tabs.forEachIndexed { index, tab ->
            NavigationBarItem(
                icon = {
                    Icon(
                        if (selectedIndex == index) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(HomeBottomBarIconSize),
                    )
                },
                label = null,
                alwaysShowLabel = false,
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                ),
            )
        }
    }
}
