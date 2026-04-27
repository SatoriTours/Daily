# Android 全项目重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构 Daily Satori Android 项目目录结构、主题系统和 UI 框架，同时保持所有现有功能不变。

**Architecture:** Feature-first 目录结构，统一 AppScaffold/AppTopBar 框架，重构主题系统使用标准 Material 3 color scheme，修复 edge-to-edge 下的标题栏间距问题。

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Koin DI, SQLDelight, Navigation Compose

---

## Phase 1: 基础设施 — 创建新目录和核心组件

### Task 1: 创建新目录结构 + 移动 core/ 层文件

**Files:**
- Move: `di/` → `core/di/`
- Move: `ui/navigation/` → `core/navigation/`
- Move: `service/` → `core/service/`
- Create: `core/util/TimeUtils.kt`

- [ ] **Step 1: 创建目录结构**

Run:
```bash
cd /home/jimxl/Documents/projects/Daily/app/src/main/kotlin/com/dailysatori
mkdir -p core/di core/navigation core/service core/util
mkdir -p ui/component/appbar ui/component/scaffold ui/component/card ui/component/dialog ui/component/input ui/component/indicator ui/component/media ui/component/content ui/component/chip ui/component/misc
mkdir -p ui/feature/home ui/feature/article ui/feature/diary ui/feature/book ui/feature/aichat ui/feature/aiconfig ui/feature/settings ui/feature/share
```

