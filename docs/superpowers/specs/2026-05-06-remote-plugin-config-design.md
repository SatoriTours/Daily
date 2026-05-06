# Remote Plugin Config Design

## Goal

Allow Daily Satori to update AI prompts and AI provider/model presets from a public HTTP server without releasing a new app version. The app must remain fully usable when the remote URL is empty, unavailable, returns invalid data, or contains partial data.

## Scope

First version supports two plugin types:

- `prompt`: named prompt content used by existing AI workflows.
- `aiProviders`: provider and model presets used by the AI configuration screen.

Initial prompt integration points:

- Article summary prompt.
- Article HTML-to-Markdown prompt.

Out of scope for the first version:

- Remote executable code.
- Remote database migrations.
- Remote MCP tool definitions.
- Authentication for plugin downloads.

## Remote File Layout

The user configures a base URL in the plugin center, for example:

```text
https://example.com/daily-plugins
```

The app downloads a fixed discovery file:

```text
https://example.com/daily-plugins/index.json
```

`index.json` lists independent plugin files:

```json
{
  "version": 1,
  "plugins": [
    {
      "id": "article-summary",
      "type": "prompt",
      "file": "prompts/article-summary.json",
      "version": 3,
      "name": "文章摘要提示词"
    },
    {
      "id": "article-markdown",
      "type": "prompt",
      "file": "prompts/article-markdown.json",
      "version": 2,
      "name": "文章 Markdown 整理提示词"
    },
    {
      "id": "ai-providers",
      "type": "aiProviders",
      "file": "providers/ai-providers.json",
      "version": 5,
      "name": "AI 服务商列表"
    }
  ]
}
```

Prompt file format:

```json
{
  "id": "article-summary",
  "version": 3,
  "content": "你是一位专业的内容分析师..."
}
```

AI providers file format:

```json
{
  "version": 5,
  "providers": [
    {
      "id": "openai",
      "name": "OpenAI",
      "apiHost": "https://api.openai.com",
      "models": [
        { "id": "gpt-5.5", "name": "GPT-5.5" }
      ]
    }
  ]
}
```

## Local Defaults And Fallback

Local defaults are mandatory and remain the final source of truth when remote data is unavailable.

Prompt resolution order:

1. Valid cached remote prompt for the requested key.
2. Built-in prompt function, such as `articleSummaryPrompt()` or `htmlToReadableMarkdownPrompt()`.

AI provider resolution order:

1. Valid cached remote provider list.
2. Built-in `aiProviders` list from `AiProviderModels.kt`.

Remote fetch failures must not block article processing, AI chat, or AI configuration. Invalid remote files are ignored and must not replace the last valid cached copy.

## Storage

Use the existing `SettingRepository` storage pattern.

Suggested keys:

- `plugin_server_url`: configured base URL.
- `plugin_index_json`: last valid index JSON.
- `plugin_content_<file>`: last valid plugin file content.
- `plugin_updated_at_<file>`: last successful update timestamp.

The existing `plugin_content_` key prefix can be preserved to minimize migration work.

## Service Responsibilities

`PluginService` becomes the single shared service for remote plugin data.

Responsibilities:

- Normalize the base URL and append `index.json` safely.
- Download and validate `index.json`.
- Download each listed plugin file.
- Validate plugin file shape before caching it.
- Expose prompt lookup by key.
- Expose AI provider list lookup.
- Fall back to built-in defaults when cache is missing or invalid.

The service should not know UI details. The plugin center view model should call service methods and render status.

## UI Behavior

Plugin Center should show:

- Base URL text field.
- Update all action that fetches `index.json` and all listed plugin files.
- List of discovered plugin entries from cached `index.json`.
- Per-plugin update action.
- Last updated timestamp when available.
- Error text for failed updates without deleting cached working data.

The empty state should distinguish between “no server URL configured” and “server configured but no valid plugins cached”.

## Data Flow

Startup and use-time behavior:

1. App uses built-in defaults immediately.
2. Plugin Center can fetch remote files and cache valid results.
3. Article processing asks `PluginService` for prompt content by key.
4. AI configuration UI asks `PluginService` for provider presets.
5. If remote data is invalid or absent, callers receive built-in defaults.

## Error Handling

Network errors:

- Return failure to Plugin Center.
- Keep existing cached content.
- Keep runtime callers on cached content or built-in defaults.

Validation errors:

- Reject the invalid file.
- Do not overwrite previous cached content.
- Surface a concise error message in Plugin Center.

Partial updates:

- A failed plugin file should not fail other plugin files.
- `updateAll` reports failed items but caches successful valid items.

Security constraints:

- Treat remote content as data only.
- Do not execute remote code.
- Do not store or fetch secrets from plugin files.
- Do not log API tokens or sensitive user content.

## Testing

Unit tests should cover:

- Prompt fallback uses built-in prompt when no remote cache exists.
- Prompt lookup uses valid cached remote prompt.
- Invalid prompt JSON is ignored.
- AI providers use built-in list when no valid remote cache exists.
- Valid remote AI providers replace built-in presets in the AI config UI path.
- `index.json` parsing rejects missing id, type, file, or unsupported type.
- Failed update does not overwrite previous valid cached content.

Integration/compile checks:

- `./gradlew :shared:testDebugUnitTest`
- `./gradlew :app:compileDebugKotlin`
- `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug` when a device is connected.
