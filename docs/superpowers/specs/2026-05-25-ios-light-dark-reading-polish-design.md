# iOS Light/Dark Reading Polish Design

## Goal

Improve Daily Satori's visual comfort without changing the app's main interaction model. The work focuses on typography, spacing, color, surface hierarchy, and reading layout across the app, with extra attention to article detail pages and news digest detail pages.

## Confirmed Direction

- Light mode uses an Apple News Light direction: clean white and light gray surfaces, high-contrast dark text, restrained system-blue accents, generous whitespace.
- Dark mode uses an Apple Dark Pro direction: near-black background, iOS-style dark gray surfaces, readable off-white text, muted secondary text, restrained system-blue accents.
- The app follows the system light/dark setting automatically.
- Detail pages use the comfortable reading density: larger body text, looser line height, clearer paragraph spacing.

## Scope

In scope:

- Global theme colors in `Color.kt` and `Theme.kt`.
- System-driven dark/light mode selection in `DailySatoriTheme`.
- Global typography in `Typography.kt`.
- Markdown reading styles in `MarkdownStyles.kt`.
- Detail reading surfaces for article detail, remote article detail, remote digest detail, and crayfish news detail.
- Small card/list typography or spacing adjustments only where needed to align with the new theme.
- Style guide updates if theme tokens or typography guidance changes.

Out of scope:

- Navigation or operation flow redesign.
- Large per-page layout rewrites.
- New content features.
- Database or persistence changes.

## Theme Design

### Light Mode

Use a soft iOS-like light palette:

- Background: near `#F5F5F7`.
- Primary surface: white.
- Secondary surfaces: light grays similar to iOS grouped backgrounds.
- Primary text: near `#1D1D1F`.
- Secondary text: near `#6E6E73`.
- Accent: restrained system blue.

Light mode must have strong contrast for body text and visible card boundaries without heavy borders or shadows.

### Dark Mode

Use a restrained iOS-like dark palette:

- Background: near black.
- Primary surface: `#1C1C1E`-style dark gray.
- Secondary surfaces: slightly lighter dark grays for cards and elevated areas.
- Primary text: off-white.
- Secondary text: muted gray.
- Accent: restrained system blue.

Dark mode should replace the current saturated liquid-blue style with calmer black/gray layering.

### System Selection

`DailySatoriTheme` should default to `isSystemInDarkTheme()` instead of always using dark mode. System bar appearance should adapt to the selected theme so status/navigation icons remain readable.

## Typography Design

Use a centralized Compose typography system. Avoid hardcoded `sp` values in screens.

Target behavior:

- Large content titles use tighter Apple-style letter spacing and stronger hierarchy.
- UI titles stay clear and compact.
- Body text remains readable at 15-17sp depending on context.
- Metadata and helper text should not become too small or too low contrast.

The existing `ContentFontFamily` and `UiFontFamily` can remain system-based unless the project already bundles a dedicated content font. If a new bundled font is added later, it should be handled as a separate scoped change.

## Reading Detail Design

Affected pages:

- `ArticleDetailScreen`
- `RemoteArticleDetailScreen`
- `RemoteDigestDetailScreen`
- `CrayfishNewsDetailScreen`

Reading style:

- Body text around 17sp with line height around 30sp.
- Paragraph and block spacing increased from the current cramped feel.
- Markdown headings use larger, clearer hierarchy.
- Lists and block quotes use comfortable indentation and spacing.
- Links use the theme primary color but avoid excessive visual noise.

Layout:

- Phone reading margins should feel closer to 20dp than the current tighter 16dp where content density is high.
- On larger widths, avoid making long-form text span the full viewport; keep line length readable where feasible within current layout constraints.
- Preserve existing tab behavior, cover collapse behavior, selection behavior, favorite/open actions, and back navigation.

Surface treatment:

- Remote article Markdown can keep a surface wrapper, but it should be light and subtle so content does not feel boxed in.
- Digest and crayfish detail content should feel like a clean reading page rather than a raw Markdown dump.

## Global Component Polish

Use the theme refresh to improve the full app without broad rewrites:

- Cards should use iOS-like surface layering instead of saturated dark-blue panels.
- Lists and cards should keep current interaction targets but improve typography and spacing where obviously cramped.
- Top bars, tabs, buttons, settings rows, and chips should inherit the new colors and typography through `MaterialTheme`.
- Existing token rules remain: no hardcoded colors, spacing, or fonts in feature screens unless a token does not exist and is added centrally.

## Accessibility

- Body text contrast must remain high in both light and dark modes.
- Secondary text should be muted but readable.
- Reading line height should stay in the accessible 1.5-1.75 range.
- Touch targets and existing interactions should not shrink.
- Color must not be the only indicator for critical states.

## Validation

After implementation:

- Run `./gradlew :app:compileDebugKotlin`.
- Run `./gradlew :app:assembleDebug`.
- If a device is available, run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`.
- If a device is available, launch with `adb shell am start -n com.dailysatori/.MainActivity`.
- Manually inspect light and dark modes if possible, especially article detail, remote article detail, remote digest detail, crayfish news detail, article/news lists, and settings.

## Risks

- Global typography changes can affect text wrapping across many screens.
- Switching to system light/dark mode can expose pages that assumed dark colors.
- Markdown style changes may alter the visual density of generated AI content and imported article content.

Mitigation:

- Keep implementation token-centered and minimal.
- Prefer small adjustments in shared theme/style files before page-specific edits.
- Verify compile/build and manually inspect the most content-heavy screens.
