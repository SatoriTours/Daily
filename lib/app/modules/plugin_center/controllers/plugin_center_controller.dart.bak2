import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/plugin_service.dart';
import 'package:get/get.dart';

/// 插件中心控制器
class PluginCenterController extends GetxController {
  // 插件列表数据
  final plugins = <PluginInfo>[].obs;

  // 插件服务器地址
  final pluginServerUrl = ''.obs;

  // 加载状态
  final isLoading = false.obs;

  // 正在更新的插件名
  final updatingPlugin = ''.obs;

  @override
  void onInit() {
    super.onInit();
    loadPluginData();
  }

  /// 加载插件数据
  Future<void> loadPluginData() async {
    isLoading.value = true;

    try {
      // 加载插件服务器URL
      pluginServerUrl.value = PluginService.i.getPluginServerUrl();

      // 加载插件列表
      plugins.value = PluginService.i.getAllPlugins();
    } catch (e) {
      logger.e("加载插件数据失败: $e");
    } finally {
      isLoading.value = false;
    }
  }

  /// 更新插件服务器URL
  Future<void> updateServerUrl(String url) async {
    try {
      await PluginService.i.setPluginServerUrl(url);
      pluginServerUrl.value = url;
      logger.i("插件服务器URL已更新: $url");
    } catch (e) {
      logger.e("更新插件服务器URL失败: $e");
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
      logger.e("更新插件失败: $fileName | $e");
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

      // 逐个更新插件，但不设置全局加载状态
      bool allSuccess = true;
      for (final name in pluginNames) {
        updatingPlugin.value = name;

        // 更新单个插件，并等待完成
        final success = await PluginService.i.forceUpdatePlugin(name);
        if (!success) {
          allSuccess = false;
        }

        // 短暂延迟，让用户能看到完成状态
        await Future.delayed(const Duration(milliseconds: 300));
      }

      // 更新完成后重新加载插件数据
      updatingPlugin.value = '';
      await loadPluginData();
      return allSuccess;
    } catch (e) {
      logger.e("更新所有插件失败: $e");
      return false;
    } finally {
      // 确保清除更新状态
      updatingPlugin.value = '';
    }
  }

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
