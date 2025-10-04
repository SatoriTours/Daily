import 'package:daily_satori/app/modules/plugin_center/controllers/plugin_center_controller.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/components/button_styles.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart';

/// 服务器URL设置对话框
class ServerUrlDialog {
  /// 私有构造函数
  ServerUrlDialog._();

  /// 显示服务器URL设置对话框
  static Future<void> show(BuildContext context, PluginCenterController controller) async {
    final textController = TextEditingController(text: controller.pluginServerUrl.value);
    final colorScheme = Theme.of(context).colorScheme;

    return Get.dialog(
      AlertDialog(
        title: const Text('插件服务器设置', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusL)),
        contentPadding: const EdgeInsets.fromLTRB(24, 20, 24, 0),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('设置插件服务器URL地址:', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
            Dimensions.verticalSpacerS,
            _buildUrlTextField(textController),
            Dimensions.verticalSpacerXs,
            _buildHintText(colorScheme),
          ],
        ),
        actionsPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            style: ButtonStyles.getTextStyle(context),
            child: const Text('取消'),
          ),
          ElevatedButton(
            onPressed: () {
              final url = textController.text.trim();
              if (url.isNotEmpty) {
                controller.updateServerUrl(url);
              }
              Navigator.of(context).pop();
            },
            style: ButtonStyles.getPrimaryStyle(context),
            child: const Text('保存'),
          ),
        ],
      ),
    );
  }

  /// 构建URL文本输入框
  static Widget _buildUrlTextField(TextEditingController controller) {
    return TextField(
      controller: controller,
      decoration: InputDecoration(
        hintText: 'https://example.com/plugins',
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          borderSide: const BorderSide(width: 1),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          borderSide: const BorderSide(width: 2),
        ),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        prefixIcon: const Icon(Icons.link),
      ),
      style: const TextStyle(fontSize: 15),
      keyboardType: TextInputType.url,
      textInputAction: TextInputAction.done,
      autocorrect: false,
      enableSuggestions: false,
    );
  }

  /// 构建提示文本
  static Widget _buildHintText(ColorScheme colorScheme) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
      decoration: BoxDecoration(
        color: colorScheme.primary.withAlpha(26),
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        border: Border.all(color: colorScheme.primary.withAlpha(77), width: 1),
      ),
      child: Row(
        children: [
          Icon(Icons.info_outline, size: 16, color: colorScheme.primary),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              '服务器URL应指向包含插件配置文件的目录',
              style: TextStyle(fontSize: 12, color: colorScheme.primary, height: 1.3),
            ),
          ),
        ],
      ),
    );
  }
}
