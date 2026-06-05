# Book AI Reflection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add viewpoint-scoped AI reflection segments in the reading module, including chat history, manual distillation summaries, and history review.

**Architecture:** Store reading reflection data in dedicated SQLDelight tables linked to `book_viewpoint`. Add a shared repository and service layer for persistence and AI calls, then add an app ViewModel and Compose bottom sheet that are scoped to the currently displayed viewpoint. Reuse existing AI configuration and chat UI patterns, but do not reuse the global `AiChatViewModel` session model.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Koin, Jetpack Compose Material 3, Kotlin coroutines, Ktor/OpenAI-compatible AI service, JUnit tests.

---

## File Map

**Create**

- `shared/src/commonMain/kotlin/com/dailysatori/data/repository/BookViewpointAiRepository.kt`: persistence boundary for reflection sessions and messages.
- `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookReflectionService.kt`: prompt construction, AI response generation, and summary generation.
- `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt`: UI state, active segment selection, sending messages, retry, new segment, and summary actions.
- `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt`: bottom sheet content for reflection chat, summary card, history drawer, and input.
- `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt`: pure helper tests for state labels, prompt list, title derivation, and retry eligibility.
- `shared/src/commonTest/kotlin/com/dailysatori/service/book/BookReflectionServiceTest.kt`: prompt and summary-format tests.

**Modify**

- `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`: add reflection tables and SQLDelight queries.
- `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`: increment `DatabaseConfig.currentSchemaVersion` from `11L` to `12L`.
- `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`: add `migrateV11ToV12()` and wire it into `runMigrations()`.
- `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`: bind `BookViewpointAiRepository` and `BookReflectionService`.
- `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`: bind `BookReflectionViewModel`.
- `app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt`: add a single restrained `深入想想` action.
- `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`: open the reflection sheet for the current viewpoint.
- `app/src/test/kotlin/com/dailysatori/ui/feature/book/ViewpointCardLayoutTest.kt`: assert the card exposes the reflection action hook.
- `app/src/test/kotlin/com/dailysatori/ui/feature/book/BooksScreenUiTextTest.kt`: assert the user-facing reflection action text.

---

## Task 1: Reflection Pure Rules

**Files:**
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt`

- [ ] **Step 1: Write failing pure-state tests**

Create `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt` with these tests:

```kotlin
package com.dailysatori.ui.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookReflectionStateTest {
    @Test
    fun summaryActionTextUsesUpdateWhenSummaryExists() {
        assertEquals("沉淀这一段", bookReflectionSummaryActionText(summary = ""))
        assertEquals("更新沉淀", bookReflectionSummaryActionText(summary = "我理解到的核心：边界"))
    }

    @Test
    fun startingPromptsAreFocusedAndLimited() {
        assertEquals(
            listOf(
                "这个观点我可能漏掉了哪些角度？",
                "帮我用更具体的例子解释一下",
                "你反问我几个问题，帮我想清楚",
            ),
            bookReflectionStartingPrompts(),
        )
    }

    @Test
    fun titleFromQuestionTrimsAndLimitsLength() {
        assertEquals("这个观点为什么成立", bookReflectionTitleFromQuestion("  这个观点为什么成立？\n还能怎么理解  "))
        assertEquals("新的思考", bookReflectionTitleFromQuestion("   "))
        assertEquals("12345678901234567890", bookReflectionTitleFromQuestion("1234567890123456789012345"))
    }

    @Test
    fun titleFromSummaryCoreLineUsesCoreContent() {
        val summary = """
            我理解到的核心：真正的问题是把短期情绪当成长期判断。
            我补上的角度：需要区分感受和事实。
            还值得继续想的问题：我在哪些场景会这样？
        """.trimIndent()

        assertEquals("真正的问题是把短期情绪当成长期判断", bookReflectionTitleFromSummary(summary))
        assertEquals("新的思考", bookReflectionTitleFromSummary("没有固定结构"))
    }

    @Test
    fun retryIsAllowedOnlyForLatestFailedAssistantOrLatestUser() {
        val messages = listOf(
            BookReflectionMessageUi("1", "user", "问题一", 1L, "ready", ""),
            BookReflectionMessageUi("2", "assistant", "失败", 2L, "failed", "网络错误"),
        )
        assertTrue(bookReflectionCanRetryLatest(messages))

        val readyMessages = listOf(
            BookReflectionMessageUi("1", "user", "问题一", 1L, "ready", ""),
            BookReflectionMessageUi("2", "assistant", "回答", 2L, "ready", ""),
        )
        assertFalse(bookReflectionCanRetryLatest(readyMessages))

        val onlyUser = listOf(BookReflectionMessageUi("1", "user", "问题一", 1L, "ready", ""))
        assertTrue(bookReflectionCanRetryLatest(onlyUser))
    }
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.book.BookReflectionStateTest'
```

Expected: FAIL because `BookReflectionMessageUi`, `bookReflectionSummaryActionText`, `bookReflectionStartingPrompts`, `bookReflectionTitleFromQuestion`, `bookReflectionTitleFromSummary`, and `bookReflectionCanRetryLatest` do not exist.

- [ ] **Step 3: Add minimal pure helpers and state models**

Create `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt` with this initial content:

```kotlin
package com.dailysatori.ui.feature.book

import androidx.lifecycle.ViewModel

data class BookReflectionMessageUi(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val status: String,
    val errorMessage: String,
    val isStreaming: Boolean = false,
)

data class BookReflectionSessionUi(
    val id: Long,
    val viewpointId: Long,
    val title: String,
    val summary: String,
    val summaryStatus: String,
    val summaryError: String,
    val updatedAt: Long,
    val summarizedAt: Long?,
)

data class BookReflectionState(
    val viewpointId: Long? = null,
    val bookTitle: String = "",
    val author: String = "",
    val viewpointTitle: String = "",
    val viewpointContent: String = "",
    val viewpointExample: String = "",
    val activeSession: BookReflectionSessionUi? = null,
    val sessions: List<BookReflectionSessionUi> = emptyList(),
    val messages: List<BookReflectionMessageUi> = emptyList(),
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val isSummarizing: Boolean = false,
    val showHistory: Boolean = false,
    val error: String? = null,
)

class BookReflectionViewModel : ViewModel()

fun bookReflectionSummaryActionText(summary: String): String =
    if (summary.isBlank()) "沉淀这一段" else "更新沉淀"

fun bookReflectionStartingPrompts(): List<String> = listOf(
    "这个观点我可能漏掉了哪些角度？",
    "帮我用更具体的例子解释一下",
    "你反问我几个问题，帮我想清楚",
)

fun bookReflectionTitleFromQuestion(question: String): String {
    val firstLine = question.trim().lineSequence().firstOrNull()?.trim().orEmpty()
        .trimEnd('？', '?', '。', '.', '！', '!')
    return firstLine.take(20).ifBlank { "新的思考" }
}

fun bookReflectionTitleFromSummary(summary: String): String {
    val core = summary.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("我理解到的核心：") }
        ?.removePrefix("我理解到的核心：")
        ?.trim()
        ?.trimEnd('。', '.', '？', '?', '！', '!')
        .orEmpty()
    return core.take(20).ifBlank { "新的思考" }
}

