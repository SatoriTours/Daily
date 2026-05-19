# App Style Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a reading-first, app-wide style system using Newsreader for content and Roboto/system sans for interface text, with unified Markdown, color, and component style roles.

**Architecture:** Keep the implementation theme-led: update shared theme files first, then migrate Markdown call sites and common components to role-based style functions. Avoid page rewrites and preserve existing behavior, navigation, data, and layout structure.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Mike Penz Markdown renderer, Android font resources, Gradle.

---

## File Structure

- Create font resources in `app/src/main/res/font/`: `newsreader_regular.ttf`, `newsreader_italic.ttf`, `newsreader_medium.ttf`, `newsreader_semibold.ttf`, `newsreader_bold.ttf`.
- Modify `app/src/main/kotlin/com/dailysatori/ui/theme/Typography.kt`: define `ContentFontFamily`, `UiFontFamily`, and a consistent Material 3 typography scale.
- Modify `app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt`: replace repeated one-off typography with reading, summary, and compact presets.
- Modify Markdown call sites in `app/src/main/kotlin/com/dailysatori/ui/**`: use role-based Markdown presets.
- Modify shared components: `ArticleCard.kt`, `DiaryCard.kt`, `SettingsRow.kt`, `AppTopBar.kt`, `SearchBar.kt`, `EmptyState.kt`, `SectionHeader.kt`, `SmartImage.kt`.
- Modify `docs/04-style-guide.md`: document the new typography and Markdown roles.

## Preconditions

- The worktree currently contains unrelated uncommitted changes. Do not revert or modify unrelated user changes.
- Before each commit, stage only the files listed in that task.
- If a touched file has existing unrelated changes, read the file and preserve them.

### Task 1: Add Newsreader Font Resources

**Files:**
- Create: `app/src/main/res/font/newsreader_regular.ttf`
- Create: `app/src/main/res/font/newsreader_italic.ttf`
- Create: `app/src/main/res/font/newsreader_medium.ttf`
- Create: `app/src/main/res/font/newsreader_semibold.ttf`
- Create: `app/src/main/res/font/newsreader_bold.ttf`
- Verify existing: `app/src/main/res/font/lato_*.ttf` remains unchanged

- [ ] **Step 1: Fetch source fonts into temp space**

Run:

```bash
rm -rf "/tmp/opencode/newsreader-fonts"
git clone --depth 1 "https://github.com/productiontype/Newsreader.git" "/tmp/opencode/newsreader-fonts"
```

Expected: repository is cloned and contains `fonts/static/ttf/Newsreader16pt-*.ttf`.

- [ ] **Step 2: Copy the required static fonts into Android resources**

Run:

```bash
cp "/tmp/opencode/newsreader-fonts/fonts/static/ttf/Newsreader16pt-Regular.ttf" "app/src/main/res/font/newsreader_regular.ttf"
cp "/tmp/opencode/newsreader-fonts/fonts/static/ttf/Newsreader16pt-Italic.ttf" "app/src/main/res/font/newsreader_italic.ttf"
cp "/tmp/opencode/newsreader-fonts/fonts/static/ttf/Newsreader16pt-Medium.ttf" "app/src/main/res/font/newsreader_medium.ttf"
cp "/tmp/opencode/newsreader-fonts/fonts/static/ttf/Newsreader16pt-SemiBold.ttf" "app/src/main/res/font/newsreader_semibold.ttf"
cp "/tmp/opencode/newsreader-fonts/fonts/static/ttf/Newsreader16pt-Bold.ttf" "app/src/main/res/font/newsreader_bold.ttf"
```

Expected: five new `.ttf` files exist under `app/src/main/res/font/`.

- [ ] **Step 3: Verify resource names are valid**

Run:

```bash
ls "app/src/main/res/font/newsreader_"*.ttf
```

Expected output includes exactly these files:

