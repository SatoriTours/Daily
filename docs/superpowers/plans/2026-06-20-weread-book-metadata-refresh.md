# WeRead Book Metadata Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store WeRead book metadata on the book record, use that metadata to make refresh stable, enrich AI/MCP generation with useful book details, and add a shortcut to open the book in WeRead.

**Architecture:** WeRead remains the metadata source: title, author, category, introduction, cover, ISBN, source URL, and internal WeRead book id are fetched and stored. AI and MCP remain the viewpoint source: prompts receive useful bibliographic context, but not the WeRead book id. Refresh uses stored metadata first, then recovered old viewpoint context, then title + author search as the final fallback.

**Tech Stack:** Kotlin, Jetpack Compose, SQLDelight, existing `WeReadSkillService`, `BookRepository`, `BooksViewModel`, and Android `Intent.ACTION_VIEW`.

---

## File Map

- Modify `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`: add book metadata columns and update book insert/update queries.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`: bump `DatabaseConfig.currentSchemaVersion` from `13L` to `14L`.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`: add V13 -> V14 migration for book metadata columns.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/data/repository/BookRepository.kt`: expose insert/update parameters for ISBN, source URL, and WeRead book id.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookSearchService.kt`: add `sourceBookId` to `BookSearchResult`.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt`: parse WeRead book id into `BookSearchResult`, use richer metadata in prompts, and keep WeRead book id out of AI/MCP prompts.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookSearchViewModel.kt`: save metadata when adding a book.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksViewModel.kt`: refresh from stored metadata and persist recovered metadata for legacy books.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`: add “打开微信读书” to the more menu when a source URL exists.
- Add focused tests in existing book test files.

---

### Task 1: Persist Book Metadata

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/BookRepository.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/data/repository/BookRepositoryInsertApiTest.kt`

- [ ] **Step 1: Write failing repository API test**

Add a test that compiles against the intended API:

```kotlin
@Test
fun insertAndReturnIdAcceptsBookMetadata() {
    assertTrue(::insertAndReturnIdWithMetadataSignatureCompiles.name.isNotBlank())
}

