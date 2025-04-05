import 'package:daily_satori/app/modules/plugin_center/controllers/plugin_center_controller.dart';
import 'package:daily_satori/app/services/plugin_service.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// 插件卡片组件
class PluginCardWidget extends StatelessWidget {
  final PluginInfo plugin;
  final PluginCenterController controller;

  const PluginCardWidget({super.key, required this.plugin, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 16),
      elevation: 2,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Obx(() {
        final isUpdating = controller.updatingPlugin.value == plugin.fileName;

        return Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: Colors.indigo.withAlpha(25),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Icon(Icons.settings_input_svideo, color: Colors.indigo, size: 20),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      plugin.fileName,
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, height: 1.2),
                    ),
                  ),
                  if (isUpdating)
                    const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2))
                  else
                    IconButton(
                      icon: const Icon(Icons.refresh, color: Colors.indigo),
                      onPressed: () => _updatePlugin(context, plugin.fileName),
                      tooltip: '更新此插件',
                      padding: EdgeInsets.zero,
                      constraints: const BoxConstraints(),
                    ),
                ],
              ),
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Text(
                  plugin.description,
                  style: TextStyle(
                    color: Theme.of(context).textTheme.bodyMedium?.color?.withAlpha(204),
                    fontSize: 14,
                    height: 1.4,
                  ),
                ),
              ),
              const SizedBox(height: 10),
              Row(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  Container(
                    padding: const EdgeInsets.all(4),
                    decoration: BoxDecoration(color: Colors.grey.withAlpha(25), borderRadius: BorderRadius.circular(4)),
                    child: const Icon(Icons.update, size: 14, color: Colors.grey),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    '上次更新: ${controller.getUpdateTimeText(plugin.lastUpdateTime)}',
                    style: TextStyle(
                      fontSize: 12,
                      color: Theme.of(context).textTheme.bodySmall?.color,
                      height: 1.2,
                      letterSpacing: 0.2,
                    ),
                  ),
                ],
              ),
            ],
          ),
        );
      }),
    );
  }

  /// 更新单个插件
  Future<void> _updatePlugin(BuildContext context, String fileName) async {
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
}
