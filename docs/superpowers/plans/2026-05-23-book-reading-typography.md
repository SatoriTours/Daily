# Book Reading Typography Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Increase book viewpoint card typography so the book page reads closer to the diary page without changing global card styles.

**Architecture:** Add a book-specific Markdown typography preset to `MarkdownStyles`, then update `ViewpointCard` to use it and larger theme typography tokens for title, metadata, and example label. Guard the behavior with a source-level UI typography contract test.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 typography, Markdown renderer, Gradle unit tests.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt`: add `bookTypography()` based on `bodyLarge`.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt`: switch viewpoint Markdown to `bookTypography()` and use larger theme typography tokens.
- Modify `app/src/test/kotlin/com/dailysatori/ui/theme/MainContentRhythmTest.kt`: add a contract test for book-specific typography.

### Task 1: Book Viewpoint Typography

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/theme/MainContentRhythmTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt`

- [ ] **Step 1: Write the failing test**

Add this test to `MainContentRhythmTest`:

```kotlin
@Test
fun bookViewpointUsesLargerReadingTypographyThanSharedCards() {
    val styles = File("src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt").readText()
    val viewpoint = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()

    assertTrue(styles.contains("fun bookTypography(): MarkdownTypography = typographyFrom("))
    assertTrue(styles.contains("body = bookTextStyle()"))
    assertTrue(styles.contains("private fun bookTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy"))
    assertEquals(2, countOccurrences(viewpoint, "typography = MarkdownStyles.bookTypography()"))
    assertTrue(viewpoint.contains("style = MaterialTheme.typography.titleLarge"))
    assertTrue(viewpoint.contains("style = MaterialTheme.typography.labelMedium"))
    assertTrue(viewpoint.contains("style = MaterialTheme.typography.titleMedium"))
}
```

- [ ] **Step 2: Run the failing test**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.MainContentRhythmTest.bookViewpointUsesLargerReadingTypographyThanSharedCards`

Expected: FAIL because `bookTypography()` is not defined and `ViewpointCard` still uses `cardTypography()`.

- [ ] **Step 3: Add book Markdown typography**

In `MarkdownStyles.kt`, add this function after `cardTypography()`:

```kotlin
@Composable
fun bookTypography(): MarkdownTypography = typographyFrom(
    body = bookTextStyle(),
    h1 = MaterialTheme.typography.headlineSmall,
    h2 = MaterialTheme.typography.titleLarge,
    h3 = MaterialTheme.typography.titleMedium,
    linkColor = MaterialTheme.colorScheme.primary,
)
```

Add this private helper after `cardTextStyle()`:

```kotlin
@Composable
private fun bookTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = UiFontFamily)
```

- [ ] **Step 4: Update ViewpointCard**

In `ViewpointCard.kt`:

```kotlin
style = MaterialTheme.typography.titleLarge
```

Use for the main title.

```kotlin
style = MaterialTheme.typography.labelMedium
```

Use for the book metadata.

```kotlin
typography = MarkdownStyles.bookTypography()
```

Use for both content and example Markdown blocks.

```kotlin
style = MaterialTheme.typography.titleMedium
```

Use for the `案例` label.

- [ ] **Step 5: Run focused test**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.MainContentRhythmTest.bookViewpointUsesLargerReadingTypographyThanSharedCards`

Expected: PASS.

- [ ] **Step 6: Run required verification**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.MainContentRhythmTest`

Expected: PASS.

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Expected: BUILD SUCCESSFUL and install succeeds.

Run: `adb -s ba5e2328 shell am start -n com.dailysatori/.MainActivity` and `adb -s 192.168.2.9:5555 shell am start -n com.dailysatori/.MainActivity` if both devices remain connected.

Expected: Activity starts or receives the intent on each connected device.

## Self-Review

- Spec coverage: The single task covers book-only Markdown typography, title/metadata/example token changes, and verification.
- Deferred marker scan: no issues found.
- Type consistency: `bookTypography()` and `bookTextStyle()` are defined before use by `ViewpointCard`.
