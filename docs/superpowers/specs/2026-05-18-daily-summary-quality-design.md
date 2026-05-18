# Daily Summary Quality Design

## Goal

Improve the quality and reliability of the daily unified news summary while preserving the current user flow and visual design.

The feature should continue to feel like the same screen, but the generated summary should be more trustworthy, less repetitive, and clearer when source data is incomplete.

## Non-Goals

- No large UI redesign.
- No navigation changes.
- No database schema changes.
- No new backend service.
- No change to the existing article detail route behavior.

## Current Problems

The current daily summary path can be improved in four areas:

- Source inputs can contain duplicates, near-duplicates, empty titles, short bodies, or low-information articles.
- Prompt input can over-represent one source when that source returns many items.
- Generated summaries rely on citation IDs, but citation validity should be enforced more strictly before presenting the result.
- Partial failures are not visible enough; a summary based on incomplete source data should say so clearly.

## Proposed Design

### Source Preparation

Before building the prompt, normalize and filter candidate articles in one dedicated preparation step.

Rules:

- Drop articles with blank titles.
- Drop articles with no useful text content after trimming title, summary, and body.
- Drop articles whose combined textual content is below the minimum useful length.
- Deduplicate exact URL matches.
- Deduplicate repeated title/source combinations.
- Prefer the most content-rich version when duplicates are found.
- Limit per-source contribution so one feed cannot dominate the prompt budget.

This keeps generation deterministic and makes prompt construction easier to test.

### Prompt Shape

Keep the output compatible with the current rendering path, but make the requested structure more explicit.

The prompt should ask for a concise daily briefing with these sections:

- 今日要点: the most important cross-source developments.
- 重要变化: concrete updates, shifts, or new information.
- 值得关注: forward-looking items the reader may want to track.
- 来源引用: citation markers that map claims back to input article IDs.

Prompt constraints:

- Do not invent facts not present in the provided articles.
- Every important claim must be supported by at least one article ID.
- Prefer synthesis across sources over listing articles one by one.
- If there is not enough reliable input, say the summary cannot be generated reliably instead of guessing.

### Citation Validation

After generation, validate all citations against the prepared source set.

Behavior:

- Remove citation references to missing or filtered-out article IDs.
- Keep only citations that can navigate to an existing article detail.
- Treat a generated summary with no valid citations as unreliable.
- Return a clear failure state when reliable citation grounding cannot be established.

This preserves the current clickable citation behavior while reducing broken or misleading references.

### Partial Failure Handling

The summary service should distinguish between complete failure and partial input failure.

Behavior:

- If at least one source succeeds and enough valid articles remain, generate the summary.
- Attach a lightweight warning when some sources failed or were skipped.
- If all sources fail, return the existing error path.
- If sources succeed but no useful articles remain after filtering, return a clear no-content failure.

The UI can reuse existing warning/error presentation. No new visual system is required.

## Component Boundaries

Keep the implementation focused and testable by separating responsibilities inside the unified news service layer.

Suggested units:

- `UnifiedNewsSummaryService`: orchestrates source collection, preparation, generation, validation, and result assembly.
- Source preparation helper: filters, deduplicates, sorts, and budgets candidate articles.
- Prompt builder: converts prepared articles into model input and preserves stable source IDs.
- Citation validator: checks generated references against prepared source IDs.

These can start as private functions or small internal data classes in the existing service file. Extract separate files only if the implementation becomes too large or tests need direct access.

## Data Flow

1. Collect candidate articles from configured local and remote sources.
2. Record source-level failures without aborting the whole run.
3. Prepare articles by filtering, deduplicating, ranking, and budgeting them.
4. Return no-content failure if the prepared set is empty or too small.
5. Build the prompt from prepared articles with stable citation IDs.
6. Generate the AI summary.
7. Validate citations against the prepared set.
8. Return success with summary, valid citations, and optional warnings.
9. Return failure if generation or citation grounding is unreliable.

## Error Handling

Expected outcomes:

- `Success`: summary generated with valid citations.
- `Success with warning`: summary generated from partial source data.
- `No useful content`: source calls may have succeeded, but filtering removed all useful items.
- `Generation failure`: model call failed or returned unusable content.
- `Ungrounded summary`: generated text has no valid citations and should not be shown as reliable.

Warnings should avoid exposing technical internals. User-facing text should describe impact, not implementation details.

## Testing Strategy

Add or update tests around the daily summary behavior.

Required coverage:

- Exact duplicate URLs are removed.
- Duplicate title/source pairs keep the richer article.
- Blank-title and low-content articles are excluded from prompt input.
- Per-source budgeting prevents one source from consuming all slots.
- Generated citations referencing missing IDs are removed.
- A summary with zero valid citations is rejected.
- Partial source failure still generates when enough valid articles remain.
- All-source failure returns an error.
- No useful content returns a clear failure.

## Verification

Minimum verification after implementation:

- `./test.sh quick`
- `./gradlew :app:compileDebugKotlin --no-configuration-cache`
- `./gradlew :app:testDebugUnitTest :shared:testDebugUnitTest --no-configuration-cache`

If behavior touches Android UI state, also run:

- `./gradlew :app:assembleDebug --no-configuration-cache`

Device installation remains optional unless a connected device is available.

## Acceptance Criteria

- Daily summaries are generated only from useful, deduplicated source articles.
- Source diversity is preserved within the prompt budget.
- Summary output retains the current visible flow and navigation behavior.
- Invalid citations cannot produce broken article-detail navigation.
- The app clearly distinguishes partial source failure from total failure.
- Regression tests cover filtering, deduplication, citation validation, and partial-failure behavior.