private fun insertAndReturnIdWithMetadataSignatureCompiles(repository: BookRepository): Long =
    repository.insertAndReturnId(
        title = "禅宗公案",
        author = "佚名",
        category = "宗教哲学",
        coverImage = "https://example.com/cover.jpg",
        introduction = "记录禅门机锋与问答。",
        isbn = "9780000000000",
        sourceUrl = "weread://reading?bId=3300045871",
        weReadBookId = "3300045871",
    )
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.dailysatori.data.repository.BookRepositoryInsertApiTest
```

Expected: compilation fails because `insertAndReturnId` does not accept `isbn`, `sourceUrl`, or `weReadBookId`.

- [ ] **Step 3: Update SQLDelight schema**

Change the book table:

```sql
CREATE TABLE book (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    author TEXT NOT NULL,
    category TEXT NOT NULL,
    cover_image TEXT NOT NULL,
    introduction TEXT NOT NULL,
    isbn TEXT NOT NULL DEFAULT '',
    source_url TEXT NOT NULL DEFAULT '',
    weread_book_id TEXT NOT NULL DEFAULT '',
    has_update INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

Update queries:

```sql
insertBook:
INSERT INTO book (title, author, category, cover_image, introduction, isbn, source_url, weread_book_id, has_update, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

updateBook:
UPDATE book SET title = ?, author = ?, category = ?, cover_image = ?, introduction = ?, isbn = ?, source_url = ?, weread_book_id = ?, has_update = ?, updated_at = ?
WHERE id = ?;
```

- [ ] **Step 4: Add database migration**

In `Config.kt`, change:

```kotlin
const val currentSchemaVersion = 14L
```

In `DatabaseMigration.runMigrations()`, add:

```kotlin
if (currentVersion < 14) {
    migrateV13ToV14()
}
```

Add:

```kotlin
private fun migrateV13ToV14() {
    log.i { "Migration V13 -> V14: Book metadata columns" }
    listOf(
        "isbn TEXT NOT NULL DEFAULT ''",
        "source_url TEXT NOT NULL DEFAULT ''",
        "weread_book_id TEXT NOT NULL DEFAULT ''",
    ).forEach { column ->
        try {
            runSql("ALTER TABLE book ADD COLUMN $column")
            log.i { "Added book column: $column" }
        } catch (e: Exception) {
            log.w(e) { "Could not add book column: $column" }
        }
    }
}
```

- [ ] **Step 5: Update repository signatures**

Update `BookRepository.insert`, `insertAndReturnId`, and `update`:

```kotlin
fun insert(
    title: String,
    author: String,
    category: String,
    coverImage: String,
    introduction: String,
    isbn: String = "",
    sourceUrl: String = "",
    weReadBookId: String = "",
    hasUpdate: Long = 0,
) {
    val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    q.insertBook(title, author, category, coverImage, introduction, isbn, sourceUrl, weReadBookId, hasUpdate, now, now)
}
```

Mirror the same optional metadata parameters in `insertAndReturnId` and `update`.

- [ ] **Step 6: Run tests**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.dailysatori.data.repository.BookRepositoryInsertApiTest
```

Expected: pass.

---

### Task 2: Save WeRead Metadata When Adding Books

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookSearchService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookSearchViewModel.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/book/WeReadSkillServiceTest.kt`

- [ ] **Step 1: Write failing parsing test**

Extend the WeRead search parsing test to assert:

```kotlin
assertEquals("3300045871", results.first().sourceBookId)
assertEquals("9787536692930", results.first().isbn)
assertEquals("weread://reading?bId=3300045871", results.first().sourceUrl)
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.dailysatori.service.book.WeReadSkillServiceTest
```

Expected: compilation fails because `sourceBookId` does not exist.

- [ ] **Step 3: Add sourceBookId to result model**

Update `BookSearchResult`:

```kotlin
val sourceBookId: String = "",
```

- [ ] **Step 4: Parse and save sourceBookId**

In `parseWeReadSearchItem`, return:

```kotlin
sourceUrl = "weread://reading?bId=$bookId",
sourceBookId = bookId,
```

In `BookSearchViewModel.addAndAnalyzeBook`, call:

```kotlin
val bookId = bookRepo.insertAndReturnId(
    title = result.title,
    author = result.author,
    category = result.category,
    coverImage = result.coverUrl,
    introduction = result.introduction,
    isbn = result.isbn,
    sourceUrl = result.sourceUrl,
    weReadBookId = result.sourceBookId,
)
```

- [ ] **Step 5: Run tests**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.dailysatori.service.book.WeReadSkillServiceTest
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookSearchUiTextTest
```

Expected: pass.

---

### Task 3: Stabilize Refresh Source Resolution

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksViewModel.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BooksScreenUiTextTest.kt`

- [ ] **Step 1: Write failing source-text test**

Add assertions that `BooksViewModel.refreshBook` uses stored book metadata first:

```kotlin
assertTrue(source.contains("book.weread_book_id"))
assertTrue(source.contains("book.source_url"))
assertTrue(source.contains("sourceBookId = refreshSourceBookId"))
assertTrue(source.contains("isbn = book.isbn"))
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BooksScreenUiTextTest
```

Expected: fails because refresh does not use stored metadata columns yet.

- [ ] **Step 3: Implement refresh source resolution**

In `refreshBook`, compute:

```kotlin
val recoveredSourceUrl = refreshSourceUrlFromViewpoints(bookId)
val refreshSourceBookId = book.weread_book_id.ifBlank {
    recoveredSourceUrl.extractWeReadBookId().orEmpty()
}
val refreshSourceUrl = book.source_url.ifBlank {
    refreshSourceBookId.takeIf { it.isNotBlank() }?.let(::weReadSourceUrlFromBookId).orEmpty()
}
```

Build the result:

```kotlin
val result = BookSearchResult(
    title = book.title,
    author = book.author,
    category = book.category,
    coverUrl = book.cover_image,
    introduction = book.introduction,
    isbn = book.isbn,
    sourceUrl = refreshSourceUrl,
    sourceBookId = refreshSourceBookId,
)
```

If metadata was recovered for an old book, update the book record after generation succeeds:

```kotlin
if (book.source_url.isBlank() || book.weread_book_id.isBlank()) {
    bookRepo.update(
        id = book.id,
        title = book.title,
        author = book.author,
        category = book.category,
        coverImage = book.cover_image,
        introduction = book.introduction,
        isbn = book.isbn,
        sourceUrl = refreshSourceUrl,
        weReadBookId = refreshSourceBookId,
        hasUpdate = book.has_update ?: 0,
    )
}
```

- [ ] **Step 4: Run tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BooksScreenUiTextTest
```

Expected: pass.

---

### Task 4: Enrich AI/MCP Context Without WeRead Book ID

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/book/WeReadSkillServiceTest.kt`

- [ ] **Step 1: Write failing prompt tests**

Add assertions to outline and enrichment prompt tests:

```kotlin
assertTrue(prompt.contains("ISBN"))
assertTrue(prompt.contains("分类"))
assertTrue(prompt.contains("作者"))
assertFalse(prompt.contains("bookId"))
assertFalse(prompt.contains("3300045871"))
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.dailysatori.service.book.WeReadSkillServiceTest
```

Expected: fails until prompts explicitly include ISBN and avoid book id.

- [ ] **Step 3: Update prompts**

In `buildBookViewpointOutlinePrompt` and `buildBookViewpointEnrichmentPrompt`, include:

```kotlin
书名：$title
作者：$author
分类：${book.category.ifBlank { info.category }}
ISBN：${book.isbn.ifBlank { "未知" }}
简介：${info.intro.ifBlank { book.introduction }}
可用目录：${chapters.take(12).joinToString("、") { it.title }}
可用书评：${reviews.take(5).joinToString("\n") { it.content }}
```

Do not include `info.bookId`, `book.sourceBookId`, or `book.sourceUrl` in AI/MCP prompts.

- [ ] **Step 4: Improve MCP search query metadata**

In `completeBookViewpointOutlines`, include ISBN only when nonblank:

```kotlin
searchQuery = listOf(title, author, book.isbn, focus.ifBlank { cleanTitle }, cleanTitle)
    .filter { it.isNotBlank() }
    .joinToString(" ")
```

- [ ] **Step 5: Run tests**

Run:

```bash
./gradlew :shared:test
```

Expected: pass.

---

### Task 5: Add WeRead Shortcut in Reading UI

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BooksScreenUiTextTest.kt`

- [ ] **Step 1: Write failing UI text test**

Add:

```kotlin
assertEquals("打开微信读书", booksOpenWeReadMenuText())
assertEquals("打开微信读书", booksOpenWeReadContentDescription())
assertTrue(source.contains("booksOpenWeReadMenuText()"))
assertTrue(source.contains("Intent.ACTION_VIEW"))
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BooksScreenUiTextTest
```

Expected: fails because the UI action does not exist.

- [ ] **Step 3: Add open action**

In `BooksScreen`, import:

```kotlin
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
```

Create:

```kotlin
val context = LocalContext.current
val currentBookSourceUrl = currentBook?.source_url.orEmpty()
```

Add menu item when source URL exists:

```kotlin
if (currentBookSourceUrl.isNotBlank()) {
    DropdownMenuItem(
        text = { Text(booksOpenWeReadMenuText()) },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
        onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentBookSourceUrl)))
            showMenu = false
        },
    )
}
```

Add helpers:

```kotlin
fun booksOpenWeReadMenuText(): String = "打开微信读书"
fun booksOpenWeReadContentDescription(): String = "打开微信读书"
```

- [ ] **Step 4: Run tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BooksScreenUiTextTest
```

