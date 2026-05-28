# Diary Photo Wall Final Design

## Goal

Redesign the diary list and diary editor around a photo-first journal style while keeping the existing diary data model and save/edit/delete behavior.

## Diary List

- Keep the app bar title `我的日记` centered according to existing app conventions.
- Show a month header, such as `五月`, followed by one natural month summary sentence.
- The month summary is generated from that month's diary content when entries exist.
- If the month has no entries, show a random positive, interesting, and reflective sentence.
- Do not label the sentence as AI-generated in the UI.
- List actual diary entries by date. Missing dates do not render placeholders.
- Each entry is one photo-wall card:
  - top row: time, mood, and a three-dot menu;
  - menu contains edit and delete;
  - images appear above text;
  - list cards may preview up to three images with a `+N` overlay for additional images;
  - body preview is limited and controlled by a single `展开正文` action;
  - tags are a single horizontally scrollable row with no `+更多` chip.

## Diary Editor

- Keep the editor as a bottom dialog/sheet.
- Top row: `取消`, centered `编辑日记` or `新建日记`, and `保存`.
- Date/time/mood show as a single quiet line.
- Images and text live in one paper surface; no separate `照片` or `正文` section titles.
- In edit mode all images are visible in a horizontally scrollable row; every image has a direct remove button.
- Tags are horizontally scrollable.
- Toolbar defaults to compact icon-only controls:
  - media picker, title, ordered list, unordered list, tag, mood, undo, redo, more.
- Media picker opens choices for camera and gallery.
- More opens a small anchored popover with low-frequency markdown actions: bold, italic, quote, task, divider, link.
- Save remains in the top row, not in the toolbar.

## Scope

- Do not change database schema.
- Do not change repository persistence contracts.
- Keep changes local to diary UI and ViewModel state only where needed for month summary display.
- Use theme tokens only; no hard-coded app colors in Kotlin.

## Verification

- Run `./gradlew :app:compileDebugKotlin`.
- Install with `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug` or target serial when requested.
- Launch with `adb shell am start -n com.dailysatori/.MainActivity`.
