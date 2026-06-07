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

## Provider Auth Config

Provider-level OAuth configuration is separate from account-level secrets.

For X, the first version uses OAuth 2.0 Authorization Code with PKCE as a public mobile client. The app must not store or require a client secret on device. The provider config contains non-user-secret values:

- `provider`: `x`
- `client_id`
- `redirect_uri`
- `scopes`: `bookmark.read tweet.read users.read offline.access`
- `authorization_url`: `https://api.x.com/2/oauth2/authorize`
- `token_url`: `https://api.x.com/2/oauth2/token`

This config can be built into the X OAuth coordinator for the first version. `external_favorite_source.auth_json` stores only account-specific token data after authorization, not provider client configuration.

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
- First sync imports only a bounded recent window by default. A separate "import older bookmarks" action can continue history import in batches.

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
- `last_sync_window_started_at`
- `last_items_seen_count`
- `last_pages_seen_count`
- `last_error`
- `last_error_code`
- `last_error_message`
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
- `ai_input_hash`
- `article_id`
- `sync_status`
- `import_status`
- `ai_status`
- `last_error_code`
- `last_error_message`
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

Use structured error codes for retry and UI logic, with human-readable messages kept separate. Suggested codes include `auth_missing_scope`, `auth_refresh_failed`, `rate_limited`, `network_timeout`, `invalid_payload`, `import_conflict`, `ai_config_missing`, and `ai_generation_failed`.

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

Each connector should expose or use provider-specific canonicalization rules. For X, all status URLs for the same Post should collapse to one canonical form regardless of `x.com`, `twitter.com`, `i/status`, username path, or tracking query parameters.

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

Canonical URL rules:

- Prefer `https://x.com/{username}/status/{postId}` when username is known.
- Fall back to `https://x.com/i/status/{postId}`.
- Treat `twitter.com`, `x.com`, `mobile.twitter.com`, and common tracking query variants as the same Post when the status id matches.

## Sync Flow

`FavoriteSyncWorker` schedules and runs sync work. It should use unique work names per source so duplicate manual taps do not create concurrent syncs for the same account.

Per-source flow:

1. Load enabled source.
2. Refresh OAuth token through the connector if needed and supported.
3. Fetch pages from the connector, letting the connector clamp page size.
4. For each page, upsert `external_favorite_item` by `(source_id, external_id)`.
5. Stop early only after a conservative repeated-known threshold, unless this is a full or older-history import.
6. Mark changed/new items for import.
7. Update source sync status, timestamps, seen counts, page counts, and rate-limit backoff.
8. Call `ExternalFavoriteImporter` for the changed/new items.
9. Call `ExternalFavoriteAiOrganizer` for imported items that need AI整理 and fit the batch budget.

Pagination cursors are per run state. Do not rely on persisted X pagination tokens as stable long-term checkpoints. The durable checkpoint is the set of external ids already imported.

Sync service completion means external data was fetched and stored. Import and AI整理 can partially fail without changing source `last_success_at` for the fetch phase. Their failures live on item-level statuses and can be retried independently.

First-sync protection:

- Default first sync imports a bounded recent window, such as 100 to 300 items.
- Manual "import older bookmarks" continues in bounded batches.
- Periodic sync never performs an unbounded historical import.
- AI整理 has its own smaller per-run budget, so a large import does not produce a large immediate AI bill.

Checkpoint and early-stop rules:

- Each run records `last_sync_window_started_at`, `last_items_seen_count`, and `last_pages_seen_count`.
- Default sync fetches up to a configured max page count or max item count.
- Early stop is allowed only after consecutive known unchanged items or pages cross a threshold.
- Full resync and older-history import ignore the normal early-stop threshold but still obey page, item, rate-limit, and battery/network limits.

## Background Scheduling

WorkManager-based automatic sync is best-effort, not real-time.

Rules:

