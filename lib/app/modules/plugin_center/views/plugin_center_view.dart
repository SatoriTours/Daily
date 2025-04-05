import 'package:daily_satori/app/modules/plugin_center/controllers/plugin_center_controller.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

// 导入抽取的组件
import 'widgets/plugin_card_widget.dart';
import 'widgets/server_url_dialog.dart';

/// 插件中心视图
class PluginCenterView extends GetView<PluginCenterController> {
  const PluginCenterView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('插件中心'),
        actions: [
          IconButton(icon: const Icon(Icons.settings), onPressed: () => ServerUrlDialog.show(context, controller)),
        ],
      ),
      body: Obx(() => _buildBody(context)),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _updateAllPlugins(context),
        tooltip: '更新全部插件',
        child: const Icon(Icons.refresh),
      ),
    );
  }

  /// 构建主体内容
  Widget _buildBody(BuildContext context) {
    if (controller.isLoading.value) {
      return const Center(child: CircularProgressIndicator());
    }

    if (controller.plugins.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.extension_off, size: 64, color: Colors.grey),
            const SizedBox(height: 16),
            const Text('没有找到任何插件', style: TextStyle(fontSize: 18)),
            const SizedBox(height: 24),
            ElevatedButton(onPressed: controller.loadPluginData, child: const Text('刷新')),
          ],
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: controller.loadPluginData,
      child: ListView.builder(
        itemCount: controller.plugins.length,
        padding: const EdgeInsets.all(16),
        itemBuilder: (context, index) {
          final plugin = controller.plugins[index];
          return PluginCardWidget(plugin: plugin, controller: controller);
        },
      ),
    );
  }

  /// 更新所有插件
  Future<void> _updateAllPlugins(BuildContext context) async {
    // 如果服务器URL未设置，提示设置
    if (controller.pluginServerUrl.value.isEmpty) {
      UIUtils.showSnackBar('未设置服务器', '请先设置插件服务器地址', isError: true);
      ServerUrlDialog.show(context, controller);
      return;
    }

    try {
      final result = await controller.updateAllPlugins();

      if (result) {
        UIUtils.showSnackBar('全部更新成功', '所有插件已成功更新');
      } else {
        UIUtils.showSnackBar('部分更新失败', '部分插件更新失败，请查看日志', isError: true);
      }
    } catch (e) {
      UIUtils.showSnackBar('更新出错', '更新插件时发生错误: $e', isError: true);
    }
  }
}
