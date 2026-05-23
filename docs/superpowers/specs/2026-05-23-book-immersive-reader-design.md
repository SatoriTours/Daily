# Book Immersive Reader Design

## Goal

Restructure the book page around immersive reading of the current book's viewpoints. The page should feel like opening a reading deck, not a management console.

## Current Issues

- The top bar exposes add, search, filter, random, and delete actions at the same level as reading.
- The current book identity is only visible inside the viewpoint card, so page hierarchy is weak.
- Viewpoint navigation relies mostly on horizontal swiping, with little explicit progress or previous/next affordance.
- Reading, book switching, adding, searching, and deleting are visually mixed.

## Approved Direction

Use a single-book immersive reader layout:

- Top bar title becomes the current book title when a book is selected, with author as secondary context where space allows.
- Primary top-level actions are reduced. Add, content search, switch book, random, and delete move into a secondary menu.
- The reading area emphasizes one viewpoint card at a time.
- A small reading progress strip shows the current viewpoint position, for example `3 / 18`.
- A bottom reading navigation row provides `上一条`, progress, and `下一条` actions so users do not need to discover swipe gestures.
- Existing horizontal swipe remains available.
- Empty states continue to guide adding a book or selecting another book.

## Scope

- Modify `BooksScreen` structure and small helper functions.
- Reuse existing `ViewpointCard`, add/search sheets, book picker sheet, and delete dialog.
- Do not change database schema or repositories.
- Do not introduce new navigation routes.

## Testing

Add source-level UI contract tests verifying:

- The book page exposes immersive-reader helper names for current title, progress text, and previous/next actions.
- Top-level actions are reduced compared with the existing three-action toolbar.
- Secondary actions include switching books, adding books, searching content, random book, and deleting the current book.
- The bottom reading controls call page navigation instead of relying only on swipe.

## Out Of Scope

- Editing viewpoints.
- A full table-of-contents screen.
- A book cover hero layout.
- Large-screen split pane redesign.
