# 项目代码全面优化设计文档

> 日期：2026-05-01 | 状态：设计中 | 范围：全项目

## 目标

1. 代码简洁高效、无重复、清晰易读
2. UI 文件行数合理，提取并复用公共组件
3. 每个文件 ≤ 500 行，每个函数 ≤ 50 行（配置类除外）
4. 优化 App 性能：页面打开更快、切换更快
5. 优化页面切换动画：详情页从右侧滑入，返回时向右滑出

## 工作流 A：代码结构优化

### A1. 拆分超长文件

**`McpAgentService.kt`**（930 行 → 拆分为 ~5 个文件）：

| 新文件 | 职责 | 预计行数 |
|--------|------|----------|
| `McpAgentService.kt` | 核心编排逻辑 | ~200 |
| `McpToolRegistry.kt` | 工具定义与执行 | ~200 |
| `McpSystemPrompt.kt` | 系统提示词构建 | ~100 |
| `McpStepExecutor.kt` | 步骤完成逻辑 | ~250 |
| `McpJsonUtils.kt` | JSON 解析工具函数 | ~80 |

### A2. 拆分超长函数（44 个 → 逐个优化）

使用 extract-method 模式：
- 将内联 lambda 提取为命名函数
- 将条件分支拆分为独立方法
- 将 composable 中的 UI 区块提取为私有 composable

**重点函数：**

| 文件 | 函数 | 当前行数 | 优化策略 |
|------|------|----------|----------|
| `McpAgentService.kt` | `completeStep` | 142 | 拆分为 prepare/execute/parse 三阶段 |
| `McpAgentService.kt` | `buildSystemPrompt` | 85 | 提取各节为独立方法 |
| `AiChatScreen.kt` | `AiChatScreen` | 117 | 提取列表、状态处理、导航栏 |
| `AiChatScreen.kt` | `MemorySearchSheet` | 103 | 提取搜索结果列表、历史记录 |
| `DiaryEditorSheet.kt` | `appendTag` | 236 | 拆分为 UI 区块和逻辑层 |
| `DiaryScreen.kt` | `DiaryScreen` | 242 | 提取筛选栏、列表、空状态 |
| `AiConfigEditScreen.kt` | `AiConfigEditScreen` | 221 | 提取各配置段 |

### A3. 拆分大型 Screen 文件（>300 行的 UI 文件）

| Screen 文件 | 当前行数 | 拆分方案 |
|-------------|----------|----------|
| `AiChatScreen.kt` | 493 | 提取 `MessageBubble`、`ChatInputBar`、`MemorySearchSheet` 为独立组件文件 |
| `DiaryEditorSheet.kt` | 446 | 提取 `EditorToolbar`、`TagSection`、`AttachmentSection` |
| `DiaryScreen.kt` | 307 | 保持在 300 行内，提取 `DiaryListSection` |
| `DataImportScreen.kt` | 295 | 提取 `ImportStepIndicator`、`ImportFileSelector` |
| `AiConfigEditScreen.kt` | 271 | 保持在 300 行内，提取表单区块 |

## 工作流 B：UI 组件抽象

### B1. 新增公共组件

| 组件 | 文件路径 | 替代的重复代码 |
|------|----------|----------------|
| `SettingsRow` | `ui/component/settings/SettingsRow.kt` | SettingsScreen、McpServerScreen、BackupSettingsScreen 中的设置行 |
| `SettingsSection` | `ui/component/settings/SettingsSection.kt` | 各设置页面的分组标题+列表模式 |
| `FormTextField` | `ui/component/input/FormTextField.kt` | AiConfigEditScreen、McpServerEditScreen 中的表单字段 |
| `DetailTopBar` | `ui/component/appbar/DetailTopBar.kt` | ArticleDetailScreen、BookSearchScreen 的顶部返回栏 |
| `ListItem` | `ui/component/list/ListItem.kt` | 各列表页中的可点击行项目 |

### B2. 组件目录结构

