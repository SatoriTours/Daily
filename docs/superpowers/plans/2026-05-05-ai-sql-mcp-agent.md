# AI SQL MCP Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the AI assistant two reliable tool layers: safe local read-only SQL for personal data questions and remote MCP web search for external concept questions.

**Architecture:** Add a `LocalSqlQueryService` that validates and executes SELECT-only queries with table allowlists and row limits. Extend the existing MCP tool registry with `query_local_database` and `search_web_with_mcp`, using enabled HTTP MCP servers only. Replace AI diary reference detail rendering with a single read-only diary summary component that always includes images.

**Tech Stack:** Kotlin Multiplatform, SQLDelight `SqlDriver`, Ktor-based remote MCP client, Android Jetpack Compose, Koin DI, Kotlin unit tests.

---

## Tasks

### Task 1: Safe Local SQL Service

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/LocalSqlQueryService.kt`
- Create: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/LocalSqlQueryServiceTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`

Steps:
- Add failing tests for SQL validation: SELECT accepted, DELETE rejected, unknown table rejected, missing LIMIT gets wrapped.
- Implement `validateLocalSql`, `localSqlToolSchemaText`, `LocalSqlQueryService.query(sql, columns)`.
- Register `LocalSqlQueryService` in shared DI.

### Task 2: Remote MCP Web Search Tool

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/RemoteMcpClient.kt`
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/RemoteMcpClientTest.kt`

Steps:
- Add failing test for `isLikelyWebSearchTool` accepting web/search/read tools and rejecting weather/local-only tools.
- Add `collectWebSearchNotes(servers, query)` using enabled HTTP MCP servers and generic web search/read tools.

### Task 3: Agent Tool Wiring And Prompt

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolRegistry.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`

Steps:
- Add `query_local_database` tool with `sql` and `columns` arguments.
- Add `search_web_with_mcp` tool with `query` argument.
- Make tool execution suspend so remote MCP can be called from chat.
- Update the system prompt to route personal-data questions to local SQL and external concept/web questions to remote MCP.

### Task 4: Unified Read-Only Diary Detail

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiReferenceDetailSheet.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`

Steps:
- Add failing tests for diary image path parsing.
- Replace `DiaryCard` in AI reference detail with read-only full diary detail: date, markdown content, image row, tags.
- Keep sheet height consistent and avoid editor behavior.

### Task 5: Verification

Commands:
- `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.mcp.LocalSqlQueryServiceTest"`
- `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.mcp.RemoteMcpClientTest"`
- `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest"`
- `./gradlew :app:compileDebugKotlin`
- `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`
- `adb shell am start -n com.dailysatori/.MainActivity`

## Self-Review

- Covers local SQL analysis, external MCP search, agent routing, and diary detail consistency.
- No database migration required.
- No commit steps included because commits require explicit user request.
