# Daily Satori 编码规范与项目约定

本文档定义了 Daily Satori 项目的完整编码标准、架构约束、功能规范和最佳实践要求，所有开发者必须严格遵守这些规范。

## 📚 技术栈与依赖

### 核心技术栈
- **Flutter**: 3.32.x
- **Dart**: 3.8.x
- **状态管理**: GetX (GetMaterialApp, GetPage, Bindings, Controller + Rx)
- **本地存储**: ObjectBox (仓储模式封装)
- **网络库**: dio, web_socket_channel
- **WebView**: flutter_inappwebview
- **AI 能力**: openai_dart + 自定义配置 (assets/configs/ai_models.yaml, ai_prompts.yaml)

### 关键依赖
- **系统集成**: url_launcher, connectivity_plus, share_plus, permission_handler
- **媒体处理**: image_picker, flutter_markdown
- **数据处理**: archive
- **UI 框架**: 自定义主题 `AppTheme`, `app/styles/`, `StyleGuide`, `components/*`

## 🏗️ 系统架构

### 分层架构原则
- **界面层** (`app/modules/*/views`): 仅负责界面展示和用户交互
- **控制层** (`app/modules/*/controllers`): GetX Controller，包含状态 Rx、视图行为、生命周期
- **绑定层** (`app/modules/*/bindings`): 注册 Controller 及依赖注入
- **服务层** (`app/services/*`): 跨模块领域服务 (AI/备份/网页解析/剪贴板等)
- **仓储层** (`app/repositories/*`): 封装 ObjectBox 查询与聚合，返回 Model 包装类
- **模型层** (`app/models/*`): 与 ObjectBox 实体配套的模型包装与领域对象

### 启动流程
1. `main.dart` → `initApp()`
2. `ServiceRegistry.registerAll()` 按优先级注册服务：
   - **critical**: 启动前必须完成 (Logger, Flutter, ObjectBox, Setting, File, Http)
   - **high**: 启动后立即 (Font, ADBlock, FreeDisk, AIConfig)
   - **normal**: 启动后异步 (AI, Backup, Migration, Plugin, Web, Book)
   - **low**: 首帧后延迟 (AppUpgrade, ShareReceive, ClipboardMonitor)
3. `GetMaterialApp` 使用 `AppPages.routes`, `AppPages.initial`

## 📁 目录结构约束

```
lib/app/
├── controllers/           # GetX 控制器基类和通用控制器
│   └── base_controller.dart              # GetX 基础控制器
├── modules/               # 功能模块
│   └── [feature]/
│       ├── controllers/   # 功能控制器 (继承 BaseGetXController)
│       ├── views/         # 页面视图 (使用 Obx 响应式)
│       ├── bindings/      # 依赖注入绑定 (现代 GetX API)
│       └── models/        # 数据模型
├── services/              # 全局服务
│   ├── navigation_service.dart           # 导航服务
│   ├── state/            # 状态管理服务 (继承 GetxService)
│   │   ├── app_state_service.dart        # 应用状态服务
│   │   ├── article_state_service.dart    # 文章状态服务
│   │   ├── diary_state_service.dart      # 日记状态服务
│   │   ├── state_services.dart           # 状态服务导出
│   │   └── state_bindings.dart           # 状态服务绑定
│   └── [service].dart    # 具体服务实现
├── repositories/          # 数据仓库层 (静态方法风格)
├── components/            # 可复用组件
├── styles/               # 主题、颜色、字体与尺寸
├── utils/                # 工具类与基类
└── routes/               # 路由配置
```

**目录约定**:
- 模块化组织：三件套 (bindings, controllers, views) 全且命名一致
- 服务独立文件：每个服务独立文件，导出于 `services.dart`
- 仓储静态方法：采用静态方法风格，导出于 `repositories.dart`
- 聚合导出：`app_exports.dart` 提供单点导入

## 🎯 GetX 架构约束与最佳实践

### GetX 优化目标
基于 GetX 官方文档的最佳实践，我们的架构遵循以下核心原则：

1. **解耦UI、逻辑、依赖和路由**
2. **使用 Get.put() 让类对所有子路由可用**
3. **使用 Get.find() 检索控制器实例而无需上下文**
4. **使用 .obs 使任何变量可观察**
5. **使用 Obx(() => Text(controller.name)) 更新UI**

### 1. 控制器规范

**必须继承 BaseGetXController**

`BaseGetXController` 提供了统一的控制器模式，包括：
- 标准的错误处理机制
- 加载状态管理
- `safeExecute()` 安全执行异步操作
- 重试机制和导航方法

```dart
// ✅ 正确：继承 BaseGetXController
class ArticlesController extends BaseGetXController {
  final isLoading = false.obs;
  final articles = <ArticleModel>[].obs;

  @override
  void onInit() {
    super.onInit();
    _loadArticles();
  }

  Future<void> _loadArticles() async {
    await safeExecute(
      () async {
        final result = await ArticleRepository.findAll();
        articles.value = result;
      },
      loadingMessage: "加载中...",
      errorMessage: "加载失败",
    );
  }
}

// ❌ 错误：直接继承 GetxController
class ArticlesController extends GetxController {
  bool isLoading = false;
  List<ArticleModel> articles = [];
}
```

**必须使用响应式变量**
```dart
// ✅ 正确：使用 .obs 响应式变量
final isLoading = false.obs;
final articles = <ArticleModel>[].obs;
final selectedTag = Rxn<String>();

// ❌ 错误：使用普通变量
bool isLoading = false;
List<ArticleModel> articles = [];
```

### 2. 状态管理约束

**跨页面状态必须使用状态服务**

我们创建了专门的状态服务来管理跨页面的全局状态：

- **AppStateService**: 管理应用级别状态（导航状态、加载状态、错误/成功消息等）
- **ArticleStateService**: 管理文章相关全局状态（活跃文章引用、文章更新事件总线、全局搜索）
- **DiaryStateService**: 管理日记相关全局状态（活跃日记引用、日记更新通知、全局过滤）

```dart
// ✅ 正确：使用状态服务管理全局状态和事件总线
class ArticleStateService extends GetxService {
  final Rxn<ArticleModel> activeArticle = Rxn<ArticleModel>();
  final Rx<ArticleUpdateEvent> articleUpdateEvent = Rx<ArticleUpdateEvent>(ArticleUpdateEvent.none());
  final RxString globalSearchQuery = ''.obs;

  void setActiveArticle(ArticleModel article) {
    activeArticle.value = article;
  }

  void notifyArticleUpdated(ArticleModel article) {
    if (activeArticle.value?.id == article.id) {
      activeArticle.value = article;
    }
    articleUpdateEvent.value = ArticleUpdateEvent.updated(article);
  }

  void notifyArticleCreated(ArticleModel article) {
    articleUpdateEvent.value = ArticleUpdateEvent.created(article);
  }

  void notifyArticleDeleted(int articleId) {
    if (activeArticle.value?.id == articleId) {
      clearActiveArticle();
    }
    articleUpdateEvent.value = ArticleUpdateEvent.deleted(articleId);
  }
}

// ❌ 错误：在控制器中管理全局状态
class ArticlesController extends BaseGetXController {
  static ArticleModel? globalActiveArticle; // 禁止静态全局变量
}
```

