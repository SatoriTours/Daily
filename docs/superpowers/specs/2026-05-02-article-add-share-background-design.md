# Article Add, Share, And Background Processing Design

**Goal:** Make adding articles visible in the list, make external shares return to the source app with a Toast, and keep article processing resilient when the app goes to the background.

**Scope:** Android app article URL intake, article list scroll feedback, and background article processing entry points.

## Requirements

- When a user adds an article from the article list dialog, the list scrolls to the top after the article is created so the new placeholder card is visible.
- External shares should not open the full app UI. The app should receive the share in a transparent receiver Activity, enqueue/save the URL, show a system Toast, and finish so the user returns to the previous app.
- Duplicate external shares should show a Toast with `链接已存在` and finish.
- App-internal clipboard prompts still use the existing confirmation dialog. Duplicate clipboard URLs still use the root snackbar.
- Article processing should not rely on ViewModel lifetime. Manual add, clipboard confirm, and external share should enqueue durable background work.
- Interrupted articles with recoverable statuses should continue via background work when the app starts.

## Architecture

- `ArticleListScreen` owns a `LazyListState` and reacts to a one-shot event from `ArticlesViewModel` by animating to item `0`.
- `ShareReceiverActivity` receives `ACTION_SEND text/plain`, extracts the first URL, checks duplicates, enqueues background work for new URLs, shows a Toast, and immediately finishes.
- `MainActivity` remains the normal launcher and no longer owns the external share intent filter.
- `ArticleProcessingWorker` is the durable Android background boundary. It uses Koin to resolve `WebpageParserService`, then runs either URL save work or interrupted-processing resume work.
- ViewModels enqueue work instead of directly running long webpage processing in `viewModelScope`.

## Data Flow

- Manual add: dialog URL -> `ArticlesViewModel.addArticle()` -> enqueue worker -> emit scroll-to-top event -> list animates to top.
- Clipboard confirm: root intake ViewModel -> enqueue worker -> dialog closes.
- External share: source app -> `ShareReceiverActivity` -> duplicate check or enqueue worker -> Toast -> `finish()`.
- App start: `DailySatoriApplication` -> enqueue resume worker for recoverable articles.

## Error Handling

- Duplicate checks use existing normalized URL comparison.
- Invalid share text shows `未找到链接` Toast and finishes.
- Worker failures are delegated to existing `WebpageParserService.saveWebpage()` status/error updates where an article has been created. Worker retry policy stays minimal unless a concrete retry requirement appears.

## Testing

- Unit tests cover the list scroll event decision and share-receiver message helpers.
- Compile verification uses `./gradlew :app:compileDebugKotlin`.
- Device verification installs and starts the app with the existing project commands.
