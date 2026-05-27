# News Summary Briefing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the approved morning-briefing redesign for the Daily Satori news summary page without degrading visual quality from the browser demo.

**Architecture:** Keep existing navigation, data loading, and citation behavior. Add focused parsing/presentation helpers for briefing content, then replace the visual body of `TodayUnifiedNewsCard` and generating skeleton with Material 3 themed briefing components.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, KMP shared database models, existing Daily Satori theme tokens.

---

## File Structure

- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsBriefingContent.kt`
  - Owns pure parsing/presentation helpers: lead extraction, numbered key point extraction, counts, source chip labels.
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsBriefingContentTest.kt`
  - Verifies parsing and display model behavior without Compose runtime.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
  - Replaces the old summary card body and generating skeleton with briefing-style Compose UI.
- Modify: `docs/superpowers/specs/2026-05-27-news-summary-briefing-design.md`
  - Only if implementation reveals a necessary clarification.

No git commits are part of this plan because this environment requires explicit user approval before committing.

## Task 1: Briefing Content Parser

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsBriefingContent.kt`
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsBriefingContentTest.kt`

- [ ] **Step 1: Write failing parser tests**

Create `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsBriefingContentTest.kt`:

```kotlin
package com.dailysatori.ui.feature.unifiednews

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnifiedNewsBriefingContentTest {
    @Test
    fun `extracts lead and citation points from summary markdown`() {
        val model = unifiedNewsBriefingContent(
            """
            # 今日统一新闻总结

            今天 AI 工具开始从功能展示走向团队治理。

            - **AI 编程工具强调治理** [R1]
            - 消费电子新品集中发布 [R2]
            """.trimIndent(),
        )

        assertEquals("今天 AI 工具开始从功能展示走向团队治理。", model.lead)
        assertEquals("今天需要知道的 2 件事", model.title)
        assertEquals(2, model.points.size)
        assertEquals("AI 编程工具强调治理", model.points[0].text)
        assertEquals("R1", model.points[0].citation)
        assertEquals("消费电子新品集中发布", model.points[1].text)
        assertEquals("R2", model.points[1].citation)
    }

    @Test
    fun `falls back when there are no citation points`() {
        val model = unifiedNewsBriefingContent("只有一段普通总结，没有引用。")

        assertEquals("今日新闻简报", model.title)
        assertEquals("只有一段普通总结，没有引用。", model.lead)
        assertEquals(emptyList(), model.points)
    }

    @Test
    fun `ignores headings when selecting lead`() {
        val model = unifiedNewsBriefingContent(
            """
            ## 科技
            第一段真正导语。
            - 新闻条目 [R1]
            """.trimIndent(),
        )

        assertEquals("第一段真正导语。", model.lead)
    }

    @Test
    fun `empty content has no lead and default title`() {
        val model = unifiedNewsBriefingContent("   ")

        assertEquals("今日新闻简报", model.title)
        assertNull(model.lead)
        assertEquals(emptyList(), model.points)
    }
}
```

- [ ] **Step 2: Run parser tests and verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsBriefingContentTest`

Expected: FAIL because `unifiedNewsBriefingContent` does not exist.

- [ ] **Step 3: Implement parser model and helpers**

Create `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsBriefingContent.kt`:

```kotlin
package com.dailysatori.ui.feature.unifiednews

data class UnifiedNewsBriefingContent(
    val title: String,
    val lead: String?,
    val points: List<UnifiedNewsBriefingPoint>,
)

data class UnifiedNewsBriefingPoint(
    val text: String,
    val citation: String?,
)

private val BriefingListItemRegex = Regex("""^\s*[-*+]\s+(.+)""")
private val BriefingCitationRegex = Regex("""\[([RCDF]\d+)]""")

fun unifiedNewsBriefingContent(content: String): UnifiedNewsBriefingContent {
    val display = displayUnifiedNewsMarkdown(content)
    val points = display.lines().mapNotNull(::briefingPointFromLine)
    return UnifiedNewsBriefingContent(
        title = if (points.isEmpty()) "今日新闻简报" else "今天需要知道的 ${points.size} 件事",
        lead = briefingLead(display),
        points = points,
    )
}

private fun briefingLead(content: String): String? = content
    .lines()
    .map { it.trim() }
    .firstOrNull { line ->
        line.isNotBlank() &&
            !line.startsWith("#") &&
            !BriefingListItemRegex.containsMatchIn(line)
    }
    ?.let(::visibleUnifiedNewsTextWithoutCitation)
    ?.ifBlank { null }

