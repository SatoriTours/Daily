# Book Reading Typography Design

## Goal

Make the book reading page more comfortable by increasing the typography hierarchy used in book viewpoint cards while matching the diary page's readable content rhythm.

## Scope

- Change only the book viewpoint card reading area.
- Keep global card Markdown styles unchanged so diary, AI, and news cards keep their current density.
- Use theme typography tokens instead of hard-coded font sizes.

## Design

`ViewpointCard` should use a book-specific Markdown typography preset for viewpoint content and examples. The preset should use `MaterialTheme.typography.bodyLarge` for paragraph/list text and larger heading levels than the shared card preset.

The card chrome should also become more readable:

- Viewpoint title uses `titleLarge` instead of `titleMedium`.
- Book metadata uses `labelMedium` instead of `labelSmall`.
- The example section label uses `titleMedium` instead of `titleSmall`.

## Testing

Add a source-level typography contract test that verifies:

- `MarkdownStyles.bookTypography()` exists.
- `ViewpointCard` uses `MarkdownStyles.bookTypography()` for both content and example Markdown blocks.
- `ViewpointCard` uses the larger title and metadata typography tokens.

## Out Of Scope

- No global `cardTypography()` changes.
- No layout redesign.
- No database or data-layer changes.