**禁止直接使用 Get.find() 查找其他控制器**

这是最重要的解耦原则之一。控制器之间不应该直接相互查找和调用。

```dart
// ❌ 禁止：紧耦合的控制器查找
if (Get.isRegistered<ArticlesController>()) {
  final ac = Get.find<ArticlesController>();
  ac.updateArticle(id);
}

// ✅ 正确：通过状态服务解耦
_articleStateService.notifyArticleUpdated(article);
```

### 事件总线模式约束

**必须使用事件总线进行跨页面状态同步**

对于需要跨多个页面同步状态的场景，必须使用事件总线模式，而不是直接的状态更新。

```dart
// ✅ 正确：使用事件总线模式
class ArticleStateService extends GetxService {
  final Rx<ArticleUpdateEvent> articleUpdateEvent = Rx<ArticleUpdateEvent>(ArticleUpdateEvent.none());

  void notifyArticleUpdated(ArticleModel article) {
    articleUpdateEvent.value = ArticleUpdateEvent.updated(article);
  }
}

// 控制器监听事件
class ArticlesController extends BaseGetXController {
  void _initStateServices() {
    ever(_articleStateService.articleUpdateEvent, (event) {
      if (event.affectsArticle(articleId)) {
        // 处理更新
      }
    });
  }
}

// ❌ 错误：直接更新状态或查找其他控制器
class ShareDialogController extends BaseGetXController {
  void saveArticle() {
    // 错误：直接查找其他控制器
    if (Get.isRegistered<ArticlesController>()) {
      Get.find<ArticlesController>().updateArticle(article.id);
    }
  }
}
```

**事件类型定义规范**

事件类型必须明确定义，包含完整的事件信息和类型检查方法。

```dart
/// 文章更新事件类型
enum ArticleEventType {
  none,
  created,
  updated,
  deleted,
}

/// 文章更新事件
class ArticleUpdateEvent {
  final ArticleEventType type;
  final ArticleModel? article;
  final int? articleId;

  const ArticleUpdateEvent._({
    required this.type,
    this.article,
    this.articleId,
  });

  factory ArticleUpdateEvent.none() => const ArticleUpdateEvent._(type: ArticleEventType.none);
  factory ArticleUpdateEvent.created(ArticleModel article) => ArticleUpdateEvent._(type: ArticleEventType.created, article: article);
  factory ArticleUpdateEvent.updated(ArticleModel article) => ArticleUpdateEvent._(type: ArticleEventType.updated, article: article);
  factory ArticleUpdateEvent.deleted(int articleId) => ArticleUpdateEvent._(type: ArticleEventType.deleted, articleId: articleId);

  /// 检查是否影响指定文章
  bool affectsArticle(int articleId) {
    return switch (type) {
      ArticleEventType.created => article?.id == articleId,
      ArticleEventType.updated => article?.id == articleId,
      ArticleEventType.deleted => this.articleId == articleId,
      ArticleEventType.none => false,
    };
  }
}
```

**状态共享模式对比**

**之前的紧耦合方式**:
```dart
// 控制器直接相互查找
if (Get.isRegistered<ArticlesController>()) {
  final ac = Get.find<ArticlesController>();
  ac.updateArticle(id);
}
```

**现在的松耦合方式（事件总线模式）**:
```dart
// 发布更新事件
_articleStateService.notifyArticleUpdated(article);

// 其他页面监听事件流
_articleStateService.articleUpdateEvent.listen((event) {
  if (event.affectsArticle(articleId)) {
    // 处理更新
  }
});
```

**事件总线模式优势**:
- **完全解耦**: 控制器之间零依赖
- **事件驱动**: 基于事件的状态同步
- **类型安全**: 明确定义的事件类型
- **性能优化**: 避免不必要的数据库查询
- **可扩展性**: 新页面只需监听事件流

### 3. 依赖注入约束

**必须使用现代 GetX API**

从 GetX 的旧版 API (`Binding` + `List<Bind>`) 迁移到现代 API (`Bindings` + `void dependencies()`)。

```dart
// ✅ 正确：使用现代 API
class ArticlesBinding extends Bindings {
  @override
  void dependencies() {
    Get.lazyPut<ArticlesController>(() => ArticlesController());
  }
}

// ❌ 错误：使用过时 API
class ArticlesBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut<ArticlesController>(() => ArticlesController())];
  }
}
```

**服务必须在 ServiceRegistry 中注册**

所有状态服务必须在 `ServiceRegistry` 中注册，确保在应用启动时正确初始化。

```dart
// ✅ 正确：在服务注册器中注册
register(
  FunctionAppService(
    serviceName: 'ArticleStateService',
    priority: ServicePriority.high,
    onInit: () => Get.put(ArticleStateService()),
  ),
);
```

**状态服务统一初始化**

通过 `StateBindings` 进行统一服务初始化：

```dart
class StateBindings extends Bindings {
  @override
  void dependencies() {
    Get.put(AppStateService());
    Get.put(ArticleStateService());
    Get.put(DiaryStateService());
  }
}
```

### 4. 路由与导航约束

**必须使用 NavigationService 进行导航**

我们创建了 `NavigationService` 来集中管理导航，提供类型安全的便捷导航方法，支持导航历史记录和路由中间件。

```dart
// ✅ 正确：使用导航服务
_navigationService.toArticleDetail(article);
_navigationService.back();

// ❌ 错误：直接使用 Get.toNamed()
Get.toNamed(Routes.articleDetail, arguments: article);
```

**导航模式对比**

**之前的方式**:
```dart
Get.toNamed(Routes.articleDetail, arguments: article);
```

**现在的方式**:
```dart
_navigationService.toArticleDetail(article);
// 类型安全、带历史记录、中间件支持
```

**路由注册约束**:
- 路由统一登记于 `app/routes/app_pages.dart`
- 常量定义在 `app_routes.dart`
- 页面创建必须绑定对应 Binding
- 禁止在视图中 `Get.put` 业务 Controller

## 📊 数据访问与仓储约定

### 仓储模式
- 仓储类均为静态方法风格 (`ArticleRepository.find()`, `ArticleRepository.update()`)
- 查询必须通过仓储，禁止在 UI/Controller 层直接访问 ObjectBox Box
- 删除需清理关联 (如文章删除需清空 tags/images/screenshots)

### 分页策略
- 列表分页通过锚点 ID 与方向标记实现
- 统一 pageSize 与排序规则 (按 `id` 倒序)
- 防抖/去重处理滚动加载

