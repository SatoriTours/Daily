# Unified News Readable Source Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make unified news easier to read by fixing Crayfish Cloudflare blocking, hiding source lists, turning summary list items into source-detail entry points, removing visible citation tokens, and increasing list spacing.

**Architecture:** Keep citation tokens in stored AI content and source tables for routing, but transform them at display time. The summary card renders only readable Markdown; source cards are removed from the feed. Crayfish HTTP requests use a shared request builder helper so every endpoint sends the same User-Agent and Bearer token.

**Tech Stack:** Kotlin Multiplatform, Ktor client, SQLDelight, Jetpack Compose, mikepenz Markdown renderer, JUnit behavior tests.

---

### Task 1: Crayfish User-Agent Header

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/CrayfishNewsService.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test near the existing Crayfish tests:

```kotlin
@Test
fun crayfishRequestsSendBrowserUserAgent() {
    val service = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/CrayfishNewsService.kt").readText()

    assertTrue(service.contains("private const val CrayfishUserAgent"))
    assertTrue(service.contains("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"))
    assertTrue(service.contains("header(HttpHeaders.UserAgent, CrayfishUserAgent)"))
    assertTrue(service.contains("private fun HttpRequestBuilder.crayfishAuth"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`

Expected: FAIL because the service does not define the User-Agent helper yet.

- [ ] **Step 3: Write minimal implementation**

In `CrayfishNewsService.kt`, add imports:

```kotlin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
```

Add the constant below imports:

```kotlin
private const val CrayfishUserAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"
```

Add helper before `decodeCrayfishNewsListResponse`:

```kotlin
private fun HttpRequestBuilder.crayfishAuth(token: String) {
    bearerAuth(token)
    header(HttpHeaders.UserAgent, CrayfishUserAgent)
}
```

Replace every `bearerAuth(config.token)` inside this service with `crayfishAuth(config.token)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`

Expected: PASS for `crayfishRequestsSendBrowserUserAgent`.

---

### Task 2: Hide Source Cards From Summary Feed

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write the failing test**

Update `unifiedNewsUiShowsDailySummaryAndObviousSourceCards` to assert the feed hides source cards:

```kotlin
@Test
fun unifiedNewsSummaryFeedHidesSourceCards() {
    val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
    val cardBody = screen.substringAfter("private fun TodayUnifiedNewsCard").substringBefore("@Composable\nprivate fun CrayfishArticleDetailScreen")

    assertTrue(screen.contains("TodayUnifiedNewsCard"))
    assertFalse(cardBody.contains("UnifiedNewsSourceCard"))
    assertFalse(cardBody.contains("Text(\"来源\""))
    assertFalse(cardBody.contains("sources.forEach"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`

Expected: FAIL because source cards are still rendered inside `TodayUnifiedNewsCard`.

- [ ] **Step 3: Write minimal implementation**

In `TodayUnifiedNewsCard`, remove the `HorizontalDivider`, `Text("来源")`, and `sources.forEach` block. Keep the header source count because the user wants the card header to show total sources.

Delete the unused `UnifiedNewsSourceCard` composable if no other code uses it.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`

Expected: PASS for the source-card hiding test.

---

### Task 3: Hide Citation Tokens But Keep Click Routing

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsContentFormat.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write the failing test**

Replace the old visible citation-link expectation with:

```kotlin
@Test
fun unifiedNewsContentFormatHidesCitationTokensButKeepsLinks() {
    val markdown = "- 新闻 A [C1]\n- 新闻 B [R2]"

    assertEquals("daily-satori-citation://C1", unifiedNewsCitationUrl("C1"))
    assertEquals(
        "- [新闻 A](daily-satori-citation://C1)\n- [新闻 B](daily-satori-citation://R2)",
        unifiedNewsMarkdownWithCitationLinks(markdown),
    )
}
```

Add a second test:

```kotlin
@Test
fun unifiedNewsContentFormatRemovesStandaloneCitationTokens() {
    assertEquals("普通句子", visibleUnifiedNewsTextWithoutCitation("普通句子 [C29]"))
    assertEquals("- 列表内容", visibleUnifiedNewsTextWithoutCitation("- 列表内容 [R3]"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`

Expected: FAIL because links currently display `[C1]` as visible link text and `visibleUnifiedNewsTextWithoutCitation` does not exist.

- [ ] **Step 3: Write minimal implementation**

In `UnifiedNewsContentFormat.kt`, add:

```kotlin
fun visibleUnifiedNewsTextWithoutCitation(text: String): String = text
    .replace(UnifiedNewsCitationRegex, "")
    .replace(Regex("""\s+([。！？；：，,.!?;:])"""), "$1")
    .trimEnd()
```

Change `unifiedNewsMarkdownWithCitationLinks` so each line with a citation links the line text while removing the token:

```kotlin
fun unifiedNewsMarkdownWithCitationLinks(content: String): String = content
    .lines()
    .joinToString("\n") { line ->
        val citation = primaryCitationInUnifiedNewsLine(line) ?: return@joinToString line
        val visible = visibleUnifiedNewsTextWithoutCitation(line)
        val listPrefix = Regex("""^(\s*[-*+]\s+)(.*)$""").find(visible)
        if (listPrefix != null) {
            val prefix = listPrefix.groupValues[1]
            val text = listPrefix.groupValues[2]
            "$prefix[$text](${unifiedNewsCitationUrl(citation)})"
        } else {
            "[$visible](${unifiedNewsCitationUrl(citation)})"
        }
    }
```

Keep `CitationText` URI interception unchanged; it will still route links by citation URL.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`

Expected: PASS for citation display tests.

---

### Task 4: Increase Markdown List Spacing

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test:

```kotlin
@Test
fun unifiedNewsMarkdownListSpacingIsComfortableForReading() {
    val styles = java.io.File("src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt").readText()
    val paddingBody = styles.substringAfter("fun padding(): MarkdownPadding").substringBefore("}")

    assertTrue(paddingBody.contains("listItemBottom = 28.dp"))
    assertTrue(paddingBody.contains("list = 16.dp"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`

Expected: FAIL because list spacing is currently smaller.

- [ ] **Step 3: Write minimal implementation**

In `MarkdownStyles.padding()`, change:

```kotlin
list = 16.dp,
listItemBottom = 28.dp,
```

Do not change colors, fonts, or global card markdown styles.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`

Expected: PASS for spacing test.

---

### Task 5: Final Verification and Device Install

**Files:**
- No source changes expected.

- [ ] **Step 1: Run focused regression tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Compile app**

Run: `./gradlew :app:compileDebugKotlin --no-configuration-cache`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install to device**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug --no-configuration-cache`

Expected: BUILD SUCCESSFUL and APK installed on connected device.

- [ ] **Step 4: Restart app**

Run: `adb shell am force-stop com.dailysatori && adb shell am start -n com.dailysatori/.MainActivity`

Expected: App starts successfully.

---

## Self-Review

- Spec coverage: Crayfish User-Agent is covered by Task 1. Hidden feed sources are covered by Task 2. Source-detail click routing remains via citation links in Task 3. Citation token removal is covered by Task 3. Markdown list spacing is covered by Task 4. Build/install verification is covered by Task 5.
- Placeholder scan: no placeholder steps remain.
- Type consistency: `CrayfishUserAgent`, `crayfishAuth`, `visibleUnifiedNewsTextWithoutCitation`, `unifiedNewsMarkdownWithCitationLinks`, and `MarkdownStyles.padding()` names are consistent across tests and implementation steps.
