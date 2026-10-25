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

## 标题栏与页面操作层级

- 普通页右侧最多 **一个页面操作位 + 最右 AI**；无页面操作不补空位。页面用 `PageAction(label, icon, enabled, priority, onClick)` 提供有名称的行为，由共享 `PageActions` 统一显示：一项直接展示，多项进入带文字的“更多”。
- `AppScaffold`、`MainPageHeader`、`SettingsScaffold` 只接收 `pageActions`，不接受任意图标行；`AppTopBar.actions` 仅供 `showAssistant = false` 的助手内部任务栏使用。自定义标题栏也使用 `PageActions`，并在其右侧放 `AssistantTopBarAction`。
- 编辑页保持 **保存 + AI**，保存不得溢出。共享操作设为 `PageActionPriority.Primary`，不允许与次要操作混排，也不允许保存打开菜单。附件、标签、“收为点子”等保留在任务工具区。批量确认流程保留既有可见的底部确认栏，不机械改写任务工作流。
- 搜索态通过 `PageSearch` 替代普通标题，显示左侧退出、搜索字段、最右 AI；隐藏普通态操作，不在正文重复搜索行。嵌入式列表由所属页面管理搜索，避免重复顶栏。
- 普通图标沿用次要文字色，AI 沿用主题强调色，删除仍须原有确认。标题栏固定在滚动区外，长标题在正文完整呈现。控件最小热区用 `Height.touchTarget`（48dp），不能把视觉尺寸当作触区。
- 日记默认右侧手柄仅露 `FloatingControl.edgeReveal`（14dp），48dp 触区全部在屏内；点击只横向展开添加/录音，两枚视觉 36dp、触区 48dp。点击空白/正文或滚动收起，但不消费正文第一次点击；返回先收起，不设置自动隐藏计时器。
- 日记手柄、按钮和录音条是真正浮层，位于底部导航上方，不占固定列表行。列表仅加 `FloatingControl.scrollClearance` 可滚动末尾余量。录音始终由原服务状态驱动，启动/停止时不能再启动；录音中不因滚动/收纳隐藏状态，完成后恢复收纳，转写继续归既有记录与任务。

当前方案与浏览器设计稿见 [标题栏规范](./superpowers/specs/2026-10-25-top-bar-action-standard-design.md) 和 [V4 原型](./mockups/top-bar-standard-v4.html)。原型验证不代表 Android 视觉、触控、边缘手势或 IME 验收。

## 底部浮动 Liquid Glass

Home 底部导航和 AI 输入条使用 iOS Liquid Glass 方向的浮动材质。目标不是实色卡片，也不是完全透明，而是在内容上方保留可辨认的背景轮廓，同时保证导航图标可读。

实现要求：

- 使用 Haze 做真实背景虚化：内容层必须挂 `.hazeSource(state = hazeState)`，浮动 glass 容器使用 `.hazeEffect(state = hazeState)`。
- Haze 样式使用透明背景，避免把内容完全盖住：`backgroundColor = Color.Transparent`。
- 底部安全区、键盘避让和外边距放在 glass 外层容器；glass 本体只包住实际控件高度，保证上下都是完整圆角。
- 不使用边框表达 glass。禁止给底部 glass 加 `BorderStroke` 或 `border =`。
- 不使用 `Modifier.blur()` 伪造 backdrop blur；它只会模糊自身内容，不能虚化后面的新闻内容。

当前稳定参数：

```kotlin
private val HomeBottomBarHazeBlurRadius = 10.dp
private const val HomeBottomBarGlassAlpha = 0.10f
private const val HomeBottomBarAiGlassAlpha = 0.16f
private const val HomeBottomBarHazeTintAlpha = 0.08f
private const val HomeBottomBarHazeNoiseFactor = 0.02f
private const val HomeBottomBarGlassTopHighlightAlpha = 0.08f
private const val HomeBottomBarGlassBottomRefractionAlpha = 0.04f
```

调参原则：

- 如果看不到后面的内容，优先降低 tint、highlight、background alpha，不要继续加大 blur。
- 如果滑动时只看到白色/亮色块，说明 blur 半径过大或高光层过强；优先降低 `HomeBottomBarHazeBlurRadius` 和高光 alpha。
- 如果图标可读性不足，先微调 `HomeBottomBarGlassAlpha`，不要恢复不透明 `surface` 背景。
- AI 输入条可比普通导航稍厚，但仍应能看到虚化后的背景轮廓。

推荐结构：

```kotlin
val hazeState = rememberHazeState()

Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
    // screen content
}

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
    ) { ... }
}
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
