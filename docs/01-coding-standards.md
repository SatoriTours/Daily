# 📋 Daily Satori 编码规范

> **本文档是项目的统一编码规范，被 CLAUDE.md 和 copilot-instructions.md 共同引用。**
>
> 所有 AI 工具（Claude、GitHub Copilot 等）生成的代码必须遵循本规范。

## 📚 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | Flutter 3.32.x / Dart 3.8.x |
| 状态管理 | Riverpod 3.0 + freezed (代码生成) |
| 本地存储 | ObjectBox (仓储模式) |
| 网络 | dio, web_socket_channel |
| WebView | flutter_inappwebview |
| AI | openai_dart + 配置文件 (assets/configs/) |
| 导航 | go_router 14.x |

## 🏗️ 项目架构

### 分层原则

| 层级 | 路径 | 职责 |
|------|------|------|
| 界面层 | `app/pages/*/views` | 界面展示与用户交互 |
| 控制层 | `app/providers/*` | Riverpod Providers，状态管理 |
| 服务层 | `app/services/*` | 跨模块服务 |
| 数据层 | `app/data/*` | 数据模型与仓储（按实体分组） |

### 目录结构

```
lib/app/
├── pages/            # 功能页面(views → ConsumerWidget)
├── providers/        # Riverpod providers (状态管理)
├── services/         # 全局服务(AI/Web服务等)
├── data/             # 数据层(模型+仓储，按实体分组)
├── components/       # 可复用组件(统一导出: components/index.dart)
├── styles/           # 样式系统
├── utils/            # 工具类(i18n扩展等)
└── routes/           # 路由配置(go_router)
```

## 🎯 Riverpod 架构约束

### 1. Provider 规范

```dart
// ✅ 使用 @riverpod 注解 + 代码生成
@riverpod
class MyController extends _$MyController {
  @override
  MyControllerState build() {
    return MyControllerState(
      count: 0,
      isLoading: false,
    );
  }

  // ✅ 状态修改通过方法
  void increment() {
    state = state.copyWith(count: state.count + 1);
  }

  // ✅ 异步操作使用 AsyncValue.guard
  Future<void> loadData() async {
    state = state.copyWith(isLoading: true);
    final result = await AsyncValue.guard(() => repository.getData());
    result.when(
      data: (data) => state = state.copyWith(count: data, isLoading: false),
      error: (e, s) => state = state.copyWith(isLoading: false),
    );
  }
}

// ✅ 使用 freezed 定义不可变状态
@freezed
class MyControllerState with _$MyControllerState {
  const factory MyControllerState({
    @Default(0) int count,
    @Default(false) bool isLoading,
  }) = _MyControllerState;
}

// ❌ 禁止手动管理状态类
// ❌ 禁止使用可变状态
```

### 2. 状态管理

- ✅ **必须**使用 providers 管理全局状态（articleStateProvider, diaryStateProvider）
- ✅ **必须**使用 `ref.watch()` 进行响应式读取，`ref.read()` 进行一次性读取
- ✅ **必须**使用 freezed 定义不可变状态模型
- ❌ **禁止** 直接使用 `.obs`、`Obx()` 等 GetX 模式
- ❌ **禁止**跨 provider 直接调用，使用 `ref.watch()` / `ref.read()`

### 3. 数据管理架构

| 层级 | 职责 |
|------|------|
| **Repository** | ObjectBox 查询、数据持久化 |
| **StateProvider** | AsyncNotifier，管理数据状态 |
| **ControllerProvider** | UI状态、用户输入逻辑 |
| **View** | ConsumerWidget，ref.watch 响应式绑定 |

### 4. Provider 依赖

```dart
// ✅ Provider 之间通过 ref 访问
@riverpod
class ArticlesController extends _$ArticlesController {
  @override
  ArticlesControllerState build() {
    // 监听状态服务
    final articlesAsync = ref.watch(articleStateProvider);
    return ArticlesControllerState();
  }

  // 读取其他 provider
  Future<void> refresh() async {
    ref.read(articleStateProvider.notifier).loadArticles();
  }
}

// ❌ 禁止循环依赖
// ❌ 禁止在 build 方法外访问 ref
```

### 5. Widget 组件规范

