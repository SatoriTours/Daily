# Daily Satori Project Refactor Design

## Goal

Refactor the project in small, behavior-preserving stages so code is easier to understand, files and directories have clearer ownership, and duplicated UI/service logic is reduced. Existing functionality, navigation, data model, and major UI layout must remain unchanged.

## Non-Goals

- Do not redesign screens or change primary user flows.
- Do not change database schema or migrations.
- Do not rename routes or alter navigation behavior.
- Do not rewrite services wholesale.
- Do not introduce compatibility layers unless an external or persisted contract requires them.

## Constraints

- Keep feature behavior unchanged.
- Keep UI visually equivalent except small consistency improvements using the existing theme system.
- Use `import com.dailysatori.ui.theme.*` and avoid hardcoded colors, spacing, typography, and radius values.
- Keep functions short and focused, targeting the project rule of 50 lines or less.
- Prefer local feature components before promoting anything to `ui/component`.
- Run `./gradlew :app:compileDebugKotlin` after each implementation phase.

## Current Hotspots

- `app/.../ui/feature/book/BooksScreen.kt` is a large Compose file mixing screen orchestration, bottom sheets, search UI, delete UI, inline add UI, and helper functions.
- `app/.../ui/feature/settings/` mixes independent domains such as backup, import, plugins, MCP servers, and weekly summaries in one flat directory.
- `app/.../ui/feature/settings/McpServerScreen.kt` contains UI state, form logic, repository access, and IO work in one screen file.
- `shared/.../service/parser/WebpageParserService.kt` mixes prompts, parsing helpers, image handling, queueing, refresh flows, and status updates.
- `shared/.../service/mcp/McpAgentService.kt` and `McpToolRegistry.kt` contain multiple responsibilities around prompts, tool dispatch, result formatting, and domain-specific tool logic.
- Several screens repeat card, form, and scaffold patterns that can be consolidated without visible UI changes.

## Recommended Approach

Use a steady, low-risk phased approach. Each phase should produce a compiling project and a reviewable diff. Avoid combining UI file splits, directory moves, and service extraction in one large change.

## Phase 1: UI File Splitting

Start with pure Compose extraction in feature directories. The first target is `BooksScreen.kt` because it is large and contains many separable UI sections.

Expected structure:

```text
app/src/main/kotlin/com/dailysatori/ui/feature/book/
├── BooksScreen.kt
├── BooksViewModel.kt
├── component/
│   ├── BookPickerSheet.kt
│   ├── BookSearchResultCard.kt
│   ├── BooksContentSearchPanel.kt
│   ├── BooksInlineAddPanel.kt
│   ├── BooksTopBar.kt
│   └── SwipeDeleteBookRow.kt
```

Rules:

- Keep `BooksScreen.kt` as the orchestration entry point.
- Move only UI helpers and small presentation functions first.
- Do not change ViewModel state shape or repository calls in this phase.
- Keep existing public Composable names when used from navigation.

## Phase 2: Settings Directory Organization

Split the flat settings feature directory by independent settings domain.

Expected structure:

```text
app/src/main/kotlin/com/dailysatori/ui/feature/settings/
├── SettingsScreen.kt
├── SettingsViewModel.kt
├── backup/
├── importing/
├── mcp/
├── plugin/
└── weekly/
```

Rules:

- Move files without changing behavior first.
- Update imports and DI registrations only as required by package moves.
- Keep navigation route constants unchanged.
- Keep settings home layout visually equivalent.

## Phase 3: Reusable UI Components

Extract only components with clear repeated use.

Candidates:

- `SettingsSectionCard` for repeated settings section card patterns.
- A small app text field wrapper only if it reduces repeated field styling across MCP, AI config, backup restore, and share dialog.
- Prefer existing `CustomCard`, `AppScaffold`, `AppTopBar`, `SearchBar`, and `SettingsRow` before adding new components.

Rules:

- Do not create broad abstractions for one-off UI.
- Keep feature-local components feature-local.
- Any visual change must be limited to consistency with existing theme constants.

## Phase 4: ViewModel Boundary Cleanup

Move UI-owned repository access and IO work into ViewModels where screens currently do too much.

Initial targets:

- `McpServerScreen.kt`
- `DataImportScreen.kt`
- `AiConfigEditScreen.kt`

Rules:

- Preserve screen events and state behavior.
- Use Koin constructor injection for ViewModels.
- Use `MutableStateFlow` privately and expose `StateFlow`.
- Keep repository APIs unchanged unless a smaller private helper is needed.

## Phase 5: Shared Service Extraction

Extract pure helpers from large shared services after UI and directory cleanup.

Initial targets:

- `WebpageParserService.kt`: prompts, parsing helpers, Twitter parsing, and image helper logic.
- `McpAgentService.kt`: prompt building, answer formatting, and tool result shaping.
- `McpToolRegistry.kt`: domain-specific tool definitions for article, diary, book, memory, and database tools.

Rules:

- Keep public service APIs stable.
- Extract deterministic helpers before changing orchestration code.
- Avoid changing prompt text unless the extracted constant preserves exact content.
- Keep tool names, arguments, and response structure unchanged.

## Validation Plan

For every phase:

```bash
./gradlew :app:compileDebugKotlin
```

For phases touching navigation, services, workers, or app startup:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb shell am start -n com.dailysatori/.MainActivity
```

Manual checks should focus on the touched feature only:

- Book list/search/add/delete after Phase 1.
- Settings sub-pages after Phase 2 and Phase 4.
- Article parsing and AI/MCP flows after Phase 5.

## Risk Controls

- Keep each diff small and reversible.
- Do not refactor unrelated code while touching a file.
- Do not rename persisted data, SQLDelight queries, routes, or tool names.
- Stop and investigate if compilation or behavior changes appear after a move.
- Prefer extraction over rewrite.

## Success Criteria

- Large screen/service files are smaller and responsibility-focused.
- Settings files are grouped by domain.
- Repeated UI patterns use existing or minimal shared components.
- Screens avoid direct repository access where a ViewModel should own state and IO.
- The app compiles after every phase.
- Existing user-visible behavior and major UI structure remain unchanged.
