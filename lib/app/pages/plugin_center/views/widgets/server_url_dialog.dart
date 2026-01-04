import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/pages/plugin_center/providers/plugin_center_controller_provider.dart';
import 'package:daily_satori/app/routes/app_navigation.dart';
import 'package:daily_satori/app/styles/styles.dart';

/// 服务器URL设置对话框
class ServerUrlDialog {
  /// 私有构造函数
  ServerUrlDialog._();

  /// 显示服务器URL设置对话框
  static Future<void> show(BuildContext context, WidgetRef ref) async {
    final controllerState = ref.read(pluginCenterControllerProvider);
    final textController = TextEditingController(text: controllerState.pluginServerUrl);
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: colorScheme.surface,
      shape: const RoundedRectangleBorder(borderRadius: Dimensions.borderRadiusTop),
      builder: (context) => Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
        child: SingleChildScrollView(
          child: Padding(
            padding: Dimensions.paddingL,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 标题栏
                Row(
                  children: [
                    Container(
                      width: Dimensions.iconSizeXxl - Dimensions.spacingS,
                      height: Dimensions.iconSizeXxl - Dimensions.spacingS,
                      decoration: BoxDecoration(
                        color: colorScheme.primary.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(Dimensions.radiusS),
                      ),
                      child: Icon(Icons.dns_rounded, color: colorScheme.primary, size: Dimensions.iconSizeM),
                    ),
                    Dimensions.horizontalSpacerM,
                    Expanded(
                      child: Text('插件服务器设置', style: textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w600)),
                    ),
                    IconButton(icon: const Icon(Icons.close_rounded), onPressed: () => AppNavigation.back()),
                  ],
                ),
                Dimensions.verticalSpacerL,

                // 说明文本
                Text(
                  '服务器地址',
                  style: textTheme.labelLarge?.copyWith(color: colorScheme.primary, fontWeight: FontWeight.w600),
                ),
                Dimensions.verticalSpacerS,

                // URL输入框
                TextField(
                  controller: textController,
                  decoration: InputDecoration(
                    hintText: 'https://example.com/plugins',
                    hintStyle: TextStyle(color: colorScheme.onSurface.withValues(alpha: 0.4)),
                    prefixIcon: Icon(Icons.link_rounded, color: colorScheme.primary),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(Dimensions.radiusM),
                      borderSide: BorderSide(color: colorScheme.outline.withValues(alpha: 0.5)),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(Dimensions.radiusM),
                      borderSide: BorderSide(color: colorScheme.outline.withValues(alpha: 0.3)),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(Dimensions.radiusM),
                      borderSide: BorderSide(color: colorScheme.primary, width: 2),
                    ),
                    filled: true,
                    fillColor: colorScheme.surfaceContainerHighest.withValues(alpha: 0.3),
                    contentPadding: Dimensions.paddingM,
                  ),
                  style: textTheme.bodyLarge,
                  keyboardType: TextInputType.url,
                  textInputAction: TextInputAction.done,
                  autocorrect: false,
                  enableSuggestions: false,
                  autofocus: true,
                ),
                Dimensions.verticalSpacerM,

                // 提示信息
                Container(
                  padding: Dimensions.paddingM,
                  decoration: BoxDecoration(
                    color: colorScheme.primary.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(Dimensions.radiusS),
                    border: Border.all(color: colorScheme.primary.withValues(alpha: 0.2)),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(Icons.info_outline_rounded, size: Dimensions.iconSizeS, color: colorScheme.primary),
                      Dimensions.horizontalSpacerS,
                      Expanded(
                        child: Text(
                          '服务器URL应指向包含插件配置文件的目录',
                          style: textTheme.bodySmall?.copyWith(color: colorScheme.primary, height: 1.4),
                        ),
                      ),
                    ],
                  ),
                ),
                Dimensions.verticalSpacerL,

                // 按钮组
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () => AppNavigation.back(),
                        style: ButtonStyles.getOutlinedStyle(context),
                        child: const Text('取消'),
                      ),
                    ),
                    Dimensions.horizontalSpacerM,
                    Expanded(
                      child: ElevatedButton(
                        onPressed: () {
                          final url = textController.text.trim();
                          if (url.isNotEmpty) {
                            ref.read(pluginCenterControllerProvider.notifier).updateServerUrl(url);
                          }
                          AppNavigation.back();
                        },
                        style: ButtonStyles.getPrimaryStyle(context),
                        child: const Text('保存'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