```dart
// ✅ 推荐：纯展示组件使用 StatelessWidget
class MyCard extends StatelessWidget {
  final String title;
  final VoidCallback? onTap;

  const MyCard({required this.title, this.onTap});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        title: Text(title),
        onTap: onTap,
      ),
    );
  }
}

// ✅ 父组件使用 ConsumerWidget
class MyView extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(myControllerProvider);

    return MyCard(
      title: 'Count: ${state.count}',
      onTap: () => ref.read(myControllerProvider.notifier).increment(),
    );
  }
}

// ❌ 禁止使用 GetView
// ❌ 禁止使用 Obx
```

### 6. Provider 读取模式

```dart
// ✅ ref.watch() - 响应式读取（Widget 中使用）
class MyView extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(myControllerProvider);
    return Text('${state.count}');
  }
}

// ✅ ref.read() - 一次性读取（回调、事件中使用）
void onButtonPressed(WidgetRef ref) {
  ref.read(myControllerProvider.notifier).increment();
}

// ✅ ref.listen() - 副作用监听
ref.listen(myControllerProvider, (previous, next) {
  if (next.hasError) {
    showError('操作失败');
  }
});

// ❌ 禁止在 build 方法外使用 ref.watch()
```

### 7. 路由与导航

```dart
// ✅ 推荐：使用 go_router
logger.i('[Navigation] 导航到文章详情');
context.go('/article/$articleId');

// ✅ 复杂逻辑封装在 Provider 方法中
@riverpod
class ArticleController extends _$ArticleController {
  void openArticle(Article article) {
    // 权限检查
    if (article.isLocked && !hasPermission) {
      showError('需要权限');
      return;
    }

    // 埋点统计
    logger.i('[Navigation] 打开文章: ${article.id}');

    // 导航
    context.go('/article/${article.id}');
  }
}

// ✅ 路由定义 (lib/app/routes/router.dart)
final routerProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    routes: [
      GoRoute(
        path: '/article/:id',
        builder: (context, state) {
          final id = int.parse(state.pathParameters['id']!);
          return ArticleDetailView(articleId: id);
        },
      ),
    ],
  );
});
```

## 🔧 错误处理与数据访问

### 异步操作

```dart
// ✅ 使用 AsyncValue.guard 包装异步结果
Future<void> fetchData() async {
  state = const AsyncValue.loading();
  state = await AsyncValue.guard(() async {
    return repository.getData();
  });
}

// ✅ 在 Widget 中处理 AsyncValue
class MyView extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dataAsync = ref.watch(myDataProvider);

    return dataAsync.when(
      data: (data) => Text('Data: $data'),
      loading: () => CircularProgressIndicator(),
      error: (e, s) => ErrorWidget(e),
    );
  }
}

// ❌ 禁止在 Provider 外手动处理异常
```

### 用户反馈

```dart
// ✅ 使用统一消息方法
showError('错误信息');
showSuccess('操作成功');
showLoading('加载中...');
```

### 数据访问

```dart
// ✅ 仓储类使用静态方法
class ArticleRepository {
  static List<Article> getAll() {
    return objectbox.articleBox.getAll();
  }

  static void save(Article article) {
    objectbox.articleBox.put(article);
  }
}

// ✅ 查询必须通过仓储层
final articles = ArticleRepository.getAll();
```

### 时间管理

```dart
// ✅ 持久化存储为 UTC
article.createdAt = DateTime.now().toUtc();

// ✅ 展示使用本地化
Text(DateTimeUtils.formatDateTimeToLocal(article.createdAt))
```

### 安全与隐私

- ✅ 敏感信息存储于 `SettingRepository`
- ❌ **禁止**在日志中输出 Token/口令

## 🎨 统一样式系统

### 导入规范

```dart
// ✅ 唯一正确方式
import 'package:daily_satori/app/styles/index.dart';
```

### 颜色系统 (AppColors)

```dart
// ✅ 使用主题感知方法
AppColors.getPrimary(context)
AppColors.getSurface(context)
AppColors.getOnSurfaceVariant(context)

// ❌ 禁止硬编码
Color(0xFF5E8BFF)  // 禁止
Colors.blue        // 禁止
```

### 尺寸系统 (Dimensions)

