# Hybrid Book Intelligence Design

## Goal

Make the reading module faster and more useful by turning a book title into a complete reading entry: reliable online book candidates, one-tap add, and 10 concise viewpoint cards that help the user understand the book quickly.

## Current State

The current book search flow already has `BookSearchScreen`, `BookSearchViewModel`, and `McpAgentService.searchBookOnline()`. The search result UI shows title, author, introduction, cover, and an add button. Adding a result only inserts the book record. It does not generate viewpoints.

The current search implementation asks the default AI model to produce book candidates from a prompt. It does not use the configured MCP web/search capability, so results can be less grounded in current online sources.

The reading module already stores viewpoints in `book_viewpoint` with `title`, `content`, and `example`, which matches the desired output structure.

## Scope

In scope:

- Use a hybrid pipeline: MCP retrieves online source material, default AI turns the material into structured results.
- On Android, only remote HTTP MCP endpoints can be called directly. Local command MCP presets such as MiniMax `uvx`/`npx` cannot run inside the phone app, so the first version must fall back to AI-only source gathering when only local-command MCP configs are available.
- Improve book search candidates with title, author, category, introduction, cover URL, and source summary.
- Change the search result action from simple add to add-and-analyze.
- After adding a selected book, generate exactly 10 important viewpoints when enough source material is available.
- Store each viewpoint as one `book_viewpoint` row:
  - `title`: one-sentence viewpoint.
  - `content`: explanation.
  - `example`: concrete case/example.
- Show clear progress while analyzing.
- Preserve partial results if fewer than 10 usable viewpoints are generated.
- Allow retrying viewpoint generation for an added book that has no viewpoints or failed analysis.

Out of scope for this iteration:

- Full-text book ingestion.
- Paid/book-copyright content extraction.
- Background job scheduling after app close.
- Editing generated viewpoints in the same flow.
- Database schema changes unless implementation finds a concrete persisted state need.

## User Experience

### Search

The user opens book search, enters a book title, and taps search. The UI shows candidate book cards with:

- Book title.
- Author.
- Short introduction.
- Cover when available.
- Optional category.
- Optional source confidence/source summary.

Each card uses `添加并分析` as the primary action.

### Add And Analyze

After tapping `添加并分析`:

1. Insert the book record.
2. Select the new book in the reading module.
3. Start analysis immediately.
4. Show step text while work runs:
   - `正在搜索书籍资料`
   - `正在提炼核心观点`
   - `正在生成观点卡片`
5. When complete, return to the reading page and display the generated viewpoint cards.

If analysis fails, keep the book and show a retry action such as `重新生成观点`.

### Viewpoints

The generated reading cards should be concise and practical. Each card contains:

- A one-sentence viewpoint that can be remembered quickly.
- A short explanation of the idea.
- A concrete example or application case.

The goal is not to summarize every chapter. The goal is to give the user the fastest useful understanding of the book.

## Architecture

Add a focused shared service, for example `BookIntelligenceService`, responsible for online source gathering and AI structuring. Keep UI state orchestration in `BookSearchViewModel` and persistence in existing repositories.

Recommended responsibilities:

- `BookIntelligenceService.searchBooks(query)`: use MCP-backed web search when available, then ask AI to produce structured `BookSearchResult` candidates.
- `BookIntelligenceService.generateViewpoints(book)`: use MCP-backed online search for the chosen book, then ask AI to produce up to 10 structured viewpoint objects.
- `BookSearchViewModel`: drives search, add, analysis progress, and error state.
- `BookRepository`: persists the selected book.
- `BookViewpointRepository`: persists generated viewpoints.

The existing `McpAgentService.searchBookOnline()` can either be replaced internally by the new service or adapted to delegate to it. The implementation should avoid adding more responsibilities to the already-large `McpAgentService` if a smaller service is practical.

`BookRepository` should expose an insertion path that returns the inserted book id, or an equivalent deterministic lookup by inserted title/author immediately after insert. Returning the inserted id is preferred because it lets the UI select the new book and attach generated viewpoints without guessing.

## Hybrid Retrieval And AI Flow

### Book Candidate Search

Input: user query.

1. Build search queries such as:
   - `<query> 书 作者 简介`
   - `<query> book author summary`
   - `<query> 豆瓣 维基 百科 目录`
2. Use configured remote MCP search/read tools where possible to collect snippets and URLs.
3. If only local-command MCP configs are available, or remote MCP calls fail, fall back to the default AI-only prompt.
4. Ask AI to output JSON array with:
   - `title`
   - `author`
   - `category`
   - `introduction`
   - `isbn`
   - `coverUrl`
   - `sourceSummary`
5. Parse defensively and drop empty-title candidates.

### Viewpoint Generation

Input: selected `BookSearchResult` or persisted `Book`.

1. Search online for summary, table of contents, reviews, notes, and key ideas.
2. Build an AI prompt requiring exactly 10 viewpoints when source material supports it.
3. Require JSON array output with:
   - `title`
   - `content`
   - `example`
4. Parse defensively.
5. Save valid rows. If fewer than 10 valid rows exist, save them and report the count.

## Error Handling

- If no candidates are found, show `未找到可靠书籍资料，请换个关键词再试`.
- If book insertion succeeds but analysis fails, keep the book and show `分析失败，可重新生成观点`.
- If fewer than 10 viewpoints are generated, show `已生成 N 个观点，可稍后重试补全`.
- Do not log API keys or full MCP configuration.
- Network/MCP failures should not crash the UI; they should downgrade to AI-only fallback when possible.
- Local command MCP configs are treated as unsupported on Android runtime and should not block book search or viewpoint generation.

## Testing And Verification

- Add shared tests for JSON parsing of book candidates and viewpoints.
- Add shared tests for prompt policy or source fallback helpers if introduced.
- Add ViewModel/unit tests for add-and-analyze state transitions where existing test patterns support it.
- Run `./gradlew :app:compileDebugKotlin` after implementation.
- Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug` and launch the app for device testing.

## Decisions

- First version uses remote MCP source gathering plus AI structuring when remote MCP is callable from Android.
- MiniMax local command MCP configs fall back to AI-only source gathering on Android.
- Viewpoint storage reuses existing `book_viewpoint` schema.
- The primary CTA becomes `添加并分析`.
- Analysis generates up to 10 viewpoint cards and preserves partial usable output.
- Existing AI-only behavior remains as fallback, not the primary path.
