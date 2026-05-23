# Unified News Local Cache Design

## Goal

When the unified news summary uses remote articles, save those articles into the local `article` table so opening summary citations is fast and does not require re-fetching remote article details.

## Current Behavior

- `UnifiedNewsSummaryService` fetches remote top articles and converts them to `UnifiedNewsSourceItem` values.
- Generated summaries store citation metadata in `unified_news_source`.
- A `remote_article` citation currently opens by calling the remote API through `UnifiedNewsViewModel.openRemoteArticle()`.
- `ArticleRepository.saveRemoteArticleAsFavorite()` can save a remote article locally, but it marks the article as favorite and is used only when the user explicitly favorites a remote article.

## Desired Behavior

- Every remote article included in a unified news summary source set is saved directly into the local `article` table.
- Saving uses the remote article's existing title, summary, viewpoints, content, cover image, URL, and timestamp.
- Saving does not call `WebpageParserService`, does not enqueue `ArticleProcessingWorker`, and does not trigger local AI analysis.
- Cached summary articles are normal local articles but are not favorites by default.
- Existing local articles are reused by URL. If an article already exists, update it with the remote metadata without changing its favorite state.
- Summary citations should prefer opening the local cached article. If local caching is unavailable for a citation, keep the existing remote citation path as a fallback.

## Architecture

Add a non-favorite remote article save path to `ArticleRepository`. `UnifiedNewsSummaryService` will cache remote articles immediately after fetching and filtering them for the summary window. The resulting `UnifiedNewsSourceItem` records will use `sourceType = LOCAL_FAVORITE` with the cached local article ID when caching succeeds, so citation navigation opens the local article detail path without a remote fetch.

The existing favorite-specific save path remains unchanged for explicit user favorites. The new cache path uses `status = "completed"` because the remote article already contains processed metadata.

## Data Mapping

- `title`: remote `title` or `url` fallback.
- `ai_title`: remote `title`.
- `ai_content`: remote `summary` or joined `viewpoints`.
- `ai_markdown_content`: remote `content`, otherwise `summary`, otherwise joined `viewpoints`.
- `url`: remote `url` when present.
- `is_favorite`: `0` for new cached rows.
- Existing rows keep their current `is_favorite` value.
- `status`: `completed`.
- `cover_image_url`: remote `coverUrl`.
- `pub_date`: `processedAt`, then `createdAt`, then the source window timestamp.

## Error Handling

Caching failures should not fail summary generation. A failed insert/update should be logged and the summary should still be generated with the existing remote citation metadata.

## Testing

- Repository-level behavior should verify that remote article caching inserts a non-favorite completed article.
- Repository-level behavior should verify that caching an existing URL updates remote metadata but preserves `is_favorite`.
- Unified news service behavior should verify that cached remote articles become local citation sources when caching succeeds.
- Existing behavior for explicit favorite saving should remain covered and unchanged.

## Out Of Scope

- No database schema changes.
- No image file downloading.
- No background parsing or AI processing for cached remote articles.
- No changes to the remote news API contract.
