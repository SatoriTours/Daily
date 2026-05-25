# Book Viewpoint Immersive Reading Design

## Goal

Improve the book viewpoint reading screen so it uses space efficiently and reads like a clean single-page article instead of a boxed card with duplicated book metadata.

## Confirmed Direction

Use an immersive single-page viewpoint layout:

- Remove the `当前书` progress strip.
- Remove the bottom `上一条 / 下一条` navigation bar.
- Keep horizontal swipe paging between viewpoints.
- Keep the top app bar, overflow actions, bottom app navigation, book search, book filter, content search, random, and delete flows unchanged.
- Remove the large card/background frame around viewpoint content.
- Display all viewpoint content directly on the page.

## Layout

The reading area should become:

```text
观点标题                                      4 / 10
                                《书名》 · 作者

观点正文

案例
案例正文
```

Details:

- The viewpoint title is the primary heading.
- Progress text such as `4 / 10` appears lightly on the same visual row as the title, aligned to the right where space allows.
- Book metadata appears below the title, right aligned and visually secondary.
- If the author is blank, show only `《书名》`.
- If no book is selected, omit the metadata line.
- Main content and example content use the existing book Markdown style.
- The whole page scrolls vertically when content is long.

## Title Cleanup

Generated viewpoint titles can repeat the book name, for example:

- `毛泽东选集（全四卷）：用事实材料校正抽象判断中的理解偏差。`
- `《毛泽东选集（全四卷）》：用事实材料校正抽象判断中的理解偏差。`

The UI should strip a leading current-book prefix from the displayed title. The stored title should not be mutated.

Rules:

- If the title starts with the current book title followed by `:` or `：`, remove that prefix.
- If the title starts with `《current book title》` followed by `:` or `：`, remove that prefix.
- Trim whitespace after removing the prefix.
- If no prefix matches, show the title unchanged.

## Component Boundaries

- `BooksScreen` remains responsible for pager state, selected book, and passing page progress/book metadata into the viewpoint content component.
- `ViewpointCard` should be changed into a direct content reader while keeping the public composable name for minimal call-site churn.
- Helper functions should cover title cleanup and book metadata formatting so tests can verify the rules without Compose rendering.

## Testing

Add or update JVM tests to verify:

- The book reading screen no longer calls `BookReadingProgressStrip` in the main reader flow.
- The book reading screen no longer calls `BookReadingNavigationBar` in the main reader flow.
- `ViewpointCard` no longer uses `Card` or `CardDefaults`.
- The content component receives progress and book metadata.
- Leading book-title prefixes are stripped from displayed viewpoint titles.
- Book metadata formats as `《书名》 · 作者`, and falls back to `《书名》` when author is blank.

## Validation

After implementation:

- Run focused book UI tests.
- Run `./gradlew :app:compileDebugKotlin`.
- Run `./gradlew :app:assembleDebug`.
- Install and launch the app if a device is available.

## Risks

- Removing explicit previous/next buttons makes swipe the primary paging method. This is intentional and accepted for this redesign.
- Existing source-text tests may still expect the old card or navigation structure and must be updated to the new approved layout.
