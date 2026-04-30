# AI Chat Memory System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a three-tier memory system (core/content/chat) for the AI assistant with ChatGPT-style UI, auto-extraction of content summaries, and persistent chat history.

**Architecture:** New SQLDelight tables (`memory_entry`, `chat_conversation`) with LIKE-based search. New `MemoryRepository` + `MemoryExtractService` for auto-summary extraction. Extended `McpAgentService` with memory tools. Redesigned `AiChatScreen` with unified pill input box + Markdown rendering + memory search BottomSheet.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose, SQLDelight, Koin, multiplatform-markdown-renderer, kotlinx-datetime

---

### Task 1: Database Schema — memory_entry + chat_conversation

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`

- [ ] **Step 1: Add memory_entry table, chat_conversation table, and queries**

Append to `DailySatori.sq` at the end of the file (after line 427):

```sql
-- Memory Entry
CREATE TABLE memory_entry (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL,
    source_type TEXT,
    source_id INTEGER,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    tags TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- Chat Conversation
CREATE TABLE chat_conversation (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    search_results TEXT,
    steps TEXT,
    created_at INTEGER NOT NULL
);

-- Memory queries
selectMemoryByType:
SELECT * FROM memory_entry WHERE type = ? ORDER BY created_at DESC;

selectMemoryBySource:
SELECT * FROM memory_entry WHERE source_type = ? AND source_id = ?;

searchMemory:
SELECT * FROM memory_entry WHERE title LIKE '%' || ? || '%' OR content LIKE '%' || ? || '%' ORDER BY created_at DESC LIMIT ?;

selectAllMemory:
SELECT * FROM memory_entry ORDER BY created_at DESC;

insertMemory:
INSERT INTO memory_entry (type, source_type, source_id, title, content, tags, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);

updateMemory:
UPDATE memory_entry SET title = ?, content = ?, tags = ?, updated_at = ? WHERE id = ?;

deleteMemory:
DELETE FROM memory_entry WHERE id = ?;

deleteMemoryBySource:
DELETE FROM memory_entry WHERE source_type = ? AND source_id = ?;

deleteAllMemoryByType:
DELETE FROM memory_entry WHERE type = ?;

memoryCountByType:
SELECT COUNT(*) FROM memory_entry WHERE type = ?;

-- Chat conversation queries
selectChatBySession:
SELECT * FROM chat_conversation WHERE session_id = ? ORDER BY created_at ASC;

insertChat:
INSERT INTO chat_conversation (session_id, role, content, search_results, steps, created_at)
VALUES (?, ?, ?, ?, ?, ?);

deleteChatBySession:
DELETE FROM chat_conversation WHERE session_id = ?;

selectChatSessions:
SELECT DISTINCT session_id FROM chat_conversation ORDER BY session_id DESC;
```

- [ ] **Step 2: Run SQLDelight code generation**

```bash
./gradlew :shared:generateCommonMainDailySatoriInterface
```

Expected: Generated `Memory_entry`, `Chat_conversation` data classes in `shared/build/generated/`. Verify no compilation errors.

- [ ] **Step 3: Build to verify schema**

```bash
./gradlew :shared:compileKotlinAndroid
```

Expected: No errors from generated queries.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq
git commit -m "feat: add memory_entry and chat_conversation tables with queries"
```

---

### Task 2: MemoryRepository + ChatConversationRepository

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/MemoryRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ChatConversationRepository.kt`

- [ ] **Step 1: Create MemoryRepository**

Create `shared/src/commonMain/kotlin/com/dailysatori/data/repository/MemoryRepository.kt`:

```kotlin
package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Memory_entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getByType(type: String): Flow<List<Memory_entry>> =
        q.selectMemoryByType(type).asFlow().mapToList(Dispatchers.IO)

    fun getBySource(sourceType: String, sourceId: Long): Memory_entry? =
        q.selectMemoryBySource(sourceType, sourceId).executeAsOneOrNull()

    fun search(query: String, limit: Long = 10): List<Memory_entry> =
        q.searchMemory(query, query, limit).executeAsList()

    fun getAllSync(): List<Memory_entry> =
        q.selectAllMemory().executeAsList()

    fun countByType(type: String): Long =
        q.memoryCountByType(type).executeAsOne()

    fun insert(
        type: String,
        sourceType: String?,
        sourceId: Long?,
        title: String,
        content: String,
        tags: String? = null,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertMemory(type, sourceType, sourceId, title, content, tags, now, now)
    }

    fun update(id: Long, title: String, content: String, tags: String?) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateMemory(title, content, tags, now, id)
    }

    fun delete(id: Long) = q.deleteMemory(id)

    fun deleteBySource(sourceType: String, sourceId: Long) =
        q.deleteMemoryBySource(sourceType, sourceId)

    fun deleteAllByType(type: String) =
        q.deleteAllMemoryByType(type)
}
```

- [ ] **Step 2: Create ChatConversationRepository**

Create `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ChatConversationRepository.kt`:

```kotlin
package com.dailysatori.data.repository

