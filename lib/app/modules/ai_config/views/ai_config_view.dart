import 'package:daily_satori/app/components/common/feature_icon.dart';
import 'package:daily_satori/app/models/models.dart';
import 'package:daily_satori/app/styles/app_styles.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/styles/dimensions.dart';
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
      appBar: AppBar(
        title: const Text('AI 配置管理'),
        centerTitle: true,
        elevation: 0,
        backgroundColor: AppTheme.getColorScheme(context).surface,
        actions: [
          IconButton(
            icon: const Icon(Icons.info_outline),
            tooltip: 'AI配置说明',
            onPressed: () => _showInfoDialog(context),
          ),
        ],
      ),
      body: Obx(() {
        if (controller.isLoading.value) {
          return AppStyles.loadingState(context);
        }

        final allConfigs = controller.configs;

        if (allConfigs.isEmpty) {
          return Center(
            child: Text(
              '没有配置',
              style: AppTheme.getTextTheme(
                context,
              ).bodyMedium?.copyWith(color: AppTheme.getColorScheme(context).onSurface.withAlpha(150)),
            ),
          );
        }

        return ListView.separated(
          padding: Dimensions.paddingM,
          itemCount: allConfigs.length,
          separatorBuilder: (_, __) => const SizedBox(height: 8),
          itemBuilder: (context, index) {
            final config = allConfigs[index];
            final color = _getTypeColor(config.functionType);

            return Card(
              margin: EdgeInsets.zero,
              child: InkWell(
                onTap: () => controller.editConfig(config),
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Row(
                    children: [
                      FeatureIcon(
                        icon: _getTypeIcon(config.functionType),
                        iconColor: color,
                        containerSize: 32,
                        iconSize: 16,
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(config.name, style: AppTheme.getTextTheme(context).titleSmall),
                            Text(
                              config.apiAddress.isEmpty ? '未设置，继承通用配置' : config.apiAddress,
                              style: AppTheme.getTextTheme(context).bodySmall?.copyWith(
                                color: AppTheme.getColorScheme(
                                  context,
                                ).onSurface.withAlpha(config.apiAddress.isEmpty ? 128 : 179),
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ),
                      ),
                      Icon(
                        Icons.chevron_right,
                        color: AppTheme.getColorScheme(context).onSurface.withAlpha(77),
                        size: 18,
                      ),
                    ],
                  ),
                ),
              ),
            );
          },
        );
      }),
    );
  }

  /// 显示AI配置信息对话框
  void _showInfoDialog(BuildContext context) {
    showDialog(context: context, builder: (context) => _InfoDialog());
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

  // 获取类型颜色
  Color _getTypeColor(int type) {
    switch (type) {
      case 0:
        return Colors.blue;
      case 1:
        return Colors.green;
      case 2:
        return Colors.orange;
      case 3:
        return Colors.purple;
      default:
        return Colors.blue;
    }
  }
}

/// 信息对话框组件
class _InfoDialog extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return AlertDialog(
      title: Row(
        children: [
          Icon(Icons.help_outline, color: colorScheme.primary),
          const SizedBox(width: 8),
          Text('AI配置说明', style: textTheme.titleLarge),
        ],
      ),
      content: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            _buildInfoItem(context, '通用配置', '用于所有AI功能的基础配置，可被其他类型配置继承'),
            const SizedBox(height: 12),
            _buildInfoItem(context, '文章总结', '用于生成文章摘要和关键点提取'),
            const SizedBox(height: 12),
            _buildInfoItem(context, '书本解读', '用于解析书籍内容和生成阅读笔记'),
            const SizedBox(height: 12),
            _buildInfoItem(context, '日记总结', '用于分析和生成日记内容'),
            const SizedBox(height: 16),
            const Divider(),
            const SizedBox(height: 8),
            Text(
              '通过不同功能类型的配置，您可以针对特定任务优化AI性能。',
              style: textTheme.bodyMedium?.copyWith(color: colorScheme.onSurface.withAlpha(179)),
            ),
          ],
        ),
      ),
      actions: [TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('了解了'))],
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
    );
  }

  Widget _buildInfoItem(BuildContext context, String title, String description) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
        const SizedBox(height: 4),
        Text(description, style: textTheme.bodyMedium?.copyWith(color: colorScheme.onSurfaceVariant)),
      ],
    );
  }
}
