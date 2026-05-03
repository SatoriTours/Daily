# LangChain4j AI Config Test Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace custom chat-completion HTTP calls with an in-app Java AI client library and add a connectivity test to the AI config editor.

**Architecture:** Keep the existing `AiService` public API so article parsing, memory extraction, and MCP code continue to call the same methods. Internally route simple text completion through LangChain4j chat models, with provider-specific model construction based on saved AI config values. The AI config editor will test the current unsaved form values by sending a short prompt and showing success/failure inline.

**Tech Stack:** Kotlin Multiplatform shared module, Android Compose UI, Koin DI, SQLDelight, LangChain4j plain Java artifacts.

---

### Task 1: Add LangChain4j Dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts`

- [ ] **Step 1: Add catalog entries**

Add LangChain4j version and provider libraries:

```toml
langchain4j = "1.14.0"

langchain4j-open-ai = { group = "dev.langchain4j", name = "langchain4j-open-ai", version.ref = "langchain4j" }
langchain4j-anthropic = { group = "dev.langchain4j", name = "langchain4j-anthropic", version.ref = "langchain4j" }
langchain4j-google-ai-gemini = { group = "dev.langchain4j", name = "langchain4j-google-ai-gemini", version.ref = "langchain4j" }
```

- [ ] **Step 2: Add dependencies to `androidMain`**

Add these dependencies under `shared/build.gradle.kts` `androidMain.dependencies`:

```kotlin
implementation(libs.langchain4j.open.ai)
implementation(libs.langchain4j.anthropic)
implementation(libs.langchain4j.google.ai.gemini)
```

- [ ] **Step 3: Verify dependency resolution**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:compileDebugKotlinAndroid`

Expected: dependency resolution succeeds or shows a concrete Android compatibility issue.

### Task 2: Use LangChain4j For Completion Calls

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/ai/AiService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/parser/ArticleProcessingContentTest.kt`

- [ ] **Step 1: Keep normalization tests green**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:testDebugUnitTest`

Expected: existing normalization and content-preservation tests pass.

- [ ] **Step 2: Add LangChain4j-backed completion**

Implement `AiService.complete()` using LangChain4j chat model construction for `openai`, `anthropic`, and `gemini` providers. Keep the method signature unchanged:

```kotlin
suspend fun complete(
    prompt: String,
    apiAddress: String,
    apiToken: String,
    modelName: String,
    provider: String = "openai",
    systemPrompt: String? = null,
    temperature: Double = 0.5,
): String
```

Expected behavior:
- Trim all config values before model construction.
- Include `systemPrompt` before user content where supported.
- Throw an exception when the provider request fails instead of returning `""`.
- Throw an exception if the response text is blank.

- [ ] **Step 3: Keep existing callers unchanged**

Leave `translate()`, `summarize()`, and `htmlToMarkdown()` calling `complete()`. Do not change article parsing, memory extraction, or MCP call sites unless compilation requires it.

- [ ] **Step 4: Compile**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`

Expected: build succeeds.

### Task 3: Add AI Config Connectivity Test

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/ai/AiService.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditScreen.kt`

- [ ] **Step 1: Add test helper method**

Add to `AiService`:

```kotlin
suspend fun testConnection(
    apiAddress: String,
    apiToken: String,
    modelName: String,
    provider: String,
): Result<String>
```

Expected behavior: call `complete("请只回复 OK", ..., temperature = 0.0)` and return success if response is non-blank; return failure with the original exception message otherwise.

- [ ] **Step 2: Inject `AiService` in edit screen**

In `AiConfigEditScreen`, get `AiService` from Koin next to `AIConfigRepository`:

```kotlin
val aiService = remember { KoinPlatform.getKoin().get<AiService>() }
```

- [ ] **Step 3: Add UI states**

Add Compose state:

```kotlin
var isTesting by remember { mutableStateOf(false) }
var testResult by remember { mutableStateOf<String?>(null) }
var testSuccess by remember { mutableStateOf<Boolean?>(null) }
```

- [ ] **Step 4: Add button**

Add an `OutlinedButton` before the save row:

```kotlin
OutlinedButton(
    onClick = { /* launch testConnection */ },
    enabled = !isTesting && selectedProvider != null && apiToken.isNotBlank() &&
        (selectedModel != null || customModelName.isNotBlank()),
    modifier = Modifier.fillMaxWidth(),
) { Text(if (isTesting) "测试中..." else "测试连接") }
```

- [ ] **Step 5: Display result**

Below the button, show `testResult` in primary color for success and error color for failure.

- [ ] **Step 6: Compile**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`

Expected: build succeeds.

### Task 4: Verify And Deploy

**Files:**
- No source changes expected.

- [ ] **Step 1: Run tests**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:testDebugUnitTest`

Expected: build successful.

- [ ] **Step 2: Compile app**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`

Expected: build successful.

- [ ] **Step 3: Install and launch**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug && adb shell am start -n com.dailysatori/.MainActivity`

Expected: APK installed on connected device and app launched.

- [ ] **Step 4: Manual smoke test**

Open AI config editor, select provider/model, paste API token, tap “测试连接”. Expected: success message for valid credentials; specific failure message for invalid credentials.
