# AI Chat Search Orchestrator Design

## Purpose

Improve AI assistant search quality during normal chat. The assistant should automatically find relevant local evidence from memory, diaries, articles, books, and book viewpoints, then answer with clickable references. This replaces the current top-bar memory search entry with better in-chat search behavior.

## Scope

In scope:

- Add a lightweight local search orchestration layer for AI chat queries.
- Use existing repositories and `memory_entry`; do not add database tables.
- Remove the AI chat top-bar memory search button and related sheet entry point.
- Keep existing chat input and message layout.
- Preserve clickable reference cards through `McpSearchResult`.
- Add regression tests for intent detection, keyword extraction, memory source conversion, result ranking, fallback answers, and top-bar search removal.

Out of scope:

- New search page or mode switch.
- Vector search, embeddings, or remote semantic indexes.
- Rebuilding the memory extraction pipeline.
- Changing the database schema.
- Redesigning the AI chat UI.

## Current Context

`AiChatViewModel` sends user messages to `McpAgentService.processQuery()`. The agent currently relies on model tool calls through `McpToolRegistry`. The prompt says to prefer `search_memory`, then use tools for diary, article, book, SQL, or web search. Search results are collected as `McpSearchResult`, persisted with chat messages, and rendered as expandable clickable reference cards.

The weakness is that search planning is model-dependent. The model can skip tools, choose the wrong tool, search only one domain, or produce incomplete references. `MemorySearchSheet` also duplicates the search idea as a separate top-bar action instead of improving chat itself.

## Architecture

Introduce `AiSearchOrchestrator` in shared code. It runs before the current agent tool loop and produces an `AiSearchPlan` plus ranked evidence results.

The orchestrator is deterministic and local:

- Analyze the user query for search intents.
- Extract 2 to 5 useful keywords.
- Search `memory_entry` first.
- Search diaries, articles, books, and book viewpoints based on intent.
- Convert memory entries with `source_type/source_id` into clickable `McpSearchResult` references when possible.
- Keep source-less core/chat memories as evidence only, not reference cards.
- Deduplicate and rank all results.
- Build an evidence prompt for AI summarization.

`McpAgentService.processQuery()` uses this layer as the first step. If local evidence exists, it gives the model the user query and evidence prompt, while still allowing the existing tool flow for statistics or external explanation requests. If the AI call fails but local evidence exists, it returns a fallback answer with the ranked clickable references.

## Intent Rules

Use simple keyword rules, not another AI call:

- Diary intent: query contains `日记`, `写过`, `心情`, `情绪`, `今天`, `昨天`, `前天`, `某天`, or `那天`.
- Article intent: query contains `文章`, `收藏`, `新闻`, `链接`, `读过`, `保存`, or `网页`.
- Book intent: query contains `书`, `读书`, `观点`, `笔记`, `摘录`, or `作者`.
- Memory intent: query contains `我之前`, `有没有提过`, `记得吗`, `找一下`, or `什么线索`.
- Statistics intent: query contains `多少`, `多久`, `最多`, `频率`, `趋势`, `最近几天`, or `最近几月`.

Default behavior:

- Always search memory first for non-empty user queries.
- If no specific content intent is detected and the query looks like recall/search, search all local content types.
- Statistics intent keeps the existing SQL tool path available and does not force raw record retrieval.
- External concept intent keeps the existing remote MCP web search path available after local context is found.

## Keyword Extraction

Extract useful search tokens from the user query:

- Remove filler words such as `帮我`, `找一下`, `有没有`, `之前`, `相关`, `内容`, `什么`, `哪些`, `一下`, `吗`, and `的`.
- Keep Chinese phrase chunks, English words, numeric dates, and mixed terms like `AI` or `GPT-5`.
- Return 2 to 5 keywords when possible.
- If extraction produces no useful tokens, search with the original trimmed query.

