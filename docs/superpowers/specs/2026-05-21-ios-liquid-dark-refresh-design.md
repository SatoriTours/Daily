# Daily Satori iOS Liquid Dark Refresh Design

## Goal

Refresh the whole app with a premium, iOS-inspired dark visual system that feels more polished, consistent, and easier to use. The redesign addresses four user-visible problems:

- Font sizes feel inconsistent across news, diary, and reading areas.
- The bottom tab bar takes too much vertical space.
- The current app icon lacks premium quality.
- Dialogs and input fields have inconsistent sizing, especially the AI assistant input bar.

## Direction

Use a restrained full-app dark system with selective Liquid Glass details. The app should feel premium and modern, but still practical for daily reading, journaling, and AI chat.

The selected approach is not a heavy neon theme. Deep backgrounds, subtle glass surfaces, cold blue accents, and carefully controlled shadows will create the premium feel. Long-form content must remain readable.

## Visual System

### Color

- Base background: near-black navy, around `#050816` and `#0F172A`.
- Surface cards: dark slate, around `#111827` and `#1E293B`.
- Elevated glass surfaces: translucent slate with subtle borders and blur-like layering where Compose support allows.
- Primary accent: sapphire/cyan blue, around `#7DD3FC`.
- Text primary: near-white, around `#F8FAFC`.
- Text secondary: slate gray-blue, around `#CBD5E1` and `#94A3B8`.
- Borders: subtle light strokes with low alpha, never bright outlines.

The design should avoid large purple gradients and strong neon glow across normal UI. Purple may only be used as a very subtle secondary background tint if needed.

### Typography

Unify the app around a system sans-serif style similar to iOS. The current split between a large editorial news font and smaller diary/reading fonts should be removed or reduced.

Target hierarchy:

- Large screen titles: `24-28sp`, semibold/bold.
- Section titles and card titles: `17-20sp`, semibold.
- Main body text: `15-17sp`, regular, comfortable line height.
- Metadata and labels: `12-13sp`.
- Bottom tab labels: `11-12sp`.

Long-form content can keep generous line height, but should not look like a separate newspaper-style product. News, diary, reading, and AI should feel like one app.

### Spacing And Shape

- Use existing `Spacing`, `Radius`, `Height`, `IconSize`, and Material theme tokens instead of hardcoded values.
- Prefer `20-24dp` radii for premium cards/dialogs.
- Prefer compact controls: inputs around `44-48dp`, icon buttons around `32-40dp` depending on context.
- Keep touch targets accessible even when the visual shape is compact.

## Components

### Bottom Tab Bar

Replace the current tall Material navigation bar appearance with a compact dark floating capsule.

Target behavior:

- Visual height around `50-56dp`, plus safe-area handling.
- Icons around `22-24dp`.
- Labels around `11-12sp`.
- Selected state uses sapphire accent and a subtle pill/glow, not a large Material indicator.
- Background uses dark glass surface with a subtle border and shadow.
- The bar should feel visually detached from content while not wasting vertical space.

### Inputs

Standardize app inputs and AI chat input.

Target behavior:

- Default single-line input height: around `44-48dp`.
- AI input bar uses a thinner glass capsule, not a bulky container.
- Send/stop button: around `32-36dp`, circular, sapphire accent when active.
- Placeholder/body text uses the shared typography scale.
- Focus state uses subtle sapphire border/glow.

### Dialogs And Sheets

Standardize dialogs and bottom sheets.

Target behavior:

- Dialog card uses deep surface color, subtle border, and `20-24dp` radius.
- Title/body/button typography follows the unified type scale.
- Buttons use consistent placement, color, and height.
- Avoid mismatched default Material sizing when it conflicts with the app visual system.

### Cards And Content Areas

Cards should use dark slate surfaces with controlled contrast. Content-heavy cards should prioritize readability over glass effects.

Target behavior:

- News, diary, reading, and AI cards use the same base surface logic.
- Important summary/AI cards may use a subtle glass highlight.
- Normal reading surfaces should avoid strong transparency or bright glow behind text.
- Metadata should be visually quieter but still legible.

## App Icon

Use the selected `Sapphire Ring` concept.

Icon goals:

- Premium dark glass base.
- Sapphire/cyan focus point.
- Metallic ring with cool blue-to-silver highlights.
- Strong inner shadow and center depression for depth.
- Slight glow around the blue focus point, but not a cheap neon effect.
- Avoid looking like a generic clock by making the ring abstract, polished, and brand-like.

Icon construction notes:

- Use a dark navy gradient background.
- Use a conic-style metal ring: sapphire highlight at the top, silver around the main arc, darker slate at the lower arc.
- Cut out the center with an inner dark surface and strong inset shadow.
- Use a short sapphire vertical focus marker at the top with controlled glow.
- Use a secondary silver marker angled down/right for balance.
- Use a metallic center cap with radial highlight and inner shadow.
- Verify readability at launcher sizes, especially around 48px equivalent.

## Implementation Boundaries

This refresh should be implemented through the existing Compose theme and shared components first. Avoid one-off fixes in individual screens unless a screen uses custom styling that bypasses the design system.

Primary files likely involved:

- `app/src/main/kotlin/com/dailysatori/ui/theme/Color.kt`
- `app/src/main/kotlin/com/dailysatori/ui/theme/Typography.kt`
- `app/src/main/kotlin/com/dailysatori/ui/theme/Spacing.kt`
- `app/src/main/kotlin/com/dailysatori/ui/theme/Shape.kt`
- `app/src/main/kotlin/com/dailysatori/ui/theme/Theme.kt`
- `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`
- `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt`
- shared dialog/input/card components under `app/src/main/kotlin/com/dailysatori/ui/component/`
- launcher icon resources under `app/src/main/res/`

## Testing And Verification

Verification must include:

- `./gradlew :app:compileDebugKotlin`
- If code/resources changed, install and launch using the project command with JDK 21.
- Visual check on device/emulator for bottom tab height, AI input height, dialogs, and launcher icon.
- Check dark text contrast in news, diary, reading, AI chat, and settings.

## Acceptance Criteria

- News, diary, reading, and AI no longer feel like separate apps because of typography mismatch.
- Bottom tab bar is visibly shorter and more premium.
- AI assistant input bar is slimmer and visually aligned with other input fields.
- Dialogs and sheets have consistent dark styling, typography, and spacing.
- App icon uses the refined Sapphire Ring direction with metal ring, inner shadow, and sapphire glow.
- The full app reads as an iOS-inspired premium dark product without sacrificing daily usability.
