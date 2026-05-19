# Remote Article Favorites Design

## Context

Local articles already support favorites through `article.is_favorite`, `ArticleRepository.getFavorites()`, `ArticleListScreen(showFavoritesOnly = true)`, and AI Chat's `get_favorite_articles` tool. Remote news articles can be opened from the remote article list and from digest summary citations, but those detail screens do not currently expose a way to save the article into the user's managed article collection.

## Goal

Allow any readable article, including articles opened from summaries, to be favorited. Favorited remote articles become local favorite articles so the user has one unified place to manage saved reading and AI Chat treats those saved articles as higher-priority context.

## Chosen Approach

When the user favorites a `RemoteArticle`, the app stores it in the local `article` table and sets `is_favorite = 1`. This reuses the existing local article repository, favorite list UI, and AI Chat tools instead of creating a second remote-favorites system.

Rejected alternatives:

- A separate remote favorite table would preserve remote identity but require new aggregation paths for list UI and AI Chat.
- Dual remote and local favorite state would be more complete but adds synchronization risk and unnecessary complexity for the current goal.

## User Experience

Remote article detail screens show a favorite action alongside the existing browser-open action. This applies when the detail screen is reached from the remote article feed, a digest's referenced article list, or unified news citation navigation.

When the user taps favorite:

- If the remote article is not in local articles, the app creates a local article with `is_favorite = 1`.
- If a local article with the same URL already exists, the app marks that local article as favorite and fills missing metadata where safe.
- The remote detail screen reflects the saved state as favorited.
- The article appears in the existing `本地收藏` list.

Unfavoriting is handled through the local article detail/list flows already present in the app. The remote detail can also toggle the local favorite state if the local article can be matched by URL.

## Data Mapping

Remote article fields map to local article fields as follows:

- `RemoteArticle.title` -> `Article.title` and, when useful, `Article.ai_title`.
- `RemoteArticle.summary` plus non-empty viewpoints -> `Article.ai_content`.
- `RemoteArticle.content` -> `Article.ai_markdown_content`.
- `RemoteArticle.url` -> `Article.url` for dedupe.
- `RemoteArticle.coverUrl` -> `Article.cover_image_url`.
- `RemoteArticle.processedAt` or `createdAt` -> `Article.pub_date` when parseable.
- Local `status` is set to `completed` because the remote article already has readable content.
- Local `is_favorite` is set to `1`.

URL is the primary dedupe key because the local schema already enforces `article.url` as unique. Remote articles without URL are rare; for those, the app may still save a local favorite using the available title/content, but no reliable cross-session dedupe is guaranteed without a schema change. This design avoids a database migration unless implementation proves URL-less remote articles are common.

## Components

`ArticleRepository` gains small synchronous or suspend-safe helpers for:

- Finding a local article by URL.
- Saving a remote article as a favorite.
- Marking an existing URL-matched article as favorite while preserving existing user-owned content.

Remote article screens receive favorite state and callbacks from their ViewModels rather than owning repository access directly.

Remote-news ViewModels own conversion from `RemoteArticle` to local favorite state because they already hold selected remote article state. Unified-news detail navigation uses the same save helper for remote articles opened from citations.

AI Chat keeps using the existing local favorite source, but prompt/tool behavior is tightened so article-related questions prefer favorite articles when relevant. Keyword article search should return favorite matches before non-favorite matches for the same query.

## AI Chat Weighting

Favorites are weighted in two practical ways:

- `get_favorite_articles` remains the direct source for saved reading context.
- `search_articles` returns matching favorite articles before non-favorites, so general article queries are more likely to include saved articles in the model context.

The system prompt will explicitly instruct the assistant to prioritize favorite articles for article-related questions and use normal article search only when favorite results are insufficient.

## Error Handling

If saving fails, the UI should keep the remote article open and expose a concise error message. Existing local article data should not be overwritten destructively. When an existing article is found by URL, the helper should preserve non-empty local fields and only fill missing or remote-derived fields needed for favorite retrieval.

## Testing

Unit-level coverage should verify:

- Remote detail page exposes favorite actions for article summary and original views.
- Remote-to-local mapping creates a favorite article with expected title, URL, summary, markdown content, cover URL, and status.
- Saving a remote article with an existing URL does not duplicate rows and marks the existing article favorite.
- AI article search ranks favorite matches before non-favorite matches.
- Existing local favorites list behavior remains unchanged.

Manual verification should include opening a remote article from both the remote article list and a digest summary, saving it, checking `本地收藏`, and asking AI Chat about a topic present in the saved article.

## Non-Goals

- No remote server favorite API integration.
- No bidirectional sync between remote and local favorite state.
- No new database schema unless implementation discovers that URL-less remote articles must be supported reliably.