private fun briefingPointFromLine(line: String): UnifiedNewsBriefingPoint? {
    val item = BriefingListItemRegex.find(line)?.groupValues?.get(1) ?: return null
    val citation = BriefingCitationRegex.find(item)?.groupValues?.get(1)
    if (citation == null && !BriefingCitationRegex.containsMatchIn(item)) return null
    val text = visibleUnifiedNewsTextWithoutCitation(item)
        .replace(Regex("""\*\*(.*?)\*\*"""), "$1")
        .replace(Regex("""__(.*?)__"""), "$1")
        .replace(Regex("""`([^`]*)`"""), "$1")
        .trim()
    return UnifiedNewsBriefingPoint(text = text, citation = citation)
}
```

- [ ] **Step 4: Run parser tests and verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsBriefingContentTest`

Expected: PASS.

## Task 2: Briefing Card Compose UI

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsBriefingContentTest.kt`

- [ ] **Step 1: Add one parser test for fallback rendering inputs**

Append this test to `UnifiedNewsBriefingContentTest`:

```kotlin
@Test
fun `plain markdown without citation remains available for fallback`() {
    val model = unifiedNewsBriefingContent(
        """
        今天市场关注 AI 硬件。

        普通段落继续保留给 CitationText fallback。
        """.trimIndent(),
    )

    assertEquals("今日新闻简报", model.title)
    assertEquals("今天市场关注 AI 硬件。", model.lead)
    assertEquals(0, model.points.size)
}
```

- [ ] **Step 2: Run the focused test**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsBriefingContentTest`

Expected: PASS.

- [ ] **Step 3: Replace `TodayUnifiedNewsCard` body with briefing UI**

In `UnifiedNewsScreen.kt`, keep the existing function signature and citation callback. Replace the implementation of `TodayUnifiedNewsCard` with a Material 3 briefing card that computes:

```kotlin
val briefing = remember(summary.content) { unifiedNewsBriefingContent(summary.content) }
```

The card should contain these new private composables in the same file:

```kotlin
@Composable
private fun UnifiedNewsBriefingHero(summary: Unified_news_summary, sources: List<Unified_news_source>, briefing: UnifiedNewsBriefingContent)

@Composable
private fun UnifiedNewsBriefingStats(sourceCount: Int, pointCount: Int, citationCount: Int)

@Composable
private fun UnifiedNewsBriefingPointList(points: List<UnifiedNewsBriefingPoint>, sources: List<Unified_news_source>, onCitationClick: (Unified_news_source) -> Unit)

@Composable
private fun UnifiedNewsBriefingSourceRow(sources: List<Unified_news_source>)
```

Required styling:

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Radius.xl),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline),
)
```

Hero styling must use `Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.primaryContainer)` and text colors from `onPrimaryContainer` / `onSurfaceVariant`.

If `briefing.points` is empty, render `CitationText` inside a styled fallback section under the hero.

- [ ] **Step 4: Compile after UI changes**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

## Task 3: Briefing Skeleton And Status Notice Polish

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`

- [ ] **Step 1: Update generating skeleton to match briefing card**

Replace `UnifiedNewsGeneratingSkeleton` with a large-radius card using:

```kotlin
shape = RoundedCornerShape(Radius.xl)
containerColor = MaterialTheme.colorScheme.surface
border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline)
```

Inside it, add a hero-like `Surface` with `surfaceContainer` and skeleton lines for metadata, title, lead, and three stats.

- [ ] **Step 2: Update refresh message shape and density**

Keep `UnifiedNewsRefreshMessage` behavior but align it with the briefing visual system:

```kotlin
Surface(
    modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.s),
    shape = RoundedCornerShape(Radius.l),
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline),
)
```

- [ ] **Step 3: Compile after polish**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

## Task 4: Verification And Device Install

**Files:**
- Verify only unless fixes are required.

- [ ] **Step 1: Run focused unit tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsBriefingContentTest`

Expected: PASS.

- [ ] **Step 2: Run required compile check**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run full debug assemble**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Install to connected device**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Expected: BUILD SUCCESSFUL and app installed.

- [ ] **Step 5: Launch app**

Run: `adb shell am start -n com.dailysatori/.MainActivity`

Expected: Android reports the activity start and the app opens.

- [ ] **Step 6: Manual visual check**

Open the news summary tab and verify:

- The first summary card has a premium briefing hero, not a plain markdown card.
- Font sizes, weights, spacing, chips, badges, and colors are visually close to the approved demo.
- Dark and light modes remain readable.
- Citation/source clicks still work.
- Remote source chips still switch to source article lists.

## Self-Review

Spec coverage:

- Briefing layout, parsing, light/dark theme roles, skeleton, notice, fallback, and verification are covered.

Placeholder scan:

- No TBD/TODO placeholders remain.

Type consistency:

- `UnifiedNewsBriefingContent`, `UnifiedNewsBriefingPoint`, and `unifiedNewsBriefingContent` are consistently named across tasks.
