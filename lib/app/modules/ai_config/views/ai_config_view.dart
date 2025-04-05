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

      return ListView(
        physics: const BouncingScrollPhysics(),
        padding: const EdgeInsets.all(16),
        children: [
          _buildConfigsByType(0, Icons.settings),
          _buildConfigsByType(1, Icons.article),
          _buildConfigsByType(2, Icons.book),
          _buildConfigsByType(3, Icons.edit_note),
        ],
      );
    });
  }

  /// 构建单个类型的配置列表
  Widget _buildConfigsByType(int type, IconData icon) {
    final configs = controller.getConfigsByType(type);

    if (configs.isEmpty) {
      return const SizedBox.shrink();
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 配置列表，直接显示卡片
        ...configs.map((config) => _ConfigCard(config: config, controller: controller)),

        // 底部间距
        const SizedBox(height: 16),
      ],
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

/// 配置卡片组件
class _ConfigCard extends StatelessWidget {
  final AIConfigModel config;
  final AIConfigController controller;

  const _ConfigCard({required this.config, required this.controller});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: isDark ? colorScheme.outline.withAlpha(120) : colorScheme.outline.withAlpha(90),
          width: 1.5,
        ),
      ),
      child: InkWell(
        onTap: () => controller.editConfig(config),
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  // 添加类型图标
                  _getTypeIcon(config.functionType),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      config.name,
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: colorScheme.onSurface),
                    ),
                  ),
                  // 添加向右箭头
                  Icon(Icons.chevron_right, color: colorScheme.onSurfaceVariant.withAlpha(150), size: 20),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                config.apiAddress.isEmpty ? '未设置，继承通用配置' : config.apiAddress,
                style: TextStyle(
                  fontSize: 14,
                  color: config.apiAddress.isEmpty ? colorScheme.onSurface.withAlpha(153) : colorScheme.onSurface,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
      ),
    );
  }

  // 获取类型图标
  Widget _getTypeIcon(int type) {
    IconData iconData;
    switch (type) {
      case 0:
        iconData = Icons.settings;
        break;
      case 1:
        iconData = Icons.article;
        break;
      case 2:
        iconData = Icons.book;
        break;
      case 3:
        iconData = Icons.edit_note;
        break;
      default:
        iconData = Icons.settings;
    }

    return Icon(iconData, size: 18, color: AppTheme.getColorScheme(Get.context!).primary);
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
