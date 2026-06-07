# External Favorites Sync Design

## Context

Daily Satori already has a local-first article collection, a local favorite flag on `article`, background article processing, encrypted secret storage, remote news source settings, and AI-generated article summaries. Recent remote-article favorite work chose the right target state: readable remote content becomes a local favorite article so the existing favorite list and AI Chat context keep working.

This feature adds the upstream layer: the app can connect to external platforms that have their own bookmark or favorite APIs, pull new saved items into Daily Satori, and let AI organize them into the local collection. The first connector is X Bookmarks, but the architecture should not be X-specific.

## Goal

Build an internal connector abstraction for external favorite sources and ship the smallest useful X Bookmarks implementation. The first version should let a user authorize X, manually or periodically sync bookmarked Posts, import them as local favorite articles, and queue AI整理 for consistent title, summary, Markdown, and tags.

The design must support multiple accounts for the same provider. Two X accounts may bookmark the same Post, and their external sync records must remain separate even if they resolve to one shared local article.

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

Settings gains an "External Favorites" entry. The first source type is X. A user can add more than one account for the same provider; rows are displayed by provider plus account name.

The X setup flow:

1. User taps "Add X".
2. App starts OAuth 2.0 authorization with the required read scopes and PKCE.
3. On callback, app stores encrypted token data and the authenticated X user id.
4. App shows the source as enabled with last sync, import, and auth status plus a manual "Sync now" action.

Sync behavior:

- Manual sync is available from the source row.
- Periodic sync can be enabled per source, initially with a conservative default such as every 6 or 12 hours.
- New imported items appear in the existing local favorites list.
- Each imported item has enough local content to be readable even before AI processing finishes.
- The source row shows states such as idle, syncing, needs auth, rate limited, failed, and last successful sync time.
- The detail view can show counts for fetched items, imported articles, pending AI整理, and failed items so sync failures are not confused with AI failures.

First version non-goals:

- No bidirectional sync back to X.
- No unbookmark/delete propagation.
- No X bookmark folders.
- No generic user-authored connector editor.

## Architecture

The feature is a pipeline, not one large service:

```text
Android OAuth Coordinator
  -> ExternalFavoriteSourceRepository

FavoriteSyncWorker
  -> FavoriteSyncService
      -> FavoriteConnectorRegistry
      -> FavoriteConnector
      -> ExternalFavoriteItemRepository

ExternalFavoriteImporter
  -> ArticleRepository
  -> TagRepository

ExternalFavoriteAiOrganizer
  -> AiConfigService
  -> AiService
  -> ArticleRepository
  -> TagRepository
```

Responsibilities:

- `Android OAuth Coordinator`: owns browser/custom-tab authorization, deep-link callback handling, PKCE, and token exchange. This stays in Android code because those concerns are platform-specific.
- `ExternalFavoriteSourceRepository`: stores source configuration and encrypted auth data.
- `FavoriteConnector`: fetches and normalizes provider data. It does not write database rows, import articles, schedule workers, or run AI.
- `FavoriteSyncService`: orchestrates source sync, token refresh, pagination, item upsert, and source-level status updates.
- `ExternalFavoriteImporter`: converts synced items into local favorite articles and links items to `article_id`.
- `ExternalFavoriteAiOrganizer`: performs bounded AI整理 for imported items and updates article fields/tags.

This split keeps platform API handling, local persistence, and AI enrichment independently testable. It also allows later flows such as "resync only", "reimport failed items", and "rerun AI整理" without calling external APIs again.

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
- `rate_limit_reset_at`
- `auth_json`
- `config_json`
- `capabilities_json`
- `created_at`
- `updated_at`

`auth_json` stores encrypted OAuth tokens and token metadata using `SecretCipher`. The repository should follow the existing `RemoteNewsSourceRepository` pattern: decrypt when returning source models, encrypt before saving, and migrate plaintext secrets if needed.

Use a unique constraint that prevents duplicate account rows for the same provider, such as `(provider, account_id)`, while still allowing multiple accounts under one provider.