```dart
// ✅ 间距常量
Dimensions.spacingXs/S/M/L/Xl/Xxl  // 4/8/16/24/32/48px

// ✅ 内边距预设
Dimensions.paddingPage/Card/Button/Input/ListItem

// ✅ 间隔组件
Dimensions.verticalSpacerS/M/L/Xl
Dimensions.horizontalSpacerS/M/L

// ✅ 圆角
Dimensions.radiusXs/S/M/L/Xl/Circular

// ✅ 图标尺寸
Dimensions.iconSizeXs/S/M/L/Xl/Xxl  // 12/16/20/24/32/48px

// ❌ 禁止硬编码
EdgeInsets.all(16)        // 禁止
BorderRadius.circular(8)  // 禁止
```

### 字体系统 (AppTypography)

```dart
// 标题系列
AppTypography.headingLarge/Medium/Small  // 32/24/20px

// 副标题系列
AppTypography.titleLarge/Medium/Small    // 18/16/14px

// 正文系列
AppTypography.bodyLarge/Medium/Small     // 16/15/13px

// 标签系列
AppTypography.labelLarge/Medium/Small    // 14/12/11px

// 特殊用途
AppTypography.buttonText/appBarTitle/chipText

// ❌ 禁止硬编码
TextStyle(fontSize: 14)  // 禁止
```

### 组件样式

```dart
// ✅ 使用 ButtonStyles
ButtonStyles.getPrimaryStyle(context)     // 主要按钮
ButtonStyles.getSecondaryStyle(context)   // 次要按钮
ButtonStyles.getOutlinedStyle(context)    // 轮廓按钮
ButtonStyles.getTextStyle(context)        // 文本按钮
ButtonStyles.getDangerStyle(context)      // 危险按钮

// ✅ 使用 InputStyles
InputStyles.getInputDecoration(context, hintText: '...')
InputStyles.getSearchDecoration(context, hintText: '...')
InputStyles.getCleanInputDecoration(context, hintText: '...')
InputStyles.getTitleInputDecoration(context, hintText: '...')

// ✅ 优先使用 StyleGuide
StyleGuide.getPageContainerDecoration(context)
StyleGuide.getCardDecoration(context)
StyleGuide.getListItemDecoration(context)
StyleGuide.getEmptyState(context, message: '...', icon: Icons.inbox)
StyleGuide.getLoadingState(context, message: '...')
StyleGuide.getErrorState(context, message: '...', onRetry: ...)
```

### 样式优先级

1. 优先使用 `StyleGuide` 高级方法
2. 其次使用组件样式类 (`ButtonStyles`, `InputStyles`)
3. 再次使用基础 Tokens (`Dimensions`, `AppColors`, `AppTypography`)
4. 最后才使用 `.copyWith()` 微调

## 📝 代码规范

### 命名约定

| 类型 | 风格 | 示例 |
|------|------|------|
| 文件/目录 | snake_case | `ai_chat_controller.dart`, `user_profile/` |
| 类/枚举 | PascalCase | `ArticleController`, `MessageType` |
| 方法/变量 | camelCase | `sendMessage()`, `userName` |
| 常量 | SCREAMING_SNAKE_CASE | `MAX_RETRY_COUNT` |

### Import 规范

```dart
// 1. Dart/Flutter 核心库
import 'dart:async';
import 'package:flutter/material.dart';

// 2. 第三方库
import 'package:get/get.dart';
import 'package:objectbox/objectbox.dart';

// 3. 项目内导入(优先聚合导出)
import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/styles/index.dart';
```

## 🎯 代码质量约束（强制）

### 1. 函数长度限制

- ✅ **必须**：每个函数/方法不超过 **50 行**
- ✅ **必须**：代码缩进不超过 **3 层**
- ✅ 超过限制时必须拆分为多个小函数

### 2. 函数拆分原则

- 每个函数只做一件事
- 函数名清晰表达意图
- 避免副作用
- 保持抽象层次一致

#### 提取方法 (Extract Method)