### 时间管理
- 持久化时间统一存储为 UTC
- 展示时使用 `DateTimeUtils.formatDateTimeToLocal` 本地化
- `DateTimeUtils.nowToString()` 仅用于日志与非持久化场景

## 🔧 错误处理与安全约束

### 异步操作约束

**必须使用 safeExecute() 方法**

`BaseGetXController` 提供的 `safeExecute()` 方法统一了异步操作的错误处理模式。

```dart
// ✅ 正确：使用安全执行
await safeExecute(
  () async {
    final result = await someAsyncOperation();
    return result;
  },
  loadingMessage: "处理中...",
  errorMessage: "操作失败",
  onSuccess: (result) => showSuccess("成功"),
  onError: (e) => logger.e("操作失败", error: e),
);

// ❌ 错误：直接 try-catch
try {
  final result = await someAsyncOperation();
  showSuccess("成功");
} catch (e) {
  showError("失败: $e");
}
```

**错误处理模式对比**

**之前的分散错误处理**:
```dart
try {
  // 操作
} catch (e) {
  logger.e("错误: $e");
  UIUtils.showError("操作失败");
}
```

**现在的统一错误处理**:
```dart
await safeExecute(
  () => someAsyncOperation(),
  loadingMessage: "处理中...",
  errorMessage: "操作失败",
  onSuccess: (result) => showSuccess("成功"),
  onError: (e) => logger.e("操作失败", error: e),
);
```

### 用户反馈约束

**必须使用统一的消息方法**

```dart
// ✅ 正确：使用统一反馈
showError("操作失败");
showSuccess("保存成功");
showLoading("处理中...");

// ❌ 错误：直接使用其他 UI 工具
UIUtils.showError("失败");
errorNotice("错误");
```

### 安全与隐私
- API Token、口令等存储于 `SettingRepository`
- 禁止在日志/异常栈中输出 Token/口令等敏感信息
- 使用 `logger` 统一输出日志 (定义于 `logger_service.dart`)
- 插件与 Web 服务地址需可配置，默认使用可信源

## 📋 数据流约束

### 数据更新模式

**必须通过状态服务通知更新**

```dart
// ✅ 正确：状态服务通知
void updateArticle(ArticleModel article) async {
  await ArticleRepository.update(article);
  _articleStateService.notifyArticleUpdated(article);
}

// ❌ 错误：直接查找其他控制器更新
void updateArticle(ArticleModel article) async {
  await ArticleRepository.update(article);
  if (Get.isRegistered<ArticlesController>()) {
    Get.find<ArticlesController>().updateArticle(article.id);
  }
}
```

### 数据监听模式

**必须使用响应式监听**

```dart
// ✅ 正确：响应式监听
void _initStateServices() {
  ever(_articleStateService.globalSearchQuery, (query) {
    if (query.isNotEmpty) {
      _handleSearch(query);
    }
  });
}

// ❌ 错误：手动检查更新
void checkForUpdates() {
  final query = _articleStateService.globalSearchQuery.value;
  if (query.isNotEmpty) {
    _handleSearch(query);
  }
}
```

## 🎨 统一风格系统约束

### 风格系统架构

Daily Satori 采用分层风格系统，确保 UI 一致性和可维护性：

1. **基础样式层** (`app/styles/base/`): 定义颜色、字体、尺寸等基础常量
2. **组件样式层** (`app/styles/components/`): 提供按钮、卡片、输入框等组件样式
3. **风格指南层** (`StyleGuide`): 提供高级设计模式和统一的应用方法

### 基础样式约束

**必须使用统一的样式常量**

```dart
// ✅ 正确：使用 Dimensions 常量
Dimensions.paddingPage
Dimensions.verticalSpacerL
Dimensions.horizontalSpacerM
Dimensions.radiusM

// ❌ 错误：硬编码数值
const EdgeInsets.fromLTRB(20, 16, 20, 16)
const SizedBox(height: 20)
const BorderRadius.circular(12)
```

**必须使用主题感知的颜色**

```dart
// ✅ 正确：使用 AppColors 获取主题颜色
AppColors.getSurface(context)
AppColors.getPrimary(context)
AppColors.getOnSurfaceVariant(context)

// ❌ 错误：硬编码颜色
Colors.white
Colors.black
Color(0xFF666666)
```

**必须使用统一的字体样式**

```dart
// ✅ 正确：使用 AppTypography 字体样式
AppTypography.appBarTitle
AppTypography.titleSmall
AppTypography.bodyMedium
AppTypography.buttonText

// ❌ 错误：硬编码字体样式
TextStyle(fontSize: 16, fontWeight: FontWeight.w600)
TextStyle(fontSize: 14, color: Colors.grey)
```

### 风格指南约束

**必须使用 StyleGuide 进行高级样式应用**

```dart
// ✅ 正确：使用 StyleGuide 方法
StyleGuide.getPrimaryButtonStyle(context)
StyleGuide.getCardDecoration(context)
StyleGuide.getInputDecoration(context, hintText: '请输入')
StyleGuide.getEmptyState(context, message: '暂无数据')

// ❌ 错误：手动构建样式
ButtonStyle(
  backgroundColor: MaterialStateProperty.all(AppColors.getPrimary(context)),
  // ...
)
BoxDecoration(
  color: AppColors.getSurface(context),
  borderRadius: BorderRadius.circular(12),
  // ...
)
```

### 样式系统层次结构

**基础常量 → 组件样式 → 风格指南**

```dart
// 1. 基础常量 (Dimensions, AppColors, AppTypography)
Dimensions.paddingPage
AppColors.getPrimary(context)
AppTypography.bodyMedium

// 2. 组件样式 (ButtonStyles, CardStyles, InputStyles)
ButtonStyles.getPrimaryStyle(context)
CardStyles.getStandardStyle(context)

// 3. 风格指南 (StyleGuide)
StyleGuide.getPrimaryButtonStyle(context)  // 内部调用 ButtonStyles
StyleGuide.getCardDecoration(context)      // 内部调用 CardStyles
```

### 页面布局约束

**必须使用统一的页面布局模式**

```dart
// ✅ 正确：使用 StyleGuide 标准布局
StyleGuide.getStandardPageLayout(
  context: context,
  child: Column(children: [...]),
  hasAppBar: true,
  hasPadding: true,
)

// ✅ 正确：使用标准列表布局
StyleGuide.getStandardListLayout(
  context: context,
  children: itemWidgets,
  padding: Dimensions.paddingPage,
)

// ❌ 错误：手动构建布局
Scaffold(
  backgroundColor: AppColors.getBackground(context),
  body: SafeArea(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(children: [...]),
    ),
  ),
)
```

### 状态组件约束

**必须使用统一的空状态、加载状态和错误状态**

