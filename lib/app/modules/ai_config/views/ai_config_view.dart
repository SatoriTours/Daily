import 'package:daily_satori/app/models/models.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../controllers/ai_config_controller.dart';

/// AI配置页面
/// 用于管理不同功能的AI配置，包括：
/// - 通用配置：所有AI功能的基础配置
/// - 文章总结：生成文章摘要和关键点
/// - 书本解读：解析书籍内容和笔记
/// - 日记总结：分析和生成日记内容
class AIConfigView extends GetView<AIConfigController> {
  const AIConfigView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.getColorScheme(context).surface,
      appBar: _buildAppBar(context),
      body: _buildAllConfigsList(),
    );
  }

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(
      title: const Text('AI 配置管理'),
      centerTitle: true,
      elevation: 0,
      backgroundColor: AppTheme.getColorScheme(context).surface,
      actions: [
        IconButton(icon: const Icon(Icons.info_outline), tooltip: 'AI配置说明', onPressed: () => _showInfoDialog(context)),
      ],
    );
  }

  /// 显示AI配置信息对话框
  void _showInfoDialog(BuildContext context) {
    showDialog(context: context, builder: (context) => _InfoDialog());
  }

  /// 构建所有配置列表
  Widget _buildAllConfigsList() {
    return Obx(() {
      if (controller.isLoading.value) {
        return _buildLoadingState();
      }

      // 获取所有配置
      final configs = controller.configs;
      if (configs.isEmpty) {
        return Center(
          child: Text('没有配置', style: TextStyle(color: AppTheme.getColorScheme(Get.context!).onSurface.withAlpha(150))),
        );
      }

      return CustomScrollView(
        physics: const BouncingScrollPhysics(),
        slivers: [
          SliverList(
            delegate: SliverChildListDelegate([
              const SizedBox(height: 16),
              _buildConfigSection(0, '通用配置', Icons.settings, Colors.blue),
              _buildConfigSection(1, '文章总结', Icons.article, Colors.green),
              _buildConfigSection(2, '书本解读', Icons.book, Colors.orange),
              _buildConfigSection(3, '日记总结', Icons.edit_note, Colors.purple),
              const SizedBox(height: 20),
            ]),
          ),
        ],
      );
    });
  }

  /// 构建配置分类区域
  Widget _buildConfigSection(int type, String title, IconData icon, Color iconColor) {
    final configs = controller.getConfigsByType(type);
    final colorScheme = AppTheme.getColorScheme(Get.context!);

    if (configs.isEmpty) {
      return const SizedBox.shrink();
    }

    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            decoration: BoxDecoration(
              color: colorScheme.surface,
              borderRadius: BorderRadius.circular(16),
              boxShadow: [BoxShadow(color: Colors.black.withAlpha(8), blurRadius: 10, offset: const Offset(0, 5))],
            ),
            child: Column(
              children:
                  configs.asMap().entries.map((entry) {
                    final index = entry.key;
                    final config = entry.value;
                    return Column(
                      children: [
                        _ConfigItem(config: config, controller: controller, iconColor: iconColor),
                        if (index < configs.length - 1) const Divider(height: 1, indent: 56),
                      ],
                    );
                  }).toList(),
            ),
          ),
        ],
      ),
    );
  }

  /// 构建加载状态
  Widget _buildLoadingState() {
    final colorScheme = AppTheme.getColorScheme(Get.context!);
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          CircularProgressIndicator(color: colorScheme.primary),
          const SizedBox(height: 16),
          Text('加载配置中...', style: TextStyle(color: colorScheme.onSurface.withAlpha(153))),
        ],
      ),
    );
  }
}

/// 配置项组件
class _ConfigItem extends StatelessWidget {
  final AIConfigModel config;
  final AIConfigController controller;
  final Color iconColor;

  const _ConfigItem({required this.config, required this.controller, required this.iconColor});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      leading: Container(
        width: 36,
        height: 36,
        decoration: BoxDecoration(color: iconColor.withAlpha(25), borderRadius: BorderRadius.circular(10)),
        child: Icon(_getTypeIcon(config.functionType), color: iconColor, size: 20),
      ),
      title: Text(config.name, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w500)),
      subtitle: Text(
        config.apiAddress.isEmpty ? '未设置，继承通用配置' : config.apiAddress,
        style: TextStyle(
          fontSize: 13,
          color:
              config.apiAddress.isEmpty ? colorScheme.onSurface.withAlpha(128) : colorScheme.onSurface.withAlpha(179),
        ),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [Icon(Icons.chevron_right, color: colorScheme.onSurface.withAlpha(77), size: 20)],
      ),
      onTap: () => controller.editConfig(config),
    );
  }

  // 获取类型图标
  IconData _getTypeIcon(int type) {
    switch (type) {
      case 0:
        return Icons.settings;
      case 1:
        return Icons.article;
      case 2:
        return Icons.book;
      case 3:
        return Icons.edit_note;
      default:
        return Icons.settings;
    }
  }
}

/// 信息对话框组件
class _InfoDialog extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return AlertDialog(
      title: Row(
        children: [
          Icon(Icons.help_outline, color: colorScheme.primary),
          const SizedBox(width: 8),
          const Text('AI配置说明'),
        ],
      ),
      content: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            _buildInfoItem('通用配置', '用于所有AI功能的基础配置，可被其他类型配置继承'),
            const SizedBox(height: 12),
            _buildInfoItem('文章总结', '用于生成文章摘要和关键点提取'),
            const SizedBox(height: 12),
            _buildInfoItem('书本解读', '用于解析书籍内容和生成阅读笔记'),
            const SizedBox(height: 12),
            _buildInfoItem('日记总结', '用于分析和生成日记内容'),
            const SizedBox(height: 16),
            const Divider(),
            const SizedBox(height: 8),
            Text('通过不同功能类型的配置，您可以针对特定任务优化AI性能。', style: TextStyle(color: colorScheme.onSurface.withAlpha(179))),
          ],
        ),
      ),
      actions: [TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('了解了'))],
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
    );
  }

  Widget _buildInfoItem(String title, String description) {
    final textTheme = Get.textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
        const SizedBox(height: 4),
        Text(description, style: textTheme.bodyMedium?.copyWith(color: Colors.grey[600])),
      ],
    );
  }
}
