# GetX to Riverpod Migration Guide

## Overview

This document tracks the migration of the Daily Satori project from GetX framework to Riverpod 3.0 framework.

**Migration Strategy**: Incremental coexistence - GetX and Riverpod will coexist during the transition period.

**Timeline**: 7-10 weeks across 5 phases

**Start Date**: [To be filled]

**Current Phase**: Phase 0 - Documentation Updates

---

## Progress Tracking

### ✅ Completed
- [x] Migration plan approved
- [x] Documentation phase started

### 🔄 In Progress
- [ ] Phase 0: Documentation Updates (Week 1)
  - [x] Migration guide created
  - [ ] Update coding standards
  - [ ] Create Riverpod style guide
  - [ ] Update CLAUDE.md
  - [ ] Update testing documentation

### ⏳ Pending
- [ ] Phase 1: Foundation & Setup (Week 2-3)
- [ ] Phase 2: State Service Migration (Week 4-5)
- [ ] Phase 3: Controller Migration (Week 6-8)
- [ ] Phase 4: Navigation Migration (Week 9-10)
- [ ] Phase 5: Cleanup & Testing (Week 11-12)

---

## Phase Overview

### Phase 0: Documentation Updates (1 week)
**Objective**: Update all documentation to reflect Riverpod patterns BEFORE any code changes.

**Deliverables**:
- `docs/RIVERPOD_MIGRATION.md` - This file
- `docs/01-coding-standards.md` - Updated with Riverpod patterns
- `docs/06-riverpod-style-guide.md` - NEW: Riverpod best practices
- `CLAUDE.md` - Updated architecture and constraints
- `integration_test/TEST_README.md` - Updated testing patterns

**Acceptance Criteria**:
- ✅ All documentation updated and reviewed
- ✅ No GetX patterns in documentation
- ✅ Code examples use Riverpod + freezed
- ✅ Team trained on new patterns

### Phase 1: Foundation & Setup (1-2 weeks)
**Objective**: Create Riverpod infrastructure with freezed for immutable state.

**Key Changes**:
- Add Riverpod dependencies to `pubspec.yaml`
- Create `lib/app/providers/` directory structure
- Create base controller patterns
- Wrap app with `ProviderScope`

**Deliverables**:
- `pubspec.yaml` - Updated dependencies
- `lib/app/providers/providers.dart` - Provider barrel file
- `lib/app/utils/base_riverpod_controller.dart` - Base controller mixin
- `lib/init_app.dart` - ProviderScope wrapper

**Acceptance Criteria**:
- ✅ Dependencies added successfully
- ✅ `flutter pub run build_runner build` generates code
- ✅ App still runs with GetX (coexistence)
- ✅ ProviderScope added

### Phase 2: State Service Migration (2 weeks)
**Objective**: Migrate StateServices to Riverpod providers.

**Files to Create**:
- `lib/app/providers/article_state_provider.dart`
- `lib/app/providers/diary_state_provider.dart`
- `lib/app/providers/books_state_provider.dart`
- `lib/app/providers/app_state_provider.dart`

**Acceptance Criteria**:
- ✅ All 4 state services have provider equivalents
- ✅ Existing GetX services still work
- ✅ Code generation successful
- ✅ Integration tests pass

### Phase 3: Controller Migration (2-3 weeks)
**Objective**: Migrate controllers from GetX to Riverpod one by one.

**Migration Order**:
1. settings_controller.dart (Simplest)
2. left_bar_controller.dart
3. home_controller.dart
4. share_dialog_controller.dart
5. backup_restore_controller.dart
6. plugin_center_controller.dart
7. ai_config_edit_controller.dart
8. chat_controller.dart
9. diary_controller.dart
10. diary_edit_controller.dart
11. books_controller.dart
12. book_viewpoint_controller.dart
13. articles_controller.dart (Most complex)
14. article_detail_controller.dart

**Acceptance Criteria**:
- ✅ All 14 controllers migrated
- ✅ All views use ConsumerWidget
- ✅ All bindings deleted
- ✅ Tests pass

### Phase 4: Navigation Migration (1-2 weeks)
**Objective**: Replace GetX navigation with go_router.

**Deliverables**:
- `lib/app/routes/router.dart` - go_router configuration
- `lib/app/routes/app_pages.dart` - Replaced

**Acceptance Criteria**:
- ✅ All routes use go_router
- ✅ Deep linking works
- ✅ Navigation tests pass

### Phase 5: Cleanup & Testing (1-2 weeks)
**Objective**: Remove all GetX traces and ensure all tests pass.

**Tasks**:
- Remove GetX from `pubspec.yaml`
- Delete all binding files
- Delete `base_controller.dart`
- Update integration tests
- Run full test suite

**Acceptance Criteria**:
- ✅ Zero GetX imports
- ✅ All integration tests pass
- ✅ `flutter analyze` clean
- ✅ Build successful

---

## Common Migration Patterns

### Pattern 1: State Service Migration

**Before (GetX)**:
```dart
class ArticleStateService extends GetxService {
  final RxList<ArticleModel> articles = <ArticleModel>[].obs;
  final RxBool isLoading = false.obs;

  Future<void> loadArticles() async {
    isLoading.value = true;
    final result = await ArticleRepository.i.findArticles();
    articles.assignAll(result);
    isLoading.value = false;
  }
}
```

