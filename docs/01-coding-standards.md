# 📋 Daily Satori 编码规范

> 所有 AI 工具生成的代码必须遵循本规范。

## 📚 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | Flutter 3.32.x / Dart 3.8.x |
| 状态管理 | Riverpod 3.0 + freezed |
| 本地存储 | ObjectBox |
| 导航 | go_router 14.x |
| AI | openai_dart |

## 🏗️ 项目架构

| 层级 | 路径 | 职责 |
|------|------|------|
| 界面层 | `pages/*/views` | ConsumerWidget |
| 控制层 | `pages/*/providers` | 页面级 Provider |
| 状态层 | `providers/*` | 全局状态 |
| 服务层 | `services/*` | 跨模块服务 |
| 数据层 | `data/*` | 模型 + 仓储 |

## 🎯 Riverpod 架构（核心）

> 详见 [Riverpod 最佳实践](./06-riverpod-style-guide.md)

### 必须遵守

- ✅ `@riverpod` 注解 + 代码生成
- ✅ `freezed` 定义不可变状态
- ✅ `ConsumerWidget` + `ref.watch()` 构建 UI
- ✅ 事件回调中使用 `ref.read()`
- ✅ `AsyncValue.guard()` 包装异步操作

### 严禁

- ❌ GetX 模式 (`.obs`, `Obx`, `Get.find`)
- ❌ Provider 循环依赖
- ❌ 在 `build()` 中使用 `ref.read()`
- ❌ View 中直接调用 Repository/Service（应通过 State Provider）
- ❌ Controller 中定义 getter 方法（应使用 State getter 或派生 Provider）
- ❌ freezed 模型中调用 Service/Repository（应将数据作为字段存储）
- ❌ 使用 `dynamic` 类型（应使用明确类型）

## 🎨 样式系统

> 详见 [样式指南](./04-style-guide.md)

```dart
// ✅ 唯一导入方式
import 'package:daily_satori/app/styles/index.dart';

// ✅ 使用主题感知方法
AppColors.getPrimary(context)
Dimensions.paddingCard
AppTypography.bodyMedium
ButtonStyles.getPrimaryStyle(context)

// ❌ 禁止硬编码
Color(0xFF5E8BFF)    // 禁止
EdgeInsets.all(16)   // 禁止
TextStyle(fontSize: 14)  // 禁止
```

## 📝 代码质量

### 强制约束

| 约束 | 限制 |
|------|------|
| 函数长度 | ≤ 50 行 |
| 缩进层数 | ≤ 3 层 |
| 分析检查 | `flutter analyze` 无错误 |

### 命名约定

| 类型 | 风格 | 示例 |
|------|------|------|
| 文件 | snake_case | `article_controller.dart` |
| 类 | PascalCase | `ArticleController` |
| 方法/变量 | camelCase | `sendMessage()` |
| 常量 | SCREAMING_SNAKE_CASE | `MAX_COUNT` |

### 日志规范

```dart
logger.d('[ClassName] 操作描述');  // 调试
logger.i('[ClassName] 用户操作');  // 信息
logger.w('[ClassName] 警告');      // 警告
logger.e('[ClassName] 错误', error: e);  // 错误
```

## 🔧 数据访问

```dart
// ✅ 仓储静态方法
final articles = ArticleRepository.getAll();
ArticleRepository.save(article);

// ✅ 时间管理
article.createdAt = DateTime.now().toUtc();  // 存储 UTC
DateTimeUtils.formatDateTimeToLocal(...)     // 展示本地化

// ✅ 用户反馈
showError('错误信息');
showSuccess('操作成功');
```

## 🧭 路由导航规范

> 项目使用 `go_router` 进行路由管理

### 导航方式

```dart
// ✅ 推荐：使用 AppNavigation 统一导航
import 'package:daily_satori/app/navigation/app_navigation.dart';
import 'package:daily_satori/app/routes/app_routes.dart';

// 跳转到命名路由
AppNavigation.toNamed(Routes.settings);

// 跳转并传递参数
AppNavigation.toNamed(Routes.articleDetail, arguments: article);

// 返回上一页
AppNavigation.back();

// 替换当前路由
AppNavigation.offNamed(Routes.home);

// 清空所有路由并跳转
AppNavigation.offAllNamed(Routes.home);
```

### 接收路由参数

```dart
import 'package:go_router/go_router.dart';

@override
void didChangeDependencies() {
  super.didChangeDependencies();
  // ✅ 使用 GoRouterState 接收参数
  final state = GoRouterState.of(context);
  final arguments = state.extra;

  if (arguments != null) {
    // 使用参数...
  }
}
```

### 路由配置

所有路由在 `lib/app/routes/app_router.dart` 中集中配置：

```dart
GoRoute(
  path: Routes.home,
  name: RouteNames.home,
  builder: (context, state) => const HomeView(),
)
```

### 严禁

- ❌ 直接使用 `Navigator.pushNamed`（除对话框外）
- ❌ 使用 `ModalRoute.of(context)?.settings.arguments` 获取参数
- ❌ 硬编码路由路径字符串（应使用 `Routes.*` 常量）

### 对话框导航

对话框和底部表单应使用 `AppNavigation.back()`：

```dart
// ✅ 关闭对话框
showDialog(
  context: context,
  builder: (context) => AlertDialog(
    actions: [
      TextButton(
        onPressed: () => AppNavigation.back(),  // ✅ 正确做法
        child: Text('关闭'),
      ),
    ],
  ),
);
```

## ⚠️ 安全与隐私

- ✅ 敏感信息存储于 `SettingRepository`
- ❌ 禁止在日志中输出 Token/口令

## ✅ 检查清单

- [ ] Provider 使用 `@riverpod` 注解
- [ ] 状态类使用 `@freezed` 注解
- [ ] `ref.watch()` 在 build 中，`ref.read()` 在事件中
- [ ] 导入 `app/styles/index.dart`
- [ ] 无硬编码颜色/间距/字体
- [ ] 函数 ≤ 50 行，缩进 ≤ 3 层
- [ ] `flutter analyze` 无问题
- [ ] View 中不直接调用 Repository/Service
- [ ] freezed 模型中的 getter 只做纯计算
- [ ] 无 `dynamic` 类型，使用明确类型
- [ ] 使用 `AppNavigation` 进行路由跳转
- [ ] 使用 `GoRouterState.of(context).extra` 接收路由参数
- [ ] 路由路径使用 `Routes.*` 常量