import com.dailysatori.shared.db.Chat_conversation
import com.dailysatori.shared.db.DailySatoriDatabase

class ChatConversationRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getBySession(sessionId: String): List<Chat_conversation> =
        q.selectChatBySession(sessionId).executeAsList()

    fun getSessions(): List<String> =
        q.selectChatSessions().executeAsList().mapNotNull { it.session_id }

    fun insert(
        sessionId: String,
        role: String,
        content: String,
        searchResults: String? = null,
        steps: String? = null,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertChat(sessionId, role, content, searchResults, steps, now)
    }

    fun deleteBySession(sessionId: String) =
        q.deleteChatBySession(sessionId)
}
```

- [ ] **Step 3: Build to verify**

```bash
./gradlew :shared:compileKotlinAndroid
```

Expected: No errors.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/data/repository/MemoryRepository.kt shared/src/commonMain/kotlin/com/dailysatori/data/repository/ChatConversationRepository.kt
git commit -m "feat: add MemoryRepository and ChatConversationRepository"
```

---

### Task 3: MemoryExtractService

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/memory/MemoryExtractService.kt`

- [ ] **Step 1: Create MemoryExtractService**

Create directory and file `shared/src/commonMain/kotlin/com/dailysatori/service/memory/MemoryExtractService.kt`:

```kotlin
package com.dailysatori.service.memory

import co.touchlab.kermit.Logger
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.MemoryRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.jsonPrimitive

