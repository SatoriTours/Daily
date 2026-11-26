# Daily Satori 样式系统参考（素纸墨青 · Calm Editorial）

> 统一导入：`import com.dailysatori.ui.theme.*`
> 交互参考稿：[app-style-redesign-v1.html](./mockups/app-style-redesign-v1.html)（配合 [07-app-magazine-design-standard.md](./07-app-magazine-design-standard.md) 共同约束全部页面）

## 核心原则

1. **禁止硬编码** - 颜色、间距、圆角、字号必须使用主题常量（`MaterialTheme.colorScheme.*`、`MaterialTheme.typography.*`、`Spacing`、`Radius`、`Height`、`BorderWidth`）
2. **素纸墨青基调** - 全应用统一采用「暖宣纸 × 炭墨 × 靛青」浅色与「夜读墨砚 × 月白 × 浅靛青」深色，杜绝冰冷纯灰白与刺眼纯黑
3. **语义色克制分工** - 靛青主色负责导航、选中与核心动作；赭石只用于高信号行动项（产品机会、待确认、创意灵感）；松石绿只用于完成/已收录/已润色态
4. **衬线标题 × 无衬线正文** - 展示级标题、页头与阅读锚点使用 `EditorialFontFamily`（衬线）；正文与交互控件使用 `UiFontFamily` / `ContentFontFamily`（无衬线）与等宽数字
5. **纸面层次替代重边框** - 新闻/详情流采用无框发丝分割；日记、热榜等需要聚拢元信息与多模态内容的模块使用微暖纸面卡片（`surface`），禁止卡片套卡片或重阴影

## 颜色（Material 3 语义槽位）

| 语义角色 | Material 3 槽位 | 浅色 · 宣纸 | 深色 · 墨砚 | 适用场景 |
|----------|-----------------|-------------|-------------|----------|
| 画布底色 | `background` | `#F7F5F0` | `#111110` | 页面底层背景、顶栏底色 |
| 柔白纸面 | `surface` | `#FFFDF9` | `#1A1917` | 日记卡片、热榜卡片、阅读主层、底部面板 |
| 轻容器 | `surfaceContainerLow` | `#FAF8F3` | `#151412` | 轻度抬起区块、内嵌引用底 |
| 次级容器 | `surfaceContainer` / `surfaceVariant` | `#EFECE4` | `#242320` | 语音条、搜索框、心情胶囊、次级按钮 |
| 高对比容器 | `surfaceContainerHigh` / `surfaceContainerHighest` | `#EAE6DC` / `#E5E0D5` | `#2A2825` / `#2F2D29` | 边缘手柄、强区分芯片、开关轨道 |
| 主墨文字 | `onSurface` / `onBackground` | `#1A1917` | `#ECE9E2` | 标题、正文主文本 |
| 次墨文字 | `onSurfaceVariant` | `#6F6A60` | `#9A958B` | 时间、来源、标签、辅助说明 |
| 发丝分割 | `outline` / `outlineVariant` | `rgba(26,25,23,0.085)` / `0.14` | `rgba(236,233,226,0.10)` / `0.16` | 极细分割线与微弱容器边线 |
| 主强调 · 靛青 | `primary` / `primaryContainer` | `#255B7A` / `#E3EFF5` | `#68B3D8` / `#163344` | 选中态、链接、AI 助手入口、续写/查看来源 |
| 暖强调 · 赭石 | `secondary` / `secondaryContainer` | `#B85D3B` / `#F7E9E3` | `#E59172` / `#382119` | 产品机会、机会提示条、点子/待确认高亮 |
| 静强调 · 松石 | `tertiary` / `tertiaryContainer` | `#2F7A5A` / `#E4F2EC` | `#64C49A` / `#183328` | 已收录点子、已润色语音、同步完成 |
| 警示 · 暖朱 | `error` / `errorContainer` | `#C94136` / `#FCE6E4` | `#E86B61` / `#4A1D19` | 错误提示、危险操作、未处理红点 |

### 强调色使用规范

- 选中态、导航态、AI 助手图标和轻量分区标题统一使用 `MaterialTheme.colorScheme.primary`（靛青）。
- 需要高信号区分（如「产品机会」标签/左强调条、创意点子研究态、待确认提示）时统一使用 `MaterialTheme.colorScheme.secondary` / `secondaryContainer`（赭石），禁止页面内自行造橙/紫/蓝硬编码色。
- 已完成、已收录、已润色等积极状态统一使用 `MaterialTheme.colorScheme.tertiary` / `tertiaryContainer`（松石绿）。
- 如需弱背景，优先使用 `primaryContainer` / `secondaryContainer` / `tertiaryContainer` 或 `primary.copy(alpha = 0.12f)`。

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

