# 项目代码全面优化 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 优化整个项目：拆分长文件/长函数、抽象公共组件、添加导航动画、配置性能优化

**Architecture:** 4 个独立工作流按顺序执行：C(导航动画) → D(性能配置) → B(组件抽象) → A(结构优化)。每个工作流内部独立，可并行执行子任务。

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose, Material 3, Koin, Navigation Compose

---

### Task 1: 导航动画 — 在 NavHost 中添加共享轴过渡

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt`

- [ ] **Step 1: 添加共享轴过渡动画**

```kotlin
package com.dailysatori.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.dailysatori.ui.feature.aiconfig.AiConfigEditScreen
import com.dailysatori.ui.feature.aiconfig.AiConfigScreen
import com.dailysatori.ui.feature.article.ArticleDetailScreen
import com.dailysatori.ui.feature.book.BookSearchScreen
import com.dailysatori.ui.feature.home.HomeScreen
import com.dailysatori.ui.feature.settings.SettingsScreen
import com.dailysatori.ui.feature.share.ShareDialogScreen

private const val ANIM_DURATION = 350

private fun slideInStart() = androidx.compose.animation.slideInHorizontally(
    animationSpec = tween(ANIM_DURATION),
    initialOffsetX = { it },
) + fadeIn(animationSpec = tween(ANIM_DURATION))

private fun slideOutEnd() = androidx.compose.animation.slideOutHorizontally(
    animationSpec = tween(ANIM_DURATION),
    targetOffsetX = { it },
) + fadeOut(animationSpec = tween(ANIM_DURATION))

private fun slideInEnd() = androidx.compose.animation.slideInHorizontally(
    animationSpec = tween(ANIM_DURATION),
    initialOffsetX = { -it },
) + fadeIn(animationSpec = tween(ANIM_DURATION))

private fun slideOutStart() = androidx.compose.animation.slideOutHorizontally(
    animationSpec = tween(ANIM_DURATION),
    targetOffsetX = { -it },
) + fadeOut(animationSpec = tween(ANIM_DURATION))