```dart
// ✅ 正确：使用 StyleGuide 状态组件
StyleGuide.getEmptyState(
  context,
  message: '暂无数据',
  icon: Icons.inbox_outlined,
  action: ElevatedButton(...),
)

StyleGuide.getLoadingState(context, message: '加载中...')

StyleGuide.getErrorState(
  context,
  message: '加载失败',
  onRetry: () => _retry(),
)

// ❌ 错误：手动构建状态组件
Center(
  child: Column(
    mainAxisAlignment: MainAxisAlignment.center,
    children: [
      Icon(Icons.error_outline, size: 48, color: Colors.grey),
      Text('暂无数据', style: TextStyle(color: Colors.grey)),
    ],
  ),
)
```

### 间距系统约束

**必须使用统一的间距系统**

```dart
// ✅ 正确：使用 Dimensions 间距常量
Dimensions.verticalSpacerS  // 小间距 (8px)
Dimensions.verticalSpacerM  // 中间距 (16px)
Dimensions.verticalSpacerL  // 大间距 (24px)
Dimensions.verticalSpacerXl // 超大间距 (32px)

Dimensions.horizontalSpacerS
Dimensions.horizontalSpacerM
Dimensions.horizontalSpacerL

// ❌ 错误：硬编码间距
const SizedBox(height: 8)
const SizedBox(height: 16)
const SizedBox(width: 12)
```

### 圆角系统约束

**必须使用统一的圆角系统**

```dart
// ✅ 正确：使用 Dimensions 圆角常量
Dimensions.radiusS      // 小圆角 (8px)
Dimensions.radiusM      // 中圆角 (12px)
Dimensions.radiusL      // 大圆角 (16px)
Dimensions.radiusCircular // 圆形 (50%)

// ❌ 错误：硬编码圆角
BorderRadius.circular(8)
BorderRadius.circular(12)
BorderRadius.circular(16)
```

### 图标尺寸约束

**必须使用统一的图标尺寸系统**

```dart
// ✅ 正确：使用 Dimensions 图标尺寸
Dimensions.iconSizeS    // 小图标 (16px)
Dimensions.iconSizeM    // 中图标 (20px)
Dimensions.iconSizeL    // 大图标 (24px)
Dimensions.iconSizeXl   // 超大图标 (32px)
Dimensions.iconSizeXxl  // 巨大图标 (48px)

// ❌ 错误：硬编码图标尺寸
Icon(Icons.star, size: 16)
Icon(Icons.star, size: 24)
Icon(Icons.star, size: 32)
```

### 导入规范

**必须使用统一的样式导入**

```dart
// ✅ 正确：使用 app/styles/index.dart 聚合导出
import 'package:daily_satori/app/styles/index.dart';

// 然后使用：
Dimensions.paddingPage
AppColors.getPrimary(context)
AppTypography.bodyMedium
StyleGuide.getPrimaryButtonStyle(context)

// ❌ 错误：单独导入每个样式文件
import 'package:daily_satori/app/styles/base/dimensions.dart';
import 'package:daily_satori/app/styles/base/colors.dart';
import 'package:daily_satori/app/styles/base/typography.dart';
import 'package:daily_satori/app/styles/style_guide.dart';
```

## 🎨 UI 约束与响应式

### 响应式 UI 约束

**必须使用 Obx 包装动态 UI**

```dart
// ✅ 正确：使用 Obx
Obx(() => Text(
  controller.isLoading.value ? "加载中..." : "内容",
))

// ❌ 错误：使用 GetBuilder
GetBuilder<ArticlesController>(
  builder: (controller) => Text(
    controller.isLoading.value ? "加载中..." : "内容",
  ),
)
```

### 状态显示约束

**必须使用响应式状态**

```dart
// ✅ 正确：直接绑定响应式变量
Obx(() => isLoading.value ? CircularProgressIndicator() : Content())

// ❌ 错误：通过控制器方法获取状态
Obx(() => controller.isLoading() ? CircularProgressIndicator() : Content())
```

## 📋 代码风格与命名规范

### 命名约定
- **文件与目录**: snake_case
- **类与枚举**: PascalCase
- **方法/变量**: camelCase
- **常量**: SCREAMING_SNAKE_CASE

### 文件命名约束
- 控制器：`xxx_controller.dart`
- 视图：`xxx_view.dart`
- 绑定：`xxx_binding.dart`
- 服务：`xxx_service.dart`
- 模型：`xxx_model.dart`

### 类命名约束
- 控制器：`XxxController`
- 视图：`XxxView`
- 绑定：`XxxBinding`
- 服务：`XxxService`
- 模型：`XxxModel`

### 方法命名约束
- 状态设置方法：`setXxx()` 或 `updateXxx()`
- 状态获取方法：直接使用响应式变量
- 事件处理方法：`handleXxx()` 或 `onXxx()`

### Import 规范
```dart
// 1. Dart/Flutter 核心库
import 'dart:async';
import 'package:flutter/material.dart';

// 2. 第三方库
import 'package:get/get.dart';
import 'package:dio/dio.dart';

// 3. 项目内导入 (优先聚合导出)
import 'package:daily_satori/app_exports.dart';
```

## 🏆 功能模块规范

### 首页 (Home)
- 底部导航：文章、日记、读书、设置

### 文章模块 (Articles, ArticleDetail)
- **列表功能**: 分页滚动、搜索、标签筛选、收藏筛选、按日期筛选
- **统计功能**: `ArticleRepository.getDailyArticleCounts`
- **详情功能**: 截图分享、图片管理、AI 生成 Markdown
- **状态共享**: 依赖状态服务实现跨页面更新

### 日记模块 (Diary)
- 编辑器组件 `DiaryEditor`，供读书页快速记录复用

### 读书模块 (Books) - 强约束
**必须始终显示"添加感悟"悬浮按钮 (FAB)**
- 位置：右下角，`FloatingActionButtonLocation.endFloat`
- 图标：`Icons.edit_note`
- tooltip：`添加感悟`

**FAB 点击行为**:
- 若当前存在观点：预填模板包含观点标题、来源书籍、深链占位 `[](app://books/viewpoint/<id>)`
- 若无观点：预填 `读书感悟：` 的空白模板
- 打开组件：`DiaryEditor`
- **禁止**在"无观点时隐藏 FAB"或移除上述点击行为

### 备份与还原
- 本地备份目录设置、归档/解档 (archive)
- **图片路径恢复约束**: 从备份恢复后，必须自动修复数据库中图片的本地路径
- 运行时渲染前调用 `FileService.i.resolveLocalMediaPath` 增强兼容性

### AI 能力 (AiService + AIConfigService)
- 翻译、摘要 (长/短)、HTML → Markdown
- 模型/地址/令牌按功能维度可覆盖 (assets 配置 + 设置)

### Web 内容与解析
- `WebService`, `WebpageParserService`, `ADBlockService`
- 内置网站资源 `assets/website`

