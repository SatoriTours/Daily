# Riverpod 最佳实践指南

> 本文档提供 Riverpod 3.0 + freezed 的最佳实践和代码规范。
>
> 所有代码必须遵循本指南以确保一致性和可维护性。

## 📋 目录

- [Provider 组织规范](#provider-组织规范)
- [状态建模最佳实践](#状态建模最佳实践)
- [freezed 使用规范](#freezed-使用规范)
- [Provider 类型选择](#provider-类型选择)
- [性能优化模式](#性能优化模式)
- [测试最佳实践](#测试最佳实践)
- [常见模式与反模式](#常见模式与反模式)

---

## Provider 组织规范

### 文件结构

```
lib/app/providers/
├── providers.dart              # 导出所有 providers (barrel file)
├── article_state_provider.dart  # 文章状态服务
├── diary_state_provider.dart    # 日记状态服务
├── books_state_provider.dart    # 书籍状态服务
├── app_state_provider.dart      # 全局应用状态
└── pages/                       # 页面级 providers
    ├── articles/
    │   ├── articles_controller_provider.dart
    │   └── article_detail_controller_provider.dart
    └── diary/
        └── diary_controller_provider.dart
```

### 命名规范

```dart
// ✅ Provider 命名: 小写下划线 + Provider 后缀
@riverpod
class ArticleState extends _$ArticleState { }

// 生成的 provider 名称: articleStateProvider

// ✅ State 类命名: 与 Provider 相同
@freezed
class ArticleStateModel with _$ArticleStateModel { }

// ✅ Notifier 命名: 与 Provider 相同
@riverpod
class ArticlesController extends _$ArticlesController { }

// ❌ 禁止: 不一致的命名
@riverpod
class articleState extends _$articleState { } // 错误: 小写开头
```

### Provider 导出

```dart
// lib/app/providers/providers.dart
// 导出所有 providers，方便统一导入

// State providers (状态服务)
export 'article_state_provider.dart';
export 'diary_state_provider.dart';
export 'books_state_provider.dart';
export 'app_state_provider.dart';

// Controller providers (页面级)
export 'pages/articles/articles_controller_provider.dart';
export 'pages/diary/diary_controller_provider.dart';

// 使用时
import 'package:daily_satori/app/providers/providers.dart';
```

---

## 状态建模最佳实践

### 不可变状态原则

```dart
// ✅ 正确: 使用 freezed 定义不可变状态
@freezed
class ArticleStateModel with _$ArticleStateModel {
  const factory ArticleStateModel({
    required List<ArticleModel> articles,
    @Default(false) bool isLoading,
    String? errorMessage,
  }) = _ArticleStateModel;
}

// ❌ 错误: 可变状态
class ArticleStateModel {
  List<ArticleModel> articles = [];
  bool isLoading = false;
}
```

### 状态粒度

```dart
// ✅ 推荐: 细粒度状态 (单一职责)
@riverpod
class ArticleListState extends _$ArticleListState {
  @override
  Future<List<ArticleModel>> build() async => [];
}

@riverpod
class ArticleFiltersState extends _$ArticleFiltersState {
  @override
  ArticleFilterModel build() => ArticleFilterModel.initial();
}

// ❌ 避免: 过于庞大的状态
@riverpod
class EverythingState extends _$EverythingState {
  @override
  EverythingModel build() {
    return EverythingModel(
      articles: [],
      diaries: [],
      books: [],
      settings: null,
      // ... 太多不相关的状态
    );
  }
}
```

### 异步状态处理

```dart
// ✅ 使用 AsyncValue 包装异步结果
@riverpod
class ArticleState extends _$ArticleState {
  @override
  Future<List<ArticleModel>> build() async {
    return [];
  }

  Future<void> loadArticles() async {
    state = const AsyncValue.loading();
    state = await AsyncValue.guard(() async {
      return ArticleRepository.i.findArticles();
    });
  }
}

// ✅ 在 UI 中处理 AsyncValue
class ArticlesView extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final articlesAsync = ref.watch(articleStateProvider);

    return articlesAsync.when(
      data: (articles) => ArticlesList(articles: articles),
      loading: () => CircularProgressIndicator(),
      error: (e, s) => ErrorWidget(e),
    );
  }
}
```

---

## freezed 使用规范

### 基础用法

```dart
// ✅ 使用 @freezed 注解
import 'package:freezed_annotation/freezed_annotation.dart';

part 'article_state_model.freezed.dart';

@freezed
class ArticleStateModel with _$ArticleStateModel {
  const factory ArticleStateModel({
    required List<ArticleModel> articles,
    @Default(false) bool isLoading,
    String? errorMessage,
  }) = _ArticleStateModel;
}
```

### copyWith 模式

```dart
// ✅ 使用 copyWith 更新状态
@riverpod
class ArticleFilters extends _$ArticleFilters {
  @override
  ArticleFilterModel build() => ArticleFilterModel.initial();

  void setOnlyFavorite(bool value) {
    state = state.copyWith(onlyFavorite: value);
  }

  void setTagId(int id) {
    state = state.copyWith(tagId: id);
  }

  void clearAll() {
    state = ArticleFilterModel.initial();
  }
}

// freezed 自动生成 copyWith 方法
@freezed
class ArticleFilterModel with _$ArticleFilterModel {
  const factory ArticleFilterModel({
    @Default(false) bool onlyFavorite,
    @Default(-1) int tagId,
    DateTime? startDate,
    DateTime? endDate,
  }) = _ArticleFilterModel;
}
```

### 联合类型 (Union Types)

```dart
// ✅ 使用 freezed 联合类型表示状态机
@freezed
class ArticleLoadState with _$ArticleLoadState {
  const factory ArticleLoadState.idle() = Idle;
  const factory ArticleLoadState.loading() = Loading;
  const factory ArticleLoadState.data(List<ArticleModel> articles) = Data;
  const factory ArticleLoadState.error(String message) = Error;
}

// 在 UI 中使用模式匹配
state.when(
  idle: () => Text('空闲状态'),
  loading: () => CircularProgressIndicator(),
  data: (articles) => ArticlesList(articles: articles),
  error: (msg) => Text('错误: $msg'),
);
```

---

## Provider 类型选择

### 决策树

```
需要管理状态？
├─ 是 → 需要修改状态？
│   ├─ 是 → 使用 @riverpod class extends _$[Name]
│   │       ├─ 简单状态 → StateNotifier
│   │       └─ 异步状态 → AsyncNotifier
│   └─ 否 → 使用 Provider (只读计算值)
└─ 否 → 直接使用普通 Dart 类
```

### 常用 Provider 类型

```dart
// 1. StateProvider - 简单状态
@riverpod
class SearchQuery extends _$SearchQuery {
  @override
  String build() => '';

  void updateQuery(String query) => state = query;
}

// 2. FutureProvider - 只读异步数据
@riverpod
Future<List<ArticleModel>> fetchArticles() async {
  return ArticleRepository.i.findArticles();
}

// 3. AsyncNotifier - 异步状态管理
@riverpod
class ArticleState extends _$ArticleState {
  @override
  Future<List<ArticleModel>> build() async {
    return [];
  }

  Future<void> refresh() async {
    state = const AsyncValue.loading();
    state = await AsyncValue.guard(() => fetchArticles());
  }
}

// 4. Notifier - 同步状态管理
@riverpod
class ThemeMode extends _$ThemeMode {
  @override
  ThemeModeData build() => ThemeModeData.system();

  void setLight() => state = ThemeModeData.light();
  void setDark() => state = ThemeModeData.dark();
}
```

---

## Widget 实现指南

### Widget 类型选择

| Widget 类型 | 适用场景 | 示例 |
|------------|----------|------|
| **StatelessWidget** | 纯展示组件，不依赖 Provider 状态 | `MyCard`, `MyButton` |
| **ConsumerWidget** | 需要监听 Provider 状态的页面或组件 | `ArticleList`, `UserProfile` |
| **ConsumerStatefulWidget** | 需要 Provider 状态 + 本地状态 (TabController, ScrollController) | `MainPage`, `VideoPlayer` |

### 代码示例

#### 1. ConsumerWidget (推荐)

```dart
class ArticleList extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final articles = ref.watch(articlesProvider);
    // ...
  }
}
```

#### 2. ConsumerStatefulWidget (需要生命周期)

```dart
class SearchPage extends ConsumerStatefulWidget {
  @override
  ConsumerState<SearchPage> createState() => _SearchPageState();
}

class _SearchPageState extends ConsumerState<SearchPage> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController();
  }

  @override
  Widget build(BuildContext context) {
    final results = ref.watch(searchResultsProvider);
    // ...
  }
}
```

---

## 性能优化模式

### select() 精确订阅

```dart
// ✅ 使用 select() 订阅状态的部分字段
class ArticlesView extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // 只在 articles 列表变化时重建，忽略 isLoading 变化
    final articles = ref.watch(
      articleStateProvider.select((state) => state.value ?? []),
    );

    return ListView.builder(
      itemCount: articles.length,
      itemBuilder: (context, index) => ArticleCard(articles[index]),
    );
  }
}

// ❌ 避免: 订阅整个状态导致不必要的重建
class ArticlesView extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(articleStateProvider); // 任何字段变化都会重建

    return Text('${state.isLoading}'); // 只需要 isLoading
  }
}
```

### keepAlive() 优化

```dart
// ✅ 使用 keepAlive() 保持状态
@riverpod
class CachedData extends _$CachedData {
  @override
  Future<List<ArticleModel>> build() async {
    ref.keepAlive(); // 即使没有监听者，也保持状态
    return ArticleRepository.i.findArticles();
  }
}
```

### 避免过度重建

```dart
// ✅ 将复杂 Widget 拆分为 ConsumerWidget
class ArticleCard extends ConsumerWidget {
  final ArticleModel article;

  const ArticleCard({required this.article});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isFavorite = ref.watch(
      articleStateProvider.select((state) => state.value?.firstWhere(
        (a) => a.id == article.id,
        orElse: () => article,
      ).isFavorite ?? false),
    );

    return Card(
      child: ListTile(
        title: Text(article.title),
        trailing: IconButton(
          icon: Icon(isFavorite ? Icons.favorite : Icons.favorite_border),
          onPressed: () => ref.read(articleStateProvider.notifier).toggleFavorite(article.id),
        ),
      ),
    );
  }
}
```

---

## 测试最佳实践

### Provider 测试

```dart
// 单元测试
void main() {
  test('loadArticles returns articles', () async {
    // 创建 ProviderContainer
    final container = ProviderContainer();

    // 添加 mock
    container.read(articleStateProvider.notifier).loadArticles();

    // 验证状态
    final state = container.read(articleStateProvider);
    expect(state.value, isNotEmpty);

    // 清理
    container.dispose();
  });
}
```

### Widget 测试

```dart
// 集成测试
testWidgets('should display articles', (tester) async {
  // 创建 container 并添加 mocks
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

  await tester.pumpAndSettle();

  expect(find.text('Articles'), findsOneWidget);

  container.dispose();
});
```

### Mock Providers

```dart
// 使用 ProviderContainer.override
test('should handle error gracefully', () async {
  final container = ProviderContainer(
    overrides: [
      articleStateProvider.overrideWith(
        (ref) => MockArticleState()..throwsError = true,
      ),
    ],
  );

  final state = container.read(articleStateProvider);
  expect(state.hasError, true);

  container.dispose();
});
```

---

## 常见模式与反模式

### ✅ 正确模式

#### 1. Provider 组合

```dart
// ✅ Provider 可以安全地依赖其他 Provider
@riverpod
class FilteredArticles extends _$FilteredArticles {
  @override
  List<ArticleModel> build() {
    // 依赖其他 provider
    final articlesAsync = ref.watch(articleStateProvider);
    final filters = ref.watch(articleFiltersProvider);

    return articlesAsync.value?.where((article) {
      if (filters.onlyFavorite && !article.isFavorite) return false;
      if (filters.tagId != -1 && !article.tagIds.contains(filters.tagId)) return false;
      return true;
    }).toList() ?? [];
  }
}
```

#### 2. 副作用监听

```dart
// ✅ 使用 ref.listen() 处理副作用
class ArticlesView extends ConsumerStatefulWidget {
  @override
  ConsumerState<ArticlesView> createState() => _ArticlesViewState();
}

class _ArticlesViewState extends ConsumerState<ArticlesView> {
  @override
  void initState() {
    super.initState();

    // 监听错误状态并显示 SnackBar
    ref.listen(articleStateProvider, (previous, next) {
      if (next.hasError) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('加载失败: ${next.error}')),
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    // ...
  }
}
```

#### 3. 生命周期管理

```dart
// ✅ 在 build 中初始化资源
@riverpod
class WebSocketConnection extends _$WebSocketConnection {
  @override
  Stream<Message> build() {
    // 连接 WebSocket
    final socket = WebSocket.connect('ws://example.com');

    // 在 provider 销毁时关闭连接
    ref.onDispose(() {
      socket.close();
    });

    return socket;
  }
}
```

### ❌ 常见错误

#### 1. 在 build 外使用 ref.watch()

```dart
// ❌ 错误: 在事件处理中使用 watch
@riverpod
class MyController extends _$MyController {
  @override
  MyState build() => MyState();

  void onButtonPressed() {
    final value = ref.watch(otherProvider); // 错误! 应该用 ref.read()
  }
}

// ✅ 正确
void onButtonPressed() {
  final value = ref.read(otherProvider); // 一次性读取
}
```

#### 2. 循环依赖

```dart
// ❌ 错误: A 依赖 B，B 又依赖 A
@riverpod
class ProviderA extends _$ProviderA {
  @override
  int build() {
    final b = ref.watch(providerBProvider); // 依赖 B
    return b * 2;
  }
}

@riverpod
class ProviderB extends _$ProviderB {
  @override
  int build() {
    final a = ref.watch(providerAProvider); // 依赖 A - 循环!
    return a + 1;
  }
}
```

#### 3. 忘记调用 build()

```dart
// ❌ 错误: 忘记调用 super.build()
@riverpod
class MyController extends _$MyController {
  @override
  MyState build() {
    // 忘记 return
  }
}

// ✅ 正确
@riverpod
class MyController extends _$MyController {
  @override
  MyState build() {
    // 初始化逻辑
    return MyState();
  }
}
```

#### 4. 可变状态

```dart
// ❌ 错误: 直接修改状态
@freezed
class MyState with _$MyState {
  factory MyState(List<int> items) = _MyState;
}

// 在 provider 中
state.items.add(1); // 编译错误 - freezed 是不可变的

// ✅ 正确
state = state.copyWith(items: [...state.items, 1]);
```

---

## 代码生成

### 执行代码生成

```bash
# 生成所有 providers
flutter pub run build_runner build

# 删除旧的生成文件后重新生成
flutter pub run build_runner build --delete-conflicting-outputs

# 监听文件变化自动生成
flutter pub run build_runner watch
```

### .g.dart 文件

```dart
// .g.dart 文件由代码生成，不要手动编辑

// article_state_provider.g.dart ( GENERATED CODE - DO NOT MODIFY)
part of 'article_state_provider.dart';

String _$articleStateProviderHash() => '...';

@ProviderFor(ArticleState)
final articleStateProvider = AutoDisposeFutureProvider<ArticleState>.internal(...);

typedef ArticleStateRef = AutoDisposeFutureProviderRef<ArticleState>;
```

---

## 调试技巧

### Provider 日志

```dart
// ✅ 添加日志监听
@riverpod
class ArticleState extends _$ArticleState {
  @override
  Future<List<ArticleModel>> build() async {
    ref.onAddListener(() {
      print('ArticleState: 新增监听者');
    });

    ref.onDispose(() {
      print('ArticleState: Provider 被销毁');
    });

    return [];
  }
}
```

### Provider Inspector

在 Flutter DevTools 中使用 Riverpod Inspector 查看:
- 所有 providers 的当前状态
- Provider 依赖关系图
- 监听者数量
- 状态变化历史

---

## 相关资源

- [Riverpod 官方文档](https://riverpod.dev)
- [freezed 文档](https://pub.dev/packages/freezed)
- [Riverpod 3.0 迁移指南](https://riverpod.dev/docs/3.0_migration)
- [项目迁移文档](./RIVERPOD_MIGRATION.md)

---

*最后更新: 2025-12-28*