class MemoryExtractService(
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
    private val memoryRepo: MemoryRepository,
) {
    private val log = Logger.withTag("MemoryExtract")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Extract a summary from source content and save as memory_entry(type='content').
     */
    suspend fun extractAndSave(
        sourceType: String,
        sourceId: Long,
        title: String,
        content: String,
    ) {
        try {
            val config = aiConfigService.getDefaultConfig() ?: return
            if (config.api_address.isBlank()) return

            val truncatedContent = if (content.length > 3000) content.take(3000) + "..." else content

            val messages = listOf(
                buildJsonObject {
                    put("role", "system")
                    put("content", buildSystemPrompt())
                },
                buildJsonObject {
                    put("role", "user")
                    put("content", "请将以下内容提取为一条简洁的记忆摘要（不超过200字）。\n\n标题: $title\n\n内容:\n$truncatedContent")
                },
            )

            val response = aiService.chatCompletion(
                apiAddress = config.api_address,
                apiToken = config.api_token,
                model = config.model_name,
                messages = messages,
            )

            val summary = extractAnswerFromResponse(response)

            if (summary.isNotBlank()) {
                val existing = memoryRepo.getBySource(sourceType, sourceId)
                if (existing != null) {
                    memoryRepo.update(
                        id = existing.id,
                        title = title,
                        content = summary,
                        tags = null,
                    )
                    log.d { "Updated memory for $sourceType:$sourceId" }
                } else {
                    memoryRepo.insert(
                        type = "content",
                        sourceType = sourceType,
                        sourceId = sourceId,
                        title = title,
                        content = summary,
                    )
                    log.d { "Created memory for $sourceType:$sourceId" }
                }
            }
        } catch (e: Exception) {
            log.e(e) { "Failed to extract memory for $sourceType:$sourceId" }
        }
    }

    /**
     * Delete memory entries associated with a source.
     */
    suspend fun deleteBySource(sourceType: String, sourceId: Long) {
        try {
            memoryRepo.deleteBySource(sourceType, sourceId)
        } catch (e: Exception) {
            log.e(e) { "Failed to delete memory for $sourceType:$sourceId" }
        }
    }

    /**
     * Rebuild all content memories from scratch.
     * Clears existing content memories, then extracts from all diary/article/book/viewpoint records.
     */
    suspend fun rebuildAll(
        articleRepo: ArticleRepository,
        diaryRepo: DiaryRepository,
        bookRepo: BookRepository,
        viewpointRepo: BookViewpointRepository,
        onProgress: (String) -> Unit,
    ) {
        try {
            onProgress("清除旧记忆...")
            memoryRepo.deleteAllByType("content")

            val articles = articleRepo.getAllSync()
            onProgress("处理文章 (0/${articles.size})...")
            articles.forEachIndexed { index, article ->
                onProgress("处理文章 (${index + 1}/${articles.size})...")
                val text = article.ai_markdown_content ?: article.content ?: ""
                val t = article.ai_title ?: article.title ?: "未命名"
                extractAndSave("article", article.id, t, text)
            }

            val diaries = diaryRepo.getAllSync()
            onProgress("处理日记 (0/${diaries.size})...")
            diaries.forEachIndexed { index, diary ->
                onProgress("处理日记 (${index + 1}/${diaries.size})...")
                extractAndSave("diary", diary.id, "日记 ${diary.created_at}", diary.content)
            }

            val books = bookRepo.getAllSync()
            onProgress("处理书籍 (0/${books.size})...")
            books.forEachIndexed { index, book ->
                onProgress("处理书籍 (${index + 1}/${books.size})...")
                extractAndSave("book", book.id, book.title, book.introduction)
            }

            val viewpoints = viewpointRepo.getAllSync()
            onProgress("处理读书观点 (0/${viewpoints.size})...")
            viewpoints.forEachIndexed { index, vp ->
                onProgress("处理读书观点 (${index + 1}/${viewpoints.size})...")
                extractAndSave("book_viewpoint", vp.id, vp.title, vp.content)
            }

            onProgress("重建完成")
        } catch (e: Exception) {
            log.e(e) { "Failed to rebuild all memories" }
            onProgress("重建失败: ${e.message}")
        }
    }

    private fun buildSystemPrompt(): String = """
你是一个个人知识管理助手。你的任务是将用户提供的内容提取为简洁的记忆摘要。
要求：
1. 摘要不超过200字
2. 只提取关键事实、观点或信息
3. 使用中文
4. 直接返回摘要文本，不要添加前缀或后缀
    """.trimIndent()

    private suspend fun extractAnswerFromResponse(response: String): String {
        return try {
            val obj = json.parseToJsonElement(response).jsonObject
            val choices = obj["choices"]?.jsonArray ?: return ""
            val firstChoice = choices.firstOrNull()?.jsonObject ?: return ""
            val message = firstChoice["message"]?.jsonObject ?: return ""
            message["content"]?.jsonPrimitive?.content ?: ""
        } catch (e: Exception) {
            log.e(e) { "Failed to parse AI response" }
            ""
        }
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :shared:compileKotlinAndroid
```

Expected: No errors.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/memory/MemoryExtractService.kt
git commit -m "feat: add MemoryExtractService for auto summary extraction"
```

---

### Task 4: DI — Register new repositories and services

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`

- [ ] **Step 1: Register MemoryRepository and ChatConversationRepository**

In `SharedModule.kt`, add at line 39 (after the `ArticleRepository` line) these two new repository registrations:

```kotlin
    single { MemoryRepository(get()) }
    single { ChatConversationRepository(get()) }
```

In `SharedModule.kt`, add after the BackupService line (line 54) the MemoryExtractService:

```kotlin
    single { MemoryExtractService(get(), get(), get()) }
```

In `SharedModule.kt`, add relevant imports at the top with the other repository imports:

```kotlin
import com.dailysatori.data.repository.MemoryRepository
import com.dailysatori.data.repository.ChatConversationRepository
import com.dailysatori.service.memory.MemoryExtractService
```

- [ ] **Step 2: Update McpAgentService registration to include MemoryRepository**

In `SharedModule.kt`, change line 87:
```kotlin
    // Before:
    single { McpAgentService(get(), get(), get(), get(), get(), get()) }
    // After:
    single { McpAgentService(get(), get(), get(), get(), get(), get(), get()) }
```

- [ ] **Step 3: Build to verify**

```bash
./gradlew :shared:compileKotlinAndroid
```

Expected: No errors. If there's a type resolution error for MemoryExtractService, make sure all imports are correct.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt
git commit -m "feat: register MemoryRepository, ChatConversationRepository, MemoryExtractService in DI"
```

---

### Task 5: McpAgentService — Add memory tools and auto-injection

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt`

- [ ] **Step 1: Add MemoryRepository dependency to constructor**

In `McpAgentService.kt`, change the constructor (lines 29-36):

```kotlin
class McpAgentService(
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
    private val articleRepo: ArticleRepository,
    private val diaryRepo: DiaryRepository,
    private val bookRepo: BookRepository,
    private val viewpointRepo: BookViewpointRepository,
    private val memoryRepo: MemoryRepository,
) {
```

Add the import:
```kotlin
import com.dailysatori.data.repository.MemoryRepository
```

- [ ] **Step 2: Add search_memory and get_memory_source tool definitions**

In `buildToolDefinitions()` (after line 309, before the closing `)`), add:

```kotlin
        buildTool("search_memory", "搜索记忆库中的内容。记忆分为三种类型：core(核心偏好/事实)、content(从日记/文章/书中提取的摘要)、chat(对话中提取的关键信息)", mapOf(
            "query" to buildParam("string", "搜索关键词"),
            "type" to buildParam("string", "记忆类型过滤: core, content, chat，不传则搜索全部"),
            "limit" to buildParam("integer", "返回的最大数量，默认为10"),
        ), listOf("query")),
        buildTool("get_memory_source", "获取指定来源的记忆内容", mapOf(
            "source_type" to buildParam("string", "来源类型: article, diary, book, book_viewpoint, chat"),
            "source_id" to buildParam("integer", "来源ID"),
        ), listOf("source_type", "source_id")),
```

- [ ] **Step 3: Add tool execution handlers in executeTool()**

In `executeTool()` (after line 358, before the `else ->` line), add:

```kotlin
                "search_memory" -> searchMemory(args)
                "get_memory_source" -> getMemorySource(args)
```

- [ ] **Step 4: Implement searchMemory() and getMemorySource() methods**

Add after `getStatistics()` (around line 503):

```kotlin
    private fun searchMemory(args: JsonObject): McpToolResult {
        val query = stringParam(args, "query") ?: return errorResult("缺少query参数")
        val type = stringParam(args, "type")
        val limit = intParam(args, "limit", 10)

        val results = if (type != null) {
            memoryRepo.search(query, limit.toLong()).filter { it.type == type }
        } else {
            memoryRepo.search(query, limit.toLong())
        }

        if (results.isEmpty()) {
            return successResult("message" to jsonPrimitive("未找到相关记忆"), "results" to jsonArray {})
        }

        return successResult(
            "results" to jsonArray {
                results.forEach { entry ->
                    +buildJsonObject {
                        put("id", entry.id)
                        put("type", entry.type)
                        put("source_type", entry.source_type ?: "")
                        put("title", entry.title)
                        put("content", entry.content.take(500))
                        put("tags", entry.tags ?: "")
                    }
                }
            },
        )
    }

    private fun getMemorySource(args: JsonObject): McpToolResult {
        val sourceType = stringParam(args, "source_type") ?: return errorResult("缺少source_type参数")
        val sourceId = longParam(args, "source_id") ?: return errorResult("缺少source_id参数")

        val entry = memoryRepo.getBySource(sourceType, sourceId)
        if (entry == null) {
            return successResult("message" to jsonPrimitive("未找到相关记忆"))
        }

        return successResult("memory" to buildJsonObject {
            put("id", entry.id)
            put("type", entry.type)
            put("title", entry.title)
            put("content", entry.content)
            put("tags", entry.tags ?: "")
        })
    }
```

- [ ] **Step 5: Add auto memory search before processing query**

In `processQuery()`, after line 75 (after `updateStep("正在理解您的问题...", "processing")`), add:

```kotlin
            val relevantMemories = memoryRepo.search(query, limit = 5)
            if (relevantMemories.isNotEmpty()) {
                val memoryContext = relevantMemories.joinToString("\n") { entry ->
                    "- [${entry.type}] ${entry.title}: ${entry.content.take(300)}"
                }
                messages.add(buildJsonObject {
                    put("role", "system")
                    put("content", "相关记忆（供参考，优先使用记忆中的信息）:\n$memoryContext")
                })
            }
```

- [ ] **Step 6: Update system prompt to mention memory tools**

In `buildSystemPrompt()`, add in the "综合" section (after the `get_statistics` line):

```
26:         ### 记忆相关
27:         - `search_memory`: 搜索你的记忆库（包含核心偏好、内容摘要、对话记忆）
28:         - `get_memory_source`: 获取指定来源的完整记忆内容
```

Update the core rule section to mention memory:

In the system prompt, change the first rule from:
```
**你只能基于用户的个人数据来回答问题，不要使用你的通用知识来回答。**
```
to:
```
**你只能基于用户的个人数据来回答问题，不要使用你的通用知识来回答。**
同时优先在记忆库中搜索相关信息。记忆库包含你的核心偏好、所有内容的AI摘要和之前对话的关键信息。
```

- [ ] **Step 7: Build shared module**

```bash
./gradlew :shared:compileKotlinAndroid
```

Expected: No errors.

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt
git commit -m "feat: add memory search tools to McpAgentService with auto-injection"
```

---

### Task 6: AiChatViewModel — Add chat persistence and session management

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`

- [ ] **Step 1: Extend AiChatState and AiChatViewModel**

Replace `AiChatViewModel.kt` entirely:

```kotlin
package com.dailysatori.ui.feature.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.ChatConversationRepository
import com.dailysatori.service.mcp.McpAgentResult
import com.dailysatori.service.mcp.McpAgentService
import com.dailysatori.service.mcp.McpSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiChatState(
    val messages: List<ChatMessageUi> = emptyList(),
    val isProcessing: Boolean = false,
    val currentStep: String = "",
    val sessionId: String = generateSessionId(),
)

data class ChatMessageUi(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val isError: Boolean = false,
    val searchResults: List<McpSearchResult> = emptyList(),
    val steps: List<String> = emptyList(),
)

class AiChatViewModel(
    private val mcpAgentService: McpAgentService,
    private val chatConversationRepo: ChatConversationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AiChatState())
    val state: StateFlow<AiChatState> = _state.asStateFlow()

    init {
        loadLatestSession()
    }

    private fun loadLatestSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessions = chatConversationRepo.getSessions()
            if (sessions.isNotEmpty()) {
                val latestSession = sessions.first()
                val messages = chatConversationRepo.getBySession(latestSession)
                if (messages.isNotEmpty()) {
                    _state.update { it.copy(
                        sessionId = latestSession,
                        messages = messages.map { msg ->
                            ChatMessageUi(
                                id = msg.id.toString(),
                                role = msg.role,
                                content = msg.content,
                                timestamp = msg.created_at,
                                searchResults = emptyList(),
                                steps = emptyList(),
                            )
                        },
                    ) }
                }
            }
        }
    }

    fun sendMessage(content: String) {
        val userMessage = ChatMessageUi(
            id = generateId(),
            role = "user",
            content = content,
            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
        )
        _state.update { it.copy(
            messages = it.messages + userMessage,
            isProcessing = true,
            currentStep = "",
        ) }
        persistMessage(userMessage)

        viewModelScope.launch(Dispatchers.IO) {
            val steps = mutableListOf<String>()
            val result = mcpAgentService.processQuery(
                query = content,
                onStep = { step, status ->
                    _state.update { it.copy(currentStep = step) }
                    if (status == "completed") steps.add(step)
                },
            )

            val assistantMessage = ChatMessageUi(
                id = generateId(),
                role = "assistant",
                content = result.answer,
                timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                isError = result.answer.startsWith("\uD83D\uDE14 **出现问题**"),
                searchResults = result.searchResults,
                steps = steps,
            )
            _state.update { it.copy(
                messages = it.messages + assistantMessage,
                isProcessing = false,
                currentStep = "",
            ) }
            persistMessage(assistantMessage)
        }
    }

    fun clearMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            chatConversationRepo.deleteBySession(_state.value.sessionId)
        }
        _state.update { it.copy(
            messages = emptyList(),
            isProcessing = false,
            currentStep = "",
            sessionId = generateSessionId(),
        ) }
    }

    private fun persistMessage(message: ChatMessageUi) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                chatConversationRepo.insert(
                    sessionId = _state.value.sessionId,
                    role = message.role,
                    content = message.content,
                    searchResults = null,
                    steps = null,
                )
            } catch (_: Exception) { }
        }
    }

    private fun generateId(): String {
        val ts = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val r = (0..9999).random()
        return "${ts}_${r}"
    }

    companion object {
        private fun generateSessionId(): String {
            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            return "chat_$now"
        }
    }
}
```

- [ ] **Step 2: Update ViewModelModule to provide ChatConversationRepository**

In `ViewModelModule.kt`, change the `AiChatViewModel` registration (lines 67-71):

```kotlin
    viewModel {
        AiChatViewModel(
            mcpAgentService = get<McpAgentService>(),
            chatConversationRepo = get<ChatConversationRepository>(),
        )
    }
