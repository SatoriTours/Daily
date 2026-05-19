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
AppColors.primary          // 0xFF5E8BFF
AppColors.success          // 0xFF4CAF50
AppColors.error            // 0xFFF44336
AppColors.warning          // 0xFFFF9800
AppColors.info             // 0xFF2196F3
```

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
Radius.l         // 16.dp
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
Height.button       // 48.dp
Height.buttonSmall  // 36.dp
Height.input        // 48.dp
Height.listItem     // 56.dp
Height.listItemSmall// 48.dp
Height.appBar       // 56.dp
Height.navBar       // 56.dp
Height.chip         // 32.dp
Height.searchBar    // 48.dp
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

Daily Satori 使用双字体系统：

- `ContentFontFamily`：Newsreader，用于长文阅读、Markdown 正文、文章/新闻详情、AI 摘要、日记预览等内容型区域。
- `UiFontFamily`：系统 Sans Serif/Roboto，用于导航、按钮、输入框、设置项、标签、时间、来源、状态等界面型文本。

常用层级：

```kotlin
MaterialTheme.typography.headlineLarge // 24sp / 32sp, 内容页大标题
MaterialTheme.typography.headlineSmall // 18sp / 26sp, 内容型区块标题
MaterialTheme.typography.titleMedium   // 16sp / 24sp, UI 标题和 TopBar
MaterialTheme.typography.titleSmall    // 14sp / 20sp, 卡片标题/设置项标题
MaterialTheme.typography.bodyLarge     // 17sp / 30sp, 长文阅读正文
MaterialTheme.typography.bodyMedium    // 15sp / 24sp, 普通 UI 正文
MaterialTheme.typography.bodySmall     // 13sp / 18sp, 元信息/说明
MaterialTheme.typography.labelMedium   // 12sp / 16sp, 标签/Badge
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