- Manual sync is the deterministic user-controlled path.
- Periodic sync should require network connectivity.
- Periodic sync may be delayed by Android battery saver, Doze, low power, metered network policy, or WorkManager scheduling.
- UI copy should say "automatic periodic sync" rather than "real-time sync".
- Source rows should show last successful sync and last attempted sync so delays are visible.

## Import Flow

`ExternalFavoriteImporter` is the only component that writes local articles for external favorite items.

Import rules:

- Normalize provider URLs before matching, then match an existing local article by normalized `canonical_url` first.
- If URL is missing or unstable, match by `content_hash` only within conservative provider-specific rules.
- If an existing article is found, mark it favorite and fill only missing safe metadata.
- If no article is found, create a favorite article with deterministic readable content.
- Link the item to the local article and set `import_status`.
- Multiple external items from different sources may link to the same `article_id`.

This keeps source identity and local dedupe separate. It also protects user-edited article fields from being overwritten by later syncs.

Overwrite rules:

- Never overwrite user-owned `comment` or manually edited tags during automatic import.
- `is_favorite` may be set to `1`; absence from a later external sync must not set it back to `0`.
- `title`, `pub_date`, and `cover_image_url` are filled only when local values are empty or clearly auto-generated.
- `ai_title`, `ai_content`, and `ai_markdown_content` are overwritten only when `ai_input_hash` changes or the user explicitly requests reorganization.
- If an existing article has richer content than the deterministic imported Markdown, preserve the richer local content.

Collection semantics:

- First version stores imported favorites as `article` rows because the existing favorites list and AI Chat context use articles.
- `external_favorite_item` is the canonical cross-platform favorite object.
- If the app later introduces a generic `collection_item` or knowledge-object table, `external_favorite_item` can migrate to that model while keeping local article links for backward compatibility.

## AI整理

The first version should avoid triggering expensive AI calls for every old bookmark at once.

Rules:

- New imports get readable deterministic Markdown immediately.
- AI整理 is queued only for new items or items whose text/raw content changed.
- The queue should process a small batch per worker run.
- If no AI config exists, imported favorites remain readable and marked as pending AI without failing sync.
- Existing article processing and X/Twitter Markdown helpers should be reused where practical, but connector imports should not depend on WebView extraction to succeed.
- AI retries should operate from stored `external_favorite_item` plus linked `article`, not from another external API fetch.
- `ai_input_hash` is computed from normalized provider, external id, text, canonical URL, media URLs, and relevant metadata. AI整理 runs only when that hash is new or changed.

AI output should fill the existing article fields and tags:

- `ai_title`
- `ai_content`
- `ai_markdown_content`
- `article_tag`

The prompt should treat short social content differently from long articles: preserve the original wording, summarize why it may be worth saving, extract topics, and avoid inventing context not present in the Post.

## Error Handling

Source-level errors:

- Missing or expired refresh token: set source `status = auth_required`.
- Missing scope: set `auth_required`, `last_error_code = auth_missing_scope`, and a specific message mentioning `bookmark.read`.
- Rate limit: set `rate_limited`, `last_error_code = rate_limited`, and store `rateLimitResetAt` when available.
- Network/server failure: set a structured error such as `network_timeout` and retry through WorkManager with bounded attempts.
- Unknown provider or unsupported capability: disable the affected source with an actionable settings error and `last_error_code`.

Item-level errors:

- Invalid or empty item: store sanitized raw item with `sync_status = skipped`, `last_error_code = invalid_payload`, and a display-safe message.
- Article insert conflict: link to the existing article by URL or content hash, or mark `import_status = failed` with `last_error_code = import_conflict`.
- AI failure: keep the article and item; set `ai_status = failed` and a structured AI error so sync does not repeatedly reimport the same item.
- Duplicate across accounts: link both external items to the same local article when URL/content matching is confident.

Local favorites should not be removed just because a later sync does not see an item. Absence can mean pagination window, API behavior, permission changes, or user unbookmarking; deletion sync is outside first-version scope.

