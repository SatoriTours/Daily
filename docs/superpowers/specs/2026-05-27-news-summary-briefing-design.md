# News Summary Briefing Redesign

## Goal

Redesign the news summary page into a daily briefing experience. The page should feel closer to a polished design mockup while staying practical for reading news: the user first sees today's main conclusion, then scans numbered key points and source coverage.

The approved visual direction is option B from the browser demo: `Morning Briefing`.

## Scope

This design applies to `UnifiedNewsSummaryPage` and the summary card shown by `TodayUnifiedNewsCard`.

In scope:

- Improve the summary tab visual hierarchy.
- Redesign each daily summary card as a briefing card.
- Keep source chips at the top of the page.
- Improve refresh/error and generating states to match the briefing style.
- Support both light and dark modes through `MaterialTheme.colorScheme`.

Out of scope:

- Changing summary generation logic.
- Changing database schema or migrations.
- Redesigning remote source article detail screens.
- Adding new navigation destinations.

## Visual Structure

The page keeps the existing app scaffold and title `新闻汇总`.

The summary tab layout is:

1. Horizontal source switcher.
2. Optional lightweight status notice for refresh errors or manual refresh messages.
3. Optional generating skeleton card.
4. Lazy list of briefing summary cards.

Each briefing card contains:

1. Metadata row: summary date, generation status when available, source count.
2. Hero section: large title such as `今天需要知道的 N 件事` plus a short lead paragraph derived from the summary content.
3. Stats row: source count, extracted key-point count, and citation/source reference count when derivable from existing content.
4. Numbered key points: parsed from citation-linked markdown list items when possible.
5. Source row: compact source chips and a hint that numbered/cited items can open sources.

## Visual Fidelity Guardrails

The implementation must preserve the visual quality of the approved browser demo. The page should not degrade into the previous plain bordered-card markdown layout.

Required visual traits:

- The first visible card area must feel like a briefing hero, not a normal list item.
- The hero area needs a distinct tinted container using `primaryContainer` or a theme surface blend.
- The title area must use larger typography than regular card titles and strong weight.
- Key points must render as numbered rows with compact number badges, not as raw markdown bullets when extraction succeeds.
- Metadata and source chips must be visually compact and rounded.
- Cards must use generous radius and internal spacing similar to the demo.
- Light and dark mode must both keep visible card boundaries, readable secondary text, and enough contrast between the hero container and the page background.

Implementation should avoid:

- A flat white/dark card with only `CitationText` inside.
- Dense markdown blocks that make the page look like an article detail page.
- Hard-coded colors copied from the HTML demo.
- Excessive shadows or translucent effects that Compose cannot reproduce consistently on Android.
- Emoji icons in the redesigned UI. Existing content-generated emoji headings may remain until content formatting is separately addressed.

## Implementation Risks And Mitigations

Risk: the HTML demo uses CSS gradients, shadows, and exact pixel tuning that Compose may not match directly.

Mitigation: reproduce the design language, not raw CSS. Use Material 3 containers, large radii, strong hierarchy, badges, and spacing tokens to achieve the same perceived quality.

Risk: the existing summary content is markdown and may not always contain clean citation bullet items.

Mitigation: parse best-effort key points. If parsing fails, keep a styled fallback with `CitationText`, but still wrap it in the briefing card hero/source structure.

Risk: `UnifiedNewsScreen.kt` is already large.

Mitigation: keep small parsing helpers and composables focused. If the file becomes hard to scan, move briefing-specific UI to a new file under `ui/feature/unifiednews/`.

Risk: dark mode can look dull if every surface uses the same color.

Mitigation: use a clear hierarchy of `background`, `surface`, `surfaceContainer`, `surfaceContainerHighest`, and `primaryContainer` so the card, hero, chips, and badges remain separated.

## Interaction

Citation interactions remain unchanged:

- Clicking cited content opens the existing source detail flow through `viewModel.openCitation`.
- Source tabs still call `selectSummarySource` and `selectRemoteSource`.
- The menu still contains local articles, favorites, settings, and generate/update actions.

The redesigned card should not introduce new gestures. It should make existing clickable citation areas more visually obvious through numbered blocks and source hints.

## Light And Dark Mode

All colors must use existing theme values:

- Background: `MaterialTheme.colorScheme.background`.
- Cards: `surface`, `surfaceContainer`, `surfaceContainerHighest`.
- Primary emphasis: `primary`, `primaryContainer`, `onPrimaryContainer`.
- Text: `onSurface`, `onSurfaceVariant`.
- Borders: `outline` or `outlineVariant`.

No hard-coded colors are allowed in Compose implementation.

## Typography And Spacing

Use existing style tokens:

- Page title remains controlled by `AppScaffold`.
- Briefing hero title uses `MaterialTheme.typography.headlineSmall` with `FontWeight.SemiBold` or stronger.
- Card section title uses `titleMedium`.
- Key point title uses `bodyMedium` with `FontWeight.SemiBold`.
- Lead text, descriptions, and fallback summary text use `bodyMedium` or `bodySmall` depending on density.
- Metadata, hints, source chips, and stat labels use `labelSmall` or `bodySmall`.
- `Spacing`, `Radius`, `BorderWidth`, and `IconSize` constants must drive layout.

