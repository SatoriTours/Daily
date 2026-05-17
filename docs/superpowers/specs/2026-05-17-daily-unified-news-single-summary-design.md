# Daily Unified News Single Summary Design

## Goal

Make the unified news feature behave like one local daily newspaper: the app collects article-level sources from remote news, crayfish news, and local favorites, then uses the local AI configuration to generate one summary for the phone's current local date.

## Source Rules

- Use the phone's local date as the canonical `summary_date`.
- Remote news must not treat the remote digest text as a source summary.
- Remote news uses remote digests only as an entry point to get the related article list.
- Each remote article from the digest article list becomes an individual source item.
- Crayfish news uses the local-date article list for enabled categories.
- Local favorites use favorite articles whose article date falls on the local date.
- The final prompt receives the merged article-level list from the three source groups.

## Persistence Rules

- There is exactly one unified summary per local date in the main UI.
- Regeneration updates the same local-date summary row instead of creating a new history item.
- Scheduled background runs may happen multiple times per day, but each run updates the same local-date summary.
- Source rows are replaced whenever the local-date summary is regenerated.
- Historical dates may remain available later, but same-day duplicates should no longer be produced.

## UI Rules

- The unified news tab focuses on today's summary instead of a timeline of time-window cards.
- The summary content should be rendered as obvious tappable news items, not as tiny `R1` / `R2` citation links.
- Each tappable item shows a clear source type label such as `远程新闻`, `小龙虾`, or `本地收藏`.
- Each tappable item includes an obvious action cue such as `查看来源`.
- Tapping the item opens the matching article detail directly.
- Citation IDs may remain internally for validation and routing, but they should not be the primary visible interaction.

## Regeneration Behavior

- The `刷新/重新生成` action fetches all three source groups again.
- It regenerates the local AI summary from the refreshed source list.
- It overwrites today's existing summary content, status, warning, and source rows.
- It should not append a new same-day summary card.

## Error Handling

- If all three source groups are empty, today's summary is saved as empty with a clear message.
- If one source group fails but others succeed, generation may continue and the failure is shown in source warnings.
- If AI generation fails, today's summary records the failure status and does not erase the last successful display content during the current session.
- AI configuration errors should remain non-retryable for background worker retry policy.

## Tests

- Remote digest collection extracts article-level remote sources, not digest summary sources.
- Regenerating the same local date updates one summary instead of inserting another visible card.
- Source replacement removes stale source rows for the same date.
- The UI exposes obvious clickable source items and does not depend on `R1` / `R2` as the primary action.
- Clicking a remote, crayfish, or local item routes to the correct article detail.
- Background runs update today's local-date summary.

## Out Of Scope

- Changing the remote news API.
- Adding a full historical browser redesign.
- Removing citation validation internally.
- Changing the user's AI provider configuration.
