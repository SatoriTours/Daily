# External Favorites Sync Design

## Context

Daily Satori already has a local-first article collection, a local favorite flag on `article`, background article processing, encrypted secret storage, remote news source settings, and AI-generated article summaries. Recent remote-article favorite work chose the right target state: readable remote content becomes a local favorite article so the existing favorite list and AI Chat context keep working.

This feature adds the upstream layer: the app can connect to external platforms that have their own bookmark or favorite APIs, pull new saved items into Daily Satori, and let AI organize them into the local collection. The first connector is X Bookmarks, but the architecture should not be X-specific.

## Goal

Build an internal connector abstraction for external favorite sources and ship the smallest useful X Bookmarks implementation. The first version should let a user authorize X, manually or periodically sync bookmarked Posts, import them as local favorite articles, and queue AI整理 for consistent title, summary, Markdown, and tags.

## Chosen Approach

Use built-in connectors behind a shared `FavoriteConnector` interface. X is the first built-in connector. Future platforms add connector implementations and settings UI without changing the sync engine, persistence model, or local article import path.

Rejected alternatives:

- Platform-specific sync logic would be faster for X only, but it would duplicate auth, pagination, dedupe, retry, and import behavior for every future source.
- A fully dynamic plugin marketplace would be more flexible, but it is too heavy for the first version because it needs runtime schemas, remote plugin trust, versioning, and UI-driven connector installation.

## X API Constraints

X Bookmarks are read through X API v2:

- Endpoint: `GET https://api.x.com/2/users/:id/bookmarks`.
- Authentication: OAuth 2.0 user access token.
- Required scope for reading bookmarks: `bookmark.read`.
- Practical scopes for this feature: `bookmark.read tweet.read users.read offline.access`.
- Pagination: request `pagination_token`; read `meta.next_token`.
- Page size: use the documented max supported by the endpoint at implementation time, but keep the connector tolerant of smaller limits and plan for rate limits.
- Useful fields: `tweet.fields=created_at,author_id,public_metrics,entities,attachments` plus `expansions=author_id,attachments.media_keys` and media/user fields when available.

X Bookmarks are private user data. The app should only request read scopes in the first version and must make reauthorization clear if credentials expire or scopes are insufficient.

## User Experience

Settings gains an "External Favorites" entry. The first source type is X.

The X setup flow:

1. User taps "Add X".
2. App starts OAuth 2.0 authorization with the required read scopes and PKCE.
3. On callback, app stores encrypted token data and the authenticated X user id.
4. App shows the source as enabled with last sync status and a manual "Sync now" action.

Sync behavior:

- Manual sync is available from the source row.
- Periodic sync can be enabled per source, initially with a conservative default such as every 6 or 12 hours.
- New imported items appear in the existing local favorites list.
- Each imported item has enough local content to be readable even before AI processing finishes.
- The source row shows states such as idle, syncing, needs auth, rate limited, failed, and last successful sync time.

First version non-goals:

- No bidirectional sync back to X.
- No unbookmark/delete propagation.
- No X bookmark folders.
- No generic user-authored connector editor.

## Data Model

Add `external_favorite_source`:

- `id`
- `provider` such as `x`
- `display_name`
- `account_id`
- `account_name`
- `enabled`
- `sync_interval_minutes`
- `last_sync_started_at`
- `last_sync_completed_at`
- `last_success_at`
- `last_error`
- `status`
- `auth_json`
- `config_json`
- `created_at`
- `updated_at`

`auth_json` stores encrypted OAuth tokens and token metadata using `SecretCipher`. The repository should follow the existing `RemoteNewsSourceRepository` pattern: decrypt when returning source models, encrypt before saving, and migrate plaintext secrets if needed.

Add `external_favorite_item`:

- `id`
- `source_id`
- `provider`
- `external_id`
- `canonical_url`
- `title`
- `text`
- `author_name`
- `source_created_at`
- `favorited_at`
- `raw_json`
- `content_hash`
- `article_id`
- `status`
- `error_message`
- `first_seen_at`
- `last_seen_at`
- `created_at`
- `updated_at`
- unique `(provider, external_id)`

The local `article` table remains the user-facing collection. Imported items should create or update local articles with `is_favorite = 1`. `external_favorite_item.article_id` links the external record to the local article for traceability and reprocessing.

## Connector Interface

The shared connector boundary should be small:

```kotlin
interface FavoriteConnector {
    val provider: String
    suspend fun refreshAuth(source: ExternalFavoriteSource): ExternalFavoriteSource
    suspend fun fetchPage(
        source: ExternalFavoriteSource,
        cursor: String?,
        limit: Int,
    ): FavoriteFetchPage
}
```

Core models:

```kotlin
data class FavoriteFetchPage(
    val items: List<ExternalFavoriteItemDraft>,
    val nextCursor: String?,
    val rateLimitResetAt: Long? = null,
)

data class ExternalFavoriteItemDraft(
    val provider: String,
    val externalId: String,
    val canonicalUrl: String?,
    val title: String?,
    val text: String,
    val authorName: String?,
    val sourceCreatedAt: Long?,
    val favoritedAt: Long?,
    val rawJson: String,
)
```

The connector only fetches and normalizes platform data. It must not write `article` rows, run AI, or own scheduling. That keeps platform code replaceable and easy to test.

