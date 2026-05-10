# X Markdown and App Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Save X/Twitter originals as faithful Markdown content and replace the Android launcher icon with a calm daily-awareness brand mark.

**Architecture:** Add a small deterministic X/Twitter Markdown formatter beside the existing article parsing helpers, then route only X/Twitter status URLs through it before AI Markdown conversion. Add adaptive and fallback launcher icon resources using vector drawables, then point the manifest at the new resources.

**Tech Stack:** Kotlin Multiplatform shared module, Kotlin common tests, Android XML vector/adaptive icon resources, Gradle Android build.

---

## File Structure

- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt`: add URL-aware Markdown generation helpers and route X/Twitter status URLs away from AI Markdown conversion.
- Modify `shared/src/commonTest/kotlin/com/dailysatori/service/parser/ArticleProcessingContentTest.kt`: add tests for X/Twitter URL detection and deterministic Markdown output.
- Create `app/src/main/res/drawable/ic_launcher_background.xml`: teal/cyan launcher background vector.
- Create `app/src/main/res/drawable/ic_launcher_foreground.xml`: centered white sunrise/ring foreground vector with adaptive icon safe area.
- Create `app/src/main/res/drawable/ic_launcher_monochrome.xml`: single-color launcher foreground for Android themed icons.
- Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`: adaptive icon definition.
- Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`: round adaptive icon definition.
- Modify `app/src/main/AndroidManifest.xml`: replace system edit icon with app launcher icons and set `android:roundIcon`.

---

### Task 1: X/Twitter Deterministic Markdown

**Files:**
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/parser/ArticleProcessingContentTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt`

- [ ] **Step 1: Add failing tests for X/Twitter URL detection and Markdown formatting**

Append these tests inside `ArticleProcessingContentTest` before the closing brace:

```kotlin
    @Test
    fun detectsTwitterStatusUrlsForPlatformMarkdown() {
        assertEquals(true, isTwitterStatusUrl("https://x.com/i/status/2051891753821556976"))
        assertEquals(true, isTwitterStatusUrl("https://x.com/0xMulight/status/2050393928340488265"))
        assertEquals(true, isTwitterStatusUrl("https://twitter.com/0xMulight/status/2050393928340488265"))
        assertEquals(false, isTwitterStatusUrl("https://x.com/home"))
        assertEquals(false, isTwitterStatusUrl("https://example.com/posts/2051891753821556976"))
    }

    @Test
    fun formatsTwitterMarkdownWithTextUrlAndMedia() {
        val extracted = ExtractedContent(
            title = "X post",
            content = "  这是一条值得收藏的推文。\n\n登录\n查看新帖  ",
            htmlContent = null,
            coverImageUrl = "https://pbs.twimg.com/media/cover.jpg?format=jpg&name=large",
            imageUrls = listOf(
                "https://pbs.twimg.com/media/cover.jpg?format=jpg&name=large",
                "https://pbs.twimg.com/media/second.jpg?format=jpg&name=large",
            ),
        )

        val markdown = twitterStatusMarkdown(
            url = "https://x.com/i/status/2051891753821556976",
            extracted = extracted,
        )

        assertEquals(
            """
            # 推文内容

            这是一条值得收藏的推文。

            原文链接：https://x.com/i/status/2051891753821556976

            ## 媒体

            ![媒体 1](https://pbs.twimg.com/media/cover.jpg?format=jpg&name=large)
            ![媒体 2](https://pbs.twimg.com/media/second.jpg?format=jpg&name=large)
            """.trimIndent(),
            markdown,
        )
    }

    @Test
    fun twitterMarkdownFallsBackToOriginalUrlWhenContentIsUnavailable() {
        val markdown = twitterStatusMarkdown(
            url = "https://x.com/i/status/2051891753821556976",
            extracted = ExtractedContent(null, "登录\n查看新帖", null, null, emptyList()),
        )

        assertEquals(
            """
            # 推文内容

            原文链接：https://x.com/i/status/2051891753821556976
            """.trimIndent(),
            markdown,
        )
    }
```

- [ ] **Step 2: Run the new tests and verify they fail**

Run:

```bash
./gradlew :shared:allTests --tests "com.dailysatori.service.parser.ArticleProcessingContentTest"
```

Expected: tests fail because `isTwitterStatusUrl` and `twitterStatusMarkdown` are not defined.

- [ ] **Step 3: Add deterministic X/Twitter helpers**

Add these helper functions near the existing top-level parser helpers in `WebpageParserService.kt`, after `articleTitleInput` and before `articleMarkdownInput`:

```kotlin
internal fun isTwitterStatusUrl(url: String?): Boolean {
    val normalized = url?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank()) return false
    val hostMatches = normalized.startsWith("https://x.com/") ||
        normalized.startsWith("http://x.com/") ||
        normalized.startsWith("https://twitter.com/") ||
        normalized.startsWith("http://twitter.com/")
    if (!hostMatches) return false
    return "/status/" in normalized || "/i/status/" in normalized
}

internal fun twitterStatusMarkdown(url: String, extracted: ExtractedContent?): String {
    val text = cleanedTwitterVisibleText(extracted?.content)
    val media = twitterMediaUrls(extracted)
    return buildString {
        append("# 推文内容\n\n")
        if (text.isNotBlank()) {
            append(text)
            append("\n\n")
        }
        append("原文链接：")
        append(url.trim())
        if (media.isNotEmpty()) {
            append("\n\n## 媒体\n\n")
            media.forEachIndexed { index, mediaUrl ->
                append("![媒体 ")
                append(index + 1)
                append("](")
                append(mediaUrl)
                append(")")
                if (index != media.lastIndex) append('\n')
            }
        }
    }.trim()
}

private fun cleanedTwitterVisibleText(text: String?): String {
    val noise = setOf(
        "登录",
        "查看新帖",
        "Sign in",
        "Log in",
        "Don’t miss what’s happening",
        "Don't miss what's happening",
    )
    return text.orEmpty()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { it in noise }
        .joinToString("\n")
        .trim()
}

private fun twitterMediaUrls(extracted: ExtractedContent?): List<String> = buildList {
    extracted?.coverImageUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
    extracted?.imageUrls.orEmpty()
        .filter { it.isNotBlank() }
        .forEach { add(it) }
}.distinct()
```