### 其他服务
- **应用升级**: `AppUpgradeService`
- **剪贴板监控**: `ClipboardMonitorService`
- **磁盘清理**: `FreeDiskService` (每 15 分钟触发)
- **分享功能**: ShareDialog / ShareReceiveService

## ⚙️ 服务注册与生命周期

### 服务约束
- 新增服务需实现 `AppService`
- 在 `ServiceRegistry.registerAll()` 注册，指定合理优先级
- 关键服务异常不得吞没：`critical` 阶段初始化失败会中断启动
- `low` 优先级服务由首帧后触发，避免阻塞首屏

### 资源管理
- Controller 中必须正确 dispose `TextEditingController/FocusNode/ScrollController`
- 避免在 `build` 中执行重计算
- 长任务放入 Service/Repository 层

## 🎨 样式系统规范

### 设计原则

Daily Satori 采用基于 **Design Tokens** 的现代化样式系统，遵循以下核心原则：

1. **一致性优先**: 所有UI组件必须使用统一的样式系统
2. **语义化设计**: 使用有意义的命名而非具体数值
3. **主题感知**: 所有样式自动适配亮色/暗色主题
4. **可维护性**: 单一来源的样式定义，避免重复
5. **可扩展性**: 易于添加新的样式变体

### 样式系统架构

```
lib/app/styles/
├── index.dart                 # 统一导出入口 ✅ 必须使用
├── base/                      # 基础设计 Tokens ✅ 推荐使用
│   ├── colors.dart           # 颜色系统 (新API)
│   ├── dimensions.dart       # 尺寸、间距、圆角 (新API)
│   ├── typography.dart       # 字体样式 (AppTypography)
│   ├── opacities.dart        # 透明度常量
│   ├── shadows.dart          # 阴影样式
│   ├── borders.dart          # 边框常量
│   └── border_styles.dart    # 边框样式工具
├── components/               # 组件样式 ✅ 推荐使用
│   ├── button_styles.dart    # 按钮样式
│   ├── card_styles.dart      # 卡片样式
│   ├── input_styles.dart     # 输入框样式
│   ├── list_styles.dart      # 列表样式
│   ├── dialog_styles.dart    # 对话框样式
│   └── ...
├── pages/                    # 页面特定样式
│   ├── articles_styles.dart
│   └── diary_styles.dart
├── style_guide.dart          # 样式应用指南 ✅ 推荐使用
├── theme/                    # 主题定义
│   └── app_theme.dart
│
├── ⚠️ 以下文件已废弃，仅为兼容旧代码保留 ⚠️
├── colors.dart               # [废弃] 使用 base/colors.dart
├── dimensions.dart           # [废弃] 使用 base/dimensions.dart
├── font_style.dart           # [废弃] 使用 base/typography.dart
├── theme.dart                # [废弃] 使用 theme/app_theme.dart
├── component_style.dart      # [废弃] 使用 components/
└── app_styles.dart           # [废弃] 使用 StyleGuide
```

**重要说明**：
- ✅ **新代码必须使用**: `base/`, `components/`, `StyleGuide`
- ⚠️ **旧代码逐步迁移**: 根目录下的 `colors.dart`, `font_style.dart` 等已标记为 `@Deprecated`
- 📦 **统一导入**: 使用 `import 'package:daily_satori/app/styles/index.dart';` 导入所有样式类

### 迁移指南 (旧API → 新API)

| 旧API (废弃) | 新API (推荐) | 说明 |
|------------|------------|------|
| `MyFontStyle.titleLarge` | `AppTypography.titleLarge(context)` | 字体需要context |
| `MyFontStyle.bodyMedium` | `AppTypography.bodyMedium(context)` | 自动适配主题 |
| `AppColors.primaryLight` | `AppColors.getPrimary(context)` | 新API使用getter方法 |
| `AppColors.textPrimaryLight` | `AppColors.getOnSurface(context)` | 语义化命名 |
| `Dimensions.spacingM` | `Dimensions.spacingM` | 大部分常量保持一致 |
| `ComponentStyle.cardTheme()` | `CardStyles.*` | 使用 components/card_styles.dart |
| `AppStyles.cardDecoration()` | `StyleGuide.cardDecoration(context)` | 使用 StyleGuide |
| `AppStyles.loadingState()` | `StyleGuide.loadingIndicator(context)` | 统一命名规范 |

**迁移步骤**：
1. 将 `import 'app/styles/colors.dart'` 改为 `import 'app/styles/index.dart'`
2. 替换旧的类名和方法调用
3. 为需要context的方法传递 `BuildContext context`
4. 测试编译和运行

### 基础 Tokens 使用规范

#### 1. 颜色系统 (AppColors)

**✅ 正确做法**：
```dart
// 使用主题感知方法
color: AppColors.getPrimary(context)
color: AppColors.getSurface(context)
color: AppColors.getOnSurfaceVariant(context)

// 使用预定义颜色
color: AppColors.primary
color: AppColors.success
color: AppColors.error
```

**❌ 错误做法**：
```dart
// 硬编码颜色
color: Color(0xFF5E8BFF)
color: Colors.blue
color: Color.fromRGBO(94, 139, 255, 1.0)

// 手动判断主题
color: isDark ? Color(0xFF...) : Color(0xFF...)
```

**可用颜色方法**：
- `AppColors.getPrimary(context)` - 主色
- `AppColors.getBackground(context)` - 背景色
- `AppColors.getSurface(context)` - 表面色
- `AppColors.getSurfaceContainer(context)` - 容器背景色
- `AppColors.getSurfaceContainerHighest(context)` - 高亮容器色
- `AppColors.getOnSurface(context)` - 表面上文本色
- `AppColors.getOnSurfaceVariant(context)` - 次要文本色
- `AppColors.getOutline(context)` - 边框色
- `AppColors.getOutlineVariant(context)` - 次要边框色
- `AppColors.getError(context)` - 错误色
- `AppColors.getSuccess(context)` - 成功色

#### 2. 尺寸系统 (Dimensions)

**间距常量**：
```dart
// ✅ 使用预定义间距
Dimensions.spacingXs   // 4px
Dimensions.spacingS    // 8px
Dimensions.spacingM    // 16px
Dimensions.spacingL    // 24px
Dimensions.spacingXl   // 32px
Dimensions.spacingXxl  // 48px

// ❌ 硬编码数值
const EdgeInsets.all(16)  // 错误
SizedBox(height: 24)       // 错误
```

**内边距预设**：
```dart
// ✅ 使用预定义 EdgeInsets
Dimensions.paddingPage         // 页面内边距 (20, 16)
Dimensions.paddingCard         // 卡片内边距 (16)
Dimensions.paddingButton       // 按钮内边距 (16, 12)
Dimensions.paddingInput        // 输入框内边距 (14, 14)
Dimensions.paddingListItem     // 列表项内边距 (16, 12)

// 方向性内边距
Dimensions.paddingHorizontalM  // 水平中等 (16, 0)
Dimensions.paddingVerticalM    // 垂直中等 (0, 16)

// ❌ 硬编码 EdgeInsets
padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16)  // 错误
padding: const EdgeInsets.all(16)  // 错误
```

