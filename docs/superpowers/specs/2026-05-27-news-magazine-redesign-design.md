# News Magazine Redesign

## Goal

Redesign the news summary, source lists, local news list, and article detail pages around the approved borderless magazine style. The result should match the browser demos in perceived quality: calm, readable, low-border, and editorial.

## Approved Direction

Approved browser demos:

- News summary magazine feed: `news-summary-magazine-v3.html`.
- Borderless article detail: `news-detail-magazine-v1.html`.
- Tab/source navigation: `news-tabs-magazine-v1.html`.
- Equal-height remote list: `news-tabs-magazine-v2.html`.

## Scope

In scope:

- Summary tab magazine redesign.
- Source tabs with inline refresh action.
- Remote news list redesign.
- Local news list added as a first-class tab and styled like remote news.
- Overflow menu simplification.
- Remote and local article detail visual consistency.
- Project App magazine design standard.

Out of scope:

- Database schema changes.
- News generation service logic changes.
- Remote source configuration UI.
- New analytics or statistics features.

## Summary Page Requirements

- Daily summary content must not be wrapped in a visible border.
- Remove source/key-point/citation stat cards.
- Use a magazine cover structure: date chip, strong editorial headline, and intro.
- Render key points as story rows with subtle hairline dividers only.
- Source chips are quiet supporting metadata.
- Dark mode must avoid large blue blocks for the main hero.

## Tab And Refresh Requirements

The source switcher row becomes the main navigation row:

- `汇总` tab.
- One tab per enabled remote source.
- `本地新闻` tab.
- A refresh button at the end of the row.

Refresh behavior:

- On `汇总`: generate/update current daily summary.
- On a remote source: refresh that remote source article list.
- On `本地新闻`: refresh or reload the local article list.

Overflow menu changes:

- Remove `本地文章`.
- Remove `生成/更新当日新闻`.
- Keep only actions not represented by tabs or refresh.

## Remote And Local List Requirements

Remote source lists and local news lists must use the same equal-height magazine list card.

Each row:

- Fixed height.
- Fixed thumbnail area.
- Title limited to two lines.
- Content introduction below title, limited to two or three lines.
- Quiet source/time metadata anchored at bottom.
- No separate header text such as `远程新闻`, `今日文章`, or `共 N 篇`.

Content intro fallback:

1. Summary.
2. First viewpoint/excerpt.
3. Domain/feed metadata only when no content text exists.

## Detail Page Requirements

Remote and local article detail pages must share the same magazine reading visual language.

Required:

- No bordered markdown/body card.
- Cover, metadata chips, large title, and intro provide hierarchy.
- AI summary/original tabs remain where applicable.
- Body content uses transparent or subtle paper background without a border.
- Favorite/open actions remain but are visually secondary.
- Details opened from summary citations must match details opened from lists.

## Implementation Notes

- Prefer shared composables for magazine list rows and article detail reading body when practical.
- Keep business behavior unchanged where possible.
- Use `MaterialTheme.colorScheme` and theme tokens only.
- Keep functions focused; split files if `UnifiedNewsScreen.kt` grows too difficult to scan.

## Testing And Verification

Automated checks should cover:

- Summary UI no longer uses stat cards.
- Remote/local lists use fixed-height row style and include content introduction.
- Remote source header text is removed.
- Overflow menu no longer contains removed actions.
- Detail markdown/body container no longer uses a border.

Manual checks:

- Light and dark modes.
- Summary tab refresh.
- Remote tab refresh.
- Local news tab.
- Remote article detail from source list.
- Remote article detail from summary citation.
- Local article detail from local list.
- Local article detail from summary citation.

## Acceptance Criteria

- Implemented screens match the approved demos in visual direction.
- News lists have stable equal-height rows with descriptions.
- Details from all entry points share the same borderless magazine reading style.
- No unnecessary statistics or duplicated menu actions remain.
- Android compile and debug build pass.
