import 'package:daily_satori/app/pages/plugin_center/controllers/plugin_center_controller.dart';
import 'package:daily_satori/app/services/plugin_service.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// 插件卡片组件
class PluginCard extends StatelessWidget {
  /// 插件信息
  final PluginInfo plugin;

  /// 控制器
  final PluginCenterController controller;

  /// 构造函数
  const PluginCard({super.key, required this.plugin, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 16),
      elevation: 2,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Obx(() {
        final isUpdating = controller.updatingPlugin.value == plugin.fileName;

        return Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 标题行
              _buildTitleRow(context, isUpdating),
              const SizedBox(height: 12),

              // 描述
              _buildDescription(context),
              const SizedBox(height: 16),

              // 更新时间
              _buildUpdateTime(context),
            ],
          ),
        );
      }),
    );
  }

  /// 构建标题行
  Widget _buildTitleRow(BuildContext context, bool isUpdating) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        // 图标
        Container(
          decoration: BoxDecoration(color: Colors.blue.withAlpha(26), borderRadius: BorderRadius.circular(8)),
          padding: const EdgeInsets.all(8),
          child: const Icon(Icons.settings_input_svideo, color: Colors.blue, size: 20),
        ),
        const SizedBox(width: 12),

        // 文件名
        Expanded(
          child: Text(plugin.fileName, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, height: 1.2)),
        ),

        // 更新按钮或进度指示器
        if (isUpdating) _buildUpdatingIndicator() else _buildUpdateButton(),
      ],
    );
  }

  /// 构建更新中指示器
  Widget _buildUpdatingIndicator() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(color: Colors.blue.withAlpha(26), borderRadius: BorderRadius.circular(16)),
      child: const Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            width: 14,
            height: 14,
            child: CircularProgressIndicator(strokeWidth: 2, valueColor: AlwaysStoppedAnimation<Color>(Colors.blue)),
          ),
          SizedBox(width: 8),
          Text('更新中', style: TextStyle(color: Colors.blue, fontSize: 13, fontWeight: FontWeight.w500)),
        ],
      ),
    );
  }

  /// 构建更新按钮
  Widget _buildUpdateButton() {
    return Material(
      color: Colors.transparent,
      borderRadius: BorderRadius.circular(20),
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: controller.updatingPlugin.isEmpty ? () => _updatePlugin() : null,
        child: Padding(
          padding: const EdgeInsets.all(8.0),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.refresh, color: controller.updatingPlugin.isEmpty ? Colors.blue : Colors.grey, size: 22),
            ],
          ),
        ),
      ),
    );
  }

  /// 构建描述文本
  Widget _buildDescription(BuildContext context) {
    return Text(
      plugin.description,
      style: TextStyle(color: Theme.of(context).textTheme.bodyMedium?.color?.withAlpha(179), fontSize: 14, height: 1.4),
    );
  }

  /// 构建更新时间信息
  Widget _buildUpdateTime(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: Theme.of(context).dividerColor.withAlpha(26),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.update, size: 14, color: Theme.of(context).textTheme.bodySmall?.color?.withAlpha(153)),
          const SizedBox(width: 6),
          Text(
            '上次更新: ${controller.getUpdateTimeText(plugin.lastUpdateTime)}',
            style: TextStyle(fontSize: 12, color: Theme.of(context).textTheme.bodySmall?.color?.withAlpha(153)),
          ),
        ],
      ),
    );
  }

  /// 更新插件
  void _updatePlugin() {
    // 如果服务器URL未设置，提示设置
    if (controller.pluginServerUrl.value.isEmpty) {
      UIUtils.showSnackBar('未设置服务器', '请先设置插件服务器地址', isError: true);
      return;
    }

    // 执行更新
    controller
        .updatePlugin(plugin.fileName)
        .then((result) {
          if (result) {
            UIUtils.showSnackBar('插件更新成功', '插件 ${plugin.fileName} 已成功更新');
          } else {
            UIUtils.showSnackBar('插件更新失败', '无法更新插件 ${plugin.fileName}', isError: true);
          }
        })
        .catchError((e) {
          UIUtils.showSnackBar('更新出错', '更新插件时发生错误: $e', isError: true);
        });
  }
}