```dart
// ❌ 错误：函数过长
Widget _buildContent(BuildContext context) {
  return Container(
    // 100+ 行的复杂逻辑...
  );
}

// ✅ 正确：拆分为多个小函数
Widget _buildContent(BuildContext context) {
  return Container(
    decoration: _buildDecoration(context),
    child: Column(
      children: [
        _buildHeader(context),
        _buildBody(context),
        _buildFooter(context),
      ],
    ),
  );
}

BoxDecoration _buildDecoration(BuildContext context) {
  return BoxDecoration(
    color: AppColors.getSurface(context),
    borderRadius: BorderRadius.circular(Dimensions.radiusS),
  );
}
```

#### 缩进控制

```dart
// ❌ 错误：缩进过深（4层+）
if (condition1) {
  if (condition2) {
    for (var item in list) {
      if (item.isValid) {
        // 第4层缩进...
      }
    }
  }
}

// ✅ 正确：提前 return，提取函数
if (!condition1 || !condition2) return;

for (var item in list) {
  _processItem(item);
}

void _processItem(Item item) {
  if (!item.isValid) return;
  // 处理逻辑...（最多3层缩进）
}
```

### 3. 日志规范

#### 必须添加日志的场景

- 用户操作（点击、输入、导航）
- 状态变化（加载、完成、错误）
- 关键业务逻辑执行
- 异步操作开始和结束
- 错误和异常情况

#### 日志格式

```dart
// 使用 LoggerService
import 'package:daily_satori/app/services/logger_service.dart';

// 调试信息
logger.d('[ClassName] 操作描述: 关键信息');

// 普通信息
logger.i('[ClassName] 用户操作: 操作详情');

// 警告信息
logger.w('[ClassName] 警告: 警告详情');

// 错误信息
logger.e('[ClassName] 错误: 错误详情', error: e, stackTrace: st);
```

#### 日志示例

```dart
class ChatController extends BaseGetXController {
  void sendMessage(String content) {
    logger.i('[ChatController] 发送消息: ${content.substring(0, min(50, content.length))}...');

    if (content.trim().isEmpty) {
      logger.w('[ChatController] 消息为空，忽略发送');
      return;
    }

    // 业务逻辑...
  }

  @override
  void onInit() {
    super.onInit();
    logger.d('[ChatController] 初始化');
  }

  @override
  void dispose() {
    logger.d('[ChatController] 释放资源');
    super.dispose();
  }
}
```

### 4. 文档注释规范

#### 必须添加文档注释

- 所有 public 类
- 所有 public 方法/函数
- 所有 public 属性（复杂的）
- 复杂的业务逻辑

#### 注释格式

```dart
/// 类的简要描述
///
/// 类的详细说明，可以多行
/// 说明类的用途、使用场景等
class MyWidget extends StatelessWidget {
  // ========================================================================
  // 属性
  // ========================================================================

  /// 属性的描述
  /// 说明属性的用途和注意事项
  final String title;

  /// 回调函数
  /// 当用户点击时触发
  final VoidCallback? onTap;

  // ========================================================================
  // UI构建
  // ========================================================================

  /// 构建主要内容
  ///
  /// 根据 [title] 显示标题
  /// 如果提供了 [onTap]，则可以点击
  Widget _buildContent(BuildContext context) {
    // ...
  }
}
```

#### 部分标记（Section Markers）

使用部分标记组织代码结构：

```dart
// ========================================================================
// 属性
// ========================================================================

// ========================================================================
// 生命周期
// ========================================================================

// ========================================================================
// 事件处理
// ========================================================================

// ========================================================================
// UI构建
// ========================================================================

// ========================================================================
// 辅助方法
// ========================================================================
```

## 🎨 Flutter 最佳实践

### Widget 构建原则

```dart
// ✅ 使用 const 构造函数
const Text('Hello');
const SizedBox(height: 16);

// ✅ 使用 const 构造器
class MyWidget extends StatelessWidget {
  const MyWidget({super.key});
}

// ✅ 提取常量 Widget
static const _emptyBox = SizedBox.shrink();
```

### 状态管理最佳实践

```dart
// ✅ StatelessWidget 用于纯展示
class UserCard extends StatelessWidget {
  final User user;
  const UserCard({required this.user});
}

// ✅ StatefulWidget 仅用于组件内部状态
class ExpandableCard extends StatefulWidget {
  // 只管理展开/折叠状态
}

// ✅ GetX 用于页面级状态
class ArticleListView extends GetView<ArticleController> {
  // 使用 controller 管理页面状态
}
```

