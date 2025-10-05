import 'package:daily_satori/app_exports.dart';

/// 插件中心控制器
///
/// 负责管理插件中心页面的UI状态和用户交互
/// 数据管理由PluginCenterStateService负责
class PluginCenterController extends GetxController {
  // MARK: - 状态服务

  late final PluginCenterStateService _pluginCenterStateService;

  // MARK: - 数据引用

  /// 插件列表 - 引用自StateService
  RxList<PluginInfo> get plugins => _pluginCenterStateService.plugins;

  /// 插件服务器地址 - 引用自StateService
  RxString get pluginServerUrl => _pluginCenterStateService.pluginServerUrl;

  /// 是否正在加载 - 引用自StateService
  RxBool get isLoading => _pluginCenterStateService.isLoading;

  /// 正在更新的插件名 - 引用自StateService
  RxString get updatingPlugin => _pluginCenterStateService.updatingPlugin;

  // MARK: - 生命周期方法

  @override
  void onInit() {
    super.onInit();
    _initStateService();
    _pluginCenterStateService.loadPluginData();
  }

  void _initStateService() {
    _pluginCenterStateService = Get.find<PluginCenterStateService>();
  }

  // MARK: - 数据操作（委托给StateService）

  /// 加载插件数据
  Future<void> loadPluginData() async {
    await _pluginCenterStateService.loadPluginData();
  }

  /// 更新插件服务器URL
  Future<void> updateServerUrl(String url) async {
    await _pluginCenterStateService.updateServerUrl(url);
  }

  /// 强制更新单个插件
  Future<bool> updatePlugin(String fileName) async {
    return await _pluginCenterStateService.updatePlugin(fileName);
  }

  /// 更新所有插件
  Future<bool> updateAllPlugins() async {
    return await _pluginCenterStateService.updateAllPlugins();
  }

  // MARK: - UI辅助方法

  /// 获取更新时间显示文本
  String getUpdateTimeText(DateTime? dateTime) {
    if (dateTime == null) {
      return '从未更新';
    }

    final now = DateTime.now();
    final difference = now.difference(dateTime);

    if (difference.inMinutes < 1) {
      return '刚刚';
    } else if (difference.inHours < 1) {
      return '${difference.inMinutes}分钟前';
    } else if (difference.inDays < 1) {
      return '${difference.inHours}小时前';
    } else if (difference.inDays < 30) {
      return '${difference.inDays}天前';
    } else {
      return '${dateTime.year}-${dateTime.month.toString().padLeft(2, '0')}-${dateTime.day.toString().padLeft(2, '0')}';
    }
  }
}
