# WeRead AI Fallback Design

## Goal

Keep the book search path anchored to WeRead while allowing books with insufficient WeRead reading material, including pending or not-yet-listed books returned by WeRead search, to still produce useful viewpoint cards through the app's default AI model.

## Scope

- Book search remains WeRead-only.
- A book can be added if it appears in WeRead search results.
- Viewpoint generation first uses WeRead book info, chapters, and reviews.
- AI fallback runs only when WeRead material is insufficient for reliable viewpoint generation.
- AI-generated viewpoints must be clearly identified as not coming from WeRead.
- No database schema migration is required.

## Non-Goals

- Do not use AI to search for books that WeRead did not return.
- Do not restore the previous Douban, Wikipedia, or generic MCP fallback path.
- Do not add another user setting for fallback behavior.
- Do not store a per-viewpoint source flag in the database in this iteration.

## Behavior

The reading page searches by calling the WeRead Skill gateway. Search results continue to show WeRead metadata and links.

When the user adds a book, the app fetches WeRead book info, chapters, and reviews. The WeRead path is considered sufficient when it has enough concrete source material to build 10 useful cards. A practical sufficiency check is:

- book title is present;
- at least one of intro, chapters, or reviews contains meaningful text;
- generated draft count reaches 10;
- generated draft content and examples meet the existing minimum detail expectations.

If the sufficiency check fails, the service calls the default AI configuration through `AiConfigService` and `AiService`. The AI prompt uses only metadata already available from WeRead search or detail calls, such as title, author, intro, category, chapter titles, and any reviews returned. The prompt must instruct the model to return exactly 10 JSON objects with `title`, `content`, and `example`.

If no default AI configuration exists, or the default API token is blank, the add flow fails with a clear analysis error instead of silently saving empty or low-quality cards.

## User Feedback

The app must visibly mark fallback output as AI-generated. The completion message should append:

`观点由 AI 生成，非微信读书内容`

Example:

`《供应链架构师》已添加，10 个观点已生成（观点由 AI 生成，非微信读书内容）`

This message is enough for the current iteration because the app already shows an inline analysis result after adding a book. Existing saved viewpoint rows do not need a persisted source badge.

## Architecture

Introduce a small result wrapper around generated viewpoints, for example `BookViewpointGenerationResult`, with:

- `drafts: List<BookViewpointDraft>`
- `source: BookViewpointSource`

`BookViewpointSource` should distinguish WeRead from AI fallback.

`BookIntelligenceSource.generateViewpoints` and `BookIntelligenceService.generateViewpoints` should return this wrapper. `WeReadSkillService` remains the main implementation. It should receive `AiConfigService` and `AiService` so fallback stays inside the book intelligence boundary and the UI does not need to understand the WeRead/AI decision.

The UI layer should only inspect the generation source to choose the completion notice text.

## Error Handling

- Missing WeRead API key remains a WeRead settings error.
- WeRead search returning no books remains `微信读书未找到相关书籍`.
- WeRead detail/review/chapter insufficiency can trigger AI fallback.
- Missing or incomplete default AI config after WeRead insufficiency should show an analysis failure that tells the user to configure AI before retrying.
- AI request failure should fail the add operation and rollback the newly inserted book, preserving the existing rollback behavior.

## Tests

- Unit test WeRead sufficiency detection for normal, sparse, and pending-book-like payloads.
- Unit test AI fallback prompt parsing and source marking.
- Unit test missing AI config error when WeRead material is insufficient.
- Update `BookIntelligenceService` facade tests for the new result wrapper.
- Update book UI text tests for the AI-generated completion notice.

## Backup Note

This follow-up does not change secret storage. WeRead API Key storage still uses `SecretCipher`; restored backups may require users to re-enter secrets if Android keystore-backed values cannot be decrypted on the restored device.