Daily Satori 采用「衬线标题 × 无衬线正文/UI」双角色排版：

- `EditorialFontFamily`（`FontFamily.Serif`）：用于 `display*`、`headline*`、`titleLarge` 等页头、杂志封面、文章/新闻大标题、日记/要闻阅读题头与引用块，建立沉静的纸墨辨识度。
- `ContentFontFamily` / `UiFontFamily`（`FontFamily.SansSerif`）：用于长文阅读正文、Markdown 正文、导航、按钮、输入框、设置项、标签、时间、来源与状态，保证手机端高密度阅读清晰度。
- 序数（`01` / `02`）、日期时间、排名与阅读时长在视觉上保持对齐与克制；卡片/故事题头如需衬线强调，使用 `MaterialTheme.typography.titleMedium.copy(fontFamily = EditorialFontFamily)`（不硬编码字号）。

常用层级：

```kotlin
MaterialTheme.typography.headlineLarge // 26sp / 34sp, Editorial Serif, 一级页头与详情大标题
MaterialTheme.typography.headlineSmall // 20sp / 28sp, Editorial Serif, 杂志总览与分区题头
MaterialTheme.typography.titleMedium   // 17sp / 24sp, UI 标题、卡片标题（阅读题头可叠 EditorialFontFamily）
MaterialTheme.typography.titleSmall    // 15sp / 21sp, 次级标题/标签页/设置项标题
MaterialTheme.typography.bodyLarge     // 17sp / 30sp, 长文阅读正文
MaterialTheme.typography.bodyMedium    // 15sp / 24sp, 普通正文与摘要
MaterialTheme.typography.bodySmall     // 13sp / 19sp, 元信息/辅助说明
MaterialTheme.typography.labelMedium   // 12sp / 17sp, 标签/操作链接/徽章
```

## 页面容器与卡片层级规范

所有页面必须二选一明确信息容器策略，禁止混搭重边框与嵌套卡片：

1. **无框阅读流**（今日要闻、我的机会、文章/热榜详情、读书观点）：内容直接落在 `background` 暖宣纸画布上，用 `Spacing.m`/`Spacing.l` 留白、序数/左侧细强调条（`2.5dp`–`3dp`）和极细发丝分割线（`outline`）建立节奏。
2. **微暖纸面卡片**（日记时间线、热榜平台榜、我的空间入口、设置分组）：容器使用 `surface`（浅 `#FFFDF9` / 深 `#1A1917`）、`RoundedCornerShape(Radius.card)` 或 `Radius.l`、无重阴影，必要时仅加极淡 `BorderWidth.xs` 轮廓线（`outline`）；卡片内次级模块（如语音条、心情胶囊）用 `surfaceContainer` 承托。

## 全页面验收基线（每改一页必查）

- 浅色（宣纸）与深色（墨砚）双模式均无硬编码颜色/字号，对比度清晰。
- 顶栏遵守「最多 1 页面操作位 + 最右 AI」，底栏不遮挡末尾内容。
- 卡片/列表项内部层级清晰：元信息安静、标题醒目、动作克制不争抢注意力。
- 真实长标题、空状态与窄屏下无截断错位、无无意义装饰卡片堆叠。

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

- **一级页面紧凑上下文顶栏（方案 A，强制）**：底部 4 个一级 Tab 页面（「今日」「日记」「读书」「我的」）不再在顶部重复展示与底栏相同的静态页名，统一使用单行紧凑栏（`Height.appBarCompact`）：左侧直接承载当下有用的动态上下文，右侧守住「最多 1 个页面操作位 + 最右 AI」：
  - **今日**：左侧为横向频道标签（`今日要闻 | 我的机会 | 热榜 | ...`），与右侧操作合并为单行，不再叠加「今日」大标题行。
  - **日记**：左侧显示当前滚动位置对应的月份与篇数（如 `2026年11月 · 14 篇`，随列表滚动吸顶更新），列表空时回退为「日记」。
  - **读书**：左侧显示当前阅读书名（含作者）。
  - **我的**：左侧显示当日日期锚点或轻量状态，右侧保持「设置 + AI」。
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