```

Add import at the top:
```kotlin
import com.dailysatori.data.repository.ChatConversationRepository
```

- [ ] **Step 3: Build app module**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: No errors.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt
git commit -m "feat: add chat persistence and session management to AiChatViewModel"
```

---

### Task 7: AiChatScreen UI — ChatGPT-style input box + Markdown rendering

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt`

- [ ] **Step 1: Rewrite AiChatScreen with new input box and Markdown rendering**

Replace `AiChatScreen.kt` entirely:

```kotlin
package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.service.mcp.McpSearchResult
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.m3.markdownColor
import org.koin.androidx.compose.koinViewModel

@Composable
fun AiChatScreen(
    onOpenMemorySearch: () -> Unit = {},
) {
    val viewModel: AiChatViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "AI 助手",
                showBack = false,
                actions = {
                    IconButton(onClick = onOpenMemorySearch) {
                        Icon(Icons.Default.Search, contentDescription = "记忆搜索")
                    }
                    IconButton(
                        onClick = { viewModel.clearMessages() },
                        enabled = state.messages.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "新对话")
                    }
                },
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                enabled = !state.isProcessing,
            )
        },
    ) { padding ->
        if (state.isProcessing && state.currentStep.isNotBlank()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(padding),
            )
        }

        if (state.messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.m))
                    Text("AI 助手", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(Spacing.s))
                    Text(
                        "基于你的知识库和记忆回答",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(vertical = Spacing.m),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }
                if (state.isProcessing) {
                    item {
                        TypingIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s).imePadding(),
        ) {
            Surface(
                shape = RoundedCornerShape(Radius.circular),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = Spacing.m, end = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "问我任何问题...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        maxLines = 6,
                        enabled = enabled,
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    FilledIconButton(
                        onClick = onSend,
                        enabled = inputText.isNotBlank() && enabled,
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = contentColorFor(MaterialTheme.colorScheme.primary),
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Text(
                "基于你的知识库和记忆回答",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = Spacing.xs, start = Spacing.s),
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessageUi) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = Radius.m,
                    topEnd = Radius.m,
                    bottomStart = if (isUser) Radius.m else Radius.xs,
                    bottomEnd = if (isUser) Radius.xs else Radius.m,
                ),
                color = when {
                    isUser -> MaterialTheme.colorScheme.primary
                    message.isError -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceContainer
                },
                modifier = Modifier.fillMaxWidth(0.85f),
            ) {
                if (isUser) {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(Spacing.m),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    val colors = markdownColor(
                        text = MaterialTheme.colorScheme.onSurface,
                        codeText = MaterialTheme.colorScheme.onSurface,
                        codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
                        inlineCodeText = MaterialTheme.colorScheme.onSurface,
                        linkText = MaterialTheme.colorScheme.primary,
                    )
                    Markdown(
                        content = message.content,
                        colors = colors,
                        typography = com.dailysatori.ui.theme.MarkdownStyles.cardTypography(),
                        padding = com.dailysatori.ui.theme.MarkdownStyles.cardPadding(),
                        modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.m, bottom = Spacing.s),
                    )
                }
            }
        }

        if (!isUser && message.searchResults.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Surface(
                shape = RoundedCornerShape(Radius.s),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = Spacing.s),
            ) {
                Column(modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs)) {
                    message.searchResults.take(3).forEach { result ->
                        Text(
                            text = "\uD83D\uDCC4 ${result.type}: ${result.title}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = Radius.m, topEnd = Radius.m, bottomStart = Radius.xs, bottomEnd = Radius.m),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(0.8f),
        ) {
            Text(
                text = "思考中...",
                modifier = Modifier.padding(Spacing.m),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 2: Build app module**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: No errors. If `markdownColor` is not found, check the correct import path — it may be `com.mikepenz.markdown.m3.markdownColor` or `com.mikepenz.markdown.compose.m3.markdownColor`. Adjust import accordingly.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt
git commit -m "feat: redesign chat UI with ChatGPT-style input and markdown rendering"
```

---

### Task 8: Memory search BottomSheet in HomeScreen

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`

- [ ] **Step 1: Add memory search bottom sheet state and content**

First, find and read the current HomeScreen to understand the exact structure. Then add a `ModalBottomSheet` that appears when `onOpenMemorySearch` is called from `AiChatScreen`.

In `HomeScreen.kt`, add these imports and state:

```kotlin
import androidx.compose.material3.*
import androidx.compose.foundation.clickable
import com.dailysatori.data.repository.MemoryRepository
import com.dailysatori.service.memory.MemoryExtractService
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import org.koin.java.KoinJavaComponent.get
```

In the composable where AiChatScreen is called, add:
```kotlin
var showMemorySheet by remember { mutableStateOf(false) }

if (showMemorySheet) {
    MemorySearchSheet(
        onDismiss = { showMemorySheet = false },
    )
}

// Pass callback to AiChatScreen
AiChatScreen(onOpenMemorySearch = { showMemorySheet = true })
```

Add the `MemorySearchSheet` composable below (in the same file or a new private composable):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemorySearchSheet(onDismiss: () -> Unit) {
    val memoryRepo = remember { get<MemoryRepository>(MemoryRepository::class.java) }
    val extractService = remember { get<MemoryExtractService>(MemoryExtractService::class.java) }
    val articleRepo = remember { get<ArticleRepository>(ArticleRepository::class.java) }
    val diaryRepo = remember { get<DiaryRepository>(DiaryRepository::class.java) }
    val bookRepo = remember { get<BookRepository>(BookRepository::class.java) }
    val viewpointRepo = remember { get<BookViewpointRepository>(BookViewpointRepository::class.java) }
    var searchQuery by remember { mutableStateOf("") }
    var memories by remember { mutableStateOf<List<Memory_entry>>(emptyList()) }
    var isRebuilding by remember { mutableStateOf(false) }
    var rebuildProgress by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(searchQuery) {
        withContext(Dispatchers.IO) {
            memories = if (searchQuery.isBlank()) {
                memoryRepo.getAllSync()
            } else {
                memoryRepo.search(searchQuery, 50)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.m)) {
            Text("记忆搜索", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(Spacing.s))

            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索记忆...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(Radius.m),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )

            if (isRebuilding && rebuildProgress.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.s))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(rebuildProgress, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(Spacing.s))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${memories.size} 条记忆",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        isRebuilding = true
                        scope.launch(Dispatchers.IO) {
                            extractService.rebuildAll(
                                articleRepo, diaryRepo, bookRepo, viewpointRepo,
                                onProgress = { rebuildProgress = it },
                            )
                            memories = memoryRepo.getAllSync()
                            isRebuilding = false
                        }
                    },
                    enabled = !isRebuilding,
                ) {
                    Text("重建全部记忆")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(400.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(vertical = Spacing.s),
            ) {
                items(memories, key = { it.id }) { memory ->
                    MemoryEntryCard(memory)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.m))
        }
    }
}

@Composable
private fun MemoryEntryCard(memory: Memory_entry) {
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemoryTypeChip(memory.type)
                Spacer(modifier = Modifier.width(Spacing.s))
                Text(
                    memory.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                memory.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MemoryTypeChip(type: String) {
    val (label, color) = when (type) {
        "core" -> "核心" to MaterialTheme.colorScheme.primary
        "content" -> "内容" to MaterialTheme.colorScheme.secondary
        "chat" -> "对话" to MaterialTheme.colorScheme.tertiary
        else -> type to MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = RoundedCornerShape(Radius.xs),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
```

Add the required import for Koin component retrieval:
```kotlin
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
```

- [ ] **Step 2: Build app module**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: No errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt
git commit -m "feat: add memory search bottom sheet to home screen"
```

---

### Task 9: Auto-extraction integration in existing ViewModels

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailViewModel.kt`

- [ ] **Step 1: Add MemoryExtractService to DiaryViewModel**

Read `DiaryViewModel.kt` and add `MemoryExtractService` as a constructor parameter. Then in the `saveDiary()` method, after `diaryRepo.insert(...)` or `diaryRepo.update(...)`, add a call to extract memory.

Key changes to `DiaryViewModel.kt`:

Add import:
```kotlin
import com.dailysatori.service.memory.MemoryExtractService
```

Add constructor parameter:
```kotlin
class DiaryViewModel(
    private val diaryRepo: DiaryRepository,
    private val memoryExtractService: MemoryExtractService,
) : ViewModel() {
```

In `saveDiary()`, after the insert/update call, add:
```kotlin
            if (content.isNotBlank()) {
                memoryExtractService.extractAndSave(
                    sourceType = "diary",
                    sourceId = existingId ?: 0L,
                    title = "日记",
                    content = content,
                )
            }
```

In `ViewModelModule.kt`, update the DiaryViewModel registration:
```kotlin
    viewModel {
        DiaryViewModel(
            diaryRepo = get<DiaryRepository>(),
            memoryExtractService = get<MemoryExtractService>(),
        )
    }
```

- [ ] **Step 2: Add MemoryExtractService to ArticleDetailViewModel**

Read `ArticleDetailViewModel.kt` and add `MemoryExtractService` as a constructor parameter. In `toggleFavorite()`, after `articleRepo.toggleFavorite(articleId)`, check if favorited and extract memory.

Add import:
```kotlin
import com.dailysatori.service.memory.MemoryExtractService
```

Add constructor parameter and modify class:
```kotlin
class ArticleDetailViewModel(
    private val articleId: Long,
    private val articleRepo: ArticleRepository,
    private val tagRepo: TagRepository,
    private val memoryExtractService: MemoryExtractService,
) : ViewModel() {
```

In `toggleFavorite()`:
```kotlin
    fun toggleFavorite() {
        viewModelScope.launch(Dispatchers.IO) {
            articleRepo.toggleFavorite(articleId)
            loadArticle()
            val article = articleRepo.getById(articleId)
            if (article != null && article.is_favorite == 1L) {
                val text = article.ai_markdown_content ?: article.content ?: ""
                val title = article.ai_title ?: article.title ?: "未命名"
                memoryExtractService.extractAndSave(
                    sourceType = "article",
                    sourceId = articleId,
                    title = title,
                    content = text,
                )
            }
        }
    }
```

In `ViewModelModule.kt`, update the ArticleDetailViewModel registration:
```kotlin
    viewModel { params ->
        ArticleDetailViewModel(
            articleId = params.get<Long>(),
            articleRepo = get<ArticleRepository>(),
            tagRepo = get<TagRepository>(),
            memoryExtractService = get<MemoryExtractService>(),
        )
    }
```

- [ ] **Step 3: Build app module**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: No errors.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryViewModel.kt app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailViewModel.kt app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt
git commit -m "feat: integrate auto memory extraction in diary and article save flows"
```

---

### Task 10: Final build, verify, and deploy

- [ ] **Step 1: Full project build**

```bash
./gradlew :app:assembleDebug
```

Expected: Build successful with no errors.

- [ ] **Step 2: Install to device**

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
```

Expected: App installed successfully.

- [ ] **Step 3: Launch app and manually verify**

```bash
adb shell am start -n com.dailysatori/.MainActivity
```

Verify:
- Navigate to AI tab
- Input box appears with rounded pill shape, send button inside
- Send a message → AI responds with Markdown rendering
- Click search icon → Memory search bottom sheet opens
- Add a diary → Memory entry auto-created
- Toggle article favorite → Memory entry auto-created
- "重建全部记忆" button works

- [ ] **Step 4: Commit any remaining changes**

```bash
git add -A
git commit -m "chore: final adjustments and verification"
```