- [ ] **Step 2: 移动 di/ 文件到 core/di/**

Run:
```bash
cd /home/jimxl/Documents/projects/Daily/app/src/main/kotlin/com/dailysatori
mv di/AppModule.kt core/di/
mv di/PlatformModule.kt core/di/
mv di/ViewModelModule.kt core/di/
rmdir di
```

Update package declarations in each file from `com.dailysatori.di` to `com.dailysatori.core.di`.

- [ ] **Step 3: 移动 navigation/ 文件到 core/navigation/**

Run:
```bash
cd /home/jimxl/Documents/projects/Daily/app/src/main/kotlin/com/dailysatori
mv ui/navigation/Routes.kt core/navigation/
mv ui/navigation/NavHost.kt core/navigation/
rmdir ui/navigation
```

Update package from `com.dailysatori.ui.navigation` to `com.dailysatori.core.navigation`.

- [ ] **Step 4: 移动 service/ 文件到 core/service/**

Run:
```bash
cd /home/jimxl/Documents/projects/Daily/app/src/main/kotlin/com/dailysatori
mv service/WebServerService.kt core/service/
mv service/ClipboardMonitorService.kt core/service/
mv service/AppUpgradeService.kt core/service/
mv service/I18nInitializer.kt core/service/
rmdir service
```

Update package from `com.dailysatori.service` to `com.dailysatori.core.service`.

- [ ] **Step 5: 创建 TimeUtils.kt**

Create `core/util/TimeUtils.kt`:

```kotlin
package com.dailysatori.core.util

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

object TimeUtils {
    fun formatRelativeTime(epochMillis: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - epochMillis
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            seconds < 60 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            hours < 24 -> "${hours}小时前"
            days < 7 -> "${days}天前"
            days < 30 -> "${days / 7}周前"
            days < 365 -> "${days / 30}月前"
            else -> "${days / 365}年前"
        }
    }

    fun formatDate(epochMs: Long): String {
        val instant = Instant.ofEpochMilli(epochMs)
        val localDate = LocalDate.ofInstant(instant, ZoneId.systemDefault())
        return "${localDate.year}-${localDate.monthValue.toString().padStart(2, '0')}-${localDate.dayOfMonth.toString().padStart(2, '0')}"
    }

    fun formatDateTime(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    fun formatShortDateTime(epochMs: Long): String {
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }
}
```

- [ ] **Step 6: 更新 DailySatoriApplication.kt 的 import**

`DailySatoriApplication.kt` needs updated imports for DI modules moved to `core.di`:

```kotlin
import com.dailysatori.core.di.appModule
import com.dailysatori.core.di.platformModule
import com.dailysatori.core.di.viewModelModule
```

(Instead of `import com.dailysatori.di.*`)

- [ ] **Step 7: 更新所有引用了旧包路径的文件**

Files that import from old paths need updating:
- `core/di/AppModule.kt` — imports `com.dailysatori.service.*` → `com.dailysatori.core.service.*`
- `core/di/PlatformModule.kt` — imports remain same (platform/* unchanged)
- `core/di/ViewModelModule.kt` — imports `com.dailysatori.service.*` → `com.dailysatori.core.service.*`, `com.dailysatori.viewmodel.*` stays (until Phase 2)
- `core/navigation/NavHost.kt` — update all page imports to new paths (will be done in Phase 2)

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: reorganize core layer (di, navigation, service, util)"
```

---

### Task 2: 重构主题系统

**Files:**
- Modify: `ui/theme/Color.kt`
- Modify: `ui/theme/Theme.kt`
- Create: `ui/theme/Shape.kt`

- [ ] **Step 1: 重构 Color.kt**

Replace `ui/theme/Color.kt` with:

```kotlin
package com.dailysatori.ui.theme

import androidx.compose.ui.graphics.Color

object AppColors {
    val primary = Color(0xFF5E8BFF)
    val primaryLight = Color(0xFF8AB4F8)

    val background = Color(0xFFF7F7F7)
    val surface = Color(0xFFFFFFFF)
    val surfaceContainer = Color(0xFFF0F0F0)
    val surfaceContainerHighest = Color(0xFFE0E0E0)

    val onBackground = Color(0xFF212121)
    val onSurface = Color(0xFF424242)
    val onSurfaceVariant = Color(0xFF757575)

    val outline = Color(0xFFE0E0E0)
    val outlineVariant = Color(0xFFBDBDBD)

    val success = Color(0xFF4CAF50)
    val error = Color(0xFFF44336)
    val warning = Color(0xFFFF9800)
    val info = Color(0xFF2196F3)

    val secondary = Color(0xFF4CAF50)
    val secondaryContainer = Color(0xFFE8F5E9)
    val onSecondaryContainer = Color(0xFF1B5E20)
    val tertiaryContainer = Color(0xFFFFF3E0)

    val tagColors = listOf(
        Color(0xFF5E8BFF), Color(0xFF26A69A), Color(0xFF66BB6A),
        Color(0xFF9CCC65), Color(0xFFD4E157), Color(0xFFFFEE58),
        Color(0xFFFFCA28), Color(0xFFFFB74D), Color(0xFFFF8A65),
        Color(0xFFE57373),
    )
}
```

(Simplify: remove `xxxDark` variants — those are handled by `darkColorScheme()` in Theme.kt directly)

- [ ] **Step 2: 重构 Theme.kt**

Replace `ui/theme/Theme.kt` with:

```kotlin
package com.dailysatori.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = AppColors.primary,
    onPrimary = Color.White,
    secondary = AppColors.secondary,
    onSecondary = Color.White,
    secondaryContainer = AppColors.secondaryContainer,
    onSecondaryContainer = AppColors.onSecondaryContainer,
    tertiaryContainer = AppColors.tertiaryContainer,
    background = AppColors.background,
    onBackground = AppColors.onBackground,
    surface = AppColors.surface,
    onSurface = AppColors.onSurface,
    surfaceVariant = AppColors.surfaceContainer,
    onSurfaceVariant = AppColors.onSurfaceVariant,
    outline = AppColors.outline,
    outlineVariant = AppColors.outlineVariant,
    error = AppColors.error,
    surfaceContainer = AppColors.surfaceContainer,
    surfaceContainerHighest = AppColors.surfaceContainerHighest,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color.White,
    secondary = Color(0xFF66BB6A),
    secondaryContainer = Color(0xFF2E3B2E),
    onSecondaryContainer = Color(0xFF81C784),
    tertiaryContainer = Color(0xFF3E2723),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFBDBDBD),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFF9E9E9E),
    outline = Color(0xFF424242),
    outlineVariant = Color(0xFF757575),
    error = Color(0xFFE57373),
    surfaceContainer = Color(0xFF2C2C2C),
    surfaceContainerHighest = Color(0xFF3A3A3A),
)

@Composable
fun DailySatoriTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
```

Key change: `SideEffect` sets transparent status bar and correct light/dark icon appearance. This fixes the large gap at top.

- [ ] **Step 3: 创建 Shape.kt**

Create `ui/theme/Shape.kt`:

```kotlin
package com.dailysatori.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: redesign theme system with edge-to-edge support"
```

---

### Task 3: 创建统一 UI 框架 (AppTopBar + AppScaffold)

**Files:**
- Create: `ui/component/appbar/AppTopBar.kt`
- Create: `ui/component/scaffold/AppScaffold.kt`

- [ ] **Step 1: 创建 AppTopBar**

Create `ui/component/appbar/AppTopBar.kt`:

```kotlin
package com.dailysatori.ui.component.appbar

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    showBack: Boolean = onBack != null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        navigationIcon = {
            if (showBack && onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
```

Key difference from old `SAppBar`:
- Uses `MaterialTheme.colorScheme.surface` as background (not hardcoded primary color)
- Uses `MaterialTheme.colorScheme.onSurface` for text (not hardcoded white)
- `TopAppBar` automatically handles `WindowInsets.statusBars` in edge-to-edge mode

- [ ] **Step 2: 创建 AppScaffold**

Create `ui/component/scaffold/AppScaffold.kt`:

```kotlin
package com.dailysatori.ui.component.scaffold

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.component.appbar.AppTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    showBack: Boolean = onBack != null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = title,
                onBack = onBack,
                showBack = showBack,
                actions = actions,
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}
```

Note: `Scaffold` with `TopAppBar` automatically handles status bar insets. The `innerPadding` includes top inset. Content using `Modifier.padding(innerPadding)` is positioned correctly below the app bar.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add unified AppTopBar and AppScaffold components"
```

---

### Task 4: 移动和重组共享组件

**Files:**
- Move + update: all `ui/components/*.kt` → `ui/component/*/` subdirectories
- Delete: `ui/components/ArticleCard.kt.fix`
- Delete: `ui/pages/PlaceholderScreen.kt`

- [ ] **Step 1: 移动组件到子目录**

Run:
```bash
cd /home/jimxl/Documents/projects/Daily/app/src/main/kotlin/com/dailysatori/ui