```text
app/src/main/res/font/newsreader_bold.ttf
app/src/main/res/font/newsreader_italic.ttf
app/src/main/res/font/newsreader_medium.ttf
app/src/main/res/font/newsreader_regular.ttf
app/src/main/res/font/newsreader_semibold.ttf
```

- [ ] **Step 4: Commit font resources**

Run:

```bash
git add "app/src/main/res/font/newsreader_regular.ttf" "app/src/main/res/font/newsreader_italic.ttf" "app/src/main/res/font/newsreader_medium.ttf" "app/src/main/res/font/newsreader_semibold.ttf" "app/src/main/res/font/newsreader_bold.ttf"
git commit -m "style: add Newsreader font resources"
```

Expected: commit succeeds with only the five new font files.

### Task 2: Rebuild App Typography Roles

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/Typography.kt`

- [ ] **Step 1: Replace typography definitions with role-based families**

Edit `Typography.kt` to this complete content:

```kotlin
package com.dailysatori.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dailysatori.R

val ContentFontFamily = FontFamily(
    Font(R.font.newsreader_regular, FontWeight.Normal),
    Font(R.font.newsreader_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.newsreader_medium, FontWeight.Medium),
    Font(R.font.newsreader_semibold, FontWeight.SemiBold),
    Font(R.font.newsreader_bold, FontWeight.Bold),
)

val UiFontFamily = FontFamily.SansSerif

val LatoFontFamily = UiFontFamily

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = ContentFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = 0.sp),
    displayMedium = TextStyle(fontFamily = ContentFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontFamily = ContentFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = ContentFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = ContentFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 30.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontFamily = ContentFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.1.sp),
    titleMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = ContentFontFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 30.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp),
)
```

- [ ] **Step 2: Compile just enough to catch font resource errors**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin
```

Expected: compile succeeds. If it fails with missing `R.font.newsreader_*`, re-check Task 1 file names.

- [ ] **Step 3: Commit typography roles**

Run:

```bash
git add "app/src/main/kotlin/com/dailysatori/ui/theme/Typography.kt"
git commit -m "style: define app typography roles"
```

Expected: commit succeeds with only `Typography.kt` staged.

### Task 3: Refactor Markdown Style Presets

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt`

- [ ] **Step 1: Replace Markdown style object with role-based presets**

Edit `MarkdownStyles.kt` to keep the same package/import structure and define these public functions:

```kotlin
object MarkdownStyles {
    @Composable
    fun readingTypography(): MarkdownTypography = typographyScale(bodySize = 17, bodyLine = 30, h1 = 26, h2 = 22, h3 = 19)

    @Composable
    fun summaryTypography(): MarkdownTypography = typographyScale(bodySize = 16, bodyLine = 27, h1 = 23, h2 = 20, h3 = 18)

    @Composable
    fun compactTypography(): MarkdownTypography = typographyScale(bodySize = 14, bodyLine = 22, h1 = 18, h2 = 16, h3 = 15)

    @Composable
    fun readingPadding(): MarkdownPadding = markdownPadding(
        block = 10.dp,
        list = 10.dp,
        listItemBottom = 8.dp,
        indentList = 22.dp,
        codeBlock = PaddingValues(12.dp),
        blockQuote = PaddingValues(12.dp),
        blockQuoteText = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        blockQuoteBar = PaddingValues.Absolute(3.dp, 0.dp, 10.dp, 0.dp),
    )

    @Composable
    fun summaryPadding(): MarkdownPadding = markdownPadding(
        block = 8.dp,
        list = 8.dp,
        listItemBottom = 6.dp,
        indentList = 20.dp,
        codeBlock = PaddingValues(10.dp),
        blockQuote = PaddingValues(10.dp),
        blockQuoteText = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        blockQuoteBar = PaddingValues.Absolute(3.dp, 0.dp, 8.dp, 0.dp),
    )

