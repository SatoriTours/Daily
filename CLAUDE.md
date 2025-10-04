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
- **UI 框架**: 自定义主题 `AppTheme`, `app/styles/theme`, `components/*`

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
