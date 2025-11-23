import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/extensions/i18n_extension.dart';
import '../controllers/ai_chat_controller.dart';
import '../../../components/ai_chat/chat_interface.dart';

/// AI聊天助手页面
class AIChatView extends GetView<AIChatController> {
  const AIChatView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.getBackground(context),
      appBar: _buildAppBar(context),
      body: Obx(
        () => ChatInterface(
          messages: controller.messages,
          onSendMessage: controller.sendMessage,
          onRetryMessage: controller.retryMessage,
          scrollController: controller.scrollController,
          inputController: controller.inputController,
          isLoading: controller.isProcessing.value,
          showHeader: false, // 不显示头部，避免重复
          headerSubtitle: controller.isProcessing.value ? controller.currentStep.value : null,
        ),
      ),
    );
  }

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(
      title: Row(
        children: [
          Container(
            padding: Dimensions.paddingS,
            decoration: BoxDecoration(
              color: AppColors.getPrimary(context).withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(Dimensions.radiusCircular),
            ),
            child: Icon(Icons.auto_awesome_outlined, size: Dimensions.iconSizeM, color: AppColors.getPrimary(context)),
          ),
          Dimensions.horizontalSpacerM,
          Text('ai_chat.title'.t),
        ],
      ),
      backgroundColor: AppColors.getSurface(context),
      elevation: 0,
      scrolledUnderElevation: 1,
      actions: [
        IconButton(
          icon: Icon(Icons.refresh, color: AppColors.getOnSurface(context)),
          onPressed: controller.clearMessages,
          tooltip: 'ai_chat.new_chat'.t,
        ),
        IconButton(
          icon: Icon(Icons.info_outline, color: AppColors.getOnSurface(context)),
          onPressed: () => _showHelpDialog(context),
          tooltip: 'ai_chat.help'.t,
        ),
      ],
    );
  }

  /// 显示帮助对话框
  void _showHelpDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Row(
          children: [
            Icon(Icons.help_outline, color: AppColors.getPrimary(context)),
            Dimensions.horizontalSpacerM,
            Text('ai_chat.help_title'.t),
          ],
        ),
        content: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              _buildHelpSection(
                context,
                title: 'ai_chat.help_features_title'.t,
                content: 'ai_chat.help_features_content'.t,
                icon: Icons.star_outline,
              ),
              Dimensions.verticalSpacerM,
              _buildHelpSection(
                context,
                title: 'ai_chat.help_usage_title'.t,
                content: 'ai_chat.help_usage_content'.t,
                icon: Icons.chat_bubble_outline,
              ),
              Dimensions.verticalSpacerM,
              _buildHelpSection(
                context,
                title: 'ai_chat.help_tips_title'.t,
                content: 'ai_chat.help_tips_content'.t,
                icon: Icons.lightbulb_outline,
              ),
            ],
          ),
        ),
        actions: [TextButton(onPressed: () => Navigator.of(context).pop(), child: Text('ai_chat.got_it'.t))],
      ),
    );
  }

  /// 构建帮助章节
  Widget _buildHelpSection(
    BuildContext context, {
    required String title,
    required String content,
    required IconData icon,
  }) {
    return Container(
      padding: Dimensions.paddingM,
      decoration: BoxDecoration(
        color: AppColors.getSurfaceContainer(context),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: Dimensions.iconSizeM, color: AppColors.getPrimary(context)),
              Dimensions.horizontalSpacerM,
              Text(title, style: AppTypography.titleMedium.copyWith(fontWeight: FontWeight.w600)),
            ],
          ),
          Dimensions.verticalSpacerS,
          Text(content, style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurfaceVariant(context))),
        ],
      ),
    );
  }
}