@Composable
fun DailySatoriNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = HomeRoute) {
        composable<HomeRoute>(
            enterTransition = { fadeIn(tween(ANIM_DURATION)) },
            exitTransition = { slideOutStart() },
        ) {
            HomeScreen(
                onArticleClick = { id -> navController.navigate(ArticleDetailRoute(id)) },
                onBookSearchClick = { navController.navigate(BookSearchRoute) },
            )
        }

        composable<ArticleDetailRoute>(
            enterTransition = { slideInStart() },
            exitTransition = { slideOutEnd() },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<ArticleDetailRoute>()
            ArticleDetailScreen(
                articleId = route.articleId,
                onBack = { navController.popBackStack() },
            )
        }

        composable<BookSearchRoute>(
            enterTransition = { slideInStart() },
            exitTransition = { slideOutEnd() },
        ) {
            BookSearchScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable<AiConfigRoute>(
            enterTransition = { slideInStart() },
            exitTransition = { slideOutEnd() },
        ) {
            AiConfigScreen(
                onBack = { navController.popBackStack() },
                onEditConfig = { id -> navController.navigate(AiConfigEditRoute(configId = id)) },
            )
        }

        composable<AiConfigEditRoute>(
            enterTransition = { slideInStart() },
            exitTransition = { slideOutEnd() },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<AiConfigEditRoute>()
            AiConfigEditScreen(
                configId = route.configId,
                onBack = { navController.popBackStack() },
            )
        }

        composable<SettingsRoute>(
            enterTransition = { slideInStart() },
            exitTransition = { slideOutEnd() },
        ) {
            SettingsScreen()
        }

        composable<ShareDialogRoute>(
            enterTransition = { slideInStart() },
            exitTransition = { slideOutEnd() },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<ShareDialogRoute>()
            ShareDialogScreen(
                url = route.url,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin
```

---

### Task 2: 性能配置 — ProGuard/R8 + Compose 优化

**Files:**
- Create: `app/proguard-rules.pro`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 创建 ProGuard 规则文件**

```proguard
# Koin DI
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# SQLDelight
-keep class com.dailysatori.shared.db.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.dailysatori.**$$serializer { *; }
-keepclassmembers class com.dailysatori.** {
    *** Companion;
}
-keepclasseswithmembers class com.dailysatori.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Navigation routes (Serializable)
-keep class com.dailysatori.core.navigation.** { *; }

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# General
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
```

- [ ] **Step 2: 在 build.gradle.kts 中添加 release 配置**

在 `app/build.gradle.kts` 的 `android { }` 块内，`packaging { }` 块之后添加：

```kotlin
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
```

- [ ] **Step 3: 编译验证**

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin
```

---

### Task 3: 公共组件 — SettingsRow

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/component/settings/SettingsRow.kt`

- [ ] **Step 1: 创建 SettingsRow 组件**

```kotlin
package com.dailysatori.ui.component.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.dailysatori.ui.component.misc.FeatureIcon
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Spacing

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.m, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeatureIcon(icon = icon, containerSize = IconSize.xl, iconSize = IconSize.s)
            Spacer(modifier = Modifier.width(Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(Spacing.s))
                trailing()
            }
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin
```

---

### Task 4: 公共组件 — DetailTopBar

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/component/appbar/DetailTopBar.kt`

- [ ] **Step 1: 创建 DetailTopBar 组件**

```kotlin
package com.dailysatori.ui.component.appbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
```

- [ ] **Step 2: 编译验证**

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin
```

---

### Task 5: 迁移 SettingsScreen 使用 SettingsRow

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt`

- [ ] **Step 1: 替换 SettingItem 为 SettingsRow**

修改 `SettingsScreen.kt`，将 `import com.dailysatori.ui.component.misc.FeatureIcon` 替换为 `import com.dailysatori.ui.component.settings.SettingsRow`，删除 `private fun SettingItem(...)` 函数（第 198-227 行），将所有 `SettingItem(` 调用改为 `SettingsRow(`。

```kotlin
package com.dailysatori.ui.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.component.settings.SettingsRow
import com.dailysatori.ui.feature.aiconfig.AiConfigScreen
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

private enum class SettingsPage {
    MAIN,
    AI_CONFIG,
    MCP_SERVER,
    PLUGIN_CENTER,
    BACKUP_SETTINGS,
    BACKUP_RESTORE,
    DATA_IMPORT,
}

@Composable
fun SettingsScreen() {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }
    var showAboutDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = currentPage != SettingsPage.MAIN) {
        currentPage = SettingsPage.MAIN
    }

    when (currentPage) {
        SettingsPage.MAIN -> {
            if (showAboutDialog) {
                AlertDialog(
                    onDismissRequest = { showAboutDialog = false },
                    title = { Text("Daily Satori") },
                    text = { Text("v${state.currentVersion}\n个人知识管理与 AI 阅读助手\n基于 KMP + Compose Multiplatform") },
                    confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("确定") } },
                )
            }
            Column(modifier = Modifier.fillMaxSize()) {
                AppTopBar(
                    title = "设置",
                    showBack = false,
                    actions = {
                        IconButton(onClick = { showAboutDialog = true }) {
                            Icon(Icons.Default.Info, contentDescription = "关于")
                        }
                    },
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.m)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Text("AI 与服务", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Card(
                        shape = RoundedCornerShape(Radius.m),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        SettingsRow(
                            icon = Icons.Default.Star,
                            title = "AI 配置",
                            subtitle = "管理模型服务商与 API 密钥",
                            onClick = { currentPage = SettingsPage.AI_CONFIG },
                        )
                        SettingsRow(
                            icon = Icons.Default.Hub,
                            title = "MCP 服务",
                            subtitle = "管理外部工具服务连接",
                            onClick = { currentPage = SettingsPage.MCP_SERVER },
                        )
                        SettingsRow(
                            icon = Icons.Default.Settings,
                            title = "插件中心",
                            subtitle = "管理 AI 提示词插件",
                            onClick = { currentPage = SettingsPage.PLUGIN_CENTER },
                        )
                    }

                    Text("网络与同步", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Card(
                        shape = RoundedCornerShape(Radius.m),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        SettingsRow(
                            icon = Icons.Default.Language,
                            title = "Web 服务",
                            subtitle = if (state.webServerRunning) "运行中" else "已停止",
                            trailing = {
                                Switch(
                                    checked = state.webServerRunning,
                                    onCheckedChange = { viewModel.toggleWebServer() },
                                )
                            },
                            onClick = { viewModel.toggleWebServer() },
                        )
                        SettingsRow(
                            icon = Icons.Default.Refresh,
                            title = "检查更新",
                            subtitle = if (state.isCheckingUpdate) "检查中..." else "当前 v${state.currentVersion}",
                            onClick = { viewModel.checkUpdate() },
                        )
                    }

                    Text("数据管理", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Card(
                        shape = RoundedCornerShape(Radius.m),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        SettingsRow(
                            icon = Icons.Default.Save,
                            title = "备份与恢复",
                            subtitle = "管理数据备份与还原",
                            onClick = { currentPage = SettingsPage.BACKUP_SETTINGS },
                        )
                        SettingsRow(
                            icon = Icons.Default.FileDownload,
                            title = "导入数据",
                            subtitle = "从 Flutter 版本迁移数据",
                            onClick = { currentPage = SettingsPage.DATA_IMPORT },
                        )
                        SettingsRow(
                            icon = Icons.Default.CloudDownload,
                            title = "下载图片",
                            subtitle = "下载文章图片到本地",
                            onClick = {},
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.xl))
                }
            }
        }
        SettingsPage.AI_CONFIG -> AiConfigScreen(onBack = { currentPage = SettingsPage.MAIN })
        SettingsPage.MCP_SERVER -> McpServerScreen(onBack = { currentPage = SettingsPage.MAIN })
        SettingsPage.PLUGIN_CENTER -> PluginCenterScreen(onBack = { currentPage = SettingsPage.MAIN })
        SettingsPage.BACKUP_SETTINGS -> BackupSettingsScreen(onBack = { currentPage = SettingsPage.MAIN }, onRestore = { currentPage = SettingsPage.BACKUP_RESTORE })
        SettingsPage.BACKUP_RESTORE -> BackupRestoreScreen(onBack = { currentPage = SettingsPage.BACKUP_SETTINGS })
        SettingsPage.DATA_IMPORT -> DataImportScreen(onBack = { currentPage = SettingsPage.MAIN })
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin
```

---

### Task 6: 拆分 McpAgentService — 提取工具定义

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolRegistry.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt`

- [ ] **Step 1: 创建 McpToolRegistry.kt**

```kotlin
package com.dailysatori.service.mcp

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.MemoryRepository
import com.dailysatori.shared.db.Diary
import com.dailysatori.shared.db.Book
import com.dailysatori.shared.db.Article
import kotlinx.serialization.json.*

class McpToolRegistry(
    private val diaryRepo: DiaryRepository,
    private val articleRepo: ArticleRepository,
    private val bookRepo: BookRepository,
    private val viewpointRepo: BookViewpointRepository,
    private val memoryRepo: MemoryRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun buildToolDefinitions(): List<JsonObject> = listOf(
        buildTool("get_latest_diary", "获取最新的日记条目", mapOf(
            "limit" to buildParam("integer", "返回的日记数量，默认为5，最大为20"),
        )),
        buildTool("get_diary_by_date", "获取指定日期的日记", mapOf(
            "date" to buildParam("string", "日期，格式为YYYY-MM-DD或相对日期如today,yesterday"),
        ), listOf("date")),
        buildTool("search_diary_by_content", "按内容关键词搜索日记", mapOf(
            "keyword" to buildParam("string", "搜索关键词，多个关键词用逗号分隔"),
            "limit" to buildParam("integer", "返回的最大数量，默认为20"),
        ), listOf("keyword")),
        buildTool("get_diary_by_tag", "获取指定标签的日记", mapOf(
            "tag" to buildParam("string", "标签名称"),
            "limit" to buildParam("integer", "返回的最大数量，默认为10"),
        ), listOf("tag")),
        buildTool("get_diary_count", "获取日记的总数量"),
        buildTool("get_latest_articles", "获取最新收藏的文章", mapOf(
            "limit" to buildParam("integer", "返回的文章数量，默认为5"),
        )),
        buildTool("search_articles", "按关键词搜索文章", mapOf(
            "keyword" to buildParam("string", "搜索关键词，多个关键词用逗号分隔"),
            "limit" to buildParam("integer", "返回的最大数量，默认为20"),
        ), listOf("keyword")),
        buildTool("get_favorite_articles", "获取标记为喜爱的文章", mapOf(
            "limit" to buildParam("integer", "返回的最大数量，默认为10"),
        )),
        buildTool("get_article_count", "获取文章的总数量"),
        buildTool("get_latest_books", "获取最新添加的书籍", mapOf(
            "limit" to buildParam("integer", "返回的书籍数量，默认为5"),
        )),
        buildTool("search_books", "按标题、作者或分类搜索书籍", mapOf(
            "keyword" to buildParam("string", "搜索关键词，多个关键词用逗号分隔"),
            "limit" to buildParam("integer", "返回的最大数量，默认为15"),
        ), listOf("keyword")),
        buildTool("search_book_notes", "搜索读书笔记内容", mapOf(
            "keyword" to buildParam("string", "搜索关键词，多个关键词用逗号分隔"),
            "limit" to buildParam("integer", "返回的最大数量，默认为20"),
        ), listOf("keyword")),
        buildTool("get_book_viewpoints", "获取书籍的读书笔记/观点", mapOf(
            "book_id" to buildParam("integer", "书籍ID"),
        ), listOf("book_id")),
        buildTool("get_book_count", "获取书籍的总数量"),
        buildTool("get_statistics", "获取应用的综合统计信息"),
        buildTool("search_memory", "搜索记忆库中的内容。记忆分为三种类型：core(核心偏好/事实)、content(从日记/文章/书中提取的摘要)、chat(对话中提取的关键信息)", mapOf(
            "query" to buildParam("string", "搜索关键词"),
            "type" to buildParam("string", "记忆类型过滤: core, content, chat，不传则搜索全部"),
            "limit" to buildParam("integer", "返回的最大数量，默认为10"),
        ), listOf("query")),
        buildTool("get_memory_source", "获取指定来源的记忆内容", mapOf(
            "source_type" to buildParam("string", "来源类型: article, diary, book, book_viewpoint, chat"),
            "source_id" to buildParam("integer", "来源ID"),
        ), listOf("source_type", "source_id")),
    )

    fun executeTool(toolName: String, argumentsStr: String): McpToolResult {
        val args = try {
            json.parseToJsonElement(argumentsStr).jsonObject
        } catch (_: Exception) {
            JsonObject(emptyMap())
        }

        return try {
            when (toolName) {
                "get_latest_diary" -> getLatestDiary(args)
                "get_diary_by_date" -> getDiaryByDate(args)
                "search_diary_by_content" -> searchDiary(args)
                "get_diary_by_tag" -> getDiaryByTag(args)
                "get_diary_count" -> getDiaryCount()
                "get_latest_articles" -> getLatestArticles(args)
                "search_articles" -> searchArticles(args)
                "get_favorite_articles" -> getFavoriteArticles(args)
                "get_article_count" -> getArticleCount()
                "get_latest_books" -> getLatestBooks(args)
                "search_books" -> searchBooks(args)
                "search_book_notes" -> searchBookNotes(args)
                "get_book_viewpoints" -> getBookViewpoints(args)
                "get_book_count" -> getBookCount()
                "get_statistics" -> getStatistics()
                "search_memory" -> searchMemory(args)
                "get_memory_source" -> getMemorySource(args)
                else -> errorResult("未知工具: $toolName")
            }
        } catch (e: Exception) {
            errorResult("工具执行失败: ${e.message}")
        }
    }

    private fun buildTool(
        name: String,
        description: String,
        properties: Map<String, JsonObject> = emptyMap(),
        required: List<String> = emptyList(),
    ): JsonObject = buildJsonObject {
        put("type", "function")
        put("function", buildJsonObject {
            put("name", name)
            put("description", description)
            put("parameters", buildJsonObject {
                put("type", "object")
                put("properties", JsonObject(properties))
                put("required", JsonArray(required.map { JsonPrimitive(it) }))
            })
        })
    }

    private fun buildParam(type: String, description: String): JsonObject = buildJsonObject {
        put("type", type)
        put("description", description)
    }

    // --- Tool implementations ---

    private fun getLatestDiary(args: JsonObject): McpToolResult {
        val limit = intParam(args, "limit", 5)
        val diaries = diaryRepo.getLatestSync(limit)
        return successResult("diaries" to diaryListToJson(diaries))
    }

    private fun getDiaryByDate(args: JsonObject): McpToolResult {
        val dateStr = stringParam(args, "date") ?: return errorResult("缺少参数: date")
        val date = parseDate(dateStr) ?: return errorResult("无效日期格式，请使用 YYYY-MM-DD")
        val startMs = date.toEpochMilliseconds()
        val endMs = startMs + 86400000
        val diaries = diaryRepo.getByDateRangeSync(startMs, endMs)
        return successResult(
            "date" to JsonPrimitive(dateStr),
            "diaries" to diaryListToJson(diaries),
        )
    }

    private fun searchDiary(args: JsonObject): McpToolResult {
        val keyword = stringParam(args, "keyword") ?: return errorResult("缺少参数: keyword")
        val limit = intParam(args, "limit", 20)
        val results = searchWithKeywords(keyword) { kw -> diaryRepo.searchSync(kw) }
        return successResult(
            "keyword" to JsonPrimitive(keyword),
            "diaries" to diaryListToJson(results.take(limit)),
        )
    }

    private fun getDiaryByTag(args: JsonObject): McpToolResult {
        val tag = stringParam(args, "tag") ?: return errorResult("缺少参数: tag")
        val limit = intParam(args, "limit", 10)
        val allDiaries = diaryRepo.getAllSync()
        val filtered = allDiaries.filter { diary ->
            diary.tags?.split(",")?.map { it.trim() }?.any { it.equals(tag, ignoreCase = true) } == true
        }
        return successResult(
            "tag" to JsonPrimitive(tag),
            "diaries" to diaryListToJson(filtered.take(limit)),
        )
    }

    private fun getDiaryCount(): McpToolResult =
        successResult("count" to JsonPrimitive(diaryRepo.count()))

    private fun getLatestArticles(args: JsonObject): McpToolResult {
        val limit = intParam(args, "limit", 5)
        val articles = articleRepo.getLatestSync(limit)
        return successResult("articles" to articleListToJson(articles))
    }

    private fun searchArticles(args: JsonObject): McpToolResult {
        val keyword = stringParam(args, "keyword") ?: return errorResult("缺少参数: keyword")
        val limit = intParam(args, "limit", 20)
        val results = searchWithKeywords(keyword) { kw -> articleRepo.searchSync(kw) }
        return successResult(
            "keyword" to JsonPrimitive(keyword),
            "articles" to articleListToJson(results.take(limit)),
        )
    }

    private fun getFavoriteArticles(args: JsonObject): McpToolResult {
        val limit = intParam(args, "limit", 10)
        val articles = articleRepo.getFavoritesSync()
        return successResult("articles" to articleListToJson(articles.take(limit)))
    }

    private fun getArticleCount(): McpToolResult =
        successResult("count" to JsonPrimitive(articleRepo.count()))

    private fun getLatestBooks(args: JsonObject): McpToolResult {
        val limit = intParam(args, "limit", 5)
        val books = bookRepo.getAllSync()
        return successResult("books" to bookListToJson(books.take(limit)))
    }

    private fun searchBooks(args: JsonObject): McpToolResult {
        val keyword = stringParam(args, "keyword") ?: return errorResult("缺少参数: keyword")
        val limit = intParam(args, "limit", 15)
        val results = searchWithKeywords(keyword) { kw -> bookRepo.searchSync(kw) }
        return successResult(
            "keyword" to JsonPrimitive(keyword),
            "books" to bookListToJson(results.take(limit)),
        )
    }

    private fun searchBookNotes(args: JsonObject): McpToolResult {
        val keyword = stringParam(args, "keyword") ?: return errorResult("缺少参数: keyword")
        val limit = intParam(args, "limit", 20)
        val results = searchWithKeywords(keyword) { kw -> viewpointRepo.searchByContentSync(kw) }
        val booksMap = bookRepo.getAllSync().associateBy { it.id }
        val notesJson = JsonArray(results.take(limit).map { vp ->
            val book = booksMap[vp.book_id]
            buildJsonObject {
                put("id", vp.id)
                put("title", vp.title)
                put("content", truncate(vp.content, 500))
                put("bookId", vp.book_id)
                put("bookTitle", book?.title ?: "未知书籍")
                put("bookAuthor", book?.author ?: "")
            }
        })
        return successResult("keyword" to JsonPrimitive(keyword), "notes" to notesJson)
    }

    private fun getBookViewpoints(args: JsonObject): McpToolResult {
        val bookId = longParam(args, "book_id") ?: return errorResult("缺少参数: book_id")
        val book = bookRepo.getById(bookId) ?: return errorResult("未找到书籍: $bookId")
        val viewpoints = viewpointRepo.getByBookSync(bookId)
        return successResult(
            "book" to buildJsonObject {
                put("id", book.id)
                put("title", book.title)
                put("author", book.author)
            },
            "viewpoints" to JsonArray(viewpoints.map { vp ->
                buildJsonObject {
                    put("id", vp.id)
                    put("title", vp.title)
                    put("content", truncate(vp.content, 500))
                }
            }),
        )
    }

    private fun getBookCount(): McpToolResult =
        successResult("count" to JsonPrimitive(bookRepo.count()))

    private fun getStatistics(): McpToolResult = successResult(
        "statistics" to buildJsonObject {
            put("articles", articleRepo.count())
            put("diaries", diaryRepo.count())
            put("books", bookRepo.count())
        },
    )

    private fun searchMemory(args: JsonObject): McpToolResult {
        val query = stringParam(args, "query") ?: return errorResult("缺少query参数")
        val type = stringParam(args, "type")
        val limit = intParam(args, "limit", 10)
        val results = if (type != null) {
            memoryRepo.search(query, limit.toLong()).filter { it.type == type }
        } else {
            memoryRepo.search(query, limit.toLong())
        }
        if (results.isEmpty()) {
            return successResult("message" to JsonPrimitive("未找到相关记忆"))
        }
        return successResult("results" to JsonArray(results.take(limit).map { entry ->
            buildJsonObject {
                put("id", entry.id)
                put("type", entry.type)
                put("source_type", entry.source_type ?: "")
                put("title", entry.title)
                put("content", entry.content.take(500))
                put("tags", entry.tags ?: "")
            }
        }))
    }

    private fun getMemorySource(args: JsonObject): McpToolResult {
        val sourceType = stringParam(args, "source_type") ?: return errorResult("缺少source_type参数")
        val sourceId = longParam(args, "source_id") ?: return errorResult("缺少source_id参数")
        val entry = memoryRepo.getBySource(sourceType, sourceId)
        if (entry == null) {
            return successResult("message" to JsonPrimitive("未找到相关记忆"))
        }
        return successResult("memory" to buildJsonObject {
            put("id", entry.id)
            put("type", entry.type)
            put("title", entry.title)
            put("content", entry.content)
            put("tags", entry.tags ?: "")
        })
    }

    // --- JSON helpers ---

    private fun successResult(vararg pairs: Pair<String, JsonElement>): McpToolResult {
        val obj = buildJsonObject {
            put("success", true)
            for ((key, value) in pairs) { put(key, value) }
            val countFields = setOf("diaries", "articles", "books", "viewpoints", "notes")
            for ((key, value) in pairs) {
                if (key in countFields && value is JsonArray) put("count", value.size)
            }
        }
        return McpToolResult(true, obj)
    }

    private fun errorResult(message: String): McpToolResult {
        val obj = buildJsonObject { put("success", false); put("error", message) }
        return McpToolResult(false, obj)
    }

    private fun intParam(args: JsonObject, key: String, default: Int): Int =
        args[key]?.jsonPrimitive?.intOrNull ?: default

    private fun stringParam(args: JsonObject, key: String): String? =
        args[key]?.jsonPrimitive?.contentOrNull

    private fun longParam(args: JsonObject, key: String): Long? =
        args[key]?.jsonPrimitive?.longOrNull

    private fun diaryListToJson(diaries: List<Diary>): JsonArray = JsonArray(diaries.map { diary ->
        buildJsonObject {
            put("id", diary.id)
            put("content", truncate(diary.content, 500))
            put("tags", diary.tags ?: "")
            put("mood", diary.mood ?: "")
            put("createdAt", formatDate(diary.created_at))
        }
    })

    private fun articleListToJson(articles: List<Article>): JsonArray = JsonArray(articles.map { article ->
        buildJsonObject {
            put("id", article.id)
            put("title", article.ai_title ?: article.title ?: "无标题")
            put("content", truncate(article.ai_content ?: article.content ?: "", 800))
            put("comment", article.comment ?: "")
            put("url", article.url ?: "")
            put("isFavorite", article.is_favorite ?: 0L)
            put("createdAt", formatDate(article.created_at))
        }
    })

    private fun bookListToJson(books: List<Book>): JsonArray = JsonArray(books.map { book ->
        buildJsonObject {
            put("id", book.id)
            put("title", book.title)
            put("author", book.author)
            put("category", book.category)
            put("createdAt", formatDate(book.created_at))
        }
    })

    private fun formatDate(timestampMs: Long): String {
        val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(timestampMs)
        return instant.toString().substring(0, 10)
    }

    private fun truncate(text: String, maxLen: Int): String =
        if (text.length <= maxLen) text else text.substring(0, maxLen) + "..."

    private fun parseDate(dateStr: String): kotlinx.datetime.Instant? {
        return try {
            when (dateStr.lowercase()) {
                "today" -> kotlinx.datetime.Clock.System.now()
                "yesterday" -> kotlinx.datetime.Instant.fromEpochMilliseconds(
                    kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - 86400000
                )
                "beforeyesterday", "before_yesterday" -> kotlinx.datetime.Instant.fromEpochMilliseconds(
                    kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - 172800000
                )
                else -> {
                    val dateStr10 = dateStr.substring(0, 10)
                    val parts = dateStr10.split("-")
                    if (parts.size == 3) kotlinx.datetime.Instant.parse("${dateStr10}T00:00:00Z")
                    else null
                }
            }
        } catch (_: Exception) { null }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> searchWithKeywords(keyword: String, searcher: (String) -> List<T>): List<T> {
        val keywords = keyword.split(Regex("[\\s,，]+"))
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && (containsChinese(it) || it.length >= 2) }
        if (keywords.isEmpty()) return emptyList()
        val resultMap = mutableMapOf<Int, T>()
        for (kw in keywords) {
            for (item in searcher(kw)) {
                val id = getItemId(item as Any)
                if (!resultMap.containsKey(id)) resultMap[id] = item
            }
        }
        return resultMap.values.toList().sortedByDescending { getItemTimestamp(it as Any) }
    }

    private fun containsChinese(text: String): Boolean =
        Regex("[\\u4e00-\\u9fa5]").containsMatchIn(text)

    private fun getItemId(item: Any): Int = when (item) {
        is Article -> item.id.toInt()
        is Diary -> item.id.toInt()
        is Book -> item.id.toInt()
        is com.dailysatori.shared.db.Book_viewpoint -> item.id.toInt()
        else -> item.hashCode()
    }

    private fun getItemTimestamp(item: Any): Long = when (item) {
        is Article -> item.created_at
        is Diary -> item.created_at
        is Book -> item.created_at
        is com.dailysatori.shared.db.Book_viewpoint -> item.created_at
        else -> 0L
    }
}
```

- [ ] **Step 2: 修改 McpAgentService.kt — 移除工具定义，委托给 McpToolRegistry**

删除原有 `McpAgentService.kt` 中的以下方法（第 288-397 行，第 537-582 行，第 728-879 行）：
- `buildToolDefinitions()`
- `buildTool()`
- `buildParam()`
- `executeTool()`
- 所有 `get*` 开头的工具执行方法（getLatestDiary ~ getMemorySource）
- `successResult()` 和 `errorResult()`
- `intParam()`, `stringParam()`, `longParam()`
- `diaryListToJson()`, `articleListToJson()`, `bookListToJson()`
- `formatDate()`, `truncate()`
- `parseDate()`, `searchWithKeywords()`, `containsChinese()`, `getItemId()`, `getItemTimestamp()`

添加 `McpToolRegistry` 依赖，修改构造函数和调用：

```kotlin
package com.dailysatori.service.mcp

import co.touchlab.kermit.Logger
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.book.BookSearchResult
import kotlinx.serialization.json.*

data class McpToolResult(val success: Boolean, val data: JsonObject? = null)
data class McpAgentResult(val answer: String, val searchResults: List<McpSearchResult>)
data class McpSearchResult(
    val id: Long,
    val type: String,
    val title: String,
    val summary: String?,
    val createdAt: String?,
    val tags: List<String>? = null,
    val isFavorite: Boolean? = null,
)

class McpAgentService(
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
    private val toolRegistry: McpToolRegistry,
) {
    private val log = Logger.withTag("MCPAgent")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        private const val MAX_TOOL_CALL_ROUNDS = 5
    }

    suspend fun processQuery(
        query: String,
        onStep: (String, String) -> Unit,
    ): McpAgentResult {
        val collectedResults = mutableListOf<McpSearchResult>()
        var currentStepName: String? = null

        fun updateStep(stepName: String, status: String) {
            if (currentStepName != null && currentStepName != stepName) {
                onStep(currentStepName!!, "completed")
            }
            currentStepName = stepName
            onStep(stepName, status)
        }

        fun completeStep() {
            if (currentStepName != null) onStep(currentStepName!!, "completed")
            onStep("完成", "completed")
        }

        return try {
            val config = aiConfigService.getDefaultConfig()
            if (config == null || config.api_address.isBlank() || config.api_token.isBlank()) {
                return McpAgentResult(
                    answer = buildErrorResponse("AI 服务未配置，请先在设置中配置 AI 接口"),
                    searchResults = emptyList(),
                )
            }

            updateStep("正在理解您的问题...", "processing")

            val messages = mutableListOf<JsonObject>()
            messages.add(buildJsonObject {
                put("role", "system")
                put("content", buildSystemPrompt())
            })

            messages.add(buildJsonObject {
                put("role", "user")
                put("content", query)
            })

            val tools = toolRegistry.buildToolDefinitions()
            val apiUrl = config.api_address.trimEnd('/')
            val apiToken = config.api_token
            val modelName = config.model_name
            val provider = config.provider
            var finalAnswer: String? = null

            for (round in 0 until MAX_TOOL_CALL_ROUNDS) {
                val response = aiService.chatCompletion(
                    messages = messages,
                    apiAddress = apiUrl,
                    apiToken = apiToken,
                    modelName = modelName,
                    provider = provider,
                    tools = tools,
                    temperature = 0.7,
                )

                if (response == null) {
                    return McpAgentResult(
                        answer = buildErrorResponse("AI 请求失败，请稍后重试"),
                        searchResults = collectedResults,
                    )
                }

                val choice = response["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                val message = choice?.get("message")?.jsonObject
                val toolCalls = message?.get("tool_calls")?.jsonArray

                if (message == null) {
                    completeStep()
                    break
                }

                if (toolCalls != null && toolCalls.isNotEmpty()) {
                    updateStep("正在查询数据...", "processing")

                    messages.add(buildJsonObject {
                        put("role", "assistant")
                        put("content", message["content"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("tool_calls", toolCalls)
                    })

                    for (toolCall in toolCalls) {
                        val tc = toolCall.jsonObject
                        val function = tc["function"]?.jsonObject
                        val toolName = function?.get("name")?.jsonPrimitive?.contentOrNull ?: continue
                        val arguments = function["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
                        val toolCallId = tc["id"]?.jsonPrimitive?.contentOrNull ?: ""

                        val toolResult = toolRegistry.executeTool(toolName, arguments)
                        collectedResults.addAll(extractSearchResults(toolName, toolResult))

                        val resultContent = toolResult.data?.toString() ?: buildJsonObject {
                            put("success", toolResult.success)
                            put("error", "unknown")
                        }.toString()

                        messages.add(buildJsonObject {
                            put("role", "tool")
                            put("tool_call_id", toolCallId)
                            put("content", resultContent)
                        })
                    }
                    updateStep("正在生成回答...", "processing")
                } else {
                    finalAnswer = message["content"]?.jsonPrimitive?.contentOrNull
                    completeStep()
                    break
                }
            }

            if (finalAnswer == null) {
                updateStep("正在整理答案...", "processing")
                val response = aiService.chatCompletion(
                    messages = messages,
                    apiAddress = apiUrl,
                    apiToken = apiToken,
                    modelName = modelName,
                    provider = provider,
                    temperature = 0.7,
                )
                finalAnswer = response?.let {
                    val choice = it["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                    val msg = choice?.get("message")?.jsonObject
                    msg?.get("content")?.jsonPrimitive?.contentOrNull
                }
                completeStep()
            }

            val filteredResults = filterRelevantResults(collectedResults, finalAnswer ?: "")
            val cleanAnswer = removeRefsTag(finalAnswer ?: buildErrorResponse("无法生成回答"))
            McpAgentResult(answer = cleanAnswer, searchResults = filteredResults)
        } catch (e: Exception) {
            log.e(e) { "MCP Agent processing failed" }
            if (currentStepName != null) onStep(currentStepName!!, "error")
            onStep("处理失败", "error")
            McpAgentResult(
                answer = buildErrorResponse("处理失败: ${e.message}"),
                searchResults = collectedResults,
            )
        }
    }

    // ... (keep buildSystemPrompt, extractSearchResults, filterRelevantResults, etc. — same as original lines 203-926 but remove tool-related methods listed above)

    private fun buildSystemPrompt(): String {
        val now = kotlinx.datetime.Clock.System.now()
        val today = kotlinx.datetime.Instant.fromEpochMilliseconds(
            now.toEpochMilliseconds()
        ).toString().substring(0, 10)
        val yesterday = kotlinx.datetime.Instant.fromEpochMilliseconds(
            now.toEpochMilliseconds() - 86400000
        ).toString().substring(0, 10)
        val beforeYesterday = kotlinx.datetime.Instant.fromEpochMilliseconds(
            now.toEpochMilliseconds() - 172800000
        ).toString().substring(0, 10)
        val currentTime = kotlinx.datetime.Instant.fromEpochMilliseconds(
            now.toEpochMilliseconds()
        ).toString().substring(0, 19)

        return """你是一个智能助手，专门帮助用户从他们的个人数据中查找和总结信息。用户的数据包括：
- **日记**: 用户的个人日记记录
- **文章**: 用户收藏的网页文章
- **书籍**: 用户添加的书籍和读书笔记
- **记忆**: 用户的记忆库，包含核心偏好、内容摘要和对话关键信息

## 核心规则

**你只能基于用户的个人数据来回答问题，不要使用你的通用知识来回答。**
同时优先在记忆库中搜索相关信息。记忆库包含你的核心偏好、所有内容的AI摘要和之前对话的关键信息。

当用户提问时，你必须：
1. **首先使用搜索工具**查找用户数据中的相关内容
2. **优先使用 search_memory 工具**在记忆库中搜索
3. **基于搜索结果**来生成回答
4. 如果没有找到相关内容，告知用户"在您的数据中没有找到相关信息"

**禁止行为**：
- 不要直接用你的知识回答问题
- 不要跳过搜索步骤直接给答案
- 不要编造用户数据中不存在的内容

## 工具使用指南

### 日记相关
- `get_latest_diary`: 获取最新的日记
- `get_diary_by_date`: 获取指定日期的日记，日期格式为 YYYY-MM-DD
- `search_diary_by_content`: 按关键词搜索日记内容
- `get_diary_by_tag`: 按标签获取日记
- `get_diary_count`: 获取日记总数

### 文章相关
- `get_latest_articles`: 获取最新收藏的文章
- `search_articles`: 按关键词搜索文章
- `get_favorite_articles`: 获取标记为喜爱的文章
- `get_article_count`: 获取文章总数

### 书籍相关
- `get_latest_books`: 获取最新添加的书籍
- `search_books`: 按书名、作者或分类搜索书籍
- `search_book_notes`: 按关键词搜索读书笔记
- `get_book_viewpoints`: 获取指定书籍的读书笔记
- `get_book_count`: 获取书籍总数

### 综合
- `get_statistics`: 获取应用数据统计

### 记忆相关
- `search_memory`: 搜索你的记忆库（包含核心偏好、内容摘要、对话记忆）。可用于查找你的偏好、过去的内容要点等
- `get_memory_source`: 获取指定来源的完整记忆内容，可按 source_type (article/diary/book/book_viewpoint/chat) 和 source_id 查询

## 日期处理规则
- "今天" → "$today"
- "昨天" → "$yesterday"
- "前天" → "$beforeYesterday"

## 回答格式要求
1. 用自然语言总结，不要返回原始 JSON
2. 重要信息用 **加粗**
3. 无结果时友好告知
4. 在回答末尾用特定格式标注引用来源：
```
<!-- refs: article_123, diary_456, book_789 -->
```
如果没有引用任何内容，标注 `<!-- refs: none -->`

当前时间: $currentTime
"""
    }

    private fun extractSearchResults(toolName: String, result: McpToolResult): List<McpSearchResult> {
        if (!result.success || result.data == null) return emptyList()
        val data = result.data

        fun jsonString(obj: JsonObject, key: String): String? =
            obj[key]?.jsonPrimitive?.contentOrNull

        fun jsonLong(obj: JsonObject, key: String): Long? =
            obj[key]?.jsonPrimitive?.longOrNull

        fun jsonBool(obj: JsonObject, key: String): Boolean? =
            obj[key]?.jsonPrimitive?.booleanOrNull

        return when {
            toolName.contains("diary") -> {
                val diaries = data["diaries"]?.jsonArray ?: return emptyList()
                diaries.mapNotNull { item ->
                    val d = item.jsonObject
                    val tags: List<String>? = when (val t = d["tags"]) {
                        is JsonPrimitive -> t.contentOrNull?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                        is JsonArray -> t.mapNotNull { it.jsonPrimitive.contentOrNull }
                        else -> null
                    }
                    McpSearchResult(
                        id = jsonLong(d, "id") ?: return@mapNotNull null,
                        type = "diary",
                        title = generateDiaryTitle(d),
                        summary = truncateNullable(jsonString(d, "content"), 100),
                        createdAt = jsonString(d, "createdAt"),
                        tags = tags,
                    )
                }
            }
            toolName.contains("article") -> {
                val articles = data["articles"]?.jsonArray ?: return emptyList()
                articles.mapNotNull { item ->
                    val a = item.jsonObject
                    McpSearchResult(
                        id = jsonLong(a, "id") ?: return@mapNotNull null,
                        type = "article",
                        title = jsonString(a, "title") ?: "未知标题",
                        summary = truncateNullable(jsonString(a, "content"), 100),
                        createdAt = jsonString(a, "createdAt"),
                        isFavorite = jsonBool(a, "isFavorite") ?: (jsonLong(a, "isFavorite") == 1L),
                    )
                }
            }
            toolName.contains("book") && !toolName.contains("note") -> {
                val books = data["books"]?.jsonArray ?: return emptyList()
                books.mapNotNull { item ->
                    val b = item.jsonObject
                    McpSearchResult(
                        id = jsonLong(b, "id") ?: return@mapNotNull null,
                        type = "book",
                        title = jsonString(b, "title") ?: "未知书名",
                        summary = jsonString(b, "author"),
                        createdAt = jsonString(b, "createdAt"),
                    )
                }
            }
            toolName.contains("book") && toolName.contains("note") -> {
                val notes = data["notes"]?.jsonArray ?: return emptyList()
                notes.mapNotNull { item ->
                    val n = item.jsonObject
                    McpSearchResult(
                        id = jsonLong(n, "id") ?: return@mapNotNull null,
                        type = "book",
                        title = jsonString(n, "bookTitle") ?: "未知书籍",
                        summary = truncateNullable(jsonString(n, "title"), 100),
                        createdAt = null,
                    )
                }
            }
            else -> emptyList()
        }
    }

    private fun filterRelevantResults(
        results: List<McpSearchResult>,
        answer: String,
    ): List<McpSearchResult> {
        if (results.isEmpty() || answer.isEmpty()) return results

        val refsMatch = Regex("<!--\\s*refs:\\s*([^>]+)\\s*-->").find(answer)
        if (refsMatch == null) return filterByTitleMatch(results, answer)

        val refsContent = refsMatch.groupValues[1].trim()
        if (refsContent.lowercase() == "none") return emptyList()
        if (refsContent.isEmpty()) return filterByTitleMatch(results, answer)

        val referencedIds = mutableMapOf<String, MutableSet<Long>>(
            "article" to mutableSetOf(),
            "diary" to mutableSetOf(),
            "book" to mutableSetOf(),
        )

        for (ref in refsContent.split(",").map { it.trim() }) {
            val match = Regex("(article|diary|book)_(\\d+)").find(ref) ?: continue
            val type = match.groupValues[1]
            val id = match.groupValues[2].toLongOrNull() ?: continue
            referencedIds[type]?.add(id)
        }

        if (referencedIds.values.sumOf { it.size } == 0) {
            return filterByTitleMatch(results, answer)
        }

        val filtered = results.filter { r ->
            when (r.type) {
                "article" -> referencedIds["article"]?.contains(r.id) == true
                "diary" -> referencedIds["diary"]?.contains(r.id) == true
                "book" -> referencedIds["book"]?.contains(r.id) == true
                else -> false
            }
        }

        return if (filtered.isEmpty() && results.isNotEmpty()) filterByTitleMatch(results, answer)
        else filtered
    }

    private fun filterByTitleMatch(results: List<McpSearchResult>, answer: String): List<McpSearchResult> {
        val answerLower = answer.lowercase()
        return results.filter { result ->
            val keywords = result.title
                .replace(Regex("[^\\w\\u4e00-\\u9fa5]"), " ")
                .split(Regex("\\s+"))
                .filter { it.length >= 2 }
            keywords.any { answerLower.contains(it.lowercase()) }
        }
    }

    private fun removeRefsTag(answer: String): String =
        answer.replace(Regex("\\n*<!--\\s*refs:[^>]*-->\\s*$"), "").trim()

    private fun buildErrorResponse(message: String): String =
        """😔 **出现问题**

$message

**建议**:
- 检查网络连接
- 确保 AI 服务配置正确
- 稍后重试"""

    private fun generateDiaryTitle(data: JsonObject): String {
        val createdAt = data["createdAt"]?.jsonPrimitive?.contentOrNull ?: return "日记"
        return try {
            val parts = createdAt.substring(0, 10).split("-")
            if (parts.size == 3) "${parts[0]}年${parts[1]}月${parts[2]}日的日记" else "日记"
        } catch (_: Exception) { "日记" }
    }

    private fun truncateNullable(text: String?, maxLen: Int): String? {
        if (text.isNullOrEmpty()) return null
        return if (text.length <= maxLen) text else text.substring(0, maxLen) + "..."
    }

    suspend fun searchBookOnline(query: String): List<BookSearchResult> {
        val config = aiConfigService.getDefaultConfig() ?: return emptyList()
        if (config.api_token.isBlank()) return emptyList()
        val systemPrompt = buildBookSearchPrompt(query)
        val response = aiService.complete(
            prompt = query,
            apiAddress = config.api_address,
            apiToken = config.api_token,
            modelName = config.model_name,
            provider = config.provider,
            systemPrompt = systemPrompt,
        )
        return parseBookSearchResults(response)
    }

    private fun buildBookSearchPrompt(query: String): String =
        """你是一个书籍搜索引擎。用户想了解关于"$query"的书籍信息。
请以 JSON 数组格式返回搜索结果，每个元素包含以下字段：
- title: 书名（字符串）
- author: 作者（字符串）
- introduction: 内容简介，200字以内（字符串）
- coverUrl: 封面图片URL，如果没有则为空字符串

只返回 JSON 数组，不要其他文字。示例格式：
[{"title":"书籍名称","author":"作者名","introduction":"内容简介...","coverUrl":""}]"""

    private fun parseBookSearchResults(response: String): List<BookSearchResult> {
        if (response.isBlank()) return emptyList()
        return try {
            val cleaned = response.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val array = json.parseToJsonElement(cleaned).jsonArray
            array.map { el ->
                val obj = el.jsonObject
                BookSearchResult(
                    title = obj["title"]?.jsonPrimitive?.content ?: "",
                    author = obj["author"]?.jsonPrimitive?.content ?: "",
                    introduction = obj["introduction"]?.jsonPrimitive?.content ?: "",
                    coverUrl = obj["coverUrl"]?.jsonPrimitive?.content ?: "",
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}
```

- [ ] **Step 3: 更新 DI 注册 (SharedModule.kt)**

在 `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt` 中添加 `McpToolRegistry` 注册，更新 `McpAgentService` 的注册：

需要在 `SharedModule.kt` 中找到 `single { McpAgentService(...) }` 并修改为：
```kotlin
single { McpToolRegistry(get(), get(), get(), get(), get()) }
single { McpAgentService(get(), get(), get()) }
```

- [ ] **Step 4: 编译验证**

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:compileKotlinAndroid
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin
```

---

### Task 7: 拆分 AiChatScreen — 提取 ChatInputBar 和 MessageBubble

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MemorySearchSheet.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt`

- [ ] **Step 1: 创建 ChatInputBar.kt**

```kotlin
package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.m, vertical = Spacing.s)
                .imePadding(),
        ) {
            Surface(
                shape = RoundedCornerShape(Radius.circular),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = Spacing.m, end = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "问我任何问题...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        maxLines = 6,
                        enabled = enabled,
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    FilledIconButton(
                        onClick = onSend,
                        enabled = inputText.isNotBlank() && enabled,
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = contentColorFor(MaterialTheme.colorScheme.primary),
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Text(
                "基于你的知识库和记忆回答",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = Spacing.xs, start = Spacing.s),
            )
        }
    }
}
```

- [ ] **Step 2: 创建 MessageBubble.kt**

```kotlin
package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.compose.Markdown

@Composable
fun MessageBubble(message: ChatMessageUi) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = Radius.m, topEnd = Radius.m,
                    bottomStart = if (isUser) Radius.m else Radius.xs,
                    bottomEnd = if (isUser) Radius.xs else Radius.m,
                ),
                color = when {
                    isUser -> MaterialTheme.colorScheme.primary
                    message.isError -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceContainer
                },
                modifier = Modifier.fillMaxWidth(0.85f),
            ) {
                if (isUser) {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(Spacing.m),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Markdown(
                        content = message.content,
                        colors = com.mikepenz.markdown.m3.markdownColor(),
                        typography = MarkdownStyles.cardTypography(),
                        padding = MarkdownStyles.cardPadding(),
                        modifier = Modifier.padding(
                            start = Spacing.m, end = Spacing.m,
                            top = Spacing.m, bottom = Spacing.s,
                        ),
                    )
                }
            }
        }

        if (!isUser && message.searchResults.isNotEmpty()) {
            SearchResultsSection(message.searchResults)
        }
    }
}

@Composable
private fun SearchResultsSection(results: List<com.dailysatori.service.mcp.McpSearchResult>) {
    Spacer(modifier = Modifier.height(Spacing.xxs))
    Surface(
        shape = RoundedCornerShape(Radius.s),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = Spacing.s),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs)) {
            results.take(3).forEach { result ->
                Text(
                    text = "\uD83D\uDCC4 ${result.type}: ${result.title}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 3: 创建 MemorySearchSheet.kt**

从 `AiChatScreen.kt` 中复制原始的 `MemorySearchSheet`、`MemoryEntryCard`、`MemoryTypeChip` composable（第 341-492 行），放入新文件：

```kotlin
package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.MemoryRepository
import com.dailysatori.service.memory.MemoryExtractService
import com.dailysatori.shared.db.Memory_entry
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorySearchSheet(onDismiss: () -> Unit) {
    val memoryRepo = koinInject<MemoryRepository>()
    val extractService = koinInject<MemoryExtractService>()
    val articleRepo = koinInject<ArticleRepository>()
    val diaryRepo = koinInject<DiaryRepository>()
    val bookRepo = koinInject<BookRepository>()
    val viewpointRepo = koinInject<BookViewpointRepository>()
    var searchQuery by remember { mutableStateOf("") }
    var memories by remember { mutableStateOf<List<Memory_entry>>(emptyList()) }
    var isRebuilding by remember { mutableStateOf(false) }
    var rebuildProgress by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(searchQuery) {
        withContext(Dispatchers.IO) {
            memories = if (searchQuery.isBlank()) {
                memoryRepo.getAllSync()
            } else {
                memoryRepo.search(searchQuery, 50)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.m)) {
            Text("记忆搜索", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(Spacing.s))

            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索记忆...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(Radius.m),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )

            if (isRebuilding && rebuildProgress.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.s))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(rebuildProgress, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(Spacing.s))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${memories.size} 条记忆",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        isRebuilding = true
                        scope.launch(Dispatchers.IO) {
                            extractService.rebuildAll(
                                articleRepo, diaryRepo, bookRepo, viewpointRepo,
                                onProgress = { rebuildProgress = it },
                            )
                            memories = memoryRepo.getAllSync()
                            isRebuilding = false
                        }
                    },
                    enabled = !isRebuilding,
                ) {
                    Text("重建全部记忆")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(400.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(vertical = Spacing.s),
            ) {
                items(memories, key = { it.id }) { memory ->
                    MemoryEntryCard(memory)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.m))
        }
    }
}

@Composable
private fun MemoryEntryCard(memory: Memory_entry) {
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemoryTypeChip(memory.type)
                Spacer(modifier = Modifier.width(Spacing.s))
                Text(
                    memory.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                memory.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MemoryTypeChip(type: String) {
    val (label, color) = when (type) {
        "core" -> "核心" to MaterialTheme.colorScheme.primary
        "content" -> "内容" to MaterialTheme.colorScheme.secondary
        "chat" -> "对话" to MaterialTheme.colorScheme.tertiary
        else -> type to MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = RoundedCornerShape(Radius.xs),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
```

- [ ] **Step 4: 精简 AiChatScreen.kt**

删除 `ChatInputBar` (第 191-267), `MessageBubble` (第 269-337), `MemorySearchSheet` (第 341-441), `MemoryEntryCard` (第 444-472), `MemoryTypeChip` (第 475-493) — 这些 composable。移除对应 imports，添加新文件的 imports。

最终 `AiChatScreen.kt` 约 100 行。

- [ ] **Step 5: 编译验证**

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin
```

---

### Task 8: 拆分 DiaryEditorSheet — 提取图片管理组件

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryImageManager.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryFormatToolbar.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt`

- [ ] **Step 1: 创建 DiaryImageManager.kt**

从 `DiaryEditorSheet.kt` 提取图片保存逻辑和媒体选择器：

```kotlin
package com.dailysatori.ui.feature.diary

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import java.io.File
import java.util.UUID

@Composable
fun rememberDiaryImageState(existingImages: List<String>): DiaryImageState {
    val context = LocalContext.current
    val images = remember(existingImages) { mutableStateListOf<String>().apply { addAll(existingImages) } }

    val diaryImagesDir = remember { File(context.filesDir, "DailySatori/diary_images").apply { mkdirs() } }

    fun saveMedia(uri: Uri, ext: String) {
        try {
            val mimeType = context.contentResolver.getType(uri)
            val actualExt = when {
                mimeType?.startsWith("video/") == true -> ".mp4"
                mimeType?.startsWith("image/") == true -> ".${ext.ifBlank { "jpg" }}"
                ext.isNotBlank() -> ext
                else -> ".jpg"
            }
            val fileName = "diary_${UUID.randomUUID()}$actualExt"
            val destFile = File(diaryImagesDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            images.add("diary_images/$fileName")
        } catch (_: Exception) { }
    }

    fun removeImage(path: String) { images.remove(path) }

    val tempPhotoUri = remember {
        val file = File(diaryImagesDir, "temp_photo_${UUID.randomUUID()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val tempVideoUri = remember {
        val file = File(diaryImagesDir, "temp_video_${UUID.randomUUID()}.mp4")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) saveMedia(tempPhotoUri, ".jpg")
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success) saveMedia(tempVideoUri, ".mp4")
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri -> saveMedia(uri, "") }
    }

    return DiaryImageState(images, cameraLauncher to tempPhotoUri, videoLauncher to tempVideoUri, galleryLauncher)
}

class DiaryImageState(
    val images: MutableList<String>,
    val camera: Pair<androidx.activity.result.ActivityResultLauncher<Uri>, Uri>,
    val video: Pair<androidx.activity.result.ActivityResultLauncher<Uri>, Uri>,
    val gallery: androidx.activity.result.ActivityResultLauncher<String>,
)

@Composable
fun DiaryMediaPickerDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    imageState: DiaryImageState,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加媒体") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                TextButton(
                    onClick = { onDismiss(); imageState.camera.first.launch(imageState.camera.second) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("拍照", modifier = Modifier.weight(1f)) }
                TextButton(
                    onClick = { onDismiss(); imageState.video.first.launch(imageState.video.second) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("录像", modifier = Modifier.weight(1f)) }
                TextButton(
                    onClick = { onDismiss(); imageState.gallery.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("从相册选择", modifier = Modifier.weight(1f)) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun DiaryImageRow(
    images: List<String>,
    onRemove: (String) -> Unit,
) {
    if (images.isEmpty()) return
    val context = LocalContext.current
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
                    Icon(Icons.Default.Clear, "删除", Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
```

- [ ] **Step 2: 创建 DiaryFormatToolbar.kt**

```kotlin
package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dailysatori.ui.theme.Spacing

@Composable
fun DiaryFormatToolbar(
    onTitle: () -> Unit,
    onBold: () -> Unit,
    onOrderedList: () -> Unit,
    onUnorderedList: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMedia: () -> Unit,
    onTag: () -> Unit,
    onSave: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    canSave: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            FormatIconButton(Icons.Default.Title, "标题", onTitle)
            FormatIconButton(Icons.Default.FormatBold, "加粗", onBold)
            FormatIconButton(Icons.Default.FormatListNumbered, "有序列表", onOrderedList)
            FormatIconButton(Icons.AutoMirrored.Filled.FormatListBulleted, "无序列表", onUnorderedList)
            FormatIconButton(Icons.AutoMirrored.Filled.Undo, "撤销", onUndo, enabled = canUndo)
            FormatIconButton(Icons.AutoMirrored.Filled.Redo, "重做", onRedo, enabled = canRedo)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            FormatIconButton(Icons.Default.AddPhotoAlternate, "添加媒体", onMedia)
            FormatIconButton(Icons.Default.Tag, "添加标签", onTag)
            IconButton(
                onClick = onSave,
                modifier = Modifier.size(36.dp),
                enabled = canSave,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "保存",
                    modifier = Modifier.size(24.dp),
                    tint = if (canSave) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun FormatIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp), enabled = enabled) {
        Icon(icon, desc, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

- [ ] **Step 3: 精简 DiaryEditorSheet.kt**

移除第 78-81 行的 `sanitizeNull`，删除第 160-209 行的媒体相关代码（`saveMedia`, `tempPhotoUri`, `tempVideoUri`, `cameraLauncher`, `videoLauncher`, `galleryLauncher`），删除第 227-258 行的 `showMediaPicker` dialog，删除第 307-346 行的图片展示代码，删除第 373-441 行的格式工具栏代码。

替换为调用新组件。最终约 200 行。

- [ ] **Step 4: 编译验证**

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin
```

---

### Task 9: 最终编译与设备测试

- [ ] **Step 1: 完整构建与安装**

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:assembleDebug
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
```

- [ ] **Step 2: 启动 App**

```bash
adb shell am start -n com.dailysatori/.MainActivity
```

- [ ] **Step 3: 验证要点**
  - 主页 Tab 切换正常
  - 文章列表 → 详情页右滑入，返回右滑出
  - 设置页各子页面切换正常 + 动画
  - AI 聊天发送消息正常
  - 日记编辑器功能完好
  - App 无崩溃

---

### Plan Completion Checklist

- [ ] Task 1: 导航动画 ✓/✗
- [ ] Task 2: 性能配置 ✓/✗
- [ ] Task 3: SettingsRow 组件 ✓/✗
- [ ] Task 4: DetailTopBar 组件 ✓/✗
- [ ] Task 5: SettingsScreen 迁移 ✓/✗
- [ ] Task 6: McpAgentService 拆分 ✓/✗
- [ ] Task 7: AiChatScreen 拆分 ✓/✗
- [ ] Task 8: DiaryEditorSheet 拆分 ✓/✗
- [ ] Task 9: 最终编译测试 ✓/✗
