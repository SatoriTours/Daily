# AI Chat Foundation Design

## Goal

Improve the AI assistant chat as the foundation for a future comprehensive personal assistant. This first iteration focuses on making the existing chat reliable and understandable before adding larger session-management or action-workspace features.

## Current Problems

- The top-right refresh action deletes the current session, which feels destructive and surprising.
- Sending a message disables the input and gives users no way to stop a long request.
- Empty assistant responses are rendered as assistant bubbles, producing a dark blank block.
- Failures are mixed into normal message rendering instead of being shown as clear recovery states.
- Recent chat history exists in storage, but the UI does not clearly communicate that history is preserved.

## Scope

This iteration will implement the minimal stable chat experience:

- Remove the refresh button from the primary chat top bar.
- Keep conversation history by default and continue loading the latest stored session on entry.
- Add an explicit stop control while the assistant is generating.
- Do not persist or render empty assistant messages.
- Show readable assistant error or stopped states instead of blank bubbles.
- Keep memory search and reference-detail behavior unchanged.

Out of scope for this iteration:

- Full session list UI.
- Multi-session delete/rename management.
- Plugin/action workspace UI.
- Streaming token-by-token responses.

## Interaction Design

The top bar keeps the title `AI 助手` and the memory search action. The refresh icon is removed to avoid implying reload or destructive clearing.

The input bar remains visible during processing. While idle, the trailing control sends non-blank input. While processing, the trailing control becomes a stop action with an accessible description such as `停止生成`. The text field can remain enabled so the user can draft the next question, but sending a new message waits until the current request is idle.

The message list shows the user's message immediately after send. While the assistant is working, a thinking/status row shows the current processing step when available, otherwise a calm default such as `正在思考...`.

If the user stops generation, the in-flight work is cancelled and the list shows a small assistant-side status card: `已停止生成`. This status is not treated as a normal AI answer and does not need references.

If the AI service returns a blank answer, the app shows a friendly error card: `这次没有生成有效回复，请稍后重试。` It should not create an empty dark bubble. If the service throws or returns a known error message, the existing user-friendly error copy can be displayed in the same error-card treatment.

## State And Data Flow

`AiChatViewModel` remains the owner of chat state. It should track the active request job so `stopGeneration()` can cancel it. Processing state should be explicit enough for the UI to distinguish idle, generating, stopped, and error display states without relying on blank message content.

User messages continue to be persisted when sent. Assistant messages are persisted only when they contain non-blank answer content or meaningful error content. Stopped placeholders and blank-response placeholders should not create permanent chat rows.

The existing `ChatConversationRepository` and `chat_conversation` schema are sufficient for this iteration, so no database migration is required.

## Error Handling

- Missing AI configuration remains a readable assistant-side error state.
- Network or provider failures remain readable assistant-side error states.
- Cancellation from user stop is not shown as an exception and should not log as a failure path.
- Empty responses are converted into a friendly retry message instead of a blank assistant message.

## Testing And Verification

Add or update focused tests where practical for pure ViewModel behavior:

- Sending a message appends and persists the user message.
- Blank assistant answers do not render or persist as empty bubbles.
- Stopping generation cancels the active job and returns the UI to idle.
- Clearing/destructive refresh is not exposed from the chat top bar.

Manual verification on Android should cover:

- Ask a normal question and confirm the assistant answer renders with references.
- Ask while processing and confirm the stop control appears.
- Stop a long request and confirm no blank assistant bubble appears.
- Leave and return to AI tab and confirm recent chat history remains.
- Confirm memory search still opens from the top bar.

## Implementation Notes

Keep changes focused in `ui/feature/aichat/` unless tests require small fakes or interfaces. Preserve the existing design system imports and avoid hard-coded colors, spacing, or typography. Any new icons must have content descriptions.
