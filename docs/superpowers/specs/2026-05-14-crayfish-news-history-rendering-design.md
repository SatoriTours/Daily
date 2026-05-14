# Crayfish News History Rendering Design

## Goal

Improve the Crayfish News screen so users can browse historical news by scrolling, and render article detail content using the original markdown body without extra generated metadata or outline sections.

## Confirmed Behavior

- The default Crayfish News view shows a scrollable history list of general news from `/news?category=general&limit=20`.
- The DJI view shows a scrollable history list of DJI news from `/news?category=dji&limit=20`.
- Selecting an item fetches the full file through `/news/general/{filename}` or `/news/dji/{filename}`.
- The detail page renders only `content` as markdown.
- The detail page does not show the filename-derived title, generated timestamp, or API `sections` outline before the content.

## Implementation Notes

- Reuse existing `CrayfishNewsListItem` cards for both categories.
- Keep the existing top-right menu for switching between 综合新闻, 大疆新闻, 返回远程新闻, and 刷新.
- The archive mode can be removed from the user-facing menu because each category is now a history feed.
- Preserve existing settings and API authentication behavior.
