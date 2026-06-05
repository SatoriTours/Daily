# Book AI Reflection Design

## Goal

Add a focused AI reflection feature inside the reading module. The feature helps the user discuss a specific book viewpoint when the idea is not fully understood, preserve the conversation process, and manually distill each discussion segment into a reusable summary.

The feature is attached to `book_viewpoint`, not to a whole book and not to the global AI chat. Each viewpoint can have multiple user-created reflection segments. A segment represents one topic or thinking period.

## Non-Goals

- Do not replace the existing global AI chat.
- Do not add a global entry for reading reflection in the first version.
- Do not automatically split conversations by time.
- Do not automatically summarize after every AI reply.
- Do not add tags, favorites, ratings, manual title editing, exports, or cross-viewpoint search in the first version.
- Do not automatically write reflection summaries into diary or memory entries in the first version.

## Current Context

The reading module currently displays `book_viewpoint` records in `BooksScreen` through `ViewpointCard`. Existing inline sheets support adding books and searching book content. The app already has AI configuration, streaming AI chat, MCP search, and chat persistence, but the global `chat_conversation` model is not a good fit for viewpoint-owned reading reflection records.

The new feature should reuse existing AI infrastructure where possible while keeping reading reflection records in dedicated tables.

## User Experience

### Entry

Add a restrained entry on the current viewpoint card, such as `深入想想`. The entry opens a reading AI reflection panel for the current `book_viewpoint`.

The reading page should remain quiet. The card should not expose multiple AI actions directly. Secondary actions live inside the panel.

### Panel Layout

The panel opens as a bottom sheet or equivalent reading-scoped overlay.

It contains:

- Header with viewpoint title and book title.
- Collapsed current viewpoint context; tapping expands the viewpoint content and example.
- Current reflection segment chat.
- A compact summary card when the current segment has a summary.
- Input bar with placeholder text such as `问问这个观点哪里还没想透...`.
- Actions: `沉淀这一段`, `换个角度聊`, and `历史`.

### Default Behavior

When opening the panel for a viewpoint:

- If the user previously opened a segment for this viewpoint, return to that segment.
- Otherwise, open the latest unsummarized segment.
- If there is no segment, create an empty segment automatically.
- If the latest segment is already summarized, keep showing it and make `换个角度聊` easy to discover.

The user should not need to choose, name, or configure a segment before asking a question.

Track the last opened segment by updating the selected session's `last_opened_at`. This avoids guessing from message timestamps and makes reopening deterministic.

### Starting Prompts

For an empty segment, show three preset prompts:

- `这个观点我可能漏掉了哪些角度？`
- `帮我用更具体的例子解释一下`
- `你反问我几个问题，帮我想清楚`

These prompts fill and send the input. Do not add more prompt templates in the first version.

### Segment Actions

`换个角度聊` creates a new segment under the same viewpoint. If the current segment has no summary, show a light hint that it has not been distilled yet, but do not block the action.

`沉淀这一段` generates or updates the current segment summary. If a summary already exists, the button label changes to `更新沉淀`. A segment has only one current summary; repeated summary generation overwrites the previous summary and updates `summarized_at`.

`历史` opens a compact segment list. History is summary-first:

- Segment title.
- Summary preview.
- Last updated time.
- State: unsummarized, summarized, summary failed.
- Actions: `继续聊` and `查看过程`.

History should not display raw chat logs by default because that makes review noisy.

## Data Model

Add dedicated SQLDelight tables.

```sql
CREATE TABLE book_viewpoint_ai_session (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    viewpoint_id INTEGER NOT NULL REFERENCES book_viewpoint(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    summary TEXT NOT NULL DEFAULT '',
    summary_status TEXT NOT NULL DEFAULT 'none',
    summary_error TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    last_opened_at INTEGER NOT NULL,
    summarized_at INTEGER
);

CREATE TABLE book_viewpoint_ai_message (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id INTEGER NOT NULL REFERENCES book_viewpoint_ai_session(id) ON DELETE CASCADE,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ready',
    error_message TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL
);
```

`summary_status` values:

- `none`: no summary has been requested.
- `generating`: summary generation is running.
- `ready`: summary is available.
- `failed`: summary generation failed.

`message.status` values:

- `ready`: normal saved message.
- `streaming`: assistant message currently streaming.
- `failed`: assistant generation failed.

The migration must increment `currentSchemaVersion` and add a versioned migration in `DatabaseMigration`. Deleting a book viewpoint must cascade-delete its reflection sessions and messages.

## Repository And ViewModel Boundaries

