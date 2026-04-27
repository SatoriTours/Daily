# Daily Satori Android 全项目重构设计

> 日期: 2026-04-27
> 方案: 大规模重写 (方案 B)

## 目标

1. 目录结构按 Android/KMP 最佳实践重组
2. UI 框架统一，所有页面使用统一的 Scaffold + TopAppBar
3. 修复 edge-to-edge 导致的标题栏上方空白过大问题
4. 主题系统重构
5. 所有现有功能保持不变

## 约束

- 现有功能不变：所有 16 个路由页面、12 个 ViewModel、12 个 Service 保持功能一致
- shared/ 模块改动较小（仅提取 DTO）
- 继续使用 Koin DI（不迁移到 Hilt）
- 继续使用 SQLDelight（数据库不变）
- Material 3 + Jetpack Compose 不变

---

## Part 1: app/ 模块目录重组

### 新目录结构

```
com/dailysatori/
├── DailySatoriApplication.kt
├── MainActivity.kt
├── App.kt                          # 原 DailySatoriApp.kt 重命名
│
├── core/
│   ├── di/
│   │   ├── AppModule.kt
│   │   ├── PlatformModule.kt
│   │   └── ViewModelModule.kt
│   ├── navigation/
│   │   ├── NavHost.kt
│   │   └── Routes.kt
│   ├── service/
│   │   ├── WebServerService.kt
│   │   ├── ClipboardMonitorService.kt
│   │   ├── AppUpgradeService.kt
│   │   └── I18nInitializer.kt
│   └── util/
│       └── TimeUtils.kt            # 提取重复的 formatTime()
│
├── ui/
│   ├── theme/
│   │   ├── Theme.kt
│   │   ├── Color.kt
│   │   ├── Typography.kt
│   │   ├── Spacing.kt
│   │   └── Shape.kt
│   │
│   ├── component/
│   │   ├── appbar/
│   │   │   └── AppTopBar.kt
│   │   ├── scaffold/
│   │   │   └── AppScaffold.kt      # 统一页面框架
│   │   ├── card/
│   │   │   ├── ArticleCard.kt
│   │   │   ├── DiaryCard.kt
│   │   │   └── CustomCard.kt
│   │   ├── dialog/
│   │   │   └── ConfirmDialog.kt
│   │   ├── input/
│   │   │   └── SearchBar.kt
│   │   ├── indicator/
│   │   │   ├── EmptyState.kt
│   │   │   ├── LoadingIndicator.kt
│   │   │   └── FilterIndicator.kt
│   │   ├── media/
│   │   │   └── SmartImage.kt
│   │   ├── content/
│   │   │   └── ContentViewer.kt
│   │   ├── chip/
│   │   │   └── TagChipRow.kt
│   │   └── misc/
│   │       ├── FeatureIcon.kt
│   │       └── SectionHeader.kt
│   │
│   └── feature/
│       ├── home/
│       │   └── HomeScreen.kt
│       ├── article/
│       │   ├── ArticleListScreen.kt
│       │   ├── ArticleDetailScreen.kt
│       │   ├── ArticleViewModel.kt
│       │   └── ArticleCardItem.kt
│       ├── diary/
│       │   ├── DiaryScreen.kt
│       │   ├── DiaryEditorSheet.kt
│       │   ├── DiaryViewModel.kt
│       │   └── DiaryCardItem.kt
│       ├── book/
│       │   ├── BooksScreen.kt
│       │   ├── BookSearchScreen.kt
│       │   ├── BooksViewModel.kt
│       │   └── ViewpointCard.kt
│       ├── aichat/
│       │   ├── AiChatScreen.kt
│       │   └── AiChatViewModel.kt
│       ├── aiconfig/
│       │   ├── AiConfigScreen.kt
│       │   ├── AiConfigEditScreen.kt
│       │   └── AiConfigViewModel.kt
│       ├── settings/
│       │   ├── SettingsScreen.kt
│       │   ├── BackupSettingsScreen.kt
│       │   ├── BackupRestoreScreen.kt
│       │   ├── PluginCenterScreen.kt
│       │   ├── DataImportScreen.kt
│       │   ├── SettingsViewModel.kt
│       │   ├── BackupSettingsViewModel.kt
│       │   ├── BackupRestoreViewModel.kt
│       │   └── PluginCenterViewModel.kt
│       └── share/
│           ├── ShareDialogScreen.kt
│           └── ShareDialogViewModel.kt
│
└── (移除: viewmodel/ 目录整体移入各 feature)
    (移除: ui/pages/PlaceholderScreen.kt)
    (移除: ui/components/ArticleCard.kt.fix)
```

