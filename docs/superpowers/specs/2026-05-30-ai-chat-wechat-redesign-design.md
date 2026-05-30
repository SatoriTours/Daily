# AI Chat WeChat Redesign Design

## Purpose

Redesign the AI assistant into a quiet WeChat-style chat experience. The screen should feel like a familiar mobile conversation: user messages on the right, assistant messages on the left, smooth bottom-up conversation flow, and true streaming output while preserving local search references.

## Scope

In scope:

- Redesign AI chat message presentation to use left/right chat bubbles.
- Fix short user messages such as `最近一条新闻是什么？` appearing visually centered in the row.
- Remove the assistant message left editorial rail and `AI 回复` kicker.
- Render Markdown and reference sources inside the assistant bubble.
- Add true streaming answer output for the final assistant response.
- Preserve existing tool/status steps while the assistant searches or queries local data.
- Improve scroll performance for long chat history and streamed assistant text.
- Add regression tests for alignment, rail removal, streaming state, and list performance constraints.

Out of scope:

- Redesigning other top-level tabs.
- Changing AI provider settings UI.
- Replacing the local search orchestrator.
- Adding a new standalone search page.
- Persisting partial streamed messages if generation is cancelled before an assistant message is created.

## Current Problems

The current AI chat screen mixes an editorial-note style with chat behavior. User messages are placed in a full-width row with a percentage-width column, so short messages can look like they sit near the middle instead of clearly on the right. Assistant messages use a note-like structure with a left rail and kicker, which does not match the desired WeChat-style interaction.

The assistant response currently appears only after `McpAgentService.processQuery()` returns a complete answer. This makes long answers feel slow and opaque. During generation, the UI only shows a generic thinking/status bubble.

Performance issues come from several sources:

- `aiChatDisplayMessages(messages).asReversed()` creates a new list for display.
- Markdown rendering can be expensive during frequent updates.
- Reference cards can add nested layout work inside each message.
- Message bubble composition has extra wrappers and decoration layers.

## UX Direction

The chosen direction is quiet WeChat-style chat:

- Conversation-first, not document-first.
- User messages are compact right-side bubbles.
- Assistant messages are left-side bubbles with readable Markdown.
- Visual styling stays calm and low-contrast, aligned with Daily Satori's personal-note character.
- The interface should feel fast, familiar, and practical rather than decorative.

## Message Layout

User messages:

- Align to the right edge of the conversation column.
- Use wrap-content width with a max width around 78 percent of the available row.
- Use a subtle primary-tinted or surface-container background.
- Use normal body typography; no title/kicker.

Assistant messages:

- Align to the left edge of the conversation column.
- Use wrap-content width with a max width around 86 percent.
- Use a neutral surface-container background.
- Remove the left vertical rail.
- Remove the `AI 回复` kicker.
- Keep the message content directly readable inside the bubble.

System/status messages:

- Keep search/tool progress visible as small left-side status bubbles, for example `正在查询数据...`.
- When final streaming starts, replace the generic thinking bubble with the live assistant bubble.

## Markdown And References

Assistant bubble content should support Markdown after the answer is complete.

Streaming state:

- Render streamed text as plain `Text` while chunks are arriving.
- Avoid running Markdown rendering on every token or small chunk.

Completed state:

- Render the final assistant content with the existing Markdown renderer.
- Keep citations and reference cards inside the same assistant bubble.
- Show references as a collapsed row by default: `引用来源 · N 条`.
- Render full reference cards only after the user expands the reference section.

This keeps chat interaction familiar while reducing initial layout work.

## True Streaming Output

Add a streaming path in shared AI chat services.

High-level flow:

1. User sends a message.
2. `AiChatViewModel` appends the user bubble immediately.
3. Tool/local-search phase runs with status updates.
4. When final answer generation begins, the ViewModel appends a temporary assistant message with `isStreaming = true`.
5. Each received text chunk updates that assistant message content.
6. When streaming completes, the message switches to `isStreaming = false`, references are attached, Markdown rendering is enabled, and the final assistant message is persisted.

Service design:

