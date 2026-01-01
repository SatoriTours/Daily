import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'chat_message.dart';
import 'message_bubble.dart';
import 'chat_input.dart';

/// AI聊天界面组件
///
/// 提供完整的聊天界面，包含消息列表、输入框、建议词等功能
/// 支持自定义头部、输入框和空状态，可灵活配置显示选项
class ChatInterface extends StatefulWidget {
  // ========================================================================
  // 属性
  // ========================================================================
  /// 消息列表
  /// 包含所有聊天消息，按时间顺序排列
  final List<ChatMessage> messages;

  /// 发送消息回调
  /// 用户点击发送按钮或按回车时触发
  final ValueChanged<String> onSendMessage;

  /// 重试消息回调
  /// 当消息发送失败时，用户可点击重试
  final Function(ChatMessage)? onRetryMessage;

  /// 滚动控制器
  /// 用于控制消息列表的滚动行为，若为空则自动创建
  final ScrollController? scrollController;

  /// 输入框控制器
  /// 用于控制输入框内容，若为空则自动创建
  final TextEditingController? inputController;

  /// 是否显示头部
  /// 控制是否显示标题区域
  final bool showHeader;

  /// 头部标题
  /// 显示在界面顶部的主标题
  final String? headerTitle;

  /// 头部副标题
  /// 显示在主标题下方的辅助信息
  final String? headerSubtitle;

  /// 是否自动滚动到底部
  /// 有新消息时是否自动滚动到最新消息
  final bool autoScrollToBottom;

  /// 自定义输入框
  /// 替代默认输入框的自定义组件
  final Widget? customInput;

  /// 加载状态
  /// 显示加载指示器，通常在等待AI回复时
  final bool isLoading;

  /// 自定义空状态
  /// 消息列表为空时显示的自定义组件
  final Widget? customEmptyState;

  const ChatInterface({
    super.key,
    required this.messages,
    required this.onSendMessage,
    this.onRetryMessage,
    this.scrollController,
    this.inputController,
    this.showHeader = true,
    this.headerTitle,
    this.headerSubtitle,
    this.autoScrollToBottom = true,
    this.customInput,
    this.isLoading = false,
    this.customEmptyState,
  });

  @override
  State<ChatInterface> createState() => _ChatInterfaceState();
}

/// ChatInterface 的状态管理类
///
/// 负责管理滚动控制器和输入框控制器的生命周期
class _ChatInterfaceState extends State<ChatInterface> {
  // ========================================================================
  // 属性
  // ========================================================================

  /// 滚动控制器
  late ScrollController _scrollController;

  /// 输入框控制器
  late TextEditingController _inputController;

  // ========================================================================
  // 生命周期
  // ========================================================================