### 性能优化

```dart
// ✅ 使用 ListView.builder 而非 ListView
ListView.builder(
  itemCount: items.length,
  itemBuilder: (context, index) => ItemWidget(items[index]),
);

// ✅ 使用 const 减少重建
const Divider();
const SizedBox(height: 8);

// ✅ 避免在 build 中创建对象
final _textStyle = TextStyle(fontSize: 14); // 在 build 外部

// ❌ 禁止在 build 中创建
Widget build(BuildContext context) {
  final style = TextStyle(fontSize: 14); // 禁止
}
```

### 类成员顺序

1. 常量
2. 静态变量
3. 实例变量（属性）
4. 构造函数
5. 生命周期方法（initState, dispose 等）
6. 公共方法
7. 事件处理方法
8. UI构建方法（build, _buildXxx）
9. 私有辅助方法

### Widget 拆分原则

```dart
// ✅ 正确：将大 Widget 拆分为小 Widget
class MyPage extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: _buildAppBar(context),
      body: _buildBody(context),
    );
  }

  Widget _buildAppBar(BuildContext context) {
    return AppBar(
      title: _buildAppBarTitle(context),
      actions: _buildAppBarActions(context),
    );
  }

  Widget _buildBody(BuildContext context) {
    return Column(
      children: [
        _buildHeader(context),
        Expanded(child: _buildContent(context)),
        _buildFooter(context),
      ],
    );
  }

  // 每个函数都很简洁，不超过50行
}
```

## ⚙️ 服务注册

- 新服务实现 `AppService` 接口
- 在 `ServiceRegistry.registerAll()` 注册
- 按优先级：critical / high / normal / low
- 资源管理：Controller 中正确 dispose

## 📝 代码质量检查

### 强制执行 flutter analyze

```bash
# ✅ 每次代码修改后必须执行
flutter analyze

# ✅ 确保输出: No issues found!
```

**执行要求**：

- 修改代码后立即执行
- 修复所有 error、warning、info
- 再次执行确认无问题
- 提交前最终检查

## 🔍 检查清单

### 架构约束

- [ ] 继承 `BaseGetXController`
- [ ] 使用状态服务（不直接查找控制器）
- [ ] 使用事件总线模式
- [ ] 直接使用 GetX 路由（Get.toNamed/back/offAllNamed）
- [ ] 导航操作添加了日志记录
- [ ] 服务在 `ServiceRegistry` 注册
- [ ] 使用 `Binding` + `List<Bind>` 依赖注入

### GetX 实践

- [ ] 变量使用 `.obs`
- [ ] UI使用 `Obx()` 更新
- [ ] 依赖注入用 `Bind.lazyPut()`
- [ ] 避免控制器相互查找
- [ ] 明确定义事件类型

### 代码质量

- [ ] 执行 `flutter analyze` 通过
- [ ] 异步操作用 `safeExecute()`
- [ ] 使用统一消息方法
- [ ] **每个函数不超过 50 行**
- [ ] **代码缩进不超过 3 层**
- [ ] 添加了适当的日志
- [ ] 添加了文档注释
- [ ] 使用了部分标记组织代码

### 样式系统

- [ ] 导入 `app/styles/index.dart`
- [ ] 使用 `Dimensions` 常量
- [ ] 使用 `AppColors.getXxx(context)`
- [ ] 使用 `AppTypography` 字体
- [ ] 使用 `ButtonStyles` / `InputStyles`
- [ ] 优先使用 `StyleGuide` 方法
- [ ] 避免硬编码数值/颜色

### Flutter 最佳实践

- [ ] 使用 `const` 构造函数
- [ ] StatelessWidget 用于纯展示
- [ ] 使用 ListView.builder 而非 ListView
- [ ] 避免在 build 中创建对象
- [ ] 正确处理资源释放 (dispose)

### 安全与隐私

- [ ] 敏感信息不输出日志
- [ ] UTC存储与本地化显示
- [ ] 正确处理用户数据

## ⚠️ 违规后果

- 代码审查不通过
- PR 被拒绝
- 需重构后重新提交
- **未执行 analyze 的代码直接拒绝**

**所有 AI 工具和开发者必须严格遵守这些约束。如有疑问，开发前讨论确认。**