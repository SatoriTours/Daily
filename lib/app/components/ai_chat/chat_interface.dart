import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/extensions/i18n_extension.dart';
import 'chat_message.dart';
import 'message_bubble.dart';
import 'chat_input.dart';

/// AI聊天界面组件
class ChatInterface extends StatefulWidget {
  /// 消息列表
  final List<ChatMessage> messages;

  /// 发送消息回调
  final ValueChanged<String> onSendMessage;

  /// 重试消息回调
  final Function(ChatMessage)? onRetryMessage;

  /// 滚动控制器
  final ScrollController? scrollController;

  /// 输入框控制器
  final TextEditingController? inputController;

  /// 是否显示头部
  final bool showHeader;

  /// 头部标题
  final String? headerTitle;

  /// 头部副标题
  final String? headerSubtitle;

  /// 是否自动滚动到底部
  final bool autoScrollToBottom;

  /// 自定义输入框
  final Widget? customInput;

  /// 加载状态
  final bool isLoading;

  /// 自定义空状态
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

class _ChatInterfaceState extends State<ChatInterface> {
  late ScrollController _scrollController;
  late TextEditingController _inputController;

  @override
  void initState() {
    super.initState();
    _scrollController = widget.scrollController ?? ScrollController();
    _inputController = widget.inputController ?? TextEditingController();

    // 监听消息变化，自动滚动到底部
    if (widget.autoScrollToBottom) {
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
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _scrollToBottom();
      });
    }
  }

  @override
  void dispose() {
    if (widget.scrollController == null) {
      _scrollController.dispose();
    }
    if (widget.inputController == null) {
      _inputController.dispose();
    }
    super.dispose();
  }

  void _scrollToBottom() {
    if (_scrollController.hasClients) {
      _scrollController.animateTo(
        _scrollController.position.maxScrollExtent,
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeOut,
      );
    }
  }

  void _handleSendMessage(String message) {
    widget.onSendMessage(message);
    _scrollToBottom();
  }

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
      padding: Dimensions.paddingVerticalM,
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
        return ActionChip(
          label: Text(suggestion),
          onPressed: () => _handleSendMessage(suggestion),
          backgroundColor: AppColors.getSurfaceContainerHighest(context),
          pressElevation: 2,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(Dimensions.radiusM),
            side: BorderSide(color: AppColors.getOutline(context).withValues(alpha: 0.3)),
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