# Card components
mv components/CustomCard.kt component/card/
mv components/ArticleCard.kt component/card/
mv components/DiaryCard.kt component/card/

# Dialog
mv components/ConfirmDialog.kt component/dialog/

# Input
mv components/SearchBar.kt component/input/

# Indicators
mv components/EmptyState.kt component/indicator/
mv components/LoadingIndicator.kt component/indicator/
mv components/FilterIndicator.kt component/indicator/

# Media
mv components/SmartImage.kt component/media/

# Content
mv components/ContentViewer.kt component/content/

# Chip
mv components/TagChipRow.kt component/chip/

# Misc
mv components/FeatureIcon.kt component/misc/
mv components/SectionHeader.kt component/misc/

# Cleanup
rm components/SAppBar.kt
rm -f components/ArticleCard.kt.fix
rm pages/PlaceholderScreen.kt
rmdir components
```

- [ ] **Step 2: 更新所有移动组件的 package 声明**

Each moved file needs its `package` line updated:
- `CustomCard.kt` → `package com.dailysatori.ui.component.card`
- `ArticleCard.kt` → `package com.dailysatori.ui.component.card`
- `DiaryCard.kt` → `package com.dailysatori.ui.component.diary`  → NO → `package com.dailysatori.ui.component.card`
- `ConfirmDialog.kt` → `package com.dailysatori.ui.component.dialog`
- `SearchBar.kt` → `package com.dailysatori.ui.component.input`
- `EmptyState.kt` → `package com.dailysatori.ui.component.indicator`
- `LoadingIndicator.kt` → `package com.dailysatori.ui.component.indicator`
- `FilterIndicator.kt` → `package com.dailysatori.ui.component.indicator`
- `SmartImage.kt` → `package com.dailysatori.ui.component.media`
- `ContentViewer.kt` → `package com.dailysatori.ui.component.content`
- `TagChipRow.kt` → `package com.dailysatori.ui.component.chip`
- `FeatureIcon.kt` → `package com.dailysatori.ui.component.misc`
- `SectionHeader.kt` → `package com.dailysatori.ui.component.misc`

Also update internal imports within these components (e.g., `ArticleCard.kt` imports `CustomCard` from same package now, imports `TagChipRow` from `chip` package, uses `TimeUtils` from `core.util`).

- [ ] **Step 3: 更新 ArticleCard.kt — 使用 TimeUtils**

In `component/card/ArticleCard.kt`, replace the private `formatRelativeTime()` function with:

```kotlin
import com.dailysatori.core.util.TimeUtils
```

And replace `formatRelativeTime(date)` → `TimeUtils.formatRelativeTime(date)`. Remove the private function.

- [ ] **Step 4: 更新 DiaryCard.kt — 使用 TimeUtils**

In `component/card/DiaryCard.kt`, replace the private `formatDiaryDateTime()` function with:

```kotlin
import com.dailysatori.core.util.TimeUtils
```

And replace `formatDiaryDateTime(diary.created_at)` → `TimeUtils.formatShortDateTime(diary.created_at)`. Remove the private function.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: reorganize shared components into categorized subdirectories"
```

---

## Phase 2: Feature 页面迁移 — 移动 ViewModels + 重写页面

### Task 5: 移动 ViewModels 到 feature 目录