## X Connector Mapping

For each bookmarked Post:

- `provider`: `x`
- `externalId`: post id
- `canonicalUrl`: `https://x.com/{username}/status/{postId}` when username is available, otherwise `https://x.com/i/status/{postId}`
- `title`: first meaningful line or AI-generated later
- `text`: post text
- `authorName`: expanded author name or username
- `sourceCreatedAt`: post `created_at`
- `favoritedAt`: null unless X exposes it in the response
- `rawJson`: original post object plus relevant includes

Local article import:

- `url`: canonical X status URL
- `title`: `X: {authorName}` or a text-derived fallback
- `ai_markdown_content`: deterministic Markdown containing author, creation time, post text, metrics, media URLs, and original link
- `ai_content`: short deterministic summary before AI runs
- `status`: `completed` if deterministic content is enough to read; `aiProcessing` or existing processing status while AI整理 is running
- `is_favorite`: `1`
- `pub_date`: post creation time when available

If the Post contains external URLs, the first version records them inside Markdown. A later version can optionally enqueue external URL article parsing and link the parsed article back to the X favorite.

## Sync Flow

`FavoriteSyncWorker` schedules and runs sync work. It should use unique work names per source so duplicate manual taps do not create concurrent syncs for the same account.

Per-source flow:

1. Load enabled source.
2. Refresh OAuth token if needed.
3. Fetch pages from the connector.
4. For each page, upsert `external_favorite_item` by `(provider, external_id)`.
5. Stop early when a page contains only items already seen recently, unless this is a full resync.
6. Convert new or changed drafts to local favorite articles.
7. Link `external_favorite_item.article_id`.
8. Queue AI整理 for imported items that need it.
9. Update source status, timestamps, and rate-limit backoff.

Pagination cursors are per run state. Do not rely on persisted X pagination tokens as stable long-term checkpoints. The durable checkpoint is the set of external ids already imported.

## AI整理

The first version should avoid triggering expensive AI calls for every old bookmark at once.

Rules:

- New imports get readable deterministic Markdown immediately.
- AI整理 is queued only for new items or items whose text/raw content changed.
- The queue should process a small batch per worker run.
- If no AI config exists, imported favorites remain readable and marked as pending AI without failing sync.
- Existing article processing and X/Twitter Markdown helpers should be reused where practical, but connector imports should not depend on WebView extraction to succeed.

AI output should fill the existing article fields and tags:

- `ai_title`
- `ai_content`
- `ai_markdown_content`
- `article_tag`

The prompt should treat short social content differently from long articles: preserve the original wording, summarize why it may be worth saving, extract topics, and avoid inventing context not present in the Post.

## Error Handling

Source-level errors:

- Missing or expired refresh token: set source `status = auth_required`.
- Missing scope: set `auth_required` with a specific message mentioning `bookmark.read`.
- Rate limit: set `rate_limited` and store `rateLimitResetAt` when available.
- Network/server failure: retry through WorkManager with bounded attempts.

Item-level errors:

- Invalid or empty item: store raw item with `status = skipped` and an error message.
- Article insert conflict: link to the existing article by URL or content hash.
- AI failure: keep the article and item; mark AI status failed separately so sync does not repeatedly reimport the same item.

Local favorites should not be removed just because a later sync does not see an item. Absence can mean pagination window, API behavior, permission changes, or user unbookmarking; deletion sync is outside first-version scope.

## Privacy and Security

- Store access tokens and refresh tokens encrypted with `SecretCipher`.
- Do not log tokens, OAuth callback codes, raw Authorization headers, or full raw JSON in normal logs.
- Only request read scopes for the first version.
- Make it clear in UI that imported X content becomes local app data.
- Preserve local-first behavior: sync is optional, and disabling a source stops future sync without deleting imported local favorites.

## Testing

Unit tests:

- X response parser maps posts, includes, authors, media, pagination, and missing optional fields.
- Connector registry returns the X connector and rejects unknown providers.
- Source repository encrypts/decrypts auth JSON.
- Item repository upserts by `(provider, external_id)` and links article ids.
- Importer creates favorite local articles and dedupes by canonical URL.
- Sync service stops early on known items and does not fail the whole run on one bad item.
- Auth-required and rate-limit responses update source status.

Worker/UI tests:

- Manual sync enqueues unique work for a source.
- Periodic sync only includes enabled sources.
- Settings screen displays source status, last sync, and actionable auth errors.

Manual verification:

- Authorize X, run sync, confirm new X bookmarks appear in local favorites.
- Turn off AI config, sync still imports readable items.
- Re-run sync and confirm no duplicate local articles.
- Expire/revoke credentials and confirm the source enters `auth_required`.

## Implementation Plan Boundary

This design is one implementation slice:

1. Database schema and migration for external favorite sources/items.
2. Repository models and encrypted auth storage.
3. Connector abstraction and X connector.
4. Sync service and WorkManager scheduling.
5. Article import and AI整理 queue integration.
6. Settings UI for X source setup, status, manual sync, and enable/disable.
7. Focused tests for mapping, persistence, dedupe, sync, and UI state.

Future slices can add bookmark folders, bidirectional write support, non-X connectors, external URL expansion from social posts, and user-configurable connector definitions.