**间隔组件**：
```dart
// ✅ 使用预定义间隔组件
Dimensions.verticalSpacerXs   // SizedBox(height: 4)
Dimensions.verticalSpacerS    // SizedBox(height: 8)
Dimensions.verticalSpacerM    // SizedBox(height: 16)
Dimensions.verticalSpacerL    // SizedBox(height: 24)
Dimensions.verticalSpacerXl   // SizedBox(height: 32)

Dimensions.horizontalSpacerS  // SizedBox(width: 8)
Dimensions.horizontalSpacerM  // SizedBox(width: 16)

// ❌ 硬编码间隔
const SizedBox(height: 20)  // 错误
const SizedBox(width: 12)   // 错误
```

**圆角系统**：
```dart
// ✅ 使用预定义圆角
BorderRadius.circular(Dimensions.radiusXs)       // 4px
BorderRadius.circular(Dimensions.radiusS)        // 8px
BorderRadius.circular(Dimensions.radiusM)        // 12px
BorderRadius.circular(Dimensions.radiusL)        // 16px
BorderRadius.circular(Dimensions.radiusXl)       // 20px
BorderRadius.circular(Dimensions.radiusCircular) // 9999px (完全圆形)

// ❌ 硬编码圆角
BorderRadius.circular(10)  // 错误
BorderRadius.circular(15)  // 错误
```

**图标尺寸**：
```dart
// ✅ 使用预定义图标尺寸
Icon(Icons.star, size: Dimensions.iconSizeXs)   // 12px
Icon(Icons.star, size: Dimensions.iconSizeS)    // 16px
Icon(Icons.star, size: Dimensions.iconSizeM)    // 20px
Icon(Icons.star, size: Dimensions.iconSizeL)    // 24px
Icon(Icons.star, size: Dimensions.iconSizeXl)   // 32px
Icon(Icons.star, size: Dimensions.iconSizeXxl)  // 48px

// ❌ 硬编码尺寸
Icon(Icons.star, size: 18)  // 错误
Icon(Icons.star, size: 22)  // 错误
```

#### 3. 字体系统 (AppTypography)

**✅ 正确做法**：
```dart
// 标题系列
style: AppTypography.headingLarge   // 32px, w600
style: AppTypography.headingMedium  // 24px, w600
style: AppTypography.headingSmall   // 20px, w600

// 副标题系列
style: AppTypography.titleLarge     // 18px, w600
style: AppTypography.titleMedium    // 16px, w600
style: AppTypography.titleSmall     // 14px, w500

// 正文系列
style: AppTypography.bodyLarge      // 16px, w400
style: AppTypography.bodyMedium     // 15px, w400
style: AppTypography.bodySmall      // 13px, w400

// 标签系列
style: AppTypography.labelLarge     // 14px, w500
style: AppTypography.labelMedium    // 12px, w500
style: AppTypography.labelSmall     // 11px, w500

// 特殊用途
style: AppTypography.buttonText     // 按钮文本
style: AppTypography.appBarTitle    // AppBar标题
style: AppTypography.chipText       // 标签文本
```

**❌ 错误做法**：
```dart
// 硬编码字体样式
style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)  // 错误
style: TextStyle(fontSize: 14)  // 错误

// 过度使用 copyWith
style: TextStyle(fontSize: 15, fontWeight: FontWeight.w400, height: 1.5)  // 错误
```

**样式微调**（仅在必要时使用）：
```dart
// ✅ 基于预定义样式微调
style: AppTypography.bodyMedium.copyWith(
  color: AppColors.getPrimary(context),  // 仅修改颜色
)

style: AppTypography.titleSmall.copyWith(
  fontWeight: FontWeight.w600,  // 仅增加字重
)

// ❌ 大量属性修改
style: AppTypography.bodyMedium.copyWith(
  fontSize: 14,        // 错误：改变了预定义尺寸
  fontWeight: FontWeight.w500,
  height: 1.3,
  letterSpacing: 0.5,
)
```

#### 4. 透明度系统 (Opacities)

**✅ 正确做法**：
```dart
// 使用预定义透明度
color: AppColors.getPrimary(context).withValues(alpha: Opacities.low)         // 10%
color: AppColors.getSurface(context).withValues(alpha: Opacities.medium)      // 20%
color: AppColors.getOutline(context).withValues(alpha: Opacities.mediumHigh)  // 25%
color: Colors.black.withValues(alpha: Opacities.extraLow)                     // 5%

// 可用透明度常量
Opacities.extraLow      // 0.05 (5%)
Opacities.low           // 0.1  (10%)
Opacities.mediumLow     // 0.15 (15%)
Opacities.medium        // 0.2  (20%)
Opacities.mediumHigh    // 0.25 (25%)
Opacities.high          // 0.3  (30%)
Opacities.half          // 0.5  (50%)
Opacities.mediumOpaque  // 0.8  (80%)
```

**❌ 错误做法**：
```dart
// 硬编码透明度
color: Colors.black.withValues(alpha: 0.15)  // 错误
color: AppColors.primary.withValues(alpha: 0.3)  // 错误
```

#### 5. 阴影系统 (AppShadows)

**✅ 正确做法**：
```dart
// 使用主题感知阴影
boxShadow: AppShadows.getXsShadow(context)  // 极小阴影
boxShadow: AppShadows.getSShadow(context)   // 小阴影
boxShadow: AppShadows.getMShadow(context)   // 中等阴影（卡片）
boxShadow: AppShadows.getLShadow(context)   // 大阴影（对话框）
boxShadow: AppShadows.getXlShadow(context)  // 特大阴影（模态框）

// 特定用途阴影
boxShadow: AppShadows.getCardShadow(context)
boxShadow: AppShadows.getButtonShadow(context)
```

**❌ 错误做法**：
```dart
// 硬编码阴影
boxShadow: [
  BoxShadow(
    color: Colors.black.withOpacity(0.1),  // 错误
    blurRadius: 8,
    offset: Offset(0, 2),
  ),
]
```

#### 6. 边框系统 (BorderStyles)

**✅ 正确做法**：
```dart
// 使用预定义边框样式
border: Border.all(
  color: AppColors.getOutline(context),
  width: BorderStyles.extraThin,  // 0.5px
)

border: Border.all(
  color: AppColors.getOutline(context),
  width: BorderStyles.thin,  // 1.0px
)

// 使用边框工具方法
border: BorderStyles.getTopBorder(
  AppColors.getOutline(context),
  opacity: Opacities.medium,
)

decoration: BorderStyles.getExtraThinBorderDecoration(
  AppColors.getOutlineVariant(context),
  radius: Dimensions.radiusS,
)
```