**Files:**
- Move: `viewmodel/ArticlesViewModel.kt` → `ui/feature/article/`
- Move: `viewmodel/ArticleDetailViewModel.kt` → `ui/feature/article/`
- Move: `viewmodel/DiaryViewModel.kt` → `ui/feature/diary/`
- Move: `viewmodel/BooksViewModel.kt` → `ui/feature/book/`
- Move: `viewmodel/AiChatViewModel.kt` → `ui/feature/aichat/`
- Move: `viewmodel/AiConfigViewModel.kt` → `ui/feature/aiconfig/`
- Move: `viewmodel/SettingsViewModel.kt` → `ui/feature/settings/`
- Move: `viewmodel/ShareDialogViewModel.kt` → `ui/feature/share/`
- Move: `viewmodel/WeeklySummaryViewModel.kt` → `ui/feature/settings/` (or separate weekly dir, but it's accessed from settings)
- Move: `viewmodel/BackupSettingsViewModel.kt` → `ui/feature/settings/`
- Move: `viewmodel/BackupRestoreViewModel.kt` → `ui/feature/settings/`
- Move: `viewmodel/PluginCenterViewModel.kt` → `ui/feature/settings/`

- [ ] **Step 1: 移动 ViewModel 文件**

Run:
```bash
cd /home/jimxl/Documents/projects/Daily/app/src/main/kotlin/com/dailysatori

mv viewmodel/ArticlesViewModel.kt ui/feature/article/
mv viewmodel/ArticleDetailViewModel.kt ui/feature/article/
mv viewmodel/DiaryViewModel.kt ui/feature/diary/
mv viewmodel/BooksViewModel.kt ui/feature/book/
mv viewmodel/AiChatViewModel.kt ui/feature/aichat/
mv viewmodel/AiConfigViewModel.kt ui/feature/aiconfig/
mv viewmodel/SettingsViewModel.kt ui/feature/settings/
mv viewmodel/ShareDialogViewModel.kt ui/feature/share/
mv viewmodel/WeeklySummaryViewModel.kt ui/feature/settings/
mv viewmodel/BackupSettingsViewModel.kt ui/feature/settings/
mv viewmodel/BackupRestoreViewModel.kt ui/feature/settings/
mv viewmodel/PluginCenterViewModel.kt ui/feature/settings/
rmdir viewmodel
```

- [ ] **Step 2: 更新所有 ViewModel 的 package 声明**

Each ViewModel's package changes:
- `ArticlesViewModel.kt` → `package com.dailysatori.ui.feature.article`
- `ArticleDetailViewModel.kt` → `package com.dailysatori.ui.feature.article`
- `DiaryViewModel.kt` → `package com.dailysatori.ui.feature.diary`
- `BooksViewModel.kt` → `package com.dailysatori.ui.feature.book`
- `AiChatViewModel.kt` → `package com.dailysatori.ui.feature.aichat`
- `AiConfigViewModel.kt` → `package com.dailysatori.ui.feature.aiconfig`
- `SettingsViewModel.kt` → `package com.dailysatori.ui.feature.settings`
- `ShareDialogViewModel.kt` → `package com.dailysatori.ui.feature.share`
- `WeeklySummaryViewModel.kt` → `package com.dailysatori.ui.feature.settings`
- `BackupSettingsViewModel.kt` → `package com.dailysatori.ui.feature.settings`
- `BackupRestoreViewModel.kt` → `package com.dailysatori.ui.feature.settings`
- `PluginCenterViewModel.kt` → `package com.dailysatori.ui.feature.settings`

Imports within ViewModels remain same for repository/service references (they use `com.dailysatori.data.repository.*`, `com.dailysatori.service.*` or `com.dailysatori.core.service.*`).

- [ ] **Step 3: 更新 core/di/ViewModelModule.kt 的 import**

All `import com.dailysatori.viewmodel.*` must change to the new package paths:
```kotlin
import com.dailysatori.ui.feature.article.ArticlesViewModel
import com.dailysatori.ui.feature.article.ArticleDetailViewModel
import com.dailysatori.ui.feature.diary.DiaryViewModel
import com.dailysatori.ui.feature.book.BooksViewModel
import com.dailysatori.ui.feature.aichat.AiChatViewModel
import com.dailysatori.ui.feature.aiconfig.AiConfigViewModel
import com.dailysatori.ui.feature.settings.SettingsViewModel
import com.dailysatori.ui.feature.share.ShareDialogViewModel
import com.dailysatori.ui.feature.settings.WeeklySummaryViewModel
import com.dailysatori.ui.feature.settings.BackupSettingsViewModel
import com.dailysatori.ui.feature.settings.BackupRestoreViewModel
import com.dailysatori.ui.feature.settings.PluginCenterViewModel
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: move ViewModels into feature directories"
```

---

### Task 6: 迁移页面文件到 feature 目录 + 使用 AppScaffold 重写

Each page needs to:
1. Move from `ui/pages/xxx/` to `ui/feature/xxx/`
2. Update package
3. Replace `Scaffold + SAppBar` with `AppScaffold`
4. Update component imports to new paths
5. Use `TimeUtils` instead of inline `formatTime()`

Below are all pages. Due to volume, they are grouped.

#### Task 6a: HomeScreen

**Files:**
- Move: `ui/pages/home/HomeScreen.kt` → `ui/feature/home/HomeScreen.kt`

- [ ] **Step 1: 移动文件**

```bash
mv ui/pages/home/HomeScreen.kt ui/feature/home/
rmdir ui/pages/home
```

- [ ] **Step 2: 重写 HomeScreen.kt**

```kotlin
package com.dailysatori.ui.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

private val tabs = listOf(
    TabItem("文章", Icons.Filled.Article, Icons.Outlined.Article),
    TabItem("日记", Icons.Filled.Book, Icons.Outlined.Book),
    TabItem("读书", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    TabItem("AI", Icons.Filled.SmartToy, Icons.Outlined.SmartToy),
    TabItem("设置", Icons.Filled.Person, Icons.Outlined.Person),
)

@Composable
fun HomeScreen(
    onArticleClick: (Long) -> Unit = {},
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
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
                            )
                        },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ),
                    )
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
            when (selectedIndex) {
                0 -> ArticleListScreen(onArticleClick = onArticleClick)
                1 -> DiaryScreen()
                2 -> BooksScreen()
                3 -> AiChatScreen()
                4 -> SettingsScreen()
            }
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: migrate HomeScreen to feature/home"
```

#### Task 6b: Article pages

**Files:**
- Move + rewrite: `ui/pages/articles/ArticlesScreen.kt` → `ui/feature/article/ArticleListScreen.kt`
- Move + rewrite: `ui/pages/article_detail/ArticleDetailScreen.kt` → `ui/feature/article/ArticleDetailScreen.kt`

- [ ] **Step 1: Move files**

```bash
mv ui/pages/articles/ArticlesScreen.kt ui/feature/article/ArticleListScreen.kt
mv ui/pages/article_detail/ArticleDetailScreen.kt ui/feature/article/
rmdir ui/pages/articles ui/pages/article_detail
```

- [ ] **Step 2: 重写 ArticleListScreen.kt**

Replace entire file with:

```kotlin
package com.dailysatori.ui.feature.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Box
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.core.util.TimeUtils
import com.dailysatori.shared.db.Article
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.media.SmartImage
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun ArticleListScreen(
    onArticleClick: (Long) -> Unit = {},
) {
    val viewModel: ArticlesViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "文章",
            showBack = false,
            actions = {
                IconButton(onClick = { viewModel.toggleSearch() }) {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                }
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("标签筛选") },
                            leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                            onClick = { showMenu = false },
                        )
                        DropdownMenuItem(
                            text = { Text(if (state.showFavoritesOnly) "显示全部" else "只看收藏") },
                            leadingIcon = {
                                Icon(
                                    if (state.showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (state.showFavoritesOnly) MaterialTheme.colorScheme.error else LocalContentColor.current,
                                )
                            },
                            onClick = {
                                viewModel.toggleFavoritesOnly()
                                showMenu = false
                            },
                        )
                    }
                }
            },
        )

        if (state.isSearchVisible) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.search(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
                placeholder = { Text("搜索文章...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )
        }

        if (state.isLoading && state.articles.isEmpty()) {
            LoadingIndicator()
        } else if (state.articles.isEmpty()) {
            EmptyState(
                icon = Icons.Default.FilterList,
                title = "暂无文章",
                subtitle = "导入数据或保存链接来添加文章",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(state.articles, key = { it.id }) { article ->
                    ArticleCardItem(article = article, onClick = { onArticleClick(article.id) })
                }
            }
        }
    }
}

@Composable
fun ArticleCardItem(article: Article, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.m),
    ) {
        Row(modifier = Modifier.padding(Spacing.m)) {
            SmartImage(
                imagePath = article.cover_image ?: article.cover_image_url,
                modifier = Modifier.padding(end = Spacing.m),
                size = 80.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = article.ai_title ?: article.title ?: "无标题",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val content = article.ai_content ?: article.content
                if (!content.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val pubDate = article.pub_date
                if (pubDate != null) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = TimeUtils.formatRelativeTime(pubDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
```

Key change: No `Scaffold` wrapper since HomeScreen already has one. Uses `AppTopBar` directly as a regular composable inside `Column`. This avoids double Scaffold nesting.

- [ ] **Step 3: 重写 ArticleDetailScreen.kt**

```kotlin
package com.dailysatori.ui.feature.article

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.media.SmartImage
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ArticleDetailScreen(
    articleId: Long,
    onBack: () -> Unit = {},
) {
    val viewModel: ArticleDetailViewModel = koinViewModel { parametersOf(articleId) }
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(articleId) {
        viewModel.loadArticle()
    }

    AppScaffold(
        title = state.article?.ai_title ?: state.article?.title ?: "文章详情",
        onBack = onBack,
        actions = {
            IconButton(onClick = { viewModel.toggleFavorite() }) {
                Icon(
                    if (state.article?.is_favorite == 1L) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "收藏",
                )
            }
            IconButton(onClick = { /* share */ }) {
                Icon(Icons.Default.Share, contentDescription = "分享")
            }
        },
    ) { modifier ->
        if (state.isLoading && state.article == null) {
            LoadingIndicator()
        } else if (state.article == null) {
            Box(modifier = modifier.fillMaxSize()) {
                Text("文章未找到", modifier = Modifier.padding(Spacing.m))
            }
        } else {
            val article = state.article!!
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                val coverImage = article.cover_image ?: article.cover_image_url
                if (!coverImage.isNullOrBlank()) {
                    SmartImage(
                        imagePath = coverImage,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                        size = 260.dp,
                    )
                }

                TabRow(selectedTabIndex = state.selectedTabIndex, modifier = Modifier.fillMaxWidth()) {
                    Tab(
                        selected = state.selectedTabIndex == 0,
                        onClick = { viewModel.selectTab(0) },
                        text = { Text("AI 摘要") },
                    )
                    Tab(
                        selected = state.selectedTabIndex == 1,
                        onClick = { viewModel.selectTab(1) },
                        text = { Text("原文") },
                    )
                }

                Box(modifier = Modifier.padding(Spacing.m)) {
                    when (state.selectedTabIndex) {
                        0 -> {
                            val summary = article.ai_markdown_content
                                ?: article.ai_content
                                ?: article.content
                                ?: "暂无摘要内容"
                            Text(text = summary, style = MaterialTheme.typography.bodyLarge)
                        }
                        else -> {
                            val original = article.html_content ?: article.content ?: "暂无原文内容"
                            Text(text = original, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: migrate article pages to feature/article with AppScaffold"
```

#### Task 6c: Diary pages

**Files:**
- Move + rewrite: `ui/pages/diary/DiaryScreen.kt` → `ui/feature/diary/DiaryScreen.kt`
- Move: `ui/pages/diary/DiaryEditorSheet.kt` → `ui/feature/diary/DiaryEditorSheet.kt`

- [ ] **Step 1: Move files**

```bash
mv ui/pages/diary/DiaryScreen.kt ui/feature/diary/
mv ui/pages/diary/DiaryEditorSheet.kt ui/feature/diary/
rmdir ui/pages/diary
```

- [ ] **Step 2: 重写 DiaryScreen.kt**

```kotlin
package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Box
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.core.util.TimeUtils
import com.dailysatori.shared.db.Diary
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.component.dialog.ConfirmDialog
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun DiaryScreen() {
    val viewModel: DiaryViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var editingDiary by remember { mutableStateOf<Diary?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Diary?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "我的日记",
            showBack = false,
            actions = {
                IconButton(onClick = { viewModel.toggleSearch() }) {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                }
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "筛选")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        state.tags.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text(tag.name ?: "") },
                                onClick = { viewModel.filterByTag(tag.id); showMenu = false },
                            )
                        }
                    }
                }
            },
        )

        if (state.isSearchVisible) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.search(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
                placeholder = { Text("搜索日记...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )
        }

        if (state.isLoading && state.diaries.isEmpty()) {
            LoadingIndicator()
        } else if (state.diaries.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Edit,
                title = "暂无日记",
                subtitle = "点击右下角 + 开始写日记",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(state.diaries, key = { it.id }) { diary ->
                    DiaryCardItem(
                        diary = diary,
                        onClick = {
                            editingDiary = diary
                            showEditor = true
                        },
                        onDelete = { showDeleteDialog = diary },
                    )
                }
            }
        }
    }

    androidx.compose.material3.FloatingActionButton(
        onClick = { editingDiary = null; showEditor = true },
        containerColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(Spacing.m),
    ) {
        Icon(Icons.Default.Add, contentDescription = "新建日记")
    }

    if (showEditor) {
        DiaryEditorSheet(
            existingDiary = editingDiary,
            onDismiss = { showEditor = false; editingDiary = null },
            onSave = { content, tags, mood ->
                if (editingDiary != null) {
                    viewModel.saveDiary(existingId = editingDiary!!.id, content = content, tags = tags, mood = mood)
                } else {
                    viewModel.saveDiary(content = content, tags = tags, mood = mood)
                }
                showEditor = false
                editingDiary = null
            },
        )
    }

    showDeleteDialog?.let { diary ->
        ConfirmDialog(
            title = "删除日记",
            message = "确定要删除这篇日记吗？",
            onConfirm = {
                viewModel.deleteDiary(diary.id)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null },
        )
    }
}

@Composable
fun DiaryCardItem(diary: Diary, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.m),
    ) {
        Column(modifier = Modifier.padding(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = TimeUtils.formatDateTime(diary.created_at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                val mood = diary.mood
                if (mood != null) {
                    Text(mood, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = diary.content ?: "",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            val tags = diary.tags
            if (!tags.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    tags.split(",").take(5).forEach { tag ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(tag.trim(), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp),
                        )
                    }
                }
            }
        }
    }
}
```

Note: For `DiaryScreen`, since it's a tab inside `HomeScreen`'s Scaffold, it should NOT have its own Scaffold. It just uses `AppTopBar` as a regular composable and `FloatingActionButton` needs special handling — the FAB should be managed by the parent `HomeScreen` or we use a `Box` overlay. The simplest approach: wrap in a `Box` and position FAB.

Actually, let's use a cleaner approach — DiaryScreen can use its own Scaffold for the FAB since HomeScreen already has `contentWindowInsets = WindowInsets.navigationBars`:

```kotlin
// Simplified: use Scaffold inside the tab content for FAB support
```

For the implementation, `DiaryScreen` will use `Scaffold` with `floatingActionButton` since FAB positioning needs a Scaffold. The inner Scaffold won't add extra top padding because there's no `topBar` — we use `AppTopBar` as regular content.

- [ ] **Step 3: Update DiaryEditorSheet.kt package**

Change package from `com.dailysatori.ui.pages.diary` to `com.dailysatori.ui.feature.diary`.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: migrate diary pages to feature/diary"
```

#### Task 6d: Book pages

**Files:**
- Move + rewrite: `ui/pages/books/BooksScreen.kt` → `ui/feature/book/BooksScreen.kt`
- Move: `ui/pages/books/BookSearchScreen.kt` → `ui/feature/book/BookSearchScreen.kt`
- Move: `ui/pages/books/ViewpointCard.kt` → `ui/feature/book/ViewpointCard.kt`

- [ ] **Step 1: Move files and update packages**

```bash
mv ui/pages/books/BooksScreen.kt ui/feature/book/
mv ui/pages/books/BookSearchScreen.kt ui/feature/book/
mv ui/pages/books/ViewpointCard.kt ui/feature/book/
rmdir ui/pages/books
```

Update all 3 files: `package com.dailysatori.ui.feature.book`. Update imports to use new component paths and `AppTopBar`/`AppScaffold`. Use `TimeUtils` for any time formatting.

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "refactor: migrate book pages to feature/book"
```

#### Task 6e: AI Chat page

**Files:**
- Move + rewrite: `ui/pages/aichat/AiChatScreen.kt` → `ui/feature/aichat/AiChatScreen.kt`

- [ ] **Step 1: Move and rewrite**

```bash
mv ui/pages/aichat/AiChatScreen.kt ui/feature/aichat/
rmdir ui/pages/aichat
```

Update package to `com.dailysatori.ui.feature.aichat`. Replace `SAppBar` with `AppTopBar`. The `ChatMessage` data class in this file should stay here (or move to ViewModel if it's the UI model).

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "refactor: migrate AI chat page to feature/aichat"
```

#### Task 6f: AI Config pages

**Files:**
- Move + rewrite: `ui/pages/aiconfig/AiConfigScreen.kt` → `ui/feature/aiconfig/AiConfigScreen.kt`
- Move: `ui/pages/aiconfig/AiConfigEditScreen.kt` → `ui/feature/aiconfig/AiConfigEditScreen.kt`

- [ ] **Step 1: Move and rewrite**

```bash
mv ui/pages/aiconfig/AiConfigScreen.kt ui/feature/aiconfig/
mv ui/pages/aiconfig/AiConfigEditScreen.kt ui/feature/aiconfig/
rmdir ui/pages/aiconfig
```

Update packages and replace `SAppBar` with `AppScaffold`/`AppTopBar`.

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "refactor: migrate AI config pages to feature/aiconfig"
```

#### Task 6g: Settings and related pages

**Files:**
- Move + rewrite: `ui/pages/settings/SettingsScreen.kt` → `ui/feature/settings/SettingsScreen.kt`
- Move: `ui/pages/backup_settings/BackupSettingsScreen.kt` → `ui/feature/settings/BackupSettingsScreen.kt`
- Move: `ui/pages/backup_restore/BackupRestoreScreen.kt` → `ui/feature/settings/BackupRestoreScreen.kt`
- Move: `ui/pages/plugin_center/PluginCenterScreen.kt` → `ui/feature/settings/PluginCenterScreen.kt`
- Move: `ui/pages/data_import/DataImportScreen.kt` → `ui/feature/settings/DataImportScreen.kt`
- Move: `ui/pages/weekly_summary/WeeklySummaryScreen.kt` → `ui/feature/settings/WeeklySummaryScreen.kt`

- [ ] **Step 1: Move all files**

```bash
mv ui/pages/settings/SettingsScreen.kt ui/feature/settings/
mv ui/pages/backup_settings/BackupSettingsScreen.kt ui/feature/settings/
mv ui/pages/backup_restore/BackupRestoreScreen.kt ui/feature/settings/
mv ui/pages/plugin_center/PluginCenterScreen.kt ui/feature/settings/
mv ui/pages/data_import/DataImportScreen.kt ui/feature/settings/
mv ui/pages/weekly_summary/WeeklySummaryScreen.kt ui/feature/settings/
rmdir ui/pages/settings ui/pages/backup_settings ui/pages/backup_restore ui/pages/plugin_center ui/pages/data_import ui/pages/weekly_summary
```

- [ ] **Step 2: Update all packages to `com.dailysatori.ui.feature.settings`**

Update all 6 files' package declarations. Update imports to use new component paths.

SettingsScreen.kt: Replace inline `TopAppBar` with `AppScaffold`. Update internal page navigation to use new import paths for `AiConfigScreen`, `PluginCenterScreen`, etc.

DataImportScreen.kt: Update `import com.dailysatori.service.import.ImportService` → `import com.dailysatori.service.import.ImportService` (stays same since it's in shared module).

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: migrate settings pages to feature/settings"
```

#### Task 6h: Share dialog page

**Files:**
- Move + rewrite: `ui/pages/share_dialog/ShareDialogScreen.kt` → `ui/feature/share/ShareDialogScreen.kt`

- [ ] **Step 1: Move and rewrite**

```bash
mv ui/pages/share_dialog/ShareDialogScreen.kt ui/feature/share/
rmdir ui/pages/share_dialog
rmdir ui/pages
```

Update package and replace `SAppBar` with `AppScaffold`.

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "refactor: migrate share dialog to feature/share"
```

---

### Task 7: 更新 NavHost 引用

**Files:**
- Modify: `core/navigation/NavHost.kt`

- [ ] **Step 1: Update all imports in NavHost.kt**

Replace all old page imports with new feature paths:

```kotlin
import com.dailysatori.ui.feature.home.HomeScreen
import com.dailysatori.ui.feature.article.ArticleListScreen
import com.dailysatori.ui.feature.article.ArticleDetailScreen
import com.dailysatori.ui.feature.diary.DiaryScreen
import com.dailysatori.ui.feature.book.BooksScreen
import com.dailysatori.ui.feature.book.BookSearchScreen
import com.dailysatori.ui.feature.aichat.AiChatScreen
import com.dailysatori.ui.feature.aiconfig.AiConfigScreen
import com.dailysatori.ui.feature.aiconfig.AiConfigEditScreen
import com.dailysatori.ui.feature.settings.SettingsScreen
import com.dailysatori.ui.feature.share.ShareDialogScreen
import com.dailysatori.ui.feature.settings.WeeklySummaryScreen
import com.dailysatori.ui.feature.settings.BackupRestoreScreen
import com.dailysatori.ui.feature.settings.BackupSettingsScreen
import com.dailysatori.ui.feature.settings.PluginCenterScreen
import com.dailysatori.ui.feature.settings.DataImportScreen
```

Also update composable references: `ArticlesScreen(` → `ArticleListScreen(`.

- [ ] **Step 2: Update DailySatoriApp.kt**

Update `App.kt` (renamed from `DailySatoriApp.kt`):
```kotlin
import com.dailysatori.core.navigation.DailySatoriNavHost
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: update NavHost to use new feature imports"
```

---

## Phase 3: 清理和验证

### Task 8: shared/ 模块微调 — 提取 DTO

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/model/ChatMessage.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/model/BookSearchResult.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/model/BackupEntry.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/model/ImportResult.kt`
- Modify: original service files to use extracted models

- [ ] **Step 1: Create model directory and extract DTOs**

```bash
mkdir -p shared/src/commonMain/kotlin/com/dailysatori/data/model
```

For each DTO class currently embedded in service files, create a dedicated file and update the original service to import from the new location. This is a lower-priority cleanup — can be done after verifying the app compiles.

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "refactor: extract DTOs to shared data/model package"
```

### Task 9: 编译验证

- [ ] **Step 1: Run assembleDebug**

```bash
cd /home/jimxl/Documents/projects/Daily
./gradlew assembleDebug 2>&1
```

Fix any compilation errors until it succeeds. Most likely issues:
- Missing imports after file moves
- Package declaration mismatches
- Import path changes not propagated

- [ ] **Step 2: Run lint**

```bash
./gradlew lint 2>&1
```

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "fix: resolve compilation errors after restructure"
```
