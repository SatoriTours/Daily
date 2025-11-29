import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';
import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import '../controllers/ai_chat_controller.dart';
import '../../../components/ai_chat/chat_interface.dart';

/// AI聊天助手页面
///
/// 提供与AI助手对话的界面，支持：
/// - 发送消息和接收AI响应
/// - 重试失败的消息
/// - 查看处理步骤和搜索结果
class AIChatView extends GetView<AIChatController> {
  const AIChatView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.getBackground(context),
      appBar: _buildAppBar(context),
      body: _buildChatBody(),
    );
  }

  // ========================================================================
  // UI构建方法
  // ========================================================================

  /// 构建聊天主体
  Widget _buildChatBody() {
    return Obx(
      () => ChatInterface(
        messages: controller.messages,
        onSendMessage: controller.sendMessage,
        onRetryMessage: controller.retryMessage,
        scrollController: controller.scrollController,
        inputController: controller.inputController,
        isLoading: controller.isProcessing.value,
        showHeader: false, // 不显示头部，AppBar已有标题
        headerSubtitle: controller.isProcessing.value ? controller.currentStep.value : null,
      ),
    );
  }

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return SAppBar(
      title: _buildAppBarTitle(context),
      backgroundColorLight: AppColors.primary,
      backgroundColorDark: AppColors.backgroundDark,
      foregroundColor: Colors.white,
      elevation: 0,
      centerTitle: true,
      actions: _buildAppBarActions(context),
    );
  }

  /// 构建应用栏标题
  Widget _buildAppBarTitle(BuildContext context) {
    return Text('ai_chat.title'.t, style: const TextStyle(color: Colors.white));
  }

  /// 构建应用栏操作按钮
  List<Widget> _buildAppBarActions(BuildContext context) {
    return [
      // 新对话按钮
      IconButton(
        icon: const Icon(Icons.refresh, color: Colors.white),
        onPressed: controller.clearMessages,
        tooltip: 'ai_chat.new_chat'.t,
      ),
      // 帮助按钮
      IconButton(
        icon: const Icon(Icons.info_outline, color: Colors.white),
        onPressed: () => _showHelpDialog(context),
        tooltip: 'ai_chat.help'.t,
      ),
    ];
  }

  // ========================================================================
  // 对话框方法
  // ========================================================================

  /// 显示帮助对话框
  ///
  /// 显示AI助手的功能说明、使用方法和提示
  void _showHelpDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: _buildHelpDialogTitle(context),
        content: _buildHelpDialogContent(context),
        actions: _buildHelpDialogActions(context),
      ),
    );
  }

  /// 构建帮助对话框标题
  Widget _buildHelpDialogTitle(BuildContext context) {
    return Row(
      children: [
        Icon(Icons.help_outline, color: AppColors.getPrimary(context)),
        Dimensions.horizontalSpacerM,
        Text('ai_chat.help_title'.t),
      ],
    );
  }

  /// 构建帮助对话框内容
  Widget _buildHelpDialogContent(BuildContext context) {
    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          // 功能介绍
          _buildHelpSection(
            context,
            title: 'ai_chat.help_features_title'.t,
            content: 'ai_chat.help_features_content'.t,
            icon: Icons.star_outline,
          ),
          Dimensions.verticalSpacerM,
          // 使用方法
          _buildHelpSection(
            context,
            title: 'ai_chat.help_usage_title'.t,
            content: 'ai_chat.help_usage_content'.t,
            icon: Icons.chat_bubble_outline,
          ),
          Dimensions.verticalSpacerM,
          // 使用提示
          _buildHelpSection(
            context,
            title: 'ai_chat.help_tips_title'.t,
            content: 'ai_chat.help_tips_content'.t,
            icon: Icons.lightbulb_outline,
          ),
        ],
      ),
    );
  }

  /// 构建帮助对话框操作按钮
  List<Widget> _buildHelpDialogActions(BuildContext context) {
    return [TextButton(onPressed: () => Navigator.of(context).pop(), child: Text('ai_chat.got_it'.t))];
  }

  /// 构建帮助章节
  ///
  /// 用于显示帮助对话框中的各个章节
  ///
  /// [context] 构建上下文
  /// [title] 章节标题
  /// [content] 章节内容
  /// [icon] 章节图标
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
          // 章节标题
          Row(
            children: [
              Icon(icon, size: Dimensions.iconSizeM, color: AppColors.getPrimary(context)),
              Dimensions.horizontalSpacerM,
              Text(title, style: AppTypography.titleMedium.copyWith(fontWeight: FontWeight.w600)),
            ],
          ),
          Dimensions.verticalSpacerS,
          // 章节内容
          Text(content, style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurfaceVariant(context))),
        ],
      ),
    );
  }
}