fun bookReflectionCanRetryLatest(messages: List<BookReflectionMessageUi>): Boolean {
    val last = messages.lastOrNull() ?: return false
    return last.role == "user" || (last.role == "assistant" && last.status == "failed")
}
```

- [ ] **Step 4: Run tests and verify they pass**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.book.BookReflectionStateTest'
```

Expected: PASS.

- [ ] **Step 5: Commit Task 1**

```bash
git add "app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt" "app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt"
git commit -m "test: define book reflection state rules"
```

---

## Task 2: Database Schema, Migration, Repository

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/BookViewpointAiRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`

- [ ] **Step 1: Add failing source-structure test for schema and migration**

Append these tests to `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt`:

```kotlin
    @Test
    fun schemaDefinesBookReflectionTablesAndQueries() {
        val schema = java.io.File("../shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()

        assertTrue(schema.contains("CREATE TABLE book_viewpoint_ai_session"))
        assertTrue(schema.contains("CREATE TABLE book_viewpoint_ai_message"))
        assertTrue(schema.contains("selectBookReflectionSessionsByViewpoint:"))
        assertTrue(schema.contains("insertBookReflectionMessage:"))
        assertTrue(schema.contains("updateBookReflectionSummary:"))
    }

    @Test
    fun migrationDefinesVersionTwelveForBookReflection() {
        val config = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt").readText()
        val migration = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt").readText()

        assertTrue(config.contains("currentSchemaVersion = 12L"))
        assertTrue(migration.contains("if (currentVersion < 12)"))
        assertTrue(migration.contains("migrateV11ToV12()"))
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS book_viewpoint_ai_session"))
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS book_viewpoint_ai_message"))
    }
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.book.BookReflectionStateTest'
```

Expected: FAIL because schema and migration have not been added.

- [ ] **Step 3: Add SQLDelight tables and queries**

In `DailySatori.sq`, insert the tables after `book_viewpoint` and before `-- Diary`:

```sql
CREATE TABLE book_viewpoint_ai_session (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    viewpoint_id INTEGER NOT NULL REFERENCES book_viewpoint(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    summary TEXT NOT NULL DEFAULT '',
    summary_status TEXT NOT NULL DEFAULT 'none',
    summary_error TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    last_opened_at INTEGER NOT NULL,
    summarized_at INTEGER
);

CREATE TABLE book_viewpoint_ai_message (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id INTEGER NOT NULL REFERENCES book_viewpoint_ai_session(id) ON DELETE CASCADE,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ready',
    error_message TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL
);
```

Add these queries after `deleteViewpointsByBook:`:

```sql
-- Book viewpoint AI reflection queries
selectBookReflectionSessionsByViewpoint:
SELECT * FROM book_viewpoint_ai_session WHERE viewpoint_id = ? ORDER BY updated_at DESC;

selectLastOpenedBookReflectionSession:
SELECT * FROM book_viewpoint_ai_session WHERE viewpoint_id = ? ORDER BY last_opened_at DESC LIMIT 1;

selectLatestUnsummarizedBookReflectionSession:
SELECT * FROM book_viewpoint_ai_session
WHERE viewpoint_id = ? AND summary_status != 'ready'
ORDER BY updated_at DESC LIMIT 1;

selectBookReflectionSessionById:
SELECT * FROM book_viewpoint_ai_session WHERE id = ?;

insertBookReflectionSession:
INSERT INTO book_viewpoint_ai_session (
    viewpoint_id, title, summary, summary_status, summary_error,
    created_at, updated_at, last_opened_at, summarized_at
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);

selectLastInsertedBookReflectionSessionId:
SELECT id FROM book_viewpoint_ai_session ORDER BY id DESC LIMIT 1;

markBookReflectionSessionOpened:
UPDATE book_viewpoint_ai_session SET last_opened_at = ?, updated_at = ? WHERE id = ?;

updateBookReflectionSessionTitle:
UPDATE book_viewpoint_ai_session SET title = ?, updated_at = ? WHERE id = ?;

updateBookReflectionSummaryStatus:
UPDATE book_viewpoint_ai_session SET summary_status = ?, summary_error = ?, updated_at = ? WHERE id = ?;

updateBookReflectionSummary:
UPDATE book_viewpoint_ai_session
SET title = ?, summary = ?, summary_status = ?, summary_error = '', summarized_at = ?, updated_at = ?
WHERE id = ?;

deleteBookReflectionSession:
DELETE FROM book_viewpoint_ai_session WHERE id = ?;

selectBookReflectionMessagesBySession:
SELECT * FROM book_viewpoint_ai_message WHERE session_id = ? ORDER BY created_at ASC;

insertBookReflectionMessage:
INSERT INTO book_viewpoint_ai_message (session_id, role, content, status, error_message, created_at)
VALUES (?, ?, ?, ?, ?, ?);

selectLastInsertedBookReflectionMessageId:
SELECT id FROM book_viewpoint_ai_message ORDER BY id DESC LIMIT 1;

updateBookReflectionMessage:
UPDATE book_viewpoint_ai_message SET content = ?, status = ?, error_message = ? WHERE id = ?;

deleteBookReflectionMessagesBySession:
DELETE FROM book_viewpoint_ai_message WHERE session_id = ?;
```

- [ ] **Step 4: Add schema migration version 12**

In `Config.kt`, change:

```kotlin
const val currentSchemaVersion = 11L
```

to:

```kotlin
const val currentSchemaVersion = 12L
```

In `DatabaseMigration.runMigrations()`, after the version 11 block, add:

```kotlin
        if (currentVersion < 12) {
            migrateV11ToV12()
        }
```

Before `getCurrentVersion()`, add:

```kotlin
    private fun migrateV11ToV12() {
        log.i { "Migration V11 -> V12: Book viewpoint AI reflection tables" }
        try {
            runSql("""
                CREATE TABLE IF NOT EXISTS book_viewpoint_ai_session (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    viewpoint_id INTEGER NOT NULL REFERENCES book_viewpoint(id) ON DELETE CASCADE,
                    title TEXT NOT NULL,
                    summary TEXT NOT NULL DEFAULT '',
                    summary_status TEXT NOT NULL DEFAULT 'none',
                    summary_error TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    last_opened_at INTEGER NOT NULL,
                    summarized_at INTEGER
                )
            """.trimIndent())
            log.i { "Created book_viewpoint_ai_session table" }
        } catch (e: Exception) {
            log.w(e) { "Could not create book_viewpoint_ai_session table" }
        }

        try {
            runSql("""
                CREATE TABLE IF NOT EXISTS book_viewpoint_ai_message (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id INTEGER NOT NULL REFERENCES book_viewpoint_ai_session(id) ON DELETE CASCADE,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'ready',
                    error_message TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL
                )
            """.trimIndent())
            log.i { "Created book_viewpoint_ai_message table" }
        } catch (e: Exception) {
            log.w(e) { "Could not create book_viewpoint_ai_message table" }
        }
    }
```

- [ ] **Step 5: Add repository implementation**

Create `shared/src/commonMain/kotlin/com/dailysatori/data/repository/BookViewpointAiRepository.kt`:

```kotlin
package com.dailysatori.data.repository

import com.dailysatori.shared.db.Book_viewpoint_ai_message
import com.dailysatori.shared.db.Book_viewpoint_ai_session
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.datetime.Clock

class BookViewpointAiRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getSessionsByViewpoint(viewpointId: Long): List<Book_viewpoint_ai_session> =
        q.selectBookReflectionSessionsByViewpoint(viewpointId).executeAsList()

    fun getLastOpenedSession(viewpointId: Long): Book_viewpoint_ai_session? =
        q.selectLastOpenedBookReflectionSession(viewpointId).executeAsOneOrNull()

    fun getLatestUnsummarizedSession(viewpointId: Long): Book_viewpoint_ai_session? =
        q.selectLatestUnsummarizedBookReflectionSession(viewpointId).executeAsOneOrNull()

    fun getSessionById(sessionId: Long): Book_viewpoint_ai_session? =
        q.selectBookReflectionSessionById(sessionId).executeAsOneOrNull()

    fun createSession(viewpointId: Long, title: String = "新的思考", now: Long = now()): Long {
        q.insertBookReflectionSession(
            viewpoint_id = viewpointId,
            title = title,
            summary = "",
            summary_status = "none",
            summary_error = "",
            created_at = now,
            updated_at = now,
            last_opened_at = now,
            summarized_at = null,
        )
        return q.selectLastInsertedBookReflectionSessionId().executeAsOne()
    }

    fun markOpened(sessionId: Long, now: Long = now()) {
        q.markBookReflectionSessionOpened(last_opened_at = now, updated_at = now, id = sessionId)
    }

    fun updateTitle(sessionId: Long, title: String, now: Long = now()) {
        q.updateBookReflectionSessionTitle(title = title, updated_at = now, id = sessionId)
    }

    fun updateSummaryStatus(sessionId: Long, status: String, error: String = "", now: Long = now()) {
        q.updateBookReflectionSummaryStatus(summary_status = status, summary_error = error, updated_at = now, id = sessionId)
    }

    fun updateSummary(sessionId: Long, title: String, summary: String, now: Long = now()) {
        q.updateBookReflectionSummary(
            title = title,
            summary = summary,
            summary_status = "ready",
            summarized_at = now,
            updated_at = now,
            id = sessionId,
        )
    }

    fun deleteSession(sessionId: Long) = q.deleteBookReflectionSession(sessionId)

    fun getMessagesBySession(sessionId: Long): List<Book_viewpoint_ai_message> =
        q.selectBookReflectionMessagesBySession(sessionId).executeAsList()

    fun insertMessage(
        sessionId: Long,
        role: String,
        content: String,
        status: String = "ready",
        errorMessage: String = "",
        now: Long = now(),
    ): Long {
        q.insertBookReflectionMessage(sessionId, role, content, status, errorMessage, now)
        return q.selectLastInsertedBookReflectionMessageId().executeAsOne()
    }

    fun updateMessage(messageId: Long, content: String, status: String, errorMessage: String = "") {
        q.updateBookReflectionMessage(content = content, status = status, error_message = errorMessage, id = messageId)
    }

    fun deleteMessagesBySession(sessionId: Long) = q.deleteBookReflectionMessagesBySession(sessionId)

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
}
```

- [ ] **Step 6: Register repository in DI**

In `SharedModule.kt`, add import:

```kotlin
import com.dailysatori.data.repository.BookViewpointAiRepository
```

Add repository binding near `BookViewpointRepository`:

```kotlin
single { BookViewpointAiRepository(get()) }
```

- [ ] **Step 7: Run SQLDelight generation and tests**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:generateCommonMainDailySatoriDatabaseInterface :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.book.BookReflectionStateTest'
```

Expected: PASS.

- [ ] **Step 8: Commit Task 2**

```bash
git add "shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq" "shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt" "shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt" "shared/src/commonMain/kotlin/com/dailysatori/data/repository/BookViewpointAiRepository.kt" "shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt" "app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt"
git commit -m "feat: add book reflection persistence"
```

---

## Task 3: AI Reflection Service

**Files:**
- Create: `shared/src/commonTest/kotlin/com/dailysatori/service/book/BookReflectionServiceTest.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookReflectionService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`

- [ ] **Step 1: Write failing prompt tests**

Create `shared/src/commonTest/kotlin/com/dailysatori/service/book/BookReflectionServiceTest.kt`:

```kotlin
package com.dailysatori.service.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookReflectionServiceTest {
    @Test
    fun userPromptIncludesViewpointContextAndExistingSummaries() {
        val prompt = buildBookReflectionUserPrompt(
            bookTitle = "反脆弱",
            author = "塔勒布",
            viewpointTitle = "压力让系统暴露真实结构",
            viewpointContent = "系统在压力下会显露隐藏脆弱点。",
            viewpointExample = "健身通过负荷暴露身体短板。",
            existingSummaries = listOf("我理解到的核心：压力不是坏事。"),
            recentMessages = listOf(BookReflectionPromptMessage("user", "这个点和拖延有什么关系？")),
            userQuestion = "我还是不理解为什么压力有价值",
        )

        assertTrue(prompt.contains("书名：反脆弱"))
        assertTrue(prompt.contains("作者：塔勒布"))
        assertTrue(prompt.contains("当前观点标题：压力让系统暴露真实结构"))
        assertTrue(prompt.contains("系统在压力下会显露隐藏脆弱点。"))
        assertTrue(prompt.contains("我理解到的核心：压力不是坏事。"))
        assertTrue(prompt.contains("user：这个点和拖延有什么关系？"))
        assertTrue(prompt.contains("用户本次问题：我还是不理解为什么压力有价值"))
    }

    @Test
    fun summarySystemPromptRequiresFixedStructure() {
        val prompt = bookReflectionSummarySystemPrompt()

        assertTrue(prompt.contains("我理解到的核心："))
        assertTrue(prompt.contains("我补上的角度："))
        assertTrue(prompt.contains("还值得继续想的问题："))
    }

    @Test
    fun aiNotConfiguredMessageIsStable() {
        assertEquals("AI 服务未配置，请先在设置中配置 AI 接口", bookReflectionAiNotConfiguredMessage())
    }
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:allTests --tests 'com.dailysatori.service.book.BookReflectionServiceTest'
```

Expected: FAIL because the service helpers do not exist.

- [ ] **Step 3: Add service and prompt helpers**

Create `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookReflectionService.kt`:

```kotlin
package com.dailysatori.service.book

import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class BookReflectionPromptMessage(
    val role: String,
    val content: String,
)

data class BookReflectionAiResult(
    val content: String,
)

class BookReflectionService(
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
) {
    suspend fun answer(
        bookTitle: String,
        author: String,
        viewpointTitle: String,
        viewpointContent: String,
        viewpointExample: String,
        existingSummaries: List<String>,
        recentMessages: List<BookReflectionPromptMessage>,
        userQuestion: String,
        onChunk: suspend (String) -> Unit,
    ): BookReflectionAiResult {
        val config = aiConfigService.getDefaultConfig()
            ?: return BookReflectionAiResult(bookReflectionAiNotConfiguredMessage())
        if (config.api_address.isBlank() || config.api_token.isBlank()) {
            return BookReflectionAiResult(bookReflectionAiNotConfiguredMessage())
        }

        val messages = listOf(
            buildJsonObject {
                put("role", "system")
                put("content", bookReflectionAnswerSystemPrompt())
            },
            buildJsonObject {
                put("role", "user")
                put("content", buildBookReflectionUserPrompt(
                    bookTitle = bookTitle,
                    author = author,
                    viewpointTitle = viewpointTitle,
                    viewpointContent = viewpointContent,
                    viewpointExample = viewpointExample,
                    existingSummaries = existingSummaries,
                    recentMessages = recentMessages,
                    userQuestion = userQuestion,
                ))
            },
        )
        val response = aiService.chatCompletionStreaming(
            messages = messages,
            apiAddress = config.api_address,
            apiToken = config.api_token,
            modelName = config.model_name,
            provider = config.provider,
            temperature = 0.5,
            onChunk = onChunk,
        )
        val content = response?.get("choices")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject?.get("content")
            ?.jsonPrimitive?.contentOrNull.orEmpty()
        return BookReflectionAiResult(content.ifBlank { bookReflectionBlankResponseMessage() })
    }

    suspend fun summarize(
        bookTitle: String,
        viewpointTitle: String,
        messages: List<BookReflectionPromptMessage>,
    ): String {
        val config = aiConfigService.getDefaultConfig()
            ?: throw IllegalStateException(bookReflectionAiNotConfiguredMessage())
        if (config.api_address.isBlank() || config.api_token.isBlank()) {
            throw IllegalStateException(bookReflectionAiNotConfiguredMessage())
        }
        val content = buildString {
            appendLine("书名：$bookTitle")
            appendLine("观点：$viewpointTitle")
            appendLine("交流过程：")
            messages.forEach { appendLine("${it.role}：${it.content}") }
        }
        return aiService.complete(
            prompt = content,
            apiAddress = config.api_address,
            apiToken = config.api_token,
            modelName = config.model_name,
            provider = config.provider,
            systemPrompt = bookReflectionSummarySystemPrompt(),
            temperature = 0.3,
        ).trim().ifBlank { bookReflectionBlankSummaryMessage() }
    }
}

fun bookReflectionAnswerSystemPrompt(): String = """
你是 Daily Satori 的读书思考助手。你的任务不是泛泛聊天，而是帮助用户把当前读书观点想透。
回答要求：
1. 先用简短语言解释核心点。
2. 补充 2 到 3 个用户可能漏掉的角度。
3. 最后提出 1 到 2 个反问，帮助用户继续思考。
4. 默认保持简洁，不要写成长文。
""".trimIndent()

fun bookReflectionSummarySystemPrompt(): String = """
你要把一段围绕读书观点的交流沉淀成用户下次容易回看的总结。
不要只总结 AI 说了什么，要提炼用户这段思考推进到了哪里。
必须使用以下固定结构：
我理解到的核心：
我补上的角度：
还值得继续想的问题：
""".trimIndent()

fun buildBookReflectionUserPrompt(
    bookTitle: String,
    author: String,
    viewpointTitle: String,
    viewpointContent: String,
    viewpointExample: String,
    existingSummaries: List<String>,
    recentMessages: List<BookReflectionPromptMessage>,
    userQuestion: String,
): String = buildString {
    appendLine("书名：$bookTitle")
    appendLine("作者：$author")
    appendLine("当前观点标题：$viewpointTitle")
    appendLine("观点正文：$viewpointContent")
    appendLine("观点例子：$viewpointExample")
    appendLine("已有片段总结：")
    if (existingSummaries.isEmpty()) appendLine("无") else existingSummaries.forEach { appendLine(it) }
    appendLine("当前片段最近消息：")
    if (recentMessages.isEmpty()) appendLine("无") else recentMessages.forEach { appendLine("${it.role}：${it.content}") }
    appendLine("用户本次问题：$userQuestion")
}

fun bookReflectionAiNotConfiguredMessage(): String = "AI 服务未配置，请先在设置中配置 AI 接口"
fun bookReflectionBlankResponseMessage(): String = "这次没有生成有效回复，请稍后重试。"
fun bookReflectionBlankSummaryMessage(): String = "我理解到的核心：这段交流还没有形成清晰结论。\n我补上的角度：可以继续补充具体例子和反面情况。\n还值得继续想的问题：这个观点和我的真实经验有什么关系？"
```

- [ ] **Step 4: Register service in DI**

In `SharedModule.kt`, add import:

```kotlin
import com.dailysatori.service.book.BookReflectionService
```

Add binding near book services:

```kotlin
single { BookReflectionService(get(), get()) }
```

- [ ] **Step 5: Run shared tests**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:allTests --tests 'com.dailysatori.service.book.BookReflectionServiceTest'
```

Expected: PASS.

- [ ] **Step 6: Commit Task 3**

```bash
git add "shared/src/commonTest/kotlin/com/dailysatori/service/book/BookReflectionServiceTest.kt" "shared/src/commonMain/kotlin/com/dailysatori/service/book/BookReflectionService.kt" "shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt"
git commit -m "feat: add book reflection AI service"
```

---

## Task 4: ViewModel Behavior

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt`

- [ ] **Step 1: Add source-boundary tests for ViewModel dependencies and methods**

Append these tests to `BookReflectionStateTest.kt`:

```kotlin
    @Test
    fun viewModelHasReadingReflectionActions() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt").readText()

        assertTrue(source.contains("fun openViewpoint("))
        assertTrue(source.contains("fun sendMessage("))
        assertTrue(source.contains("fun createNewSegment("))
        assertTrue(source.contains("fun generateSummary("))
        assertTrue(source.contains("fun retryLatest("))
        assertTrue(source.contains("BookViewpointAiRepository"))
        assertTrue(source.contains("BookReflectionService"))
    }

    @Test
    fun viewModelIsRegisteredInKoin() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt").readText()

        assertTrue(source.contains("BookReflectionViewModel"))
        assertTrue(source.contains("reflectionRepo = get<BookViewpointAiRepository>()"))
        assertTrue(source.contains("reflectionService = get<BookReflectionService>()"))
    }
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.book.BookReflectionStateTest'
```

Expected: FAIL because ViewModel methods and DI binding are missing.

- [ ] **Step 3: Replace stub ViewModel with functional implementation**

Update `BookReflectionViewModel.kt` to keep the pure helpers from Task 1 and replace `class BookReflectionViewModel : ViewModel()` with this implementation:

```kotlin
class BookReflectionViewModel(
    private val reflectionRepo: com.dailysatori.data.repository.BookViewpointAiRepository,
    private val reflectionService: com.dailysatori.service.book.BookReflectionService,
) : ViewModel() {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(BookReflectionState())
    val state: kotlinx.coroutines.flow.StateFlow<BookReflectionState> = _state.asStateFlow()
    private var activeJob: kotlinx.coroutines.Job? = null

    fun openViewpoint(
        viewpointId: Long,
        bookTitle: String,
        author: String,
        viewpointTitle: String,
        viewpointContent: String,
        viewpointExample: String,
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            val session = reflectionRepo.getLastOpenedSession(viewpointId)
                ?: reflectionRepo.getLatestUnsummarizedSession(viewpointId)
                ?: reflectionRepo.createSession(viewpointId).let { reflectionRepo.getSessionById(it)!! }
            reflectionRepo.markOpened(session.id)
            val sessions = reflectionRepo.getSessionsByViewpoint(viewpointId).map(::toSessionUi)
            val messages = reflectionRepo.getMessagesBySession(session.id).map(::toMessageUi)
            _state.update {
                it.copy(
                    viewpointId = viewpointId,
                    bookTitle = bookTitle,
                    author = author,
                    viewpointTitle = viewpointTitle,
                    viewpointContent = viewpointContent,
                    viewpointExample = viewpointExample,
                    activeSession = toSessionUi(session),
                    sessions = sessions,
                    messages = messages,
                    isLoading = false,
                )
            }
        }
    }

    fun sendMessage(content: String) {
        val question = content.trim()
        val snapshot = _state.value
        val session = snapshot.activeSession ?: return
        if (question.isBlank() || snapshot.isProcessing) return
        activeJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val userMessageId = reflectionRepo.insertMessage(session.id, "user", question)
            if (snapshot.messages.none { it.role == "user" }) {
                reflectionRepo.updateTitle(session.id, bookReflectionTitleFromQuestion(question))
            }
            reloadActiveSession(session.id)
            _state.update { it.copy(isProcessing = true) }
            val assistantId = reflectionRepo.insertMessage(session.id, "assistant", "", status = "streaming")
            val streamed = StringBuilder()
            try {
                val result = reflectionService.answer(
                    bookTitle = snapshot.bookTitle,
                    author = snapshot.author,
                    viewpointTitle = snapshot.viewpointTitle,
                    viewpointContent = snapshot.viewpointContent,
                    viewpointExample = snapshot.viewpointExample,
                    existingSummaries = snapshot.sessions.mapNotNull { it.summary.takeIf(String::isNotBlank) },
                    recentMessages = reflectionRepo.getMessagesBySession(session.id).takeLast(12).map {
                        com.dailysatori.service.book.BookReflectionPromptMessage(it.role, it.content)
                    },
                    userQuestion = question,
                    onChunk = { chunk ->
                        streamed.append(chunk)
                        reflectionRepo.updateMessage(assistantId, streamed.toString(), "streaming")
                        reloadMessages(session.id)
                    },
                )
                reflectionRepo.updateMessage(assistantId, result.content, "ready")
            } catch (error: Exception) {
                reflectionRepo.updateMessage(
                    messageId = assistantId,
                    content = "AI 回复失败，请稍后重试。",
                    status = "failed",
                    errorMessage = error.message.orEmpty(),
                )
            } finally {
                reloadActiveSession(session.id)
                _state.update { it.copy(isProcessing = false) }
                activeJob = null
            }
        }
    }

    fun createNewSegment() {
        val viewpointId = _state.value.viewpointId ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val sessionId = reflectionRepo.createSession(viewpointId)
            reloadActiveSession(sessionId)
        }
    }

    fun generateSummary() {
        val snapshot = _state.value
        val session = snapshot.activeSession ?: return
        if (snapshot.isSummarizing || snapshot.messages.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _state.update { it.copy(isSummarizing = true) }
            reflectionRepo.updateSummaryStatus(session.id, "generating")
            try {
                val messages = reflectionRepo.getMessagesBySession(session.id).map {
                    com.dailysatori.service.book.BookReflectionPromptMessage(it.role, it.content)
                }
                val summary = reflectionService.summarize(snapshot.bookTitle, snapshot.viewpointTitle, messages)
                reflectionRepo.updateSummary(session.id, bookReflectionTitleFromSummary(summary), summary)
            } catch (error: Exception) {
                reflectionRepo.updateSummaryStatus(session.id, "failed", error.message.orEmpty())
            } finally {
                reloadActiveSession(session.id)
                _state.update { it.copy(isSummarizing = false) }
            }
        }
    }

    fun retryLatest() {
        val messages = _state.value.messages
        if (!bookReflectionCanRetryLatest(messages)) return
        val latestQuestion = messages.asReversed().firstOrNull { it.role == "user" }?.content ?: return
        sendMessage(latestQuestion)
    }

    fun toggleHistory() {
        _state.update { it.copy(showHistory = !it.showHistory) }
    }

    fun selectSession(sessionId: Long) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            reflectionRepo.markOpened(sessionId)
            reloadActiveSession(sessionId)
        }
    }

    fun stopGeneration() {
        activeJob?.cancel()
        activeJob = null
        _state.update { it.copy(isProcessing = false) }
    }

    private fun reloadActiveSession(sessionId: Long) {
        val session = reflectionRepo.getSessionById(sessionId) ?: return
        val sessions = reflectionRepo.getSessionsByViewpoint(session.viewpoint_id).map(::toSessionUi)
        val messages = reflectionRepo.getMessagesBySession(sessionId).map(::toMessageUi)
        _state.update { it.copy(activeSession = toSessionUi(session), sessions = sessions, messages = messages, isLoading = false) }
    }

    private fun reloadMessages(sessionId: Long) {
        val messages = reflectionRepo.getMessagesBySession(sessionId).map(::toMessageUi)
        _state.update { it.copy(messages = messages) }
    }
}

private fun toSessionUi(session: com.dailysatori.shared.db.Book_viewpoint_ai_session): BookReflectionSessionUi =
    BookReflectionSessionUi(
        id = session.id,
        viewpointId = session.viewpoint_id,
        title = session.title,
        summary = session.summary,
        summaryStatus = session.summary_status,
        summaryError = session.summary_error,
        updatedAt = session.updated_at,
        summarizedAt = session.summarized_at,
    )

private fun toMessageUi(message: com.dailysatori.shared.db.Book_viewpoint_ai_message): BookReflectionMessageUi =
    BookReflectionMessageUi(
        id = message.id.toString(),
        role = message.role,
        content = message.content,
        createdAt = message.created_at,
        status = message.status,
        errorMessage = message.error_message,
        isStreaming = message.status == "streaming",
    )
```

Keep the helper functions and data classes from Task 1 in the same file.

- [ ] **Step 4: Register ViewModel in Koin**

In `ViewModelModule.kt`, add imports:

```kotlin
import com.dailysatori.data.repository.BookViewpointAiRepository
import com.dailysatori.service.book.BookReflectionService
import com.dailysatori.ui.feature.book.BookReflectionViewModel
```

Add binding after `BookContentSearchViewModel`:

```kotlin
    viewModel {
        BookReflectionViewModel(
            reflectionRepo = get<BookViewpointAiRepository>(),
            reflectionService = get<BookReflectionService>(),
        )
    }
```

- [ ] **Step 5: Run tests**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.book.BookReflectionStateTest'
```

Expected: PASS.

- [ ] **Step 6: Commit Task 4**

```bash
git add "app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt" "app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt" "app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt"
git commit -m "feat: add book reflection view model"
```

---

## Task 5: Reflection Sheet UI

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt`

- [ ] **Step 1: Add source tests for UI text and actions**

Append these tests to `BookReflectionStateTest.kt`:

```kotlin
    @Test
    fun reflectionSheetUsesRequiredUserFacingLabels() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt").readText()

        assertTrue(source.contains("深入想想"))
        assertTrue(source.contains("沉淀这一段"))
        assertTrue(source.contains("更新沉淀"))
        assertTrue(source.contains("换个角度聊"))
        assertTrue(source.contains("历史"))
        assertTrue(source.contains("查看过程"))
        assertTrue(source.contains("继续聊"))
    }
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.book.BookReflectionStateTest'
```

Expected: FAIL because `BookReflectionSheet.kt` does not exist.

- [ ] **Step 3: Create reflection sheet UI**

Create `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt`:

```kotlin
package com.dailysatori.ui.feature.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.feature.aichat.ChatInputField
import com.dailysatori.ui.feature.aichat.ChatMessageUi
import com.dailysatori.ui.feature.aichat.MessageBubble
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@Composable
fun BookReflectionSheet(
    state: BookReflectionState,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPromptClick: (String) -> Unit,
    onGenerateSummary: () -> Unit,
    onNewSegment: () -> Unit,
    onToggleHistory: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onRetryLatest: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        BookReflectionHeader(state)
        state.activeSession?.takeIf { it.summary.isNotBlank() }?.let { BookReflectionSummaryCard(it) }
        BookReflectionActions(
            summary = state.activeSession?.summary.orEmpty(),
            isSummarizing = state.isSummarizing,
            showHistory = state.showHistory,
            onGenerateSummary = onGenerateSummary,
            onNewSegment = onNewSegment,
            onToggleHistory = onToggleHistory,
        )
        if (state.showHistory) {
            BookReflectionHistory(state.sessions, onSelectSession)
        } else {
            BookReflectionMessages(state, onPromptClick, onRetryLatest)
        }
        ChatInputField(
            inputText = inputText,
            onInputChange = onInputChange,
            onSend = onSend,
            onStop = onStop,
            isProcessing = state.isProcessing,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BookReflectionHeader(state: BookReflectionState) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("深入想想", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(state.viewpointTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("《${state.bookTitle}》", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = if (expanded) "收起观点" else "展开观点",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { expanded = !expanded },
        )
        if (expanded) {
            Text(state.viewpointContent, style = MaterialTheme.typography.bodySmall)
            if (state.viewpointExample.isNotBlank()) Text(state.viewpointExample, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BookReflectionActions(
    summary: String,
    isSummarizing: Boolean,
    showHistory: Boolean,
    onGenerateSummary: () -> Unit,
    onNewSegment: () -> Unit,
    onToggleHistory: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Button(onClick = onGenerateSummary, enabled = !isSummarizing) {
            Text(if (isSummarizing) "沉淀中" else bookReflectionSummaryActionText(summary))
        }
        OutlinedButton(onClick = onNewSegment) { Text("换个角度聊") }
        OutlinedButton(onClick = onToggleHistory) { Text(if (showHistory) "当前" else "历史") }
    }
}

@Composable
private fun BookReflectionSummaryCard(session: BookReflectionSessionUi) {
    Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("已沉淀", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(session.summary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BookReflectionMessages(
    state: BookReflectionState,
    onPromptClick: (String) -> Unit,
    onRetryLatest: () -> Unit,
) {
    if (state.messages.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            bookReflectionStartingPrompts().forEach { prompt ->
                AssistChip(onClick = { onPromptClick(prompt) }, label = { Text(prompt) })
            }
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        items(state.messages, key = { it.id }) { message ->
            MessageBubble(message = message.toChatMessageUi())
        }
        if (bookReflectionCanRetryLatest(state.messages)) {
            item { OutlinedButton(onClick = onRetryLatest) { Text("重新生成") } }
        }
    }
}

@Composable
private fun BookReflectionHistory(
    sessions: List<BookReflectionSessionUi>,
    onSelectSession: (Long) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        items(sessions, key = { it.id }) { session ->
            Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(session.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(session.summary.ifBlank { "还没有沉淀" }, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        OutlinedButton(onClick = { onSelectSession(session.id) }) { Text("继续聊") }
                        OutlinedButton(onClick = { onSelectSession(session.id) }) { Text("查看过程") }
                    }
                }
            }
        }
    }
}

private fun BookReflectionMessageUi.toChatMessageUi(): ChatMessageUi = ChatMessageUi(
    id = id,
    role = role,
    content = if (status == "failed" && errorMessage.isNotBlank()) errorMessage else content,
    timestamp = createdAt,
    isError = status == "failed",
    isStreaming = isStreaming,
)
```

- [ ] **Step 4: Run tests**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.book.BookReflectionStateTest'
```

Expected: PASS.

- [ ] **Step 5: Commit Task 5**

```bash
git add "app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt" "app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt"
git commit -m "feat: add book reflection sheet"
```

---

## Task 6: Reading Screen Integration And Verification

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/ViewpointCardLayoutTest.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BooksScreenUiTextTest.kt`

- [ ] **Step 1: Add or update tests for entry text**

In `BooksScreenUiTextTest.kt`, add:

```kotlin
    @Test
    fun booksReflectionActionTextIsRestrained() {
        assertEquals("深入想想", booksReflectionActionText())
    }
```

In `ViewpointCardLayoutTest.kt`, add:

```kotlin
    @Test
    fun viewpointCardCanShowReflectionAction() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()

        assertTrue(source.contains("onReflect"))
        assertTrue(source.contains("booksReflectionActionText()"))
    }
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.book.BooksScreenUiTextTest' --tests 'com.dailysatori.ui.feature.book.ViewpointCardLayoutTest'
```

Expected: FAIL because reflection action is not wired.

- [ ] **Step 3: Add `ViewpointCard` reflection action**

In `ViewpointCard.kt`, add imports:

```kotlin
import androidx.compose.material3.OutlinedButton
```

Add parameter to `ViewpointCard`:

```kotlin
    onReflect: () -> Unit = {},
```

Inside the `else -> ViewpointBody(...)` branch, change it to:

```kotlin
                else -> {
                    ViewpointBody(content = content, example = example)
                    OutlinedButton(onClick = onReflect, modifier = Modifier.align(Alignment.End)) {
                        Text(booksReflectionActionText())
                    }
                }
```

If `Modifier.align(Alignment.End)` is not accepted in that scope, wrap the button in a `Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End)`.

- [ ] **Step 4: Wire sheet in `BooksScreen`**

In `BooksScreen.kt`, create ViewModel and state near the existing ViewModels:

```kotlin
    val reflectionViewModel: BookReflectionViewModel = koinViewModel()
    val reflectionState by reflectionViewModel.state.collectAsState()
    var showReflectionSheet by remember { mutableStateOf(false) }
    var reflectionInput by remember { mutableStateOf("") }
```

Pass `onReflect` to `ViewpointCard`:

```kotlin
                            onReflect = {
                                reflectionViewModel.openViewpoint(
                                    viewpointId = vp.id,
                                    bookTitle = currentBook?.title.orEmpty(),
                                    author = currentBook?.author.orEmpty(),
                                    viewpointTitle = vp.title,
                                    viewpointContent = vp.content,
                                    viewpointExample = vp.example,
                                )
                                showReflectionSheet = true
                            },
```

Add a modal sheet after the existing inline-mode sheet block:

```kotlin
    if (showReflectionSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showReflectionSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
            tonalElevation = 0.dp,
        ) {
            BookReflectionSheet(
                state = reflectionState,
                inputText = reflectionInput,
                onInputChange = { reflectionInput = it },
                onSend = {
                    if (reflectionInput.isNotBlank()) {
                        reflectionViewModel.sendMessage(reflectionInput)
                        reflectionInput = ""
                    }
                },
                onStop = reflectionViewModel::stopGeneration,
                onPromptClick = { prompt ->
                    reflectionInput = prompt
                    reflectionViewModel.sendMessage(prompt)
                    reflectionInput = ""
                },
                onGenerateSummary = reflectionViewModel::generateSummary,
                onNewSegment = reflectionViewModel::createNewSegment,
                onToggleHistory = reflectionViewModel::toggleHistory,
                onSelectSession = reflectionViewModel::selectSession,
                onRetryLatest = reflectionViewModel::retryLatest,
            )
        }
    }
```

Add helper near other text helpers:

```kotlin
fun booksReflectionActionText(): String = "深入想想"
```

- [ ] **Step 5: Run focused tests**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.book.BooksScreenUiTextTest' --tests 'com.dailysatori.ui.feature.book.ViewpointCardLayoutTest' --tests 'com.dailysatori.ui.feature.book.BookReflectionStateTest'
```

Expected: PASS.

- [ ] **Step 6: Run required compile/build verification**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:assembleDebug
```

Expected: both commands succeed with no Kotlin compile errors.

- [ ] **Step 7: Install and launch on connected device**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: app installs and launches. Manually verify that a reading viewpoint shows `深入想想`, opens the reflection sheet, can create/send a segment, and can trigger `沉淀这一段` when AI is configured.

- [ ] **Step 8: Commit Task 6**

```bash
git add "app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt" "app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt" "app/src/test/kotlin/com/dailysatori/ui/feature/book/ViewpointCardLayoutTest.kt" "app/src/test/kotlin/com/dailysatori/ui/feature/book/BooksScreenUiTextTest.kt"
git commit -m "feat: add book reflection entry"
```

---

## Final Verification

- [ ] Run all focused tests:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.book.*' :shared:allTests --tests 'com.dailysatori.service.book.BookReflectionServiceTest'
```

Expected: PASS.

- [ ] Run required project checks:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:assembleDebug
```

Expected: PASS.

- [ ] Install and launch:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: install and launch succeed.

- [ ] Manual smoke checklist:

- Open reading tab.
- Open a viewpoint.
- Tap `深入想想`.
- Confirm the sheet shows viewpoint title, book title, three starting prompts, and input placeholder behavior.
- Send a question.
- Confirm user message is preserved even if AI fails.
- Tap `沉淀这一段`.
- Confirm a summary card appears or failure status does not block chat.
- Tap `换个角度聊` and confirm a new empty segment appears.
- Tap `历史` and confirm summaries are shown before raw chat.