**❌ 错误做法**：
```dart
// 硬编码边框
border: Border.all(color: Colors.grey, width: 0.5)  // 错误
border: Border.all(color: Color(0xFFE0E0E0), width: 1)  // 错误
```

### 组件样式使用规范

#### 1. 按钮样式 (ButtonStyles)

**✅ 正确做法**：
```dart
// 主要按钮
ElevatedButton(
  style: ButtonStyles.getPrimaryStyle(context),
  onPressed: () {},
  child: Text('确认'),
)

// 次要按钮
ElevatedButton(
  style: ButtonStyles.getSecondaryStyle(context),
  onPressed: () {},
  child: Text('取消'),
)

// 轮廓按钮
OutlinedButton(
  style: ButtonStyles.getOutlinedStyle(context),
  onPressed: () {},
  child: Text('了解更多'),
)

// 文本按钮
TextButton(
  style: ButtonStyles.getTextStyle(context),
  onPressed: () {},
  child: Text('跳过'),
)

// 危险操作按钮
ElevatedButton(
  style: ButtonStyles.getDangerStyle(context),
  onPressed: () {},
  child: Text('删除'),
)
```

**❌ 错误做法**：
```dart
// 硬编码按钮样式
ElevatedButton(
  style: ElevatedButton.styleFrom(
    backgroundColor: Color(0xFF5E8BFF),  // 错误
    padding: EdgeInsets.symmetric(vertical: 12, horizontal: 16),  // 错误
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),  // 错误
  ),
  onPressed: () {},
  child: Text('按钮'),
)
```

#### 2. 输入框样式 (InputStyles)

**✅ 正确做法**：
```dart
// 标准输入框
TextField(
  decoration: InputStyles.getInputDecoration(
    context,
    hintText: '请输入内容',
  ),
)

// 搜索框
TextField(
  decoration: InputStyles.getSearchDecoration(
    context,
    hintText: '搜索文章...',
  ),
)

// 无边框输入框（日记等）
TextField(
  decoration: InputStyles.getCleanInputDecoration(
    context,
    hintText: '记录你的想法...',
  ),
)

// 标题输入框
TextField(
  style: AppTypography.titleMedium,
  decoration: InputStyles.getTitleInputDecoration(
    context,
    hintText: '输入标题',
  ),
)
```

**❌ 错误做法**：
```dart
// 硬编码输入框样式
TextField(
  decoration: InputDecoration(
    hintText: '输入内容',
    filled: true,
    fillColor: Color(0xFFF0F0F0),  // 错误
    border: OutlineInputBorder(
      borderRadius: BorderRadius.circular(10),  // 错误
    ),
    contentPadding: EdgeInsets.all(14),  // 错误
  ),
)
```

### StyleGuide 高级应用

`StyleGuide` 提供了更高级的样式应用方法，推荐优先使用：

#### 1. 容器装饰

**✅ 推荐做法**：
```dart
// 页面容器
Container(
  decoration: StyleGuide.getPageContainerDecoration(context),
  child: content,
)

// 卡片
Container(
  decoration: StyleGuide.getCardDecoration(context),
  child: content,
)

// 列表项
Container(
  decoration: StyleGuide.getListItemDecoration(context),
  child: content,
)
```

#### 2. 状态组件

**✅ 推荐做法**：
```dart
// 空状态
StyleGuide.getEmptyState(
  context,
  message: '暂无数据',
  icon: Icons.inbox_outlined,
  action: ElevatedButton(
    onPressed: onRefresh,
    child: Text('刷新'),
  ),
)

// 加载状态
StyleGuide.getLoadingState(context, message: '加载中...')

// 错误状态
StyleGuide.getErrorState(
  context,
  message: '加载失败',
  onRetry: onRetry,
)
```

#### 3. 页面布局

**✅ 推荐做法**：
```dart
// 标准页面布局
StyleGuide.getStandardPageLayout(
  context: context,
  child: content,
  hasPadding: true,
)

// 列表布局
StyleGuide.getStandardListLayout(
  context: context,
  children: listItems,
)

// 网格布局
StyleGuide.getStandardGridLayout(
  context: context,
  children: gridItems,
  crossAxisCount: 2,
)
```

### 样式系统导入规范

**✅ 唯一正确的导入方式**：
```dart
import 'package:daily_satori/app/styles/index.dart';

// index.dart 已经导出所有样式相关类：
// - AppColors
// - Dimensions
// - AppTypography
// - Opacities
// - AppShadows
// - BorderStyles
// - ButtonStyles
// - InputStyles
// - StyleGuide
// - 等等...
```

**❌ 错误的导入方式**：
```dart
// 不要单独导入
import 'package:daily_satori/app/styles/base/colors.dart';  // 错误
import 'package:daily_satori/app/styles/base/dimensions.dart';  // 错误
import 'package:daily_satori/app/styles/components/button_styles.dart';  // 错误
```

### 完整示例：符合规范的页面

```dart
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/index.dart';

class ExampleView extends StatelessWidget {
  const ExampleView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.getBackground(context),
      appBar: AppBar(
        title: Text('示例页面', style: AppTypography.appBarTitle),
      ),
      body: SingleChildScrollView(
        padding: Dimensions.paddingPage,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 页面标题
            Text('欢迎使用', style: AppTypography.headingMedium),
            Dimensions.verticalSpacerS,

            // 页面描述
            Text(
              '这是一个符合样式规范的页面示例',
              style: AppTypography.bodyMedium.copyWith(
                color: AppColors.getOnSurfaceVariant(context),
              ),
            ),
            Dimensions.verticalSpacerL,

            // 卡片容器
            Container(
              padding: Dimensions.paddingCard,
              decoration: StyleGuide.getCardDecoration(context),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('卡片标题', style: AppTypography.titleMedium),
                  Dimensions.verticalSpacerS,
                  Text(
                    '卡片内容描述',
                    style: AppTypography.bodyMedium,
                  ),
                ],
              ),
            ),
            Dimensions.verticalSpacerL,

            // 输入框
            TextField(
              decoration: InputStyles.getInputDecoration(
                context,
                hintText: '请输入内容',
              ),
            ),
            Dimensions.verticalSpacerL,

            // 按钮组
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    style: ButtonStyles.getOutlinedStyle(context),
                    onPressed: () {},
                    child: Text('取消', style: AppTypography.buttonText),
                  ),
                ),
                Dimensions.horizontalSpacerM,
                Expanded(
                  child: ElevatedButton(
                    style: ButtonStyles.getPrimaryStyle(context),
                    onPressed: () {},
                    child: Text('确认', style: AppTypography.buttonText),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
```

### 样式系统最佳实践

