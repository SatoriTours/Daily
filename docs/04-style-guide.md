# Daily Satori 样式系统参考

> 统一导入：`import com.dailysatori.ui.theme.*`

## 核心原则

1. **禁止硬编码** - 颜色、间距、字体必须使用主题常量
2. **Material 3 驱动** - 使用 `MaterialTheme.colorScheme.*` 获取颜色
3. **优先级**：主题常量 > 内联值（禁止）

## 颜色

```kotlin
// Material 3 主题颜色（自动适配亮色/暗色模式）
MaterialTheme.colorScheme.primary
MaterialTheme.colorScheme.onPrimary
MaterialTheme.colorScheme.surface
MaterialTheme.colorScheme.onSurface
MaterialTheme.colorScheme.onSurfaceVariant
MaterialTheme.colorScheme.surfaceContainer
MaterialTheme.colorScheme.surfaceContainerHighest
MaterialTheme.colorScheme.errorContainer
MaterialTheme.colorScheme.onErrorContainer

// AppColors 扩展色（位于 Color.kt）
AppColors.primary          // iOS system blue, light-mode default
AppColors.primaryLight     // iOS system blue variant for dark surfaces
AppColors.success          // iOS system green
AppColors.error            // iOS system red
AppColors.warning          // iOS system orange
AppColors.info             // iOS system blue
```

### 强调色使用规范

选择态、导航态和轻量分区标题统一使用 `MaterialTheme.colorScheme.primary`。例如底部导航选中态、新闻来源筛选选中态、读书观点中的“案例”分区标题，都应使用主题主色保持一致。

禁止为同类强调状态引入新的非主题色；如需弱背景，使用 `MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)` 或既有主题容器色。

## 间距 (Spacing)

```kotlin
Spacing.xxs  // 2.dp
Spacing.xs   // 4.dp
Spacing.s    // 8.dp
Spacing.m    // 16.dp
Spacing.l    // 24.dp
Spacing.xl   // 32.dp
Spacing.xxl  // 48.dp
```

## 圆角 (Radius)

```kotlin
Radius.none      // 0.dp
Radius.xxs       // 2.dp
Radius.xs        // 4.dp
Radius.s         // 8.dp
Radius.m         // 12.dp
Radius.l         // 22.dp
Radius.xl        // 24.dp
Radius.circular  // 100.dp
```

## 图标尺寸 (IconSize)

```kotlin
IconSize.xs   // 16.dp
IconSize.s    // 18.dp
IconSize.m    // 20.dp
IconSize.l    // 24.dp
IconSize.xl   // 32.dp
IconSize.xxl  // 48.dp
```

## 控件高度 (Height)

```kotlin
Height.button       // 46.dp
Height.buttonSmall  // 34.dp
Height.input        // 46.dp
Height.listItem     // 54.dp
Height.listItemSmall// 46.dp
Height.appBar       // 54.dp
Height.appBarCompact// 48.dp
Height.navBar       // 52.dp
Height.chip         // 30.dp
Height.searchBar    // 46.dp
```

## 边框宽度 (BorderWidth)

```kotlin
BorderWidth.xs   // 0.5.dp
BorderWidth.s    // 1.dp
BorderWidth.m    // 1.5.dp
BorderWidth.l    // 2.dp
BorderWidth.xl   // 4.dp
```

## 字体 (AppTypography)

Daily Satori 使用内容/UI 两类排版角色；当前两类角色都共享系统 SansSerif 字体族：

- `ContentFontFamily`：系统内容字体，用于长文阅读、Markdown 正文、文章/新闻详情、AI 摘要、日记预览等内容型区域。
- `UiFontFamily`：系统 Sans Serif/Roboto，用于导航、按钮、输入框、设置项、标签、时间、来源、状态等界面型文本。

常用层级：

```kotlin
MaterialTheme.typography.headlineLarge // 26sp / 34sp, 内容页大标题
MaterialTheme.typography.headlineSmall // 20sp / 28sp, 内容型区块标题
MaterialTheme.typography.titleMedium   // 17sp / 24sp, UI 标题和 TopBar
MaterialTheme.typography.titleSmall    // 15sp / 21sp, 卡片标题/设置项标题
MaterialTheme.typography.bodyLarge     // 17sp / 30sp, 长文阅读正文
MaterialTheme.typography.bodyMedium    // 15sp / 24sp, 普通 UI 正文
MaterialTheme.typography.bodySmall     // 13sp / 19sp, 元信息/说明
MaterialTheme.typography.labelMedium   // 12sp / 17sp, 标签/Badge
```

## Composable 组件使用示例

```kotlin
// Surface 卡片
Surface(
    shape = RoundedCornerShape(Radius.m),
    color = MaterialTheme.colorScheme.surfaceContainer,
) { ... }

// 输入框
TextField(
    colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    ),
    shape = RoundedCornerShape(Radius.circular),
    textStyle = MaterialTheme.typography.bodyMedium,
)

// 间距
Spacer(modifier = Modifier.height(Spacing.m))
Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)

// 按钮
FilledIconButton(
    modifier = Modifier.size(40.dp),
    shape = CircleShape,
    colors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary,
    ),
)
```

## Markdown 样式

Markdown 必须使用 `MarkdownStyles` 的场景化预设：

```kotlin
// 文章、新闻详情等全屏阅读
Markdown(
    content = text,
    typography = MarkdownStyles.readingTypography(),
    padding = MarkdownStyles.readingPadding(),
)

// 统一新闻、摘要等中等长度内容
Markdown(
    content = text,
    typography = MarkdownStyles.summaryTypography(),
    padding = MarkdownStyles.summaryPadding(),
)

// 聊天气泡、日记卡片、观点卡片等受限空间
Markdown(
    content = text,
    typography = MarkdownStyles.compactTypography(),
    padding = MarkdownStyles.compactPadding(),
)
```

禁止在页面内手写 Markdown 的 `TextStyle(fontSize = ...sp)` 和 padding。

## 禁止示例

```kotlin
// 禁止硬编码
Color(0xFF5E8BFF)                           // 应使用 MaterialTheme.colorScheme.primary
Modifier.padding(16.dp)                     // 应使用 Modifier.padding(Spacing.m)
TextStyle(fontSize = 14.sp)                 // 应使用 MaterialTheme.typography.titleSmall
Modifier.height(48.dp)                      // 应使用 Modifier.height(Height.button)
RoundedCornerShape(8.dp)                    // 应使用 RoundedCornerShape(Radius.s)
```
