# Unified News Summary Design

## Goal

Create one AI-generated news summary that combines remote news summaries, Crayfish general news, Crayfish DJI news, and local favorite articles. The feature reduces context switching by giving the user one readable summary while still allowing each cited point to jump directly to its source.

## Scope

This design covers the first version of unified news summaries:

- Generate AI summaries for natural-day windows.
- Run generation in the background with WorkManager using reliable, non-exact scheduling.
- Backfill missed summary windows when the app starts.
- Persist summaries and citation source mappings locally.
- Render citations in the summary body as clickable references.
- Jump directly from a citation to the matching source detail.
- Reduce bottom navigation clutter by making the first tab the news summary landing page and moving secondary pages into the top-right menu.

This version does not require exact alarm scheduling, push notifications, or a visual citation preview sheet.

## Summary Windows

All windows use the device local time zone.

- 08:00 snapshot: today 00:00 through 08:00.
- 13:30 snapshot: today 00:00 through 13:30.
- 18:00 snapshot: today 00:00 through 18:00.
- 21:00 snapshot: today 00:00 through 21:00.
- 00:00 final summary: previous day 00:00 through 23:59:59.999.

WorkManager may execute later than the target time. A delayed run still generates the intended due window, not a moving "now" window. App startup also checks for missed due windows and enqueues generation for missing summaries.

## Architecture

Add an independent unified news module instead of extending the current remote news ViewModel. The module has separate data collection, AI generation, storage, worker scheduling, and UI state. Existing remote news, Crayfish news, and local article modules remain source providers and detail destinations.

Main units:

- `UnifiedNewsSummaryService` in `shared`: gathers sources, builds the AI prompt, calls `AiService`, validates citations, and persists the result.
- `UnifiedNewsSummaryRepository` in `shared`: reads and writes summary rows and citation source rows.
- `UnifiedNewsWorker` and `UnifiedNewsScheduler` in Android app code: schedule reliable background work, chain the next due run, and perform startup backfill.
- `UnifiedNewsViewModel` and `UnifiedNewsScreen`: load summaries, trigger manual retry/regeneration, and route citation clicks.
- Citation renderer helpers: detect source reference tokens such as `[R1]`, `[C1]`, `[D1]`, and `[F1]` and expose click targets.

## Data Sources

Each source item receives a stable citation key before AI generation.

- Remote news summaries use prefix `R`, for example `[R1]`. The source should include digest title, date, summary, sections, and referenced article metadata when available.
- Crayfish general news uses prefix `C`, for example `[C1]`. The source should include filename, generated timestamp, preview, and markdown content.
- Crayfish DJI news uses prefix `D`, for example `[D1]`. It uses the same shape as Crayfish general news.
- Local favorite articles use prefix `F`, for example `[F1]`. The source should include article id, title or AI title, AI summary, markdown content when useful, url, and created time. The first version filters local favorites by `article.created_at` because the current schema does not store the time an article became a favorite. Implementation should add a repository query that returns favorite articles inside a date range.

The service collects only items whose source timestamps fall inside the target window. If a source has no reliable timestamp, it is excluded from natural-day generation for the first version instead of guessing. This keeps the summary faithful to the requested day.

Remote and Crayfish services do not currently expose explicit date-range query APIs in the app. The first implementation should fetch a bounded recent set, filter locally by parsed source time, and record a warning if the source response does not contain reliable timestamps. If date filtering proves too lossy for Crayfish filenames or generated timestamps, add timestamp parsing as a dedicated helper with tests rather than falling back to latest-N behavior.

## Persistence

Add `unified_news_summary`:

- `id`: primary key.
- `summary_date`: local date string in `YYYY-MM-DD`.
- `window_key`: one of `0800`, `1330`, `1800`, `2100`, `final`.
- `window_start_ms`, `window_end_ms`: epoch milliseconds for the exact source window.
- `title`: generated or fallback title.
- `content`: generated Markdown content.
- `status`: `pending`, `success`, `failed`, or `empty`.
- `error_message`: nullable failure detail.
- `source_warnings`: nullable text containing skipped-source or partial-failure notes.
- `generated_at`: epoch milliseconds when generation finished.
- `created_at`, `updated_at`: row timestamps.

Add `unified_news_source`:

- `id`: primary key.
- `summary_id`: foreign key to `unified_news_summary`.
- `ref_key`: citation key such as `R1` or `F3`.
- `source_type`: `remote_digest`, `remote_article`, `crayfish_general`, `crayfish_dji`, or `local_favorite`.
- `source_id`: remote id or local article id when available.
- `source_filename`: Crayfish filename when applicable.
- `source_url`: article url when applicable.
- `title`, `summary`, `source_time`: display and routing metadata.

The summary content and source mappings are saved together so historical citations remain stable even if source lists refresh later.

## AI Prompt Contract

The prompt gives the model a numbered source packet and requires Markdown output in Chinese.

Rules:

- Use only supplied sources.
- Every factual claim in `重点速览` and `值得关注` must include at least one citation token.
- Citation tokens must exactly match provided keys, including brackets.
- Do not invent citations.
- Prefer concise synthesis over source-by-source listing.
- If sources conflict, state the uncertainty and cite both sources.

Example output shape:

```markdown
# 今日统一新闻总结

## 重点速览

1. AI 终端侧能力继续升温，硬件厂商围绕本地模型体验展开竞争。[R1][F2]
2. 大疆相关动态集中在新品、供应链和行业应用。[D1]

## 值得关注

- 远程新闻中的宏观趋势和本地收藏中的深度文章形成互补。[R2][F1]
```

After generation, the service scans all citation tokens. If the generated content contains any unknown citation token, the summary is marked `failed` and the content is not shown as a successful summary. The first version fails clearly instead of trying to repair generated text.

## Scheduling

Use WorkManager as the reliable background boundary.

- Register unique one-time work on app startup for the next target window. When the worker finishes, schedule the next target window.
- Also enqueue a backfill check on app startup so missed windows are generated even if the OS delayed or skipped earlier work.
- The worker checks which windows are due and missing, then generates them. This allows one run to catch up multiple windows after a long delay.
- The worker returns success when there is no due work, when all due summaries are generated, or when a window has no material and is saved as `empty`.
- The worker returns retry for transient AI or network failures.
- Startup backfill runs after dependency initialization and enqueues work if any due summary is missing.

Do not use a single 24-hour periodic worker for this feature. It cannot naturally represent the five requested daily windows. Chained one-time requests with calculated initial delays fit the requested schedule better while still using WorkManager's reliable execution model.

Because WorkManager cannot guarantee exact wall-clock execution, the scheduler optimizes for reliability and battery behavior, not minute-level precision. A delayed 13:30 run still generates the `1330` window rather than expanding to the delayed execution time.

## UI Design

Replace the current first bottom tab `文章` with `新闻汇总`. This becomes the app's default landing page and shows the unified summary first.

Reduce bottom navigation to the primary daily workflows:

- `新闻汇总`: unified news summary landing page.
- `日记`: existing diary flow.
- `读书`: existing book flow.
- `AI`: existing AI chat flow.

Remove `文章`, `远程新闻`, and `设置` from the bottom navigation. They remain reachable from menus and routes instead of occupying permanent tab space.

The news area should provide entries for:

- `统一总结`: default view for AI summaries.
- `本地文章`: existing local article list, including all articles and favorite filtering.
- `远程新闻`: existing remote news digests/articles/feeds.
- `小龙虾新闻`: existing Crayfish news flows.
- `本地收藏`: filtered local favorite articles listed inside the news area and routed to the existing article detail screen.
- `设置`: app settings, moved from the bottom tab into the top-right menu of the news landing page.

The top-right menu on `新闻汇总` should be the main entry point for secondary destinations:

- `本地文章`
- `本地收藏`
- `远程新闻`
- `小龙虾新闻`
- `设置`
- `刷新/重新生成`

This keeps the bottom bar focused while preserving access to the existing detailed pages.

The unified summary screen shows:

- Latest available summary for today, preferring the newest successful window.
- Window label, generation time, and status.
- Markdown summary with clickable citation tokens.
- History selector for earlier windows and previous days.
- Retry or regenerate action for failed and successful summaries.

Navigation implementation should keep the current `ArticleDetailRoute(articleId)` for local article details. For settings, prefer adding a regular route or screen state that opens `SettingsScreen` from the news menu, instead of keeping settings as a bottom tab index.

## Citation Navigation

Clicking a citation directly opens the source detail.

- `remote_digest`: open remote digest detail using the saved remote digest id.
- `remote_article`: open remote article detail using the saved remote article id.
- `crayfish_general`: fetch/open Crayfish general detail by saved filename.
- `crayfish_dji`: fetch/open Crayfish DJI detail by saved filename.
- `local_favorite`: navigate to existing `ArticleDetailRoute(articleId)`.

If a source can no longer be opened, show an inline error or toast and keep the user on the summary screen.

## Error Handling

- If one remote source fails, continue with other available sources and record a non-blocking warning.
- If no sources exist for a window, save an `empty` summary and skip the AI call.
- If AI configuration is missing, mark the due summary as `failed` with a user-facing message.
- If AI generation fails, keep the latest previous successful summary visible and expose retry.
- If citations fail validation, mark the summary as `failed` rather than presenting broken links.

## Testing

Cover the following behavior:

- Time window calculation, especially the 00:00 final summary for the previous day.
- Due-window detection and duplicate prevention.
- Next-window delay calculation for chained one-time WorkManager requests.
- Citation key assignment and validation.
- Startup backfill for missed windows.
- Partial source failure still generating from remaining sources.
- Empty-window behavior avoids AI calls.
- Database migration creates both new tables.
- UI citation clicks resolve to the correct route target.
- Bottom navigation contains the reduced primary tabs, with article/news/settings destinations reachable from the `新闻汇总` menu.

## Implementation Notes

- Adding the new database tables requires incrementing `currentSchemaVersion` and adding a migration in `DatabaseMigration.kt`.
- The first implementation should keep source collection conservative. Exclude timestamp-ambiguous items rather than guessing dates.
- The first implementation can render citation tokens with a custom text renderer around Markdown if the existing Markdown component does not support inline clickable spans.
- Manual regeneration should update the existing summary row for the same `summary_date` and `window_key` instead of creating duplicates.
- Avoid adding a favorite timestamp column in the first version unless product behavior requires "favorited today" instead of "favorite article created today". Adding that column would require backfill semantics that current data cannot reconstruct accurately.