- [ ] **Step 4: Route X/Twitter Markdown generation away from AI conversion**

Change `processAiTasks` Markdown generation call from:

```kotlin
val markdown = generateArticleMarkdown(article, extracted, extracted?.htmlContent ?: "", apiAddress, apiToken, modelName, provider)
```

to:

```kotlin
val markdown = generateArticleMarkdown(article, extracted, apiAddress, apiToken, modelName, provider)
```

Then replace the `generateArticleMarkdown` function signature and first lines with:

```kotlin
    private suspend fun generateArticleMarkdown(
        article: Article,
        extracted: ExtractedContent?,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        provider: String,
    ): String {
        val url = article.url.orEmpty()
        if (isTwitterStatusUrl(url)) {
            return generatedMarkdownOrFallback(
                generated = twitterStatusMarkdown(url, extracted),
                existing = article.ai_markdown_content,
                extractedContent = null,
            )
        }
        val htmlContent = extracted?.htmlContent.orEmpty()
        if (htmlContent.isBlank()) return generatedMarkdownOrFallback("", article.ai_markdown_content, extracted?.content)
```

Keep the existing `try` block below those lines unchanged.

- [ ] **Step 5: Run parser tests and verify they pass**

Run:

```bash
./gradlew :shared:allTests --tests "com.dailysatori.service.parser.ArticleProcessingContentTest"
```

Expected: `ArticleProcessingContentTest` passes.

---

### Task 2: Android Launcher Icon Resources

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add adaptive icon background vector**

Create `app/src/main/res/drawable/ic_launcher_background.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="18"
                android:startY="8"
                android:endX="94"
                android:endY="102"
                android:type="linear"
                android:startColor="#0891B2"
                android:centerColor="#0F766E"
                android:endColor="#164E63" />
        </aapt:attr>
    </path>
</vector>
```

- [ ] **Step 2: Add adaptive icon foreground vector**

Create `app/src/main/res/drawable/ic_launcher_foreground.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#00000000"
        android:pathData="M30,54a24,24 0,1 1,48 0"
        android:strokeColor="#F0FDFA"
        android:strokeWidth="7"
        android:strokeLineCap="round" />
    <path
        android:fillColor="#00000000"
        android:pathData="M24,68h60"
        android:strokeColor="#F0FDFA"
        android:strokeWidth="7"
        android:strokeLineCap="round" />
    <path
        android:fillColor="#CCF7F3"
        android:pathData="M54,40m-7,0a7,7 0,1 1,14 0a7,7 0,1 1,-14 0" />
    <path
        android:fillColor="#00000000"
        android:pathData="M38,78c9,6 23,6 32,0"
        android:strokeColor="#CCF7F3"
        android:strokeWidth="5"
        android:strokeLineCap="round" />
</vector>
```

- [ ] **Step 3: Add monochrome themed icon vector**

Create `app/src/main/res/drawable/ic_launcher_monochrome.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#00000000"
        android:pathData="M30,54a24,24 0,1 1,48 0"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="7"
        android:strokeLineCap="round" />
    <path
        android:fillColor="#00000000"
        android:pathData="M24,68h60"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="7"
        android:strokeLineCap="round" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M54,40m-7,0a7,7 0,1 1,14 0a7,7 0,1 1,-14 0" />
    <path
        android:fillColor="#00000000"
        android:pathData="M38,78c9,6 23,6 32,0"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="5"
        android:strokeLineCap="round" />
</vector>
```

- [ ] **Step 4: Add adaptive icon XML resources**

Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
```

Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
```

- [ ] **Step 5: Point manifest at the new icon resources**

Change the `<application>` attributes in `app/src/main/AndroidManifest.xml` from:

```xml
        android:icon="@android:drawable/ic_menu_edit"
        android:label="Daily Satori"
```

to:

```xml
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="Daily Satori"
```

- [ ] **Step 6: Run Android resource compilation**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: build succeeds. If the vector gradient fails because `aapt` namespace is missing, add `xmlns:aapt="http://schemas.android.com/aapt"` to `ic_launcher_background.xml` and rerun.

---

### Task 3: Required Verification And Device Install

**Files:**
- No code files unless verification exposes a bug.

- [ ] **Step 1: Run required compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Install debug build to connected device**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
```

Expected: install succeeds, or command reports no connected device. If no device is connected, report that install verification could not run.

- [ ] **Step 3: Launch app on device**

Run:

```bash
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: app launches, or `adb` reports no connected device. If no device is connected, report that launch verification could not run.

---

## Self-Review

- Spec coverage: Task 1 covers faithful X/Twitter Markdown and no AI rewrite. Task 2 covers adaptive, round, monochrome, and manifest icon resources. Task 3 covers required compile/install/launch verification.
- Placeholder scan: no TBD/TODO placeholders remain.
- Type consistency: helper names are consistently `isTwitterStatusUrl`, `twitterStatusMarkdown`, `cleanedTwitterVisibleText`, and `twitterMediaUrls`.
