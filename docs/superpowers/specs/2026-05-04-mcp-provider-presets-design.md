# MCP Provider Presets Design

## Goal

Improve MCP service setup so users do not need to manually find and enter service URLs for common providers. The first version prioritizes common presets for GLM, DeepSeek, Minimax, and ChatGPT/OpenAI. Users select a provider, enter one API key, choose one or more supported MCP services, and add them in one operation.

## Current State

`McpServerScreen` currently supports a simple manual form with service name, service URL, API key, and enabled state. Storage is backed by `mcp_server` with `name`, `server_url`, `api_key`, `enabled`, `created_at`, and `updated_at`. This is flexible but pushes provider-specific MCP knowledge onto the user.

The AI config screen already has a provider dropdown pattern that can guide the new MCP flow.

## Scope

In scope:

- Add built-in MCP provider presets for GLM, DeepSeek, Minimax, and ChatGPT/OpenAI.
- Show supported MCP services after selecting a provider.
- Separate normal MCP services from Coding Plan MCP services.
- Allow users to select multiple MCP services and add them together.
- Require only one API key for the selected provider during batch add.
- Keep the current manual MCP add/edit path as an advanced fallback.

Out of scope for this iteration:

- User-created provider templates.
- Remote template updates.
- Advanced per-service header/body configuration.
- Database schema changes unless implementation finds a concrete persisted metadata need.

## UX Design

The MCP settings list page remains the entry point and continues showing saved MCP service cards with enable switches. The floating add button opens a provider-based add flow instead of the raw manual form.

The add flow:

1. User selects a provider from a dropdown.
2. User enters the provider API key once.
3. The screen lists the selected provider's MCP templates grouped by type:
   - Normal MCP
   - Coding Plan MCP
4. User checks one or more templates.
5. User taps `添加选中 MCP` to insert all selected services as enabled records.

The screen also exposes a secondary `手动添加` action for custom MCP services that are not included in presets.

Example layout:

```text
添加 MCP 服务

服务商
[ Minimax                         v ]

API Key
[ sk-...                            ]

普通 MCP
[x] Web Search        联网搜索与网页内容读取
[ ] Image             图片生成/处理能力

Coding Plan MCP
[x] Coding Plan       规划、代码任务辅助
[ ] Code Context      代码上下文检索

[ 手动添加 ]        [ 添加选中 MCP ]
```

## Data Model

Use a shared code-side preset catalog, for example `McpProviderCatalog`, containing:

- Provider id and display name.
- List of MCP templates.
- Template id, display name, description, type, transport, server URL or command, args, and environment variables.

Template type should be explicit, for example `NORMAL` and `CODING_PLAN`, so Minimax and GLM can expose both regular and coding-plan-specific MCP services without conflating them.

Persisted MCP records should keep the existing user-facing fields and add compact metadata columns because common official MCP servers are not always remote URLs. For example, MiniMax's official MCP configuration uses `uvx minimax-mcp` or `npx -y minimax-mcp-js` with environment variables.

- `name` as `Provider Name / Template Name`, for example `Minimax / Web Search`.
- `server_url` from template URL when the transport is remote; otherwise store the command label, such as `uvx minimax-mcp`.
- `api_key` from the entered provider API key.
- `enabled` defaults to true.
- `provider` stores the provider id.
- `template_id` stores the preset template id.
- `template_type` stores `normal` or `coding_plan`.
- `config_json` stores the resolved MCP configuration without exposing the API key in logs. The JSON includes command, args, env keys, transport, and any preset parameters such as API host.

Existing rows remain valid after migration because the new columns have non-null defaults.

## Components

- `McpProviderCatalog`: shared preset definitions and lookup helpers.
- `McpServerScreen`: list page and navigation between list, preset add, and manual edit modes.
- `McpServerPresetAddScreen`: provider dropdown, API key field, grouped selectable MCP templates, and batch insert action.
- Existing manual edit screen: retained for editing current records and adding custom services.

## Error Handling

- Disable batch add until provider, API key, and at least one MCP template are selected.
- If a selected template already exists by `server_url`, skip it to avoid silently overwriting an existing API key.
- Show a short result message after save, such as `已添加 3 个 MCP，跳过 1 个已存在服务`.
- Keep API key local to saved records and do not log it.

## Testing And Verification

- Add shared tests for preset catalog grouping and duplicate detection helper if introduced.
- Add UI-level logic tests only if existing project patterns make this practical; otherwise keep the logic small and test shared helpers.
- Run `./gradlew :app:compileDebugKotlin` after implementation.
- If code changes are deployed, run the project-required install command with the configured JDK and start the app on a connected device.

## Decisions

- First version uses common preset coverage rather than exhaustive provider catalogs.
- Provider preset data is bundled in code.
- Normal MCP and Coding Plan MCP are separate template types and separate UI groups.
- Manual MCP configuration remains available as an advanced fallback.
