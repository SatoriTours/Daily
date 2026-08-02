# Database Schema Semantics

This file documents fields that look redundant but currently carry separate product or migration meaning.

## Article content fields

- `article.ai_markdown_content`: current renderable Markdown used by article detail screens and downstream summaries.
- `article.original_markdown_content`: source Markdown captured from remote news sync before local processing can rewrite `ai_markdown_content`.
- `article.ai_content`: plain summary/body text used by search, cards, and legacy imports.

Do not merge these fields without a migration that preserves remote-news source reconstruction and current article rendering.

## Article image fields

- `article.cover_image`: local cached image path or app-managed image reference.
- `article.cover_image_url`: original remote URL used for fetching or refetching covers.

The two fields intentionally separate local cache state from remote source identity.

## External favorite source errors

- `external_favorite_source.last_error`: legacy human-readable error summary.
- `external_favorite_source.last_error_code`: stable machine-readable error category.
- `external_favorite_source.last_error_message`: current display/debug detail.

New code should prefer `last_error_code` and `last_error_message`. Keep `last_error` until backup/import compatibility has a dedicated migration.

## External favorite item provider

- `external_favorite_item.provider` duplicates `external_favorite_source.provider` intentionally.

It preserves the provider identity on each imported item for source-scoped processing and diagnostics even if source rows are renamed, restored, or analyzed independently.

## External favorite ordering

- `external_favorite_item.favorited_at` is the remote favorite timestamp when the provider supplies one.
- X does not supply a bookmark timestamp. Its connector therefore stores a synthetic ordering value in this field, preserving the newest-to-oldest order returned by the remote bookmark pages.
- Existing X rows without this value trigger one remote-order repair scan during the next sync.
