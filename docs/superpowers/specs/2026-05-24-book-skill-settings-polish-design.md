# Book, Skill Settings, and Back Navigation Polish Design

## Scope

This change fixes five issues on `main` without introducing a new navigation model or broader agent runtime work.

## Book Picker Sheet

The book picker bottom sheet currently renders all books in a plain `Column`. On small screens or long lists, the final row can be clipped by the sheet bottom area. Replace the list body with a bounded `LazyColumn` and add bottom content padding that accounts for visual spacing and system navigation. The sheet title and drag handle remain unchanged.

Success criteria:
- The last book row is fully scrollable into view.
- Existing select and delete behavior remains unchanged.

## Add Book Sheet

The add-book search result card should prioritize book information, not the action buttons. Keep the existing bottom sheet and search flow, but make each result card more editorial:
- Cover, title, and author stay at the top.
- Introduction becomes the main body and shows more text than today.
- The external WeRead action becomes a secondary action.
- The add action remains the primary action but uses cleaner spacing and hierarchy.

If introduction is blank, the card should not show an empty description area.

## Book Viewpoint Import Count

Adding a book should import up to 20 valid viewpoints. If fewer than 20 real usable viewpoints are available, save the available count and do not force AI-generated padding just to reach 20.

Success criteria:
- At most 20 viewpoints are created per added book.
- Existing sparse-material fallback/error behavior remains intact.

## Skill Test Button

The Skill edit screen needs a test button for the selected or edited Skill.

Phase 1 behavior:
- The button is available on the edit form for built-in and custom Skills.
- It tests the current form values rather than requiring a save first.
- It shows testing, success, and failure states in the form.
- For WeRead, the test should verify that a token/gateway can make a lightweight WeRead request if the existing service layer supports it; otherwise it should validate required fields and report a clear failure.
- For custom Skills, the test should validate required fields and gateway reachability/configuration if no generic execution path exists yet.

The test action must not persist form changes.

## Settings Back Navigation

When Settings is opened from the unified news tab, Android system back should return to the news summary page instead of exiting to the launcher. Settings subpages should still return to the Settings main page first.

Success criteria:
- Back from `SettingsPage.MAIN` switches `UnifiedNewsPage.SETTINGS` back to `UnifiedNewsPage.SUMMARY`.
- Back from Settings subpages continues to return to Settings main.

## Testing

Add or update focused tests for:
- Book picker/add-sheet text and layout helper behavior where practical.
- Maximum viewpoint import count.
- Skill test state transitions and labels.
- Unified news Settings back callback wiring.

Run:
- `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.book.*"`
- `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.book.*" --tests "com.dailysatori.ui.feature.settings.skills.*" --tests "com.dailysatori.UnifiedNewsBehaviorTest"`
- `./gradlew :app:compileDebugKotlin`
- `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`
- `adb -s ba5e2328 shell am start -n com.dailysatori/.MainActivity`