Expected: pass.

---

### Task 6: Final Verification and Install

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run shared tests**

```bash
./gradlew :shared:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run app book tests**

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BooksScreenUiTextTest
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookSearchUiTextTest
```

Expected: both commands end with `BUILD SUCCESSFUL`.

- [ ] **Step 3: Install and open when device is connected**

```bash
adb connect 192.168.2.113:41517
./gradlew :app:installDebug
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: install succeeds and the App opens.

- [ ] **Step 4: Manual acceptance check**

On the device:

1. Open the reading page.
2. Open the top-right menu.
3. Confirm “打开微信读书” appears for WeRead-added books.
4. Tap it and confirm Android opens WeRead or a chooser for the WeRead URL.
5. Refresh a WeRead-added book.
6. Confirm logs show stored metadata path:

```text
BooksRefresh: Book refresh started ... hasStoredWeReadBookId=true
BookAiFallback: AI outline response parsed ...
BooksRefresh: Book refresh finished ...
```

---

## Self-Review

- Spec coverage: covers metadata persistence, refresh source ordering, richer AI/MCP context without WeRead book id, WeRead shortcut, compatibility with legacy books, and device install.
- Placeholder scan: no TODO/TBD placeholders remain.
- Type consistency: metadata names are `isbn`, `source_url`, `weread_book_id` in DB and `isbn`, `sourceUrl`, `sourceBookId` in Kotlin search results.
