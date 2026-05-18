# Staged Project Refactor Design

## Goal

Refactor the Daily Satori Kotlin Multiplatform Android project so the code is easier to read, modules have clearer responsibilities, files are better sized, and duplicated code is reduced while preserving all current functionality and UI behavior.

## Non-Goals

- Do not redesign user-facing UI.
- Do not rewrite the app into a new architecture framework.
- Do not replace Koin, SQLDelight, Compose, or the current KMP module layout.
- Do not change database schema except in the explicit migration-safety stage.
- Do not bundle unrelated feature work into refactor commits.

## Refactor Strategy

Use staged vertical refactoring instead of one large rewrite. Each stage has one primary area, small commits, and a verification gate. The project must remain buildable after every stage so the work can stop or ship safely at any checkpoint.

The stages are ordered from lowest risk and highest cleanup value to higher-risk shared logic changes:

1. Baseline and legacy cleanup.
2. UI foundation component extraction.
3. UI large-screen decomposition.
4. Shared service layer simplification.
5. Data layer consistency and migration safety.
6. Final verification, documentation, and guardrails.

## Stage 1: Baseline And Legacy Cleanup

### Scope

Remove stale Flutter-era project artifacts and align automation with the current KMP Android app.

### Target Files

- `.github/workflows/unit-tests.yml`
- `test.sh`
- `pubspec.yaml`
- `pubspec.lock`
- `analysis_options.yaml`
- `lib/`
- `test/`
- `assets/`
- `.gitignore`
- `README.md`
- `.opencode/skill/release-version/SKILL.md`

### Design

Make `app/build.gradle.kts` the only app version source. Remove or quarantine Flutter metadata only after `git grep` confirms no current Gradle/KMP workflow depends on it. Update CI to run Gradle checks instead of Flutter commands. Remove root assets only if they are confirmed duplicates or Flutter-only; keep runtime assets in `app/src/main/assets` and shared resources in `shared/src/commonMain/resources`.

### Verification

- `git grep -n "pubspec\|flutter analyze\|flutter test\|lib/\|objectbox.g.dart"`
- `./gradlew :app:compileDebugKotlin --no-configuration-cache`
- `./gradlew :app:testDebugUnitTest --no-configuration-cache` if CI/test workflow is changed.

## Stage 2: UI Foundation Component Extraction

### Scope

Extract repeated presentation patterns without changing page structure.

### Target Files

- `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt`
- `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsDetailScreens.kt`
- `app/src/main/kotlin/com/dailysatori/ui/component/card/ArticleCard.kt`
- `app/src/main/kotlin/com/dailysatori/ui/component/media/SmartImage.kt`
- `app/src/main/kotlin/com/dailysatori/ui/component/settings/`
- `app/src/main/kotlin/com/dailysatori/ui/component/content/`

### Design

Create small reusable UI primitives for duplicated code:

- Markdown tab pager used by local and remote article details.
- Article summary card layout and cover placeholder logic.
- Settings form rows and status cards.
- Image debug badges and repeated visual constants using theme tokens or local constants.

Keep feature-specific state and navigation in the existing screens. Shared components should be stateless and receive all content/callbacks as parameters.

### Verification

- `./gradlew :app:compileDebugKotlin --no-configuration-cache`
- Focused UI behavior tests where pure formatting helpers exist.
- Manual smoke: local article detail tabs, remote article detail tabs, article list cards, remote digest article cards.

## Stage 3: UI Large-Screen Decomposition

### Scope

Split oversized Compose files into focused host/content/component files while preserving state ownership.

### Target Files

- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerScreen.kt`
- `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`
- `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/importing/DataImportScreen.kt`
- `app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt`

### Design

Keep each feature's public screen entry point stable. Extract internal composables into files named by responsibility, for example `McpServerListContent`, `McpServerEditForm`, `BooksTopBarActions`, `BooksPagerContent`, `UnifiedNewsDetailHost`, and `UnifiedNewsPageContent`. Navigation helpers may be extracted from `NavHost.kt`, but route names and destination behavior must remain unchanged.

### Verification

- `./gradlew :app:compileDebugKotlin --no-configuration-cache`
- Existing feature-specific unit tests.
- Manual smoke: MCP list/add/edit, books pager/sheets/delete dialog, unified news citation details/back behavior, import ZIP flow.

## Stage 4: Shared Service Layer Simplification

### Scope

Reduce procedural complexity in shared services and isolate pure logic.

### Target Files

- `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt`
- `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`
- `shared/src/commonMain/kotlin/com/dailysatori/service/import/ImportService.kt`
- `shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteNewsService.kt`
- `shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/CrayfishNewsService.kt`
- `shared/src/commonMain/kotlin/com/dailysatori/service/ai/AiService.kt`
- `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/RemoteMcpClient.kt`

### Design

Prefer private helpers and small collaborators over broad abstractions. Extract repeated HTTP URL/auth/status helpers, prompt-input formatting helpers, import entity processors, and article parser persistence helpers. Keep public APIs stable unless tests prove a narrower API is safe.

### Verification

- `./gradlew :app:compileDebugKotlin --no-configuration-cache`
- Focused tests for pure helpers such as URL builders, citation validation, markdown/image normalization, import ID remapping, and AI endpoint selection.

## Stage 5: Data Layer Consistency And Migration Safety

### Scope

Standardize repositories and make migrations safer without changing schema by default.

### Target Files

- `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`
- `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- `shared/src/commonMain/kotlin/com/dailysatori/data/repository/`

### Design

Standardize timestamp helpers, Long-backed Boolean conversion, Flow/Sync method naming, and SQL count/search queries. Treat `DatabaseMigration.kt` as a separate high-risk sub-stage: add tests or reproducible seed verification before changing migration semantics. Do not bump schema version unless schema changes are explicitly required.

### Verification

- `./gradlew :app:compileDebugKotlin --no-configuration-cache`
- Migration regression tests or manual seeded database migration checks.
- Repository parity tests for count/search changes.

## Stage 6: Final Verification And Guardrails

### Scope

Make the new structure discoverable and prevent regression into unclear boundaries.

### Target Files

- `docs/01-coding-standards.md`
- `docs/02-testing.md`
- `README.md`
- Feature package README files only if needed.

### Design

Document where new code should go: feature screen entry points, reusable UI components, shared services, repositories, migrations, and tests. Update stale Flutter references. Add a short refactor checklist based on the actual patterns created in prior stages.

### Verification

- `./gradlew :app:compileDebugKotlin --no-configuration-cache`
- `git grep -n "Flutter\|flutter\|pubspec\|Riverpod\|go_router"` to ensure remaining references are intentional historical docs or removed.

## Cross-Stage Rules

- Start each stage from a clean worktree.
- Use an isolated branch/worktree for implementation.
- Keep each commit behavior-preserving and focused.
- Run compile after every stage.
- Run targeted tests before and after behavior-sensitive refactors.
- Do not combine database migration work with UI or CI cleanup.
- If a refactor requires changing behavior, stop and ask before proceeding.

## Acceptance Criteria

- All current user-visible functions and UI flows remain unchanged.
- The active project no longer has confusing Flutter release/build metadata.
- Large UI screens are split into focused files with stable public entry points.
- Shared services have smaller, named helpers for repeated logic.
- Repository patterns and migration handling are more consistent and easier to test.
- Documentation reflects the current KMP Android project structure.