Add a reading-specific repository, for example `BookViewpointAiRepository`, responsible for:

- Loading sessions by viewpoint.
- Creating sessions.
- Loading messages by session.
- Inserting user and assistant messages.
- Updating session title, summary, summary status, and timestamps.
- Marking a session as last opened.
- Deleting sessions if a future UI needs it.

Add a reading-specific ViewModel, for example `BookReflectionViewModel`, responsible for:

- Opening a viewpoint reflection context.
- Selecting or creating the active segment.
- Sending messages.
- Retrying the latest failed assistant response.
- Creating a new segment.
- Generating or updating the segment summary.
- Loading history segments.

Do not reuse `AiChatViewModel` directly. Its session model and screen behavior are global-chat oriented. Shared helper functions and UI components can be reused when they fit.

## AI Behavior

The AI must behave like a reading-thinking companion, not a generic assistant.

Each user message should include fixed context:

```text
书名、作者
当前观点标题
观点正文
观点例子
当前观点下已有片段标题和总结
当前片段最近若干条消息
用户本次问题
```

Default answer shape:

- Explain the core point briefly.
- Add 2 to 3 missing angles or blind spots.
- End with 1 to 2 questions that help the user continue thinking.

Answers should be concise by default. The user can continue asking if they want depth.

The service can reuse existing AI configuration and MCP/local search capability. Record ownership stays in reading reflection tables even if AI uses MCP search internally.

## Summary Behavior

Summary generation is manual. It runs only when the user taps `沉淀这一段` or `更新沉淀`.

The summary output should use a fixed structure:

```text
我理解到的核心：
我补上的角度：
还值得继续想的问题：
```

The summary should distill the user's thinking progress, not merely summarize the assistant response. It should include useful unresolved questions.

On success:

- Save `summary`.
- Set `summary_status = 'ready'`.
- Clear `summary_error`.
- Update `summarized_at` and `updated_at`.
- Update the segment title from the summary core sentence when that sentence is nonblank.

On failure:

- Keep all chat messages unchanged.
- Set `summary_status = 'failed'`.
- Save a short `summary_error`.
- Keep the previous summary if one existed.

## Title Behavior

Do not require manual title editing in the first version.

- New empty segment title: `新的思考`.
- After the first user message, use the first meaningful part of the question as a temporary title.
- After summary generation, update the title from the summary's core sentence when available.

This keeps history usable without adding a title-management workflow.

## Failure And Recovery

User messages are saved before AI generation starts. If the assistant call fails, keep the user question and add or update a failed assistant message with a concise error.

Support retrying the latest failed assistant response or the last user message in the active segment. The first version only needs to support retry for the current segment's latest user question.

Stopping generation should not delete prior ready messages. If a streaming assistant message is cancelled before producing useful content, remove the streaming placeholder or mark it as stopped consistently with the existing AI chat behavior.

Summary failure must not block chat continuation or new segment creation.

## Backup And Restore

Because the app backs up the database, the new tables should be included automatically if backup uses the full database file. Verification should confirm that after backup and restore, reflection sessions, messages, and summaries remain available.

## Privacy Boundary

The feature sends the current viewpoint context and user questions to the configured AI provider, consistent with existing AI chat behavior. It should reuse the existing AI configuration and should not introduce a separate provider setting.

## Testing And Verification

Unit-level tests should cover pure helpers where practical:

- Segment selection priority.
- Summary button label.
- Summary status transitions.
- Title derivation from user question or summary.
- Retry eligibility for latest failed assistant response.

Integration or manual verification should cover:

- Opening a viewpoint creates or selects the correct segment.
- Sending a message saves user and assistant messages.
- AI failure preserves the user question and allows retry.
- Manual summary creates a structured summary.
- Updating summary overwrites the previous summary.
- Starting a new segment does not require summarizing the old one.
- History shows summaries before raw chat.
- Deleting a viewpoint removes related sessions and messages.
- Backup and restore keep reading reflection data.

After implementation, run:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb shell am start -n com.dailysatori/.MainActivity
```

## First Version Scope

Build only the focused loop:

```text
读书观点卡片
  -> 深入想想
    -> 当前片段聊天
    -> 沉淀这一段
    -> 换个角度聊
    -> 历史沉淀列表
```

This creates a complete reflection workflow while keeping the reading page simple.

## Future Extensions

- Search all reading reflection summaries.
- Export all reflections for one book.
- Write a reflection summary into diary.
- Add selected summaries to memory.
- Link related reflection segments across viewpoints.
- Allow manual title editing.