The implementation should preserve mobile readability. Cards should avoid dense nested layouts deeper than necessary.

Target visual density:

- Page horizontal padding: `Spacing.m`.
- Lazy list vertical gap: `Spacing.m`.
- Briefing card internal padding: `Spacing.m` to `Spacing.l` depending on available width.
- Hero internal vertical spacing: mostly `Spacing.s` and `Spacing.m`; avoid cramped `Spacing.xs` between major text blocks.
- Numbered key point row vertical padding: at least `Spacing.s`.
- Source chips: compact, rounded, with horizontal padding around `Spacing.s`.
- Card radius: use `Radius.l` or `Radius.xl`; avoid small `Radius.s` cards for this page.

The typography should feel editorial and premium. Do not make every text block the same size or weight. The hero title, key point titles, metadata, and secondary descriptions must have clear contrast.

## Color, Shape, And Elevation Tokens

The visual quality depends on color layering as much as layout. Implementation must use these roles consistently:

- Page background: `MaterialTheme.colorScheme.background`.
- Main card container: `MaterialTheme.colorScheme.surface` with a visible `outline` border.
- Hero container: `MaterialTheme.colorScheme.primaryContainer` or `surfaceContainer` with primary accents.
- Stat tiles and metadata badges: `surfaceContainer` or `surfaceContainerHighest`.
- Active source chip: `primary` container with readable `onPrimary` text.
- Inactive source chips: `surfaceContainer` with `onSurfaceVariant` text.
- Number badge: `primaryContainer` with `onPrimaryContainer` text.
- Secondary descriptions: `onSurfaceVariant`; avoid low-alpha custom text that becomes unreadable in dark mode.
- Error/refresh notice: `surfaceContainerHighest` with `onSurfaceVariant`, unless it needs semantic `errorContainer`.

Shape and elevation guidance:

- The briefing card should have a large rounded shape and subtle border. It should not rely on heavy shadows.
- Nested elements should use progressively smaller radii: card largest, hero medium-large, badges/chips circular.
- Borders must remain visible in both light and dark modes.

## Demo Fidelity Requirements

The implemented Android page should not be worse than the approved browser demo in perceived polish. Because Compose and HTML render differently, fidelity is judged by these user-visible qualities:

- Clear first impression: the first summary card looks designed, not auto-generated.
- Strong hierarchy: hero title is dominant, numbered points are scannable, metadata is quiet.
- Balanced spacing: no cramped text, no oversized empty gaps, no inconsistent paddings between sections.
- Color harmony: light mode is airy and clean; dark mode has enough contrast and does not collapse into flat black cards.
- Component consistency: chips, badges, stats, skeleton, and notice surfaces feel like one visual system.
- Content readability: Chinese text line height and size remain comfortable on a phone screen.

If an implementation choice makes the Compose screen look visibly weaker than the demo, prefer adding a small focused component/style adjustment over shipping a merely layout-similar version.

## Content Parsing

The existing summary content is markdown with citation links. The redesign should reuse the same content source and avoid changing stored data.

Preferred parsing behavior:

- Extract citation-linked bullet items as numbered key points.
- Use the first non-empty non-heading markdown paragraph as the lead when available.
- Use `今天需要知道的 N 件事` where `N` is the extracted key-point count; fall back to `今日新闻简报` when there are no extracted key points.
- Fall back to the current `CitationText` rendering if parsing cannot produce key points.
- Preserve the ability to open citation sources.

## States

Loading:

- Keep `LoadingIndicator` for initial full-page loading.

Empty:

- Keep the existing `EmptyState` copy: `暂无新闻汇总` and `点击右上角生成/更新当日新闻`.

Generating:

- Replace the plain skeleton card with a briefing-style skeleton card using the same card shape and spacing as summary cards.

Refresh message/error:

- Show a compact notice surface above content.
- Use theme surfaces and `onSurfaceVariant` text.
- Do not block reading existing summaries.

## Components

Expected Compose units:

- `UnifiedNewsBriefingCard`: replaces the visual body of `TodayUnifiedNewsCard`.
- `UnifiedNewsBriefingHero`: metadata, headline, lead, and stats.
- `UnifiedNewsBriefingPointList`: numbered key points with citation callbacks.
- `UnifiedNewsSourceChipRow`: compact source row inside the card.
- `UnifiedNewsBriefingSkeleton`: generating state card.

These components can live in `UnifiedNewsScreen.kt` unless the file becomes too large to understand. Prefer the smallest correct split.

## Testing And Verification

Manual verification:

- Light mode and dark mode both have readable contrast.
- Summary tab still opens citation source details.
- Remote source tabs still show articles.
- Refresh message and generating skeleton are visually consistent.
- Empty state remains usable.

Build verification:

- Run `./gradlew :app:compileDebugKotlin` after implementation.
- Run `./gradlew :app:assembleDebug` if compile succeeds.
- Per project instructions, install and launch on device after code changes when feasible.

## Acceptance Criteria

- The news summary page visually matches the approved briefing direction from the demo.
- The first summary card has the same perceived hierarchy as the demo: prominent hero, compact metadata, stats, numbered key points, and source chips.
- The design supports light and dark modes without hard-coded colors.
- Existing navigation and citation behavior continue working.
- No database or generation logic changes are required.
- Android compilation passes.