    @Composable
    fun compactPadding(): MarkdownPadding = markdownPadding(
        block = 4.dp,
        list = 6.dp,
        listItemBottom = 6.dp,
        indentList = 16.dp,
        codeBlock = PaddingValues(8.dp),
        blockQuote = PaddingValues(8.dp),
        blockQuoteText = PaddingValues(0.dp),
        blockQuoteBar = PaddingValues.Absolute(0.dp, 0.dp, 0.dp, 0.dp),
    )

    @Composable fun typography(): MarkdownTypography = readingTypography()
    @Composable fun padding(): MarkdownPadding = readingPadding()
    @Composable fun cardTypography(): MarkdownTypography = compactTypography()
    @Composable fun cardPadding(): MarkdownPadding = compactPadding()
    @Composable fun remoteArticleTypography(): MarkdownTypography = readingTypography()
    @Composable fun remoteArticlePadding(): MarkdownPadding = readingPadding()
}
```

Then add this private helper below the object or inside it as a private function:

```kotlin
private fun typographyScale(
    bodySize: Int,
    bodyLine: Int,
    h1: Int,
    h2: Int,
    h3: Int,
): MarkdownTypography = DefaultMarkdownTypography(
    h1 = contentStyle(FontWeight.Bold, h1, h1 + 10),
    h2 = contentStyle(FontWeight.Bold, h2, h2 + 8),
    h3 = contentStyle(FontWeight.SemiBold, h3, h3 + 7),
    h4 = contentStyle(FontWeight.SemiBold, bodySize, bodyLine),
    h5 = contentStyle(FontWeight.Medium, bodySize - 1, bodyLine - 4),
    h6 = uiStyle(FontWeight.Medium, 13, 18),
    text = contentStyle(FontWeight.Normal, bodySize, bodyLine),
    code = uiStyle(FontWeight.Normal, 13, 20),
    inlineCode = uiStyle(FontWeight.Medium, 13, 20),
    quote = contentStyle(FontWeight.Normal, bodySize, bodyLine, FontStyle.Italic),
    paragraph = contentStyle(FontWeight.Normal, bodySize, bodyLine),
    ordered = contentStyle(FontWeight.Normal, bodySize, bodyLine),
    bullet = contentStyle(FontWeight.Normal, bodySize, bodyLine),
    list = contentStyle(FontWeight.Normal, bodySize, bodyLine),
    link = contentStyle(FontWeight.Medium, bodySize, bodyLine),
)

private fun contentStyle(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    fontStyle: FontStyle = FontStyle.Normal,
): TextStyle = TextStyle(
    fontFamily = ContentFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontStyle = fontStyle,
)

private fun uiStyle(weight: FontWeight, size: Int, lineHeight: Int): TextStyle = TextStyle(
    fontFamily = UiFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)
```

Keep these imports: `PaddingValues`, `Composable`, `TextStyle`, `FontStyle`, `FontWeight`, `dp`, `sp`, `DefaultMarkdownTypography`, `MarkdownPadding`, `MarkdownTypography`, `markdownPadding`.

- [ ] **Step 2: Compile Markdown style changes**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin
```

Expected: compile succeeds and existing call sites still work through compatibility wrappers.

- [ ] **Step 3: Commit Markdown style presets**

Run:

```bash
git add "app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt"
git commit -m "style: unify markdown typography presets"
```

Expected: commit succeeds with only `MarkdownStyles.kt` staged.

