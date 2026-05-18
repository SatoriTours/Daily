# Daily Satori App Style Unification Design

## Goal

Unify the visual language across Daily Satori without making every screen look identical. The app should feel calmer and more content-focused, especially in article, news, diary, AI summary, and Markdown-heavy surfaces. Utility screens such as settings, search, inputs, and navigation should remain clear and efficient.

The chosen direction is reading-first: long-form content should be comfortable to read, while lists and controls should keep enough density for daily use.

## Typography Direction

Use a two-family typography system:

- `Newsreader`: content and editorial text. Use for long-form reading, Markdown body, article/news detail pages, AI summaries, diary previews, and content section headings.
- `Roboto`: app interface text. Use for navigation, buttons, settings rows, inputs, metadata, labels, chips, status text, and short utility copy.

This creates a clear role split: content feels editorial and calm; controls feel familiar and Android-native.

## Type Scale

The app should use a small set of reusable sizes instead of scattered one-off values.

| Role | Size / Line Height | Family | Typical Use |
| --- | --- | --- | --- |
| Page title | 24sp / 32sp | Content pages: Newsreader; utility pages: Roboto | Detail titles, major screen titles |
| Section title | 18sp / 26sp | Newsreader or Roboto by context | Section headers, card groups |
| Card title | 16sp / 24sp | Roboto by default, Newsreader for content cards | Article cards, diary cards, settings cards |
| Reading body | 17sp / 30sp | Newsreader | Article/news/detail Markdown paragraphs |
| UI body | 15sp / 24sp | Roboto | Settings descriptions, normal UI copy |
| Metadata | 13sp / 18sp | Roboto | Time, source, status, captions |
| Label | 12sp / 16sp | Roboto | Chips, badges, compact labels |

The exact Material 3 typography slots should be mapped in `Typography.kt`, not recreated inline in screens. Screens should use `MaterialTheme.typography.*` or dedicated Markdown style functions.

## Markdown System

`MarkdownStyles.kt` should be the single source for Markdown typography and spacing.

Provide three public Markdown presets:

- `readingTypography()` and `readingPadding()`: for article detail, remote article detail, digest/detail reading views, and any full-screen reading content.
- `summaryTypography()` and `summaryPadding()`: for unified news summaries and medium-length generated summaries.
- `compactTypography()` and `compactPadding()`: for chat bubbles, diary cards, viewpoint cards, previews, and constrained card content.

Existing functions may remain only if they delegate to these presets during migration. The final call sites should prefer the role-based names above.

Markdown type rules:

- Markdown body uses `Newsreader` and the reading body scale for full reading surfaces.
- Markdown headings use `Newsreader` with clear but restrained hierarchy.
- Inline code and code blocks use the UI font family unless a monospace font is already available in the app.
- Links use the theme primary color and should not introduce custom colors per screen.
- Quotes should use the same body size as surrounding content, italic style, and consistent block padding.
- Lists should use the same body rhythm as paragraphs; avoid large one-off list item gaps.

## Color Semantics

Use Material color roles and existing app semantic colors instead of hardcoded page-specific colors.

| Role | Color Source |
| --- | --- |
| Primary readable text | `MaterialTheme.colorScheme.onSurface` |
| Secondary text, metadata | `MaterialTheme.colorScheme.onSurfaceVariant` |
| Page background | `MaterialTheme.colorScheme.background` or `surface` by surface type |
| Card/background blocks | `surfaceContainer` or `surfaceContainerHighest` |
| Links and primary actions | `MaterialTheme.colorScheme.primary` |
| Success, warning, info, error | `AppColors.success`, `AppColors.warning`, `AppColors.info`, `MaterialTheme.colorScheme.error` or equivalent semantic roles |

Hardcoded colors inside feature and component UI should be removed unless they are part of the theme definition itself or necessary transparent values such as `Color.Transparent`.

## Component Rules

Common components should carry the style system so feature screens do not repeatedly decide local typography.

- Top bars use Roboto and Material title roles unless the title is clearly content/editorial.
- Cards use consistent title, body, and metadata typography.
- Settings rows use Roboto title/body/metadata roles and theme colors.
- Search and input fields use Roboto UI body scale.
- Empty/loading/error states use the same hierarchy: title, body, optional metadata/action.

## Implementation Scope

The first implementation pass should focus on high-impact shared style files and high-visibility call sites:

- Rework `app/src/main/kotlin/com/dailysatori/ui/theme/Typography.kt` to define Newsreader and Roboto families and a consistent Material 3 type scale.
- Rework `app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt` around reading, summary, and compact presets.
- Update Markdown call sites in article, remote news, unified news, crayfish news, AI chat, viewpoint, and diary card surfaces.
- Update common components such as cards, top bars, settings rows, search/input, empty/loading states where they currently diverge from the new roles.
- Replace hardcoded colors in UI feature/component code with theme semantic colors where practical.
- Update `docs/04-style-guide.md` with the new typography and Markdown guidance.

The pass should avoid unrelated layout rewrites, database changes, behavior changes, and broad refactors.

## Testing And Verification

After implementation:

- Run `./gradlew :app:compileDebugKotlin`.
- Run `./gradlew :app:assembleDebug` if compile succeeds.
- If a device is connected, run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug` and launch `adb shell am start -n com.dailysatori/.MainActivity`.
- Manually inspect representative screens: article detail, remote article detail, unified news, AI chat, diary card/list, settings, and search/input surfaces.

## Non-Goals

- Do not redesign navigation structure.
- Do not change business logic, data models, database schema, or network behavior.
- Do not force every page into the same visual density.
- Do not introduce page-specific typography exceptions unless a shared role cannot cover the use case.
