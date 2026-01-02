# go_router 迁移指南

## 概述

项目已成功从传统的 `MaterialApp` + `onGenerateRoute` 方式迁移到 `go_router`，使路由管理更加现代化和简洁。

## 主要变更

### 1. 路由配置 (`lib/app/routes/app_router.dart`)

创建了新的路由配置文件，使用 `GoRouter` 管理所有路由：

```dart
final GoRouter appRouter = GoRouter(
  navigatorKey: rootNavigatorKey,
  initialLocation: Routes.home,
  routes: [
    GoRoute(
      path: Routes.home,
      name: RouteNames.home,
      builder: (context, state) => const HomeView(),
    ),
    // ... 其他路由
  ],
);
```

### 2. 应用入口 (`lib/main.dart`)

- 将 `MaterialApp` 替换为 `MaterialApp.router`
- 使用 `routerConfig` 参数传入 `appRouter`

```dart
MaterialApp.router(
  title: 'Daily Satori',
  theme: AppTheme.light,
  darkTheme: AppTheme.dark,
  themeMode: ThemeMode.system,
  routerConfig: appRouter,
)
```

### 3. 导航服务 (`lib/app/navigation/app_navigation.dart`)

重构了 `AppNavigation` 类，保留原有 API 但内部使用 `go_router`：

- `toNamed()` - 使用 `appRouter.push()`
- `back()` - 使用 `appRouter.pop()`
- `offNamed()` - 使用 `appRouter.pushReplacement()`
- `offAllNamed()` - 使用 `appRouter.go()`

### 4. 参数传递更新

需要传递参数的页面已更新为使用 `GoRouterState.of(context).extra`：

- `ArticleDetailView` - 文章详情页
- `ShareDialogView` - 分享对话框
- `AIConfigEditView` - AI 配置编辑页

**之前的方式：**
```dart
final args = ModalRoute.of(context)?.settings.arguments;
```

**现在的方式：**
```dart
final state = GoRouterState.of(context);
final args = state.extra;
```

## 使用方式

### 基本导航

```dart
// 导航到命名路由
AppNavigation.toNamed(Routes.settings);

// 导航并传递参数
AppNavigation.toNamed(Routes.articleDetail, arguments: articleId);

// 返回上一页
AppNavigation.back();

// 替换当前路由
AppNavigation.offNamed(Routes.home);

// 清空所有路由并导航
AppNavigation.offAllNamed(Routes.home);
```

### 在页面中接收参数

```dart
@override
void didChangeDependencies() {
  super.didChangeDependencies();
  final state = GoRouterState.of(context);
  final arguments = state.extra;
  // 使用参数...
}
```

## go_router 最佳实践

### 1. 使用命名路由

所有路由都配置了 `name` 属性，使用 `RouteNames` 常量：

```dart
GoRoute(
  path: Routes.home,
  name: RouteNames.home,
  builder: (context, state) => const HomeView(),
)
```

### 2. 参数传递使用 extra

go_router 推荐使用 `extra` 参数传递复杂对象：

```dart
context.push(Routes.articleDetail, extra: article);
```

### 3. 全局 Navigator Key

保留了全局 Navigator Key，用于显示对话框等场景：

```dart
final GlobalKey<NavigatorState> rootNavigatorKey = GlobalKey<NavigatorState>();
```

### 4. 调试支持

路由配置中启用了调试日志（开发时）：

```dart
GoRouter(
  debugLogDiagnostics: false, // 生产环境关闭
  // ...
)
```

## 兼容性说明

### 保持向后兼容

`AppNavigation` 类的 API 保持不变，现有代码无需大规模修改：

- ✅ `AppNavigation.toNamed()` 仍然可用
- ✅ `AppNavigation.back()` 仍然可用
- ✅ 所有路由常量 (`Routes.*`) 保持不变

### 需要更新的地方

仅有 3 个页面需要更新参数接收方式：

1. `ArticleDetailView`
2. `ShareDialogView`
3. `AIConfigEditView`

## 优势

### 1. 声明式路由

- 所有路由集中管理，易于维护
- 路由配置更清晰

### 2. 类型安全

- go_router 提供更好的类型检查
- 减少运行时错误

### 3. 深度链接支持

- 原生支持 Web URL
- 更容易实现 Deep Links

### 4. 现代化

- 符合 Flutter 官方推荐
- 活跃的社区支持

## 测试

迁移后已通过以下测试：

- ✅ `flutter analyze` - 无警告无错误
- ✅ 所有页面导航正常
- ✅ 参数传递功能正常
- ✅ 返回导航正常

## 参考资源

- [go_router 官方文档](https://pub.dev/documentation/go_router/latest/)
- [Flutter 路由最佳实践](https://docs.flutter.dev/ui/navigation)