### Task 4: Migrate Markdown Call Sites To Role-Based Names

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteDigestDetailScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/content/MarkdownTabPager.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsDetailScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiReferenceDetailSheet.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt`

- [ ] **Step 1: Replace full reading Markdown call sites**

Use these replacements:

```kotlin
typography = MarkdownStyles.readingTypography(),
padding = MarkdownStyles.readingPadding(),
```

Apply to article/detail reading surfaces:

```text
RemoteArticleDetailScreen.kt
RemoteDigestDetailScreen.kt
MarkdownTabPager.kt
CrayfishNewsDetailScreen.kt
```

- [ ] **Step 2: Replace summary Markdown call sites**

Use these replacements:

```kotlin
typography = MarkdownStyles.summaryTypography(),
padding = MarkdownStyles.summaryPadding(),
```

Apply to summary/news-feed surfaces:

```text
CitationText.kt
CrayfishNewsScreen.kt
```

- [ ] **Step 3: Replace compact Markdown call sites**

Use these replacements:

```kotlin
typography = MarkdownStyles.compactTypography(),
padding = MarkdownStyles.compactPadding(),
```

Apply to constrained card and bubble surfaces:

```text
MessageBubble.kt
AiReferenceDetailSheet.kt
ViewpointCard.kt
DiaryCard.kt
```

- [ ] **Step 4: Confirm no feature call site uses legacy Markdown names**

Run:

```bash
rg "MarkdownStyles\.(typography|padding|cardTypography|cardPadding|remoteArticleTypography|remoteArticlePadding)" "app/src/main/kotlin/com/dailysatori/ui"
```

Expected: no matches outside `app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt`.

- [ ] **Step 5: Compile migrated call sites**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin
```

Expected: compile succeeds.

- [ ] **Step 6: Commit Markdown call site migration**

Run:

```bash
git add "app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt" "app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteDigestDetailScreen.kt" "app/src/main/kotlin/com/dailysatori/ui/component/content/MarkdownTabPager.kt" "app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt" "app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsDetailScreen.kt" "app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsScreen.kt" "app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt" "app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiReferenceDetailSheet.kt" "app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt" "app/src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt"
git commit -m "style: migrate markdown surfaces to role presets"
```

Expected: commit includes only Markdown call site changes.

### Task 5: Normalize Shared Component Typography And Semantic Colors

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/card/ArticleCard.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/settings/SettingsRow.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/input/SearchBar.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/indicator/EmptyState.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/misc/SectionHeader.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/media/SmartImage.kt`

- [ ] **Step 1: Keep ArticleCard on shared role styles**

In `ArticleCard.kt`, keep these roles:

```kotlin
style = MaterialTheme.typography.titleSmall
style = MaterialTheme.typography.bodySmall
style = MaterialTheme.typography.labelSmall
```

No behavior change is required because Task 2 redefines those roles globally.

- [ ] **Step 2: Keep SettingsRow UI-oriented and explicit**

In `SettingsRow.kt`, ensure the title and subtitle stay:

```kotlin
Text(title, style = MaterialTheme.typography.titleSmall)
Text(
    subtitle,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)
```

No behavior change is required because Task 2 redefines those roles globally.

- [ ] **Step 3: Keep AppTopBar on UI title typography**

In `AppTopBar.kt`, ensure the title remains:

```kotlin
Text(
    text = title,
    style = MaterialTheme.typography.titleMedium,
)
```

No behavior change is required because Task 2 redefines `titleMedium` as Roboto/system sans.

- [ ] **Step 4: Keep SearchBar input text on UI body typography**

In `SearchBar.kt`, ensure placeholder and text field stay:

```kotlin
style = MaterialTheme.typography.bodyMedium
textStyle = MaterialTheme.typography.bodyMedium
```

No behavior change is required because Task 2 redefines `bodyMedium` as UI body.

- [ ] **Step 5: Normalize EmptyState hierarchy**

In `EmptyState.kt`, change the title to UI title medium and keep subtitle as UI body:

```kotlin
Text(title, style = MaterialTheme.typography.titleMedium)
if (subtitle != null) {
    Spacer(modifier = Modifier.height(Spacing.xs))
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
```

- [ ] **Step 6: Normalize SectionHeader hierarchy**

In `SectionHeader.kt`, change the text style to section-title size while retaining primary color:

```kotlin
Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
```

- [ ] **Step 7: Replace SmartImage hardcoded debug colors with semantic colors**

In `SmartImage.kt`, remove `import androidx.compose.ui.graphics.Color` and add `import com.dailysatori.ui.theme.AppColors` if needed. Replace the debug icon block with:

```kotlin
Icon(
    imageVector = if (isLocal) Icons.Filled.PhoneAndroid else Icons.Filled.Wifi,
    contentDescription = if (isLocal) "本地图片" else "远程图片",
    modifier = Modifier
        .align(Alignment.BottomEnd)
        .size(12.dp)
        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f), CircleShape),
    tint = if (isLocal) AppColors.success else AppColors.info,
)
```

- [ ] **Step 8: Compile component changes**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin
```