For each selected domain, search each keyword and merge the results. This avoids failures caused by searching only the full sentence.

## Recall Limits

Initial recall limits:

- Memory: 10 entries.
- Diary: 8 entries.
- Article: 8 entries.
- Book: 5 entries.
- Book viewpoint: 8 entries.

The final ranked evidence set sent to AI should contain at most 12 items. Reference cards shown under the assistant answer should contain at most 8 clickable results.

## Ranking

Each candidate receives a simple score:

- Title matches a keyword: +5.
- Summary/content matches a keyword: +3.
- Candidate comes from memory and resolves to an openable original source: +2.
- Article is favorite: +2.
- Candidate was created in the last 30 days: +1.
- Candidate type matches the primary detected intent: +3.

Sort by descending score, then by newest timestamp when available. Deduplicate by `type + id`, keeping the highest scoring candidate.

## Evidence Prompt

When local evidence exists, build a compact text block for the model:

```text
用户问题：...

已找到的本地证据：
[article_12] 文章｜标题｜日期｜摘要...
[diary_8] 日记｜2026-05-30｜内容片段...
[book_viewpoint_3] 读书笔记｜书名｜观点标题｜内容片段...
[core_memory_5] 记忆｜偏好｜内容...
```

The prompt must instruct the model:

- Answer only from the evidence.
- Do not invent missing facts.
- If evidence is insufficient, say what was not found.
- End with `<!-- refs: ... -->` using only openable reference IDs.

## Reference Behavior

Clickable references use `McpSearchResult`:

- Article evidence maps to `type=article` and opens article detail.
- Diary evidence maps to `type=diary` and opens diary reference detail.
- Book evidence maps to `type=book` and opens book/first viewpoint reference detail.
- Book viewpoint evidence maps to `type=book` with the viewpoint id, matching current detail-loading behavior.
- Memory entries with source `article`, `diary`, `book`, or `book_viewpoint` convert to the corresponding openable result when the source exists.
- Memory entries with source `chat`, no source, or deleted source remain evidence-only and do not create reference cards.

## UI Behavior

Remove the top-bar memory search action from `AiChatScreen`:

- No search icon in the title bar.
- No `MemorySearchSheet` entry from the AI chat screen.
- Keep existing assistant welcome content, input bar, thinking indicator, message bubbles, and reference cards.

Reference cards remain expandable under assistant messages. They should be populated from orchestrated local results when the model answer references them or when fallback answer is used.

## Failure Handling

- If AI is not configured and local evidence exists, return a local fallback answer with reference cards and a short note that AI configuration is required for richer synthesis.
- If AI is not configured and no local evidence exists, keep the existing AI configuration error message.
- If one local source search fails, ignore that source and keep evidence from other sources.
- If no evidence is found, answer with `在您的数据中没有找到相关信息。` and no reference cards.
- If AI request fails and evidence exists, return `buildFallbackAnswer(query, rankedResults)` with ranked references.

## Testing Strategy

Add source-level or unit-style tests following current project patterns:

- Diary-intent queries trigger memory plus diary search planning.
- Article-intent queries trigger memory plus article search planning.
- Book-intent queries trigger memory plus book and book viewpoint search planning.
- Generic recall queries trigger memory and all local content searches.
- Keyword extraction removes filler words and preserves useful terms.
- Memory entries with openable source types convert into clickable references.
- Core/chat memory without an openable source stays evidence-only.
- Ranking prefers title matches, matching intent, favorites, and recent content.
- AI chat top-bar search action and `MemorySearchSheet` entry are removed from `AiChatScreen`.

## Acceptance Criteria

- Normal chat queries automatically search relevant local data before AI answering.
- The assistant can surface clickable references for diaries, articles, books, and book viewpoints.
- Memory improves search quality but does not create dead reference cards when no openable source exists.
- The top-bar memory search button is removed.
- Existing AI chat message layout and input flow are preserved.
- No database schema change is required.