```
ui/component/
├── appbar/
│   ├── AppTopBar.kt          # 主页顶部栏（现有）
│   └── DetailTopBar.kt       # 详情页顶部栏（新增）
├── card/
│   ├── ArticleCard.kt        # 文章卡片（现有）
│   ├── CustomCard.kt         # 通用卡片（现有）
│   └── DiaryCard.kt          # 日记卡片（现有）
├── chip/
│   └── TagChipRow.kt         # 标签行（现有）
├── content/
│   └── ContentViewer.kt      # 内容查看器（现有）
├── dialog/
│   └── ConfirmDialog.kt      # 确认对话框（现有）
├── indicator/
│   ├── EmptyState.kt         # 空状态（现有）
│   ├── FilterIndicator.kt    # 筛选指示器（现有）
│   └── LoadingIndicator.kt   # 加载指示器（现有）
├── input/
│   ├── SearchBar.kt          # 搜索栏（现有）
│   └── FormTextField.kt      # 表单文本字段（新增）
├── list/
│   └── ListItem.kt           # 通用列表项（新增）
├── media/
│   └── SmartImage.kt         # 智能图片（现有）
├── misc/
│   ├── FeatureIcon.kt        # 功能图标（现有）
│   └── SectionHeader.kt      # 区块标题（现有）
├── scaffold/
│   └── AppScaffold.kt        # App 脚手架（现有）
└── settings/
    ├── SettingsRow.kt         # 设置行（新增）
    └── SettingsSection.kt     # 设置区块（新增）
```

### B3. 每个 Screen 目标行数

所有 UI 文件目标 ≤ 400 行，大部分 ≤ 300 行。

## 工作流 C：导航动画

### C1. 过渡动画方案

使用 Material 3 的 `AnimatedContentTransitionScope` 配置共享轴过渡：

- **前进动画**（进入详情页）：`slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)` + `fadeIn()`
- **返回动画**（返回列表页）：`slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)` + `fadeOut()`

### C2. 实现位置

在 `NavHost.kt` 的每个 `composable()` 调用中添加 `enterTransition` 和 `exitTransition` 参数。

### C3. 影响的路由

| 路由 | 动画类型 |
|------|----------|
| Home → ArticleDetail | 前进（右滑入） |
| ArticleDetail → Home | 返回（右滑出） |
| Home → BookSearch | 前进 |
| BookSearch → Home | 返回 |
| Home → AiConfig | 前进 |
| AiConfig → Home | 返回 |
| AiConfig → AiConfigEdit | 前进 |
| AiConfigEdit → AiConfig | 返回 |
| Home → Settings | 前进 |
| Settings → Home | 返回 |
| ArticleDetail → ShareDialog | 前进 |
| ShareDialog → ArticleDetail | 返回 |

## 工作流 D：性能优化

### D1. R8 / ProGuard 配置

1. 创建 `app/proguard-rules.pro`
2. 在 `app/build.gradle.kts` 中添加 release 配置：
   - `minifyEnabled = true`
   - `shrinkResources = true`
   - `proguardFiles` 指向规则文件
3. ProGuard 规则保护：
   - Koin DI 相关类
   - SQLDelight 生成类
   - kotlinx.serialization 序列化类

### D2. Compose 性能优化

1. 启用 `strongSkipping`（Compose Compiler 1.5+ 默认）
2. 为数据类添加 `@Immutable` 或 `@Stable` 注解
3. 确保 ViewModel 中的 State 使用不可变数据

### D3. 懒加载优化

1. 列表使用 `LazyColumn` 替代 `Column` + `verticalScroll`
2. `key` 参数正确设置以避免重组

## 实施顺序

1. **工作流 C（导航动画）** — 独立、影响小、快速见效
2. **工作流 D（性能配置）** — 独立、配置为主
3. **工作流 B（组件抽象）** — 为后续重构提供基础
4. **工作流 A（结构优化）** — 最大的改动，在组件基础就绪后进行

## 验证标准

每次修改后执行：
```bash
./gradlew :app:compileDebugKotlin
```

最终验证：
```bash
./gradlew :app:assembleDebug
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
```
