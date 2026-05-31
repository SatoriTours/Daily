package com.dailysatori.ui.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

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
private val HomeBottomBarHazeBlurRadius = 10.dp
private const val HomeBottomBarSlideDurationMillis = 480
private const val HomeBottomBarGlassAlpha = 0.10f
private const val HomeBottomBarAiGlassAlpha = 0.16f
private const val HomeBottomBarHazeTintAlpha = 0.08f
private const val HomeBottomBarHazeNoiseFactor = 0.02f
private const val HomeBottomBarGlassTopHighlightAlpha = 0.08f
private const val HomeBottomBarGlassBottomRefractionAlpha = 0.04f

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
    val hazeState = rememberHazeState()

    LaunchedEffect(tabs.size) {
        if (selectedIndex !in tabs.indices) selectedIndex = 0
    }

    LaunchedEffect(selectedBookId) {
        if (selectedBookId != null) selectedIndex = READING_TAB_INDEX
    }

    Scaffold(contentWindowInsets = WindowInsets.navigationBars) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
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
            if (homeBottomBarVisibleForTab(selectedIndex)) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    HomeBottomBarSurface(
                        selectedIndex = selectedIndex,
                        aiInputController = aiInputController,
                        hazeState = hazeState,
                        onTabSelected = { selectedIndex = it },
                        onHomeClick = { selectedIndex = TODAY_TAB_INDEX },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeBottomBarSurface(
    selectedIndex: Int,
    aiInputController: AiChatInputController?,
    hazeState: HazeState,
    onTabSelected: (Int) -> Unit,
    onHomeClick: () -> Unit,
) {
    val isAiMode = selectedIndex == AI_CHAT_TAB_INDEX
    Box(
        modifier = Modifier
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
    ) {
        HomeGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = HomeBottomBarGlassAlpha),
            hazeState = hazeState,
        ) {
            AnimatedVisibility(
                visible = !isAiMode,
                enter = fadeIn(),
                exit = fadeOut(animationSpec = tween(HomeBottomBarSlideDurationMillis)),
                modifier = Modifier.fillMaxWidth(),
                label = "home-bottom-tabs",
            ) {
                HomeTabNavigationBar(
                    selectedIndex = selectedIndex,
                    onTabSelected = onTabSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(
                visible = isAiMode,
                enter = homeBottomBarEnterTransition(),
                exit = homeBottomBarExitTransition(),
                modifier = Modifier.fillMaxWidth(),
                label = "home-bottom-ai-overlay",
            ) {
                AiCompactInputRow(
                    aiInputController = aiInputController,
                    hazeState = hazeState,
                    onHomeClick = onHomeClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun homeBottomBarEnterTransition(): EnterTransition =
    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(HomeBottomBarSlideDurationMillis)) + fadeIn()

private fun homeBottomBarExitTransition(): ExitTransition =
    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(HomeBottomBarSlideDurationMillis)) + fadeOut(
        animationSpec = tween(HomeBottomBarSlideDurationMillis),
    )

@Composable
private fun AiCompactInputRow(
    aiInputController: AiChatInputController?,
    hazeState: HazeState,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeGlassSurface(
        modifier = modifier.padding(Spacing.xs),
        color = MaterialTheme.colorScheme.surface.copy(alpha = HomeBottomBarAiGlassAlpha),
        hazeState = hazeState,
    ) {
        Row(
            modifier = Modifier.height(HomeBottomBarHeight - Spacing.s).padding(horizontal = Spacing.xs),
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
                compact = true,
            )
        }
    }
}

@Composable
private fun HomeGlassSurface(
    modifier: Modifier = Modifier,
    color: Color,
    hazeState: HazeState,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.circular)
    Box(
        modifier = modifier.clip(shape).hazeEffect(
            state = hazeState,
            style = HazeDefaults.style(
                backgroundColor = Color.Transparent,
                tint = HazeDefaults.tint(MaterialTheme.colorScheme.surface.copy(alpha = HomeBottomBarHazeTintAlpha)),
                blurRadius = HomeBottomBarHazeBlurRadius,
                noiseFactor = HomeBottomBarHazeNoiseFactor,
            ),
        ).background(color),
    ) {
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = HomeBottomBarGlassTopHighlightAlpha),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.primary.copy(alpha = HomeBottomBarGlassBottomRefractionAlpha),
                    ),
                ),
            ),
        )
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        Color.Transparent,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                    ),
                ),
            ),
        )
        content()
    }
}

@Composable
private fun HomeTabNavigationBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.height(HomeBottomBarHeight).padding(Spacing.xs),
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