Expected: compile succeeds.

- [ ] **Step 9: Commit component style normalization**

Run:

```bash
git add "app/src/main/kotlin/com/dailysatori/ui/component/card/ArticleCard.kt" "app/src/main/kotlin/com/dailysatori/ui/component/settings/SettingsRow.kt" "app/src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt" "app/src/main/kotlin/com/dailysatori/ui/component/input/SearchBar.kt" "app/src/main/kotlin/com/dailysatori/ui/component/indicator/EmptyState.kt" "app/src/main/kotlin/com/dailysatori/ui/component/misc/SectionHeader.kt" "app/src/main/kotlin/com/dailysatori/ui/component/media/SmartImage.kt"
git commit -m "style: normalize shared component text roles"
```

Expected: commit contains only shared component style changes.

### Task 6: Update Style Guide Documentation

**Files:**
- Modify: `docs/04-style-guide.md`

- [ ] **Step 1: Replace the typography section**

Replace the existing `## 字体 (AppTypography)` section with:

```markdown
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
```

- [ ] **Step 2: Replace the Markdown section**

Replace the existing `## Markdown 样式` section with:

```markdown
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

// 聊天气泡、日记卡片、书摘卡片等受限空间
Markdown(
    content = text,
    typography = MarkdownStyles.compactTypography(),
    padding = MarkdownStyles.compactPadding(),
)
```

禁止在页面内手写 Markdown 的 `TextStyle(fontSize = ...sp)` 和 padding。
```

- [ ] **Step 3: Compile docs-adjacent changes are not needed, but verify grep rules**

Run:

```bash
rg "TextStyle\(fontSize|[0-9]+\.sp" "app/src/main/kotlin/com/dailysatori/ui" --glob "*.kt"
```

Expected: matches are limited to theme files or existing unrelated code that is intentionally not part of this pass.

- [ ] **Step 4: Commit documentation**

Run:

```bash
git add "docs/04-style-guide.md"
git commit -m "docs: update app style guide"
```

Expected: commit succeeds with only the style guide.

### Task 7: Final Verification And Device Smoke Test

**Files:**
- No code files unless verification exposes compile errors.

- [ ] **Step 1: Run required compile check**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run full debug build**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Install if a device is connected**

Run:

```bash
adb devices
```

If at least one device is listed as `device`, run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: install succeeds and the app launches.

- [ ] **Step 4: Manual visual smoke checklist**

Inspect these screens and note issues in the final response:

```text
Article detail: body should feel comfortable and use Newsreader.
Remote article detail: Markdown headings and paragraphs should be consistent.
Unified news: summary should be readable but slightly denser than article detail.
AI chat: assistant Markdown should fit bubbles without oversized headings.
Diary list/card: preview should be readable without looking like a full article page.
Settings: rows should remain UI-like and not editorial.
Search/input: text should remain compact and Android-like.
```

- [ ] **Step 5: Review final diff and report unrelated changes**

Run:

```bash
git status --short
git diff --stat HEAD
```

Expected: no unintended uncommitted changes from this implementation pass. If unrelated user changes remain, mention them without modifying them.

## Self-Review

- Spec coverage: typography direction is covered by Tasks 1-2; Markdown system by Tasks 3-4; color semantics and shared component rules by Task 5; documentation by Task 6; verification by Task 7.
- Placeholder scan: no unfinished markers or unspecified implementation steps remain.
- Type consistency: public Markdown functions are consistently named `readingTypography`, `summaryTypography`, `compactTypography`, `readingPadding`, `summaryPadding`, and `compactPadding` across tasks.
