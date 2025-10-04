import 'package:daily_satori/app/components/common/feature_icon.dart';
import 'package:daily_satori/app/styles/index.dart';
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
      body: _buildBody(context),
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

  /// 构建主体内容
  Widget _buildBody(BuildContext context) {
    return Obx(() {
      if (controller.isLoading.value) {
        return StyleGuide.getLoadingState(context);
      }
      final allConfigs = controller.configs;
      if (allConfigs.isEmpty) {
        return _buildEmptyState(context);
      }
      return _buildConfigList(context, allConfigs);
    });
  }

  /// 构建空状态提示
  Widget _buildEmptyState(BuildContext context) {
    return Center(
      child: Text(
        '没有配置',
        style: AppTheme.getTextTheme(
          context,
        ).bodyMedium?.copyWith(color: AppTheme.getColorScheme(context).onSurface.withValues(alpha: 150)),
      ),
    );
  }

  /// 构建配置列表
  Widget _buildConfigList(BuildContext context, List<dynamic> configs) {
    return ListView.separated(
      padding: Dimensions.paddingM,
      itemCount: configs.length,
      separatorBuilder: (_, _) => const SizedBox(height: 12),
      itemBuilder: (context, index) => _buildConfigCard(context, configs[index]),
    );
  }

  /// 构建配置卡片
  Widget _buildConfigCard(BuildContext context, dynamic config) {
    final color = _getTypeColor(config.functionType);
    return Card(
      margin: EdgeInsets.zero,
      child: InkWell(
        onTap: () => controller.editConfig(config),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              _buildConfigIcon(config.functionType, color),
              const SizedBox(width: 16),
              _buildConfigInfo(context, config),
              _buildChevronIcon(context),
            ],
          ),
        ),
      ),
    );
  }

  /// 构建配置图标
  Widget _buildConfigIcon(int type, Color color) {
    return FeatureIcon(icon: _getTypeIcon(type), iconColor: color, containerSize: 32, iconSize: 16);
  }

  /// 构建配置信息
  Widget _buildConfigInfo(BuildContext context, dynamic config) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(config.name, style: AppTheme.getTextTheme(context).titleSmall),
          Text(
            config.apiAddress.isEmpty ? '未设置，继承通用配置' : config.apiAddress,
            style: AppTheme.getTextTheme(context).bodySmall?.copyWith(
              color: AppTheme.getColorScheme(
                context,
              ).onSurface.withValues(alpha: config.apiAddress.isEmpty ? 128 : 179),
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }

  /// 构建右侧箭头图标
  Widget _buildChevronIcon(BuildContext context) {
    return Icon(Icons.chevron_right, color: AppTheme.getColorScheme(context).onSurface.withValues(alpha: 77), size: 18);
  }

  /// 显示AI配置信息对话框
  void _showInfoDialog(BuildContext context) {
    showDialog(context: context, builder: (context) => const AIConfigInfoDialog());
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

/// AI配置信息对话框
class AIConfigInfoDialog extends StatelessWidget {
  const AIConfigInfoDialog({super.key});
  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);
    return AlertDialog(
      title: _buildDialogTitle(context, colorScheme, textTheme),
      content: _buildDialogContent(context, colorScheme, textTheme),
      actions: [TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('了解了'))],
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
    );
  }

  /// 构建对话框标题
  Widget _buildDialogTitle(BuildContext context, ColorScheme colorScheme, TextTheme textTheme) {
    return Row(
      children: [
        Icon(Icons.help_outline, color: colorScheme.primary),
        const SizedBox(width: 8),
        Text('AI配置说明', style: textTheme.titleLarge),
      ],
    );
  }

  /// 构建对话框内容
  Widget _buildDialogContent(BuildContext context, ColorScheme colorScheme, TextTheme textTheme) {
    return SingleChildScrollView(
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
            style: textTheme.bodyMedium?.copyWith(color: colorScheme.onSurface.withValues(alpha: 179)),
          ),
        ],
      ),
    );
  }

  /// 构建信息项目
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
