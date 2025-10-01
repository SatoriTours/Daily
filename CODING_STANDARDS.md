# Daily Satori 编码规范与约束

本文档定义了 Daily Satori 项目的编码标准、架构约束和最佳实践要求，所有代码必须严格遵守这些规范。

## 🎯 核心原则

### 1. 架构分离原则
- **UI层**: 只负责界面展示和用户交互
- **逻辑层**: 业务逻辑处理和数据转换
- **服务层**: 数据持久化和外部服务调用
- **路由层**: 页面导航和参数传递

### 2. GetX 最佳实践约束
**禁止直接使用 Get.find() 查找其他控制器**
```dart
// ❌ 禁止：紧耦合的控制器查找
if (Get.isRegistered<ArticlesController>()) {
  final ac = Get.find<ArticlesController>();
  ac.updateArticle(id);
}

// ✅ 正确：通过状态服务解耦
_articleStateService.notifyArticleUpdated(article);
```

**必须使用状态服务进行跨页面状态共享**
```dart
// ✅ 正确：使用状态服务
_articleStateService.setActiveArticle(article);
_diaryStateService.setGlobalTagFilter(tag);
_appStateService.showGlobalSuccess('操作成功');
```

## 📁 目录结构约束

```
lib/app/
├── controllers/           # GetX 控制器基类和通用控制器
├── modules/               # 功能模块
│   └── [feature]/
│       ├── controllers/   # 功能控制器
│       ├── views/         # 页面视图
│       ├── bindings/      # 依赖注入绑定
│       └── models/        # 数据模型
├── services/              # 全局服务
│   ├── state/            # 状态管理服务
│   └── [service].dart    # 具体服务实现
├── repositories/          # 数据仓库层
├── components/            # 可复用组件
└── utils/                 # 工具类
```

## 🏗️ GetX 架构约束

### 1. 控制器规范
**必须继承 BaseGetXController**
```dart
// ✅ 正确
class ArticlesController extends BaseGetXController {
  // 实现
}

// ❌ 错误
class ArticlesController extends GetxController {
  // 不允许直接继承 GetxController
}
```

**必须使用响应式变量**
```dart
// ✅ 正确
final isLoading = false.obs;
final articles = <ArticleModel>[].obs;

// ❌ 错误
bool isLoading = false;
List<ArticleModel> articles = [];
```

### 2. 状态管理约束
**跨页面状态必须使用状态服务**
```dart
// ✅ 正确：使用状态服务管理全局状态
class ArticleStateService extends GetxService {
  final Rxn<ArticleModel> activeArticle = Rxn<ArticleModel>();
  final RxString globalSearchQuery = ''.obs;

  void setActiveArticle(ArticleModel article) {
    activeArticle.value = article;
  }
}

// ❌ 错误：在控制器中管理全局状态
class ArticlesController extends BaseGetXController {
  static ArticleModel? globalActiveArticle; // 禁止静态全局变量
}
```

### 3. 依赖注入约束
**必须使用现代 GetX API**
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

### 4. 导航约束
**必须使用 NavigationService 进行导航**
```dart
// ✅ 正确：使用导航服务
_navigationService.toArticleDetail(article);
_navigationService.back();

// ❌ 错误：直接使用 Get.toNamed()
Get.toNamed(Routes.articleDetail, arguments: article);
```

## 🔧 错误处理约束

### 1. 异步操作约束
**必须使用 safeExecute() 方法**
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
);

// ❌ 错误：直接 try-catch
try {
  final result = await someAsyncOperation();
  showSuccess("成功");
} catch (e) {
  showError("失败: $e");
}
```

### 2. 用户反馈约束
**必须使用统一的消息方法**
```dart
// ✅ 正确：使用统一反馈
showError("操作失败");
showSuccess("保存成功");
showLoading("处理中...");

// ❌ 错误：直接使用 UI 工具
UIUtils.showError("失败");
errorNotice("错误");
```

## 📋 数据流约束

### 1. 数据更新模式
**必须通过状态服务通知更新**
```dart
// ✅ 正确：状态服务通知
void updateArticle(ArticleModel article) {
  await ArticleRepository.update(article);
  _articleStateService.notifyArticleUpdated(article);
}

// ❌ 错误：直接查找其他控制器更新
void updateArticle(ArticleModel article) {
  await ArticleRepository.update(article);
  if (Get.isRegistered<ArticlesController>()) {
    Get.find<ArticlesController>().updateArticle(article.id);
  }
}
```

### 2. 数据监听模式
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

## 🎨 UI 约束

### 1. 响应式 UI 约束
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

### 2. 状态显示约束
**必须使用响应式状态**
```dart
// ✅ 正确：直接绑定响应式变量
Obx(() => isLoading.value ? CircularProgressIndicator() : Content())

// ❌ 错误：通过控制器方法获取状态
Obx(() => controller.isLoading() ? CircularProgressIndicator() : Content())
```

## 📝 代码质量约束

### 1. 强制代码分析检查
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

### 2. 方法命名约束
- 状态设置方法：`setXxx()` 或 `updateXxx()`
- 状态获取方法：直接使用响应式变量
- 事件处理方法：`handleXxx()` 或 `onXxx()`

### 3. 文件命名约束
- 控制器：`xxx_controller.dart`
- 视图：`xxx_view.dart`
- 绑定：`xxx_binding.dart`
- 服务：`xxx_service.dart`
- 模型：`xxx_model.dart`

### 4. 类命名约束
- 控制器：`XxxController`
- 视图：`XxxView`
- 绑定：`XxxBinding`
- 服务：`XxxService`
- 模型：`XxxModel`

## 🔍 检查清单

在提交代码前，必须检查以下约束：

### 架构约束
- [ ] 是否继承 `BaseGetXController`
- [ ] 是否使用状态服务而非直接控制器查找
- [ ] 是否使用 `NavigationService` 进行导航
- [ ] 是否在 `ServiceRegistry` 中注册服务

### 代码质量检查
- [ ] 是否执行了 `flutter analyze` 检查
- [ ] 是否修复了所有 error、warning 和 info
- [ ] 是否确认输出为 "No issues found!"
- [ ] 是否使用 `safeExecute()` 处理异步操作
- [ ] 是否使用响应式变量（`.obs`）
- [ ] 是否使用 `Obx()` 包装动态 UI
- [ ] 是否使用统一的消息方法

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

## 📚 参考资料

- [GetX 官方文档](https://github.com/jonataslaw/getx/blob/master/README.zh-cn.md)
- [项目架构设计文档](./GETX_OPTIMIZATION_SUMMARY.md)

---

**注意**: 这些约束是为了保证代码质量和架构一致性，所有开发者必须严格遵守。如有疑问，请在开发前讨论确认。