import 'package:daily_satori/app_exports.dart';

/// 导航服务
///
/// 集中管理应用导航逻辑，提供类型安全的导航方法，
/// 并支持页面间参数传递和状态管理
class NavigationService extends GetxService {
  /// 当前页面路由名称
  final RxString currentRoute = ''.obs;

  /// 导航历史栈
  final RxList<String> navigationHistory = <String>[].obs;

  /// 导航到指定页面
  Future<T?>? toNamed<T>(
    String page, {
    Object? arguments,
    bool preventDuplicates = true,
    Map<String, String>? parameters,
  }) {
    currentRoute.value = page;
    navigationHistory.add(page);

    logger.i('导航到页面: $page, 参数: $arguments');
    return Get.toNamed<T>(
      page,
      arguments: arguments,
      preventDuplicates: preventDuplicates,
      parameters: parameters,
    );
  }

  /// 替换当前页面
  Future<T?>? offNamed<T>(
    String page, {
    Object? arguments,
    Map<String, String>? parameters,
  }) {
    currentRoute.value = page;
    navigationHistory.clear();
    navigationHistory.add(page);

    logger.i('替换页面: $page, 参数: $arguments');
    return Get.offNamed<T>(
      page,
      arguments: arguments,
      parameters: parameters,
    );
  }

  /// 替换所有页面
  Future<T?>? offAllNamed<T>(
    String page, {
    Object? arguments,
    Map<String, String>? parameters,
  }) {
    currentRoute.value = page;
    navigationHistory.clear();
    navigationHistory.add(page);

    logger.i('替换所有页面: $page, 参数: $arguments');
    return Get.offAllNamed<T>(
      page,
      arguments: arguments,
      parameters: parameters,
    );
  }

  /// 返回上一页
  void back<T>([T? result]) {
    if (navigationHistory.isNotEmpty) {
      navigationHistory.removeLast();
      if (navigationHistory.isNotEmpty) {
        currentRoute.value = navigationHistory.last;
      } else {
        currentRoute.value = '';
      }
    }

    logger.i('返回上一页，结果: $result');
    Get.back(result: result);
  }

  /// 检查是否可以返回
  bool canGoBack() {
    return navigationHistory.length > 1;
  }

  /// 获取导航历史
  List<String> getNavigationHistory() {
    return List.unmodifiable(navigationHistory);
  }

  /// 清空导航历史
  void clearNavigationHistory() {
    navigationHistory.clear();
    currentRoute.value = '';
    logger.i('清空导航历史');
  }

  /// 文章相关的便捷导航方法

  /// 导航到文章列表
  void toArticles() {
    toNamed(Routes.articles);
  }

  /// 导航到文章详情
  void toArticleDetail(dynamic article) {
    toNamed(Routes.articleDetail, arguments: article);
  }

  /// 日记相关的便捷导航方法

  /// 导航到日记页面
  void toDiary() {
    toNamed(Routes.diary);
  }

  /// 设置相关的便捷导航方法

  /// 导航到设置页面
  void toSettings() {
    toNamed(Routes.settings);
  }

  /// 导航到AI配置页面
  void toAIConfig() {
    toNamed(Routes.aiConfig);
  }

  /// 导航到AI配置编辑页面
  void toAIConfigEdit(dynamic config) {
    toNamed(Routes.aiConfigEdit, arguments: config);
  }

  /// 备份相关的便捷导航方法

  /// 导航到备份恢复页面
  void toBackupRestore() {
    toNamed(Routes.backupRestore);
  }

  /// 导航到备份设置页面
  void toBackupSettings() {
    toNamed(Routes.backupSettings);
  }

  /// 其他便捷导航方法

  /// 导航到主页
  void toHome() {
    offAllNamed(Routes.home);
  }

  /// 导航到插件中心
  void toPluginCenter() {
    toNamed(Routes.pluginCenter);
  }

  /// 导航到书籍页面
  void toBooks() {
    toNamed(Routes.books);
  }

  /// 显示分享对话框
  void toShareDialog(dynamic data) {
    toNamed(Routes.shareDialog, arguments: data);
  }

  @override
  void onInit() {
    super.onInit();
    // 注意：中间件暂时禁用，因为 GetX API 可能有变化
    // Get.routing.addMiddleware(_navigationMiddleware());
    logger.i('NavigationService 初始化完成');
  }

  // GetX 路由中间件配置
  // 注意：这里暂时注释掉，因为 GetX API 可能有变化
  /*
  GetMiddleware _navigationMiddleware() {
    return GetMiddleware(
      onPageCalled: (route) {
        final routeName = route?.settings.name;
        if (routeName != null && routeName != currentRoute.value) {
          currentRoute.value = routeName;
          if (!navigationHistory.contains(routeName)) {
            navigationHistory.add(routeName);
          }
        }
        return route;
      },
    );
  }
  */

  @override
  void onClose() {
    navigationHistory.close();
    currentRoute.close();
    super.onClose();
    logger.i('NavigationService 已关闭');
  }
}