**After (Riverpod + freezed)**:
```dart
@riverpod
class ArticleState extends _$ArticleState {
  @override
  Future<List<ArticleModel>> build() async {
    return [];
  }

  Future<void> loadArticles({
    String? keyword,
    bool? favorite,
  }) async {
    state = const AsyncValue.loading();
    state = await AsyncValue.guard(() async {
      return ArticleRepository.i.findArticles(
        keyword: keyword,
        isFavorite: favorite,
      );
    });
  }

  void addArticle(ArticleModel article) {
    final current = state.value ?? [];
    state = AsyncValue.data([...current, article]);
  }
}
```

### Pattern 2: Controller Migration

**Before (GetX)**:
```dart
class ArticlesController extends BaseController {
  final onlyFavorite = false.obs;
  final tagId = (-1).obs;

  RxList<ArticleModel> get articles => _articleStateService.articles;

  Future<void> toggleFavorite(int id) async {
    await ArticleRepository.i.toggleFavorite(id);
    _articleStateService.updateArticleInList(id);
  }
}
```

**After (Riverpod + freezed)**:
```dart
@riverpod
class ArticlesController extends _$ArticlesController {
  @override
  ArticlesControllerState build() {
    return ArticlesControllerState(
      onlyFavorite: false,
      tagId: -1,
    );
  }

  List<ArticleModel> get articles {
    final asyncValue = ref.watch(articleStateProvider);
    return asyncValue.value ?? [];
  }

  Future<void> toggleFavorite(int id) async {
    await ArticleRepository.i.toggleFavorite(id);
    ref.read(articleStateProvider.notifier).updateArticle(id);
  }
}

@freezed
class ArticlesControllerState with _$ArticlesControllerState {
  const factory ArticlesControllerState({
    @Default(false) bool onlyFavorite,
    @Default(-1) int tagId,
  }) = _ArticlesControllerState;
}
```

### Pattern 3: View Migration

**Before (GetX)**:
```dart
class ArticlesView extends GetView<ArticlesController> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Obx(() {
        final isLoading = controller.isLoadingArticles.value;
        final articles = controller.articles;

        if (isLoading) {
          return CircularProgressIndicator();
        }

        return ListView.builder(
          itemCount: articles.length,
          itemBuilder: (context, index) => ArticleCard(articles[index]),
        );
      }),
    );
  }
}
```

**After (Riverpod)**:
```dart
class ArticlesView extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final articlesAsync = ref.watch(articleStateProvider);

    return Scaffold(
      body: articlesAsync.when(
        data: (articles) => ListView.builder(
          itemCount: articles.length,
          itemBuilder: (context, index) => ArticleCard(articles[index]),
        ),
        loading: () => CircularProgressIndicator(),
        error: (err, s) => ErrorWidget(err),
      ),
    );
  }
}
```

### Pattern 4: Testing Migration

**Before (GetX)**:
```dart
testWidgets('should display articles', (tester) async {
  await tester.pumpWidget(GetMaterialApp(home: ArticlesView()));
  await tester.pumpAndSettle();

  expect(find.text('Articles'), findsOneWidget);
});
```

**After (Riverpod)**:
```dart
testWidgets('should display articles', (tester) async {
  final container = ProviderContainer(
    overrides: [
      articleStateProvider.overrideWith((ref) => MockArticleState()),
    ],
  );

  await tester.pumpWidget(
    UncontrolledProviderScope(
      container: container,
      child: const MaterialApp(home: ArticlesView()),
    ),
  );

  expect(find.text('Articles'), findsOneWidget);

  container.dispose();
});
```

---

## Key Dependencies

### Phase 0-1 (Foundation)
```yaml
dependencies:
  get: ^4.6.6  # Keep for coexistence
  flutter_riverpod: ^3.0.0
  riverpod_annotation: ^3.0.0
  freezed_annotation: ^2.4.0

dev_dependencies:
  build_runner: ^2.4.15
  riverpod_generator: ^3.0.0
  riverpod_lint: ^3.0.0
  freezed: ^2.4.0
```

### Phase 5 (Final)
```yaml
dependencies:
  flutter_riverpod: ^3.0.0
  riverpod_annotation: ^3.0.0
  go_router: ^14.0.0
  freezed_annotation: ^2.4.0

dev_dependencies:
  build_runner: ^2.4.15
  riverpod_generator: ^3.0.0
  riverpod_lint: ^3.0.0
  freezed: ^2.4.0
```

---

## Success Criteria

### Functional Requirements
- ✅ All existing features work identically
- ✅ Zero functional regressions
- ✅ All integration tests pass
- ✅ Deep linking works

### Quality Requirements
- ✅ No GetX imports remaining
- ✅ `flutter analyze` shows zero issues
- ✅ Test coverage ≥ 80%
- ✅ No memory leaks

### Documentation Requirements
- ✅ All docs updated
- ✅ No GetX patterns in docs
- ✅ Team trained on Riverpod patterns

---

## Risk Mitigation

### Rollback Strategy
- Each phase is git-committed separately
- GetX and Riverpod coexist during migration (Phases 1-3)
- Can rollback to any phase by reverting commits

### Testing Strategy
- Run integration tests after each phase
- Manual testing of migrated modules
- Performance profiling
- Memory leak checks

---

## Resources

### Official Documentation
- [Riverpod 3.0 Migration Guide](https://riverpod.dev/docs/3.0_migration)
- [About Code Generation](https://riverpod.dev/docs/concepts/about_code_generation)
- [freezed Documentation](https://pub.dev/packages/freezed)

### Team Training
- Review this migration guide
- Study Riverpod fundamentals
- Practice with small examples
- Code review sessions for each phase

---

## Notes

- **Update this file** as migration progresses
- Track completion dates for each phase
- Document any issues encountered and solutions
- Share learnings with the team

---

*Last Updated: 2025-12-28*