1. **优先级顺序**：
   - 第一优先：`StyleGuide` 高级方法
   - 第二优先：组件样式类 (`ButtonStyles`, `InputStyles` 等)
   - 第三优先：基础 Tokens (`Dimensions`, `AppColors`, `AppTypography`)
   - 最后手段：`copyWith()` 微调（必须基于预定义样式）

2. **主题适配**：
   - 所有颜色必须通过 `AppColors.getXxx(context)` 获取
   - 所有阴影必须通过 `AppShadows.getXxxShadow(context)` 获取
   - 避免硬编码任何颜色值

3. **可维护性**：
   - 发现需要重复使用的样式组合时，添加到相应的样式类中
   - 不要在多个页面中复制相同的样式代码
   - 新增常用数值时，添加到 `Dimensions` 或相应常量类

4. **扩展性**：
   - 需要新的按钮变体时，添加到 `ButtonStyles`
   - 需要新的输入框样式时，添加到 `InputStyles`
   - 需要新的布局模式时，添加到 `StyleGuide`

## 📝 代码质量约束

### 强制代码分析检查

**每次代码更改后必须执行 flutter analyze**

```bash
# 每次修改代码后必须执行此命令
flutter analyze

# 如果发现任何问题，必须立即修复
# 确保输出为：No issues found!
```

**执行要求**：
- 每次修改代码后必须立即执行 `flutter analyze`
- 发现任何 error、warning 或 info 都必须立即修复
- 修复完成后必须再次执行 `flutter analyze` 确认无问题
- 只有当输出显示 "No issues found!" 时才能继续下一步开发
- 在提交代码前必须最终执行一次 `flutter analyze` 确认

**修复流程**：
```bash
# 1. 执行分析
flutter analyze

# 2. 如果发现问题，逐个修复
# 根据分析输出修复所有 error、warning 和 info

# 3. 验证修复结果
flutter analyze

# 4. 确认输出为 "No issues found!" 后继续
```

## 🎉 GetX 优化收益

通过遵循上述 GetX 最佳实践，我们获得了以下收益：

1. **更好的可维护性**: 控制器解耦，状态管理集中化
2. **更强的可测试性**: 依赖注入使单元测试更容易
3. **更好的用户体验**: 统一的加载状态和错误处理
4. **更好的开发体验**: 类型安全的导航和统一的API
5. **更好的性能**: GetX 的智能依赖管理和响应式更新
6. **更好的扩展性**: 事件总线模式支持新功能的无缝集成
7. **更好的架构**: 事件驱动的状态同步，避免循环依赖

## 🔍 检查清单

在提交代码前，必须检查以下约束：

### 架构约束
- [ ] 是否继承 `BaseGetXController`
- [ ] 是否使用状态服务而非直接控制器查找
- [ ] 是否使用事件总线模式进行跨页面状态同步
- [ ] 是否使用 `NavigationService` 进行导航
- [ ] 是否在 `ServiceRegistry` 中注册服务

### GetX 最佳实践
- [ ] 是否使用 `.obs` 使变量可观察
- [ ] 是否使用 `Obx()` 更新 UI
- [ ] 是否使用 `Get.put()` 或 `Get.lazyPut()` 注册依赖
- [ ] 是否避免控制器之间直接相互查找
- [ ] 是否使用状态服务进行跨页面状态共享
- [ ] 是否使用事件总线模式替代直接状态更新
- [ ] 是否明确定义事件类型和检查方法

### 代码质量检查
- [ ] 是否执行了 `flutter analyze` 检查
- [ ] 是否修复了所有 error、warning 和 info
- [ ] 是否确认输出为 "No issues found!"
- [ ] 是否使用 `safeExecute()` 处理异步操作
- [ ] 是否使用响应式变量（`.obs`）
- [ ] 是否使用 `Obx()` 包装动态 UI
- [ ] 是否使用统一的消息方法

### 样式系统检查
- [ ] 是否使用 `import 'package:daily_satori/app/styles/index.dart';` 导入样式
- [ ] 是否使用 `Dimensions` 常量而非硬编码数值
- [ ] 是否使用 `AppColors.getXxx(context)` 而非硬编码颜色
- [ ] 是否使用 `AppTypography` 字体样式而非 `TextStyle(...)`
- [ ] 是否使用 `Opacities` 常量而非硬编码透明度
- [ ] 是否使用 `AppShadows.getXxxShadow(context)` 而非硬编码阴影
- [ ] 是否使用 `BorderStyles` 常量而非硬编码边框
- [ ] 是否使用 `ButtonStyles.getXxxStyle(context)` 定义按钮
- [ ] 是否使用 `InputStyles.getXxxDecoration(context)` 定义输入框
- [ ] 是否优先使用 `StyleGuide` 高级方法
- [ ] 是否使用统一的间距系统 (`verticalSpacerS/M/L/Xl`)
- [ ] 是否使用统一的圆角系统 (`radiusS/M/L`)
- [ ] 是否使用统一的图标尺寸系统 (`iconSizeS/M/L/Xl`)
- [ ] 是否避免在页面中直接写 `EdgeInsets`、`BorderRadius`、`Color`
- [ ] 是否避免使用 `copyWith` 大量修改预定义样式
- [ ] 新增重复样式是否添加到样式系统而非复制代码

### 功能约束检查
- [ ] 读书页 FAB 是否始终显示且行为正确
- [ ] 备份恢复后图片路径是否自动修复
- [ ] 是否正确处理时间的 UTC 存储和本地化显示
- [ ] 是否避免在日志中输出敏感信息

### 命名规范
- [ ] 文件名是否符合约束
- [ ] 类名是否符合约束
- [ ] 方法名是否符合约束

## ⚠️ 违规后果

违反这些约束将导致：
1. **代码审查不通过**
2. **PR 被拒绝**
3. **需要重构后重新提交**

### 特别注意：代码质量检查违规
- **未执行 `flutter analyze` 的代码将直接被拒绝**
- **存在任何 error、warning 或 info 的 PR 将被拒绝**
- **必须提供 "No issues found!" 的分析结果作为通过条件**

## 🔄 变更管理

### 功能约束变更
- 修改读书页 FAB 或相关控制器时，必须保证上述读书页行为不变
- 如需临时移除或修改，请先在此文件更新约束，并在 PR 描述中说明原因与回滚计划

### 文档维护
- 改动涉及架构、服务、功能时，需同步更新本文档
- 新增模块或服务需补充到相应章节
- 确保文档与代码实现保持一致

## 📚 参考资料

- [GetX 官方文档](https://github.com/jonataslaw/getx/blob/master/README.zh-cn.md)
- [Flutter 官方文档](https://flutter.dev/docs)
- [Dart 语言规范](https://dart.dev/guides)
- [ObjectBox Flutter 文档](https://docs.objectbox.io/flutter)

---

**注意**: 这些约束是为了保证代码质量、架构一致性和功能稳定性，所有开发者必须严格遵守。如有疑问，请在开发前讨论确认。
