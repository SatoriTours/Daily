# Daily Satori App Magazine Design Standard

## Purpose

Daily Satori should feel like a calm reading product, not a dashboard. News, summaries, article lists, and detail pages should prioritize editorial rhythm, readable text, and clear hierarchy over statistics, borders, and dense controls.

This standard is the default direction for future page design. If the product direction changes, update this document first and then implement pages from it.

## Core Principles

- Prefer borderless magazine layouts over boxed dashboard layouts.
- Use spacing, typography, paper-like surfaces, and subtle dividers to create hierarchy.
- Avoid statistics unless they directly help the user decide what to read.
- Keep controls close to the context they affect.
- Preserve light and dark mode quality with theme colors only.
- Avoid hard-coded colors, spacing, font sizes, and one-off visual constants.

## Visual Language

Use a paper-and-ink model:

- Page background: `MaterialTheme.colorScheme.background`.
- Primary reading surfaces: `surface` or transparent page background.
- Secondary raised surfaces: `surfaceContainer`.
- Small chips and metadata: `surfaceContainerHighest`.
- Text: `onSurface` for headlines, `onSurfaceVariant` for metadata and supporting copy.
- Accent: `primary` only for selected controls, links, and small emphasis. Do not use large blue hero blocks by default.

Borders should be rare:

- Do not wrap daily summary content, article lists, or article body text in visible bordered panels.
- Use a single subtle outer card only when a container needs separation from the background.
- Prefer hairline dividers inside content when needed.
- Avoid stacking bordered cards inside bordered cards.

## Typography

- Screen title: existing app bar typography.
- Magazine cover headline: `headlineSmall` or `titleLarge` with strong weight.
- Article/detail title: large, high-weight title with comfortable line height.
- Content introduction: `bodyMedium` or `bodyLarge`, medium contrast.
- List title: `titleSmall` or `bodyMedium` with `FontWeight.SemiBold`.
- List summary: `bodySmall` or `bodyMedium`, `onSurfaceVariant`, 2-3 lines.
- Metadata: `labelSmall` or `bodySmall`, visually quiet.

## News Summary Page

The summary page uses a magazine feed direction.

Required behavior:

- The daily summary content should not have a visible border around the whole content block.
- The lead summary appears as a magazine cover: date chip, strong headline, and editorial intro.
- Key items appear as readable story rows, not dashboard stat cards.
- Remove source count, key-point count, and citation count cards.
- Source chips may appear as quiet supporting context, not primary content.
- Deep dark-mode blue hero backgrounds are not used for the main summary area.

## Source Tabs And Refresh

The news page source switcher is the primary navigation for news modes.

Tabs should include:

- `汇总`
- enabled remote news sources
- `本地新闻`

The refresh action lives in the same row as the tabs:

- When `汇总` is selected, refresh generates or updates the current daily summary.
- When a remote source is selected, refresh reloads that source's current article list.
- When `本地新闻` is selected, refresh performs a lightweight local list refresh when needed.

The overflow menu should not duplicate tab actions:

- Do not show `本地文章` in the overflow menu.
- Do not show `生成/更新当日新闻` in the overflow menu.
- Keep only actions that are not represented by tabs or the refresh button.

## Remote And Local News Lists

Remote news and local news list items share one visual system.

Required list style:

- Each item has the same height.
- Cover thumbnail has a fixed width and height.
- Title is limited to two lines.
- Content introduction appears below the title and is limited to two or three lines.
- Source/time metadata sits at the bottom in quiet text.
- Cards should feel like magazine directory entries, not bordered data rows.
- Avoid a separate header such as `远程新闻`, `今日文章`, or `共 N 篇` above the list.

If a content introduction is missing, fallback order should be:

1. Article summary.
2. First non-empty viewpoint or excerpt.
3. Domain/feed metadata only as a last resort.

## Article Detail Pages

All article detail entry points should share the same borderless magazine reading layout:

- remote article detail opened from a remote source list;
- remote article detail opened from a summary citation;
- local article detail opened from local news;
- local article detail opened from a summary citation.

Required detail style:

- Do not wrap the markdown/body content in a bordered card.
- Use cover image, metadata chips, large title, and optional intro before the tab row or body.
- `AI 摘要` and `原文` tabs remain available where applicable.
- Body text sits on the page background or a very subtle paper surface without visible border.
- Use blockquote/section rhythm, subtle dividers, and spacing for hierarchy.
- Favorite and browser actions remain available but should not dominate the reading view.

## Loading And Empty States

- Loading skeletons should match the magazine page rhythm.
- Empty states may use existing components, but copy should be concise.
- Refresh or error messages should be compact and should not interrupt reading existing content.

## Design Review Checklist

Before shipping a page using this standard, verify:

- The page does not look like a dashboard unless the task explicitly requires one.
- There are no unnecessary stats.
- There are no nested bordered panels.
- Light and dark modes both preserve paper hierarchy.
- Text is comfortable on a phone screen.
- List rows have stable heights when the design calls for a list.
- Detail pages opened from different entry points look consistent.
