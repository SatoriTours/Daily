# X Markdown and App Icon Design

## Context

Daily Satori has two user-facing issues:

- Some saved X/Twitter status URLs can produce a useful AI summary while the saved original Markdown is sparse or nearly empty.
- The current Android launcher icon uses the default system edit icon and does not communicate the product identity.

## Goals

- For `x.com/i/status/...`, `x.com/.../status/...`, and equivalent Twitter status URLs, save original Markdown as faithful platform content rather than an AI-rewritten article.
- Preserve confirmed post text, original URL, and post media or media thumbnails when available.
- Replace the Android launcher icon with a simple, calm, recognizable icon representing daily awareness, with adaptive and legacy launcher compatibility.

## Non-Goals

- Do not invent missing post content when X hides it behind login, script rendering, or network restrictions.
- Do not build a dedicated X API integration.
- Do not redesign the in-app UI or brand system beyond the launcher icon.

## Article Markdown Behavior

For X/Twitter status URLs, Markdown generation should prefer a deterministic platform-content format and should not call AI to rewrite original Markdown:

```markdown
# 推文内容

<cleaned post text, if available>

原文链接：<original url>

## 媒体

![媒体 1](<image or thumbnail url>)
```

X visible text can include navigation, login prompts, and recommendations. The implementation should avoid saving the full WebView text directly for X URLs. It should use a small X-specific formatter that keeps only confirmed page/post text when available, includes media URLs or thumbnails, and always includes the original URL.

Fallback order for X Markdown:

1. Existing non-blank Markdown.
2. Newly formatted X Markdown containing confirmed text and/or media.
3. Minimal Markdown containing the original URL when no confirmed text or media is available.

AI summary output must not be used to fill original Markdown because that would make the “original” view non-original.

This avoids a mismatch where the AI summary contains rich interpretation while the original Markdown is empty or rewritten into non-original prose.

## App Icon Direction

The launcher icon should use the “每日觉察” concept:

- Background: calm teal/cyan gradient, suitable for Android launcher shapes.
- Foreground: white or near-white abstract sunrise and ring mark.
- Meaning: daily rhythm, clarity, awareness, and Satori-like insight.
- Style: simple, large-shape, no text, no emoji, readable at small sizes.
- Safe area: foreground mark stays centered with enough padding for Android adaptive icon masks.

Implementation should include adaptive icon resources, legacy launcher fallback, round icon, and monochrome icon where supported. Vector drawables are preferred for maintainability.

## Testing And Verification

- Add unit coverage for X/Twitter Markdown generation to ensure visible text and media URLs are preserved.
- Run `./gradlew :app:compileDebugKotlin` after code changes.
- If feasible with the connected environment, run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug` and launch the app.