## Privacy and Security

- Store access tokens and refresh tokens encrypted with `SecretCipher`.
- Do not log tokens, OAuth callback codes, raw Authorization headers, full raw JSON, or full response bodies in normal logs.
- Only request read scopes for the first version.
- Make it clear in UI that imported X content becomes local app data.
- Preserve local-first behavior: sync is optional, and disabling a source stops future sync without deleting imported local favorites.
- Keep OAuth callback handling in Android code and avoid storing authorization codes after token exchange.
- Redact raw provider payloads from bug-report/export surfaces unless the user explicitly includes local app data.
- `raw_json` stores only fields required for display, import, reorganization, and debugging. Do not persist entire HTTP responses.
- If provider payloads become large, truncate nonessential fields or introduce compression only after evaluating database size, backup size, and restore compatibility.
- Backup/export documentation should state that external favorite records may contain imported social content and metadata.

## Completeness Checks

The design covers the first-version lifecycle end to end:

- Source onboarding: Android OAuth, source storage, encrypted credentials, multi-account uniqueness.
- Fetching: connector registry, provider capabilities, pagination, token refresh, rate-limit status, first-sync limits, conservative checkpoints.
- Persistence: source records, source-scoped external items, independent sync/import/AI states, structured errors, sanitized payloads.
- Import: deterministic local favorite creation, provider URL canonicalization, existing article dedupe, cross-account item linking, overwrite protection.
- AI整理: bounded queue, no-AI fallback, retry from local stored data, social-content-specific prompt behavior, `ai_input_hash` idempotency.
- Operations: manual sync, best-effort periodic sync, unique work per source, partial failure isolation, visible sync attempts.
- Privacy: read-only scopes, encrypted secrets, no token logging, optional sync, raw payload minimization.

First-version decision: keep item-level `ai_status` and update article fields when AI succeeds. Do not reuse the existing article-processing status as the source of truth for external favorite AI state, because article status currently serves multiple workflows.

## Testing

Unit tests:

- X response parser maps posts, includes, authors, media, pagination, and missing optional fields.
- Connector registry returns the X connector and rejects unknown providers.
- Source repository encrypts/decrypts auth JSON.
- Item repository upserts by `(source_id, external_id)` and links article ids.
- Importer creates favorite local articles and dedupes by canonical provider URL.
- X URL canonicalizer treats `x.com`, `twitter.com`, `mobile.twitter.com`, `/i/status`, username status paths, and tracking query variants for the same status id as one canonical URL.
- Importer preserves user-owned fields and does not overwrite richer local content during automatic import.
- `ai_input_hash` prevents repeated AI整理 for unchanged external items.
- Two X sources can sync the same post without overwriting each other's external item rows, while linking to one local article when URL matching is confident.
- Sync service stops early on known items and does not fail the whole run on one bad item.
- First sync respects the bounded import window, and older-history import continues in batches.
- Import and AI failures do not turn a successful fetch into a failed source sync.
- Auth-required and rate-limit responses update source status.
- Structured error codes drive retry/UI behavior without parsing display text.

Worker/UI tests:

- Manual sync enqueues unique work for a source.
- Periodic sync only includes enabled sources and requires network connectivity.
- Settings screen displays source status, last sync, and actionable auth errors.
- UI does not present periodic sync as real-time.

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
5. Provider auth config, X URL canonicalizer, and sanitized payload mapping.
6. Sync service, checkpoint policy, first-sync limit, and WorkManager scheduling constraints.
7. External favorite importer, overwrite rules, and local article linking.
8. AI整理 queue integration with bounded batches and `ai_input_hash`.
9. Settings UI for X source setup, status, manual sync, older-history import, and enable/disable.
10. Focused tests for mapping, persistence, dedupe, sync, import, AI state, and UI state.

Future slices can add bookmark folders, bidirectional write support, non-X connectors, external URL expansion from social posts, and user-configurable connector definitions.
