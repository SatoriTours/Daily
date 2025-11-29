import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/styles/index.dart';

/// 插件中心状态服务
///
/// 作为插件数据的唯一数据源，管理插件列表和服务器配置
class PluginCenterStateService extends GetxService {
  // MARK: - 可观察属性

  /// 插件列表
  final RxList<PluginInfo> plugins = <PluginInfo>[].obs;

  /// 插件服务器地址
  final RxString pluginServerUrl = ''.obs;

  /// 是否正在加载
  final RxBool isLoading = false.obs;

  /// 正在更新的插件名
  final RxString updatingPlugin = ''.obs;

  // MARK: - 生命周期方法

  @override
  void onInit() {
    super.onInit();
    logger.i('PluginCenterStateService 初始化');
  }

  @override
  void onClose() {
    plugins.clear();
    logger.i('PluginCenterStateService 已关闭');
    super.onClose();
  }

  // MARK: - 数据加载

  /// 加载插件数据
  Future<void> loadPluginData() async {
    isLoading.value = true;

    try {
      // 加载插件服务器URL
      pluginServerUrl.value = PluginService.i.getPluginServerUrl();

      // 加载插件列表
      plugins.value = PluginService.i.getAllPlugins();
    } catch (e) {
      logger.e("[插件中心状态服务] 加载插件数据失败: $e");
    } finally {
      isLoading.value = false;
    }
  }

  // MARK: - 数据操作

  /// 更新插件服务器URL
  Future<void> updateServerUrl(String url) async {
    try {
      await PluginService.i.setPluginServerUrl(url);
      pluginServerUrl.value = url;
      logger.i("[插件中心状态服务] 插件服务器URL已更新: $url");
    } catch (e) {
      logger.e("[插件中心状态服务] 更新插件服务器URL失败: $e");
    }
  }

  /// 强制更新单个插件
  Future<bool> updatePlugin(String fileName) async {
    updatingPlugin.value = fileName;

    try {
      final result = await PluginService.i.forceUpdatePlugin(fileName);
      if (result) {
        await loadPluginData(); // 重新加载插件数据
        return true;
      }
      return false;
    } catch (e) {
      logger.e("[插件中心状态服务] 更新插件失败: $fileName | $e");
      return false;
    } finally {
      updatingPlugin.value = '';
    }
  }

  /// 更新所有插件
  Future<bool> updateAllPlugins() async {
    try {
      // 获取所有插件文件名
      final pluginNames = plugins.map((p) => p.fileName).toList();

      // 逐个更新插件
      bool allSuccess = true;
      for (final name in pluginNames) {
        updatingPlugin.value = name;

        // 更新单个插件，并等待完成
        final success = await PluginService.i.forceUpdatePlugin(name);
        if (!success) {
          allSuccess = false;
        }

        // 短暂延迟，让用户能看到完成状态
        await Future.delayed(Animations.durationNormal);
      }

      // 更新完成后重新加载插件数据
      updatingPlugin.value = '';
      await loadPluginData();
      return allSuccess;
    } catch (e) {
      logger.e("[插件中心状态服务] 更新所有插件失败: $e");
      return false;
    } finally {
      // 确保清除更新状态
      updatingPlugin.value = '';
    }
  }
}
