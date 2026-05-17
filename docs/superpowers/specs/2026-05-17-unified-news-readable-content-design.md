# Unified News Readable Content Design

## Goal

Fix two issues in the unified news summary: the summary body should read like a clean Markdown article, and each daily summary should use enough source material to reflect the full day rather than only a truncated preview.

## Root Cause

The current UI renders summary Markdown through `CitationText`, which splits the entire document into `FlowRow` text tokens. This destroys Markdown structure, so headings, bullets, paragraphs, and emphasis appear crowded and visually noisy.

The current crayfish source collector uses only `CrayfishNewsListItem.preview`. The latest device DB showed a single source with a 500-character preview ending mid-word, so AI only saw the first few items from a daily news file.

## Approved Direction

Use option A: a Markdown reading page. Render the summary with the existing Markdown renderer so headings, lists, bold text, and paragraphs keep their structure. Preserve clickable citation behavior by making citation references tappable within rendered text where possible, without adding a separate source-directory redesign.

For source coverage, keep the existing time-window filtering but fetch the matching crayfish daily file content after list discovery. Store the full file content in the source item used for prompting, so the AI can summarize more than the list preview.

## Scope

- Replace the summary body rendering path with Markdown-first rendering.
- Keep citation validation and citation click routing.
- Fetch crayfish full file content for matching list items.
- Keep budgets bounded so prompts do not become unbounded.
- Do not change database schema.
- Do not redesign bottom navigation or secondary pages.

## Verification

- Add regression tests proving the UI uses Markdown rendering instead of `FlowRow` token layout.
- Add regression tests proving crayfish matching files are fetched and full content is passed into prompt sources.
- Run focused unified news tests, compile, assemble, install, and launch.
