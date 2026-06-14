# Reading Reflection Demo Design

## Goal

Create a browser demo for a redesigned reading reflection flow. The demo should show a full path from reading a viewpoint to opening reflection, using compact actions, and browsing history.

## Problems To Solve

- The reading page overflow menu carries too many unrelated actions.
- The reflection sheet uses large stacked buttons for `沉淀`, `换个角度聊`, and `历史`, which consumes too much vertical space.
- History is hard to browse because it behaves like a mode toggle rather than a navigable timeline.
- The `深入想想` button at the bottom of the reading content looks visually heavy and interrupts reading.

## Demo Direction

Use `阅读页轻入口 + 反思面板三段式 + 历史时间线`.

## Reading Page

- Replace the bottom-of-content `深入想想` button with a lightweight floating capsule near the lower-right reading area.
- Keep the reading text visually dominant.
- Keep the primary bottom navigation visually separate from reflection actions.

## Reflection Sheet

- Use a bottom-sheet style panel.
- Top section: a minimal `想一想` title, without repeating `当前观点` or the viewpoint title.
- First action state: show one primary action, `开始想`, with `换个问法` as a small secondary action beside the prompt and `看历史` as a quiet secondary entry.
- After the user starts or changes the angle, reveal a lightweight settle row with `沉淀`; do not show `沉淀` as an initial peer action.

## History

- Show history as a timeline/list of reflection sessions.
- Each item shows status, short summary, and time.
- Selecting a history item previews the saved reflection without forcing a confusing current/history toggle.
- Viewing history should hide settle affordances to avoid treating old records as the active reflection.

## Demo Requirements

- Static browser demo served on `0.0.0.0:10001`.
- URL must be usable from the local network.
- No production app code changes for the demo.
- The visual style should be calm, reading-first, and minimal.

## Out Of Scope

- Persisting real history data.
- Integrating with the Android app implementation.
- Changing release version or publishing a new APK.
