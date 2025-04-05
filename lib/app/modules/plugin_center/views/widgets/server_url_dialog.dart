import 'package:daily_satori/app/modules/plugin_center/controllers/plugin_center_controller.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// 服务器URL设置对话框组件
class ServerUrlDialog extends StatelessWidget {
  final PluginCenterController controller;

  const ServerUrlDialog({super.key, required this.controller});

  /// 显示服务器URL设置对话框
  static void show(BuildContext context, PluginCenterController controller) {
    Get.dialog(ServerUrlDialog(controller: controller));
  }

  @override
  Widget build(BuildContext context) {
    final textController = TextEditingController(text: controller.pluginServerUrl.value);

    return AlertDialog(
      title: const Text('插件服务器设置'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('设置插件服务器URL地址:'),
          const SizedBox(height: 8),
          TextField(
            maxLines: null,
            keyboardType: TextInputType.multiline,
            controller: textController,
            decoration: const InputDecoration(hintText: 'https://example.com/plugins', border: OutlineInputBorder()),
          ),
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('取消')),
        ElevatedButton(
          onPressed: () {
            final url = textController.text.trim();
            if (url.isNotEmpty) {
              controller.updateServerUrl(url);
            }
            Navigator.of(context).pop();
          },
          child: const Text('保存'),
        ),
      ],
    );
  }
}
