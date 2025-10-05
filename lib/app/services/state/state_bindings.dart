import 'package:daily_satori/app_exports.dart';

/// 全局状态服务绑定类
///
/// 负责在应用启动时初始化所有状态管理服务，
/// 并使用 GetX 的依赖注入系统进行管理
class StateServicesBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [
      Bind.put<AppStateService>(AppStateService()),
      Bind.put<ArticleStateService>(ArticleStateService()),
      Bind.put<DiaryStateService>(DiaryStateService()),
      Bind.put<BooksStateService>(BooksStateService()),
      Bind.put<AIConfigStateService>(AIConfigStateService()),
      Bind.put<PluginCenterStateService>(PluginCenterStateService()),
    ];
  }
}

/// 状态服务初始化器
///
/// 提供便捷方法来初始化状态服务
class StateServiceInitializer {
  static Future<void> init() async {
    // 创建并注册状态服务
    Get.put(AppStateService());
    Get.put(ArticleStateService());
    Get.put(DiaryStateService());
    Get.put(BooksStateService());
    Get.put(AIConfigStateService());
    Get.put(PluginCenterStateService());

    // 记录初始化完成
    logger.i('状态服务初始化完成');
  }
}
