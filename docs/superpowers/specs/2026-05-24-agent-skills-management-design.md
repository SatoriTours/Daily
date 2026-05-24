# Agent Skills Management Design

## Goal

Add a unified Skills management system where WeRead is one built-in external Skill, users can add more Skills, and hidden scenario agents decide which enabled Skills to call for article summaries, book intelligence, and AI Chat.

## Product Model

The app has two kinds of Skills.

System Skills are invisible to users. They define how a scenario agent should work:

- `article_summary_agent`: summarize saved or remote articles and decide whether supporting external Skills are needed.
- `book_intelligence_agent`: search books, collect book material, generate viewpoints, and disclose AI fallback output.
- `ai_chat_agent`: answer chat requests using local tools, memory, and enabled external Skills when useful.

External Skills are visible in Settings. They are tools the scenario agents may call. Users can add, edit, enable, disable, and delete external Skills, except built-in external Skills.

WeRead is a built-in external Skill. It is preloaded, visible in Skills management, cannot be deleted, and only needs a token to become usable.

## First Implementation Scope

Build this in three phases to reduce risk.

Phase 1 creates the data model and management UI:

- Add a `skill_config` table and repository.
- Preload the WeRead Skill.
- Migrate existing `weread_api_key` into the WeRead Skill token.
- Replace the standalone WeRead settings entry with a Skills settings entry.
- Keep the existing WeRead book runtime working while reading credentials from Skills.

Phase 2 routes book intelligence through `book_intelligence_agent`:

- The book agent receives a task prompt and a list of enabled Skill tools.
- WeRead is exposed as structured tools for search, book info, chapters, and reviews.
- The agent chooses whether to call WeRead and whether AI fallback is needed.
- Existing user-facing behavior remains: search is WeRead-backed when WeRead is enabled, sparse material can fallback to AI, and AI output is disclosed.

Phase 3 routes article summaries and AI Chat through scenario agents:

- Article summary uses `article_summary_agent` with article/web/source tools.
- AI Chat uses `ai_chat_agent` with the broadest allowed tool set.
- Existing local MCP tools can be exposed through the same registry so the agent has one tool interface.

## Data Model

Add `skill_config` with fields:

- `id INTEGER PRIMARY KEY AUTOINCREMENT`
- `name TEXT NOT NULL`
- `description TEXT NOT NULL DEFAULT ''`
- `gateway_url TEXT NOT NULL`
- `api_token TEXT NOT NULL DEFAULT ''`
- `skill_version TEXT NOT NULL DEFAULT ''`
- `enabled INTEGER NOT NULL DEFAULT 0`
- `builtin INTEGER NOT NULL DEFAULT 0`
- `provider TEXT NOT NULL DEFAULT ''`
- `template_id TEXT NOT NULL DEFAULT ''`
- `tool_schema_json TEXT NOT NULL DEFAULT ''`
- `created_at INTEGER NOT NULL`
- `updated_at INTEGER NOT NULL`

The token is encrypted with `SecretCipher`, following the AI config and MCP server pattern. Built-in rows cannot be deleted. WeRead uses `template_id = "weread"` and `provider = "weread"`.

Database migration must increment `DatabaseConfig.currentSchemaVersion`, create the table, insert the WeRead built-in row if missing, and migrate `SettingKeys.weReadApiKey` into the WeRead row token. The old setting may remain for compatibility but the runtime should prefer `skill_config`.

## Skill Runtime

Add a `SkillConfigRepository` to read/decrypt Skills and save encrypted tokens.

Add a `SkillRegistry` that exposes enabled external Skills as AI tools.

For built-in WeRead, expose explicit tools:

- `weread_search_books`
- `weread_get_book_info`
- `weread_get_chapters`
- `weread_get_reviews`

For custom Skills, expose a generic tool:

- `call_external_skill`

The generic tool accepts `skill_id`, `api_name`, and `params_json`. The agent uses the custom Skill description and `tool_schema_json` to decide how to call it. This keeps custom Skills useful without requiring the app to know every API shape.

## Scenario Agents

Add a small scenario-agent layer over `AiService.chatCompletion` tool calling.

Each scenario agent provides:

- A hidden system prompt.
- A tool allowlist policy.
- A response contract for the caller.

The allowlist is code-owned, not user-owned:

- `article_summary_agent` can use article, webpage, summary, and external Skills when the prompt says external context is useful.
- `book_intelligence_agent` can use book/local data tools, WeRead tools, and external Skills related to books.
- `ai_chat_agent` can use local data, memory, and all enabled external Skills.

Users do not manually assign Skills to scenarios in the first version. The AI chooses from the tools exposed by the scenario agent.

## Settings UI

Replace the Settings row `微信读书` with `Skills` under `AI 与服务`.

The Skills page is list-based, similar to AI config:

- Shows the number of Skills.
- Shows each Skill name, built-in/custom badge, enabled status, and token status.
- Built-in WeRead cannot be deleted.
- Custom Skills can be added, edited, deleted, enabled, and disabled.

The edit page fields are:

- Name
- Description for AI
- Gateway URL
- Skill version
- API Token
- Enabled switch
- Tool schema JSON

For WeRead, the edit page keeps immutable built-in fields read-only and focuses on API Token and enabled state.

## Error Handling

- Missing WeRead token should become `请先在 Skills 中配置微信读书 Token`.
- Disabled WeRead should be treated as unavailable to the book agent.
- Built-in Skill delete attempts should be ignored or shown as unavailable in UI.
- Invalid custom Skill JSON should block save with a visible error.
- External Skill gateway failures should report the Skill name in the error message.

## Security And Backup

Skill tokens are secrets and must be encrypted with `SecretCipher`.

Backups may still require token re-entry after restore if Android keystore-backed encrypted values cannot be decrypted on the restored device. This behavior matches current WeRead token storage.

## Tests

- Repository tests for encrypt/decrypt and built-in delete protection.
- Migration tests or source tests for creating `skill_config` and inserting WeRead.
- Skill text tests for Settings labels, badges, delete restrictions, and token status.
- Runtime tests for resolving WeRead token from Skills instead of `SettingKeys.weReadApiKey`.
- Agent registry tests for exposing WeRead tools and generic custom Skill tools.
- Scenario prompt tests for article, book, and chat agent contracts.

## Non-Goals

- Do not build a marketplace or remote catalog in the first version.
- Do not let users edit hidden System Skills in the first version.
- Do not require users to manually map each Skill to each scenario.
- Do not remove existing MCP server support; it can be bridged into the unified registry later.
