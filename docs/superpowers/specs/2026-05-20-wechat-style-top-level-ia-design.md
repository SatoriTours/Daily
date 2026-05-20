# WeChat-Style Top-Level IA Design

## Context

Daily Satori currently exposes top-level areas as `新闻汇总 / 日记 / 读书 / AI`. This mirrors implementation modules, but it makes saved articles, favorites, diary entries, and settings feel distributed across multiple contexts. The requested direction is to keep existing functionality while simplifying the app with a WeChat-like information architecture: stable top-level destinations, low visual noise, list-first entry points, and fewer competing actions per screen.

This spec covers only the first phase: top-level navigation and page归位. Detailed visual redesigns for individual pages, especially the book reading experience, will be handled in follow-up specs.

## Goal

Change the app's primary mental model from feature modules to four user-centered destinations:

- `今日`: what to read or catch up on now.
- `记录`: what the user saved or wrote.
- `读书`: book viewpoints and reading.
- `AI`: conversational retrieval and synthesis.

No user-facing feature should be removed in this phase.

## Chosen Approach

Use the recommended phased approach. Phase 1 changes the bottom navigation and introduces a records hub, while reusing existing screens internally.

Rejected alternatives:

- A full one-shot WeChat redesign would touch navigation, records aggregation, reading UI, settings, and page-level visual language at once. That is too risky for one implementation cycle.
- A visual-only pass would improve spacing but would not simplify navigation or user mental model.

## Top-Level Navigation

The bottom navigation becomes:

1. `今日`
2. `记录`
3. `读书`
4. `AI`

`今日` remains the default first tab and continues to render the existing unified news experience. This preserves current news-summary behavior while giving the tab a more user-centered name.

`记录` is a new top-level hub. It should present list-style entries for:

- `日记`
- `文章`
- `本地收藏`

The first implementation may route each entry to the existing corresponding screen or inline mode. The goal is not to rewrite every content screen yet; it is to create one stable place for saved and authored content.

`读书` continues to render the existing `BooksScreen`. Its typography and reading layout will be redesigned in a later phase.

`AI` continues to render the existing `AiChatScreen`.

## Records Hub UX

The records hub should feel like a WeChat-style list screen:

- Simple top title: `记录`.
- Plain grouped list rows, not heavy cards.
- Each row has a clear label, short secondary description, and trailing chevron.
- Row tap opens the existing feature surface.
- Keep settings and low-frequency management out of this first hub unless already required by the current screen.

Initial rows:

- `日记`: opens the existing diary surface.
- `文章`: opens the existing local article list.
- `本地收藏`: opens the existing local favorites list.

## Top Bar Rules

Each top-level tab should expose only the current context title and the minimal actions already needed for that screen. This phase should not remove existing actions inside reused screens unless they conflict with the new tab structure.

The `我的/设置` access path may remain as currently implemented for now. A later phase will collect settings, backup, plugins, AI config, and remote source management into a more stable low-frequency area.

## Data And Navigation Flow

This phase should not change persistence schemas.

Navigation remains local to `HomeScreen` where possible:

- Bottom tab state controls top-level destination.
- `记录` owns an internal selection state for `日记 / 文章 / 本地收藏` if reused screens need to appear inline.
- Existing article detail navigation from lists to `ArticleDetailRoute` must continue to work.
- AI citation navigation to articles must continue to work.
- Selected book/viewpoint behavior must continue to switch to `读书`.

## Testing

Add or update source-level/unit tests for:

- Bottom tab labels are `今日 / 记录 / 读书 / AI` in that order.
- `记录` exists as a top-level destination.
- Records hub exposes `日记`, `文章`, and `本地收藏` entries.
- Existing article click navigation still routes to `ArticleDetailRoute`.
- Existing selected-book behavior still switches to the reading tab.

Manual verification:

- Launch the app and confirm the four tabs are visible.
- Open `今日` and confirm unified news still works.
- Open `记录`, enter diary, articles, and local favorites.
- Open an article detail from records and return.
- Open `读书` and `AI` to confirm existing screens still load.

## Non-Goals

- No book reading typography/layout change in this phase.
- No database schema changes.
- No settings/backup/plugin IA rewrite in this phase.
- No removal of existing features.
- No full WeChat visual restyling of every screen yet.
