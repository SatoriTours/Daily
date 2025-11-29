import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/index.dart';

/// AI配置信息对话框
///
/// 显示各种 AI 配置类型的说明信息
class AIConfigInfoDialog extends StatelessWidget {
  const AIConfigInfoDialog({super.key});

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: _buildDialogTitle(context),
      content: _buildDialogContent(context),
      actions: [TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('了解了'))],
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusL)),
    );
  }

  /// 构建对话框标题
  Widget _buildDialogTitle(BuildContext context) {
    return Row(
      children: [
        Icon(Icons.help_outline, color: AppColors.getPrimary(context), size: Dimensions.iconSizeM),
        Dimensions.horizontalSpacerM,
        Text('AI配置说明', style: AppTypography.titleLarge),
      ],
    );
  }

  /// 构建对话框内容
  Widget _buildDialogContent(BuildContext context) {
    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          _buildInfoItem(context, '通用配置', '用于所有AI功能的基础配置，可被其他类型配置继承'),
          Dimensions.verticalSpacerM,
          _buildInfoItem(context, '文章总结', '用于生成文章摘要和关键点提取'),
          Dimensions.verticalSpacerM,
          _buildInfoItem(context, '书本解读', '用于解析书籍内容和生成阅读笔记'),
          Dimensions.verticalSpacerM,
          _buildInfoItem(context, '日记总结', '用于分析和生成日记内容'),
          Dimensions.verticalSpacerL,
          const Divider(),
          Dimensions.verticalSpacerM,
          Text(
            '通过不同功能类型的配置，您可以针对特定任务优化AI性能。',
            style: AppTypography.bodyMedium.copyWith(
              color: AppColors.getOnSurface(context).withValues(alpha: Opacities.medium),
            ),
          ),
        ],
      ),
    );
  }

  /// 构建信息项目
  Widget _buildInfoItem(BuildContext context, String title, String description) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: AppTypography.titleMedium.copyWith(fontWeight: FontWeight.w600)),
        Dimensions.verticalSpacerS,
        Text(description, style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurfaceVariant(context))),
      ],
    );
  }
}
