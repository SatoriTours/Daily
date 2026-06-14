# Reflection Actions Layout Design

## Goal

Polish the book reflection sheet so action controls feel intentional instead of scattered. The approved direction is the refined scheme B from the browser demo.

## Layout

- Keep the sheet title area as the primary command surface.
- Place a compact right-aligned capsule action group in the header: `开始`, `历史`, and `沉淀` or `更新`.
- In the empty state, show `沉淀` as disabled or omit it if the implementation cannot express disabled text cleanly.
- After an AI answer exists, enable the third action as `沉淀`; after a summary exists, label it `更新`.
- Remove the old body-level `开始想`, `换个问法`, `看历史`, and settle-row button stack.

## Body Content

- Use a calm guide card beneath the header: “从一个问题开始。也可以直接在底部输入自己的问题。”
- Replace `换个问法` with tappable question rows. Each row has a short label and a concrete prompt.
- Empty state question rows: `补角度`, `举例子`, `反问我`.
- Existing-answer follow-up rows: `继续追问`, `换个角度`.
- Keep conversation messages in the scrollable area and keep the chat input fixed at the bottom.

## Behavior

- `开始` sends the default first prompt.
- `历史` toggles the history view.
- `沉淀` or `更新` calls the existing summary generation action.
- Question rows send their associated prompt immediately.
- The summary action is disabled while processing or summarizing.

## Constraints

- Use existing theme spacing, radius, colors, and Material components.
- Do not add new persistence or API behavior.
- Preserve existing auto-scroll and fixed input behavior.
- Keep changes scoped to the reflection sheet and its focused tests.

## Testing

- Add source-level regression coverage for the new header action capsule and removal of the old scattered button stack.
- Keep existing reflection state, scrolling, and input placement tests passing.