- Keep existing `processQuery()` for non-streaming fallback and tests.
- Add a streaming API such as `processQueryStreaming(query, onStep, onChunk)`.
- Reuse local search orchestration and tool execution from the existing path.
- Stream only the final assistant response. Tool-call rounds can remain request/response.
- If the AI provider or response format does not support streaming, fall back to current complete-answer behavior.

Provider behavior:

- Support OpenAI-compatible SSE responses where possible.
- Parse incremental content chunks defensively.
- Treat malformed stream events as non-fatal if a fallback full response is available.
- Preserve privacy masking/restoration behavior around tool outputs and final answer text.

## State Model

Extend chat UI state with streaming-aware message data.

`ChatMessageUi` should include enough information to render efficiently:

- `id`
- `role`
- `content`
- `timestamp`
- `isError`
- `isStreaming`
- `searchResults`
- `steps`

State update rules:
- User messages are appended once.
- A streaming assistant message is created once per request.
- Chunk updates replace only that message's content.
- Finalization updates that message with final content, references, steps, and `isStreaming = false`.
- Cancelled generation should stop streaming and show `已停止生成` without persisting an incomplete assistant answer unless meaningful content exists.

## Scroll Behavior

The chat should behave like a normal messaging app:

- New messages appear at the bottom.
- Sending a message scrolls to the bottom.
- Streaming keeps the bottom anchored only if the user is already near the bottom.
- If the user scrolls upward during streaming, do not force-scroll every chunk.
- Older messages load when scrolling near the top.

Implementation may keep `LazyColumn(reverseLayout = true)` if it remains simpler with the existing paging model, but the visual result must be bottom-anchored and right/left aligned like chat. If `reverseLayout` causes alignment or scroll anchoring issues, switch to normal order and prepend older messages explicitly.

## Performance Requirements

- Avoid creating a reversed message list on every recomposition.
- Keep stable `LazyColumn` keys based on message id.
- Keep `contentType` for user, assistant, and status rows.
- Do not render Markdown while an answer is streaming.
- Do not render full reference cards until the reference section is expanded.
- Avoid `IntrinsicSize` around chat message content.
- Reduce message bubble nesting and remove decorative draw work that is not needed.
- Use `derivedStateOf` or small helper functions where they prevent broad recomposition.

## Error Handling

- If streaming fails before any content arrives, use the existing non-streaming fallback answer path.
- If streaming fails after partial content arrives, show the partial content and append a short error note inside the assistant message or as a small status bubble.
- If AI service is not configured, keep existing configuration error behavior and local fallback behavior.
- If references arrive after streaming completes, attach them to the assistant bubble without changing the user message.

## Testing Strategy

Add or update unit/source tests for:

- User message layout uses right alignment and wrap-content/max-width behavior, not a fixed percentage column that visually centers short text.
- Assistant message layout has no left rail and no `AI 回复` kicker.
- Assistant streaming message renders plain text while `isStreaming = true`.
- Completed assistant message can render Markdown and references inside the bubble.
- Reference section defaults collapsed and renders cards only after expansion state allows it.
- Display list handling avoids repeated `asReversed()` creation in the composable path.
- Streaming state updates replace the active assistant message instead of appending a new message per chunk.
- Stop generation leaves the state idle with the stopped status.

Manual verification on device:

- Ask `最近一条新闻是什么？` and confirm user bubble is right aligned.
- Ask a longer question and confirm assistant text streams progressively.
- Confirm the assistant bubble has no left rail.
- Expand references inside the assistant bubble.
- Scroll during streaming and confirm the list remains smooth and does not fight user scrolling.

## Acceptance Criteria

- The AI assistant looks and behaves like a quiet WeChat-style chat screen.
- Short user messages are clearly right aligned and no longer appear in the middle of the row.
- AI replies stream progressively when the provider supports streaming.
- Non-streaming fallback still works.
- Assistant replies no longer show a left vertical rail.
- Markdown and references appear inside the assistant bubble after completion.
- Scrolling long histories feels smoother, with less recomposition and fewer heavy layouts during streaming.
- Existing local search references, tool calls, persistence, delete, copy, and re-ask behaviors continue to work.