### 变更清单

| 变更 | 说明 |
|------|------|
| `viewmodel/*.kt` | 移入对应 `feature/` 目录 |
| `ui/pages/*` | 重命名为 `ui/feature/*` |
| `di/` | 移入 `core/di/` |
| `ui/navigation/` | 移入 `core/navigation/` |
| `service/` (app) | 移入 `core/service/` |
| `ui/components/` | 重组为 `ui/component/` + 子目录 |
| `DailySatoriApp.kt` | 重命名为 `App.kt` |
| `PlaceholderScreen.kt` | 删除（未使用） |
| `ArticleCard.kt.fix` | 删除（残留文件） |
| `formatTime()` | 提取到 `core/util/TimeUtils.kt` |

---

## Part 2: 主题系统重构

### Color.kt 重构

- 移除手动维护的 `xxxLight`/`xxxDark` 命名对
- 使用 Material 3 的 `lightColorScheme()` / `darkColorScheme()` 在 Theme.kt 中一次性定义
- `AppColors` 对象保留语义化扩展颜色（如 tagColors、success/error/warning/info）

### 新增 Shape.kt

定义统一 Shape tokens:
- `AppShapes.small` — 4.dp
- `AppShapes.medium` — 12.dp
- `AppShapes.large` — 16.dp
- `AppShapes.full` — 100.dp (circular)

### Theme.kt 增强

- 正确处理 edge-to-edge 下的状态栏颜色
- 通过 `SideEffect` 设置 `SystemBarStyle`

---

## Part 3: UI 框架统一

### AppScaffold 组件

统一的页面框架，处理：
- TopAppBar 配置
- WindowInsets（状态栏、导航栏）
- 统一的 padding 逻辑
- edge-to-edge 适配

```kotlin
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    showBack: Boolean = onBack != null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
)
```

### AppTopBar 组件

统一标题栏：
- 正确的背景色（使用 MaterialTheme.colorScheme 而非硬编码）
- 正确的文字颜色（使用 onPrimary / onSurface）
- 统一的返回按钮样式

### 标题栏间距修复

- `MainActivity.enableEdgeToEdge()` 保持
- `AppTopBar` 内使用 `TopAppBar` 自带的 WindowInsets 处理
- `Scaffold` 设置正确的 `contentWindowInsets`
- 移除各 Screen 中手动添加的多余 padding

---

## Part 4: shared/ 模块微调

### 新增 data/model/ 目录

从各 Service 文件中提取 DTO:

| 原位置 | 提取到 |
|--------|--------|
| `service/ai/AiService.kt` 中的 `ChatMessage` | `data/model/ChatMessage.kt` |
| `service/ai/AiService.kt` 中的 `AiSummaryResult` | `data/model/AiSummaryResult.kt` |
| `service/book/BookSearchService.kt` 中的 `BookSearchResult` | `data/model/BookSearchResult.kt` |
| `service/mcp/McpAgentService.kt` 中的 DTO | `data/model/McpModels.kt` |
| `service/backup/BackupService.kt` 中的 `BackupEntry` | `data/model/BackupEntry.kt` |
| `service/parser/WebpageParserService.kt` 中的 DTO | `data/model/ParserModels.kt` |
| `service/import/ImportService.kt` 中的 `ImportResult` | `data/model/ImportResult.kt` |

其余 shared/ 结构保持不变。

---

## Part 5: 实施顺序

1. 创建新目录结构
2. 移动/重命名文件
3. 重构主题系统
4. 创建 AppScaffold + AppTopBar 组件
5. 逐个页面迁移到新框架
6. 修复 WindowInsets 问题
7. 提取 shared/ DTO
8. 清理残留文件
9. 验证所有功能正常

---

## 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| 大量文件移动可能引入编译错误 | 每步移动后运行 `./gradlew assembleDebug` 验证 |
| WindowInsets 处理不当导致布局问题 | 使用标准 Material 3 TopAppBar 默认行为 |
| ViewModel 移动后 Koin 注册路径变化 | Koin 通过类注册，不依赖包路径 |
| 功能回归 | 逐页面验证，保持所有路由可达 |
