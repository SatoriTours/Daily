import 'package:daily_satori/app/modules/plugin_center/controllers/plugin_center_controller.dart';
import 'package:daily_satori/app/modules/plugin_center/views/widgets/plugin_card.dart';
import 'package:daily_satori/app/modules/plugin_center/views/widgets/server_url_dialog.dart';
import 'package:daily_satori/app/modules/plugin_center/views/widgets/update_status_indicator.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/components/button_styles.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart' as base_dim;

/// 插件中心视图
class PluginCenterView extends GetView<PluginCenterController> {
  const PluginCenterView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: _buildAppBar(context),
      body: Obx(() => _buildBody(context)),
      floatingActionButton: _buildFloatingActionButton(context),
    );
  }

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(
      title: const Text('插件中心'),
      centerTitle: true,
      elevation: 1,
      actions: [
        IconButton(
          icon: const Icon(Icons.settings),
          tooltip: '服务器设置',
          onPressed: () => ServerUrlDialog.show(context, controller),
        ),
      ],
    );
  }

  /// 构建主体内容
  Widget _buildBody(BuildContext context) {
    if (controller.isLoading.value) {
      return const Center(child: CircularProgressIndicator());
    }

    if (controller.plugins.isEmpty) {
      return _buildEmptyView(context);
    }

    return Column(
      children: [
        // 更新状态指示器
        UpdateStatusIndicator(updatingFileName: controller.updatingPlugin.value),

        // 插件列表
        Expanded(
          child: RefreshIndicator(
            onRefresh: controller.loadPluginData,
            child: ListView.builder(
              itemCount: controller.plugins.length,
              padding: const EdgeInsets.all(16),
              physics: const AlwaysScrollableScrollPhysics(),
              itemBuilder: (context, index) {
                return PluginCard(plugin: controller.plugins[index], controller: controller);
              },
            ),
          ),
        ),
      ],
    );
  }

  /// 构建空视图
  Widget _buildEmptyView(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.extension_off, size: 72, color: Colors.grey),
          const SizedBox(height: 20),
          const Text('没有找到任何插件', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w500)),
          const SizedBox(height: 8),
          Text('请检查插件服务器设置', style: TextStyle(fontSize: 14, color: Colors.grey.shade600)),
          const SizedBox(height: 24),
          ElevatedButton.icon(
            onPressed: controller.loadPluginData,
            icon: const Icon(Icons.refresh),
            label: const Text('刷新'),
            style: ButtonStyles.getPrimaryStyle(context).copyWith(
              padding: WidgetStateProperty.all(const EdgeInsets.symmetric(horizontal: 24, vertical: 12)),
              minimumSize: WidgetStateProperty.all(Size.fromHeight(base_dim.Dimensions.buttonHeightSmall)),
            ),
          ),
        ],
      ),
    );
  }

  /// 构建浮动按钮
  Widget _buildFloatingActionButton(BuildContext context) {
    return Obx(() {
      // 如果正在更新某个插件，不显示按钮
      if (controller.updatingPlugin.isNotEmpty) {
        return const SizedBox.shrink();
      }

      return FloatingActionButton.small(
        onPressed: () => _updateAllPlugins(context),
        tooltip: '更新所有插件',
        child: const Icon(Icons.refresh),
      );
    });
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