`capabilities_json` records connector-level behavior used by UI and scheduling, for example max page size, whether folders are supported, whether a provider exposes `favoritedAt`, whether write-back is supported, and whether token refresh is available.

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
- `sync_status`
- `import_status`
- `ai_status`
- `error_message`
- `first_seen_at`
- `last_seen_at`
- `created_at`
- `updated_at`
- unique `(source_id, external_id)`

The local `article` table remains the user-facing collection. Imported items should create or update local articles with `is_favorite = 1`. `external_favorite_item.article_id` links the external record to the local article for traceability and reprocessing.

The unique key is source-scoped. `(provider, external_id)` is not sufficient because different accounts on the same provider can bookmark the same external object. Cross-account dedupe belongs in `ExternalFavoriteImporter`, where matching by `canonical_url` or `content_hash` can intentionally link several external records to one local article.

Suggested status values:

- `sync_status`: `seen`, `skipped`, `stale`, `deleted_remote_unknown`, `failed`.
- `import_status`: `not_imported`, `imported`, `duplicate_linked`, `failed`.
- `ai_status`: `not_needed`, `pending`, `processing`, `completed`, `failed`.

These statuses must remain independent. A synced X item can be imported successfully even if AI整理 fails, and an AI retry should not re-fetch X data.

## Connector Interface

The shared connector boundary should be small:

```kotlin
interface FavoriteConnector {
    val provider: String
    val capabilities: FavoriteConnectorCapabilities
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
data class FavoriteConnectorCapabilities(
    val maxPageSize: Int,
    val supportsFolders: Boolean,
    val supportsFavoritedAt: Boolean,
    val supportsWriteBack: Boolean,
    val supportsRefreshToken: Boolean,
)

data class FavoriteFetchPage(
    val items: List<ExternalFavoriteItemDraft>,
    val nextCursor: String?,
    val rateLimitResetAt: Long? = null,
    val exhausted: Boolean = nextCursor == null,
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

The connector only fetches and normalizes platform data. It must not write `article` rows, run AI, own scheduling, or control Android OAuth UI. That keeps platform code replaceable and easy to test.

The sync service passes an intended page limit, but each connector clamps it according to `capabilities.maxPageSize`. This avoids putting X-specific page-size or rate-limit rules in the generic sync engine.

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

X connector capabilities for the first version:

- `maxPageSize`: the current documented max for the X bookmarks endpoint at implementation time.
- `supportsFolders`: false for first version, even though folder endpoints exist.
- `supportsFavoritedAt`: false unless the endpoint response includes a reliable favorite timestamp.
- `supportsWriteBack`: false because first version is read-only.
- `supportsRefreshToken`: true when OAuth was granted with `offline.access`.

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
2. Refresh OAuth token through the connector if needed and supported.
3. Fetch pages from the connector, letting the connector clamp page size.
4. For each page, upsert `external_favorite_item` by `(source_id, external_id)`.
5. Stop early when a page contains only items already seen recently, unless this is a full resync.
6. Mark changed/new items for import.
7. Update source sync status, timestamps, and rate-limit backoff.
8. Call `ExternalFavoriteImporter` for the changed/new items.
9. Call `ExternalFavoriteAiOrganizer` for imported items that need AI整理 and fit the batch budget.

Pagination cursors are per run state. Do not rely on persisted X pagination tokens as stable long-term checkpoints. The durable checkpoint is the set of external ids already imported.

Sync service completion means external data was fetched and stored. Import and AI整理 can partially fail without changing source `last_success_at` for the fetch phase. Their failures live on item-level statuses and can be retried independently.

## Import Flow

`ExternalFavoriteImporter` is the only component that writes local articles for external favorite items.

Import rules:

- Match an existing local article by normalized `canonical_url` first.
- If URL is missing or unstable, match by `content_hash` only within conservative provider-specific rules.
- If an existing article is found, mark it favorite and fill only missing safe metadata.
- If no article is found, create a favorite article with deterministic readable content.
- Link the item to the local article and set `import_status`.
- Multiple external items from different sources may link to the same `article_id`.

This keeps source identity and local dedupe separate. It also protects user-edited article fields from being overwritten by later syncs.

## AI整理

The first version should avoid triggering expensive AI calls for every old bookmark at once.

Rules:

- New imports get readable deterministic Markdown immediately.
- AI整理 is queued only for new items or items whose text/raw content changed.
- The queue should process a small batch per worker run.
- If no AI config exists, imported favorites remain readable and marked as pending AI without failing sync.
- Existing article processing and X/Twitter Markdown helpers should be reused where practical, but connector imports should not depend on WebView extraction to succeed.
- AI retries should operate from stored `external_favorite_item` plus linked `article`, not from another external API fetch.

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
- Unknown provider or unsupported capability: disable the affected source with an actionable settings error.

Item-level errors:

- Invalid or empty item: store raw item with `sync_status = skipped` and an error message.
- Article insert conflict: link to the existing article by URL or content hash.
- AI failure: keep the article and item; mark AI status failed separately so sync does not repeatedly reimport the same item.
- Duplicate across accounts: link both external items to the same local article when URL/content matching is confident.

Local favorites should not be removed just because a later sync does not see an item. Absence can mean pagination window, API behavior, permission changes, or user unbookmarking; deletion sync is outside first-version scope.

## Privacy and Security

- Store access tokens and refresh tokens encrypted with `SecretCipher`.
- Do not log tokens, OAuth callback codes, raw Authorization headers, or full raw JSON in normal logs.
- Only request read scopes for the first version.
- Make it clear in UI that imported X content becomes local app data.
- Preserve local-first behavior: sync is optional, and disabling a source stops future sync without deleting imported local favorites.
- Keep OAuth callback handling in Android code and avoid storing authorization codes after token exchange.
- Redact raw provider payloads from bug-report/export surfaces unless the user explicitly includes local app data.

## Completeness Checks

The design covers the first-version lifecycle end to end:

- Source onboarding: Android OAuth, source storage, encrypted credentials, multi-account uniqueness.
- Fetching: connector registry, provider capabilities, pagination, token refresh, rate-limit status.
- Persistence: source records, source-scoped external items, independent sync/import/AI states.
- Import: deterministic local favorite creation, existing article dedupe, cross-account item linking.
- AI整理: bounded queue, no-AI fallback, retry from local stored data, social-content-specific prompt behavior.
- Operations: manual sync, periodic sync, unique work per source, partial failure isolation.
- Privacy: read-only scopes, encrypted secrets, no token logging, optional sync.

First-version decision: keep item-level `ai_status` and update article fields when AI succeeds. Do not reuse the existing article-processing status as the source of truth for external favorite AI state, because article status currently serves multiple workflows.

## Testing

Unit tests:

- X response parser maps posts, includes, authors, media, pagination, and missing optional fields.
- Connector registry returns the X connector and rejects unknown providers.
- Source repository encrypts/decrypts auth JSON.
- Item repository upserts by `(source_id, external_id)` and links article ids.
- Importer creates favorite local articles and dedupes by canonical URL.
- Two X sources can sync the same post without overwriting each other's external item rows, while linking to one local article when URL matching is confident.
- Sync service stops early on known items and does not fail the whole run on one bad item.
- Import and AI failures do not turn a successful fetch into a failed source sync.
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
3. Android OAuth coordinator for X authorization and callback handling.
4. Connector abstraction, capabilities, registry, and X connector.
5. Sync service and WorkManager scheduling.
6. External favorite importer and local article linking.
7. AI整理 queue integration with bounded batches.
8. Settings UI for X source setup, status, manual sync, and enable/disable.
9. Focused tests for mapping, persistence, dedupe, sync, import, AI state, and UI state.

Future slices can add bookmark folders, bidirectional write support, non-X connectors, external URL expansion from social posts, and user-configurable connector definitions.
