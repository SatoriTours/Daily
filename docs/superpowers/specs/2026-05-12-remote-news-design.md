# Remote News Design

## Goal

Add a read-only "远程新闻" experience to the Android app that reads articles, digests, and feeds from the Rails project's `/api/v1/external` API. Data is fetched from the server when the user opens or refreshes the page and is never saved to the local app database.

## Rails API

The Rails project at `../web` exposes the external API in `app/controllers/api/v1/external_controller.rb`.

Authentication remains token based:

- `Authorization: Bearer <token>`
- `X-Api-Token: <token>`

Add pagination to the read-only list endpoints:

- `GET /api/v1/external/digests?page=1&per_page=20`
- `GET /api/v1/external/articles?page=1&per_page=20`
- `GET /api/v1/external/feeds?page=1&per_page=50`

List responses include their existing collection key plus pagination metadata:

```json
{
  "digests": [],
  "pagination": {
    "page": 1,
    "per_page": 20,
    "total": 100,
    "total_pages": 5,
    "next": 2
  }
}
```

`GET /api/v1/external/digests/:id` continues to return the digest and its related completed articles. `GET /api/v1/external/articles/:id` returns a detailed article payload including content.

## App Settings

The existing settings page adds a row in the "网络与同步" section:

- Title: `远程新闻设置`
- Subtitle: configured service host, or `未配置`

The settings subpage contains:

- `服务地址`, for example `http://192.168.1.10:3000`
- `API Token`
- `测试连接`, which requests one digest with `per_page=1`
- `保存`

No automatic refresh interval is configured.

Settings are stored through the existing settings repository with new keys:

- `remote_news_base_url`
- `remote_news_api_token`

## App Navigation

The bottom navigation adds a new top-level tab:

- `远程新闻`

The tab opens a single remote news page. It does not contain top tabs. The default mode is the digest list.

## Remote News Page

The default view is the digest list:

- Fetches `GET /api/v1/external/digests?page=1&per_page=20` when opened.
- Pull-to-refresh reloads page 1.
- Scrolling near the bottom loads `pagination.next`.
- Digest cards show date, title, summary, highlights, and article count.
- Tapping a digest opens digest detail.

The top-right overflow menu provides:

- `查看文章`
- `查看信息源`
- `刷新`

Selecting `查看文章` switches the same page into article list mode:

- Fetches `GET /api/v1/external/articles?page=1&per_page=20`.
- Supports pull-to-refresh and next-page loading.
- Uses the same visual language as the existing local article list.
- Removes local write actions such as add, favorite, delete, and reprocess.
- Tapping an article opens remote article detail.

Selecting `查看信息源` switches the same page into feed list mode:

- Fetches `GET /api/v1/external/feeds?page=1&per_page=50`.
- Supports pull-to-refresh and next-page loading.
- Shows name, category, status, health score, last fetched time, and next fetch time.
- Read-only; no create, edit, delete, pause, or resume actions.

## Detail Pages

Digest detail:

- Calls `GET /api/v1/external/digests/:id`.
- Shows summary, highlights, sections, and associated articles.
- Associated articles come from the digest detail response and are not persisted locally.
- Tapping an associated article opens remote article detail.

Remote article detail:

- Calls `GET /api/v1/external/articles/:id`.
- Uses the existing local article detail visual style where practical.
- Shows title, source, summary, viewpoints, cover image, and markdown content.
- Keeps read-only actions only, such as opening the original URL in the browser.

## Data Flow

The Android app adds a shared remote news service around the existing Ktor `HttpClient`:

- Normalizes the configured base URL.
- Appends `/api/v1/external/...` paths.
- Adds the bearer token header.
- Parses remote article, digest, feed, and pagination DTOs.
- Maps HTTP and network failures to user-visible messages.

The app ViewModel owns in-memory state for the current mode, loaded items, pagination, loading state, and error state. No remote news data is inserted into SQLite or cached as local app content.

## Error And Empty States

- Missing service address or token: show `请先配置远程新闻服务`.
- `401`: show `Token 无效，请检查远程新闻设置`.
- Network failures: show a retryable error state.
- Next-page failures: keep already loaded data and show a bottom retry action.
- Empty lists: show a mode-specific empty state.

## Testing And Verification

Rails tests cover:

- External API token authentication remains required.
- `articles`, `digests`, and `feeds` return pagination metadata.
- `page` and `per_page` return the expected subset.

Android tests cover:

- URL normalization and path construction.
- Bearer token header usage.
- Empty configuration handling.
- `401` mapping to the token error message.
- Pagination merge behavior in the ViewModel.

Verification commands after implementation:

- Rails tests for the external controller in `../web`.
- `./gradlew :app:compileDebugKotlin` in the Android project.
- If a device is connected, install and launch the app with the project-standard commands.
