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

```kotlin
// 使用 Material 3 Typography
MaterialTheme.typography.headlineLarge
MaterialTheme.typography.headlineMedium
MaterialTheme.typography.headlineSmall
MaterialTheme.typography.titleLarge
MaterialTheme.typography.titleMedium
MaterialTheme.typography.titleSmall
MaterialTheme.typography.bodyLarge     // 16sp
MaterialTheme.typography.bodyMedium    // 15sp (最常用)
MaterialTheme.typography.bodySmall     // 13sp
MaterialTheme.typography.labelLarge    // 14sp
MaterialTheme.typography.labelMedium   // 12sp
MaterialTheme.typography.labelSmall    // 11sp
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

```kotlin
// 使用 MarkdownStyles（位于 MarkdownStyles.kt）
Markdown(
    content = text,
    colors = markdownColor(),
    typography = MarkdownStyles.cardTypography(),
    padding = MarkdownStyles.cardPadding(),
)
```

## 禁止示例

```kotlin
// 禁止硬编码
Color(0xFF5E8BFF)                           // 应使用 MaterialTheme.colorScheme.primary
Modifier.padding(16.dp)                     // 应使用 Modifier.padding(Spacing.m)
TextStyle(fontSize = 14.sp)                 // 应使用 MaterialTheme.typography.titleSmall
Modifier.height(48.dp)                      // 应使用 Modifier.height(Height.button)
RoundedCornerShape(8.dp)                    // 应使用 RoundedCornerShape(Radius.s)
```
