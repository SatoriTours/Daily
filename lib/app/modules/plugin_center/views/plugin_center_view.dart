import 'package:daily_satori/app/modules/plugin_center/controllers/plugin_center_controller.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:daily_satori/app/services/plugin_service.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

// 导入抽取的组件
import 'widgets/plugin_card_widget.dart';
import 'widgets/server_url_dialog.dart';

/// 插件中心视图
class PluginCenterView extends GetView<PluginCenterController> {
  const PluginCenterView({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('插件中心'),
        actions: [IconButton(icon: const Icon(Icons.settings), onPressed: () => _showServerUrlDialog(context))],
      ),
      body: Obx(() => _buildBody(context)),
      floatingActionButton: Obx(() {
        // 如果正在更新某个插件，显示取消按钮
        if (controller.updatingPlugin.isNotEmpty) {
          return const SizedBox.shrink(); // 更新过程中不显示按钮
        }

        return FloatingActionButton(
          onPressed: () => _updateAllPlugins(context),
          child: const Icon(Icons.refresh),
          tooltip: '更新全部插件',
        );
      }),
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
      child: Column(
        children: [
          // 当前更新状态指示器
          Obx(() {
            final updatingName = controller.updatingPlugin.value;
            if (updatingName.isNotEmpty) {
              return Container(
                padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
                color: Colors.blue.withOpacity(0.1),
                child: Row(
                  children: [
                    const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
                    const SizedBox(width: 12),
                    Expanded(child: Text('正在更新: $updatingName', style: const TextStyle(color: Colors.blue))),
                  ],
                ),
              );
            }
            return const SizedBox.shrink();
          }),

          // 插件列表
          Expanded(
            child: ListView.builder(
              itemCount: controller.plugins.length,
              padding: const EdgeInsets.all(16),
              itemBuilder: (context, index) {
                final plugin = controller.plugins[index];
                return _buildPluginCard(context, plugin);
              },
            ),
          ),
        ],
      ),
    );
  }

  /// 构建插件卡片
  Widget _buildPluginCard(BuildContext context, PluginInfo plugin) {
    return Card(
      margin: const EdgeInsets.only(bottom: 16),
      child: Obx(() {
        final isUpdating = controller.updatingPlugin.value == plugin.fileName;

        return Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(Icons.description, color: Colors.blue),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(plugin.fileName, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                  ),
                  if (isUpdating)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                      decoration: BoxDecoration(
                        color: Colors.blue.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          SizedBox(
                            width: 12,
                            height: 12,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              valueColor: AlwaysStoppedAnimation<Color>(Colors.blue),
                            ),
                          ),
                          SizedBox(width: 8),
                          Text('更新中', style: TextStyle(color: Colors.blue, fontSize: 12)),
                        ],
                      ),
                    )
                  else
                    IconButton(
                      icon: const Icon(Icons.refresh),
                      onPressed:
                          controller.updatingPlugin.isEmpty
                              ? () => _updatePlugin(context, plugin.fileName)
                              : null, // 禁用按钮当有其他插件正在更新
                      tooltip: '更新此插件',
                    ),
                ],
              ),
              const SizedBox(height: 8),
              Text(plugin.description, style: TextStyle(color: Theme.of(context).textTheme.bodySmall?.color)),
              const SizedBox(height: 12),
              Row(
                children: [
                  const Icon(Icons.update, size: 16, color: Colors.grey),
                  const SizedBox(width: 4),
                  Text(
                    '上次更新: ${controller.getUpdateTimeText(plugin.lastUpdateTime)}',
                    style: TextStyle(fontSize: 12, color: Theme.of(context).textTheme.bodySmall?.color),
                  ),
                ],
              ),
            ],
          ),
        );
      }),
    );
  }

  /// 显示服务器URL设置对话框
  void _showServerUrlDialog(BuildContext context) {
    final textController = TextEditingController(text: controller.pluginServerUrl.value);

    Get.dialog(
      AlertDialog(
        title: const Text('插件服务器设置'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('设置插件服务器URL地址:'),
            const SizedBox(height: 8),
            TextField(
              controller: textController,
              decoration: const InputDecoration(hintText: 'https://example.com/plugins', border: OutlineInputBorder()),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Get.back(), child: const Text('取消')),
          ElevatedButton(
            onPressed: () {
              final url = textController.text.trim();
              if (url.isNotEmpty) {
                controller.updateServerUrl(url);
              }
              Get.back();
            },
            child: const Text('保存'),
          ),
        ],
      ),
    );
  }

  /// 更新单个插件
  Future<void> _updatePlugin(BuildContext context, String fileName) async {
    // 如果服务器URL未设置，提示设置
    if (controller.pluginServerUrl.value.isEmpty) {
      UIUtils.showSnackBar('未设置服务器', '请先设置插件服务器地址', isError: true);
      _showServerUrlDialog(context);
      return;
    }

    try {
      final result = await controller.updatePlugin(fileName);

      if (result) {
        UIUtils.showSnackBar('插件更新成功', '插件 $fileName 已成功更新');
      } else {
        UIUtils.showSnackBar('插件更新失败', '无法更新插件 $fileName', isError: true);
      }
    } catch (e) {
      UIUtils.showSnackBar('更新出错', '更新插件时发生错误: $e', isError: true);
    }
  }

  /// 更新所有插件
  Future<void> _updateAllPlugins(BuildContext context) async {
    // 如果服务器URL未设置，提示设置
    if (controller.pluginServerUrl.value.isEmpty) {
      UIUtils.showSnackBar('未设置服务器', '请先设置插件服务器地址', isError: true);
      _showServerUrlDialog(context);
      return;
    }

    // 显示确认对话框
    await UIUtils.showConfirmation(
      '更新所有插件',
      '确定要更新所有插件吗？插件将依次更新，请稍候。',
      confirmText: '更新',
      cancelText: '取消',
      onConfirmed: () async {
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
      },
    );
  }
}