  @override
  void initState() {
    super.initState();
    logger.d('[ChatInterface] 初始化，消息数量: ${widget.messages.length}');

    _scrollController = widget.scrollController ?? ScrollController();
    _inputController = widget.inputController ?? TextEditingController();

    // 监听消息变化，自动滚动到底部
    if (widget.autoScrollToBottom) {
      logger.d('[ChatInterface] 启用自动滚动');
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _scrollToBottom();
      });
    }
  }

  @override
  void didUpdateWidget(ChatInterface oldWidget) {
    super.didUpdateWidget(oldWidget);

    // 消息列表变化时自动滚动到底部
    if (widget.autoScrollToBottom && widget.messages.length != oldWidget.messages.length) {
      logger.d('[ChatInterface] 消息数量变化: ${oldWidget.messages.length} -> ${widget.messages.length}');
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _scrollToBottom();
      });
    }
  }

  @override
  void dispose() {
    logger.d('[ChatInterface] 释放资源');

    if (widget.scrollController == null) {
      _scrollController.dispose();
    }
    if (widget.inputController == null) {
      _inputController.dispose();
    }
    super.dispose();
  }

  // ========================================================================
  // 辅助方法
  // ========================================================================

  /// 滚动到底部
  /// 带动画效果，使用缓出曲线
  void _scrollToBottom() {
    if (_scrollController.hasClients) {
      logger.d('[ChatInterface] 滚动到底部');
      _scrollController.animateTo(
        _scrollController.position.maxScrollExtent,
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeOut,
      );
    } else {
      logger.d('[ChatInterface] 滚动控制器未就绪，跳过滚动');
    }
  }

  /// 处理发送消息
  /// 发送消息后自动滚动到底部
  void _handleSendMessage(String message) {
    logger.i('[ChatInterface] 发送消息: ${message.substring(0, message.length > 50 ? 50 : message.length)}...');
    widget.onSendMessage(message);
    _scrollToBottom();
  }

  // ========================================================================
  // UI构建
  // ========================================================================

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        if (widget.showHeader) _buildHeader(context),
        Expanded(child: _buildMessagesList(context)),
        _buildInputArea(context),
      ],
    );
  }

  /// 构建头部
  Widget _buildHeader(BuildContext context) {
    return Container(
      padding: Dimensions.paddingM,
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        border: Border(bottom: BorderSide(color: AppColors.getOutline(context).withValues(alpha: 0.2))),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: Dimensions.paddingS,
                decoration: BoxDecoration(
                  color: AppColors.getPrimaryContainer(context),
                  borderRadius: BorderRadius.circular(Dimensions.radiusCircular),
                ),
                child: Icon(
                  Icons.auto_awesome_outlined,
                  size: Dimensions.iconSizeM,
                  color: AppColors.getOnPrimaryContainer(context),
                ),
              ),
              Dimensions.horizontalSpacerM,
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.headerTitle ?? 'ai_chat.title'.t,
                      style: AppTypography.titleLarge.copyWith(fontWeight: FontWeight.bold),
                    ),
                    if (widget.headerSubtitle != null) ...[
                      Dimensions.verticalSpacerXs,
                      Text(
                        widget.headerSubtitle!,
                        style: AppTypography.bodySmall.copyWith(color: AppColors.getOnSurfaceVariant(context)),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  /// 构建消息列表
  Widget _buildMessagesList(BuildContext context) {
    if (widget.messages.isEmpty && !widget.isLoading) {
      return _buildEmptyState(context);
    }

    return ListView.builder(
      controller: _scrollController,
      padding: const EdgeInsets.only(top: 16, bottom: 24),
      itemCount: widget.messages.length,
      itemBuilder: (context, index) {
        final message = widget.messages[index];
        return MessageBubble(message: message, onRetry: () => widget.onRetryMessage?.call(message));
      },
    );
  }

  /// 构建空状态
  Widget _buildEmptyState(BuildContext context) {
    if (widget.customEmptyState != null) {
      return widget.customEmptyState!;
    }

    return Center(
      child: Padding(
        padding: Dimensions.paddingPage,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 120,
              height: 120,
              decoration: BoxDecoration(color: AppColors.getPrimaryContainer(context), shape: BoxShape.circle),
              child: Icon(Icons.chat_bubble_outline, size: 60, color: AppColors.getOnPrimaryContainer(context)),
            ),
            Dimensions.verticalSpacerL,
            Text(
              'ai_chat.empty_title'.t,
              style: AppTypography.titleLarge.copyWith(fontWeight: FontWeight.bold),
              textAlign: TextAlign.center,
            ),
            Dimensions.verticalSpacerS,
            Text(
              'ai_chat.empty_subtitle'.t,
              style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurfaceVariant(context)),
              textAlign: TextAlign.center,
            ),
            Dimensions.verticalSpacerL,
            _buildSuggestionChips(context),
          ],
        ),
      ),
    );
  }

  /// 构建建议标签
  Widget _buildSuggestionChips(BuildContext context) {
    final suggestions = [
      'ai_chat.suggestion_articles'.t,
      'ai_chat.suggestion_diary'.t,
      'ai_chat.suggestion_books'.t,
      'ai_chat.suggestion_summary'.t,
    ];

    return Wrap(
      spacing: Dimensions.spacingS,
      runSpacing: Dimensions.spacingS,
      alignment: WrapAlignment.center,
      children: suggestions.map((suggestion) {
        return Material(
          color: AppColors.getSurfaceContainer(context),
          borderRadius: BorderRadius.circular(20),
          child: InkWell(
            onTap: () => _handleSendMessage(suggestion),
            borderRadius: BorderRadius.circular(20),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: AppColors.getOutline(context).withValues(alpha: 0.3)),
              ),
              child: Text(suggestion, style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurface(context))),
            ),
          ),
        );
      }).toList(),
    );
  }

  /// 构建输入区域
  Widget _buildInputArea(BuildContext context) {
    if (widget.customInput != null) {
      return widget.customInput!;
    }

    return SimpleChatInput(
      controller: _inputController,
      disabled: widget.isLoading,
      hintText: 'ai_chat.input_hint'.t,
      onSendMessage: _handleSendMessage,
    );
  }
}
